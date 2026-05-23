package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

/**
 * Strategy and timeframe settings used by the opportunity radar.
 */
public class RadarStrategyConfig {

    public enum MovingAverageType {
        EMA,
        SMA
    }

    private boolean rsiEnabled = true;
    private int rsiPeriod = 5;
    private double rsiOversold = 40.0;
    private double rsiOverbought = 60.0;
    private double rsiWeight = 1.0;

    private boolean movingAverageEnabled = true;
    private MovingAverageType movingAverageType = MovingAverageType.EMA;
    private int fastMovingAveragePeriod = 5;
    private int slowMovingAveragePeriod = 20;
    private double movingAverageWeight = 0.8;

    private boolean volumeBreakoutEnabled = true;
    private int breakoutLookbackBars = 20;
    private double volumeMultiplier = 1.8;
    private double volumeBreakoutWeight = 0.8;
    private boolean requireRsiEntryConfirmation = true;
    private boolean blockBreakoutOnRsiOverbought = true;
    private boolean requireBreakoutContinuation = true;
    private boolean requirePriceAboveVwapForLong = false;
    private boolean requireBreakoutNextBarConfirmation = false;
    private boolean blockMovingAverageOnlyEntry = false;
    private double maxEntryRiseFromRecentLowPercent = 0.0;
    private double minimumEntryScore = 0.50;
    private boolean marketRegimeFilterEnabled = true;
    private boolean weakMarketStrictLongEnabled = true;
    private WeakMarketLongPolicy weakMarketLongPolicy = WeakMarketLongPolicy.BLOCK_ALL;
    private double weakOutperformBenchmarkPercent = 0.30;
    private double weakOutperformIndustryPercent = 0.20;
    private boolean rangeMarketRequiresVwapAndVolume = true;
    private double internalAllowVwapPassPercent = 60.0;
    private double internalAllowAverageReturnPercent = 0.0;
    private double internalAllowVolumeSustainPercent = 20.0;
    private double internalBlockVwapPassPercent = 40.0;
    private double internalBlockAverageReturnPercent = -0.8;
    private int internalBlockNewLowExcessCount = 2;
    private boolean volumeSustainEnabled = false;
    private boolean atrRiskEnabled = false;
    private boolean atrChaseLimitEnabled = false;
    private int atrPeriod = 14;
    private double atrStopMultiplier = 1.00;
    private double atrTakeProfitMultiplier = 1.60;
    private double atrChaseLimitMultiplier = 2.00;
    private boolean backtestCrossDayWarmupEnabled = false;
    private int backtestWarmupBarCount = 96;

    private Timeframe dayTradeTimeframe = Timeframe.M1;
    private Timeframe executionConfirmationTimeframe = Timeframe.M1;
    private Timeframe shortSwingTimeframe = Timeframe.M15;
    private Timeframe swingTradeTimeframe = Timeframe.H1;
    private int dayTradeBarCount = 160;
    private int shortSwingBarCount = 180;
    private int swingTradeBarCount = 240;

    public static RadarStrategyConfig createDefault() {
        return new RadarStrategyConfig();
    }

    public static RadarStrategyConfig createSimulationTestTemplate() {
        RadarStrategyConfig config = createDefault();
        config.setRsiPeriod(5);
        config.setRsiOversold(40.0);
        config.setRsiOverbought(60.0);
        config.setFastMovingAveragePeriod(5);
        config.setSlowMovingAveragePeriod(20);
        config.setBreakoutLookbackBars(20);
        config.setVolumeMultiplier(1.4);
        return config;
    }

    public static RadarStrategyConfig createAggressiveTemplate() {
        RadarStrategyConfig config = createDefault();
        config.setRsiPeriod(5);
        config.setRsiOversold(42.0);
        config.setRsiOverbought(62.0);
        config.setFastMovingAveragePeriod(5);
        config.setSlowMovingAveragePeriod(20);
        config.setBreakoutLookbackBars(20);
        config.setVolumeMultiplier(1.6);
        return config;
    }

