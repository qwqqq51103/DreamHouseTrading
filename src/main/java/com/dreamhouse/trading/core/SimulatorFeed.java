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
    
    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        if (!lastPrices.containsKey(symbol)) {
            lastPrices.put(symbol, 100.0 + random.nextDouble() * 50);
            initializeOrderBook(symbol);
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

        // 為每個商品生成獨立的基準價格（忽略已有的lastPrices）
        double basePrice = 100.0 + random.nextDouble() * 50;
        lastPrices.put(symbol, basePrice);
        System.out.println("[SimulatorFeed] " + symbol + " 基準價格: " + basePrice);

        LocalDateTime startTime = LocalDateTime.now().minusMinutes(50);
        double currentPrice = basePrice - 5.0 + random.nextDouble() * 10.0; // 起始價格

        // 生成 50 根歷史 K 線
        for (int i = 0; i < 50; i++) {
            LocalDateTime barTime = startTime.plusMinutes(i);

            // 模擬K線的開高低收
            double open = currentPrice;
            double change = (random.nextDouble() - 0.5) * 2.0; // ±1.0 的變化
            double close = round(open + change);

            // 高低價
            double high = round(Math.max(open, close) + random.nextDouble() * 0.5);
            double low = round(Math.min(open, close) - random.nextDouble() * 0.5);

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
        
        // 5 檔 ASK
        for (int i = 0; i < 5; i++) {
            double price = round(midPrice + (i + 1) * 0.2);
            long qty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.ASK, price, qty, i));
        }
        
        // 5 檔 BID
        for (int i = 0; i < 5; i++) {
            double price = round(midPrice - (i + 1) * 0.2);
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
            
            // 生成新價格（隨機遊走）
            double change = (random.nextDouble() - 0.5) * 0.5;
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

