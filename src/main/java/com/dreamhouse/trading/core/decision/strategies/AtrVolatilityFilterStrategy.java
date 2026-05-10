package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Diagnostic volatility filter signal based on ATR percent.
 */
public class AtrVolatilityFilterStrategy extends IntradaySignalStrategy {

    private int atrPeriod = 14;
    private double minAtrPercent = 0.001;
    private double maxAtrPercent = 0.08;

    public AtrVolatilityFilterStrategy() {
        super("AtrVolatilityFilter", "ATR volatility filter");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        if (!hasUsableSeries(barIndex, atrPeriod + 1)) {
            return noTrade("Not enough bars for ATR filter");
        }

        double atrPercent = calculateAtrPercent(barIndex);
        if (atrPercent < minAtrPercent) {
            return hold(0.6, String.format("ATR %.2f%% below active range", atrPercent * 100.0));
        }
        if (atrPercent > maxAtrPercent) {
            return hold(0.7, String.format("ATR %.2f%% above risk range", atrPercent * 100.0));
        }
        return hold(0.3, String.format("ATR %.2f%% within range", atrPercent * 100.0));
    }

    private double calculateAtrPercent(int barIndex) {
        int start = Math.max(1, barIndex - atrPeriod + 1);
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= barIndex; i++) {
            Bar current = barSeries.getBar(i);
            Bar previous = barSeries.getBar(i - 1);
            double high = current.getHighPrice().doubleValue();
            double low = current.getLowPrice().doubleValue();
            double previousClose = previous.getClosePrice().doubleValue();
            sum += Math.max(high - low, Math.max(Math.abs(high - previousClose), Math.abs(low - previousClose)));
            count++;
        }
        double atr = count == 0 ? 0.0 : sum / count;
        return atr / Math.max(0.01, close(barIndex));
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = Math.max(2, atrPeriod);
    }

    public void setMinAtrPercent(double minAtrPercent) {
        this.minAtrPercent = Math.max(0.0, minAtrPercent);
    }

    public void setMaxAtrPercent(double maxAtrPercent) {
        this.maxAtrPercent = maxAtrPercent > 0.0 ? maxAtrPercent : Double.MAX_VALUE;
    }
}
