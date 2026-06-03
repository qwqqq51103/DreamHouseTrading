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

class TradeRecordTest {

    @Test
    void shouldPreservePaperTradeSemanticFields() {
        LocalDateTime entryTime = LocalDateTime.of(2026, 5, 13, 9, 35);
        LocalDateTime exitTime = LocalDateTime.of(2026, 5, 13, 10, 5);

        TradeRecord record = new TradeRecord.Builder("T-001", "2330.TW")
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M5)
                .decisionSource(DecisionResult.DecisionSource.AUTO_MONITOR)
                .autoManaged(true)
                .entryTime(entryTime)
                .entryPrice(100.0)
                .entryBarIndex(7)
                .exitTime(exitTime)
                .exitPrice(110.0)
                .exitBarIndex(13)
                .quantity(1000)
                .exitReason(ExitReason.TAKE_PROFIT)
                .grossProfit(10_000.0)
                .commission(142.5)
                .tax(150.0)
                .slippageCost(50.0)
                .netProfit(9_657.5)
                .entryReason("VWAP breakout with volume sustain")
                .exitReasonText("TAKE_PROFIT")
                .blockReason("none")
                .setupScore(0.82)
                .radarScoreComponents("MovingAverageTrend:LONG confidence=0.90 weight=1.00 long=0.90 reason=EMA up")
                .strategySettingSummary("A template, M5, volumeMultiplier=1.5")
                .strategyName("AutoMonitor-A")
                .notes("paper trade semantic fields")
                .build();

        assertThat(record.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.AUTO_MONITOR);
        assertThat(record.isAutoManaged()).isTrue();
        assertThat(record.getTradeMode()).isEqualTo(TradeMode.DAY_TRADE);
        assertThat(record.getTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(record.getQuantity()).isEqualTo(1000);
        assertThat(record.getGrossProfit()).isEqualTo(10_000.0);
        assertThat(record.getCommission()).isEqualTo(142.5);
        assertThat(record.getTax()).isEqualTo(150.0);
        assertThat(record.getSlippageCost()).isEqualTo(50.0);
        assertThat(record.getNetProfit()).isEqualTo(9_657.5);
        assertThat(record.getEntryReason()).contains("VWAP breakout");
        assertThat(record.getExitReasonText()).isEqualTo("TAKE_PROFIT");
        assertThat(record.getBlockReason()).isEqualTo("none");
        assertThat(record.getSetupScore()).isEqualTo(0.82);
        assertThat(record.getRadarScoreComponents()).contains("MovingAverageTrend", "long=0.90");
        assertThat(record.getStrategySettingSummary()).contains("A template", "M5");
        assertThat(record.getHoldingBars()).isEqualTo(6);
        assertThat(record.getHoldingMinutes()).isEqualTo(30);
    }

    @Test
    void shouldNotDropDecisionSourceWhenExportingOrBuildingRecord(@TempDir Path tempDir) throws Exception {
        TradeRecord autoMonitor = semanticRecord("T-002", DecisionResult.DecisionSource.AUTO_MONITOR);
        TradeRecord radarReplay = semanticRecord("T-003", DecisionResult.DecisionSource.RADAR_REPLAY);
        TradeRecord backtest = semanticRecord("T-004", DecisionResult.DecisionSource.BACKTEST);

        assertThat(autoMonitor.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.AUTO_MONITOR);
        assertThat(radarReplay.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.RADAR_REPLAY);
        assertThat(backtest.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.BACKTEST);
        assertThat(autoMonitor.getDecisionSource()).isNotEqualTo(DecisionResult.DecisionSource.MANUAL);
        assertThat(radarReplay.getDecisionSource()).isNotEqualTo(DecisionResult.DecisionSource.MANUAL);
        assertThat(backtest.getDecisionSource()).isNotEqualTo(DecisionResult.DecisionSource.MANUAL);

        Path csv = tempDir.resolve("trade-records.csv");
        LogExporter.exportToCSV(List.of(autoMonitor, radarReplay, backtest), csv.toString());
        String content = Files.readString(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("decision_source", "auto_managed", "timeframe", "slippage_cost");
        assertThat(content).contains("AUTO_MONITOR", "RADAR_REPLAY", "BACKTEST");
        assertThat(content).contains("true", "M1", "radar long", "fixed test config");
    }

    private TradeRecord semanticRecord(String tradeId, DecisionResult.DecisionSource source) {
        return new TradeRecord.Builder(tradeId, "2317.TW")
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .decisionSource(source)
                .autoManaged(source == DecisionResult.DecisionSource.AUTO_MONITOR)
                .entryTime(LocalDateTime.of(2026, 5, 13, 9, 40))
                .exitTime(LocalDateTime.of(2026, 5, 13, 10, 0))
                .entryPrice(50.0)
                .exitPrice(52.0)
                .quantity(1000)
                .grossProfit(2_000.0)
                .commission(80.0)
                .tax(78.0)
                .slippageCost(20.0)
                .entryReason("radar long")
                .exitReasonText("FORCE_CLOSE")
                .radarScoreComponents("VolumeBreakout:LONG confidence=0.80 weight=1.00 long=0.80")
                .strategySettingSummary("fixed test config")
                .build();
    }
}
