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

class DayTradingSignalStrategiesTest {

    @Test
    void openingRangeBreakoutEmitsLongSignal() {
        BarSeries series = new BaseBarSeries("ORB");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 6; i++) {
            series.addBar(bar(start.plusMinutes(i * 5L), 100.0, 101.0, 99.0, 100.2, 1000.0));
        }
        series.addBar(bar(start.plusMinutes(30), 100.8, 102.4, 100.6, 102.0, 1800.0));

        OpeningRangeBreakoutStrategy strategy = new OpeningRangeBreakoutStrategy();
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() > 0.5);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    @Test
    void volumeBreakoutEmitsLongSignalOnPriceAndVolumeExpansion() {
        BarSeries series = new BaseBarSeries("VOL");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 20; i++) {
            series.addBar(bar(start.plusMinutes(i * 5L), 100.0, 100.8, 99.4, 100.1, 1000.0));
        }
        series.addBar(bar(start.plusMinutes(100), 100.5, 103.0, 100.2, 102.5, 2400.0));

        VolumeBreakoutStrategy strategy = new VolumeBreakoutStrategy();
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() >= 0.5);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    @Test
    void vwapPullbackEmitsLongSignalAfterReclaim() {
        BarSeries series = new BaseBarSeries("VWAP");
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        for (int i = 0; i < 8; i++) {
            series.addBar(bar(start.plusMinutes(i * 5L), 100.0, 100.5, 99.7, 100.1, 1000.0));
        }
        series.addBar(bar(start.plusMinutes(40), 100.0, 100.2, 99.5, 99.9, 1000.0));
        series.addBar(bar(start.plusMinutes(45), 99.9, 101.1, 99.8, 100.8, 1300.0));

        VwapPullbackStrategy strategy = new VwapPullbackStrategy();
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.LONG, strategy.getSignal());
        assertTrue(strategy.getConfidence() > 0.45);
        assertNotNull(strategy.getSuggestedStopLoss());
        assertNotNull(strategy.getSuggestedTakeProfit());
    }

    @Test
    void closeGuardEmitsExitSignalNearSessionClose() {
        BarSeries series = new BaseBarSeries("CLOSE");
        series.addBar(bar(LocalDateTime.of(2026, 1, 5, 13, 20), 100.0, 100.2, 99.8, 100.1, 1000.0));

        DayTradeCloseGuardStrategy strategy = new DayTradeCloseGuardStrategy();
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());

        assertEquals(SignalType.EXIT, strategy.getSignal());
        assertTrue(strategy.getConfidence() >= 0.9);
    }

    private static BaseBar bar(LocalDateTime startTime, double open, double high, double low,
                               double close, double volume) {
        Duration duration = Duration.ofMinutes(5);
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
