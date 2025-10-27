package com.dreamhouse.trading.core.backtest;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * 投資組合管理類
 * 負責管理現金、持倉和計算總資產
 */
public class Portfolio {
    
    private double initialCash;
    private double cash;
    private final Map<String, Position> positions;
    private double totalValue;
    
    // 統計數據
    private double maxDrawdown = 0.0;
    private double maxValue = 0.0;
    private int totalTrades = 0;
    private int winningTrades = 0;
    
    /**
     * 構造函數
     */
    public Portfolio(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.positions = new HashMap<>();
        this.totalValue = initialCash;
        this.maxValue = initialCash;
    }
    
    /**
     * 重置投資組合
     */
    public void reset(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.positions.clear();
        this.totalValue = initialCash;
        this.maxValue = initialCash;
        this.maxDrawdown = 0.0;
        this.totalTrades = 0;
        this.winningTrades = 0;
    }
    
    /**
     * 添加持倉
     */
    public void addPosition(String symbol, int quantity, double price, double costRate) {
        double totalCost = quantity * price * (1 + costRate);
        
        if (cash >= totalCost) {
            cash -= totalCost;
            
            Position existingPosition = positions.get(symbol);
            if (existingPosition != null) {
                // 增加現有持倉
                existingPosition.addQuantity(quantity, price, costRate);
            } else {
                // 創建新持倉
                positions.put(symbol, new Position(symbol, quantity, price, costRate));
            }
            
            totalTrades++;
        } else {
            throw new IllegalStateException("Insufficient cash for purchase");
        }
    }
    
    /**
     * 減少持倉
     */
    public void reducePosition(String symbol, int quantity, double price, double costRate) {
        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalStateException("No position found for symbol: " + symbol);
        }
        
        if (position.getQuantity() < quantity) {
            throw new IllegalStateException("Insufficient position quantity");
        }
        
        // 計算盈虧
        double profit = position.calculateProfit(quantity, price, costRate);
        double proceeds = quantity * price * (1 - costRate);
        
        cash += proceeds;
        
        // 更新統計
        if (profit > 0) {
            winningTrades++;
        }
        
        // 減少持倉
        position.reduceQuantity(quantity, price, costRate);
        
        // 如果持倉為0，移除
        if (position.getQuantity() == 0) {
            positions.remove(symbol);
        }
        
        totalTrades++;
    }
    
    /**
     * 平倉所有持倉
     */
    public void closeAllPositions(double currentPrice) {
        for (Position position : positions.values()) {
            double proceeds = position.getQuantity() * currentPrice * 0.999; // 扣除手續費
            cash += proceeds;
        }
        positions.clear();
    }
    
    /**
     * 更新市值
     */
    public void updateMarketValue(double currentPrice) {
        double positionValue = 0.0;
        
        for (Position position : positions.values()) {
            positionValue += position.getQuantity() * currentPrice;
        }
        
        totalValue = cash + positionValue;
        
        // 更新最大值和最大回撤
        if (totalValue > maxValue) {
            maxValue = totalValue;
        }
        
        double currentDrawdown = (maxValue - totalValue) / maxValue;
        if (currentDrawdown > maxDrawdown) {
            maxDrawdown = currentDrawdown;
        }
    }
    
    /**
     * 獲取持倉
     */
    public Position getPosition(String symbol) {
        return positions.get(symbol);
    }
    
    /**
     * 獲取所有持倉
     */
    public Collection<Position> getPositions() {
        return positions.values();
    }
    
    /**
     * 計算持倉總價值
     */
    public double getPositionValue() {
        return totalValue - cash;
    }
    
    /**
     * 計算總收益率
     */
    public double getTotalReturn() {
        return (totalValue - initialCash) / initialCash;
    }
    
    /**
     * 計算勝率
     */
    public double getWinRate() {
        return totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;
    }
    
    /**
     * 獲取持倉數量
     */
    public int getPositionCount() {
        return positions.size();
    }
    
    /**
     * 檢查是否有足夠現金
     */
    public boolean hasEnoughCash(double amount) {
        return cash >= amount;
    }
    
    /**
     * 檢查是否有持倉
     */
    public boolean hasPosition(String symbol) {
        return positions.containsKey(symbol);
    }
    
    // Getters
    public double getInitialCash() { return initialCash; }
    public double getCash() { return cash; }
    public double getTotalValue() { return totalValue; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public double getMaxValue() { return maxValue; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    
    @Override
    public String toString() {
        return String.format("Portfolio{cash=%.2f, totalValue=%.2f, positions=%d, return=%.2f%%}", 
                           cash, totalValue, positions.size(), getTotalReturn() * 100);
    }
}
