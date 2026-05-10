package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.signal.StrategySignal;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionEngineRiskFiltersTest {

    @Test
    void shortEntryIsBlockedWhenShortSellingDisabled() {
        DecisionEngine engine = createEngine(config -> {
            config.setShortSellingEnabled(false);
            config.setRegimeDetectionEnabled(false);
            config.setTrendAnalysisEnabled(false);
        });
        engine.setStrategySignals(List.of(signal(SignalType.SHORT, 0.9, 105.0, 90.0)));

        DecisionResult result = engine.onBar(flatSeries().getLastBar());

        assertEquals(DecisionResult.Action.NO_ACTION, result.getAction());
        assertTrue(result.getReason().contains("Short signal"));
    }

    @Test
    void riskRewardFilterBlocksWeakEntry() {
        DecisionEngine engine = createEngine(config -> {
            config.setRegimeDetectionEnabled(false);
            config.setTrendAnalysisEnabled(false);
            config.setMinRiskRewardRatio(2.0);
        });
        engine.setStrategySignals(List.of(signal(SignalType.LONG, 0.9, 99.0, 101.0)));

        DecisionResult result = engine.onBar(flatSeries().getLastBar());

        assertEquals(DecisionResult.Action.NO_ACTION, result.getAction());
        assertTrue(result.getReason().contains("Risk/reward"));
    }

    @Test
    void atrFilterBlocksLowVolatilityEntry() {
        DecisionEngine engine = createEngine(config -> {
            config.setRegimeDetectionEnabled(false);
            config.setTrendAnalysisEnabled(false);
            config.setMinEntryAtrPercent(0.05);
        });
        engine.setStrategySignals(List.of(signal(SignalType.LONG, 0.9, 95.0, 112.0)));

        DecisionResult result = engine.onBar(flatSeries().getLastBar());

        assertEquals(DecisionResult.Action.NO_ACTION, result.getAction());
        assertTrue(result.getReason().contains("ATR"));
    }

    private static DecisionEngine createEngine(java.util.function.Consumer<DecisionConfig> customizer) {
        DecisionConfig config = DecisionConfig.createAggressive();
        config.setVerboseLogging(false);
        customizer.accept(config);
        DecisionEngine engine = new DecisionEngine(config, new Portfolio(100000.0));
        engine.setSymbol("TEST");
        engine.setBarSeries(flatSeries(), Timeframe.M5);
        return engine;
    }

    private static StrategySignal signal(SignalType signalType, double confidence,
                                         double stopLoss, double takeProfit) {
        return new StrategySignal.Builder("TEST_SIGNAL", Timeframe.M5, signalType)
            .confidence(confidence)
            .weight(1.0)
            .reason("test")
            .suggestedStopLoss(stopLoss)
            .suggestedTakeProfit(takeProfit)
            .build();
    }

    private static BarSeries flatSeries() {
        BarSeries series = new BaseBarSeries("TEST");
        ZonedDateTime start = ZonedDateTime.now().minusHours(4);
        for (int i = 0; i < 40; i++) {
            series.addBar(new BaseBar(
                Duration.ofMinutes(5),
                start.plusMinutes(i * 5L),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.2),
                BigDecimal.valueOf(99.8),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(10000.0)));
        }
        return series;
    }
}
