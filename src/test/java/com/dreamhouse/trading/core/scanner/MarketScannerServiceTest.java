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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketScannerServiceTest {

    @Test
    void scanUsesConfiguredMovingAverageAndVolumeBreakoutSignals() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.1);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setBlockBreakoutOnRsiOverbought(false);
        radarConfig.setRequireBreakoutContinuation(false);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isTrue();
        assertThat(result.getDecisionResult().getAction()).isEqualTo(DecisionResult.Action.OPEN_LONG);
        assertThat(result.getRawSignalSummary()).contains("MovingAverageTrend");
        assertThat(result.getRawSignalSummary()).contains("VolumeBreakout");
        assertThat(result.getScoreComponents())
                .extracting(RadarScoreComponent::name)
                .contains("MovingAverageTrend", "VolumeBreakout");
        assertThat(result.getLongBonusSummary()).contains("MovingAverageTrend", "long=");
    }

    @Test
    void rsiShortBlocksOpenLongEvenWhenTrendAndVolumeAreLong() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(true);
        radarConfig.setRsiPeriod(5);
        radarConfig.setRsiOversold(20.0);
        radarConfig.setRsiOverbought(60.0);
        radarConfig.setRsiWeight(0.1);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(1.0);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setVolumeBreakoutWeight(1.0);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setBlockBreakoutOnRsiOverbought(false);
        radarConfig.setRequireBreakoutContinuation(false);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getRawSignalSummary()).contains("SignalRSI:SHORT");
        assertThat(result.getRawSignalSummary()).contains("MovingAverageTrend:LONG");
        assertThat(result.getBlockReason()).contains("SignalRSI=SHORT");
    }

    @Test
    void vwapFilterBlocksOpenLongWhenPriceIsBelowVwap() {
        MarketScannerService scanner = new MarketScannerService(new BelowVwapBreakoutFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(1.0);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setVolumeBreakoutWeight(1.0);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setRequirePriceAboveVwapForLong(true);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getRawSignalSummary()).contains("VolumeBreakout:LONG");
        assertThat(result.getBlockReason()).contains("VWAP");
    }

    @Test
    void nextBarBreakoutConfirmationBlocksInitialBreakoutBar() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setRequireBreakoutNextBarConfirmation(true);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getRawSignalSummary()).contains("VolumeBreakout:LONG");
        assertThat(result.getBlockReason()).contains("前一根 K 線不是有效放量突破");
    }

    @Test
    void confirmedBreakoutFollowThroughCanConfirmBlockedMovingAverageOnlyEntry() {
        MarketScannerService scanner = new MarketScannerService(new ConfirmedBreakoutFollowThroughFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(1.0);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setVolumeBreakoutWeight(1.0);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setRequireBreakoutNextBarConfirmation(true);
        radarConfig.setBlockMovingAverageOnlyEntry(true);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isTrue();
        assertThat(result.getRawSignalSummary()).contains("MovingAverageTrend:LONG");
        assertThat(result.getRawSignalSummary()).doesNotContain("VolumeBreakout:LONG");
    }

    @Test
    void chaseLimitBlocksEntriesFarAboveRecentLow() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setMaxEntryRiseFromRecentLowPercent(0.01);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getBlockReason()).contains("追價限制");
    }

    @Test
    void bConvergenceTemplateUsesChaseLimitWithoutOverFiltering() {
        RadarStrategyConfig config = RadarStrategyConfig.createBConvergenceTemplate();

        assertThat(config.isRequireBreakoutNextBarConfirmation()).isFalse();
        assertThat(config.isBlockMovingAverageOnlyEntry()).isFalse();
        assertThat(config.getMaxEntryRiseFromRecentLowPercent()).isEqualTo(0.03);
        assertThat(config.isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(config.isRequireBreakoutContinuation()).isTrue();
    }

    @Test
    void movingAverageOnlyEntryIsBlockedWhenAdditionalConfirmationIsRequired() {
        MarketScannerService scanner = new MarketScannerService(new SmoothUptrendFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(1.0);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(10.0);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setBlockMovingAverageOnlyEntry(true);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getRawSignalSummary()).contains("MovingAverageTrend:LONG");
        assertThat(result.getRawSignalSummary()).doesNotContain("VolumeBreakout:LONG");
        assertThat(result.getBlockReason()).contains("EMA");
    }

    @Test
    void noEntrySignalIsSplitIntoConcreteRadarReasons() {
        MarketScannerService scanner = new MarketScannerService(new DowntrendFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.95);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(10.0);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.getBlockReason()).contains("未進場");
        assertThat(result.getBlockReason()).doesNotContain("No entry signal");
    }

    @Test
    void dayTradeAbcTemplatesDisableInternalMarketFilterAndExposeWarmupComparison() {
        RadarStrategyConfig groupA = RadarStrategyConfig.createDayTradeGroupATemplate();
        RadarStrategyConfig groupB = RadarStrategyConfig.createDayTradeGroupBTemplate();
        RadarStrategyConfig groupC = RadarStrategyConfig.createDayTradeGroupCTemplate();

        assertThat(groupA.getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(groupA.getSlowMovingAveragePeriod()).isEqualTo(34);
        assertThat(groupA.getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.EMA);
        assertThat(groupA.isBacktestCrossDayWarmupEnabled()).isTrue();
        assertThat(groupA.isBlockMovingAverageOnlyEntry()).isTrue();
        assertThat(groupA.isRequireBreakoutNextBarConfirmation()).isTrue();
        assertThat(groupA.getMinimumEntryScore()).isEqualTo(0.45);
        assertThat(groupB.getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(groupB.getSlowMovingAveragePeriod()).isEqualTo(21);
        assertThat(groupB.getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.EMA);
        assertThat(groupB.isBacktestCrossDayWarmupEnabled()).isFalse();
        assertThat(groupB.isBlockMovingAverageOnlyEntry()).isTrue();
        assertThat(groupB.isRequireBreakoutNextBarConfirmation()).isFalse();
        assertThat(groupB.getMinimumEntryScore()).isEqualTo(0.40);
        assertThat(groupC.getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(groupC.getSlowMovingAveragePeriod()).isEqualTo(21);
        assertThat(groupC.getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.SMA);
        assertThat(groupC.isBacktestCrossDayWarmupEnabled()).isFalse();
        assertThat(groupC.isBlockMovingAverageOnlyEntry()).isTrue();
        assertThat(groupC.isRequireBreakoutNextBarConfirmation()).isFalse();
        assertThat(groupC.getMinimumEntryScore()).isEqualTo(0.40);
        assertThat(groupC.getVolumeMultiplier()).isEqualTo(1.45);
        assertThat(List.of(groupA, groupB, groupC))
                .allSatisfy(config -> assertThat(config.isMarketRegimeFilterEnabled()).isFalse());
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

    @Test
    void weakMarketBlocksOpenLongWhenSymbolIsNotAboveVwap() {
        DeterministicFeed feed = new DeterministicFeed();
        MarketScannerService scanner = new MarketScannerService(feed);
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.1);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setMarketRegimeFilterEnabled(true);
        radarConfig.setWeakMarketStrictLongEnabled(true);
        radarConfig.setWeakMarketLongPolicy(WeakMarketLongPolicy.ALLOW_EXTREME_STRENGTH_ONLY);

        MarketContextSnapshot context = weakContext(
                feed.fetchHistoricalBars("TEST", Timeframe.M1, 80),
                new SymbolMarketContext(
                        "TEST",
                        "TAIEX",
                        "半導體",
                        110.0,
                        1.0,
                        -1.0,
                        0.0,
                        2.0,
                        1.0,
                        120.0,
                        0.2,
                        true,
                        false,
                        "未站上 VWAP"));

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig)
                .marketContext(context));

        assertThat(result.hasTradeSignal()).isFalse();
        assertThat(result.getBlockReason()).contains("VWAP");
        assertThat(result.getMarketRegime()).isEqualTo(MarketRegime.WEAK);
    }

    @Test
    void atrRiskAddsStopLossTakeProfitAndReason() {
        MarketScannerService scanner = new MarketScannerService(new DeterministicFeed());
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.1);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setVolumeBreakoutEnabled(true);
        radarConfig.setBreakoutLookbackBars(12);
        radarConfig.setVolumeMultiplier(1.2);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setAtrRiskEnabled(true);
        radarConfig.setAtrPeriod(14);
        radarConfig.setAtrChaseLimitMultiplier(100.0);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(80)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig));

        assertThat(result.hasTradeSignal()).isTrue();
        assertThat(result.getSuggestedStopLoss()).isNotNull();
        assertThat(result.getSuggestedTakeProfit()).isNotNull();
        assertThat(result.getRawSignalSummary()).contains("ATR");
        assertThat(result.getReason()).contains("ATR");
    }

    private MarketContextSnapshot weakContext(List<Bar> bars, SymbolMarketContext symbolContext) {
        return new MarketContextSnapshot(
                MarketRegime.WEAK,
                "弱勢盤",
                new MarketMetric("TAIEX", 100.0, -1.0, 101.0, -0.1, false, 1_000.0, 20),
                new MarketMetric("TPEx", 100.0, -1.2, 101.0, -0.1, false, 1_000.0, 20),
                Map.of("TEST", symbolContext),
                Map.of("半導體", new IndustryStrength("半導體", 0.0, 1_000.0, 1, 1, 0.7)),
                Map.of("TEST", bars),
                LocalDateTime.now());
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

    private static class SmoothUptrendFeed implements MarketDataFeed {
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
                close += 0.15;
                bars.add(new Bar(
                        start.plusMinutes(i),
                        open,
                        close + 0.05,
                        open - 0.05,
                        close,
                        1_000));
            }
            return bars;
        }
    }

    private static class ConfirmedBreakoutFollowThroughFeed implements MarketDataFeed {
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
            for (int i = 0; i < 78; i++) {
                double open = close;
                close += 0.15;
                bars.add(new Bar(
                        start.plusMinutes(i),
                        open,
                        close + 0.05,
                        open - 0.05,
                        close,
                        1_000));
            }
            bars.add(new Bar(
                    start.plusMinutes(78),
                    close,
                    120.20,
                    close - 0.10,
                    120.00,
                    5_000));
            bars.add(new Bar(
                    start.plusMinutes(79),
                    120.00,
                    120.15,
                    119.90,
                    120.10,
                    1_000));
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
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 9, 0);
            for (int i = 0; i < 40; i++) {
                bars.add(new Bar(
                        start.plusMinutes(i),
                        120.0,
                        120.4,
                        119.6,
                        120.0,
                        10_000));
            }

            double close = 100.0;
            for (int i = 40; i < 79; i++) {
                double open = close;
                close += 0.10;
                bars.add(new Bar(
                        start.plusMinutes(i),
                        open,
                        close + 0.1,
                        open - 0.1,
                        close,
                        100));
            }
            bars.add(new Bar(
                    start.plusMinutes(79),
                    close,
                    close + 2.4,
                    close - 0.1,
                    close + 2.0,
                    5_000));
            return bars;
        }
    }
}
