package com.dreamhouse.trading.core.backtest.strategies;

import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * 簡單移動平均線策略
 * 當價格突破短期均線時買入，跌破長期均線時賣出
 */
public class SimpleMovingAverageStrategy extends BaseStrategy {
    
    private SMAIndicator shortSMA;
    private SMAIndicator longSMA;
    private ClosePriceIndicator closePrice;
    
    private int shortPeriod;
    private int longPeriod;
    private String symbol;
    
    /**
     * 構造函數
     */
    public SimpleMovingAverageStrategy() {
        super("簡單移動平均線策略", "基於短期和長期移動平均線的交叉信號進行交易");
    }
    
    @Override
    protected void initializeDefaultConfig() {
        config.setParameter("shortPeriod", 10);
        config.setParameter("longPeriod", 20);
        config.setParameter("symbol", "STOCK");
        config.setParameter("maxPosition", 1000);
        config.setParameter("debug", false);
    }
    
    @Override
    protected void onInitialize() {
        // 讀取配置參數
        shortPeriod = config.getIntParameter("shortPeriod", 10);
        longPeriod = config.getIntParameter("longPeriod", 20);
        symbol = config.getStringParameter("symbol", "STOCK");
        
        // 初始化技術指標
        closePrice = new ClosePriceIndicator(barSeries);
        shortSMA = new SMAIndicator(closePrice, shortPeriod);
        longSMA = new SMAIndicator(closePrice, longPeriod);
        
        log(String.format("策略初始化完成 - 短期均線: %d, 長期均線: %d", shortPeriod, longPeriod));
    }
    
    @Override
    public void onBar(int barIndex, Bar bar) {
        // 需要足夠的數據才能計算指標
        if (barIndex < longPeriod) {
            if (barIndex % 10 == 0) {
                System.out.println(String.format("等待數據累積: %d/%d", barIndex, longPeriod));
            }
            return;
        }
        
        double currentPrice = bar.getClosePrice().doubleValue();
        double shortMA = shortSMA.getValue(barIndex).doubleValue();
        double longMA = longSMA.getValue(barIndex).doubleValue();
        
        // 獲取前一根K線的均線值
        double prevShortMA = barIndex > 0 ? shortSMA.getValue(barIndex - 1).doubleValue() : shortMA;
        double prevLongMA = barIndex > 0 ? longSMA.getValue(barIndex - 1).doubleValue() : longMA;
        
        if (barIndex % 20 == 0) { // 每20根K線輸出一次詳細信息
            System.out.printf("Bar %d: Price=%.2f, Short MA=%.2f, Long MA=%.2f, 持倉: %s%n", 
                             barIndex, currentPrice, shortMA, longMA, 
                             hasPosition(symbol) ? "是" : "否");
        }
        
        // 檢查買入信號：短期均線上穿長期均線
        if (shouldBuy(shortMA, longMA, prevShortMA, prevLongMA)) {
            executeBuy(currentPrice);
        }
        // 檢查賣出信號：短期均線下穿長期均線
        else if (shouldSell(shortMA, longMA, prevShortMA, prevLongMA)) {
            executeSell(currentPrice);
        }
    }
    
    /**
     * 判斷是否應該買入
     */
    private boolean shouldBuy(double shortMA, double longMA, double prevShortMA, double prevLongMA) {
        if (!hasPosition(symbol)) {
            // 方式1：金叉（精確穿越）
            if (prevShortMA <= prevLongMA && shortMA > longMA) {
                System.out.printf("[SMA] 買入信號(金叉) - 短MA: %.2f > 長MA: %.2f%n", shortMA, longMA);
                return true;
            }

            // 方式2：短期均線在長期均線上方且距離擴大（趨勢確認）
            if (shortMA > longMA && prevShortMA > prevLongMA) {
                double currentGap = (shortMA - longMA) / longMA;
                double prevGap = (prevShortMA - prevLongMA) / prevLongMA;
                // 如果距離擴大超過 0.5% 也視為買入信號
                if (currentGap > prevGap && currentGap > 0.005) {
                    System.out.printf("[SMA] 買入信號(趨勢) - 短MA: %.2f, 長MA: %.2f, 距離: %.2f%%%n",
                                     shortMA, longMA, currentGap * 100);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判斷是否應該賣出
     */
    private boolean shouldSell(double shortMA, double longMA, double prevShortMA, double prevLongMA) {
        if (hasPosition(symbol)) {
            // 方式1：死叉（精確穿越）
            if (prevShortMA >= prevLongMA && shortMA < longMA) {
                System.out.printf("[SMA] 賣出信號(死叉) - 短MA: %.2f < 長MA: %.2f%n", shortMA, longMA);
                return true;
            }

            // 方式2：短期均線在長期均線下方（趨勢反轉）
            if (shortMA < longMA) {
                double gap = (longMA - shortMA) / longMA;
                if (gap > 0.003) { // 距離超過 0.3% 就賣出
                    System.out.printf("[SMA] 賣出信號(反轉) - 短MA: %.2f, 長MA: %.2f, 距離: %.2f%%%n",
                                     shortMA, longMA, gap * 100);
                    return true;
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
        
        int maxPosition = config.getIntParameter("maxPosition", 1000);
        double availableCash = portfolio.getCash();
        
        // 計算可買入的數量（預留手續費）
        int quantity = Math.min(maxPosition, (int) (availableCash / (price * 1.002)));
        
        if (quantity > 0) {
            // 設定停利停損
            double stopLoss = price * 0.95;     // 停損 5%
            double takeProfit = price * 1.08;   // 停利 8%
            
            if (buyWithStops(symbol, quantity, stopLoss, takeProfit, "SMA金叉")) {
                log(String.format("SMA金叉買入 - 數量: %d, 價格: %.2f, 停損: %.2f, 停利: %.2f", 
                                 quantity, price, stopLoss, takeProfit));
            }
        }
    }
    
    /**
     * 執行賣出操作
     */
    private void executeSell(double price) {
        int currentQuantity = getPositionQuantity(symbol);
        
        if (currentQuantity > 0) {
            String reason = "SMA死叉";
            if (sellWithReason(symbol, currentQuantity, reason)) {
                log(String.format("SMA死叉賣出 - 數量: %d, 價格: %.2f, 原因: %s", 
                                 currentQuantity, price, reason));
            }
        }
    }
    
    @Override
    protected void onCleanup() {
        // 平倉所有持倉
        int remainingQuantity = getPositionQuantity(symbol);
        if (remainingQuantity > 0) {
            sell(symbol, remainingQuantity);
            log("策略結束，平倉所有持倉");
        }
    }
    
    /**
     * 獲取策略狀態描述
     */
    public String getStatusDescription() {
        if (barSeries == null || barSeries.getBarCount() < longPeriod) {
            return "等待足夠數據...";
        }
        
        int lastIndex = barSeries.getBarCount() - 1;
        double shortMA = shortSMA.getValue(lastIndex).doubleValue();
        double longMA = longSMA.getValue(lastIndex).doubleValue();
        
        String trend = shortMA > longMA ? "多頭" : "空頭";
        int position = getPositionQuantity(symbol);
        
        return String.format("趨勢: %s, 持倉: %d, 短期MA: %.2f, 長期MA: %.2f", 
                           trend, position, shortMA, longMA);
    }
}
