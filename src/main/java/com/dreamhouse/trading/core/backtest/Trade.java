package com.dreamhouse.trading.core.backtest;

import java.time.LocalDateTime;

/**
 * 交易記錄類
 * 記錄單筆交易的詳細信息
 */
public class Trade {
    
    private final LocalDateTime timestamp;
    private final String symbol;
    private final TradeType type;
    private final int quantity;
    private final double price;
    private final double commission;
    private final double totalAmount;
    
    // 停利停損資訊
    private final Double stopLoss;      // 停損價格
    private final Double takeProfit;    // 停利價格
    private final String exitReason;   // 出場原因
    
    /**
     * 構造函數 (不包含停利停損)
     */
    public Trade(LocalDateTime timestamp, String symbol, TradeType type, 
                 int quantity, double price, double commission) {
        this(timestamp, symbol, type, quantity, price, commission, null, null, null);
    }
    
    /**
     * 完整構造函數 (包含停利停損)
     */
    public Trade(LocalDateTime timestamp, String symbol, TradeType type, 
                 int quantity, double price, double commission,
                 Double stopLoss, Double takeProfit, String exitReason) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.commission = commission;
        this.totalAmount = quantity * price;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.exitReason = exitReason;
    }
    
    /**
     * 計算交易成本（包含手續費）
     */
    public double getTotalCost() {
        return totalAmount + (totalAmount * commission);
    }
    
    /**
     * 計算交易收入（扣除手續費）
     */
    public double getNetProceeds() {
        return totalAmount - (totalAmount * commission);
    }
    
    /**
     * 獲取手續費金額
     */
    public double getCommissionAmount() {
        return totalAmount * commission;
    }
    
    // Getters
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSymbol() { return symbol; }
    public TradeType getType() { return type; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getCommission() { return commission; }
    public double getTotalAmount() { return totalAmount; }
    public Double getStopLoss() { return stopLoss; }
    public Double getTakeProfit() { return takeProfit; }
    public String getExitReason() { return exitReason; }
    
    @Override
    public String toString() {
        return String.format("Trade{%s %s %d@%.2f, commission=%.4f, total=%.2f}", 
                           timestamp, type, quantity, price, commission, totalAmount);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Trade trade = (Trade) obj;
        return timestamp.equals(trade.timestamp) && 
               symbol.equals(trade.symbol) && 
               type == trade.type;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(timestamp, symbol, type);
    }
}
