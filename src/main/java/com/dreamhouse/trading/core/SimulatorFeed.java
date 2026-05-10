package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class SimulatorFeed implements MarketDataFeed {
    private static final double REALTIME_MAX_CHANGE_RATIO = 0.018;
    private static final double REALTIME_SHOCK_PROBABILITY = 0.15;
    private static final double REALTIME_SHOCK_RATIO = 0.05;
    private static final double HISTORICAL_BAR_CHANGE_RATIO = 0.035;
    private static final double HISTORICAL_WICK_RATIO = 0.015;
    private static final int MAX_SIMULATED_M1_BARS = 500;

    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final Random random = new Random();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, List<DepthLevel>> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Map<Timeframe, List<Bar>>> historicalBars = new ConcurrentHashMap<>();
    private boolean connected = false;
    private boolean paused = false;  // 新增：暫停狀態
    private ScheduledFuture<?> marketDataTask;  // 新增：任務引用

    /**
     * 根據商品代號獲取合理的基準價格
     * @param symbol 商品代號
     * @return 基準價格
     */
    private double getReasonableBasePrice(String symbol) {
        // 台股（.TW 或 .TWO 結尾）
        if (symbol.endsWith(".TW") || symbol.endsWith(".TWO")) {
            String code = symbol.split("\\.")[0];
            // 高價股（台積電、大立光等）
            if (code.equals("2330") || code.equals("3008")) {
                return 800.0 + random.nextDouble() * 400.0;  // 800-1200
            }
            // 中高價股
            else if (code.startsWith("23") || code.startsWith("24")) {
                return 400.0 + random.nextDouble() * 400.0;  // 400-800
            }
            // 一般台股
            else {
                return 50.0 + random.nextDouble() * 100.0;  // 50-150
            }
        }
        // 港股（.HK 結尾）
        else if (symbol.endsWith(".HK")) {
            return 50.0 + random.nextDouble() * 200.0;  // 50-250
        }
        // A股（.SS 上海 或 .SZ 深圳）
        else if (symbol.endsWith(".SS") || symbol.endsWith(".SZ")) {
            return 20.0 + random.nextDouble() * 80.0;  // 20-100
        }
        // 美股及其他
        else {
            // 高價股（如 Amazon, Google, Tesla 等）
            if (symbol.equals("AMZN") || symbol.equals("GOOGL") ||
                symbol.equals("TSLA") || symbol.equals("NVDA")) {
                return 200.0 + random.nextDouble() * 600.0;  // 200-800
            }
            // 一般美股
            else {
                return 80.0 + random.nextDouble() * 120.0;  // 80-200
            }
        }
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        if (!lastPrices.containsKey(symbol)) {
            double basePrice = getReasonableBasePrice(symbol);
            lastPrices.put(symbol, basePrice);
            initializeOrderBook(symbol);
            System.out.println("[SimulatorFeed] 訂閱商品 " + symbol + "，初始價格: " + basePrice);
        }
    }
    
    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            // ⭐ 如果列表為空，從 Map 中移除該商品，避免繼續生成模擬數據
            if (list.isEmpty()) {
                listeners.remove(symbol);
            }
        }
    }
    
    @Override
    public void start() {
        ensureExecutor();
        connected = true;
        
        // 在背景執行緒中生成歷史數據，避免阻塞 EDT
        new Thread(() -> {
            generateHistoricalData();
            
            // 歷史數據生成完畢後，開始即時數據更新
            if (connected && !executor.isShutdown()) {
                try {
                    marketDataTask = executor.scheduleAtFixedRate(this::generateMarketData, 0, 1000, TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.RejectedExecutionException ignored) {
                    // stop() can race with initial historical generation during tests or fast reconnects.
                }
            }
        }, "HistoricalDataGenerator").start();
    }
    
    /**
     * 生成 50 條歷史 K 線數據
     */
    private void generateHistoricalData() {
        for (String symbol : listeners.keySet()) {
            generateHistoricalDataForSymbol(symbol);
        }
    }

    /**
     * 為特定商品生成歷史數據（預設使用1分鐘週期）
     */
    public void generateHistoricalDataForSymbol(String symbol) {
        loadHistoricalData(symbol, Timeframe.M1);
    }

    /**
     * 根據週期生成歷史數據（使用預設K線數量）
     */
    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe) {
        // 根據週期決定預設的K線數量
        int defaultBarCount;
        switch (timeframe) {
            case M1:
                defaultBarCount = 50;  // 50分鐘
                break;
            case M5:
                defaultBarCount = 100; // 500分鐘 ≈ 8小時
                break;
            case M15:
                defaultBarCount = 100; // 1500分鐘 ≈ 1天
                break;
            case M30:
                defaultBarCount = 100; // 3000分鐘 ≈ 2天
                break;
            case H1:
                defaultBarCount = 120; // 120小時 ≈ 5天
                break;
            case D1:
                defaultBarCount = 200; // 200天
                break;
            case W1:
                defaultBarCount = 100; // 100週 ≈ 2年
                break;
            default:
                defaultBarCount = 50;
        }
        loadHistoricalData(symbol, timeframe, defaultBarCount);
    }

    /**
     * 根據週期和K線數量生成歷史數據
     */
    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        System.out.println("[SimulatorFeed] 開始為商品 " + symbol + " 生成 " + barCount + " 根 " + timeframe.getLabel() + " 歷史數據");

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null || symbolListeners.isEmpty()) {
            System.err.println("[SimulatorFeed] 錯誤：找不到商品 " + symbol + " 的監聽器");
            return;
        }

        System.out.println("[SimulatorFeed] 找到 " + symbolListeners.size() + " 個監聽器");

        List<Bar> bars = fetchHistoricalBars(symbol, timeframe, barCount);

        for (Bar bar : bars) {
            // 根據週期決定發送的tick數量
            int intervalMinutes = timeframe.getMinutes();
            int ticksPerBar = Math.max(1, Math.min(4, 4 / (intervalMinutes / 60 + 1)));

            // 生成這個週期內的 Tick 數據
            for (int j = 0; j < ticksPerBar; j++) {
                long secondsInterval = (intervalMinutes * 60L) / ticksPerBar;
                LocalDateTime tickTime = bar.getTimestamp().plusSeconds(j * secondsInterval);
                double tickPrice;

                if (j == 0) {
                    tickPrice = bar.getOpen();
                } else if (j == ticksPerBar - 1) {
                    tickPrice = bar.getClose();
                } else {
                    // 中間的 tick 在 low 和 high 之間
                    tickPrice = round(bar.getLow() + random.nextDouble() * (bar.getHigh() - bar.getLow()));
                }

                long tickVolume = bar.getVolume() / ticksPerBar;
                Tick tick = new Tick(symbol, tickTime, tickPrice, tickVolume);

                // 在 Swing 執行緒中通知
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                    }
                });

                // 每個 tick 之間暫停一小段時間，讓UI有時間更新
                if (j < ticksPerBar - 1) {
                    try {
                        Thread.sleep(5); // 5ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

        }

        // 更新最後價格
        if (!bars.isEmpty()) {
            lastPrices.put(symbol, bars.get(bars.size() - 1).getClose());
        }

        System.out.println("[SimulatorFeed] " + symbol + " " + timeframe.getLabel() + " 歷史數據生成完成，共 " + bars.size() + " 根K線");
    }

    @Override
    public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
        int safeCount = Math.max(2, barCount);
        Map<Timeframe, List<Bar>> byTimeframe =
            historicalBars.computeIfAbsent(symbol, key -> new ConcurrentHashMap<>());

        List<Bar> cached = byTimeframe.get(timeframe);
        if (cached == null || cached.size() < safeCount) {
            cached = generateBars(symbol, timeframe, safeCount);
            byTimeframe.put(timeframe, cached);
        }

        return MarketDataNormalizer.normalizeBars(cached, safeCount);
    }
    
    @Override
    public void stop() {
        connected = false;
        if (marketDataTask != null) {
            marketDataTask.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    private void initializeOrderBook(String symbol) {
        List<DepthLevel> depth = new ArrayList<>();
        double midPrice = lastPrices.get(symbol);

        // 檔位間距為價格的0.05%（對於1000元股票約為0.5元）
        double tickSize = midPrice * 0.0005;

        // 5 檔 ASK
        for (int i = 0; i < 5; i++) {
            double price = round(midPrice + (i + 1) * tickSize);
            long qty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.ASK, price, qty, i));
        }

        // 5 檔 BID
        for (int i = 0; i < 5; i++) {
            double price = round(midPrice - (i + 1) * tickSize);
            long qty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.BID, price, qty, i));
        }

        orderBooks.put(symbol, depth);
    }
    
    private void generateMarketData() {
        // 如果暫停，則不生成數據
        if (paused) {
            return;
        }

        for (String symbol : listeners.keySet()) {
            double lastPrice = lastPrices.get(symbol);

            // 生成新價格（隨機遊走）- 變化幅度為當前價格的±0.2%
            double maxChange = lastPrice * REALTIME_MAX_CHANGE_RATIO;
            double change = (random.nextDouble() - 0.5) * 2.0 * maxChange;
            if (random.nextDouble() < REALTIME_SHOCK_PROBABILITY) {
                change += random.nextGaussian() * lastPrice * REALTIME_SHOCK_RATIO;
            }
            double newPrice = round(Math.max(1.0, lastPrice + change));
            lastPrices.put(symbol, newPrice);
            
            LocalDateTime now = LocalDateTime.now();
            
            // 生成 Tick
            Tick tick = new Tick(symbol, now, newPrice, 100 + random.nextInt(500));
            updateRealtimeBar(symbol, tick);
            
            // 生成 Trade
            Trade.Side side = random.nextBoolean() ? Trade.Side.BID : Trade.Side.ASK;
            Trade trade = new Trade(now, newPrice, 100 + random.nextInt(300), side);
            
            // 更新五檔
            updateOrderBook(symbol, newPrice);
            List<DepthLevel> depth = new ArrayList<>(orderBooks.get(symbol));
            
            // 通知所有監聽器（在 Swing 執行緒中）
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners != null) {
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                        listener.onTrade(trade);
                        listener.onDepthUpdate(depth);
                    }
                });
            }
        }
    }
    
    private void updateOrderBook(String symbol, double midPrice) {
        List<DepthLevel> depth = orderBooks.get(symbol);
        if (depth == null) return;
        
        for (DepthLevel level : depth) {
            // 隨機調整數量
            if (random.nextDouble() < 0.3) {
                long delta = (long)(random.nextGaussian() * 200);
                long newQty = Math.max(100, level.getQuantity() + delta);
                level.setQuantity(newQty);
            }
        }
    }
    
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void updateRealtimeBar(String symbol, Tick tick) {
        Map<Timeframe, List<Bar>> byTimeframe =
            historicalBars.computeIfAbsent(symbol, key -> new ConcurrentHashMap<>());
        List<Bar> bars = byTimeframe.computeIfAbsent(Timeframe.M1, key -> new CopyOnWriteArrayList<>());

        LocalDateTime minute = tick.getTimestamp().withSecond(0).withNano(0);
        double price = tick.getPrice();
        long volume = tick.getVolume();

        synchronized (bars) {
            if (bars.isEmpty()) {
                bars.add(new Bar(minute, price, price, price, price, volume));
                return;
            }

            Bar last = bars.get(bars.size() - 1);
            if (last.getTimestamp().equals(minute)) {
                bars.set(bars.size() - 1, new Bar(
                    last.getTimestamp(),
                    last.getOpen(),
                    Math.max(last.getHigh(), price),
                    Math.min(last.getLow(), price),
                    price,
                    last.getVolume() + volume
                ));
            } else if (last.getTimestamp().isBefore(minute)) {
                double open = last.getClose();
                bars.add(new Bar(minute, open, Math.max(open, price), Math.min(open, price), price, volume));
                trimBars(bars, MAX_SIMULATED_M1_BARS);
            } else {
                bars.add(new Bar(minute, price, price, price, price, volume));
                bars.sort(Comparator.comparing(Bar::getTimestamp));
                trimBars(bars, MAX_SIMULATED_M1_BARS);
            }
        }
    }

    private void trimBars(List<Bar> bars, int maxSize) {
        while (bars.size() > maxSize) {
            bars.remove(0);
        }
    }

    private void ensureExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newScheduledThreadPool(1);
        }
    }

    private List<Bar> generateBars(String symbol, Timeframe timeframe, int barCount) {
        double basePrice = lastPrices.getOrDefault(symbol, getReasonableBasePrice(symbol));
        lastPrices.put(symbol, basePrice);

        int intervalMinutes = timeframe.getMinutes();
        LocalDateTime startTime = LocalDateTime.now()
            .withSecond(0)
            .withNano(0)
            .minusMinutes((long) barCount * intervalMinutes);

        double priceVariation = basePrice * 0.05;
        double currentPrice = basePrice - priceVariation + random.nextDouble() * (priceVariation * 2);
        List<Bar> bars = new ArrayList<>();

        for (int i = 0; i < barCount; i++) {
            LocalDateTime barTime = startTime.plusMinutes((long) i * intervalMinutes);
            double open = currentPrice;
            double maxChange = currentPrice * HISTORICAL_BAR_CHANGE_RATIO * Math.sqrt(Math.max(1.0, intervalMinutes) / 5.0);
            double change = (random.nextDouble() - 0.5) * 2.0 * maxChange;
            if (random.nextDouble() < 0.12) {
                change += random.nextGaussian() * currentPrice * HISTORICAL_BAR_CHANGE_RATIO;
            }
            double close = round(Math.max(1.0, open + change));
            double maxWick = currentPrice * HISTORICAL_WICK_RATIO * Math.sqrt(Math.max(1.0, intervalMinutes) / 5.0);
            double high = round(Math.max(open, close) + random.nextDouble() * maxWick);
            double low = round(Math.max(0.01, Math.min(open, close) - random.nextDouble() * maxWick));
            long baseVolume = 5000 + random.nextInt(15000);
            long volume = (long) (baseVolume * (intervalMinutes / 1.0));

            bars.add(new Bar(barTime, round(open), high, low, close, volume));
            currentPrice = close;
        }

        lastPrices.put(symbol, currentPrice);
        return bars;
    }
    
    /**
     * 暫停即時數據生成
     */
    @Override
    public void pause() {
        paused = true;
        System.out.println("[SimulatorFeed] 數據模擬已暫停");
    }
    
    /**
     * 恢復即時數據生成
     */
    @Override
    public void resume() {
        paused = false;
        System.out.println("[SimulatorFeed] 數據模擬已恢復");
    }
    
    /**
     * 獲取當前暫停狀態
     */
    @Override
    public boolean isPaused() {
        return paused;
    }
}