    public static RadarStrategyConfig createBalancedTemplate() {
        RadarStrategyConfig config = createDefault();
        config.setRsiPeriod(14);
        config.setRsiOversold(30.0);
        config.setRsiOverbought(70.0);
        config.setFastMovingAveragePeriod(20);
        config.setSlowMovingAveragePeriod(60);
        config.setBreakoutLookbackBars(40);
        config.setVolumeMultiplier(1.5);
        config.setDayTradeTimeframe(Timeframe.M5);
        return config;
    }

    public static RadarStrategyConfig createBConvergenceTemplate() {
        RadarStrategyConfig config = createDefault();
        config.setDayTradeTimeframe(Timeframe.M5);
        config.setDayTradeBarCount(220);
        config.setRsiEnabled(true);
        config.setRsiPeriod(9);
        config.setRsiOversold(35.0);
        config.setRsiOverbought(68.0);
        config.setRsiWeight(0.90);
        config.setMovingAverageEnabled(true);
        config.setMovingAverageType(MovingAverageType.EMA);
        config.setFastMovingAveragePeriod(8);
        config.setSlowMovingAveragePeriod(34);
        config.setMovingAverageWeight(0.80);
        config.setVolumeBreakoutEnabled(true);
        config.setBreakoutLookbackBars(30);
        config.setVolumeMultiplier(1.60);
        config.setVolumeBreakoutWeight(0.85);
        config.setRequireRsiEntryConfirmation(true);
        config.setBlockBreakoutOnRsiOverbought(true);
        config.setRequireBreakoutContinuation(true);
        config.setRequirePriceAboveVwapForLong(true);
        config.setRequireBreakoutNextBarConfirmation(false);
        config.setMaxEntryRiseFromRecentLowPercent(0.03);
        config.setMinimumEntryScore(0.45);
        config.setMarketRegimeFilterEnabled(true);
        config.setWeakMarketStrictLongEnabled(true);
        config.setWeakMarketLongPolicy(WeakMarketLongPolicy.ALLOW_EXTREME_STRENGTH_ONLY);
        config.setRangeMarketRequiresVwapAndVolume(true);
        config.setVolumeSustainEnabled(true);
        config.setAtrRiskEnabled(true);
        config.setAtrChaseLimitEnabled(true);
        config.setAtrPeriod(14);
        config.setAtrStopMultiplier(1.00);
        config.setAtrTakeProfitMultiplier(1.60);
        config.setAtrChaseLimitMultiplier(2.00);
        return config;
    }

    public static RadarStrategyConfig createDayTradeGroupATemplate() {
        RadarStrategyConfig config = createBConvergenceTemplate();
        config.setSlowMovingAveragePeriod(21);
        config.setBacktestCrossDayWarmupEnabled(false);
        config.setMarketRegimeFilterEnabled(false);
        config.setWeakMarketStrictLongEnabled(false);
        config.setRangeMarketRequiresVwapAndVolume(false);
        return config;
    }

    public static RadarStrategyConfig createDayTradeGroupBTemplate() {
        RadarStrategyConfig config = createBConvergenceTemplate();
        config.setBacktestCrossDayWarmupEnabled(true);
        config.setBacktestWarmupBarCount(120);
        config.setMarketRegimeFilterEnabled(false);
        config.setWeakMarketStrictLongEnabled(false);
        config.setRangeMarketRequiresVwapAndVolume(false);
        return config;
    }

    public static RadarStrategyConfig createDayTradeGroupCTemplate() {
        RadarStrategyConfig config = createDayTradeGroupBTemplate();
        config.setRsiOversold(38.0);
        config.setRsiOverbought(70.0);
        config.setRsiWeight(0.65);
        config.setVolumeMultiplier(1.45);
        config.setVolumeBreakoutWeight(0.95);
        config.setMinimumEntryScore(0.50);
        config.setBlockMovingAverageOnlyEntry(true);
        config.setRequireBreakoutNextBarConfirmation(true);
        return config;
    }

