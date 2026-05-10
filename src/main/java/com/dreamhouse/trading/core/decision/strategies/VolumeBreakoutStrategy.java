package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only price breakout confirmed by volume expansion.
 */
public class VolumeBreakoutStrategy extends IntradaySignalStrategy {

    private int breakoutLookbackBars = 20;
    private double volumeMultiplier = 1.8;

    public VolumeBreakoutStrategy() {
        super("VolumeBreakout", "Volume confirmed breakout");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        if (!hasUsableSeries(barIndex, breakoutLookbackBars)) {
            return noTrade("Not enough bars for volume breakout");
        }

        int start = Math.max(currentDayStartIndex(barIndex), barIndex - breakoutLookbackBars);
        int previousEnd = barIndex - 1;
        double priorHigh = highestHigh(start, previousEnd);
        double priorLow = lowestLow(start, previousEnd);
        double averageVolume = averageVolume(start, previousEnd);
        double currentClose = bar.getClosePrice().doubleValue();
        double volumeRatio = averageVolume <= 0.0 ? 1.0 : volume(barIndex) / averageVolume;
        boolean volumeSurge = volumeRatio >= volumeMultiplier;

        if (currentClose > priorHigh && volumeSurge) {
            double stopLoss = Math.min(priorLow, currentClose * 0.98);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double priceExpansion = (currentClose - priorHigh) / Math.max(0.01, priorHigh);
            double confidence = clampConfidence(0.5 + priceExpansion * 10.0 + Math.min(0.25, (volumeRatio - volumeMultiplier) * 0.08));
            return longSignal(confidence,
                String.format("Volume breakout above %.2f with %.2fx volume", priorHigh, volumeRatio),
                stopLoss,
                takeProfit);
        }

        if (currentClose < priorLow && volumeSurge) {
            return hold(0.45, String.format("Downside volume break risk below %.2f", priorLow));
        }

        return noTrade("No volume breakout");
    }

    public void setBreakoutLookbackBars(int breakoutLookbackBars) {
        this.breakoutLookbackBars = Math.max(3, breakoutLookbackBars);
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = Math.max(1.0, volumeMultiplier);
    }
}
