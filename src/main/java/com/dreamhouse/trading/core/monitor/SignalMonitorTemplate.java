package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.decision.DecisionConfig;

/**
 * Monitor template payload shared by UI, config persistence, replay, and tests.
 */
public record SignalMonitorTemplate(
        String name,
        String description,
        SignalMonitorConfig monitorConfig,
        DecisionConfig decisionConfig,
        boolean userDefined) {

    public boolean builtIn() {
        return !userDefined;
    }
}
