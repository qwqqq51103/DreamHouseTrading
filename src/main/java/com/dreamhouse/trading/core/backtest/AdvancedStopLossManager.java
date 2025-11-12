package com.dreamhouse.trading.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * 高級停損管理器
 * 支援多種停損類型：移動止損、時間止損、波動率調整止損
 */
public class AdvancedStopLossManager {
    
    private final Map<String, StopLossConfig> configurations = new HashMap<>();
    private final Map<String, PositionStopLoss> activeStops = new HashMap<>();
    
    /**
     * 停損配置
     */
    public static class StopLossConfig {
        // 基本停損
        private double fixedStopLossPercent = 0.05;    // 固定停損 5%
        private double takeProfitPercent = 0.10;       // 固定停利 10%
        
        // 移動止損配置
        private boolean trailingStopEnabled = false;
        private double trailingStopPercent = 0.03;     // 移動止損 3%
        private double trailingStopActivation = 0.05;  // 達到 5% 盈利後啟動移動止損
        
        // 時間止損配置
        private boolean timeBasedStopEnabled = false;
        private int maxHoldingHours = 72;              // 最大持有 72 小時
        
        // 波動率調整停損配置
        private boolean volatilityAdjustedEnabled = false;
        private double atrMultiplier = 2.0;            // ATR 倍數
        private int atrPeriod = 14;                    // ATR 週期
        
        // 技術指標停損配置
        private boolean technicalStopEnabled = false;
        private int smaBreakPeriod = 20;               // 跌破 SMA(20) 停損
        
        // Getters and Setters
        public double getFixedStopLossPercent() { return fixedStopLossPercent; }
        public void setFixedStopLossPercent(double fixedStopLossPercent) { 
            this.fixedStopLossPercent = fixedStopLossPercent; 
        }
        
        public double getTakeProfitPercent() { return takeProfitPercent; }
        public void setTakeProfitPercent(double takeProfitPercent) { 
            this.takeProfitPercent = takeProfitPercent; 
        }
        
        public boolean isTrailingStopEnabled() { return trailingStopEnabled; }
        public void setTrailingStopEnabled(boolean trailingStopEnabled) { 
            this.trailingStopEnabled = trailingStopEnabled; 
        }
        
        public double getTrailingStopPercent() { return trailingStopPercent; }
        public void setTrailingStopPercent(double trailingStopPercent) { 
            this.trailingStopPercent = trailingStopPercent; 
        }
        
        public double getTrailingStopActivation() { return trailingStopActivation; }
        public void setTrailingStopActivation(double trailingStopActivation) { 
            this.trailingStopActivation = trailingStopActivation; 
        }
        
        public boolean isTimeBasedStopEnabled() { return timeBasedStopEnabled; }
        public void setTimeBasedStopEnabled(boolean timeBasedStopEnabled) { 
            this.timeBasedStopEnabled = timeBasedStopEnabled; 
        }
        
        public int getMaxHoldingHours() { return maxHoldingHours; }
        public void setMaxHoldingHours(int maxHoldingHours) { 
            this.maxHoldingHours = maxHoldingHours; 
        }
        
        public boolean isVolatilityAdjustedEnabled() { return volatilityAdjustedEnabled; }
        public void setVolatilityAdjustedEnabled(boolean volatilityAdjustedEnabled) { 
            this.volatilityAdjustedEnabled = volatilityAdjustedEnabled; 
        }
        
        public double getAtrMultiplier() { return atrMultiplier; }
        public void setAtrMultiplier(double atrMultiplier) { 
            this.atrMultiplier = atrMultiplier; 
        }
        
        public int getAtrPeriod() { return atrPeriod; }
        public void setAtrPeriod(int atrPeriod) { 
            this.atrPeriod = atrPeriod; 
        }
        
        public boolean isTechnicalStopEnabled() { return technicalStopEnabled; }
        public void setTechnicalStopEnabled(boolean technicalStopEnabled) { 
            this.technicalStopEnabled = technicalStopEnabled; 
        }
        
