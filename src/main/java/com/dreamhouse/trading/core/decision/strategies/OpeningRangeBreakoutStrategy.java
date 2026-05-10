package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only opening range breakout signal for intraday scans and backtests.
 */
public class OpeningRangeBreakoutStrategy extends IntradaySignalStrategy {

    private int openingRangeBars = 6;
    private int volumeLookbackBars = 20;
    private double volumeMultiplier = 1.2;

    public OpeningRangeBreakoutStrategy() {
        super("OpeningRangeBreakout", "Opening range breakout");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        if (!hasUsableSeries(barIndex, openingRangeBars)) {
            return noTrade("Not enough bars for opening range");
        }

        int dayStart = currentDayStartIndex(barIndex);
        int rangeEnd = Math.min(dayStart + openingRangeBars - 1, barIndex - 1);
        if (barIndex <= rangeEnd) {
            return noTrade("Opening range is still forming");
        }

        double rangeHigh = highestHigh(dayStart, rangeEnd);
        double rangeLow = lowestLow(dayStart, rangeEnd);
        double currentClose = bar.getClosePrice().doubleValue();
        double previousClose = close(barIndex - 1);
        double averageVolume = averageVolume(Math.max(dayStart, barIndex - volumeLookbackBars), barIndex - 1);
        double volumeRatio = averageVolume <= 0.0 ? 1.0 : volume(barIndex) / averageVolume;
        boolean volumeConfirmed = volumeRatio >= volumeMultiplier;

        if (currentClose > rangeHigh && previousClose <= rangeHigh && volumeConfirmed) {
            double breakoutPercent = (currentClose - rangeHigh) / Math.max(0.01, rangeHigh);
            double stopLoss = Math.min(rangeLow, currentClose * 0.99);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double confidence = clampConfidence(0.55 + breakoutPercent * 12.0 + Math.min(0.2, (volumeRatio - 1.0) * 0.1));
            return longSignal(confidence,
                String.format("Opening range breakout above %.2f with %.2fx volume", rangeHigh, volumeRatio),
                stopLoss,
                takeProfit);
        }

        if (currentClose > rangeHigh) {
            return hold(0.35, String.format("Above opening range %.2f but volume is %.2fx", rangeHigh, volumeRatio));
        }

        if (currentClose < rangeLow && volumeConfirmed) {
            return hold(0.45, String.format("Opening range downside break risk below %.2f", rangeLow));
        }

        return noTrade("No opening range breakout");
    }

    public void setOpeningRangeBars(int openingRangeBars) {
        this.openingRangeBars = Math.max(2, openingRangeBars);
    }

    public void setVolumeLookbackBars(int volumeLookbackBars) {
        this.volumeLookbackBars = Math.max(2, volumeLookbackBars);
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = Math.max(1.0, volumeMultiplier);
    }
}
