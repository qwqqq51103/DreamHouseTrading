package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.model.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回測引擎核心類
 * 負責執行交易策略的歷史回測
 */
public class BacktestEngine {
    
    private final BarSeries barSeries;
    private final Portfolio portfolio;
    private final List<Strategy> strategies;
    private final List<BacktestListener> listeners;
    
    // 回測參數
    private double initialCapital = 100000.0; // 初始資金
    private double commission = 0.001; // 手續費率 (0.1%)
    private double slippage = 0.0005; // 滑點 (0.05%)
    
    // 回測狀態
    private boolean isRunning = false;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BacktestResult result;
    private int currentBarIndex = 0;  // 當前處理的K線索引
    
    /**
     * 構造函數
     */
    public BacktestEngine() {
        this.barSeries = new BaseBarSeries();
        this.portfolio = new Portfolio(initialCapital);
        this.strategies = new ArrayList<>();
        this.listeners = new ArrayList<>();
    }
    
    /**
     * 設定回測數據
     */
    public void setData(List<Bar> bars) {
        // 創建新的 BarSeries
        BarSeries newBarSeries = new BaseBarSeries();
        newBarSeries.setMaximumBarCount(Integer.MAX_VALUE);
        
        // 添加數據
        for (Bar bar : bars) {
            org.ta4j.core.Bar ta4jBar = org.ta4j.core.BaseBar.builder()
                .timePeriod(java.time.Duration.ofMinutes(1)) // 假設1分鐘K線
                .endTime(bar.getTimestamp().atZone(java.time.ZoneId.systemDefault()))
                .openPrice(newBarSeries.numOf(bar.getOpen()))
                .highPrice(newBarSeries.numOf(bar.getHigh()))
                .lowPrice(newBarSeries.numOf(bar.getLow()))
                .closePrice(newBarSeries.numOf(bar.getClose()))
                .volume(newBarSeries.numOf(bar.getVolume()))
                .build();
            
            newBarSeries.addBar(ta4jBar);
        }
        
        // 替換現有數據
        while (barSeries.getBarCount() > 0) {
            // ta4j 0.15 沒有 removeBar 方法，我們重新創建 BarSeries
        }
        
        // 將新數據複製到現有 BarSeries
        for (int i = 0; i < newBarSeries.getBarCount(); i++) {
            barSeries.addBar(newBarSeries.getBar(i));
        }
        
        if (!bars.isEmpty()) {
            startDate = bars.get(0).getTimestamp();
            endDate = bars.get(bars.size() - 1).getTimestamp();
        }
    }
    
    /**
     * 添加交易策略
     */
    public void addStrategy(Strategy strategy) {
        strategies.add(strategy);
        strategy.setEngine(this);
    }
    
    /**
     * 移除交易策略
     */
    public void removeStrategy(Strategy strategy) {
        strategies.remove(strategy);
    }
    
