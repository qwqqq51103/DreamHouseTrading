package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TradeStatisticsAnalyzerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 5, 9, 0);

    @Test
    void shouldCalculateGrossCommissionTaxSlippageAndNetProfit() {
        BacktestResult result = sampleResult();
        TradeStatisticsAnalyzer.StatisticsReport report =
                new TradeStatisticsAnalyzer(result.getTrades(), result).generateReport();

        assertThat(report.grossProfit).isEqualTo(1_000.0);
        assertThat(report.commission).isEqualTo(29.925);
        assertThat(report.tax).isEqualTo(33.0);
        assertThat(report.slippageCost).isEqualTo(10.0);
        assertThat(report.netProfit).isCloseTo(927.075, within(0.000001));
    }

    @Test
    void shouldCalculateWinRateFromClosedTradesOnly() {
        BacktestResult result = new BacktestResult(START, START.plusMinutes(30), 1_000_000.0);
        result.addTrade(new Trade(START.plusMinutes(1), "2330.TW", TradeType.BUY,
                100, 100.0, 0.0, 0.0, 0.0, 98.0, 110.0, "ENTRY"));
        result.addTrade(new Trade(START.plusMinutes(5), "2330.TW", TradeType.SELL,
                100, 110.0, 0.0, 0.0, 0.0, null, null, "TAKE_PROFIT"));
        result.addTrade(new Trade(START.plusMinutes(10), "2317.TW", TradeType.BUY,
                100, 100.0, 0.0, 0.0, 0.0, 98.0, 110.0, "ENTRY"));
        result.addTrade(new Trade(START.plusMinutes(15), "2317.TW", TradeType.SELL,
                100, 90.0, 0.0, 0.0, 0.0, null, null, "STOP_LOSS"));
        result.addTrade(new Trade(START.plusMinutes(20), "2454.TW", TradeType.BUY,
                100, 100.0, 0.0, 0.0, 0.0, 98.0, 110.0, "ENTRY"));
        result.calculate();

        TradeStatisticsAnalyzer.StatisticsReport report =
                new TradeStatisticsAnalyzer(result.getTrades(), result).generateReport();

        assertThat(report.completedPairs).isEqualTo(2);
        assertThat(report.exitReasonDistribution)
                .containsEntry("TAKE_PROFIT", 1)
                .containsEntry("STOP_LOSS", 1);
    }

    @Test
    void shouldKeepExitReasonStatistics() {
        BacktestResult result = sampleResult();
        result.addTrade(new Trade(START.plusMinutes(10), "2317.TW", TradeType.BUY,
                100, 50.0, 0.0, 0.0, 0.0, 48.0, 55.0, "ENTRY"));
        result.addTrade(new Trade(START.plusMinutes(20), "2317.TW", TradeType.SELL,
                100, 45.0, 0.0, 0.0, 0.0, null, null, "FORCE_CLOSE"));

        TradeStatisticsAnalyzer.StatisticsReport report =
                new TradeStatisticsAnalyzer(result.getTrades(), result).generateReport();

        assertThat(report.exitReasonDistribution)
                .containsEntry("TAKE_PROFIT", 1)
                .containsEntry("FORCE_CLOSE", 1);
    }

    @Test
    void shouldKeepBlockReasonStatistics() {
        BacktestResult result = sampleResult();
        result.addSignalObservation(new BacktestResult.SignalObservation(
                "2330.TW", START, "OPEN_LONG", true, "成本後停利空間不足",
                0.1, "BLOCK_LONG", "RANGE", "RANGE", "semi",
                null, null, null, false, null, null, List.of(), "", "",
                0.8, -0.4, -0.1));

        TradeStatisticsAnalyzer.StatisticsReport report =
                new TradeStatisticsAnalyzer(result.getTrades(), result).generateReport();

        assertThat(report.blockReasonDistribution).containsEntry("成本後停利空間不足", 1L);
    }

    private BacktestResult sampleResult() {
        BacktestResult result = new BacktestResult(START, START.plusMinutes(5), 1_000_000.0);
        result.addTrade(new Trade(START.plusMinutes(1), "2330.TW", TradeType.BUY,
                100, 100.0, 0.001425, 0.0, 5.0, 98.0, 112.0, "ENTRY_REASON"));
        result.addTrade(new Trade(START.plusMinutes(5), "2330.TW", TradeType.SELL,
                100, 110.0, 0.001425, 0.003, 5.0, null, null, "TAKE_PROFIT"));
        result.addSnapshot(START, 1_000_000.0, 1_000_000.0, 0.0, 0);
        result.addSnapshot(START.plusMinutes(5), 1_000_930.075, 1_000_930.075, 0.0, 0);
        result.calculate();
        return result;
    }
}
