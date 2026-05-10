package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only breakout from a recent consolidation platform.
 */
public class PlatformBreakoutStrategy extends IntradaySignalStrategy {

    private int platformLookbackBars = 20;
    private double maxPlatformRangePercent = 0.08;
    private double volumeMultiplier = 1.25;

    public PlatformBreakoutStrategy() {
        super("PlatformBreakout", "Platform consolidation breakout");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        if (!hasUsableSeries(barIndex, platformLookbackBars)) {
            return noTrade("Not enough bars for platform breakout");
        }

        int start = barIndex - platformLookbackBars;
        int previousEnd = barIndex - 1;
        double platformHigh = highestHigh(start, previousEnd);
        double platformLow = lowestLow(start, previousEnd);
        double midpoint = (platformHigh + platformLow) / 2.0;
        double rangePercent = (platformHigh - platformLow) / Math.max(0.01, midpoint);
        double currentClose = bar.getClosePrice().doubleValue();
        double averageVolume = averageVolume(start, previousEnd);
        double volumeRatio = averageVolume <= 0.0 ? 1.0 : volume(barIndex) / averageVolume;

        boolean compactPlatform = rangePercent <= maxPlatformRangePercent;
        boolean breakout = currentClose > platformHigh && volumeRatio >= volumeMultiplier;
        if (compactPlatform && breakout) {
            double stopLoss = Math.min(platformLow, currentClose * 0.94);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double confidence = clampConfidence(0.52
                + (maxPlatformRangePercent - rangePercent) * 2.0
                + Math.min(0.2, (volumeRatio - 1.0) * 0.08));
            return longSignal(confidence,
                String.format("Platform breakout above %.2f after %.1f%% range", platformHigh, rangePercent * 100.0),
                stopLoss,
                takeProfit);
        }

        if (!compactPlatform) {
            return noTrade(String.format("Platform range too wide %.1f%%", rangePercent * 100.0));
        }

        return noTrade("No platform breakout");
    }

    public void setPlatformLookbackBars(int platformLookbackBars) {
        this.platformLookbackBars = Math.max(5, platformLookbackBars);
    }

    public void setMaxPlatformRangePercent(double maxPlatformRangePercent) {
        this.maxPlatformRangePercent = Math.max(0.01, maxPlatformRangePercent);
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = Math.max(1.0, volumeMultiplier);
    }
}
