package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only VWAP pullback continuation signal.
 */
public class VwapPullbackStrategy extends IntradaySignalStrategy {

    private int minimumBars = 8;
    private int volumeLookbackBars = 20;
    private double pullbackTolerance = 0.003;

    public VwapPullbackStrategy() {
        super("VwapPullback", "VWAP pullback continuation");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        if (!hasUsableSeries(barIndex, minimumBars)) {
            return noTrade("Not enough bars for VWAP pullback");
        }

        int dayStart = currentDayStartIndex(barIndex);
        if (barIndex - dayStart < minimumBars) {
            return noTrade("Current session has too few bars for VWAP");
        }

        double currentVwap = calculateVwap(dayStart, barIndex);
        double previousVwap = calculateVwap(dayStart, barIndex - 1);
        double currentClose = bar.getClosePrice().doubleValue();
        double previousClose = close(barIndex - 1);
        double previousLow = low(barIndex - 1);
        double averageVolume = averageVolume(Math.max(dayStart, barIndex - volumeLookbackBars), barIndex - 1);
        double volumeRatio = averageVolume <= 0.0 ? 1.0 : volume(barIndex) / averageVolume;

        boolean pulledBackToVwap = previousLow <= previousVwap * (1.0 + pullbackTolerance);
        boolean reclaimedVwap = previousClose <= previousVwap * (1.0 + pullbackTolerance)
            && currentClose > currentVwap
            && currentClose > previousClose;

        if (pulledBackToVwap && reclaimedVwap && volumeRatio >= 0.8) {
            double stopLoss = Math.min(previousLow, currentVwap * 0.99);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double distanceAboveVwap = (currentClose - currentVwap) / Math.max(0.01, currentVwap);
            double confidence = clampConfidence(0.48 + distanceAboveVwap * 12.0 + Math.min(0.18, volumeRatio * 0.08));
            return longSignal(confidence,
                String.format("VWAP pullback reclaimed %.2f with %.2fx volume", currentVwap, volumeRatio),
                stopLoss,
                takeProfit);
        }

        if (currentClose < currentVwap) {
            return hold(0.30, String.format("Below VWAP %.2f; wait for reclaim", currentVwap));
        }

        return noTrade("No VWAP pullback setup");
    }

    private double calculateVwap(int startInclusive, int endInclusive) {
        double priceVolume = 0.0;
        double totalVolume = 0.0;
        for (int i = Math.max(0, startInclusive); i <= endInclusive; i++) {
            Bar bar = barSeries.getBar(i);
            double typicalPrice = (bar.getHighPrice().doubleValue()
                + bar.getLowPrice().doubleValue()
                + bar.getClosePrice().doubleValue()) / 3.0;
            double barVolume = bar.getVolume().doubleValue();
            priceVolume += typicalPrice * barVolume;
            totalVolume += barVolume;
        }
        return totalVolume <= 0.0 ? close(endInclusive) : priceVolume / totalVolume;
    }

    public void setMinimumBars(int minimumBars) {
        this.minimumBars = Math.max(3, minimumBars);
    }

    public void setVolumeLookbackBars(int volumeLookbackBars) {
        this.volumeLookbackBars = Math.max(2, volumeLookbackBars);
    }

    public void setPullbackTolerance(double pullbackTolerance) {
        this.pullbackTolerance = Math.max(0.0, pullbackTolerance);
    }
}
