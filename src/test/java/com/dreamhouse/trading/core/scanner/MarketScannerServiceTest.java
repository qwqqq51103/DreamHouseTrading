package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.finmind.FinMindAccessDeniedException;
import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketScannerServiceTest {

    @Test
    void scanUsesConfiguredRsiMovingAverageAndVolumeBreakoutSignals() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.1);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(true);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setRsiOversold(80.0);
        radarConfig.setRsiOverbought(95.0);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isTrue();
        assertThat(result.getDecisionResult().getAction()).isEqualTo(DecisionResult.Action.OPEN_LONG);
        assertThat(result.getRawSignalSummary()).contains("SignalRSI");
        assertThat(result.getRawSignalSummary()).contains("MovingAverageTrend");
        assertThat(result.getRawSignalSummary()).contains("VolumeBreakout");
    }

    @Test
    void scanRethrowsGlobalFinMindAccessFailures() {
        MarketScannerService scanner = new MarketScannerService(new AccessDeniedFeed());

        assertThatThrownBy(() -> scanner.scan("2330.TW", MarketScannerService.ScanRequest.createDefault()))
                .isInstanceOf(FinMindAccessDeniedException.class)
                .hasMessageContaining("ip banned");
    }

    @Test
    void rsiOversoldLongIsBlockedWhenTrendIsDownAndVolumeDoesNotConfirm() {
        MarketScannerService scanner = new MarketScannerService(new DowntrendFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(true);
        radarConfig.setRsiOversold(80.0);
        radarConfig.setRsiOverbought(99.0);
        radarConfig.setRsiWeight(1.0);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(0.0);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(10.0);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getBlockReason()).contains("RSI 超賣訊號缺少 EMA");
    }

    private static class DeterministicFeed implements MarketDataFeed {
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
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);
            double close = 100.0;
            for (int i = 0; i < 79; i++) {
                double open = close;
                close += 0.2;
                bars.add(new Bar(
                        start.plusMinutes(i),
                        open,
                        close + 0.1,
                        open - 0.1,
                        close,
                        1_000));
            }
            bars.add(new Bar(
                    start.plusMinutes(79),
                    close,
                    close + 3.5,
                    close - 0.2,
                    close + 3.0,
                    5_000));
            return bars;
        }
    }

    private static class AccessDeniedFeed implements MarketDataFeed {
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
            throw new FinMindAccessDeniedException("FinMind authentication or permission failed: ip banned", 403, "403");
        }
    }

    private static class DowntrendFeed implements MarketDataFeed {
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
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);
            double close = 100.0;
            for (int i = 0; i < 80; i++) {
                double open = close;
                close -= i > 60 ? 0.45 : 0.10;
                bars.add(new Bar(
                        start.plusMinutes(i),
                        open,
                        open + 0.1,
                        close - 0.1,
                        close,
                        1_000));
            }
            return bars;
        }
    }
}
