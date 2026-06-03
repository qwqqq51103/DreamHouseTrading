package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.monitor.SignalMonitorConfig;
import com.dreamhouse.trading.core.monitor.SignalMonitorTemplate;
import com.dreamhouse.trading.core.monitor.SignalMonitorTemplateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RadarStrategyConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPreserveHardBlockFlagsWhenCopiedOrSerialized() {
        RadarStrategyConfig original = scannerFlagConfig();
        RadarStrategyConfig copied = original.copy();

        assertScannerFlags(copied);

        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        monitor.setRadarStrategyConfig(original);
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));
        manager.saveUserTemplate(new SignalMonitorTemplate(
                "custom-hard-blocks",
                "custom scanner flags",
                monitor,
                SignalMonitorTemplateManager.createDayTradeStandardMonitorConfig(),
                true));

        RadarStrategyConfig loaded = manager.loadUserTemplates().get(0).monitorConfig().getRadarStrategyConfig();

        assertScannerFlags(loaded);
    }

    @Test
    void shouldKeepWarmupAndTimeframeSettings() {
        RadarStrategyConfig original = scannerFlagConfig();
        original.setBacktestCrossDayWarmupEnabled(true);
        original.setBacktestWarmupBarCount(144);
        original.setDayTradeTimeframe(Timeframe.M5);
        original.setExecutionConfirmationTimeframe(Timeframe.M1);
        original.setShortSwingTimeframe(Timeframe.M15);
        original.setSwingTradeTimeframe(Timeframe.H1);
        original.setDayTradeBarCount(240);
        original.setShortSwingBarCount(260);
        original.setSwingTradeBarCount(320);

        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        monitor.setRadarStrategyConfig(original);
        SignalMonitorTemplateManager manager = new SignalMonitorTemplateManager(tempDir.resolve("templates.properties"));
        manager.saveUserTemplate(new SignalMonitorTemplate(
                "custom-warmup",
                "custom warmup",
                monitor,
                DecisionConfig.createDefault(),
                true));

        RadarStrategyConfig loaded = manager.loadUserTemplates().get(0).monitorConfig().getRadarStrategyConfig();

        assertThat(loaded.isBacktestCrossDayWarmupEnabled()).isTrue();
        assertThat(loaded.getBacktestWarmupBarCount()).isEqualTo(144);
        assertThat(loaded.getDayTradeTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(loaded.getExecutionConfirmationTimeframe()).isEqualTo(Timeframe.M1);
        assertThat(loaded.getShortSwingTimeframe()).isEqualTo(Timeframe.M15);
        assertThat(loaded.getSwingTradeTimeframe()).isEqualTo(Timeframe.H1);
        assertThat(loaded.getDayTradeBarCount()).isEqualTo(240);
        assertThat(loaded.getShortSwingBarCount()).isEqualTo(260);
        assertThat(loaded.getSwingTradeBarCount()).isEqualTo(320);
    }

    @Test
    void shouldIncludeScannerFlagsInSettingSummary() {
        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        monitor.setRadarStrategyConfig(scannerFlagConfig());

        String summary = SignalMonitorTemplateManager.buildStrategySettingSummary(
                "custom-summary",
                monitor,
                SignalMonitorTemplateManager.createDayTradeStandardMonitorConfig());

        assertThat(summary)
                .contains("requireAboveVwap=true")
                .contains("volumeSustain=true")
                .contains("atrChaseLimitEnabled=true")
                .contains("blockRsiOverbought=true")
                .contains("marketRegimeFilter=true")
                .contains("crossDayWarmup=true")
                .contains("maType=SMA")
                .contains("maFast=8")
                .contains("maSlow=21")
                .contains("nextBarConfirm=true")
                .contains("minimumEntryScore=0.620");
    }

    private static RadarStrategyConfig scannerFlagConfig() {
        RadarStrategyConfig config = RadarStrategyConfig.createDefault();
        config.setRequirePriceAboveVwapForLong(true);
        config.setRangeMarketRequiresVwapAndVolume(true);
        config.setVolumeSustainEnabled(true);
        config.setAtrRiskEnabled(true);
        config.setAtrChaseLimitEnabled(true);
        config.setAtrChaseLimitMultiplier(1.4);
        config.setRsiEnabled(true);
        config.setBlockBreakoutOnRsiOverbought(true);
        config.setRequireRsiEntryConfirmation(true);
        config.setMarketRegimeFilterEnabled(true);
        config.setWeakMarketStrictLongEnabled(true);
        config.setMovingAverageType(RadarStrategyConfig.MovingAverageType.SMA);
        config.setFastMovingAveragePeriod(8);
        config.setSlowMovingAveragePeriod(21);
        config.setRequireBreakoutNextBarConfirmation(true);
        config.setMinimumEntryScore(0.62);
        config.setBacktestCrossDayWarmupEnabled(true);
        return config;
    }

    private static void assertScannerFlags(RadarStrategyConfig config) {
        assertThat(config.isRequirePriceAboveVwapForLong()).isTrue();
        assertThat(config.isRangeMarketRequiresVwapAndVolume()).isTrue();
        assertThat(config.isVolumeSustainEnabled()).isTrue();
        assertThat(config.isAtrRiskEnabled()).isTrue();
        assertThat(config.isAtrChaseLimitEnabled()).isTrue();
        assertThat(config.isBlockBreakoutOnRsiOverbought()).isTrue();
        assertThat(config.isRequireRsiEntryConfirmation()).isTrue();
        assertThat(config.isMarketRegimeFilterEnabled()).isTrue();
        assertThat(config.isWeakMarketStrictLongEnabled()).isTrue();
        assertThat(config.getMovingAverageType()).isEqualTo(RadarStrategyConfig.MovingAverageType.SMA);
        assertThat(config.getFastMovingAveragePeriod()).isEqualTo(8);
        assertThat(config.getSlowMovingAveragePeriod()).isEqualTo(21);
        assertThat(config.isRequireBreakoutNextBarConfirmation()).isTrue();
        assertThat(config.getMinimumEntryScore()).isEqualTo(0.62);
    }
}
