package com.dreamhouse.trading.core.backtest.strategies;

import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * MACD 策略
 * 基於 MACD 指標的金叉死叉信號進行交易
 */
public class MACDStrategy extends BaseStrategy {
    
    private MACDIndicator macd;
    private EMAIndicator macdSignal;
    private ClosePriceIndicator closePrice;
    
    private int fastPeriod;
    private int slowPeriod;
    private int signalPeriod;
    private String symbol;
    private int maxPosition;
    
    // 記錄前一根K線的 MACD 值，用於判斷交叉
    private double prevMACD = 0.0;
    private double prevSignal = 0.0;
    
    /**
     * 構造函數
     */
    public MACDStrategy() {
        super("MACD 策略", "基於 MACD 指標的金叉死叉信號進行交易");
    }
    
    @Override
    protected void initializeDefaultConfig() {
        config.setParameter("fastPeriod", 12);
        config.setParameter("slowPeriod", 26);
        config.setParameter("signalPeriod", 9);
        config.setParameter("symbol", "STOCK");
        config.setParameter("maxPosition", 1000);
        config.setParameter("debug", false);
    }
    
    @Override
    protected void onInitialize() {
        // 讀取配置參數
        fastPeriod = config.getIntParameter("fastPeriod", 12);
        slowPeriod = config.getIntParameter("slowPeriod", 26);
        signalPeriod = config.getIntParameter("signalPeriod", 9);
        symbol = config.getStringParameter("symbol", "STOCK");
        maxPosition = config.getIntParameter("maxPosition", 1000);
        
        // 初始化技術指標
        closePrice = new ClosePriceIndicator(barSeries);
        macd = new MACDIndicator(closePrice, fastPeriod, slowPeriod);
        macdSignal = new EMAIndicator(macd, signalPeriod);
        
        log(String.format("MACD 策略初始化完成 - 快線: %d, 慢線: %d, 信號線: %d", 
                         fastPeriod, slowPeriod, signalPeriod));
    }
    
    @Override
    public void onBar(int barIndex, Bar bar) {
        // 需要足夠的數據才能計算 MACD
        int requiredBars = slowPeriod + signalPeriod;
        if (barIndex < requiredBars) {
            return;
        }
        
        double currentPrice = bar.getClosePrice().doubleValue();
        double currentMACD = macd.getValue(barIndex).doubleValue();
        double currentSignal = macdSignal.getValue(barIndex).doubleValue();
        double histogram = currentMACD - currentSignal;
        
        debug(String.format("Bar %d: Price=%.2f, MACD=%.4f, Signal=%.4f, Histogram=%.4f", 
                           barIndex, currentPrice, currentMACD, currentSignal, histogram));
        
        // 檢查買入信號：MACD 金叉
        if (shouldBuy(currentMACD, currentSignal)) {
            executeBuy(currentPrice, histogram);
        }
        // 檢查賣出信號：MACD 死叉或止損
        else if (shouldSell(currentMACD, currentSignal)) {
            executeSell(currentPrice, histogram);
        }
        
        // 更新前一根K線的值
        prevMACD = currentMACD;
        prevSignal = currentSignal;
    }
    
