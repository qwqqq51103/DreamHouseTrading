package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.execution.OrderType;

/**
 * Metadata-based exit policy for auto-monitor risk bookkeeping.
 */
public final class AutoMonitorExitPolicy {

    private AutoMonitorExitPolicy() {
    }

    public static boolean shouldRegisterStopLossCooldown(ExecutionResult result, DecisionResult signal) {
        if (result == null || !result.isSuccess()) {
            return false;
        }
        OrderType orderType = result.getOrderType() != null
                ? result.getOrderType()
                : signal != null ? signal.getOrderType() : null;
        return isStopLossOrderType(orderType);
    }

    private static boolean isStopLossOrderType(OrderType orderType) {
        return orderType == OrderType.STOP
                || orderType == OrderType.STOP_LIMIT
                || orderType == OrderType.TRAILING_STOP;
    }
}
