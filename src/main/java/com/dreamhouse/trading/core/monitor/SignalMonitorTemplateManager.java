package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import com.dreamhouse.trading.core.scanner.WeakMarketLongPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Stores and copies signal-monitor templates without constructing Swing UI.
 */
public class SignalMonitorTemplateManager {

    public static final String CURRENT_TEMPLATE_NAME = "Current settings";
    public static final String GROUP_A_NAME = "A Group Stable EMA8/34";
    public static final String GROUP_B_NAME = "B Group Relaxed EMA8/21";
    public static final String GROUP_C_NAME = "C Group SMA8/21 Volume";

    private final Path templateStore;

    public SignalMonitorTemplateManager(Path templateStore) {
        this.templateStore = templateStore;
    }

    public List<SignalMonitorTemplate> loadTemplatesForDialog(
            SignalMonitorConfig currentMonitor,
            DecisionConfig currentDecision) {
        List<SignalMonitorTemplate> templates = new ArrayList<>();
        templates.add(new SignalMonitorTemplate(
                CURRENT_TEMPLATE_NAME,
                "Use the current monitor settings without changing template values.",
                copyMonitorConfig(currentMonitor != null ? currentMonitor : SignalMonitorConfig.createDefault()),
                copyDecisionConfig(currentDecision != null ? currentDecision : createDayTradeStandardMonitorConfig()),
                false));
        templates.addAll(builtInTemplates());
        templates.addAll(loadUserTemplates());
        return templates;
    }

    public List<SignalMonitorTemplate> builtInTemplates() {
        return List.of(
                new SignalMonitorTemplate(
                        GROUP_A_NAME,
                        "Stable EMA8/34 with cross-day warmup, MA-only block, and next-bar breakout confirmation.",
                        SignalMonitorConfig.createDayTradeGroupATemplate(),
                        createBConvergenceMonitorConfig(),
                        false),
                new SignalMonitorTemplate(
                        GROUP_B_NAME,
                        "Relaxed EMA8/21 day-trade template with B convergence scanner gates.",
                        SignalMonitorConfig.createDayTradeGroupBTemplate(),
                        createBConvergenceMonitorConfig(),
                        false),
                new SignalMonitorTemplate(
                        GROUP_C_NAME,
                        "SMA8/21 comparison template with 1.45x volume multiplier.",
                        SignalMonitorConfig.createDayTradeGroupCTemplate(),
                        createBConvergenceMonitorConfig(),
                        false));
    }

    public boolean isBuiltInTemplateName(String templateName) {
        if (templateName == null) {
            return false;
        }
        return builtInTemplates().stream().anyMatch(template -> template.name().equals(templateName));
    }

