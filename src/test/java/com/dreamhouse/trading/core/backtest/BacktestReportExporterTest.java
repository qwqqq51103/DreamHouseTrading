package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.scanner.RadarScoreComponent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestReportExporterTest {

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
}