    public static RadarStrategyConfig createDayTradeDefensiveTemplate() {
        RadarStrategyConfig config = createBConvergenceTemplate();
        config.setDayTradeTimeframe(Timeframe.M5);
        config.setDayTradeBarCount(180);
        config.setRsiPeriod(9);
        config.setRsiOversold(32.0);
        config.setRsiOverbought(64.0);
        config.setRsiWeight(0.65);
        config.setFastMovingAveragePeriod(8);
        config.setSlowMovingAveragePeriod(34);
        config.setMovingAverageWeight(0.90);
        config.setBreakoutLookbackBars(36);
        config.setVolumeMultiplier(1.80);
        config.setVolumeBreakoutWeight(0.90);
        config.setRequireBreakoutContinuation(true);
        config.setRequireBreakoutNextBarConfirmation(false);
        config.setRequirePriceAboveVwapForLong(true);
        config.setMaxEntryRiseFromRecentLowPercent(0.02);
        config.setMinimumEntryScore(0.60);
        config.setWeakMarketLongPolicy(WeakMarketLongPolicy.BLOCK_ALL);
        config.setVolumeSustainEnabled(true);
        config.setAtrRiskEnabled(true);
        config.setAtrChaseLimitEnabled(true);
        config.setAtrStopMultiplier(0.90);
        config.setAtrTakeProfitMultiplier(1.40);
        config.setAtrChaseLimitMultiplier(1.50);
        return config;
    }

    public static RadarStrategyConfig createDayTradeStandardTemplate() {
        RadarStrategyConfig config = createBConvergenceTemplate();
        config.setDayTradeTimeframe(Timeframe.M5);
        config.setDayTradeBarCount(220);
        config.setRsiPeriod(9);
        config.setRsiOversold(35.0);
        config.setRsiOverbought(68.0);
        config.setRsiWeight(0.80);
        config.setBreakoutLookbackBars(30);
        config.setVolumeMultiplier(1.60);
        config.setVolumeBreakoutWeight(0.85);
        config.setMinimumEntryScore(0.50);
        config.setWeakMarketLongPolicy(WeakMarketLongPolicy.ALLOW_EXTREME_STRENGTH_ONLY);
        config.setAtrRiskEnabled(true);
        config.setAtrChaseLimitEnabled(true);
        config.setAtrStopMultiplier(1.00);
        config.setAtrTakeProfitMultiplier(1.70);
        config.setAtrChaseLimitMultiplier(1.80);
        return config;
    }

    public static RadarStrategyConfig createDayTradeMomentumTemplate() {
        RadarStrategyConfig config = createBConvergenceTemplate();
        config.setDayTradeTimeframe(Timeframe.M5);
        config.setDayTradeBarCount(220);
        config.setRsiPeriod(7);
        config.setRsiOversold(38.0);
        config.setRsiOverbought(72.0);
        config.setRsiWeight(0.70);
        config.setFastMovingAveragePeriod(5);
        config.setSlowMovingAveragePeriod(20);
        config.setMovingAverageWeight(0.85);
        config.setBreakoutLookbackBars(20);
        config.setVolumeMultiplier(2.00);
        config.setVolumeBreakoutWeight(1.00);
        config.setRequireBreakoutContinuation(true);
        config.setRequireBreakoutNextBarConfirmation(true);
        config.setRequirePriceAboveVwapForLong(true);
        config.setMaxEntryRiseFromRecentLowPercent(0.025);
        config.setMinimumEntryScore(0.55);
        config.setWeakMarketLongPolicy(WeakMarketLongPolicy.BLOCK_ALL);
        config.setVolumeSustainEnabled(true);
        config.setAtrRiskEnabled(true);
        config.setAtrChaseLimitEnabled(true);
        config.setAtrStopMultiplier(1.00);
        config.setAtrTakeProfitMultiplier(2.00);
        config.setAtrChaseLimitMultiplier(1.80);
        return config;
    }

