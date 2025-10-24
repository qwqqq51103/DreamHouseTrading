package com.dreamhouse.trading.core.model;

import java.time.LocalDateTime;

public class Tick {
    private final String symbol;
    private final LocalDateTime timestamp;
    private final double price;
    private final long volume;
    
    public Tick(String symbol, LocalDateTime timestamp, double price, long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.price = price;
        this.volume = volume;
    }
    
    public String getSymbol() { return symbol; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getPrice() { return price; }
    public long getVolume() { return volume; }
}

