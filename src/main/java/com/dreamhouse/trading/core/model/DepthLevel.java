package com.dreamhouse.trading.core.model;

public class DepthLevel {
    public enum Side { BID, ASK }
    
    private final Side side;
    private final double price;
    private long quantity;
    private final int level; // 0=最佳價, 1=次佳...
    
    public DepthLevel(Side side, double price, long quantity, int level) {
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.level = level;
    }
    
    public Side getSide() { return side; }
    public double getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public int getLevel() { return level; }
}