    public RadarStrategyConfig copyForMode(TradeMode mode) {
        RadarStrategyConfig copy = copy();
        switch (mode) {
            case DAY_TRADE -> {
                copy.setDayTradeTimeframe(dayTradeTimeframe);
                copy.setRsiPeriod(rsiPeriod > 0 ? rsiPeriod : 5);
                copy.setRsiOversold(rsiOversold);
                copy.setRsiOverbought(rsiOverbought);
                copy.setFastMovingAveragePeriod(fastMovingAveragePeriod);
                copy.setSlowMovingAveragePeriod(slowMovingAveragePeriod);
                copy.setBreakoutLookbackBars(breakoutLookbackBars);
                copy.setVolumeMultiplier(volumeMultiplier);
            }
            case SHORT_SWING -> {
                copy.setRsiPeriod(rsiPeriod == 5 ? 14 : rsiPeriod);
                copy.setRsiOversold(rsiOversold == 40.0 ? 30.0 : rsiOversold);
                copy.setRsiOverbought(rsiOverbought == 60.0 ? 70.0 : rsiOverbought);
                copy.setFastMovingAveragePeriod(fastMovingAveragePeriod == 5 ? 20 : fastMovingAveragePeriod);
                copy.setSlowMovingAveragePeriod(slowMovingAveragePeriod == 20 ? 60 : slowMovingAveragePeriod);
                copy.setBreakoutLookbackBars(breakoutLookbackBars == 20 ? 40 : breakoutLookbackBars);
                copy.setVolumeMultiplier(volumeMultiplier == 1.8 ? 1.5 : volumeMultiplier);
            }
            case SWING_TRADE -> {
                copy.setRsiPeriod(rsiPeriod == 5 ? 21 : rsiPeriod);
                copy.setRsiOversold(rsiOversold == 40.0 ? 25.0 : rsiOversold);
                copy.setRsiOverbought(rsiOverbought == 60.0 ? 75.0 : rsiOverbought);
                copy.setFastMovingAveragePeriod(fastMovingAveragePeriod == 5 ? 20 : fastMovingAveragePeriod);
                copy.setSlowMovingAveragePeriod(slowMovingAveragePeriod == 20 ? 60 : slowMovingAveragePeriod);
                copy.setBreakoutLookbackBars(breakoutLookbackBars == 20 ? 60 : breakoutLookbackBars);
                copy.setVolumeMultiplier(volumeMultiplier == 1.8 ? 1.3 : volumeMultiplier);
            }
            default -> {
            }
        }
        return copy;
    }

    public Timeframe resolveTimeframe(TradeMode mode) {
        return switch (mode) {
            case DAY_TRADE -> dayTradeTimeframe;
            case SHORT_SWING -> shortSwingTimeframe;
            case SWING_TRADE -> swingTradeTimeframe;
            default -> dayTradeTimeframe;
        };
    }

    public int resolveBarCount(TradeMode mode) {
        return switch (mode) {
            case DAY_TRADE -> dayTradeBarCount;
            case SHORT_SWING -> shortSwingBarCount;
            case SWING_TRADE -> swingTradeBarCount;
            default -> dayTradeBarCount;
        };
    }

