package com.dreamhouse.trading.core.backtest;

/**
 * 交易類型枚舉
 */
public enum TradeType {
    BUY("買入"),
    SELL("賣出");
    
    private final String displayName;
    
    TradeType(String displayName) {
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
