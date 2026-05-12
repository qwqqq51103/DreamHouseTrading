package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import org.ta4j.core.Bar;

/**
 * Price breakout confirmed by abnormal volume.
 */
public class VolumeBreakoutSignalStrategy extends DecisionBaseStrategy {

    private int lookbackBars = 20;
    private double volumeMultiplier = 1.8;

    public VolumeBreakoutSignalStrategy() {
        super("VolumeBreakout", "Volume-confirmed price breakout signal");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        if (barSeries == null || barIndex < lookbackBars) {
            updateSignal(SignalType.NO_TRADE, 0.0, "Not enough bars for volume breakout");
            return;
        }
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        double previousHigh = Double.NEGATIVE_INFINITY;
        double previousLow = Double.POSITIVE_INFINITY;
        double volumeSum = 0.0;
        int start = Math.max(0, barIndex - lookbackBars);
        int count = 0;

        for (int i = start; i < barIndex; i++) {
            Bar historyBar = barSeries.getBar(i);
            previousHigh = Math.max(previousHigh, historyBar.getHighPrice().doubleValue());
            previousLow = Math.min(previousLow, historyBar.getLowPrice().doubleValue());
            volumeSum += historyBar.getVolume().doubleValue();
            count++;
        }

        if (count == 0 || previousHigh == Double.NEGATIVE_INFINITY || previousLow == Double.POSITIVE_INFINITY) {
            updateSignal(SignalType.NO_TRADE, 0.0, "No usable history for volume breakout");
            return buildSignal(currentSignal, confidence, signalReason);
        }

        double averageVolume = volumeSum / count;
        double currentVolume = bar.getVolume().doubleValue();
        double close = bar.getClosePrice().doubleValue();
        double volumeRatio = averageVolume > 0.0 ? currentVolume / averageVolume : 0.0;
        boolean volumeConfirmed = volumeRatio >= volumeMultiplier;

        SignalType signal;
        double confidence;
        String reason;
        if (close > previousHigh && volumeConfirmed) {
            signal = SignalType.LONG;
            confidence = Math.min(0.9, 0.35 + Math.min(0.55, (volumeRatio - volumeMultiplier) * 0.2));
            reason = String.format("Breakout %.2f > high %.2f with volume %.2fx >= %.2fx",
                    close, previousHigh, volumeRatio, volumeMultiplier);
        } else if (close < previousLow && volumeConfirmed) {
            signal = SignalType.SHORT;
            confidence = Math.min(0.9, 0.35 + Math.min(0.55, (volumeRatio - volumeMultiplier) * 0.2));
            reason = String.format("Breakdown %.2f < low %.2f with volume %.2fx >= %.2fx",
                    close, previousLow, volumeRatio, volumeMultiplier);
        } else {
            signal = SignalType.HOLD;
            confidence = 0.2;
            reason = String.format("No volume breakout: close %.2f, high %.2f, low %.2f, volume %.2fx",
                    close, previousHigh, previousLow, volumeRatio);
        }

        updateSignal(signal, confidence, reason);
        return buildSignal(signal, confidence, reason);
    }

    public void setLookbackBars(int lookbackBars) {
        this.lookbackBars = Math.max(5, lookbackBars);
    }

    public void setVolumeMultiplier(double volumeMultiplier) {
        this.volumeMultiplier = Math.max(1.0, volumeMultiplier);
    }
}
