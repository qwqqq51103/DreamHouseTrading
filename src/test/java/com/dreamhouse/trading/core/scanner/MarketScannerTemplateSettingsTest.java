package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.monitor.SignalMonitorConfig;
import com.dreamhouse.trading.core.monitor.SignalMonitorTemplate;
import com.dreamhouse.trading.core.monitor.SignalMonitorTemplateManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketScannerTemplateSettingsTest {

    @Test
    void shouldScannerUseTemplateAppliedFlags() {
        SignalMonitorTemplate template = new SignalMonitorTemplateManager(null).builtInTemplates().stream()
                .filter(candidate -> candidate.name().equals(SignalMonitorTemplateManager.GROUP_A_NAME))
                .findFirst()
                .orElseThrow();
        SignalMonitorConfig applied = SignalMonitorTemplateManager.copyMonitorConfig(template.monitorConfig());
        applied.getRadarStrategyConfig().setRsiEnabled(false);
        applied.getRadarStrategyConfig().setRequireRsiEntryConfirmation(false);
        applied.getRadarStrategyConfig().setBlockBreakoutOnRsiOverbought(false);
        applied.getRadarStrategyConfig().setRequireBreakoutNextBarConfirmation(false);
        applied.getRadarStrategyConfig().setRequireBreakoutContinuation(false);
        applied.getRadarStrategyConfig().setMaxEntryRiseFromRecentLowPercent(1.0);
        applied.getRadarStrategyConfig().setAtrChaseLimitEnabled(false);
        DecisionConfig decisionConfig = SignalMonitorTemplateManager.copyDecisionConfig(template.decisionConfig());
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);

        MarketScanResult result = new MarketScannerService(new BelowVwapBreakoutFeed()).scan(
                "TEST",
                requestFrom(applied, decisionConfig));

        assertThat(applied.getRadarStrategyConfig().isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getBlockReason()).contains("VWAP").doesNotContain("No entry signal");
        assertThat(result.getScoreComponents()).isNotEmpty();
    }

    @Test
    void shouldReplayUseSameTemplateSettingsAsAutoMonitor() {
        SignalMonitorTemplate template = new SignalMonitorTemplateManager(null).builtInTemplates().stream()
                .filter(candidate -> candidate.name().equals(SignalMonitorTemplateManager.GROUP_C_NAME))
                .findFirst()
                .orElseThrow();
        SignalMonitorConfig applied = SignalMonitorTemplateManager.copyMonitorConfig(template.monitorConfig());
        DecisionConfig decisionConfig = SignalMonitorTemplateManager.copyDecisionConfig(template.decisionConfig());

        MarketScannerService.ScanRequest autoMonitorRequest = requestFrom(applied, decisionConfig)
                .asOfTime(LocalDateTime.of(2026, 1, 5, 10, 0));
        MarketScannerService.ScanRequest replayRequest = autoMonitorRequest.copy()
                .asOfTime(LocalDateTime.of(2026, 1, 5, 10, 5));

        assertThat(replayRequest.getTimeframe()).isEqualTo(autoMonitorRequest.getTimeframe());
        assertThat(replayRequest.getBarCount()).isEqualTo(autoMonitorRequest.getBarCount());
        assertThat(replayRequest.getTradeMode()).isEqualTo(TradeMode.DAY_TRADE);
        assertThat(replayRequest.getRadarStrategyConfig().getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.SMA);
        assertThat(replayRequest.getRadarStrategyConfig().getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(replayRequest.getRadarStrategyConfig().getSlowMovingAveragePeriod()).isEqualTo(21);
        assertThat(replayRequest.getRadarStrategyConfig().getVolumeMultiplier()).isEqualTo(1.45);
        assertThat(replayRequest.getRadarStrategyConfig().isBlockMovingAverageOnlyEntry()).isTrue();
        assertThat(replayRequest.getRadarStrategyConfig().isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(replayRequest.getDecisionConfig().getRiskConfig().isAllowShortSelling()).isFalse();
    }

    private static MarketScannerService.ScanRequest requestFrom(
            SignalMonitorConfig monitorConfig,
            DecisionConfig decisionConfig) {
        RadarStrategyConfig radar = monitorConfig.getRadarStrategyConfig();
        return MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(radar.resolveTimeframe(TradeMode.DAY_TRADE))
                .barCount(radar.resolveBarCount(TradeMode.DAY_TRADE))
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radar);
    }

    private static class BelowVwapBreakoutFeed implements MarketDataFeed {
        @Override
        public void subscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void unsubscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            List<Bar> bars = new ArrayList<>();
            LocalDateTime start = LocalDateTime.of(2026, 1, 5, 9, 0);
            for (int i = 0; i < 40; i++) {
                bars.add(new Bar(start.plusMinutes(i), 120.0, 120.4, 119.6, 120.0, 10_000));
            }
            double close = 100.0;
            for (int i = 40; i < 79; i++) {
                double open = close;
                close += 0.10;
                bars.add(new Bar(start.plusMinutes(i), open, close + 0.1, open - 0.1, close, 100));
            }
            bars.add(new Bar(start.plusMinutes(79), close, close + 2.4, close - 0.1, close + 2.0, 5_000));
            return bars;
        }
    }
}
