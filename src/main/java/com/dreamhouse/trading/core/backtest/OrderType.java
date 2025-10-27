package com.dreamhouse.trading.core.backtest;

/**
 * 訂單類型枚舉
 */
public enum OrderType {
    MARKET("市價單"),
    LIMIT("限價單"),
    STOP("停損單"),
    STOP_LIMIT("停損限價單");
    
    private final String displayName;
    
    OrderType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
