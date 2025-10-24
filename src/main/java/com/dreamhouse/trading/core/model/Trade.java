package com.dreamhouse.trading.core.model;

import java.time.LocalDateTime;

public class Trade {
    public enum Side { BID, ASK }
    
    private final LocalDateTime timestamp;
    private final double price;
    private final long quantity;
    private final Side side;
    
    public Trade(LocalDateTime timestamp, double price, long quantity, Side side) {
        this.timestamp = timestamp;
        this.price = price;
        this.quantity = quantity;
        this.side = side;
    }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public Side getSide() { return side; }
}

