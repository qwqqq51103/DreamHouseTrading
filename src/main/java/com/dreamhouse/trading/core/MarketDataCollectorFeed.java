package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MarketDataFeed backed by the local MySQL database populated by MarketDataCollector.
 */
public class MarketDataCollectorFeed implements MarketDataFeed {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCollectorFeed.class);
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofSeconds(90);
    private static final long POLL_INTERVAL_SECONDS = 10;
    private static final Set<Timeframe> INTRADAY_TIMEFRAMES =
            EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1);

    private final MarketDataCollectorRepository repository;
    private final Duration staleThreshold;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastNotifiedTickTime = new ConcurrentHashMap<>();
    private final boolean closeRepository;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollTask;
    private volatile boolean connected;
    private volatile boolean paused;

    public MarketDataCollectorFeed() {
        this(createDefaultRepository(), DEFAULT_STALE_THRESHOLD, true);
    }

    public MarketDataCollectorFeed(MarketDataCollectorRepository repository) {
        this(repository, DEFAULT_STALE_THRESHOLD, false);
    }

    public MarketDataCollectorFeed(MarketDataCollectorRepository repository, Duration staleThreshold) {
        this(repository, staleThreshold, false);
    }

    private MarketDataCollectorFeed(MarketDataCollectorRepository repository, Duration staleThreshold, boolean closeRepository) {
        this.repository = repository;
        this.staleThreshold = staleThreshold != null ? staleThreshold : DEFAULT_STALE_THRESHOLD;
        this.closeRepository = closeRepository;
    }

    private static MarketDataCollectorRepository createDefaultRepository() {
        try {
            return new MarketDataCollectorRepository(
                    MarketDataCollectorRepository.DEFAULT_JDBC_URL,
                    MarketDataCollectorRepository.DEFAULT_USER,
                    MarketDataCollectorRepository.DEFAULT_PASSWORD);
        } catch (SQLException e) {
            return MarketDataCollectorRepository.unavailable(e.getMessage());
        }
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        if (symbol == null || symbol.isBlank() || listener == null) {
            return;
        }
        listeners.computeIfAbsent(symbol, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        notifyLatestTick(symbol);
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) {
            return;
        }
        symbolListeners.remove(listener);
        if (symbolListeners.isEmpty()) {
            listeners.remove(symbol);
            lastNotifiedTickTime.remove(symbol);
        }
    }

    @Override
    public synchronized void start() {
        connected = repository.isAvailable();
        if (!connected) {
            logger.warn("MarketDataCollectorFeed is not connected; database is unavailable");
            return;
        }
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "market-data-collector-feed");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (pollTask == null || pollTask.isCancelled()) {
            pollTask = executor.scheduleAtFixedRate(this::pollLatestTicks, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        logger.info("MarketDataCollectorFeed connected to local collector database");
    }

    @Override
    public synchronized void stop() {
        connected = false;
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (closeRepository) {
            repository.close();
        }
    }

    @Override
    public boolean isConnected() {
        return connected && repository.isAvailable();
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
        if (symbol == null || symbol.isBlank() || timeframe == null || barCount <= 0) {
            return List.of();
        }
        if (!repository.isAvailable()) {
            connected = false;
            return List.of();
        }
        DataFreshness freshness = checkFreshness(symbol);
        if (isMarketOpen() && freshness.stale()) {
            logger.warn(
                    "{} local collector data is stale; latest={}, lag={}s, threshold={}s; skipping scan instead of calling FinMind",
                    symbol,
                    freshness.latest(),
                    freshness.lagSeconds(),
                    staleThreshold.toSeconds());
            return List.of();
        }

        String interval = toCollectorInterval(timeframe);
        List<Bar> candles = interval == null ? List.of() : repository.findLatestCandles(symbol, interval, barCount);
        if (candles.size() >= barCount || !INTRADAY_TIMEFRAMES.contains(timeframe)) {
            return candles;
        }

        List<Tick> ticks = repository.findTodayMarketOpenTicks(symbol);
        List<Bar> bars = aggregateTicks(ticks, timeframe);
        if (bars.size() > barCount) {
            return new ArrayList<>(bars.subList(bars.size() - barCount, bars.size()));
        }
        return bars;
    }

    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        List<Bar> bars = fetchHistoricalBars(symbol, timeframe, barCount);
        notifyBarsAsTicks(symbol, bars);
    }

    boolean isStale(String symbol) {
        return checkFreshness(symbol).stale();
    }

    private DataFreshness checkFreshness(String symbol) {
        LocalDateTime latest = repository.findLatestDataTime(symbol);
        if (latest == null) {
            return new DataFreshness(null, -1L, true);
        }
        long lagSeconds = Math.max(0L, Duration.between(latest, LocalDateTime.now(TAIPEI_ZONE)).toSeconds());
        return new DataFreshness(latest, lagSeconds, lagSeconds > staleThreshold.toSeconds());
    }

    List<Bar> aggregateTicks(List<Tick> ticks, Timeframe timeframe) {
        if (ticks == null || ticks.isEmpty() || !INTRADAY_TIMEFRAMES.contains(timeframe)) {
            return List.of();
        }
        List<Tick> sortedTicks = ticks.stream()
                .filter(tick -> tick != null && tick.getTimestamp() != null)
                .sorted(Comparator.comparing(Tick::getTimestamp))
                .toList();

        Map<LocalDateTime, MutableBar> grouped = new LinkedHashMap<>();
        long previousVolume = -1L;
        for (Tick tick : sortedTicks) {
            LocalDateTime bucketTime = floorToTimeframe(tick.getTimestamp(), timeframe);
            MutableBar bar = grouped.computeIfAbsent(bucketTime, ignored -> new MutableBar(bucketTime, tick.getPrice()));
            long volumeContribution = resolveVolumeContribution(previousVolume, tick.getVolume());
            bar.add(tick.getPrice(), volumeContribution);
            previousVolume = tick.getVolume();
        }

        return grouped.values().stream()
                .map(MutableBar::toBar)
                .toList();
    }

    private void pollLatestTicks() {
        if (paused || !connected) {
            return;
        }
        for (String symbol : listeners.keySet()) {
            notifyLatestTick(symbol);
        }
    }

    private void notifyLatestTick(String symbol) {
        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null || symbolListeners.isEmpty() || isMarketOpen() && isStale(symbol)) {
            return;
        }
        List<Tick> ticks = repository.findLatestTicks(symbol, 1);
        if (ticks.isEmpty()) {
            return;
        }
        Tick tick = ticks.get(0);
        LocalDateTime lastTime = lastNotifiedTickTime.get(symbol);
        if (lastTime != null && !tick.getTimestamp().isAfter(lastTime)) {
            return;
        }
        lastNotifiedTickTime.put(symbol, tick.getTimestamp());
        SwingUtilities.invokeLater(() -> {
            for (MarketDataListener listener : symbolListeners) {
                listener.onTick(tick);
            }
        });
    }

    private void notifyBarsAsTicks(String symbol, List<Bar> bars) {
        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null || symbolListeners.isEmpty() || bars == null || bars.isEmpty()) {
            return;
        }
        for (Bar bar : bars) {
            generateTicksFromBar(symbol, bar, symbolListeners);
        }
    }

    private void generateTicksFromBar(String symbol, Bar bar, List<MarketDataListener> symbolListeners) {
        double[] prices = {bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose()};
        long tickVolume = Math.max(0L, bar.getVolume() / prices.length);
        for (int index = 0; index < prices.length; index++) {
            Tick tick = new Tick(symbol, bar.getTimestamp().plusSeconds(index * 15L), prices[index], tickVolume);
            SwingUtilities.invokeLater(() -> {
                for (MarketDataListener listener : symbolListeners) {
                    listener.onTick(tick);
                }
            });
        }
    }

    private String toCollectorInterval(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> "1m";
            case M5 -> "5m";
            case M15 -> "15m";
            case M30 -> "30m";
            case H1 -> "1h";
            case D1 -> "1d";
            default -> null;
        };
    }

    private LocalDateTime floorToTimeframe(LocalDateTime timestamp, Timeframe timeframe) {
        LocalDateTime minute = timestamp.truncatedTo(ChronoUnit.MINUTES);
        int frameMinutes = timeframe.getMinutes();
        int minuteOfDay = minute.getHour() * 60 + minute.getMinute();
        int flooredMinuteOfDay = minuteOfDay - (minuteOfDay % frameMinutes);
        return minute.toLocalDate().atStartOfDay().plusMinutes(flooredMinuteOfDay);
    }

    private long resolveVolumeContribution(long previousVolume, long currentVolume) {
        if (currentVolume <= 0) {
            return 0L;
        }
        if (previousVolume < 0) {
            return currentVolume;
        }
        if (currentVolume >= previousVolume) {
            return currentVolume - previousVolume;
        }
        return currentVolume;
    }

    private boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now(TAIPEI_ZONE);
        return switch (now.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> {
                int minuteOfDay = now.getHour() * 60 + now.getMinute();
                yield minuteOfDay >= 9 * 60 && minuteOfDay <= 13 * 60 + 30;
            }
        };
    }

    private static class MutableBar {
        private final LocalDateTime timestamp;
        private final double open;
        private double high;
        private double low;
        private double close;
        private long volume;

        private MutableBar(LocalDateTime timestamp, double open) {
            this.timestamp = timestamp;
            this.open = open;
            this.high = open;
            this.low = open;
            this.close = open;
        }

        private void add(double price, long volumeContribution) {
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += Math.max(0L, volumeContribution);
        }

        private Bar toBar() {
            return new Bar(timestamp, open, high, low, close, volume);
        }
    }

    private record DataFreshness(LocalDateTime latest, long lagSeconds, boolean stale) {
    }
}
