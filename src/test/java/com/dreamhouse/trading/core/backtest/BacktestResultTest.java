package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.scanner.RadarScoreComponent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BacktestResultTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 5, 9, 0);

    @Test
    void shouldCalculateGrossCommissionTaxSlippageAndNetProfit() {
        BacktestResult result = resultWithClosedTrade("TAKE_PROFIT");

        assertThat(result.getGrossProfit()).isEqualTo(1_000.0);
        assertThat(result.getTotalCommission()).isEqualTo(29.925);
        assertThat(result.getTotalTax()).isEqualTo(33.0);
        assertThat(result.getTotalSlippageCost()).isEqualTo(10.0);
        assertThat(result.getNetProfit()).isCloseTo(927.075, within(0.000001));
    }

    @Test
    void shouldCalculateWinRateFromClosedTradesOnly() {
        BacktestResult result = resultWithClosedTrade("TAKE_PROFIT");
        result.addTrade(new Trade(START.plusMinutes(20), "2317.TW", TradeType.BUY,
                100, 50.0, 0.0, 0.0, 0.0, 48.0, 55.0, "ENTRY_ONLY"));
        result.addSnapshot(START.plusMinutes(30), 1_001_000.0, 995_000.0, 6_000.0, 1);

        result.calculate();

        assertThat(result.getWinningTrades()).isEqualTo(1);
        assertThat(result.getLosingTrades()).isZero();
        assertThat(result.getWinRate()).isEqualTo(1.0);
    }

    @Test
    void shouldKeepExitReasonStatistics() {
        BacktestResult result = resultWithClosedTrade("STOP_LOSS");
        result.addTrade(new Trade(START.plusMinutes(20), "2317.TW", TradeType.BUY,
                100, 50.0, 0.0, 0.0, 0.0, 48.0, 55.0, "ENTRY"));
        result.addTrade(new Trade(START.plusMinutes(25), "2317.TW", TradeType.SELL,
                100, 52.0, 0.0, 0.0, 0.0, null, null, "VWAP_BREAK"));

        assertThat(result.getExitReasonStatistics())
                .containsEntry("STOP_LOSS", 1L)
                .containsEntry("VWAP_BREAK", 1L);
    }

    @Test
    void shouldKeepBlockReasonStatistics() {
        BacktestResult result = resultWithClosedTrade("TAKE_PROFIT");
        result.addSignalObservation(observation("SignalRSI=SHORT"));
        result.addSignalObservation(observation("Volume Sustain failed"));

        assertThat(result.getBlockReasonStatistics())
                .containsEntry("SignalRSI=SHORT", 1L)
                .containsEntry("Volume Sustain failed", 1L)
                .doesNotContainKey("No entry signal");
    }

    private BacktestResult resultWithClosedTrade(String exitReason) {
        BacktestResult result = new BacktestResult(START, START.plusMinutes(10), 1_000_000.0);
        result.addTrade(new Trade(START.plusMinutes(1), "2330.TW", TradeType.BUY,
                100, 100.0, 0.001425, 0.0, 5.0, 98.0, 112.0, "ENTRY_REASON"));
        result.addTrade(new Trade(START.plusMinutes(5), "2330.TW", TradeType.SELL,
                100, 110.0, 0.001425, 0.003, 5.0, null, null, exitReason));
        result.addSnapshot(START, 1_000_000.0, 1_000_000.0, 0.0, 0);
        result.addSnapshot(START.plusMinutes(10), 1_000_930.075, 1_000_930.075, 0.0, 0);
        return result;
    }

    private BacktestResult.SignalObservation observation(String reason) {
        return new BacktestResult.SignalObservation(
                "2330.TW",
                START,
                "OPEN_LONG",
                true,
                reason,
                0.0,
                "BLOCK_LONG",
                "RANGE",
                "RANGE",
                "semi",
                10.0,
                100.0,
                0.1,
                false,
                0.0,
                0.0,
                List.of(new RadarScoreComponent("SignalRSI", "SHORT", 0.8, 1.0, 0.0, reason)),
                "SignalRSI[SHORT conf=0.800 weight=1.000 long=0.000] - " + reason,
                "",
                1.0,
                -0.5,
                0.2);
    }
}