    public RadarStrategyConfig copy() {
        RadarStrategyConfig copy = new RadarStrategyConfig();
        copy.rsiEnabled = rsiEnabled;
        copy.rsiPeriod = rsiPeriod;
        copy.rsiOversold = rsiOversold;
        copy.rsiOverbought = rsiOverbought;
        copy.rsiWeight = rsiWeight;
        copy.movingAverageEnabled = movingAverageEnabled;
        copy.movingAverageType = movingAverageType;
        copy.fastMovingAveragePeriod = fastMovingAveragePeriod;
        copy.slowMovingAveragePeriod = slowMovingAveragePeriod;
        copy.movingAverageWeight = movingAverageWeight;
        copy.volumeBreakoutEnabled = volumeBreakoutEnabled;
        copy.breakoutLookbackBars = breakoutLookbackBars;
        copy.volumeMultiplier = volumeMultiplier;
        copy.volumeBreakoutWeight = volumeBreakoutWeight;
        copy.requireRsiEntryConfirmation = requireRsiEntryConfirmation;
        copy.blockBreakoutOnRsiOverbought = blockBreakoutOnRsiOverbought;
        copy.requireBreakoutContinuation = requireBreakoutContinuation;
        copy.requirePriceAboveVwapForLong = requirePriceAboveVwapForLong;
        copy.requireBreakoutNextBarConfirmation = requireBreakoutNextBarConfirmation;
        copy.blockMovingAverageOnlyEntry = blockMovingAverageOnlyEntry;
        copy.maxEntryRiseFromRecentLowPercent = maxEntryRiseFromRecentLowPercent;
        copy.minimumEntryScore = minimumEntryScore;
        copy.marketRegimeFilterEnabled = marketRegimeFilterEnabled;
        copy.weakMarketStrictLongEnabled = weakMarketStrictLongEnabled;
        copy.weakMarketLongPolicy = weakMarketLongPolicy;
        copy.weakOutperformBenchmarkPercent = weakOutperformBenchmarkPercent;
        copy.weakOutperformIndustryPercent = weakOutperformIndustryPercent;
        copy.rangeMarketRequiresVwapAndVolume = rangeMarketRequiresVwapAndVolume;
        copy.internalAllowVwapPassPercent = internalAllowVwapPassPercent;
        copy.internalAllowAverageReturnPercent = internalAllowAverageReturnPercent;
        copy.internalAllowVolumeSustainPercent = internalAllowVolumeSustainPercent;
        copy.internalBlockVwapPassPercent = internalBlockVwapPassPercent;
        copy.internalBlockAverageReturnPercent = internalBlockAverageReturnPercent;
        copy.internalBlockNewLowExcessCount = internalBlockNewLowExcessCount;
        copy.volumeSustainEnabled = volumeSustainEnabled;
        copy.atrRiskEnabled = atrRiskEnabled;
        copy.atrChaseLimitEnabled = atrChaseLimitEnabled;
        copy.atrPeriod = atrPeriod;
        copy.atrStopMultiplier = atrStopMultiplier;
        copy.atrTakeProfitMultiplier = atrTakeProfitMultiplier;
        copy.atrChaseLimitMultiplier = atrChaseLimitMultiplier;
        copy.backtestCrossDayWarmupEnabled = backtestCrossDayWarmupEnabled;
        copy.backtestWarmupBarCount = backtestWarmupBarCount;
        copy.dayTradeTimeframe = dayTradeTimeframe;
        copy.executionConfirmationTimeframe = executionConfirmationTimeframe;
        copy.shortSwingTimeframe = shortSwingTimeframe;
        copy.swingTradeTimeframe = swingTradeTimeframe;
        copy.dayTradeBarCount = dayTradeBarCount;
        copy.shortSwingBarCount = shortSwingBarCount;
        copy.swingTradeBarCount = swingTradeBarCount;
        return copy;
    }

