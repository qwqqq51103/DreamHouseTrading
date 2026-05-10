package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;

/**
 * Long-only signal for volume contraction followed by expansion breakout.
 */
public class LowVolumeConsolidationBreakoutStrategy extends IntradaySignalStrategy {

    private int contractionBars = 12;
    private int baselineBars = 30;
    private double contractionVolumeRatio = 0.75;
    private double expansionVolumeRatio = 1.35;

    public LowVolumeConsolidationBreakoutStrategy() {
        super("LowVolumeBreakout", "Low-volume consolidation breakout");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        int requiredBars = baselineBars + contractionBars;
        if (!hasUsableSeries(barIndex, requiredBars)) {
            return noTrade("Not enough bars for low-volume breakout");
        }

        int contractionStart = barIndex - contractionBars;
        int baselineStart = Math.max(0, contractionStart - baselineBars);
        double baselineVolume = averageVolume(baselineStart, contractionStart - 1);
        double contractionVolume = averageVolume(contractionStart, barIndex - 1);
        double currentVolume = volume(barIndex);
        double contractionRatio = baselineVolume <= 0.0 ? 1.0 : contractionVolume / baselineVolume;
        double expansionRatio = contractionVolume <= 0.0 ? 1.0 : currentVolume / contractionVolume;
        double consolidationHigh = highestHigh(contractionStart, barIndex - 1);
        double consolidationLow = lowestLow(contractionStart, barIndex - 1);
        double currentClose = bar.getClosePrice().doubleValue();

        boolean volumeContracted = contractionRatio <= contractionVolumeRatio;
        boolean volumeExpanded = expansionRatio >= expansionVolumeRatio;
        boolean breakout = currentClose > consolidationHigh;

        if (volumeContracted && volumeExpanded && breakout) {
            double stopLoss = Math.min(consolidationLow, currentClose * 0.95);
            double takeProfit = twoToOneTakeProfit(currentClose, stopLoss);
            double confidence = clampConfidence(0.50
                + Math.min(0.2, (expansionRatio - 1.0) * 0.08)
                + Math.min(0.15, (1.0 - contractionRatio) * 0.25));
            return longSignal(confidence,
                String.format("Low-volume base breakout; contraction %.2fx, expansion %.2fx",
                    contractionRatio, expansionRatio),
                stopLoss,
                takeProfit);
        }

        if (volumeContracted && currentClose <= consolidationHigh) {
            return hold(0.35, "Volume contraction base forming");
        }

        return noTrade("No low-volume breakout");
    }

    public void setContractionBars(int contractionBars) {
        this.contractionBars = Math.max(3, contractionBars);
    }

    public void setBaselineBars(int baselineBars) {
        this.baselineBars = Math.max(5, baselineBars);
    }

    public void setContractionVolumeRatio(double contractionVolumeRatio) {
        this.contractionVolumeRatio = Math.max(0.1, contractionVolumeRatio);
    }

    public void setExpansionVolumeRatio(double expansionVolumeRatio) {
        this.expansionVolumeRatio = Math.max(1.0, expansionVolumeRatio);
    }
}