    /**
     * 判斷是否應該買入
     */
    private boolean shouldBuy(double currentMACD, double currentSignal) {
        if (!hasPosition(symbol)) {
            // 方式1：MACD 金叉（精確穿越）
            if (prevMACD <= prevSignal && currentMACD > currentSignal) {
                boolean trendStrong = currentMACD > -0.001;
                if (trendStrong) {
                    System.out.printf("[MACD] 買入信號(金叉) - MACD: %.4f > Signal: %.4f%n",
                                     currentMACD, currentSignal);
                    return true;
                }
            }

            // 方式2：MACD 在信號線上方且柱狀圖擴大（寬鬆條件）
            if (currentMACD > currentSignal) {
                double histogram = currentMACD - currentSignal;
                double prevHistogram = prevMACD - prevSignal;
                // 柱狀圖擴大且為正值
                if (histogram > 0 && histogram > prevHistogram && histogram > 0.0001) {
                    System.out.printf("[MACD] 買入信號(趨勢) - MACD: %.4f, Signal: %.4f, Histogram: %.4f%n",
                                     currentMACD, currentSignal, histogram);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 判斷是否應該賣出
     */
    private boolean shouldSell(double currentMACD, double currentSignal) {
        if (!hasPosition(symbol)) {
            return false;
        }

        // 方式1：MACD 死叉（精確穿越）
        if (prevMACD >= prevSignal && currentMACD < currentSignal) {
            System.out.printf("[MACD] 賣出信號(死叉) - MACD: %.4f < Signal: %.4f%n",
                             currentMACD, currentSignal);
            return true;
        }

        // 方式2：MACD 在信號線下方（趨勢反轉）
        if (currentMACD < currentSignal) {
            double histogram = currentMACD - currentSignal;
            if (histogram < -0.0001) { // 柱狀圖為負
                System.out.printf("[MACD] 賣出信號(反轉) - MACD: %.4f, Signal: %.4f, Histogram: %.4f%n",
                                 currentMACD, currentSignal, histogram);
                return true;
            }
        }

        // 方式3：獲利了結 - 持倉獲利且 MACD 開始收斂
        Portfolio portfolio = getPortfolio();
        if (portfolio != null) {
            Position position = portfolio.getPosition(symbol);
            if (position != null) {
                double currentPrice = getCurrentPrice();
                double returnRate = position.getReturnRate(currentPrice);
                double histogram = currentMACD - currentSignal;
                double prevHistogram = prevMACD - prevSignal;

                // 獲利 2% 以上且柱狀圖開始縮小
                if (returnRate > 0.02 && histogram < prevHistogram && histogram > 0) {
                    System.out.printf("[MACD] 賣出信號(獲利) - 獲利: %.2f%%, Histogram縮小: %.4f -> %.4f%n",
                                     returnRate * 100, prevHistogram, histogram);
                    return true;
                }

                // 止損：虧損超過 3%
                if (returnRate < -0.03) {
                    System.out.printf("[MACD] 賣出信號(止損) - 虧損: %.2f%%%n", returnRate * 100);
                    return true;
                }
            }
        }

        return false;
    }
    
    /**
     * 執行買入操作
     */
    private void executeBuy(double price, double histogram) {
        Portfolio portfolio = getPortfolio();
        if (portfolio == null) return;
        
        double availableCash = portfolio.getCash();
        
        // 根據 MACD 強度調整倉位（histogram 越大，倉位越重）
        double positionRatio = Math.min(0.8, Math.max(0.3, Math.abs(histogram) * 1000));
        int quantity = Math.min(maxPosition, (int) (availableCash * positionRatio / (price * 1.002)));
        
        if (quantity > 0) {
            // 設定停利停損
            double stopLoss = price * 0.94;     // 停損 6%
            double takeProfit = price * 1.12;   // 停利 12%
            
            if (buyWithStops(symbol, quantity, stopLoss, takeProfit, "MACD金叉")) {
                log(String.format("MACD金叉買入 - Histogram: %.4f, 倉位比例: %.1f%%, 數量: %d, 價格: %.2f, 停損: %.2f, 停利: %.2f", 
                                 histogram, positionRatio * 100, quantity, price, stopLoss, takeProfit));
            }
        }
    }
    
    /**
     * 執行賣出操作
     */
    private void executeSell(double price, double histogram) {
        int currentQuantity = getPositionQuantity(symbol);
        
        if (currentQuantity > 0) {
            Portfolio portfolio = getPortfolio();
            String reason = prevMACD >= prevSignal && macd.getValue(barSeries.getBarCount() - 1).doubleValue() < 
                           macdSignal.getValue(barSeries.getBarCount() - 1).doubleValue() ? "MACD死叉" : "MACD止損";
            
            double profit = 0.0;
            if (portfolio != null) {
                Position position = portfolio.getPosition(symbol);
                if (position != null) {
                    profit = position.getReturnRate(price) * 100;
                }
            }
            
            if (sellWithReason(symbol, currentQuantity, reason)) {
                log(String.format("MACD賣出 (%s) - Histogram: %.4f, 收益: %.2f%%, 數量: %d, 價格: %.2f", 
                                 reason, histogram, profit, currentQuantity, price));
            }
        }
    }
    
    @Override
    protected void onCleanup() {
        // 平倉所有持倉
        int remainingQuantity = getPositionQuantity(symbol);
        if (remainingQuantity > 0) {
            sell(symbol, remainingQuantity);
            log("MACD 策略結束，平倉所有持倉");
        }
    }
    
    /**
     * 獲取策略狀態描述
     */
    public String getStatusDescription() {
        int requiredBars = slowPeriod + signalPeriod;
        if (barSeries == null || barSeries.getBarCount() < requiredBars) {
            return "等待足夠數據...";
        }
        
        int lastIndex = barSeries.getBarCount() - 1;
        double currentMACD = macd.getValue(lastIndex).doubleValue();
        double currentSignal = macdSignal.getValue(lastIndex).doubleValue();
        double histogram = currentMACD - currentSignal;
        int position = getPositionQuantity(symbol);
        
        String trend = histogram > 0 ? "多頭" : "空頭";
        String strength = Math.abs(histogram) > 0.002 ? "強" : "弱";
        
        return String.format("MACD: %.4f, 信號: %.4f, 趨勢: %s(%s), 持倉: %d", 
                           currentMACD, currentSignal, trend, strength, position);
    }
}
