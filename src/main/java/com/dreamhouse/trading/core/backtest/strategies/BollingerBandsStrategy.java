package com.dreamhouse.trading.core.backtest.strategies;

import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

/**
 * 布林通道策略
 * 基於布林通道的突破和回歸信號進行交易
 */
public class BollingerBandsStrategy extends BaseStrategy {
    
    private ClosePriceIndicator closePrice;
    private SMAIndicator sma;
    private StandardDeviationIndicator stdDev;
    private BollingerBandsUpperIndicator upperBand;
    private BollingerBandsMiddleIndicator middleBand;
    private BollingerBandsLowerIndicator lowerBand;
    
    private int period;
    private double multiplier;
    private String symbol;
    private int maxPosition;
    private String strategy; // "breakout" 或 "reversion"
    
    /**
     * 構造函數
     */
    public BollingerBandsStrategy() {
        super("布林通道策略", "基於布林通道的突破或均值回歸信號進行交易");
    }
    
    @Override
    protected void initializeDefaultConfig() {
        config.setParameter("period", 20);
        config.setParameter("multiplier", 2.0);
        config.setParameter("symbol", "STOCK");
        config.setParameter("maxPosition", 1000);
        config.setParameter("strategy", "reversion"); // "breakout" 或 "reversion"
        config.setParameter("debug", false);
    }
    
    @Override
    protected void onInitialize() {
        // 讀取配置參數
        period = config.getIntParameter("period", 20);
        multiplier = config.getDoubleParameter("multiplier", 2.0);
        symbol = config.getStringParameter("symbol", "STOCK");
        maxPosition = config.getIntParameter("maxPosition", 1000);
        strategy = config.getStringParameter("strategy", "reversion");
        
        // 初始化技術指標
        closePrice = new ClosePriceIndicator(barSeries);
        sma = new SMAIndicator(closePrice, period);
        stdDev = new StandardDeviationIndicator(closePrice, period);
        
        middleBand = new BollingerBandsMiddleIndicator(sma);
        upperBand = new BollingerBandsUpperIndicator(middleBand, stdDev, barSeries.numOf(multiplier));
        lowerBand = new BollingerBandsLowerIndicator(middleBand, stdDev, barSeries.numOf(multiplier));
        
        log(String.format("布林通道策略初始化完成 - 週期: %d, 倍數: %.1f, 策略: %s", 
                         period, multiplier, strategy));
    }
    
    @Override
    public void onBar(int barIndex, Bar bar) {
        // 需要足夠的數據才能計算布林通道
        if (barIndex < period) {
            return;
        }
        
        double currentPrice = bar.getClosePrice().doubleValue();
        double upper = upperBand.getValue(barIndex).doubleValue();
        double middle = middleBand.getValue(barIndex).doubleValue();
        double lower = lowerBand.getValue(barIndex).doubleValue();
        
        // 計算價格在通道中的位置 (0-1)
        double bandPosition = (currentPrice - lower) / (upper - lower);
        
        debug(String.format("Bar %d: Price=%.2f, Upper=%.2f, Middle=%.2f, Lower=%.2f, Position=%.2f", 
                           barIndex, currentPrice, upper, middle, lower, bandPosition));
        
        if ("breakout".equals(strategy)) {
            handleBreakoutStrategy(currentPrice, upper, middle, lower, bandPosition);
        } else {
            handleReversionStrategy(currentPrice, upper, middle, lower, bandPosition);
        }
    }
    
    /**
     * 突破策略邏輯
     */
    private void handleBreakoutStrategy(double price, double upper, double middle, double lower, double bandPosition) {
        // 突破上軌買入
        if (shouldBuyBreakout(price, upper, bandPosition)) {
            executeBuy(price, "突破上軌");
        }
        // 跌破下軌賣出（如果有持倉）
        else if (shouldSellBreakout(price, lower, bandPosition)) {
            executeSell(price, "跌破下軌");
        }
        // 回到中軌附近止盈
        else if (hasPosition(symbol) && Math.abs(bandPosition - 0.5) < 0.1) {
            executeSell(price, "回歸中軌");
        }
    }
    
    /**
     * 均值回歸策略邏輯
     */
    private void handleReversionStrategy(double price, double upper, double middle, double lower, double bandPosition) {
        // 觸及下軌買入（超賣反彈）
        if (shouldBuyReversion(price, lower, bandPosition)) {
            executeBuy(price, "下軌反彈");
        }
        // 觸及上軌賣出（超買回調）
        else if (shouldSellReversion(price, upper, bandPosition)) {
            executeSell(price, "上軌回調");
        }
        // 回到中軌止盈
        else if (hasPosition(symbol) && Math.abs(price - middle) / middle < 0.005) {
            executeSell(price, "回歸中軌");
        }
    }
    
