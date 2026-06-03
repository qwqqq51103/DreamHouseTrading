package com.dreamhouse.trading.core.execution;

/**
 * Execution order type.
 */
public enum OrderType {
    MARKET("Market", "MKT"),
    LIMIT("Limit", "LMT"),
    STOP("Stop", "STP"),
    TAKE_PROFIT("Take Profit", "TP"),
    STOP_LIMIT("Stop Limit", "STP_LMT"),
    TRAILING_STOP("Trailing Stop", "TSL"),
    CONDITIONAL("Conditional", "COND");

    private final String displayName;
    private final String shortCode;

    OrderType(String displayName, String shortCode) {
        this.displayName = displayName;
        this.shortCode = shortCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    public boolean isMarketOrder() {
        return this == MARKET || this == STOP || this == TRAILING_STOP || this == TAKE_PROFIT;
    }

    public boolean isLimitOrder() {
        return this == LIMIT || this == STOP_LIMIT;
    }

    public boolean isStopOrder() {
        return this == STOP || this == STOP_LIMIT || this == TRAILING_STOP || this == TAKE_PROFIT;
    }

    @Override
    public String toString() {
        return displayName + " (" + shortCode + ")";
    }
}
