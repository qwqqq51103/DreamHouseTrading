package com.dreamhouse.trading.core.backtest;

import java.time.LocalDateTime;

/**
 * 持倉類
 * 表示單一股票的持倉信息
 */
public class Position {
    
    private final String symbol;
    private int quantity;
    private double averagePrice;
    private double totalCost;
    private final LocalDateTime openTime;
    private LocalDateTime lastUpdateTime;

    // K 線索引追蹤（用於回測）
    private int entryBarIndex = -1;  // 進場時的 K 線索引

    // 統計數據
    private double realizedPnL = 0.0;
    private double totalCommission = 0.0;
    
    /**
     * 構造函數
     */
    public Position(String symbol, int quantity, double price, double costRate) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averagePrice = price;
        this.totalCost = quantity * price * (1 + costRate);
        this.openTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        this.totalCommission = quantity * price * costRate;
    }
    
    /**
     * 增加持倉數量
     */
    public void addQuantity(int additionalQuantity, double price, double costRate) {
        double additionalCost = additionalQuantity * price * (1 + costRate);
        
        // 重新計算平均成本
        this.averagePrice = (totalCost + additionalCost) / (quantity + additionalQuantity);
        this.quantity += additionalQuantity;
        this.totalCost += additionalCost;
        this.totalCommission += additionalQuantity * price * costRate;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 減少持倉數量
     */
    public void reduceQuantity(int reduceQuantity, double price, double costRate) {
        if (reduceQuantity > quantity) {
            throw new IllegalArgumentException("Cannot reduce more than current quantity");
        }
        
        // 計算這部分的成本
        double reduceCost = (totalCost / quantity) * reduceQuantity;
        double proceeds = reduceQuantity * price * (1 - costRate);
        
        // 更新已實現盈虧
        realizedPnL += proceeds - reduceCost;
        
        // 更新持倉
        this.quantity -= reduceQuantity;
        this.totalCost -= reduceCost;
        this.totalCommission += reduceQuantity * price * costRate;
        this.lastUpdateTime = LocalDateTime.now();
    }
    
    /**
     * 計算未實現盈虧
     */
    public double getUnrealizedPnL(double currentPrice) {
        double currentValue = quantity * currentPrice;
        return currentValue - totalCost;
    }
    
    /**
     * 計算總盈虧（已實現 + 未實現）
     */
    public double getTotalPnL(double currentPrice) {
        return realizedPnL + getUnrealizedPnL(currentPrice);
    }
    
    /**
     * 計算收益率
     */
    public double getReturnRate(double currentPrice) {
        if (totalCost == 0) return 0.0;
        return getTotalPnL(currentPrice) / totalCost;
    }
    
    /**
     * 計算當前市值
     */
    public double getMarketValue(double currentPrice) {
        return quantity * currentPrice;
    }
    
    /**
     * 計算部分賣出的盈虧
     */
    public double calculateProfit(int sellQuantity, double sellPrice, double costRate) {
        if (sellQuantity > quantity) {
            throw new IllegalArgumentException("Sell quantity exceeds position quantity");
        }
        
        double avgCost = totalCost / quantity;
        double costBasis = avgCost * sellQuantity;
        double proceeds = sellQuantity * sellPrice * (1 - costRate);
        
        return proceeds - costBasis;
    }
    
    /**
     * 檢查是否為盈利持倉
     */
    public boolean isProfitable(double currentPrice) {
        return getTotalPnL(currentPrice) > 0;
    }
    
    /**
     * 獲取持倉天數
     */
    public long getHoldingDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(openTime.toLocalDate(), 
                                                         LocalDateTime.now().toLocalDate());
    }
    
    /**
     * 獲取持倉小時數
     */
    public long getHoldingHours() {
        return java.time.temporal.ChronoUnit.HOURS.between(openTime, LocalDateTime.now());
    }

    /**
     * 計算持倉 K 線數量（用於回測）
     * @param currentBarIndex 當前 K 線索引
     * @return 持倉 K 線數量，如果未設置則返回 -1
     */
    public int getHoldingBars(int currentBarIndex) {
        if (entryBarIndex < 0) {
            return -1;  // 未設置進場索引
        }
        return currentBarIndex - entryBarIndex;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public int getQuantity() { return quantity; }
    public double getAveragePrice() { return averagePrice; }
    public double getTotalCost() { return totalCost; }
    public LocalDateTime getOpenTime() { return openTime; }
    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public double getRealizedPnL() { return realizedPnL; }
    public double getTotalCommission() { return totalCommission; }
    public int getEntryBarIndex() { return entryBarIndex; }

    // Setters
    public void setEntryBarIndex(int entryBarIndex) {
        this.entryBarIndex = entryBarIndex;
    }
    
    @Override
    public String toString() {
        return String.format("Position{symbol='%s', quantity=%d, avgPrice=%.2f, cost=%.2f, realizedPnL=%.2f}", 
                           symbol, quantity, averagePrice, totalCost, realizedPnL);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Position position = (Position) obj;
        return symbol.equals(position.symbol);
    }
    
    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}