    /**
     * 添加回測監聽器
     */
    public void addListener(BacktestListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 執行回測
     */
    public BacktestResult runBacktest() {
        if (barSeries.getBarCount() == 0) {
            throw new IllegalStateException("No data available for backtest");
        }
        
        if (strategies.isEmpty()) {
            throw new IllegalStateException("No strategies added for backtest");
        }
        
        isRunning = true;
        result = new BacktestResult(startDate, endDate, initialCapital);
        
        // 重置投資組合
        portfolio.reset(initialCapital);
        
        // 初始化策略
        for (Strategy strategy : strategies) {
            strategy.initialize(barSeries);
        }
        
        // 通知開始
        System.out.println("回測開始，數據量: " + barSeries.getBarCount() + " 根K線");
        notifyBacktestStarted();
        
        try {
            // 逐根K線執行回測
            for (int i = 0; i < barSeries.getBarCount() && isRunning; i++) {
                currentBarIndex = i;  // 更新當前K線索引
                processBar(i);
                
                // 通知進度
                double progress = (double) (i + 1) / barSeries.getBarCount();
                notifyProgressUpdate(progress);
                
                // 添加小延遲讓 UI 有時間更新，並檢查停止狀態
                if (i % 5 == 0) { // 每5根K線輸出一次進度和檢查停止
                    System.out.println("回測進度: " + String.format("%.1f%%", progress * 100) + 
                                     ", 運行狀態: " + isRunning);
                    try {
                        Thread.sleep(10); // 10ms 延遲讓 UI 更新
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        isRunning = false;
                        break;
                    }
                }
            }
            
            // 完成回測
            finishBacktest();
            
        } catch (Exception e) {
            isRunning = false;
            throw new RuntimeException("Backtest execution failed", e);
        }
        
        return result;
    }
    
    /**
     * 處理單根K線
     */
    private void processBar(int barIndex) {
        org.ta4j.core.Bar currentBar = barSeries.getBar(barIndex);
        LocalDateTime timestamp = currentBar.getBeginTime().toLocalDateTime();

        // 更新投資組合市值
        portfolio.updateMarketValue(currentBar.getClosePrice().doubleValue());

        // 執行策略邏輯
        for (Strategy strategy : strategies) {
            try {
                strategy.onBar(barIndex, currentBar);
            } catch (Exception e) {
                System.err.println("Strategy execution error at bar " + barIndex + ": " + e.getMessage());
            }
        }

        // 記錄快照
        result.addSnapshot(timestamp, portfolio.getTotalValue(), portfolio.getCash(),
                          portfolio.getPositionValue(), portfolio.getPositions().size());

        // 通知監聽器 K 線已處理
        notifyBarProcessed(barIndex, currentBar);
    }
    
    /**
     * 完成回測
     */
    private void finishBacktest() {
        isRunning = false;
        
        // 平倉所有持倉
        portfolio.closeAllPositions(barSeries.getLastBar().getClosePrice().doubleValue());
        
        // 計算最終結果
        result.calculate();
        
        // 通知完成
        System.out.println("回測完成，總交易次數: " + result.getTrades().size());
        notifyBacktestCompleted(result);
    }
    
    /**
     * 停止回測
     */
    public void stopBacktest() {
        System.out.println("收到停止回測請求");
        isRunning = false;
        System.out.println("回測狀態設為停止: " + isRunning);
    }
    
    /**
     * 執行買入訂單
     */
    public boolean buy(String symbol, int quantity, OrderType orderType) {
        if (!isRunning) return false;
        
        double price = getCurrentPrice();
        double totalCost = price * quantity * (1 + commission + slippage);
        
        if (portfolio.getCash() >= totalCost) {
            portfolio.addPosition(symbol, quantity, price, commission + slippage);
            
            // 記錄交易
            result.addTrade(new Trade(getCurrentTimestamp(), symbol, TradeType.BUY, 
                           quantity, price, commission + slippage));
            
            notifyTradeExecuted(symbol, TradeType.BUY, quantity, price);
            return true;
        }
        
        return false;
    }
    
    /**
     * 執行賣出訂單
     */
    public boolean sell(String symbol, int quantity, OrderType orderType) {
        if (!isRunning) return false;
        
        Position position = portfolio.getPosition(symbol);
        if (position != null && position.getQuantity() >= quantity) {
            double price = getCurrentPrice();
            
            portfolio.reducePosition(symbol, quantity, price, commission + slippage);
            
            // 記錄交易
            result.addTrade(new Trade(getCurrentTimestamp(), symbol, TradeType.SELL, 
                           quantity, price, commission + slippage));
            
            notifyTradeExecuted(symbol, TradeType.SELL, quantity, price);
            return true;
        }
        
        return false;
    }
    
    /**
     * 執行買入訂單 (帶停利停損)
     */
    public boolean buyWithStops(String symbol, int quantity, OrderType orderType, 
                               Double stopLoss, Double takeProfit, String reason) {
        if (!isRunning) return false;
        
        double price = getCurrentPrice();
        double totalCost = price * quantity * (1 + commission + slippage);
        
        if (portfolio.getCash() >= totalCost) {
            portfolio.addPosition(symbol, quantity, price, commission + slippage);
            
            // 記錄交易 (帶停利停損資訊)
            result.addTrade(new Trade(getCurrentTimestamp(), symbol, TradeType.BUY, 
                           quantity, price, commission + slippage, stopLoss, takeProfit, reason));
            
            notifyTradeExecuted(symbol, TradeType.BUY, quantity, price);
            return true;
        }
        
        return false;
    }
    
    /**
     * 執行賣出訂單 (帶出場原因)
     */
    public boolean sellWithReason(String symbol, int quantity, OrderType orderType, String reason) {
        if (!isRunning) return false;
        
        Position position = portfolio.getPosition(symbol);
        if (position != null && position.getQuantity() >= quantity) {
            double price = getCurrentPrice();
            
            portfolio.reducePosition(symbol, quantity, price, commission + slippage);
            
            // 記錄交易 (帶出場原因)
            result.addTrade(new Trade(getCurrentTimestamp(), symbol, TradeType.SELL, 
                           quantity, price, commission + slippage, null, null, reason));
            
            notifyTradeExecuted(symbol, TradeType.SELL, quantity, price);
            return true;
        }
        
        return false;
    }
    
    // 輔助方法
    private double getCurrentPrice() {
        // 使用當前正在處理的K線價格，而不是最後一根
        if (currentBarIndex >= 0 && currentBarIndex < barSeries.getBarCount()) {
            return barSeries.getBar(currentBarIndex).getClosePrice().doubleValue();
        }
        return barSeries.getLastBar().getClosePrice().doubleValue();
    }
    
    private LocalDateTime getCurrentTimestamp() {
        // 使用當前正在處理的K線時間，而不是最後一根
        if (currentBarIndex >= 0 && currentBarIndex < barSeries.getBarCount()) {
            return barSeries.getBar(currentBarIndex).getBeginTime().toLocalDateTime();
        }
        return barSeries.getLastBar().getBeginTime().toLocalDateTime();
    }
    
    // 通知方法
    private void notifyBacktestStarted() {
        for (BacktestListener listener : listeners) {
            listener.onBacktestStarted();
        }
    }

    private void notifyProgressUpdate(double progress) {
        for (BacktestListener listener : listeners) {
            listener.onProgressUpdate(progress);
        }
    }

    private void notifyBarProcessed(int barIndex, org.ta4j.core.Bar bar) {
        for (BacktestListener listener : listeners) {
            listener.onBarProcessed(barIndex, bar);
        }
    }

    private void notifyTradeExecuted(String symbol, TradeType type, int quantity, double price) {
        for (BacktestListener listener : listeners) {
            listener.onTradeExecuted(symbol, type, quantity, price);
        }
    }

    private void notifyBacktestCompleted(BacktestResult result) {
        for (BacktestListener listener : listeners) {
            listener.onBacktestCompleted(result);
        }
    }
    
    // Getters and Setters
    public BarSeries getBarSeries() { return barSeries; }
    public Portfolio getPortfolio() { return portfolio; }
    public boolean isRunning() { return isRunning; }
    public BacktestResult getResult() { return result; }
    
    public void setInitialCapital(double initialCapital) {
        this.initialCapital = initialCapital;
    }
    
    public void setCommission(double commission) {
        this.commission = commission;
    }
    
    public void setSlippage(double slippage) {
        this.slippage = slippage;
    }
}