        public int getSmaBreakPeriod() { return smaBreakPeriod; }
        public void setSmaBreakPeriod(int smaBreakPeriod) { 
            this.smaBreakPeriod = smaBreakPeriod; 
        }
    }
    
    /**
     * 持倉停損狀態
     */
    private static class PositionStopLoss {
        private final String symbol;
        private final double entryPrice;
        private final LocalDateTime entryTime;
        private double stopLoss;
        private double takeProfit;
        private double highestPrice;  // 記錄最高價格（用於移動止損）
        private boolean trailingActive = false;
        
        public PositionStopLoss(String symbol, double entryPrice, LocalDateTime entryTime,
                               double stopLoss, double takeProfit) {
            this.symbol = symbol;
            this.entryPrice = entryPrice;
            this.entryTime = entryTime;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.highestPrice = entryPrice;
        }
        
        public void updateTrailingStop(double currentPrice, double trailingPercent, double activation) {
            // 更新最高價格
            if (currentPrice > highestPrice) {
                highestPrice = currentPrice;
            }
            
            // 檢查是否啟動移動止損
            double returnRate = (highestPrice - entryPrice) / entryPrice;
            if (returnRate >= activation) {
                trailingActive = true;
                // 更新停損價格為最高價格的 (1 - trailingPercent)
                double newStopLoss = highestPrice * (1 - trailingPercent);
                if (newStopLoss > stopLoss) {
                    stopLoss = newStopLoss;
                }
            }
        }
    }
    
    /**
     * 設置策略的停損配置
     */
    public void setConfig(String strategyName, StopLossConfig config) {
        configurations.put(strategyName, config);
    }
    
    /**
     * 獲取策略的停損配置
     */
    public StopLossConfig getConfig(String strategyName) {
        return configurations.getOrDefault(strategyName, new StopLossConfig());
    }
    
    /**
     * 開倉時計算停損停利價格
     */
    public StopLossPrices calculateStopLossPrices(String strategyName, String symbol,
                                                   double entryPrice, LocalDateTime entryTime,
                                                   BarSeries barSeries) {
        StopLossConfig config = getConfig(strategyName);

        double stopLoss;
        double takeProfit;

        if (config.isVolatilityAdjustedEnabled() && barSeries != null && barSeries.getBarCount() > config.getAtrPeriod()) {
            // 使用 ATR 計算波動率調整停損
            ATRIndicator atr = new ATRIndicator(barSeries, config.getAtrPeriod());
            double atrValue = atr.getValue(barSeries.getBarCount() - 1).doubleValue();

            stopLoss = entryPrice - (atrValue * config.getAtrMultiplier());
            takeProfit = entryPrice + (atrValue * config.getAtrMultiplier() * 2); // 停利為停損的兩倍
        } else {
            // 使用固定百分比
            stopLoss = entryPrice * (1 - config.getFixedStopLossPercent());
            takeProfit = entryPrice * (1 + config.getTakeProfitPercent());
        }

        // 創建持倉停損狀態
        String key = strategyName + "_" + symbol;
        activeStops.put(key, new PositionStopLoss(symbol, entryPrice, entryTime, stopLoss, takeProfit));

        return new StopLossPrices(stopLoss, takeProfit);
    }

    /**
     * 手動設定持倉的停損停利（供外部調用）
     * 用於當停損停利已由外部計算好時，直接設定到管理器中
     */
    public void setPositionStop(String strategyName, String symbol,
                               double entryPrice, LocalDateTime entryTime,
                               double stopLoss, double takeProfit) {
        String key = strategyName + "_" + symbol;
        PositionStopLoss posStop = new PositionStopLoss(symbol, entryPrice, entryTime,
                                                        stopLoss, takeProfit);
        activeStops.put(key, posStop);

        System.out.println(String.format("[StopManager] 設定停損停利：%s, 進場:%.2f, 停損:%.2f, 停利:%.2f",
                symbol, entryPrice, stopLoss, takeProfit));
    }
    
