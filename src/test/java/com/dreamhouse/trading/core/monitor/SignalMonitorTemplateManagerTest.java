package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalMonitorTemplateManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepBuiltInAbcTemplatesProtected() {
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));

        assertThat(manager.deleteUserTemplate(SignalMonitorTemplateManager.GROUP_A_NAME)).isFalse();
        assertThatThrownBy(() -> manager.saveUserTemplate(template(SignalMonitorTemplateManager.GROUP_B_NAME)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Built-in");
        assertThat(manager.builtInTemplates())
                .extracting(SignalMonitorTemplate::name)
                .containsExactly(
                        SignalMonitorTemplateManager.GROUP_A_NAME,
                        SignalMonitorTemplateManager.GROUP_B_NAME,
                        SignalMonitorTemplateManager.GROUP_C_NAME);
    }

    @Test
    void shouldApplyBuiltInTemplateWithoutDroppingScannerFlags() {
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));
        SignalMonitorTemplate groupA = manager.builtInTemplates().stream()
                .filter(template -> template.name().equals(SignalMonitorTemplateManager.GROUP_A_NAME))
                .findFirst()
                .orElseThrow();

        SignalMonitorConfig applied = SignalMonitorTemplateManager.copyMonitorConfig(groupA.monitorConfig());
        RadarStrategyConfig radar = applied.getRadarStrategyConfig();

        assertThat(radar.getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.EMA);
        assertThat(radar.getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(radar.getSlowMovingAveragePeriod()).isEqualTo(34);
        assertThat(radar.isBacktestCrossDayWarmupEnabled()).isTrue();
        assertThat(radar.isBlockMovingAverageOnlyEntry()).isTrue();
        assertThat(radar.isRequireBreakoutNextBarConfirmation()).isTrue();
        assertThat(radar.isMarketRegimeFilterEnabled()).isFalse();
        assertThat(radar.isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(radar.isVolumeSustainEnabled()).isTrue();
        assertThat(radar.isAtrChaseLimitEnabled()).isTrue();
    }

    @Test
    void shouldSaveAndLoadCustomTemplateRoundTrip() {
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));
        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        monitor.setScanIntervalSeconds(13);
        monitor.setTimeframe(Timeframe.M5);
        monitor.setDayTradeOrderQuantity(1000);
        monitor.getRadarStrategyConfig().setDayTradeTimeframe(Timeframe.M5);
        monitor.getRadarStrategyConfig().setExecutionConfirmationTimeframe(Timeframe.M1);
        monitor.getRadarStrategyConfig().setBacktestCrossDayWarmupEnabled(true);
        monitor.getRadarStrategyConfig().setBacktestWarmupBarCount(130);
        monitor.getRadarStrategyConfig().setRequirePriceAboveVwapForLong(true);
        monitor.getRadarStrategyConfig().setVolumeSustainEnabled(true);
        monitor.getRadarStrategyConfig().setAtrChaseLimitEnabled(true);

        manager.saveUserTemplate(new SignalMonitorTemplate(
                "custom-round-trip",
                "custom",
                monitor,
                SignalMonitorTemplateManager.createDayTradeStandardMonitorConfig(),
                true));

        List<SignalMonitorTemplate> loaded = manager.loadUserTemplates();

        assertThat(loaded).hasSize(1);
        SignalMonitorConfig loadedMonitor = loaded.get(0).monitorConfig();
        assertThat(loadedMonitor.getScanIntervalSeconds()).isEqualTo(13);
        assertThat(loadedMonitor.getTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(loadedMonitor.getDayTradeOrderQuantity()).isEqualTo(1000);
        assertThat(loadedMonitor.getRadarStrategyConfig().getDayTradeTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(loadedMonitor.getRadarStrategyConfig().getExecutionConfirmationTimeframe()).isEqualTo(Timeframe.M1);
        assertThat(loadedMonitor.getRadarStrategyConfig().isBacktestCrossDayWarmupEnabled()).isTrue();
        assertThat(loadedMonitor.getRadarStrategyConfig().getBacktestWarmupBarCount()).isEqualTo(130);
        assertThat(loadedMonitor.getRadarStrategyConfig().isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(loadedMonitor.getRadarStrategyConfig().isVolumeSustainEnabled()).isTrue();
        assertThat(loadedMonitor.getRadarStrategyConfig().isAtrChaseLimitEnabled()).isTrue();
    }

    @Test
    void shouldNotOverwriteBuiltInTemplatesWhenSavingCustomTemplate() {
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));
        manager.saveUserTemplate(template("custom-A"));

        List<SignalMonitorTemplate> dialogTemplates = manager.loadTemplatesForDialog(
                SignalMonitorConfig.createDayTradeStandardTemplate(),
                SignalMonitorTemplateManager.createDayTradeStandardMonitorConfig());

        assertThat(dialogTemplates)
                .extracting(SignalMonitorTemplate::name)
                .contains(
                        SignalMonitorTemplateManager.CURRENT_TEMPLATE_NAME,
                        SignalMonitorTemplateManager.GROUP_A_NAME,
                        SignalMonitorTemplateManager.GROUP_B_NAME,
                        SignalMonitorTemplateManager.GROUP_C_NAME,
                        "custom-A");
        assertThat(manager.builtInTemplates())
                .extracting(template -> template.monitorConfig().getRadarStrategyConfig().getFastMovingAveragePeriod())
                .containsExactly(8, 8, 8);
    }

    private static SignalMonitorTemplate template(String name) {
        return new SignalMonitorTemplate(
                name,
                "custom",
                SignalMonitorConfig.createDayTradeStandardTemplate(),
                DecisionConfig.createDefault(),
                true);
    }
}