    public boolean isRsiEnabled() { return rsiEnabled; }
    public void setRsiEnabled(boolean rsiEnabled) { this.rsiEnabled = rsiEnabled; }
    public int getRsiPeriod() { return rsiPeriod; }
    public void setRsiPeriod(int rsiPeriod) { this.rsiPeriod = Math.max(2, rsiPeriod); }
    public double getRsiOversold() { return rsiOversold; }
    public void setRsiOversold(double rsiOversold) { this.rsiOversold = clamp(rsiOversold, 1.0, 99.0); }
    public double getRsiOverbought() { return rsiOverbought; }
    public void setRsiOverbought(double rsiOverbought) { this.rsiOverbought = clamp(rsiOverbought, 1.0, 99.0); }
    public double getRsiWeight() { return rsiWeight; }
    public void setRsiWeight(double rsiWeight) { this.rsiWeight = clamp(rsiWeight, 0.0, 1.0); }
    public boolean isMovingAverageEnabled() { return movingAverageEnabled; }
    public void setMovingAverageEnabled(boolean movingAverageEnabled) { this.movingAverageEnabled = movingAverageEnabled; }
    public MovingAverageType getMovingAverageType() { return movingAverageType; }
    public void setMovingAverageType(MovingAverageType movingAverageType) { this.movingAverageType = movingAverageType != null ? movingAverageType : MovingAverageType.EMA; }
    public int getFastMovingAveragePeriod() { return fastMovingAveragePeriod; }
    public void setFastMovingAveragePeriod(int fastMovingAveragePeriod) { this.fastMovingAveragePeriod = Math.max(2, fastMovingAveragePeriod); }
    public int getSlowMovingAveragePeriod() { return slowMovingAveragePeriod; }
    public void setSlowMovingAveragePeriod(int slowMovingAveragePeriod) { this.slowMovingAveragePeriod = Math.max(3, slowMovingAveragePeriod); }
    public double getMovingAverageWeight() { return movingAverageWeight; }
    public void setMovingAverageWeight(double movingAverageWeight) { this.movingAverageWeight = clamp(movingAverageWeight, 0.0, 1.0); }
    public boolean isVolumeBreakoutEnabled() { return volumeBreakoutEnabled; }
    public void setVolumeBreakoutEnabled(boolean volumeBreakoutEnabled) { this.volumeBreakoutEnabled = volumeBreakoutEnabled; }
    public int getBreakoutLookbackBars() { return breakoutLookbackBars; }
    public void setBreakoutLookbackBars(int breakoutLookbackBars) { this.breakoutLookbackBars = Math.max(5, breakoutLookbackBars); }
    public double getVolumeMultiplier() { return volumeMultiplier; }
    public void setVolumeMultiplier(double volumeMultiplier) { this.volumeMultiplier = Math.max(1.0, volumeMultiplier); }
    public double getVolumeBreakoutWeight() { return volumeBreakoutWeight; }
    public void setVolumeBreakoutWeight(double volumeBreakoutWeight) { this.volumeBreakoutWeight = clamp(volumeBreakoutWeight, 0.0, 1.0); }
    public boolean isRequireRsiEntryConfirmation() { return requireRsiEntryConfirmation; }
    public void setRequireRsiEntryConfirmation(boolean requireRsiEntryConfirmation) { this.requireRsiEntryConfirmation = requireRsiEntryConfirmation; }
    public boolean isBlockBreakoutOnRsiOverbought() { return blockBreakoutOnRsiOverbought; }
    public void setBlockBreakoutOnRsiOverbought(boolean blockBreakoutOnRsiOverbought) { this.blockBreakoutOnRsiOverbought = blockBreakoutOnRsiOverbought; }
    public boolean isRequireBreakoutContinuation() { return requireBreakoutContinuation; }
    public void setRequireBreakoutContinuation(boolean requireBreakoutContinuation) { this.requireBreakoutContinuation = requireBreakoutContinuation; }
    public boolean isRequirePriceAboveVwapForLong() { return requirePriceAboveVwapForLong; }
    public void setRequirePriceAboveVwapForLong(boolean requirePriceAboveVwapForLong) { this.requirePriceAboveVwapForLong = requirePriceAboveVwapForLong; }
    public boolean isRequireBreakoutNextBarConfirmation() { return requireBreakoutNextBarConfirmation; }
    public void setRequireBreakoutNextBarConfirmation(boolean requireBreakoutNextBarConfirmation) { this.requireBreakoutNextBarConfirmation = requireBreakoutNextBarConfirmation; }
    public boolean isBlockMovingAverageOnlyEntry() { return blockMovingAverageOnlyEntry; }
    public void setBlockMovingAverageOnlyEntry(boolean blockMovingAverageOnlyEntry) { this.blockMovingAverageOnlyEntry = blockMovingAverageOnlyEntry; }
    public double getMaxEntryRiseFromRecentLowPercent() { return maxEntryRiseFromRecentLowPercent; }
    public void setMaxEntryRiseFromRecentLowPercent(double maxEntryRiseFromRecentLowPercent) { this.maxEntryRiseFromRecentLowPercent = clamp(maxEntryRiseFromRecentLowPercent, 0.0, 1.0); }
    public double getMinimumEntryScore() { return minimumEntryScore; }
    public void setMinimumEntryScore(double minimumEntryScore) { this.minimumEntryScore = clamp(minimumEntryScore, 0.0, 1.0); }
    public boolean isMarketRegimeFilterEnabled() { return marketRegimeFilterEnabled; }
    public void setMarketRegimeFilterEnabled(boolean marketRegimeFilterEnabled) { this.marketRegimeFilterEnabled = marketRegimeFilterEnabled; }
    public boolean isWeakMarketStrictLongEnabled() { return weakMarketStrictLongEnabled; }
    public void setWeakMarketStrictLongEnabled(boolean weakMarketStrictLongEnabled) { this.weakMarketStrictLongEnabled = weakMarketStrictLongEnabled; }
    public WeakMarketLongPolicy getWeakMarketLongPolicy() { return weakMarketLongPolicy; }
    public void setWeakMarketLongPolicy(WeakMarketLongPolicy weakMarketLongPolicy) { this.weakMarketLongPolicy = weakMarketLongPolicy != null ? weakMarketLongPolicy : WeakMarketLongPolicy.BLOCK_ALL; }
    public double getWeakOutperformBenchmarkPercent() { return weakOutperformBenchmarkPercent; }
    public void setWeakOutperformBenchmarkPercent(double weakOutperformBenchmarkPercent) { this.weakOutperformBenchmarkPercent = clamp(weakOutperformBenchmarkPercent, 0.0, 10.0); }
    public double getWeakOutperformIndustryPercent() { return weakOutperformIndustryPercent; }
    public void setWeakOutperformIndustryPercent(double weakOutperformIndustryPercent) { this.weakOutperformIndustryPercent = clamp(weakOutperformIndustryPercent, 0.0, 10.0); }
    public boolean isRangeMarketRequiresVwapAndVolume() { return rangeMarketRequiresVwapAndVolume; }
    public void setRangeMarketRequiresVwapAndVolume(boolean rangeMarketRequiresVwapAndVolume) { this.rangeMarketRequiresVwapAndVolume = rangeMarketRequiresVwapAndVolume; }
    public double getInternalAllowVwapPassPercent() { return internalAllowVwapPassPercent; }
    public void setInternalAllowVwapPassPercent(double internalAllowVwapPassPercent) { this.internalAllowVwapPassPercent = clamp(internalAllowVwapPassPercent, 0.0, 100.0); }
    public double getInternalAllowAverageReturnPercent() { return internalAllowAverageReturnPercent; }
    public void setInternalAllowAverageReturnPercent(double internalAllowAverageReturnPercent) { this.internalAllowAverageReturnPercent = clamp(internalAllowAverageReturnPercent, -10.0, 10.0); }
    public double getInternalAllowVolumeSustainPercent() { return internalAllowVolumeSustainPercent; }
    public void setInternalAllowVolumeSustainPercent(double internalAllowVolumeSustainPercent) { this.internalAllowVolumeSustainPercent = clamp(internalAllowVolumeSustainPercent, 0.0, 100.0); }
    public double getInternalBlockVwapPassPercent() { return internalBlockVwapPassPercent; }
    public void setInternalBlockVwapPassPercent(double internalBlockVwapPassPercent) { this.internalBlockVwapPassPercent = clamp(internalBlockVwapPassPercent, 0.0, 100.0); }
    public double getInternalBlockAverageReturnPercent() { return internalBlockAverageReturnPercent; }
    public void setInternalBlockAverageReturnPercent(double internalBlockAverageReturnPercent) { this.internalBlockAverageReturnPercent = clamp(internalBlockAverageReturnPercent, -10.0, 10.0); }
    public int getInternalBlockNewLowExcessCount() { return internalBlockNewLowExcessCount; }
    public void setInternalBlockNewLowExcessCount(int internalBlockNewLowExcessCount) { this.internalBlockNewLowExcessCount = Math.max(0, internalBlockNewLowExcessCount); }
    public boolean isVolumeSustainEnabled() { return volumeSustainEnabled; }
    public void setVolumeSustainEnabled(boolean volumeSustainEnabled) { this.volumeSustainEnabled = volumeSustainEnabled; }
    public boolean isAtrRiskEnabled() { return atrRiskEnabled; }
    public void setAtrRiskEnabled(boolean atrRiskEnabled) { this.atrRiskEnabled = atrRiskEnabled; }
    public boolean isAtrChaseLimitEnabled() { return atrChaseLimitEnabled; }
    public void setAtrChaseLimitEnabled(boolean atrChaseLimitEnabled) { this.atrChaseLimitEnabled = atrChaseLimitEnabled; }
    public int getAtrPeriod() { return atrPeriod; }
    public void setAtrPeriod(int atrPeriod) { this.atrPeriod = Math.max(3, atrPeriod); }
    public double getAtrStopMultiplier() { return atrStopMultiplier; }
    public void setAtrStopMultiplier(double atrStopMultiplier) { this.atrStopMultiplier = Math.max(0.1, atrStopMultiplier); }
    public double getAtrTakeProfitMultiplier() { return atrTakeProfitMultiplier; }
    public void setAtrTakeProfitMultiplier(double atrTakeProfitMultiplier) { this.atrTakeProfitMultiplier = Math.max(0.1, atrTakeProfitMultiplier); }
    public double getAtrChaseLimitMultiplier() { return atrChaseLimitMultiplier; }
    public void setAtrChaseLimitMultiplier(double atrChaseLimitMultiplier) { this.atrChaseLimitMultiplier = Math.max(0.1, atrChaseLimitMultiplier); }
    public boolean isBacktestCrossDayWarmupEnabled() { return backtestCrossDayWarmupEnabled; }
    public void setBacktestCrossDayWarmupEnabled(boolean backtestCrossDayWarmupEnabled) { this.backtestCrossDayWarmupEnabled = backtestCrossDayWarmupEnabled; }
    public int getBacktestWarmupBarCount() { return backtestWarmupBarCount; }
    public void setBacktestWarmupBarCount(int backtestWarmupBarCount) { this.backtestWarmupBarCount = Math.max(0, backtestWarmupBarCount); }
    public Timeframe getDayTradeTimeframe() { return dayTradeTimeframe; }
    public void setDayTradeTimeframe(Timeframe dayTradeTimeframe) { this.dayTradeTimeframe = dayTradeTimeframe != null ? dayTradeTimeframe : Timeframe.M1; }
    public Timeframe getExecutionConfirmationTimeframe() { return executionConfirmationTimeframe; }
    public void setExecutionConfirmationTimeframe(Timeframe executionConfirmationTimeframe) {
        this.executionConfirmationTimeframe = executionConfirmationTimeframe != null ? executionConfirmationTimeframe : Timeframe.M1;
    }
    public Timeframe getShortSwingTimeframe() { return shortSwingTimeframe; }
    public void setShortSwingTimeframe(Timeframe shortSwingTimeframe) { this.shortSwingTimeframe = shortSwingTimeframe != null ? shortSwingTimeframe : Timeframe.M15; }
    public Timeframe getSwingTradeTimeframe() { return swingTradeTimeframe; }
    public void setSwingTradeTimeframe(Timeframe swingTradeTimeframe) { this.swingTradeTimeframe = swingTradeTimeframe != null ? swingTradeTimeframe : Timeframe.H1; }
    public int getDayTradeBarCount() { return dayTradeBarCount; }
    public void setDayTradeBarCount(int dayTradeBarCount) { this.dayTradeBarCount = Math.max(60, dayTradeBarCount); }
    public int getShortSwingBarCount() { return shortSwingBarCount; }
    public void setShortSwingBarCount(int shortSwingBarCount) { this.shortSwingBarCount = Math.max(80, shortSwingBarCount); }
    public int getSwingTradeBarCount() { return swingTradeBarCount; }
    public void setSwingTradeBarCount(int swingTradeBarCount) { this.swingTradeBarCount = Math.max(100, swingTradeBarCount); }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
