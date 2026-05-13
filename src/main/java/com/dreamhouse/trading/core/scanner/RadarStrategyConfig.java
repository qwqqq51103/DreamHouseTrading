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
    private double maxEntryRiseFromRecentLowPercent = 0.0;
    private double minimumEntryScore = 0.50;

    private Timeframe dayTradeTimeframe = Timeframe.M1;
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
        config.setDayTradeTimeframe(Timeframe.M1);
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
        config.setRequirePriceAboveVwapForLong(false);
        config.setRequireBreakoutNextBarConfirmation(false);
        config.setMaxEntryRiseFromRecentLowPercent(0.03);
        config.setMinimumEntryScore(0.45);
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
        copy.maxEntryRiseFromRecentLowPercent = maxEntryRiseFromRecentLowPercent;
        copy.minimumEntryScore = minimumEntryScore;
        copy.dayTradeTimeframe = dayTradeTimeframe;
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
    public double getMaxEntryRiseFromRecentLowPercent() { return maxEntryRiseFromRecentLowPercent; }
    public void setMaxEntryRiseFromRecentLowPercent(double maxEntryRiseFromRecentLowPercent) { this.maxEntryRiseFromRecentLowPercent = clamp(maxEntryRiseFromRecentLowPercent, 0.0, 1.0); }
    public double getMinimumEntryScore() { return minimumEntryScore; }
    public void setMinimumEntryScore(double minimumEntryScore) { this.minimumEntryScore = clamp(minimumEntryScore, 0.0, 1.0); }
    public Timeframe getDayTradeTimeframe() { return dayTradeTimeframe; }
    public void setDayTradeTimeframe(Timeframe dayTradeTimeframe) { this.dayTradeTimeframe = dayTradeTimeframe != null ? dayTradeTimeframe : Timeframe.M1; }
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
