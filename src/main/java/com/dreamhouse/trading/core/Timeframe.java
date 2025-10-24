package com.dreamhouse.trading.core;

public enum Timeframe {
    M1("1分", 1),
    M5("5分", 5),
    M15("15分", 15),
    M30("30分", 30),
    H1("60分", 60),
    D1("日線", 1440),
    W1("週線", 10080);
    
    private final String label;
    private final int minutes;
    
    Timeframe(String label, int minutes) {
        this.label = label;
        this.minutes = minutes;
    }
    
    public String getLabel() { return label; }
    public int getMinutes() { return minutes; }
    
    @Override
    public String toString() { return label; }
}