    public List<SignalMonitorTemplate> loadUserTemplates() {
        if (templateStore == null || !Files.exists(templateStore)) {
            return List.of();
        }
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(templateStore)) {
            props.load(input);
        } catch (IOException e) {
            System.err.println("[MonitorTemplates] load failed: " + e.getMessage());
            return List.of();
        }
        int count = parseInt(props.getProperty("count"), 0);
        List<SignalMonitorTemplate> templates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "template." + i + ".";
            String name = props.getProperty(prefix + "name", "").trim();
            if (name.isEmpty() || isBuiltInTemplateName(name)) {
                continue;
            }
            templates.add(new SignalMonitorTemplate(
                    name,
                    props.getProperty(prefix + "description", "User-defined monitor template: " + name),
                    readMonitorConfig(props, prefix),
                    readDecisionConfig(props, prefix),
                    true));
        }
        return templates;
    }

    public void saveUserTemplate(SignalMonitorTemplate newTemplate) {
        if (newTemplate == null || newTemplate.name() == null || newTemplate.name().isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (isBuiltInTemplateName(newTemplate.name())) {
            throw new IllegalArgumentException("Built-in templates cannot be overwritten");
        }

        LinkedHashMap<String, SignalMonitorTemplate> templates = new LinkedHashMap<>();
        for (SignalMonitorTemplate template : loadUserTemplates()) {
            templates.put(template.name(), template);
        }
        templates.put(newTemplate.name(), new SignalMonitorTemplate(
                newTemplate.name(),
                newTemplate.description(),
                copyMonitorConfig(newTemplate.monitorConfig()),
                copyDecisionConfig(newTemplate.decisionConfig()),
                true));
        writeUserTemplates(templates.values().stream().toList());
    }

    public boolean deleteUserTemplate(String templateName) {
        if (isBuiltInTemplateName(templateName)) {
            return false;
        }
        LinkedHashMap<String, SignalMonitorTemplate> templates = new LinkedHashMap<>();
        for (SignalMonitorTemplate template : loadUserTemplates()) {
            templates.put(template.name(), template);
        }
        if (templates.remove(templateName) == null) {
            return false;
        }
        writeUserTemplates(templates.values().stream().toList());
        return true;
    }

    private void writeUserTemplates(List<SignalMonitorTemplate> templates) {
        Properties props = new Properties();
        props.setProperty("count", String.valueOf(templates.size()));
        int index = 0;
        for (SignalMonitorTemplate template : templates) {
            String prefix = "template." + index++ + ".";
            props.setProperty(prefix + "name", template.name());
            props.setProperty(prefix + "description", template.description() != null ? template.description() : "");
            writeMonitorConfig(props, prefix, template.monitorConfig());
            writeDecisionConfig(props, prefix, template.decisionConfig());
        }
        try {
            Files.createDirectories(templateStore.getParent());
            try (OutputStream output = Files.newOutputStream(templateStore)) {
                props.store(output, "DreamHouseTrading user monitor templates");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write monitor templates: " + e.getMessage(), e);
        }
    }

    public static SignalMonitorConfig copyMonitorConfig(SignalMonitorConfig source) {
        SignalMonitorConfig copy = new SignalMonitorConfig();
        if (source == null) {
            return copy;
        }
        copy.setScanIntervalSeconds(source.getScanIntervalSeconds());
        copy.setTimeframe(source.getTimeframe());
        copy.setMinSignalIntervalMinutes(source.getMinSignalIntervalMinutes());
        copy.setBarCount(source.getBarCount());
        copy.setTradeMode(source.getTradeMode());
        copy.setBatchScanMode(source.isBatchScanMode());
        copy.setDecisionSource(source.getDecisionSource());
        copy.setAutoManagedEntries(source.isAutoManagedEntries());
        copy.setDayTradeOrderQuantity(source.getDayTradeOrderQuantity());
        copy.setRadarStrategyConfig(source.getRadarStrategyConfig() != null
                ? source.getRadarStrategyConfig().copy()
                : RadarStrategyConfig.createDefault());
        copy.setEarlyEntryBlockEnabled(source.isEarlyEntryBlockEnabled());
        copy.setEarlyEntryBlockStart(source.getEarlyEntryBlockStart());
        copy.setEarlyEntryBlockEnd(source.getEarlyEntryBlockEnd());
        copy.setStopLossCooldownEnabled(source.isStopLossCooldownEnabled());
        copy.setStopLossCooldownMinutes(source.getStopLossCooldownMinutes());
        copy.setLatestAutoEntryTime(source.getLatestAutoEntryTime());
        copy.setDailyMaxLoss(source.getDailyMaxLoss());
        copy.setDailyMaxStopLossCount(source.getDailyMaxStopLossCount());
        copy.setConsecutiveLossLimit(source.getConsecutiveLossLimit());
        copy.setDisableTradingAfterLossLimit(source.isDisableTradingAfterLossLimit());
        copy.setDailyMaxAutoTrades(source.getDailyMaxAutoTrades());
        copy.setEntryPacingMinutes(source.getEntryPacingMinutes());
        copy.setPostExitCooldownMinutes(source.getPostExitCooldownMinutes());
        copy.setOneEntryPerFiveMinuteBar(source.isOneEntryPerFiveMinuteBar());
        copy.setRangeFailureExitEnabled(source.isRangeFailureExitEnabled());
        copy.setRangeFailureExitMinutes(source.getRangeFailureExitMinutes());
        copy.setRangeFailureMinR(source.getRangeFailureMinR());
        copy.setRangeFailureVolumeSustainExitEnabled(source.isRangeFailureVolumeSustainExitEnabled());
        copy.setRsiPeriod(source.getRsiPeriod());
        copy.setRsiOversold(source.getRsiOversold());
        copy.setRsiOverbought(source.getRsiOverbought());
        return copy;
    }

    public static DecisionConfig copyDecisionConfig(DecisionConfig source) {
        DecisionConfig effectiveSource = source != null ? source : DecisionConfig.createDefault();
        DecisionConfig copy = DecisionConfig.createDefault();
        copy.setMainLoopTimeframe(effectiveSource.getMainLoopTimeframe());
        copy.setRiskMonitorTimeframe(effectiveSource.getRiskMonitorTimeframe());
        copy.setRegimeDetectionEnabled(effectiveSource.isRegimeDetectionEnabled());
        copy.setTrendAnalysisEnabled(effectiveSource.isTrendAnalysisEnabled());
        copy.setPatternDetectionEnabled(effectiveSource.isPatternDetectionEnabled());
        copy.setVotingEnabled(effectiveSource.isVotingEnabled());
        copy.setRiskManagementEnabled(effectiveSource.isRiskManagementEnabled());
        copy.setLogLevel(effectiveSource.getLogLevel());
        copy.setVerboseLogging(effectiveSource.isVerboseLogging());
        copy.getVotingConfig().setLongEntryThreshold(effectiveSource.getVotingConfig().getLongEntryThreshold());
        copy.getVotingConfig().setShortEntryThreshold(effectiveSource.getVotingConfig().getShortEntryThreshold());
        copy.getVotingConfig().setExitThreshold(effectiveSource.getVotingConfig().getExitThreshold());
        copy.getVotingConfig().setReverseThreshold(effectiveSource.getVotingConfig().getReverseThreshold());
        copy.getVotingConfig().setReverseEnabled(effectiveSource.getVotingConfig().isReverseEnabled());
        copy.getVotingConfig().setMinVotingStrategies(effectiveSource.getVotingConfig().getMinVotingStrategies());
        copy.getVotingConfig().setRequireConsensus(effectiveSource.getVotingConfig().isRequireConsensus());
        copy.getVotingConfig().setSignalValidityMs(effectiveSource.getVotingConfig().getSignalValidityMs());
        copy.getRiskConfig().setRiskPercentPerTrade(effectiveSource.getRiskConfig().getRiskPercentPerTrade());
        copy.getRiskConfig().setMaxDailyLossPercent(effectiveSource.getRiskConfig().getMaxDailyLossPercent());
        copy.getRiskConfig().setMaxSymbolLossPercent(effectiveSource.getRiskConfig().getMaxSymbolLossPercent());
        copy.getRiskConfig().setMaxConcurrentPositions(effectiveSource.getRiskConfig().getMaxConcurrentPositions());
        copy.getRiskConfig().setMaxPositionSizePercent(effectiveSource.getRiskConfig().getMaxPositionSizePercent());
        copy.getRiskConfig().setMinCashReservePercent(effectiveSource.getRiskConfig().getMinCashReservePercent());
        copy.getRiskConfig().setDailyLossLimitEnabled(effectiveSource.getRiskConfig().isDailyLossLimitEnabled());
        copy.getRiskConfig().setSymbolLossLimitEnabled(effectiveSource.getRiskConfig().isSymbolLossLimitEnabled());
        copy.getRiskConfig().setAllowExitOnlyAfterDailyLimit(effectiveSource.getRiskConfig().isAllowExitOnlyAfterDailyLimit());
        copy.getRiskConfig().setMaxHoldingBars(effectiveSource.getRiskConfig().getMaxHoldingBars());
        copy.getRiskConfig().setForceCloseAtEndOfDay(effectiveSource.getRiskConfig().isForceCloseAtEndOfDay());
        copy.getRiskConfig().setCloseBeforeEndOfDayBars(effectiveSource.getRiskConfig().getCloseBeforeEndOfDayBars());
        copy.getRiskConfig().setMinRiskRewardRatio(effectiveSource.getRiskConfig().getMinRiskRewardRatio());
        copy.getRiskConfig().setMinVolatilityPercent(effectiveSource.getRiskConfig().getMinVolatilityPercent());
        copy.getRiskConfig().setMaxVolatilityPercent(effectiveSource.getRiskConfig().getMaxVolatilityPercent());
        copy.getRiskConfig().setAllowShortSelling(effectiveSource.getRiskConfig().isAllowShortSelling());
        return copy;
    }

    public static DecisionConfig createBConvergenceMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.35);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.5);
        config.getRiskConfig().setMaxConcurrentPositions(3);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    public static DecisionConfig createDayTradeStandardMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.40);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.5);
        config.getRiskConfig().setMaxConcurrentPositions(3);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    public static String buildStrategySettingSummary(
            String strategyName,
            SignalMonitorConfig monitorConfig,
            DecisionConfig decisionConfig) {
        SignalMonitorConfig monitor = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDayTradeStandardTemplate();
        RadarStrategyConfig radar = monitor.getRadarStrategyConfig() != null
                ? monitor.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        DecisionConfig decision = decisionConfig != null ? decisionConfig : createDayTradeStandardMonitorConfig();

        return String.format(Locale.US,
                "strategy=%s;scanIntervalSec=%d;mainTimeframe=%s;dayTradeTimeframe=%s;executionConfirmTimeframe=%s;"
                        + "barCount=%d;dayTradeBars=%d;tradeMode=%s;decisionSource=%s;autoManaged=%s;quantity=%d;"
                        + "minSignalIntervalMin=%d;earlyBlock=%s %s-%s;latestEntry=%s;stopLossCooldown=%s %dmin;postExitCooldownMin=%d;"
                        + "maxPositions=%d;dailyMaxLoss=%.2f;dailyMaxStopLossCount=%d;consecutiveLossLimit=%d;disableAfterLossLimit=%s;"
                        + "dailyMaxAutoTrades=%d;entryPacingMin=%d;oneEntryPerM5=%s;"
                        + "longThreshold=%.3f;exitThreshold=%.3f;minStrategies=%d;minRR=%.3f;minVolatility=%.3f;maxVolatility=%.3f;"
                        + "rsiEnabled=%s;rsiPeriod=%d;rsiOversold=%.2f;rsiOverbought=%.2f;rsiWeight=%.2f;requireRsiConfirm=%s;blockRsiOverbought=%s;"
                        + "maEnabled=%s;maType=%s;maFast=%d;maSlow=%d;maWeight=%.2f;"
                        + "volumeBreakout=%s;breakoutLookback=%d;volumeMultiplier=%.2f;volumeWeight=%.2f;volumeSustain=%s;breakoutContinuation=%s;nextBarConfirm=%s;blockMaOnlyEntry=%s;"
                        + "requireAboveVwap=%s;rangeRequiresVwapVolume=%s;rangeFailureExit=%s;rangeFailureMinutes=%d;rangeFailureMinR=%.2f;rangeVolumeFailExit=%s;"
                        + "marketRegimeFilter=%s;internalAllowVwap=%.2f;internalAllowAvgReturn=%.2f;internalAllowVolumeSustain=%.2f;"
                        + "internalBlockVwap=%.2f;internalBlockAvgReturn=%.2f;internalBlockNewLowExcess=%d;"
                        + "weakStrictLong=%s;weakPolicy=%s;weakOutperformInternalBenchmark=%.2f;weakOutperformWatchlistGroup=%.2f;"
                        + "atrRisk=%s;atrChaseLimitEnabled=%s;atrPeriod=%d;atrStop=%.2f;atrTakeProfit=%.2f;atrChaseLimit=%.2f;"
                        + "maxEntryRiseFromRecentLow=%.3f;minimumEntryScore=%.3f;crossDayWarmup=%s;warmupBars=%d",
                strategyName != null ? strategyName : CURRENT_TEMPLATE_NAME,
                monitor.getScanIntervalSeconds(),
                monitor.getTimeframe(),
                radar.getDayTradeTimeframe(),
                radar.getExecutionConfirmationTimeframe(),
                monitor.getBarCount(),
                radar.getDayTradeBarCount(),
                monitor.getTradeMode(),
                monitor.getDecisionSource(),
                monitor.isAutoManagedEntries(),
                monitor.getDayTradeOrderQuantity(),
                monitor.getMinSignalIntervalMinutes(),
                monitor.isEarlyEntryBlockEnabled(),
                monitor.getEarlyEntryBlockStart(),
                monitor.getEarlyEntryBlockEnd(),
                monitor.getLatestAutoEntryTime(),
                monitor.isStopLossCooldownEnabled(),
                monitor.getStopLossCooldownMinutes(),
                monitor.getPostExitCooldownMinutes(),
                decision.getRiskConfig().getMaxConcurrentPositions(),
                monitor.getDailyMaxLoss(),
                monitor.getDailyMaxStopLossCount(),
                monitor.getConsecutiveLossLimit(),
                monitor.isDisableTradingAfterLossLimit(),
                monitor.getDailyMaxAutoTrades(),
                monitor.getEntryPacingMinutes(),
                monitor.isOneEntryPerFiveMinuteBar(),
                decision.getVotingConfig().getLongEntryThreshold(),
                decision.getVotingConfig().getExitThreshold(),
                decision.getVotingConfig().getMinVotingStrategies(),
                decision.getRiskConfig().getMinRiskRewardRatio(),
                decision.getRiskConfig().getMinVolatilityPercent(),
                decision.getRiskConfig().getMaxVolatilityPercent(),
                radar.isRsiEnabled(),
                radar.getRsiPeriod(),
                radar.getRsiOversold(),
                radar.getRsiOverbought(),
                radar.getRsiWeight(),
                radar.isRequireRsiEntryConfirmation(),
                radar.isBlockBreakoutOnRsiOverbought(),
                radar.isMovingAverageEnabled(),
                radar.getMovingAverageType(),
                radar.getFastMovingAveragePeriod(),
                radar.getSlowMovingAveragePeriod(),
                radar.getMovingAverageWeight(),
                radar.isVolumeBreakoutEnabled(),
                radar.getBreakoutLookbackBars(),
                radar.getVolumeMultiplier(),
                radar.getVolumeBreakoutWeight(),
                radar.isVolumeSustainEnabled(),
                radar.isRequireBreakoutContinuation(),
                radar.isRequireBreakoutNextBarConfirmation(),
                radar.isBlockMovingAverageOnlyEntry(),
                radar.isRequirePriceAboveVwapForLong(),
                radar.isRangeMarketRequiresVwapAndVolume(),
                monitor.isRangeFailureExitEnabled(),
                monitor.getRangeFailureExitMinutes(),
                monitor.getRangeFailureMinR(),
                monitor.isRangeFailureVolumeSustainExitEnabled(),
                radar.isMarketRegimeFilterEnabled(),
                radar.getInternalAllowVwapPassPercent(),
                radar.getInternalAllowAverageReturnPercent(),
                radar.getInternalAllowVolumeSustainPercent(),
                radar.getInternalBlockVwapPassPercent(),
                radar.getInternalBlockAverageReturnPercent(),
                radar.getInternalBlockNewLowExcessCount(),
                radar.isWeakMarketStrictLongEnabled(),
                radar.getWeakMarketLongPolicy(),
                radar.getWeakOutperformBenchmarkPercent(),
                radar.getWeakOutperformIndustryPercent(),
                radar.isAtrRiskEnabled(),
                radar.isAtrChaseLimitEnabled(),
                radar.getAtrPeriod(),
                radar.getAtrStopMultiplier(),
                radar.getAtrTakeProfitMultiplier(),
                radar.getAtrChaseLimitMultiplier(),
                radar.getMaxEntryRiseFromRecentLowPercent(),
                radar.getMinimumEntryScore(),
                radar.isBacktestCrossDayWarmupEnabled(),
                radar.getBacktestWarmupBarCount());
    }

    private static void writeMonitorConfig(Properties props, String prefix, SignalMonitorConfig monitorConfig) {
        SignalMonitorConfig monitor = monitorConfig != null ? monitorConfig : SignalMonitorConfig.createDayTradeStandardTemplate();
        RadarStrategyConfig radar = monitor.getRadarStrategyConfig() != null
                ? monitor.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        props.setProperty(prefix + "scanIntervalSeconds", String.valueOf(monitor.getScanIntervalSeconds()));
        props.setProperty(prefix + "timeframe", monitor.getTimeframe().name());
        props.setProperty(prefix + "minSignalIntervalMinutes", String.valueOf(monitor.getMinSignalIntervalMinutes()));
        props.setProperty(prefix + "barCount", String.valueOf(monitor.getBarCount()));
        props.setProperty(prefix + "tradeMode", monitor.getTradeMode().name());
        props.setProperty(prefix + "batchScanMode", String.valueOf(monitor.isBatchScanMode()));
        props.setProperty(prefix + "decisionSource", monitor.getDecisionSource().name());
        props.setProperty(prefix + "autoManagedEntries", String.valueOf(monitor.isAutoManagedEntries()));
        props.setProperty(prefix + "dayTradeOrderQuantity", String.valueOf(monitor.getDayTradeOrderQuantity()));
        props.setProperty(prefix + "earlyEntryBlockEnabled", String.valueOf(monitor.isEarlyEntryBlockEnabled()));
        props.setProperty(prefix + "earlyEntryBlockStart", monitor.getEarlyEntryBlockStart().toString());
        props.setProperty(prefix + "earlyEntryBlockEnd", monitor.getEarlyEntryBlockEnd().toString());
        props.setProperty(prefix + "stopLossCooldownEnabled", String.valueOf(monitor.isStopLossCooldownEnabled()));
        props.setProperty(prefix + "stopLossCooldownMinutes", String.valueOf(monitor.getStopLossCooldownMinutes()));
        props.setProperty(prefix + "latestAutoEntryTime", monitor.getLatestAutoEntryTime().toString());
        props.setProperty(prefix + "dailyMaxLoss", String.valueOf(monitor.getDailyMaxLoss()));
        props.setProperty(prefix + "dailyMaxStopLossCount", String.valueOf(monitor.getDailyMaxStopLossCount()));
        props.setProperty(prefix + "consecutiveLossLimit", String.valueOf(monitor.getConsecutiveLossLimit()));
        props.setProperty(prefix + "disableTradingAfterLossLimit", String.valueOf(monitor.isDisableTradingAfterLossLimit()));
        props.setProperty(prefix + "dailyMaxAutoTrades", String.valueOf(monitor.getDailyMaxAutoTrades()));
        props.setProperty(prefix + "entryPacingMinutes", String.valueOf(monitor.getEntryPacingMinutes()));
        props.setProperty(prefix + "postExitCooldownMinutes", String.valueOf(monitor.getPostExitCooldownMinutes()));
        props.setProperty(prefix + "oneEntryPerFiveMinuteBar", String.valueOf(monitor.isOneEntryPerFiveMinuteBar()));
        props.setProperty(prefix + "rangeFailureExitEnabled", String.valueOf(monitor.isRangeFailureExitEnabled()));
        props.setProperty(prefix + "rangeFailureExitMinutes", String.valueOf(monitor.getRangeFailureExitMinutes()));
        props.setProperty(prefix + "rangeFailureMinR", String.valueOf(monitor.getRangeFailureMinR()));
        props.setProperty(prefix + "rangeFailureVolumeSustainExitEnabled", String.valueOf(monitor.isRangeFailureVolumeSustainExitEnabled()));

        props.setProperty(prefix + "radar.dayTradeTimeframe", radar.getDayTradeTimeframe().name());
        props.setProperty(prefix + "radar.executionConfirmationTimeframe", radar.getExecutionConfirmationTimeframe().name());
        props.setProperty(prefix + "radar.shortSwingTimeframe", radar.getShortSwingTimeframe().name());
        props.setProperty(prefix + "radar.swingTradeTimeframe", radar.getSwingTradeTimeframe().name());
        props.setProperty(prefix + "radar.dayTradeBarCount", String.valueOf(radar.getDayTradeBarCount()));
        props.setProperty(prefix + "radar.shortSwingBarCount", String.valueOf(radar.getShortSwingBarCount()));
        props.setProperty(prefix + "radar.swingTradeBarCount", String.valueOf(radar.getSwingTradeBarCount()));
        props.setProperty(prefix + "radar.rsiEnabled", String.valueOf(radar.isRsiEnabled()));
        props.setProperty(prefix + "radar.rsiPeriod", String.valueOf(radar.getRsiPeriod()));
        props.setProperty(prefix + "radar.rsiOversold", String.valueOf(radar.getRsiOversold()));
        props.setProperty(prefix + "radar.rsiOverbought", String.valueOf(radar.getRsiOverbought()));
        props.setProperty(prefix + "radar.rsiWeight", String.valueOf(radar.getRsiWeight()));
        props.setProperty(prefix + "radar.requireRsiEntryConfirmation", String.valueOf(radar.isRequireRsiEntryConfirmation()));
        props.setProperty(prefix + "radar.movingAverageEnabled", String.valueOf(radar.isMovingAverageEnabled()));
        props.setProperty(prefix + "radar.movingAverageType", radar.getMovingAverageType().name());
        props.setProperty(prefix + "radar.fastMovingAveragePeriod", String.valueOf(radar.getFastMovingAveragePeriod()));
        props.setProperty(prefix + "radar.slowMovingAveragePeriod", String.valueOf(radar.getSlowMovingAveragePeriod()));
        props.setProperty(prefix + "radar.movingAverageWeight", String.valueOf(radar.getMovingAverageWeight()));
        props.setProperty(prefix + "radar.volumeBreakoutEnabled", String.valueOf(radar.isVolumeBreakoutEnabled()));
        props.setProperty(prefix + "radar.breakoutLookbackBars", String.valueOf(radar.getBreakoutLookbackBars()));
        props.setProperty(prefix + "radar.volumeMultiplier", String.valueOf(radar.getVolumeMultiplier()));
        props.setProperty(prefix + "radar.volumeBreakoutWeight", String.valueOf(radar.getVolumeBreakoutWeight()));
        props.setProperty(prefix + "radar.minimumEntryScore", String.valueOf(radar.getMinimumEntryScore()));
        props.setProperty(prefix + "radar.blockBreakoutOnRsiOverbought", String.valueOf(radar.isBlockBreakoutOnRsiOverbought()));
        props.setProperty(prefix + "radar.requireBreakoutContinuation", String.valueOf(radar.isRequireBreakoutContinuation()));
        props.setProperty(prefix + "radar.requirePriceAboveVwapForLong", String.valueOf(radar.isRequirePriceAboveVwapForLong()));
        props.setProperty(prefix + "radar.requireBreakoutNextBarConfirmation", String.valueOf(radar.isRequireBreakoutNextBarConfirmation()));
        props.setProperty(prefix + "radar.blockMovingAverageOnlyEntry", String.valueOf(radar.isBlockMovingAverageOnlyEntry()));
        props.setProperty(prefix + "radar.maxEntryRiseFromRecentLowPercent", String.valueOf(radar.getMaxEntryRiseFromRecentLowPercent()));
        props.setProperty(prefix + "radar.marketRegimeFilterEnabled", String.valueOf(radar.isMarketRegimeFilterEnabled()));
        props.setProperty(prefix + "radar.weakMarketStrictLongEnabled", String.valueOf(radar.isWeakMarketStrictLongEnabled()));
        props.setProperty(prefix + "radar.weakMarketLongPolicy", radar.getWeakMarketLongPolicy().name());
        props.setProperty(prefix + "radar.weakOutperformBenchmarkPercent", String.valueOf(radar.getWeakOutperformBenchmarkPercent()));
        props.setProperty(prefix + "radar.weakOutperformIndustryPercent", String.valueOf(radar.getWeakOutperformIndustryPercent()));
        props.setProperty(prefix + "radar.internalAllowVwapPassPercent", String.valueOf(radar.getInternalAllowVwapPassPercent()));
        props.setProperty(prefix + "radar.internalAllowAverageReturnPercent", String.valueOf(radar.getInternalAllowAverageReturnPercent()));
        props.setProperty(prefix + "radar.internalAllowVolumeSustainPercent", String.valueOf(radar.getInternalAllowVolumeSustainPercent()));
        props.setProperty(prefix + "radar.internalBlockVwapPassPercent", String.valueOf(radar.getInternalBlockVwapPassPercent()));
        props.setProperty(prefix + "radar.internalBlockAverageReturnPercent", String.valueOf(radar.getInternalBlockAverageReturnPercent()));
        props.setProperty(prefix + "radar.internalBlockNewLowExcessCount", String.valueOf(radar.getInternalBlockNewLowExcessCount()));
        props.setProperty(prefix + "radar.rangeMarketRequiresVwapAndVolume", String.valueOf(radar.isRangeMarketRequiresVwapAndVolume()));
        props.setProperty(prefix + "radar.volumeSustainEnabled", String.valueOf(radar.isVolumeSustainEnabled()));
        props.setProperty(prefix + "radar.atrRiskEnabled", String.valueOf(radar.isAtrRiskEnabled()));
        props.setProperty(prefix + "radar.atrChaseLimitEnabled", String.valueOf(radar.isAtrChaseLimitEnabled()));
        props.setProperty(prefix + "radar.atrPeriod", String.valueOf(radar.getAtrPeriod()));
        props.setProperty(prefix + "radar.atrStopMultiplier", String.valueOf(radar.getAtrStopMultiplier()));
        props.setProperty(prefix + "radar.atrTakeProfitMultiplier", String.valueOf(radar.getAtrTakeProfitMultiplier()));
        props.setProperty(prefix + "radar.atrChaseLimitMultiplier", String.valueOf(radar.getAtrChaseLimitMultiplier()));
        props.setProperty(prefix + "radar.backtestCrossDayWarmupEnabled", String.valueOf(radar.isBacktestCrossDayWarmupEnabled()));
        props.setProperty(prefix + "radar.backtestWarmupBarCount", String.valueOf(radar.getBacktestWarmupBarCount()));
    }

    private static SignalMonitorConfig readMonitorConfig(Properties props, String prefix) {
        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        RadarStrategyConfig radar = monitor.getRadarStrategyConfig().copy();
        monitor.setScanIntervalSeconds(parseInt(props.getProperty(prefix + "scanIntervalSeconds"), monitor.getScanIntervalSeconds()));
        monitor.setTimeframe(parseEnum(props.getProperty(prefix + "timeframe"), Timeframe.class, monitor.getTimeframe()));
        monitor.setMinSignalIntervalMinutes(parseInt(props.getProperty(prefix + "minSignalIntervalMinutes"), monitor.getMinSignalIntervalMinutes()));
        monitor.setBarCount(parseInt(props.getProperty(prefix + "barCount"), monitor.getBarCount()));
        monitor.setTradeMode(parseEnum(props.getProperty(prefix + "tradeMode"), com.dreamhouse.trading.core.decision.classifier.TradeMode.class, monitor.getTradeMode()));
        monitor.setBatchScanMode(parseBoolean(props.getProperty(prefix + "batchScanMode"), monitor.isBatchScanMode()));
        monitor.setDecisionSource(parseEnum(props.getProperty(prefix + "decisionSource"), DecisionResult.DecisionSource.class, monitor.getDecisionSource()));
        monitor.setAutoManagedEntries(parseBoolean(props.getProperty(prefix + "autoManagedEntries"), monitor.isAutoManagedEntries()));
        monitor.setDayTradeOrderQuantity(parseInt(props.getProperty(prefix + "dayTradeOrderQuantity"), monitor.getDayTradeOrderQuantity()));
        monitor.setEarlyEntryBlockEnabled(parseBoolean(props.getProperty(prefix + "earlyEntryBlockEnabled"), monitor.isEarlyEntryBlockEnabled()));
        monitor.setEarlyEntryBlockStart(parseTime(props.getProperty(prefix + "earlyEntryBlockStart"), monitor.getEarlyEntryBlockStart()));
        monitor.setEarlyEntryBlockEnd(parseTime(props.getProperty(prefix + "earlyEntryBlockEnd"), monitor.getEarlyEntryBlockEnd()));
        monitor.setStopLossCooldownEnabled(parseBoolean(props.getProperty(prefix + "stopLossCooldownEnabled"), monitor.isStopLossCooldownEnabled()));
        monitor.setStopLossCooldownMinutes(parseInt(props.getProperty(prefix + "stopLossCooldownMinutes"), monitor.getStopLossCooldownMinutes()));
        monitor.setLatestAutoEntryTime(parseTime(props.getProperty(prefix + "latestAutoEntryTime"), monitor.getLatestAutoEntryTime()));
        monitor.setDailyMaxLoss(parseDouble(props.getProperty(prefix + "dailyMaxLoss"), monitor.getDailyMaxLoss()));
        monitor.setDailyMaxStopLossCount(parseInt(props.getProperty(prefix + "dailyMaxStopLossCount"), monitor.getDailyMaxStopLossCount()));
        monitor.setConsecutiveLossLimit(parseInt(props.getProperty(prefix + "consecutiveLossLimit"), monitor.getConsecutiveLossLimit()));
        monitor.setDisableTradingAfterLossLimit(parseBoolean(props.getProperty(prefix + "disableTradingAfterLossLimit"), monitor.isDisableTradingAfterLossLimit()));
        monitor.setDailyMaxAutoTrades(parseInt(props.getProperty(prefix + "dailyMaxAutoTrades"), monitor.getDailyMaxAutoTrades()));
        monitor.setEntryPacingMinutes(parseInt(props.getProperty(prefix + "entryPacingMinutes"), monitor.getEntryPacingMinutes()));
        monitor.setPostExitCooldownMinutes(parseInt(props.getProperty(prefix + "postExitCooldownMinutes"), monitor.getPostExitCooldownMinutes()));
        monitor.setOneEntryPerFiveMinuteBar(parseBoolean(props.getProperty(prefix + "oneEntryPerFiveMinuteBar"), monitor.isOneEntryPerFiveMinuteBar()));
        monitor.setRangeFailureExitEnabled(parseBoolean(props.getProperty(prefix + "rangeFailureExitEnabled"), monitor.isRangeFailureExitEnabled()));
        monitor.setRangeFailureExitMinutes(parseInt(props.getProperty(prefix + "rangeFailureExitMinutes"), monitor.getRangeFailureExitMinutes()));
        monitor.setRangeFailureMinR(parseDouble(props.getProperty(prefix + "rangeFailureMinR"), monitor.getRangeFailureMinR()));
        monitor.setRangeFailureVolumeSustainExitEnabled(parseBoolean(props.getProperty(prefix + "rangeFailureVolumeSustainExitEnabled"), monitor.isRangeFailureVolumeSustainExitEnabled()));

        radar.setDayTradeTimeframe(parseEnum(props.getProperty(prefix + "radar.dayTradeTimeframe"), Timeframe.class, radar.getDayTradeTimeframe()));
        radar.setExecutionConfirmationTimeframe(parseEnum(props.getProperty(prefix + "radar.executionConfirmationTimeframe"), Timeframe.class, radar.getExecutionConfirmationTimeframe()));
        radar.setShortSwingTimeframe(parseEnum(props.getProperty(prefix + "radar.shortSwingTimeframe"), Timeframe.class, radar.getShortSwingTimeframe()));
        radar.setSwingTradeTimeframe(parseEnum(props.getProperty(prefix + "radar.swingTradeTimeframe"), Timeframe.class, radar.getSwingTradeTimeframe()));
        radar.setDayTradeBarCount(parseInt(props.getProperty(prefix + "radar.dayTradeBarCount"), radar.getDayTradeBarCount()));
        radar.setShortSwingBarCount(parseInt(props.getProperty(prefix + "radar.shortSwingBarCount"), radar.getShortSwingBarCount()));
        radar.setSwingTradeBarCount(parseInt(props.getProperty(prefix + "radar.swingTradeBarCount"), radar.getSwingTradeBarCount()));
        radar.setRsiEnabled(parseBoolean(props.getProperty(prefix + "radar.rsiEnabled"), radar.isRsiEnabled()));
        radar.setRsiPeriod(parseInt(props.getProperty(prefix + "radar.rsiPeriod"), radar.getRsiPeriod()));
        radar.setRsiOversold(parseDouble(props.getProperty(prefix + "radar.rsiOversold"), radar.getRsiOversold()));
        radar.setRsiOverbought(parseDouble(props.getProperty(prefix + "radar.rsiOverbought"), radar.getRsiOverbought()));
        radar.setRsiWeight(parseDouble(props.getProperty(prefix + "radar.rsiWeight"), radar.getRsiWeight()));
        radar.setRequireRsiEntryConfirmation(parseBoolean(props.getProperty(prefix + "radar.requireRsiEntryConfirmation"), radar.isRequireRsiEntryConfirmation()));
        radar.setMovingAverageEnabled(parseBoolean(props.getProperty(prefix + "radar.movingAverageEnabled"), radar.isMovingAverageEnabled()));
        radar.setMovingAverageType(parseEnum(props.getProperty(prefix + "radar.movingAverageType"), RadarStrategyConfig.MovingAverageType.class, radar.getMovingAverageType()));
        radar.setFastMovingAveragePeriod(parseInt(props.getProperty(prefix + "radar.fastMovingAveragePeriod"), radar.getFastMovingAveragePeriod()));
        radar.setSlowMovingAveragePeriod(parseInt(props.getProperty(prefix + "radar.slowMovingAveragePeriod"), radar.getSlowMovingAveragePeriod()));
        radar.setMovingAverageWeight(parseDouble(props.getProperty(prefix + "radar.movingAverageWeight"), radar.getMovingAverageWeight()));
        radar.setVolumeBreakoutEnabled(parseBoolean(props.getProperty(prefix + "radar.volumeBreakoutEnabled"), radar.isVolumeBreakoutEnabled()));
        radar.setBreakoutLookbackBars(parseInt(props.getProperty(prefix + "radar.breakoutLookbackBars"), radar.getBreakoutLookbackBars()));
        radar.setVolumeMultiplier(parseDouble(props.getProperty(prefix + "radar.volumeMultiplier"), radar.getVolumeMultiplier()));
        radar.setVolumeBreakoutWeight(parseDouble(props.getProperty(prefix + "radar.volumeBreakoutWeight"), radar.getVolumeBreakoutWeight()));
        radar.setMinimumEntryScore(parseDouble(props.getProperty(prefix + "radar.minimumEntryScore"), radar.getMinimumEntryScore()));
        radar.setBlockBreakoutOnRsiOverbought(parseBoolean(props.getProperty(prefix + "radar.blockBreakoutOnRsiOverbought"), radar.isBlockBreakoutOnRsiOverbought()));
        radar.setRequireBreakoutContinuation(parseBoolean(props.getProperty(prefix + "radar.requireBreakoutContinuation"), radar.isRequireBreakoutContinuation()));
        radar.setRequirePriceAboveVwapForLong(parseBoolean(props.getProperty(prefix + "radar.requirePriceAboveVwapForLong"), radar.isRequirePriceAboveVwapForLong()));
        radar.setRequireBreakoutNextBarConfirmation(parseBoolean(props.getProperty(prefix + "radar.requireBreakoutNextBarConfirmation"), radar.isRequireBreakoutNextBarConfirmation()));
        radar.setBlockMovingAverageOnlyEntry(parseBoolean(props.getProperty(prefix + "radar.blockMovingAverageOnlyEntry"), radar.isBlockMovingAverageOnlyEntry()));
        radar.setMaxEntryRiseFromRecentLowPercent(parseDouble(props.getProperty(prefix + "radar.maxEntryRiseFromRecentLowPercent"), radar.getMaxEntryRiseFromRecentLowPercent()));
        radar.setMarketRegimeFilterEnabled(parseBoolean(props.getProperty(prefix + "radar.marketRegimeFilterEnabled"), radar.isMarketRegimeFilterEnabled()));
        radar.setWeakMarketStrictLongEnabled(parseBoolean(props.getProperty(prefix + "radar.weakMarketStrictLongEnabled"), radar.isWeakMarketStrictLongEnabled()));
        radar.setWeakMarketLongPolicy(parseEnum(props.getProperty(prefix + "radar.weakMarketLongPolicy"), WeakMarketLongPolicy.class, radar.getWeakMarketLongPolicy()));
        radar.setWeakOutperformBenchmarkPercent(parseDouble(props.getProperty(prefix + "radar.weakOutperformBenchmarkPercent"), radar.getWeakOutperformBenchmarkPercent()));
        radar.setWeakOutperformIndustryPercent(parseDouble(props.getProperty(prefix + "radar.weakOutperformIndustryPercent"), radar.getWeakOutperformIndustryPercent()));
        radar.setInternalAllowVwapPassPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowVwapPassPercent"), radar.getInternalAllowVwapPassPercent()));
        radar.setInternalAllowAverageReturnPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowAverageReturnPercent"), radar.getInternalAllowAverageReturnPercent()));
        radar.setInternalAllowVolumeSustainPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowVolumeSustainPercent"), radar.getInternalAllowVolumeSustainPercent()));
        radar.setInternalBlockVwapPassPercent(parseDouble(props.getProperty(prefix + "radar.internalBlockVwapPassPercent"), radar.getInternalBlockVwapPassPercent()));
        radar.setInternalBlockAverageReturnPercent(parseDouble(props.getProperty(prefix + "radar.internalBlockAverageReturnPercent"), radar.getInternalBlockAverageReturnPercent()));
        radar.setInternalBlockNewLowExcessCount(parseInt(props.getProperty(prefix + "radar.internalBlockNewLowExcessCount"), radar.getInternalBlockNewLowExcessCount()));
        radar.setRangeMarketRequiresVwapAndVolume(parseBoolean(props.getProperty(prefix + "radar.rangeMarketRequiresVwapAndVolume"), radar.isRangeMarketRequiresVwapAndVolume()));
        radar.setVolumeSustainEnabled(parseBoolean(props.getProperty(prefix + "radar.volumeSustainEnabled"), radar.isVolumeSustainEnabled()));
        radar.setAtrRiskEnabled(parseBoolean(props.getProperty(prefix + "radar.atrRiskEnabled"), radar.isAtrRiskEnabled()));
        radar.setAtrChaseLimitEnabled(parseBoolean(props.getProperty(prefix + "radar.atrChaseLimitEnabled"), radar.isAtrChaseLimitEnabled()));
        radar.setAtrPeriod(parseInt(props.getProperty(prefix + "radar.atrPeriod"), radar.getAtrPeriod()));
        radar.setAtrStopMultiplier(parseDouble(props.getProperty(prefix + "radar.atrStopMultiplier"), radar.getAtrStopMultiplier()));
        radar.setAtrTakeProfitMultiplier(parseDouble(props.getProperty(prefix + "radar.atrTakeProfitMultiplier"), radar.getAtrTakeProfitMultiplier()));
        radar.setAtrChaseLimitMultiplier(parseDouble(props.getProperty(prefix + "radar.atrChaseLimitMultiplier"), radar.getAtrChaseLimitMultiplier()));
        radar.setBacktestCrossDayWarmupEnabled(parseBoolean(props.getProperty(prefix + "radar.backtestCrossDayWarmupEnabled"), radar.isBacktestCrossDayWarmupEnabled()));
        radar.setBacktestWarmupBarCount(parseInt(props.getProperty(prefix + "radar.backtestWarmupBarCount"), radar.getBacktestWarmupBarCount()));
        monitor.setRadarStrategyConfig(radar);
        return monitor;
    }

    private static void writeDecisionConfig(Properties props, String prefix, DecisionConfig decisionConfig) {
        DecisionConfig decision = decisionConfig != null ? decisionConfig : createDayTradeStandardMonitorConfig();
        props.setProperty(prefix + "decision.longEntryThreshold", String.valueOf(decision.getVotingConfig().getLongEntryThreshold()));
        props.setProperty(prefix + "decision.exitThreshold", String.valueOf(decision.getVotingConfig().getExitThreshold()));
        props.setProperty(prefix + "decision.minVotingStrategies", String.valueOf(decision.getVotingConfig().getMinVotingStrategies()));
        props.setProperty(prefix + "decision.riskManagementEnabled", String.valueOf(decision.isRiskManagementEnabled()));
        props.setProperty(prefix + "decision.maxConcurrentPositions", String.valueOf(decision.getRiskConfig().getMaxConcurrentPositions()));
        props.setProperty(prefix + "decision.minRiskRewardRatio", String.valueOf(decision.getRiskConfig().getMinRiskRewardRatio()));
        props.setProperty(prefix + "decision.minVolatilityPercent", String.valueOf(decision.getRiskConfig().getMinVolatilityPercent()));
        props.setProperty(prefix + "decision.maxVolatilityPercent", String.valueOf(decision.getRiskConfig().getMaxVolatilityPercent()));
    }

    private static DecisionConfig readDecisionConfig(Properties props, String prefix) {
        DecisionConfig decision = createDayTradeStandardMonitorConfig();
        decision.getVotingConfig().setLongEntryThreshold(parseDouble(props.getProperty(prefix + "decision.longEntryThreshold"), decision.getVotingConfig().getLongEntryThreshold()));
        decision.getVotingConfig().setExitThreshold(parseDouble(props.getProperty(prefix + "decision.exitThreshold"), decision.getVotingConfig().getExitThreshold()));
        decision.getVotingConfig().setMinVotingStrategies(parseInt(props.getProperty(prefix + "decision.minVotingStrategies"), decision.getVotingConfig().getMinVotingStrategies()));
        decision.setRiskManagementEnabled(parseBoolean(props.getProperty(prefix + "decision.riskManagementEnabled"), decision.isRiskManagementEnabled()));
        decision.getRiskConfig().setMaxConcurrentPositions(parseInt(props.getProperty(prefix + "decision.maxConcurrentPositions"), decision.getRiskConfig().getMaxConcurrentPositions()));
        decision.getRiskConfig().setMinRiskRewardRatio(parseDouble(props.getProperty(prefix + "decision.minRiskRewardRatio"), decision.getRiskConfig().getMinRiskRewardRatio()));
        decision.getRiskConfig().setMinVolatilityPercent(parseDouble(props.getProperty(prefix + "decision.minVolatilityPercent"), decision.getRiskConfig().getMinVolatilityPercent()));
        decision.getRiskConfig().setMaxVolatilityPercent(parseDouble(props.getProperty(prefix + "decision.maxVolatilityPercent"), decision.getRiskConfig().getMaxVolatilityPercent()));
        decision.getRiskConfig().setAllowShortSelling(false);
        return decision;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        try {
            return value == null ? fallback : LocalTime.parse(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
