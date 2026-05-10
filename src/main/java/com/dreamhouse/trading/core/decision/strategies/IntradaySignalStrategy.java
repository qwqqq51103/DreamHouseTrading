package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.signal.StrategySignal;
import org.ta4j.core.Bar;

/**
 * Shared helpers for intraday signal strategies.
 */
abstract class IntradaySignalStrategy extends DecisionBaseStrategy {

    IntradaySignalStrategy(String name, String description) {
        super(name, description);
    }

    protected double close(int index) {
        return barSeries.getBar(index).getClosePrice().doubleValue();
    }

    protected double high(int index) {
        return barSeries.getBar(index).getHighPrice().doubleValue();
    }

    protected double low(int index) {
        return barSeries.getBar(index).getLowPrice().doubleValue();
    }

    protected double volume(int index) {
        return barSeries.getBar(index).getVolume().doubleValue();
    }

    protected int currentDayStartIndex(int barIndex) {
        if (barSeries == null || barIndex <= 0) {
            return 0;
        }

        var currentDate = barSeries.getBar(barIndex).getEndTime().toLocalDate();
        int index = barIndex;
        while (index > 0 && barSeries.getBar(index - 1).getEndTime().toLocalDate().equals(currentDate)) {
            index--;
        }
        return index;
    }

    protected double highestHigh(int startInclusive, int endInclusive) {
        double highest = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, startInclusive); i <= endInclusive; i++) {
            highest = Math.max(highest, high(i));
        }
        return highest;
    }

    protected double lowestLow(int startInclusive, int endInclusive) {
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = Math.max(0, startInclusive); i <= endInclusive; i++) {
            lowest = Math.min(lowest, low(i));
        }
        return lowest;
    }

    protected double averageVolume(int startInclusive, int endInclusive) {
        int start = Math.max(0, startInclusive);
        if (endInclusive < start) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= endInclusive; i++) {
            sum += volume(i);
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    protected double clampConfidence(double value) {
        return Math.max(0.0, Math.min(0.95, value));
    }

    protected StrategySignal noTrade(String reason) {
        updateSignal(SignalType.NO_TRADE, 0.0, reason);
        return buildSignal(SignalType.NO_TRADE, 0.0, reason);
    }

    protected StrategySignal hold(double confidence, String reason) {
        updateSignal(SignalType.HOLD, confidence, reason);
        return buildSignal(SignalType.HOLD, confidence, reason);
    }

    protected StrategySignal longSignal(double confidence, String reason, double stopLoss, double takeProfit) {
        updateSignal(SignalType.LONG, confidence, reason, stopLoss, takeProfit);
        return buildSignal(SignalType.LONG, confidence, reason, stopLoss, takeProfit);
    }

    protected StrategySignal exitSignal(double confidence, String reason) {
        updateSignal(SignalType.EXIT, confidence, reason);
        return buildSignal(SignalType.EXIT, confidence, reason);
    }

    protected double twoToOneTakeProfit(double entry, double stopLoss) {
        double risk = Math.max(0.01, entry - stopLoss);
        return entry + risk * 2.0;
    }

    protected boolean hasUsableSeries(int barIndex, int minBars) {
        return barSeries != null && barIndex >= minBars && barIndex < barSeries.getBarCount();
    }

    protected double trueRange(Bar bar) {
        return bar.getHighPrice().doubleValue() - bar.getLowPrice().doubleValue();
    }
}
