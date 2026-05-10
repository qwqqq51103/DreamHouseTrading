package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

import java.time.LocalTime;

/**
 * Exit signal for Taiwan day trades before the closing auction window.
 */
public class DayTradeCloseGuardStrategy extends IntradaySignalStrategy {

    private LocalTime forceExitTime = LocalTime.of(13, 20);

    public DayTradeCloseGuardStrategy() {
        super("DayTradeCloseGuard", "Intraday forced close guard");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        LocalTime barTime = bar.getEndTime().toLocalTime();
        if (!barTime.isBefore(forceExitTime)) {
            return exitSignal(0.95, String.format("Close guard after %s", forceExitTime));
        }
        return noTrade("Close guard not active");
    }

    public void setForceExitTime(LocalTime forceExitTime) {
        if (forceExitTime != null) {
            this.forceExitTime = forceExitTime;
        }
    }
}
