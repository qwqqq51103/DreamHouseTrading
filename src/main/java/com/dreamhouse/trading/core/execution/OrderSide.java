package com.dreamhouse.trading.core.execution;

/**
 * Order side for simulated execution.
 */
public enum OrderSide {
    BUY,
    SELL,
    SHORT,
    COVER;

    public boolean opensExposure() {
        return this == BUY || this == SHORT;
    }

    public boolean closesExposure() {
        return this == SELL || this == COVER;
    }
}
