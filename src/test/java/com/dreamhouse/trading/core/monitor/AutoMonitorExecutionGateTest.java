package com.dreamhouse.trading.core.monitor;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AutoMonitorExecutionGateTest {

    @Test
    void shouldBlockOpenLongBeforeWarmupWindowEnds() {
        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(
                defaultConfig(),
                stateAt(LocalDateTime.of(2026, 1, 5, 9, 5)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.EARLY_ENTRY_BLOCK);
        assertThat(decision.reason()).contains("early session warmup");
    }

    @Test
    void shouldBlockNewPositionAfter1305() {
        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(
                defaultConfig(),
                stateAt(LocalDateTime.of(2026, 1, 5, 13, 6)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.LATEST_ENTRY_TIME);
        assertThat(decision.reason()).contains("latest entry time");
    }

    @Test
    void shouldForceCloseAutoManagedPositionsAt1325() {
        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(
                defaultConfig(),
                stateAt(LocalDateTime.of(2026, 1, 5, 13, 25)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.FORCE_CLOSE_WINDOW);
        assertThat(decision.reason()).contains("force close window");
    }

    @Test
    void shouldRespectSameFiveMinuteBarOneTradeLimit() {
        LocalDateTime signalTime = LocalDateTime.of(2026, 1, 5, 10, 2);
        AutoMonitorExecutionGate.GateState state = new AutoMonitorExecutionGate.GateState(
                "2330.TW",
                signalTime,
                false,
                false,
                null,
                0,
                null,
                Set.of(AutoMonitorExecutionGate.fiveMinuteBucket(signalTime)),
                0,
                3);

        AutoMonitorExecutionGate.GateDecision decision = AutoMonitorExecutionGate.evaluateOpenLong(defaultConfig(), state);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.ONE_ENTRY_PER_FIVE_MINUTE_BAR);
        assertThat(decision.reason()).contains("one entry per M5");
    }

    @Test
    void shouldRespectDailyMaxTradesAndMaxOpenPositions() {
        AutoMonitorExecutionGate.GateDecision dailyMax = AutoMonitorExecutionGate.evaluateOpenLong(
                defaultConfig(),
                new AutoMonitorExecutionGate.GateState(
                        "2330.TW",
                        LocalDateTime.of(2026, 1, 5, 10, 0),
                        false,
                        false,
                        null,
                        5,
                        null,
                        Set.of(),
                        0,
                        3));
        AutoMonitorExecutionGate.GateDecision maxPositions = AutoMonitorExecutionGate.evaluateOpenLong(
                defaultConfig(),
                new AutoMonitorExecutionGate.GateState(
                        "2317.TW",
                        LocalDateTime.of(2026, 1, 5, 10, 10),
                        false,
                        false,
                        null,
                        1,
                        null,
                        Set.of(),
                        3,
                        3));

        assertThat(dailyMax.allowed()).isFalse();
        assertThat(dailyMax.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.DAILY_MAX_TRADES);
        assertThat(dailyMax.reason()).contains("daily max");
        assertThat(maxPositions.allowed()).isFalse();
        assertThat(maxPositions.blockType()).isEqualTo(AutoMonitorExecutionGate.BlockType.MAX_OPEN_POSITIONS);
        assertThat(maxPositions.reason()).contains("max open positions");
    }

    private AutoMonitorExecutionGate.GateConfig defaultConfig() {
        return new AutoMonitorExecutionGate.GateConfig(
                true,
                LocalTime.of(9, 0),
                LocalTime.of(9, 15),
                LocalTime.of(13, 5),
                5,
                5,
                true,
                LocalTime.of(13, 25));
    }

    private AutoMonitorExecutionGate.GateState stateAt(LocalDateTime signalTime) {
        return new AutoMonitorExecutionGate.GateState(
                "2330.TW",
                signalTime,
                false,
                false,
                null,
                0,
                null,
                Set.of(),
                0,
                3);
    }
}
