package com.dreamhouse.trading.core.backtest.strategies;

import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * RSI 策略
 * 基於相對強弱指標的超買超賣信號進行交易
 */
public class RSIStrategy extends BaseStrategy {
    
    private RSIIndicator rsi;
    private ClosePriceIndicator closePrice;
    
    private int rsiPeriod;
    private double oversoldThreshold;
    private double overboughtThreshold;
    private String symbol;
    private int maxPosition;
    
    /**
     * 構造函數
     */
    public RSIStrategy() {
        super("RSI 策略", "基於 RSI 指標的超買超賣信號進行交易");
    }
    
    @Override
    protected void initializeDefaultConfig() {
        config.setParameter("rsiPeriod", 14);
        config.setParameter("oversoldThreshold", 30.0);
        config.setParameter("overboughtThreshold", 70.0);
        config.setParameter("symbol", "STOCK");
        config.setParameter("maxPosition", 1000);
        config.setParameter("debug", false);
    }
    
    @Override
    protected void onInitialize() {
        // 讀取配置參數
        rsiPeriod = config.getIntParameter("rsiPeriod", 14);
        oversoldThreshold = config.getDoubleParameter("oversoldThreshold", 30.0);
        overboughtThreshold = config.getDoubleParameter("overboughtThreshold", 70.0);
        symbol = config.getStringParameter("symbol", "STOCK");
        maxPosition = config.getIntParameter("maxPosition", 1000);
        
        // 初始化技術指標
        closePrice = new ClosePriceIndicator(barSeries);
        rsi = new RSIIndicator(closePrice, rsiPeriod);
        
        log(String.format("RSI 策略初始化完成 - 週期: %d, 超賣: %.1f, 超買: %.1f", 
                         rsiPeriod, oversoldThreshold, overboughtThreshold));
    }
    
    @Override
    public void onBar(int barIndex, Bar bar) {
        // 需要足夠的數據才能計算 RSI
        if (barIndex < rsiPeriod) {
            if (barIndex % 5 == 0) {
                System.out.println(String.format("[RSI] 等待數據累積: %d/%d", barIndex, rsiPeriod));
            }
            return;
        }
        
        double currentPrice = bar.getClosePrice().doubleValue();
        double currentRSI = rsi.getValue(barIndex).doubleValue();
        
        if (barIndex % 10 == 0) {
            System.out.printf("[RSI] Bar %d: Price=%.2f, RSI=%.2f, 持倉: %s%n", 
                             barIndex, currentPrice, currentRSI, 
                             hasPosition(symbol) ? "是" : "否");
        }
        
        // 檢查買入信號：RSI 從超賣區域回升
        if (shouldBuy(currentRSI, barIndex)) {
            executeBuy(currentPrice);
        }
        // 檢查賣出信號：RSI 進入超買區域或止損
        else if (shouldSell(currentRSI, barIndex)) {
            executeSell(currentPrice);
        }
    }
    
