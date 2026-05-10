package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.SignalType;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingSignalStrategiesTest {

    @Test
    void movingAverageAlignmentEmitsLongSignalForBullishStack() {
        BarSeries series = new BaseBarSeries("MA");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 35; i++) {
            double close = 100.0 + i * 0.6;
            series.addBar(bar(start.plusHours(i), close - 0.3, close + 0.5, close - 0.7, close, 1000.0));
        }

        MovingAverageAlignmentStrategy strategy = new MovingAverageAlignmentStrategy();
        strategy.setPeriods(5, 10, 20);
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() > 0.5);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    @Test
    void platformBreakoutEmitsLongSignalAfterCompactRange() {
        BarSeries series = new BaseBarSeries("PLATFORM");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 16; i++) {
            series.addBar(bar(start.plusHours(i), 100.0, 101.0, 99.2, 100.1, 1000.0));
        }
        series.addBar(bar(start.plusHours(16), 101.2, 102.6, 101.0, 102.2, 1600.0));

        PlatformBreakoutStrategy strategy = new PlatformBreakoutStrategy();
        strategy.setPlatformLookbackBars(16);
        strategy.setMaxPlatformRangePercent(0.05);
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() > 0.5);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    @Test
    void lowVolumeConsolidationBreakoutEmitsLongSignal() {
        BarSeries series = new BaseBarSeries("LOWVOL");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 8; i++) {
            series.addBar(bar(start.plusHours(i), 100.0, 101.0, 99.2, 100.0, 1000.0));
        }
        for (int i = 8; i < 12; i++) {
            series.addBar(bar(start.plusHours(i), 100.0, 100.8, 99.5, 100.0, 500.0));
        }
        series.addBar(bar(start.plusHours(12), 100.4, 102.0, 100.0, 101.5, 1100.0));

        LowVolumeConsolidationBreakoutStrategy strategy = new LowVolumeConsolidationBreakoutStrategy();
        strategy.setBaselineBars(8);
        strategy.setContractionBars(4);
        strategy.setExpansionVolumeRatio(1.6);
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() >= 0.5);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    private static BaseBar bar(LocalDateTime startTime, double open, double high, double low,
                               double close, double volume) {
        Duration duration = Duration.ofHours(1);
        return new BaseBar(
            duration,
            startTime.plus(duration).atZone(ZoneId.systemDefault()),
            BigDecimal.valueOf(open),
            BigDecimal.valueOf(high),
            BigDecimal.valueOf(low),
            BigDecimal.valueOf(close),
            BigDecimal.valueOf(volume));
    }
}
