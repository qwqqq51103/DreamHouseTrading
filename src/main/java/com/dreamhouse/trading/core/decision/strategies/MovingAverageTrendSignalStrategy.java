package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Moving average trend signal for radar scans.
 */
public class MovingAverageTrendSignalStrategy extends DecisionBaseStrategy {

    private int fastPeriod = 5;
    private int slowPeriod = 20;
    private RadarStrategyConfig.MovingAverageType averageType = RadarStrategyConfig.MovingAverageType.EMA;
    private org.ta4j.core.Indicator<Num> fastAverage;
    private org.ta4j.core.Indicator<Num> slowAverage;
    private boolean initialized;

    public MovingAverageTrendSignalStrategy() {
        super("MovingAverageTrend", "Moving average trend signal");
    }

    @Override
    public void initialize(BarSeries barSeries) {
        super.initialize(barSeries);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        if (averageType == RadarStrategyConfig.MovingAverageType.SMA) {
            fastAverage = new SMAIndicator(closePrice, fastPeriod);
            slowAverage = new SMAIndicator(closePrice, slowPeriod);
        } else {
            fastAverage = new EMAIndicator(closePrice, fastPeriod);
            slowAverage = new EMAIndicator(closePrice, slowPeriod);
        }
        initialized = true;
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        if (!initialized || barIndex < slowPeriod) {
            updateSignal(SignalType.NO_TRADE, 0.0, "Not enough bars for moving average trend");
            return;
        }
        generateSignal(barIndex, bar);
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        double fast = fastAverage.getValue(barIndex).doubleValue();
        double slow = slowAverage.getValue(barIndex).doubleValue();
        double close = bar.getClosePrice().doubleValue();
        double spread = slow != 0.0 ? Math.abs(fast - slow) / slow : 0.0;
        double priceDistance = slow != 0.0 ? Math.abs(close - slow) / slow : 0.0;
        double confidence = Math.min(0.9, 0.25 + (spread * 12.0) + (priceDistance * 4.0));

        SignalType signal;
        String reason;
        if (fast > slow && close >= fast) {
            signal = SignalType.LONG;
            reason = String.format("%s trend up: fast %.2f > slow %.2f, close %.2f >= fast",
                    averageType, fast, slow, close);
        } else if (fast < slow && close <= fast) {
            signal = SignalType.SHORT;
            reason = String.format("%s trend down: fast %.2f < slow %.2f, close %.2f <= fast",
                    averageType, fast, slow, close);
        } else {
            signal = SignalType.HOLD;
            confidence = 0.25;
            reason = String.format("%s trend neutral: fast %.2f, slow %.2f, close %.2f",
                    averageType, fast, slow, close);
        }

        updateSignal(signal, confidence, reason);
        return buildSignal(signal, confidence, reason);
    }

    public void setFastPeriod(int fastPeriod) {
        this.fastPeriod = Math.max(2, fastPeriod);
    }

    public void setSlowPeriod(int slowPeriod) {
        this.slowPeriod = Math.max(3, slowPeriod);
    }

    public void setAverageType(RadarStrategyConfig.MovingAverageType averageType) {
        this.averageType = averageType != null ? averageType : RadarStrategyConfig.MovingAverageType.EMA;
    }
}
