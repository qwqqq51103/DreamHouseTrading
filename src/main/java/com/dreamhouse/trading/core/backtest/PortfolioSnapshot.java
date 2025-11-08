package com.dreamhouse.trading.core.backtest;

import java.time.LocalDateTime;

/**
 * 投資組合快照類
 * 記錄特定時間點的投資組合狀態
 */
public class PortfolioSnapshot {
    
    private final LocalDateTime timestamp;
    private final double totalValue;
    private final double cash;
    private final double positionValue;
    private final int positionCount;
    
    /**
     * 構造函數
     */
    public PortfolioSnapshot(LocalDateTime timestamp, double totalValue, double cash, 
                           double positionValue, int positionCount) {
        this.timestamp = timestamp;
        this.totalValue = totalValue;
        this.cash = cash;
        this.positionValue = positionValue;
        this.positionCount = positionCount;
    }
    
    /**
     * 計算現金比例
     */
    public double getCashRatio() {
        return totalValue > 0 ? cash / totalValue : 0.0;
    }
    
    /**
     * 計算持倉比例
     */
    public double getPositionRatio() {
        return totalValue > 0 ? positionValue / totalValue : 0.0;
    }
    
    /**
     * 是否持倉
     */
    public boolean hasPosition() {
        return positionCount > 0;
    }
    
    /**
     * 獲取持倉狀態描述
     */
    public String getPositionStatus() {
        if (positionCount == 0) {
            return "空倉";
        } else {
            return String.format("持倉 (%d個)", positionCount);
        }
    }
    
    // Getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getTotalValue() { return totalValue; }
    public double getCash() { return cash; }
    public double getPositionValue() { return positionValue; }
    public int getPositionCount() { return positionCount; }
    
    @Override
    public String toString() {
        return String.format("Snapshot{%s: total=%.2f, cash=%.2f, positions=%d}", 
                           timestamp, totalValue, cash, positionCount);
    }
}
