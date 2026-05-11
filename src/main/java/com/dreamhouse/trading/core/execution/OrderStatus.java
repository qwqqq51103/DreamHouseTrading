package com.dreamhouse.trading.core.execution;

/**
 * Order lifecycle status for simulated execution.
 */
public enum OrderStatus {
    NEW,
    ACCEPTED,
    FILLED,
    CANCELLED,
    REJECTED
}
