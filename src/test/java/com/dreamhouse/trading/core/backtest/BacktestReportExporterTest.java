package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.scanner.RadarScoreComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BacktestReportExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsRadarScoreComponentsInTextAndHtmlReports() {
        LocalDateTime time = LocalDateTime.of(2026, 5, 21, 9, 20);
        BacktestResult result = new BacktestResult(time, time.plusMinutes(5), 1_000_000.0);
        result.addSignalObservation(new BacktestResult.SignalObservation(
                "2330.TW",
                time,
                "OPEN_LONG",
                false,
                "entry",
                0.72,
                "ALLOW_LONG",
                "TREND_UP",
                "TREND_UP",
                "半導體",
                12.0,
                100.0,
                0.2,
                true,
                0.3,
                0.2,
                List.of(new RadarScoreComponent(
                        "SignalRSI",
                        "LONG",
                        0.82,
                        0.9,
                        0.738,
                        "RSI超賣")),
                "SignalRSI[LONG conf=0.820 weight=0.900 long=0.738] - RSI超賣",
                "SignalRSI[LONG conf=0.820 weight=0.900 long=0.738] - RSI超賣",
                1.2,
                -0.4,
                0.7));
        result.addSignalObservation(new BacktestResult.SignalObservation(
                "2317.TW",
                time.plusMinutes(5),
                "NO_ACTION",
                true,
                "量能延續不足：最近 K 線沒有維持放量",
                0.21,
                "BLOCK_LONG",
                "RANGE",
                "RANGE",
                "電子",
                45.0,
                50.0,
                -0.1,
                false,
                -0.2,
                -0.1,
                List.of(new RadarScoreComponent(
                        "VolumeBreakout",
                        "HOLD",
                        0.2,
                        0.85,
                        0.0,
                        "No volume breakout")),
                "VolumeBreakout[HOLD conf=0.200 weight=0.850 long=0.000] - No volume breakout",
                "",
                0.4,
                -0.9,
                -0.3));

        String text = BacktestReportExporter.generateTextReport(result, "SQL 雷達", "M5");
        String html = BacktestReportExporter.generateHtmlReport(result, "SQL 雷達", "M5");

        assertThat(text).contains(
                "加分明細",
                "做多加分項",
                "SignalRSI[LONG",
                "加分條件貢獻統計",
                "硬阻擋後續統計",
                "Volume Sustain 未通過");
        assertThat(html).contains(
                "加分明細",
                "做多加分項",
                "SignalRSI[LONG",
                "加分條件貢獻統計",
                "硬阻擋後續統計",
                "Volume Sustain 未通過");
    }

    @Test
    void shouldExportBlockReasons() {
        BacktestResult result = reportResult();

        String text = BacktestReportExporter.generateTextReport(result, "P0", "M5 DAY_TRADE");

        assertThat(text).contains("SignalRSI=SHORT");
        assertThat(text).doesNotContain("No entry signal");
    }

    @Test
    void shouldExportEntryAndExitReasons() {
        BacktestResult result = reportResult();

        String text = BacktestReportExporter.generateTextReport(result, "P0", "M5 DAY_TRADE");

        assertThat(text).contains("ENTRY_REASON", "TAKE_PROFIT");
    }

    @Test
    void shouldExportCostBreakdown() {
        BacktestResult result = reportResult();

        List<BacktestReportExporter.TradeLifecycle> trades = BacktestReportExporter.pairTrades(result.getTrades());

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).grossProfit()).isEqualTo(1_000.0);
        assertThat(trades.get(0).commission()).isEqualTo(29.925);
        assertThat(trades.get(0).tax()).isEqualTo(33.0);
        assertThat(trades.get(0).slippageCost()).isEqualTo(10.0);
        assertThat(trades.get(0).netProfit()).isCloseTo(927.075, within(0.000001));
    }

    @Test
    void shouldExportRadarScoreComponents() {
        BacktestResult result = reportResult();

        String text = BacktestReportExporter.generateTextReport(result, "P0", "M5 DAY_TRADE");

        assertThat(text).contains(
                "SignalRSI",
                "LONG",
                "conf=0.820",
                "weight=0.900",
                "long=0.738",
                "RSI oversold");
    }

    @Test
    void shouldWriteCsvWithUtf8Bom() throws Exception {
        BacktestResult result = reportResult();
        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            String path = BacktestReportExporter.exportDetailedCsv(result, "P0中文", "M5 DAY_TRADE");

            byte[] bytes = Files.readAllBytes(Path.of(path));
            String csv = Files.readString(Path.of(path));
            assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
            assertThat(csv).contains("SignalRSI", "SignalRSI=SHORT", "ENTRY_REASON", "TAKE_PROFIT");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    private BacktestResult reportResult() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 5, 9, 0);
        BacktestResult result = new BacktestResult(time, time.plusMinutes(10), 1_000_000.0);
        result.addTrade(new Trade(time.plusMinutes(1), "2330.TW", TradeType.BUY,
                100, 100.0, 0.001425, 0.0, 5.0, 98.0, 112.0, "ENTRY_REASON"));
        result.addTrade(new Trade(time.plusMinutes(5), "2330.TW", TradeType.SELL,
                100, 110.0, 0.001425, 0.003, 5.0, null, null, "TAKE_PROFIT"));
        result.addSnapshot(time, 1_000_000.0, 1_000_000.0, 0.0, 0);
        result.addSnapshot(time.plusMinutes(10), 1_000_927.075, 1_000_927.075, 0.0, 0);
        result.addSignalObservation(new BacktestResult.SignalObservation(
                "2330.TW",
                time.plusMinutes(2),
                "OPEN_LONG",
                false,
                "ENTRY_REASON",
                0.72,
                "ALLOW_LONG",
                "TREND_UP",
                "TREND_UP",
                "semi",
                12.0,
                100.0,
                0.2,
                true,
                0.3,
                0.2,
                List.of(new RadarScoreComponent(
                        "SignalRSI",
                        "LONG",
                        0.82,
                        0.9,
                        0.738,
                        "RSI oversold")),
                "SignalRSI[LONG conf=0.820 weight=0.900 long=0.738] - RSI oversold",
                "SignalRSI[LONG conf=0.820 weight=0.900 long=0.738] - RSI oversold",
                1.2,
                -0.4,
                0.7));
        result.addSignalObservation(new BacktestResult.SignalObservation(
                "2317.TW",
                time.plusMinutes(3),
                "OPEN_LONG",
                true,
                "SignalRSI=SHORT",
                0.12,
                "BLOCK_LONG",
                "RANGE",
                "RANGE",
                "semi",
                null,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                "",
                "",
                0.3,
                -0.8,
                -0.2));
        result.calculate();
        return result;
    }
}