    /**
     * 突破策略買入條件
     */
    private boolean shouldBuyBreakout(double price, double upper, double bandPosition) {
        return !hasPosition(symbol) && price > upper && bandPosition > 1.02;
    }
    
    /**
     * 突破策略賣出條件
     */
    private boolean shouldSellBreakout(double price, double lower, double bandPosition) {
        return hasPosition(symbol) && (price < lower || bandPosition < -0.02);
    }
    
    /**
     * 均值回歸策略買入條件
     */
    private boolean shouldBuyReversion(double price, double lower, double bandPosition) {
        return !hasPosition(symbol) && bandPosition < 0.1 && price <= lower * 1.005;
    }
    
    /**
     * 均值回歸策略賣出條件
     */
    private boolean shouldSellReversion(double price, double upper, double bandPosition) {
        return hasPosition(symbol) && (bandPosition > 0.9 || price >= upper * 0.995);
    }
    
    /**
     * 執行買入操作
     */
    private void executeBuy(double price, String reason) {
        Portfolio portfolio = getPortfolio();
        if (portfolio == null) return;
        
        double availableCash = portfolio.getCash();
        
        // 根據通道寬度調整倉位（通道越窄，倉位越重）
        int lastIndex = barSeries.getBarCount() - 1;
        double upper = upperBand.getValue(lastIndex).doubleValue();
        double lower = lowerBand.getValue(lastIndex).doubleValue();
        double bandWidth = (upper - lower) / price;
        
        double positionRatio = Math.min(0.8, Math.max(0.3, 1.0 - bandWidth * 10));
        int quantity = Math.min(maxPosition, (int) (availableCash * positionRatio / (price * 1.002)));
        
        if (quantity > 0) {
            // 設定停利停損
            double stopLoss = price * 0.96;     // 停損 4%
            double takeProfit = price * 1.06;   // 停利 6%
            
            if (buyWithStops(symbol, quantity, stopLoss, takeProfit, "布林" + reason)) {
                log(String.format("布林通道買入 (%s) - 通道寬度: %.2f%%, 倉位: %.1f%%, 數量: %d, 價格: %.2f, 停損: %.2f, 停利: %.2f", 
                                 reason, bandWidth * 100, positionRatio * 100, quantity, price, stopLoss, takeProfit));
            }
        }
    }
    
    /**
     * 執行賣出操作
     */
    private void executeSell(double price, String reason) {
        int currentQuantity = getPositionQuantity(symbol);
        
        if (currentQuantity > 0) {
            Portfolio portfolio = getPortfolio();
            double profit = 0.0;
            if (portfolio != null) {
                Position position = portfolio.getPosition(symbol);
                if (position != null) {
                    profit = position.getReturnRate(price) * 100;
                }
            }
            
            if (sellWithReason(symbol, currentQuantity, "布林" + reason)) {
                log(String.format("布林通道賣出 (%s) - 收益: %.2f%%, 數量: %d, 價格: %.2f", 
                                 reason, profit, currentQuantity, price));
            }
        }
    }
    
    @Override
    protected void onCleanup() {
        // 平倉所有持倉
        int remainingQuantity = getPositionQuantity(symbol);
        if (remainingQuantity > 0) {
            sell(symbol, remainingQuantity);
            log("布林通道策略結束，平倉所有持倉");
        }
    }
    
    /**
     * 獲取策略狀態描述
     */
    public String getStatusDescription() {
        if (barSeries == null || barSeries.getBarCount() < period) {
            return "等待足夠數據...";
        }
        
        int lastIndex = barSeries.getBarCount() - 1;
        double currentPrice = getCurrentPrice();
        double upper = upperBand.getValue(lastIndex).doubleValue();
        double middle = middleBand.getValue(lastIndex).doubleValue();
        double lower = lowerBand.getValue(lastIndex).doubleValue();
        
        double bandPosition = (currentPrice - lower) / (upper - lower);
        int position = getPositionQuantity(symbol);
        
        String zone = "中軌";
        if (bandPosition > 0.8) zone = "上軌";
        else if (bandPosition < 0.2) zone = "下軌";
        
        return String.format("價格: %.2f (%s), 通道位置: %.1f%%, 策略: %s, 持倉: %d", 
                           currentPrice, zone, bandPosition * 100, strategy, position);
    }
}
