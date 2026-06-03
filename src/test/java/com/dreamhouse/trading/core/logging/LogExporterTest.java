package com.dreamhouse.trading.core.logging;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogExporterTest {

    private static final List<String> EXPECTED_HEADER = List.of(
            "trade_id",
            "symbol",
            "trade_mode",
            "strategy_name",
            "entry_time",
            "entry_price",
            "entry_bar_index",
            "quantity",
            "entry_commission_rate",
            "exit_time",
            "exit_price",
            "exit_bar_index",
            "exit_commission_rate",
            "exit_reason",
            "stop_loss",
            "take_profit",
            "trailing_stop",
            "gross_profit",
            "net_profit",
            "return_percent",
            "risk_reward_ratio",
            "mae",
            "mfe",
            "holding_bars",
            "holding_minutes",
            "holding_days",
            "notes",
            "decision_source",
            "auto_managed",
            "timeframe",
            "commission",
            "tax",
            "slippage_cost",
            "entry_reason",
            "exit_reason_text",
            "block_reason",
            "setup_score",
            "radar_score_components",
            "strategy_setting_summary");

    @Test
    void shouldWriteUtf8Bom(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("trades.csv");

        LogExporter.exportToCSV(List.of(record()), csv.toString());

        byte[] bytes = Files.readAllBytes(csv);
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    void shouldExportStableAsciiHeaderOrder(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("trades.csv");

        LogExporter.exportToCSV(List.of(record()), csv.toString());

        String content = Files.readString(csv, StandardCharsets.UTF_8);
        String header = content.lines().findFirst().orElseThrow();
        assertThat(header.charAt(0)).isEqualTo('\ufeff');
        assertThat(header.substring(1).split(",", -1)).containsExactlyElementsOf(EXPECTED_HEADER);
    }

    @Test
    void shouldExportSemanticFields(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("trades.csv");

        LogExporter.exportToCSV(List.of(record()), csv.toString());

        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertThat(content).contains(
                "decision_source",
                "auto_managed",
                "trade_mode",
                "timeframe",
                "quantity",
                "entry_price",
                "exit_price",
                "gross_profit",
                "commission",
                "tax",
                "slippage_cost",
                "net_profit",
                "entry_reason",
                "exit_reason_text",
                "block_reason",
                "radar_score_components",
                "strategy_setting_summary");
        assertThat(content).contains(
                "BACKTEST",
                "false",
                "M5",
                "VWAP breakout",
                "STOP_LOSS",
                "VolumeBreakout:LONG",
                "A template");
    }

    private TradeRecord record() {
        return new TradeRecord.Builder("T-001", "2330.TW")
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M5)
                .decisionSource(DecisionResult.DecisionSource.BACKTEST)
                .autoManaged(false)
                .entryTime(LocalDateTime.of(2026, 5, 13, 9, 35))
                .exitTime(LocalDateTime.of(2026, 5, 13, 10, 5))
                .entryPrice(100.0)
                .exitPrice(110.0)
                .quantity(1000)
                .exitReason(ExitReason.STOP_LOSS)
                .grossProfit(10_000.0)
                .commission(210.0)
                .tax(330.0)
                .slippageCost(35.0)
                .netProfit(9_425.0)
                .entryReason("VWAP breakout")
                .exitReasonText("STOP_LOSS")
                .blockReason("none")
                .setupScore(0.82)
                .radarScoreComponents("VolumeBreakout:LONG confidence=0.80 weight=1.00 long=0.80")
                .strategySettingSummary("A template")
                .strategyName("Backtest-A")
                .build();
    }
}
