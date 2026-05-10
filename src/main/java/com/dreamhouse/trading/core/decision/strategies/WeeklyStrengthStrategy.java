package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only higher-timeframe strength signal.
 */
public class WeeklyStrengthStrategy extends IntradaySignalStrategy {

    private int trendLookbackBars = 20;
    private int breakoutLookbackBars = 12;

    public WeeklyStrengthStrategy() {
        super("WeeklyStrength", "Higher-timeframe strength");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        int requiredBars = Math.max(trendLookbackBars, breakoutLookbackBars) + 1;
        if (!hasUsableSeries(barIndex, requiredBars)) {
            return noTrade("Not enough bars for higher-timeframe strength");
        }

        double currentClose = bar.getClosePrice().doubleValue();
        double trendStartClose = close(barIndex - trendLookbackBars);
        double priorHigh = highestHigh(barIndex - breakoutLookbackBars, barIndex - 1);
        double recentLow = lowestLow(barIndex - breakoutLookbackBars, barIndex - 1);
        boolean trendUp = currentClose > trendStartClose;
        boolean newStrength = currentClose > priorHigh;

        if (trendUp && newStrength) {
            double stopLoss = Math.min(recentLow, currentClose * 0.94);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double trendGain = (currentClose - trendStartClose) / Math.max(0.01, trendStartClose);
            double confidence = clampConfidence(0.52 + trendGain * 2.0);
            return longSignal(confidence,
                    String.format("Higher-timeframe strength above %.2f", priorHigh),
                    stopLoss,
                    takeProfit);
        }

        if (!trendUp) {
            return hold(0.4, "Higher timeframe has not turned up");
        }
        return noTrade("No higher-timeframe breakout");
    }

    public void setTrendLookbackBars(int trendLookbackBars) {
        this.trendLookbackBars = Math.max(5, trendLookbackBars);
    }

    public void setBreakoutLookbackBars(int breakoutLookbackBars) {
        this.breakoutLookbackBars = Math.max(3, breakoutLookbackBars);
    }
}
