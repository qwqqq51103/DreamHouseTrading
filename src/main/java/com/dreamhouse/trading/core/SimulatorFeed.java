package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

public class SimulatorFeed implements MarketDataFeed {
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final Random random = new Random();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, List<DepthLevel>> orderBooks = new ConcurrentHashMap<>();
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
        }
    }
    
    @Override
    public void start() {
        connected = true;
        
        // 在背景執行緒中生成歷史數據，避免阻塞 EDT
        new Thread(() -> {
            generateHistoricalData();
            
            // 歷史數據生成完畢後，開始即時數據更新
            marketDataTask = executor.scheduleAtFixedRate(this::generateMarketData, 0, 1000, TimeUnit.MILLISECONDS);
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
     * 為特定商品生成歷史數據
     */
    public void generateHistoricalDataForSymbol(String symbol) {
        System.out.println("[SimulatorFeed] 開始為商品 " + symbol + " 生成歷史數據");

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null || symbolListeners.isEmpty()) {
            System.err.println("[SimulatorFeed] 錯誤：找不到商品 " + symbol + " 的監聽器");
            return;
        }

        System.out.println("[SimulatorFeed] 找到 " + symbolListeners.size() + " 個監聽器");

        // 為每個商品生成獨立且合理的基準價格
        double basePrice = getReasonableBasePrice(symbol);
        lastPrices.put(symbol, basePrice);
        System.out.println("[SimulatorFeed] " + symbol + " 基準價格: " + basePrice);

        LocalDateTime startTime = LocalDateTime.now().minusMinutes(50);
        // 起始價格在基準價格附近浮動（±5%）
        double priceVariation = basePrice * 0.05;
        double currentPrice = basePrice - priceVariation + random.nextDouble() * (priceVariation * 2);

        // 生成 50 根歷史 K 線
        for (int i = 0; i < 50; i++) {
            LocalDateTime barTime = startTime.plusMinutes(i);

            // 模擬K線的開高低收
            double open = currentPrice;
            // 價格變化幅度為當前價格的±1%
            double maxChange = currentPrice * 0.01;
            double change = (random.nextDouble() - 0.5) * 2.0 * maxChange;
            double close = round(open + change);

            // 高低價（最大波動0.5%）
            double maxWick = currentPrice * 0.005;
            double high = round(Math.max(open, close) + random.nextDouble() * maxWick);
            double low = round(Math.min(open, close) - random.nextDouble() * maxWick);

            // 成交量
            long volume = 5000 + random.nextInt(15000);

            // 生成這一分鐘內的 Tick 數據
            // 為了模擬K線，我們在每分鐘發送 4 個 tick
            for (int j = 0; j < 4; j++) {
                LocalDateTime tickTime = barTime.plusSeconds(j * 15);
                double tickPrice;

                if (j == 0) {
                    tickPrice = open;
                } else if (j == 3) {
                    tickPrice = close;
                } else {
                    // 中間的 tick 在 low 和 high 之間
                    tickPrice = round(low + random.nextDouble() * (high - low));
                }

                long tickVolume = volume / 4;
                Tick tick = new Tick(symbol, tickTime, tickPrice, tickVolume);

                // 在 Swing 執行緒中通知
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                    }
                });

                // 每個 tick 之間暫停一小段時間，讓UI有時間更新
                if (i < 49 || j < 3) { // 最後一個不暫停
                    try {
                        Thread.sleep(10); // 10ms，總共約 2 秒完成
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            // 更新當前價格為本K線的收盤價
            currentPrice = close;
        }

        // 更新最後價格
        lastPrices.put(symbol, currentPrice);

        System.out.println("[SimulatorFeed] " + symbol + " 歷史數據生成完成，共 50 根K線，最終價格: " + currentPrice);
    }
    
    @Override
    public void stop() {
        connected = false;
        executor.shutdown();
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
            double maxChange = lastPrice * 0.002;
            double change = (random.nextDouble() - 0.5) * maxChange;
            double newPrice = round(Math.max(1.0, lastPrice + change));
            lastPrices.put(symbol, newPrice);
            
            LocalDateTime now = LocalDateTime.now();
            
            // 生成 Tick
            Tick tick = new Tick(symbol, now, newPrice, 100 + random.nextInt(500));
            
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