    /**
     * 檢查是否應該觸發停損或停利
     */
    public StopTrigger checkStopTrigger(String strategyName, String symbol, double currentPrice,
                                       LocalDateTime currentTime, BarSeries barSeries) {
        String key = strategyName + "_" + symbol;
        PositionStopLoss posStop = activeStops.get(key);
        
        if (posStop == null) {
            return null;
        }
        
        StopLossConfig config = getConfig(strategyName);

        // 先更新移動止損（如果啟用）
        if (config.isTrailingStopEnabled()) {
            posStop.updateTrailingStop(currentPrice, config.getTrailingStopPercent(),
                                      config.getTrailingStopActivation());
        }

        // 1. 檢查固定停損（但如果移動止損已激活，則跳過固定停損檢查）
        if (!posStop.trailingActive && currentPrice <= posStop.stopLoss) {
            removeStop(strategyName, symbol);
            return new StopTrigger(StopTriggerType.STOP_LOSS, posStop.stopLoss, "固定停損");
        }

        // 2. 檢查固定停利
        if (currentPrice >= posStop.takeProfit) {
            removeStop(strategyName, symbol);
            return new StopTrigger(StopTriggerType.TAKE_PROFIT, posStop.takeProfit, "固定停利");
        }

        // 3. 檢查移動止損
        if (config.isTrailingStopEnabled() && posStop.trailingActive && currentPrice <= posStop.stopLoss) {
            removeStop(strategyName, symbol);
            return new StopTrigger(StopTriggerType.TRAILING_STOP, posStop.stopLoss, "移動止損");
        }
        
        // 4. 檢查時間止損
        if (config.isTimeBasedStopEnabled()) {
            long hoursSinceEntry = ChronoUnit.HOURS.between(posStop.entryTime, currentTime);
            if (hoursSinceEntry >= config.getMaxHoldingHours()) {
                removeStop(strategyName, symbol);
                return new StopTrigger(StopTriggerType.TIME_BASED, currentPrice, 
                                      "時間止損 (" + hoursSinceEntry + "小時)");
            }
        }
        
        // 5. 檢查技術指標停損 (跌破均線)
        if (config.isTechnicalStopEnabled() && barSeries != null && 
            barSeries.getBarCount() > config.getSmaBreakPeriod()) {
            
            SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 
                                               config.getSmaBreakPeriod());
            double smaValue = sma.getValue(barSeries.getBarCount() - 1).doubleValue();
            
            if (currentPrice < smaValue && posStop.entryPrice > smaValue) {
                removeStop(strategyName, symbol);
                return new StopTrigger(StopTriggerType.TECHNICAL_STOP, currentPrice, 
                                      "跌破SMA(" + config.getSmaBreakPeriod() + ")");
            }
        }
        
        return null;
    }
    
    /**
     * 移除停損記錄（平倉時調用）
     */
    public void removeStop(String strategyName, String symbol) {
        String key = strategyName + "_" + symbol;
        activeStops.remove(key);
    }
    
    /**
     * 停損價格
     */
    public static class StopLossPrices {
        private final double stopLoss;
        private final double takeProfit;
        
        public StopLossPrices(double stopLoss, double takeProfit) {
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
        
        public double getStopLoss() { return stopLoss; }
        public double getTakeProfit() { return takeProfit; }
    }
    
    /**
     * 停損觸發類型
     */
    public enum StopTriggerType {
        STOP_LOSS,      // 固定停損
        TAKE_PROFIT,    // 固定停利
        TRAILING_STOP,  // 移動止損
        TIME_BASED,     // 時間止損
        TECHNICAL_STOP  // 技術指標止損
    }
    
    /**
     * 停損觸發結果
     */
    public static class StopTrigger {
        private final StopTriggerType type;
        private final double price;
        private final String reason;
        
        public StopTrigger(StopTriggerType type, double price, String reason) {
            this.type = type;
            this.price = price;
            this.reason = reason;
        }
        
        public StopTriggerType getType() { return type; }
        public double getPrice() { return price; }
        public String getReason() { return reason; }
    }
}

