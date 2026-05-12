package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.finmind.FinMindAccessDeniedException;
import com.dreamhouse.trading.core.finmind.FinMindQuotaExceededException;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 監控服務，負責批次掃描觀察清單並依排名觸發交易信號。
 */
public class SignalMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(SignalMonitorService.class);
    private static final List<TradeMode> RADAR_MODES = List.of(
            TradeMode.DAY_TRADE,
            TradeMode.SHORT_SWING,
            TradeMode.SWING_TRADE);
    private static final Duration UPSTREAM_FAILURE_COOLDOWN = Duration.ofMinutes(15);

    private final SignalMonitorConfig config;
    private final MarketDataFeed dataFeed;
    private final MarketScannerService scannerService;
    private final DecisionConfig decisionConfig;
    private final Set<String> monitoredSymbols = ConcurrentHashMap.newKeySet();
    private final Map<String, LocalDateTime> lastSignalTime = new ConcurrentHashMap<>();
    private final AtomicInteger roundCounter = new AtomicInteger();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> monitorTask;
    private volatile boolean running;
    private volatile LocalDateTime upstreamFailurePausedUntil;

    private BiConsumer<String, DecisionResult> onSignalDetected;
    private Consumer<String> onStatusUpdate;
    private Consumer<List<MarketScanResult>> onScanResults;

    public SignalMonitorService(MarketDataFeed dataFeed, SignalMonitorConfig config, DecisionConfig decisionConfig) {
        this.dataFeed = dataFeed;
        this.config = config != null ? config : SignalMonitorConfig.createDefault();
        this.decisionConfig = decisionConfig != null ? decisionConfig : DecisionConfig.createDefault();
        this.scannerService = new MarketScannerService(dataFeed);
    }

    public synchronized void start(Collection<String> symbols) {
        if (running) {
            logger.warn("Signal monitor is already running");
            return;
        }
        monitoredSymbols.clear();
        if (symbols != null) {
            monitoredSymbols.addAll(symbols.stream().filter(symbol -> symbol != null && !symbol.isBlank()).toList());
        }
        if (monitoredSymbols.isEmpty()) {
            notifyStatus("監控未啟動：觀察清單為空");
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "signal-monitor");
            thread.setDaemon(true);
            return thread;
        });
        monitorTask = executor.scheduleAtFixedRate(this::scanSymbols, 0, config.getScanIntervalSeconds(), TimeUnit.SECONDS);
        running = true;
        notifyStatus(String.format("監控已啟動：%d 檔商品，每 %d 秒掃描一次",
                monitoredSymbols.size(), config.getScanIntervalSeconds()));
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        if (monitorTask != null) {
            monitorTask.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        monitorTask = null;
        executor = null;
        running = false;
        upstreamFailurePausedUntil = null;
        roundCounter.set(0);
        lastSignalTime.clear();
        notifyStatus("監控已停止");
    }

    private void scanSymbols() {
        if (monitoredSymbols.isEmpty()) {
            notifyStatus("監控略過：觀察清單為空");
            return;
        }

        LocalDateTime pausedUntil = upstreamFailurePausedUntil;
        if (pausedUntil != null && LocalDateTime.now().isBefore(pausedUntil)) {
            notifyStatus(String.format("Market data scan paused until %s because FinMind rejected the previous request",
                    pausedUntil));
            return;
        }

        int round = roundCounter.incrementAndGet();
        notifyStatus(String.format("第 %d 輪掃描開始", round));

        List<MarketScanResult> results = new ArrayList<>();
        boolean scanPaused = false;
        for (String symbol : monitoredSymbols) {
            try {
                MarketScanResult bestResult = findBestRadarCandidate(symbol);
                if (bestResult != null) {
                    results.add(bestResult);
                }
            } catch (FinMindAccessDeniedException | FinMindQuotaExceededException e) {
                upstreamFailurePausedUntil = LocalDateTime.now().plus(UPSTREAM_FAILURE_COOLDOWN);
                logger.warn("Market data scan paused for {} minutes after FinMind rejected request for {}: {}",
                        UPSTREAM_FAILURE_COOLDOWN.toMinutes(), symbol, e.getMessage());
                notifyStatus(String.format("Market data scan paused for %d minutes: %s",
                        UPSTREAM_FAILURE_COOLDOWN.toMinutes(), e.getMessage()));
                scanPaused = true;
                break;
            } catch (Exception e) {
                logger.warn("Scan failed for {}: {}", symbol, e.getMessage());
            }
        }

        results.sort(Comparator.naturalOrder());
        notifyScanResults(results);
        if (scanPaused) {
            return;
        }
        notifyStatus(String.format("第 %d 輪雷達排名已更新，共 %d 檔", round, results.size()));

        int signalCount = 0;
        for (MarketScanResult result : results) {
            if (!result.hasTradeSignal()) {
                continue;
            }
            if (!isNewSignal(result.getSymbol())) {
                continue;
            }
            signalCount++;
            lastSignalTime.put(result.getSymbol(), LocalDateTime.now());
            notifySignal(result.getSymbol(), result.getDecisionResult());
        }

        notifyStatus(String.format("第 %d 輪掃描完成：掃描 %d 檔，觸發 %d 筆訊號",
                round, results.size(), signalCount));
    }

    private MarketScanResult findBestRadarCandidate(String symbol) {
        MarketScanResult best = null;
        for (TradeMode mode : RADAR_MODES) {
            MarketScanResult candidate = scannerService.scan(symbol, createScanRequest(mode));
            if (candidate == null) {
                continue;
            }
            if (best == null || compareCandidates(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private int compareCandidates(MarketScanResult left, MarketScanResult right) {
        int actionableCompare = Boolean.compare(left.hasTradeSignal(), right.hasTradeSignal());
        if (actionableCompare != 0) {
            return actionableCompare;
        }
        int scoreCompare = Double.compare(left.getScore(), right.getScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return Double.compare(left.getConfidence(), right.getConfidence());
    }

    private MarketScannerService.ScanRequest createScanRequest(TradeMode mode) {
        return MarketScannerService.ScanRequest.createDefault()
                .tradeMode(mode)
                .timeframe(resolveTimeframe(mode))
                .barCount(resolveBarCount(mode))
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(config.getRadarStrategyConfig());
    }

    private Timeframe resolveTimeframe(TradeMode mode) {
        if (config.getRadarStrategyConfig() != null) {
            return config.getRadarStrategyConfig().resolveTimeframe(mode);
        }
        return switch (mode) {
            case DAY_TRADE -> config.getTimeframe();
            case SHORT_SWING -> Timeframe.M15;
            case SWING_TRADE -> Timeframe.H1;
            default -> config.getTimeframe();
        };
    }

    private int resolveBarCount(TradeMode mode) {
        if (config.getRadarStrategyConfig() != null) {
            return config.getRadarStrategyConfig().resolveBarCount(mode);
        }
        return switch (mode) {
            case DAY_TRADE -> Math.max(config.getBarCount(), 120);
            case SHORT_SWING -> Math.max(config.getBarCount(), 160);
            case SWING_TRADE -> Math.max(config.getBarCount(), 240);
            default -> config.getBarCount();
        };
    }

    private boolean isNewSignal(String symbol) {
        LocalDateTime lastTime = lastSignalTime.get(symbol);
        if (lastTime == null) {
            return true;
        }
        return Duration.between(lastTime, LocalDateTime.now()).toMinutes() >= config.getMinSignalIntervalMinutes();
    }

    private void notifySignal(String symbol, DecisionResult result) {
        if (onSignalDetected == null) {
            return;
        }
        try {
            onSignalDetected.accept(symbol, result);
        } catch (Exception e) {
            logger.error("Signal callback failed", e);
        }
    }

    private void notifyStatus(String message) {
        logger.info(message);
        if (onStatusUpdate == null) {
            return;
        }
        try {
            onStatusUpdate.accept(message);
        } catch (Exception e) {
            logger.error("Status callback failed", e);
        }
    }

    private void notifyScanResults(List<MarketScanResult> results) {
        if (onScanResults == null) {
            return;
        }
        try {
            onScanResults.accept(results);
        } catch (Exception e) {
            logger.error("Scan result callback failed", e);
        }
    }

    public void setOnSignalDetected(BiConsumer<String, DecisionResult> onSignalDetected) {
        this.onSignalDetected = onSignalDetected;
    }

    public void setOnStatusUpdate(Consumer<String> onStatusUpdate) {
        this.onStatusUpdate = onStatusUpdate;
    }

    public void setOnScanResults(Consumer<List<MarketScanResult>> onScanResults) {
        this.onScanResults = onScanResults;
    }

    public boolean isRunning() {
        return running;
    }

    public SignalMonitorConfig getConfig() {
        return config;
    }
}
