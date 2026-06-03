package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGeneratorTest {

    @Test
    void shouldExportEntryAndExitReasonsAndCostBreakdown() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
        BacktestResult result = new BacktestResult(start, start.plusMinutes(5), 1_000_000.0);
        result.addTrade(new Trade(start.plusMinutes(1), "2330.TW", TradeType.BUY,
                100, 100.0, 0.001425, 0.0, 5.0, 98.0, 112.0, "ENTRY_REASON"));
        result.addTrade(new Trade(start.plusMinutes(5), "2330.TW", TradeType.SELL,
                100, 110.0, 0.001425, 0.003, 5.0, null, null, "TAKE_PROFIT"));
        result.addSnapshot(start, 1_000_000.0, 1_000_000.0, 0.0, 0);
        result.addSnapshot(start.plusMinutes(5), 1_000_930.075, 1_000_930.075, 0.0, 0);
        result.calculate();

        String html = ReportGenerator.generateHtmlReport(result, "P0");

        assertThat(html).contains("ENTRY_REASON", "TAKE_PROFIT", "tax", "slippageCost", "netAmount");
        assertThat(html).contains("5.00", "33.00");
    }
}