    /**
     * 判斷是否應該買入
     */
    private boolean shouldBuy(double currentRSI, int barIndex) {
        // 沒有持倉時檢查買入條件
        if (!hasPosition(symbol)) {
            // 方式1：RSI 從超賣區域回升（精確穿越）
            if (currentRSI > oversoldThreshold && barIndex > 0) {
                double prevRSI = rsi.getValue(barIndex - 1).doubleValue();
                if (prevRSI <= oversoldThreshold) {
                    System.out.printf("[RSI] 買入信號(穿越) - Bar %d: RSI %.2f -> %.2f (超賣閾值: %.1f)%n",
                                     barIndex, prevRSI, currentRSI, oversoldThreshold);
                    return true;
                }
            }

            // 方式2：RSI 在超賣區域（更寬鬆，便於測試）
            if (currentRSI <= oversoldThreshold) {
                System.out.printf("[RSI] 買入信號(超賣) - Bar %d: RSI %.2f <= %.1f%n",
                                 barIndex, currentRSI, oversoldThreshold);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判斷是否應該賣出
     */
    private boolean shouldSell(double currentRSI, int barIndex) {
        // 有持倉時檢查賣出條件
        if (hasPosition(symbol)) {
            // 方式1：RSI 超買信號（直接賣出）
            if (currentRSI >= overboughtThreshold) {
                System.out.printf("[RSI] 賣出信號(超買) - Bar %d: RSI %.2f >= %.1f%n",
                                 barIndex, currentRSI, overboughtThreshold);
                return true;
            }

            // 方式2：RSI 回到中性區域（50 附近）- 寬鬆條件
            if (currentRSI > 50.0 && currentRSI < 60.0) {
                Position position = getPortfolio().getPosition(symbol);
                if (position != null) {
                    double currentPrice = getCurrentPrice();
                    // 如果已經獲利就賣出（獲利了結）
                    boolean takeProfit = position.getReturnRate(currentPrice) > 0.02; // 2% 以上就賣
                    if (takeProfit) {
                        System.out.printf("[RSI] 賣出信號(獲利) - Bar %d: RSI %.2f, 獲利: %.2f%%%n",
                                         barIndex, currentRSI, position.getReturnRate(currentPrice) * 100);
                        return true;
                    }
                }
            }

            // 方式3：止損 - RSI 跌破 45 且虧損
            if (currentRSI < 45.0) {
                Position position = getPortfolio().getPosition(symbol);
                if (position != null) {
                    double currentPrice = getCurrentPrice();
                    boolean stopLoss = position.getReturnRate(currentPrice) < -0.03; // 虧損 3% 止損
                    if (stopLoss) {
                        System.out.printf("[RSI] 賣出信號(止損) - Bar %d: RSI %.2f, 虧損: %.2f%%%n",
                                         barIndex, currentRSI, position.getReturnRate(currentPrice) * 100);
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 執行買入操作
     */
    private void executeBuy(double price) {
        Portfolio portfolio = getPortfolio();
        if (portfolio == null) return;
        
        double availableCash = portfolio.getCash();
        
        // 計算可買入的數量（預留手續費）
        int quantity = Math.min(maxPosition, (int) (availableCash / (price * 1.002)));
        
        if (quantity > 0) {
            // 計算停損和停利價格
            double stopLoss = price * 0.95;     // 停損 5%
            double takeProfit = price * 1.10;   // 停利 10%
            
            if (buyWithStops(symbol, quantity, stopLoss, takeProfit, "RSI超賣反彈")) {
                double currentRSI = rsi.getValue(barSeries.getBarCount() - 1).doubleValue();
                log(String.format("RSI 買入信號 - RSI: %.2f, 數量: %d, 價格: %.2f, 停損: %.2f, 停利: %.2f", 
                                 currentRSI, quantity, price, stopLoss, takeProfit));
            }
        }
    }
    
    /**
     * 執行賣出操作
     */
    private void executeSell(double price) {
        int currentQuantity = getPositionQuantity(symbol);
        
        if (currentQuantity > 0) {
            double currentRSI = rsi.getValue(barSeries.getBarCount() - 1).doubleValue();
            String reason = currentRSI >= overboughtThreshold ? "RSI超買" : "RSI止損";
            
            if (sellWithReason(symbol, currentQuantity, reason)) {
                log(String.format("RSI 賣出信號 (%s) - RSI: %.2f, 數量: %d, 價格: %.2f", 
                                 reason, currentRSI, currentQuantity, price));
            }
        }
    }
    
    @Override
    protected void onCleanup() {
        // 平倉所有持倉
        int remainingQuantity = getPositionQuantity(symbol);
        if (remainingQuantity > 0) {
            sell(symbol, remainingQuantity);
            log("RSI 策略結束，平倉所有持倉");
        }
    }
    
    /**
     * 獲取策略狀態描述
     */
    public String getStatusDescription() {
        if (barSeries == null || barSeries.getBarCount() < rsiPeriod) {
            return "等待足夠數據...";
        }
        
        int lastIndex = barSeries.getBarCount() - 1;
        double currentRSI = rsi.getValue(lastIndex).doubleValue();
        int position = getPositionQuantity(symbol);
        
        String signal = "中性";
        if (currentRSI <= oversoldThreshold) {
            signal = "超賣";
        } else if (currentRSI >= overboughtThreshold) {
            signal = "超買";
        }
        
        return String.format("RSI: %.2f (%s), 持倉: %d", currentRSI, signal, position);
    }
}
