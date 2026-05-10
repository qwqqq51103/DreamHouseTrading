package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only trend signal based on bullish moving-average alignment.
 */
public class MovingAverageAlignmentStrategy extends IntradaySignalStrategy {

    private int shortPeriod = 10;
    private int mediumPeriod = 20;
    private int longPeriod = 50;
    private int slopeLookback = 5;

    public MovingAverageAlignmentStrategy() {
        super("MovingAverageAlignment", "Bullish moving-average alignment");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        int requiredBars = longPeriod + slopeLookback;
        if (!hasUsableSeries(barIndex, requiredBars)) {
            return noTrade("Not enough bars for MA alignment");
        }

        double shortMa = simpleMovingAverage(barIndex, shortPeriod);
        double mediumMa = simpleMovingAverage(barIndex, mediumPeriod);
        double longMa = simpleMovingAverage(barIndex, longPeriod);
        double previousShortMa = simpleMovingAverage(barIndex - slopeLookback, shortPeriod);
        double currentClose = bar.getClosePrice().doubleValue();

        boolean aligned = currentClose > shortMa && shortMa > mediumMa && mediumMa > longMa;
        boolean slopePositive = shortMa > previousShortMa;
        if (aligned && slopePositive) {
            double stopLoss = Math.min(longMa, currentClose * 0.95);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double maSpread = (shortMa - longMa) / Math.max(0.01, longMa);
            double confidence = clampConfidence(0.5 + maSpread * 6.0);
            return longSignal(confidence,
                String.format("Bullish MA alignment %.2f > %.2f > %.2f", shortMa, mediumMa, longMa),
                stopLoss,
                takeProfit);
        }

        if (currentClose < mediumMa) {
            return hold(0.35, String.format("Price below medium MA %.2f", mediumMa));
        }

        return noTrade("No bullish MA alignment");
    }

    private double simpleMovingAverage(int endIndex, int period) {
        int start = endIndex - period + 1;
        double sum = 0.0;
        for (int i = start; i <= endIndex; i++) {
            sum += close(i);
        }
        return sum / period;
    }

    public void setPeriods(int shortPeriod, int mediumPeriod, int longPeriod) {
        if (shortPeriod >= mediumPeriod || mediumPeriod >= longPeriod) {
            throw new IllegalArgumentException("MA periods must be ordered short < medium < long");
        }
        this.shortPeriod = Math.max(3, shortPeriod);
        this.mediumPeriod = Math.max(this.shortPeriod + 1, mediumPeriod);
        this.longPeriod = Math.max(this.mediumPeriod + 1, longPeriod);
    }

    public void setSlopeLookback(int slopeLookback) {
        this.slopeLookback = Math.max(1, slopeLookback);
    }
}
