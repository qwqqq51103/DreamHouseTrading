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
    
    /**
     * 構造函數
     */
    public Trade(LocalDateTime timestamp, String symbol, TradeType type, 
                 int quantity, double price, double commission) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.commission = commission;
        this.totalAmount = quantity * price;
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
