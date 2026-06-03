package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SignalMonitorConfigTest {

    @Test
    void shouldPreserveAutoMonitorCadenceSettings() {
        SignalMonitorConfig config = SignalMonitorConfig.createDayTradeStandardTemplate();
        config.setScanIntervalSeconds(12);
        config.setMinSignalIntervalMinutes(7);
        config.setEarlyEntryBlockEnabled(true);
        config.setEarlyEntryBlockStart(LocalTime.of(9, 3));
        config.setEarlyEntryBlockEnd(LocalTime.of(9, 18));
        config.setLatestAutoEntryTime(LocalTime.of(13, 4));
        config.setDailyMaxAutoTrades(4);
        config.setEntryPacingMinutes(6);
        config.setPostExitCooldownMinutes(35);
        config.setOneEntryPerFiveMinuteBar(true);

        SignalMonitorConfig copied = SignalMonitorTemplateManager.copyMonitorConfig(config);

        assertThat(copied.getScanIntervalSeconds()).isEqualTo(12);
        assertThat(copied.getMinSignalIntervalMinutes()).isEqualTo(7);
        assertThat(copied.isEarlyEntryBlockEnabled()).isTrue();
        assertThat(copied.getEarlyEntryBlockStart()).isEqualTo(LocalTime.of(9, 3));
        assertThat(copied.getEarlyEntryBlockEnd()).isEqualTo(LocalTime.of(9, 18));
        assertThat(copied.getLatestAutoEntryTime()).isEqualTo(LocalTime.of(13, 4));
        assertThat(copied.getDailyMaxAutoTrades()).isEqualTo(4);
        assertThat(copied.getEntryPacingMinutes()).isEqualTo(6);
        assertThat(copied.getPostExitCooldownMinutes()).isEqualTo(35);
        assertThat(copied.isOneEntryPerFiveMinuteBar()).isTrue();
    }

    @Test
    void shouldPreserveTradeModeAndQuantityRules() {
        SignalMonitorConfig config = SignalMonitorConfig.createDayTradeStandardTemplate();
        config.setTimeframe(Timeframe.M5);
        config.setBarCount(240);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setBatchScanMode(true);
        config.setDayTradeOrderQuantity(1000);

        SignalMonitorConfig copied = SignalMonitorTemplateManager.copyMonitorConfig(config);

        assertThat(copied.getTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(copied.getBarCount()).isEqualTo(240);
        assertThat(copied.getTradeMode()).isEqualTo(TradeMode.DAY_TRADE);
        assertThat(copied.isBatchScanMode()).isTrue();
        assertThat(copied.getDayTradeOrderQuantity()).isEqualTo(1000);
    }

    @Test
    void shouldPreserveDecisionSourceAndAutoManagedDefaults() {
        SignalMonitorConfig config = SignalMonitorConfig.createDayTradeStandardTemplate();

        SignalMonitorConfig copied = SignalMonitorTemplateManager.copyMonitorConfig(config);

        assertThat(copied.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.AUTO_MONITOR);
        assertThat(copied.isAutoManagedEntries()).isTrue();
        assertThat(copied.getDayTradeOrderQuantity()).isEqualTo(1000);
    }
}
