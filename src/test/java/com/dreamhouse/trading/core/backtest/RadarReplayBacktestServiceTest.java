package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.monitor.AutoMonitorExecutionGate;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RadarReplayBacktestServiceTest {

    private static final String SYMBOL = "2330.TW";
    private static final LocalDateTime SESSION_START = LocalDateTime.of(2026, 1, 5, 9, 0);

    @Test
    void shouldUseNextBarOpenToAvoidLookaheadBias() {
        List<Bar> bars = smoothUptrendBars(80);
        RadarReplayBacktestService service = replayService(0.0, 0.0, 0.0, LocalTime.of(13, 5));

        BacktestResult result = service.replay(SYMBOL, bars, scanRequest(radarConfig(10.0, 20.0)));

        Trade buy = firstTradeOfType(result, TradeType.BUY);
        int entryIndex = indexOfBarAt(bars, buy.getTimestamp());
        assertThat(entryIndex).isGreaterThan(0);
        assertThat(buy.getPrice()).isEqualTo(bars.get(entryIndex).getOpen());
        assertThat(buy.getPrice()).isNotEqualTo(bars.get(entryIndex - 1).getClose());
    }

    @Test
    void shouldStopLossBeforeTakeProfitWhenSameBarTouchesBoth() {
        List<Bar> bars = smoothUptrendBars(80);
        bars.set(31, new Bar(SESSION_START.plusMinutes(31), 110.0, 250.0, 1.0, 120.0, 2_000));
        RadarReplayBacktestService service = replayService(0.0, 0.0, 0.0, LocalTime.of(13, 5));

        BacktestResult result = service.replay(SYMBOL, bars, scanRequest(radarConfig(1.0, 1.0)));

        Trade buy = firstTradeOfType(result, TradeType.BUY);
        Trade sell = firstTradeOfType(result, TradeType.SELL);
        assertThat(sell.getPrice()).isEqualTo(buy.getStopLoss());
        assertThat(sell.getPrice()).isLessThan(buy.getTakeProfit());
        assertThat(sell.getNetProceeds() - buy.getTotalCost()).isLessThan(0.0);
    }

    @Test
    void shouldForceCloseOpenPositionAt1325() {
        List<Bar> bars = smoothUptrendBars(270);
        RadarReplayBacktestService service = replayService(0.0, 0.0, 0.0, LocalTime.of(13, 30));

        BacktestResult result = service.replay(SYMBOL, bars, scanRequest(radarConfig(10_000.0, 10_000.0)));

        Trade sell = firstTradeOfType(result, TradeType.SELL);
        assertThat(sell.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 5, 13, 25));
        assertThat(sell.getExitReason()).isEqualTo("FORCE_CLOSE");
    }

    @Test
    void shouldBlockEntryWhenNetRewardAfterCostIsNotPositive() {
        List<Bar> bars = smoothUptrendBars(80);
        RadarReplayBacktestService service = replayService(0.02, 0.02, 0.0, LocalTime.of(13, 5));

        BacktestResult result = service.replay(SYMBOL, bars, scanRequest(radarConfig(1.0, 0.1)));

        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getSignalObservations())
                .anySatisfy(observation -> {
                    assertThat(observation.action()).isEqualTo("OPEN_LONG");
                    assertThat(observation.blocked()).isTrue();
                    assertThat(observation.reason()).isNotBlank();
                });
    }

    @Test
    void shouldApplyGlobalCadenceMaxPositionsAndSameFiveMinuteBarLimit() {
        LocalDateTime signalTime = LocalDateTime.of(2026, 1, 5, 10, 2);
        AutoMonitorExecutionGate.GateConfig config = new AutoMonitorExecutionGate.GateConfig(
                true,
                LocalTime.of(9, 0),
                LocalTime.of(9, 15),
                LocalTime.of(13, 5),
                1,
                0,
                true,
                LocalTime.of(13, 25));

        AutoMonitorExecutionGate.GateDecision dailyMax = AutoMonitorExecutionGate.evaluateOpenLong(
                config,
                new AutoMonitorExecutionGate.GateState(
                        "2330.TW",
                        signalTime,
                        false,
                        false,
                        null,
                        1,
                        null,
                        java.util.Set.of(),
                        0,
                        3));
        AutoMonitorExecutionGate.GateDecision sameM5 = AutoMonitorExecutionGate.evaluateOpenLong(
                config,
                new AutoMonitorExecutionGate.GateState(
                        "2317.TW",
                        signalTime,
                        false,
                        false,
                        null,
                        0,
                        null,
                        java.util.Set.of(AutoMonitorExecutionGate.fiveMinuteBucket(signalTime)),
                        0,
                        3));
        AutoMonitorExecutionGate.GateDecision maxPositions = AutoMonitorExecutionGate.evaluateOpenLong(
                config,
                new AutoMonitorExecutionGate.GateState(
                        "2454.TW",
                        signalTime,
                        false,
                        false,
                        null,
                        0,
                        null,
                        java.util.Set.of(),
                        3,
                        3));

        assertThat(dailyMax.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.DAILY_MAX_TRADES);
        assertThat(sameM5.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.ONE_ENTRY_PER_FIVE_MINUTE_BAR);
        assertThat(maxPositions.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.MAX_OPEN_POSITIONS);
    }

    @Test
    void shouldUseSignalTimeNotBarStartForTimingGate() {
        LocalDateTime barStart = LocalDateTime.of(2026, 1, 5, 13, 0);
        LocalDateTime signalTime = barStart.plusMinutes(5);

        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(
                new AutoMonitorExecutionGate.GateConfig(
                        false,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 0),
                        LocalTime.of(13, 5),
                        5,
                        0,
                        true,
                        LocalTime.of(13, 25)),
                new AutoMonitorExecutionGate.GateState(
                        SYMBOL,
                        signalTime,
                        false,
                        false,
                        null,
                        0,
                        null,
                        java.util.Set.of(),
                        0,
                        3));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.LATEST_ENTRY_TIME);
        assertThat(barStart.toLocalTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void shouldHaltTradingAfterDailyLossBreaker() {
        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(
                new AutoMonitorExecutionGate.GateConfig(
                        false,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 0),
                        LocalTime.of(13, 5),
                        5,
                        0,
                        false,
                        LocalTime.of(13, 25)),
                new AutoMonitorExecutionGate.GateState(
                        SYMBOL,
                        LocalDateTime.of(2026, 1, 5, 10, 30),
                        true,
                        false,
                        null,
                        1,
                        null,
                        java.util.Set.of(),
                        0,
                        3));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.TRADING_HALTED);
        assertThat(decision.reason()).contains("risk breaker");
    }

    @Test
    void shouldKeepWarmupDiagnosticsInReplaySummary() {
        List<Bar> warmup = List.of(
                new Bar(LocalDateTime.of(2026, 1, 2, 13, 15), 98.0, 99.0, 97.5, 98.5, 1_000),
                new Bar(LocalDateTime.of(2026, 1, 2, 13, 20), 98.5, 99.5, 98.0, 99.0, 1_000));
        List<Bar> session = smoothUptrendBars(40);
        RadarStrategyConfig radar = radarConfig(10.0, 20.0);
        radar.setBacktestCrossDayWarmupEnabled(true);
        radar.setBacktestWarmupBarCount(30);
        radar.setSlowMovingAveragePeriod(8);

        BacktestResult result = replayService(0.0, 0.0, 0.0, LocalTime.of(13, 5))
                .replay(SYMBOL, warmup, session, scanRequest(radar));

        assertThat(result.getWarmupDiagnostics()).hasSize(1);
        BacktestResult.WarmupDiagnostic diagnostic = result.getWarmupDiagnostics().get(0);
        assertThat(diagnostic.enabled()).isTrue();
        assertThat(diagnostic.requestedBars()).isEqualTo(30);
        assertThat(diagnostic.loadedBars()).isEqualTo(2);
        assertThat(diagnostic.firstWarmupTime()).isEqualTo(warmup.get(0).getTimestamp());
        assertThat(diagnostic.lastWarmupTime()).isEqualTo(warmup.get(1).getTimestamp());
    }

    private RadarReplayBacktestService replayService(
            double commissionRate,
            double dayTradeSellTaxRate,
            double slippageRate,
            LocalTime latestEntryTime) {
        return new RadarReplayBacktestService(
                1_000_000.0,
                commissionRate,
                dayTradeSellTaxRate,
                slippageRate,
                LocalTime.of(9, 0),
                LocalTime.of(9, 0),
                latestEntryTime,
                60,
                -100_000.0,
                10,
                10,
                true,
                1,
                0,
                false);
    }

    private MarketScannerService.ScanRequest scanRequest(RadarStrategyConfig radarConfig) {
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        return MarketScannerService.ScanRequest.createDefault()
                .tradeMode(TradeMode.DAY_TRADE)
                .timeframe(Timeframe.M1)
                .barCount(160)
                .decisionConfig(decisionConfig)
                .radarStrategyConfig(radarConfig);
    }

    private RadarStrategyConfig radarConfig(double atrStopMultiplier, double atrTakeProfitMultiplier) {
        RadarStrategyConfig radarConfig = RadarStrategyConfig.createDefault();
        radarConfig.setRsiEnabled(false);
        radarConfig.setMovingAverageEnabled(true);
        radarConfig.setFastMovingAveragePeriod(3);
        radarConfig.setSlowMovingAveragePeriod(8);
        radarConfig.setMovingAverageWeight(1.0);
        radarConfig.setVolumeBreakoutEnabled(false);
        radarConfig.setMinimumEntryScore(0.0);
        radarConfig.setRequirePriceAboveVwapForLong(false);
        radarConfig.setRequireBreakoutContinuation(false);
        radarConfig.setMarketRegimeFilterEnabled(false);
        radarConfig.setVolumeSustainEnabled(false);
        radarConfig.setAtrRiskEnabled(true);
        radarConfig.setAtrChaseLimitEnabled(false);
        radarConfig.setAtrPeriod(14);
        radarConfig.setAtrStopMultiplier(atrStopMultiplier);
        radarConfig.setAtrTakeProfitMultiplier(atrTakeProfitMultiplier);
        return radarConfig;
    }

    private List<Bar> smoothUptrendBars(int count) {
        List<Bar> bars = new ArrayList<>();
        double previousClose = 100.0;
        for (int i = 0; i < count; i++) {
            double open = previousClose + 0.05;
            double close = open + 0.20;
            bars.add(new Bar(
                    SESSION_START.plusMinutes(i),
                    open,
                    close + 0.10,
                    open - 0.10,
                    close,
                    1_000));
            previousClose = close;
        }
        return bars;
    }

    private Trade firstTradeOfType(BacktestResult result, TradeType type) {
        return result.getTrades().stream()
                .filter(trade -> trade.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private int indexOfBarAt(List<Bar> bars, LocalDateTime timestamp) {
        for (int i = 0; i < bars.size(); i++) {
            if (bars.get(i).getTimestamp().equals(timestamp)) {
                return i;
            }
        }
        return -1;
    }
}
