package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;

import java.time.LocalTime;

/**
 * 信號監控配置。
 */
public class SignalMonitorConfig {

    private int scanIntervalSeconds = 10;
    private Timeframe timeframe = Timeframe.M5;
    private int minSignalIntervalMinutes = 5;
    private int barCount = 160;
    private TradeMode tradeMode = TradeMode.DAY_TRADE;
    private boolean batchScanMode = true;
    private RadarStrategyConfig radarStrategyConfig = RadarStrategyConfig.createDefault();
    private boolean earlyEntryBlockEnabled = true;
    private LocalTime earlyEntryBlockStart = LocalTime.of(9, 0);
    private LocalTime earlyEntryBlockEnd = LocalTime.of(9, 15);
    private boolean stopLossCooldownEnabled = true;
    private int stopLossCooldownMinutes = 60;
    private LocalTime latestAutoEntryTime = LocalTime.of(13, 5);
    private double dailyMaxLoss = -3_000.0;
    private int dailyMaxStopLossCount = 3;
    private int consecutiveLossLimit = 2;
    private boolean disableTradingAfterLossLimit = true;
    private int dailyMaxAutoTrades = 5;
    private int entryPacingMinutes = 5;
    private int postExitCooldownMinutes = 30;
    private boolean oneEntryPerFiveMinuteBar = true;
    private boolean rangeFailureExitEnabled = true;
    private int rangeFailureExitMinutes = 30;
    private double rangeFailureMinR = 0.5;
    private boolean rangeFailureVolumeSustainExitEnabled = true;
    private Integer rsiPeriod;
    private Double rsiOversold;
    private Double rsiOverbought;

    public static SignalMonitorConfig createDefault() {
        return new SignalMonitorConfig();
    }

    public static SignalMonitorConfig createAggressiveTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(8);
        config.setTimeframe(Timeframe.M1);
        config.setMinSignalIntervalMinutes(2);
        config.setBarCount(180);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setRadarStrategyConfig(RadarStrategyConfig.createAggressiveTemplate());
        return config;
    }

    public static SignalMonitorConfig createSimulationTestTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(5);
        config.setTimeframe(Timeframe.M1);
        config.setMinSignalIntervalMinutes(1);
        config.setBarCount(220);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setRadarStrategyConfig(RadarStrategyConfig.createSimulationTestTemplate());
        return config;
    }

    public static SignalMonitorConfig createBalancedTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(15);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(5);
        config.setBarCount(160);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setRadarStrategyConfig(RadarStrategyConfig.createBalancedTemplate());
        return config;
    }

    public static SignalMonitorConfig createBConvergenceTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(10);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(10);
        config.setBarCount(220);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setEarlyEntryBlockEnabled(true);
        config.setEarlyEntryBlockStart(LocalTime.of(9, 0));
        config.setEarlyEntryBlockEnd(LocalTime.of(9, 15));
        config.setStopLossCooldownEnabled(true);
        config.setStopLossCooldownMinutes(60);
        config.setDailyMaxAutoTrades(5);
        config.setEntryPacingMinutes(5);
        config.setPostExitCooldownMinutes(30);
        config.setOneEntryPerFiveMinuteBar(true);
        config.setRangeFailureExitEnabled(true);
        config.setRangeFailureExitMinutes(30);
        config.setRangeFailureMinR(0.5);
        config.setRangeFailureVolumeSustainExitEnabled(true);
        config.setLatestAutoEntryTime(LocalTime.of(13, 5));
        config.setRadarStrategyConfig(RadarStrategyConfig.createBConvergenceTemplate());
        return config;
    }

    public static SignalMonitorConfig createDayTradeDefensiveTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(15);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(10);
        config.setBarCount(180);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setEarlyEntryBlockEnabled(true);
        config.setEarlyEntryBlockStart(LocalTime.of(9, 0));
        config.setEarlyEntryBlockEnd(LocalTime.of(9, 20));
        config.setStopLossCooldownEnabled(true);
        config.setStopLossCooldownMinutes(90);
        config.setDailyMaxLoss(-2_000.0);
        config.setDailyMaxStopLossCount(2);
        config.setConsecutiveLossLimit(2);
        config.setDailyMaxAutoTrades(3);
        config.setEntryPacingMinutes(10);
        config.setPostExitCooldownMinutes(45);
        config.setOneEntryPerFiveMinuteBar(true);
        config.setRangeFailureExitEnabled(true);
        config.setRangeFailureExitMinutes(25);
        config.setRangeFailureMinR(0.5);
        config.setRangeFailureVolumeSustainExitEnabled(true);
        config.setLatestAutoEntryTime(LocalTime.of(12, 50));
        config.setRadarStrategyConfig(RadarStrategyConfig.createDayTradeDefensiveTemplate());
        return config;
    }

    public static SignalMonitorConfig createDayTradeStandardTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(10);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(5);
        config.setBarCount(220);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setEarlyEntryBlockEnabled(true);
        config.setEarlyEntryBlockStart(LocalTime.of(9, 0));
        config.setEarlyEntryBlockEnd(LocalTime.of(9, 15));
        config.setStopLossCooldownEnabled(true);
        config.setStopLossCooldownMinutes(60);
        config.setDailyMaxLoss(-3_000.0);
        config.setDailyMaxStopLossCount(3);
        config.setConsecutiveLossLimit(2);
        config.setDailyMaxAutoTrades(5);
        config.setEntryPacingMinutes(5);
        config.setPostExitCooldownMinutes(30);
        config.setOneEntryPerFiveMinuteBar(true);
        config.setRangeFailureExitEnabled(true);
        config.setRangeFailureExitMinutes(30);
        config.setRangeFailureMinR(0.5);
        config.setRangeFailureVolumeSustainExitEnabled(true);
        config.setLatestAutoEntryTime(LocalTime.of(13, 5));
        config.setRadarStrategyConfig(RadarStrategyConfig.createDayTradeStandardTemplate());
        return config;
    }

    public static SignalMonitorConfig createDayTradeMomentumTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(8);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(5);
        config.setBarCount(220);
        config.setTradeMode(TradeMode.DAY_TRADE);
        config.setEarlyEntryBlockEnabled(true);
        config.setEarlyEntryBlockStart(LocalTime.of(9, 0));
        config.setEarlyEntryBlockEnd(LocalTime.of(9, 10));
        config.setStopLossCooldownEnabled(true);
        config.setStopLossCooldownMinutes(60);
        config.setDailyMaxLoss(-3_000.0);
        config.setDailyMaxStopLossCount(3);
        config.setConsecutiveLossLimit(2);
        config.setDailyMaxAutoTrades(5);
        config.setEntryPacingMinutes(5);
        config.setPostExitCooldownMinutes(30);
        config.setOneEntryPerFiveMinuteBar(true);
        config.setRangeFailureExitEnabled(true);
        config.setRangeFailureExitMinutes(20);
        config.setRangeFailureMinR(0.5);
        config.setRangeFailureVolumeSustainExitEnabled(true);
        config.setLatestAutoEntryTime(LocalTime.of(13, 0));
        config.setRadarStrategyConfig(RadarStrategyConfig.createDayTradeMomentumTemplate());
        return config;
    }

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public void setScanIntervalSeconds(int scanIntervalSeconds) {
        this.scanIntervalSeconds = Math.max(3, scanIntervalSeconds);
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe != null ? timeframe : Timeframe.M5;
    }

    public int getMinSignalIntervalMinutes() {
        return minSignalIntervalMinutes;
    }

    public void setMinSignalIntervalMinutes(int minSignalIntervalMinutes) {
        this.minSignalIntervalMinutes = Math.max(1, minSignalIntervalMinutes);
    }

    public int getBarCount() {
        return barCount;
    }

    public void setBarCount(int barCount) {
        this.barCount = Math.max(20, barCount);
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public void setTradeMode(TradeMode tradeMode) {
        this.tradeMode = tradeMode != null ? tradeMode : TradeMode.DAY_TRADE;
    }

    public boolean isBatchScanMode() {
        return batchScanMode;
    }

    public void setBatchScanMode(boolean batchScanMode) {
        this.batchScanMode = batchScanMode;
    }

    public RadarStrategyConfig getRadarStrategyConfig() {
        return radarStrategyConfig;
    }

    public void setRadarStrategyConfig(RadarStrategyConfig radarStrategyConfig) {
        this.radarStrategyConfig = radarStrategyConfig != null ? radarStrategyConfig : RadarStrategyConfig.createDefault();
    }

    public boolean isEarlyEntryBlockEnabled() {
        return earlyEntryBlockEnabled;
    }

    public void setEarlyEntryBlockEnabled(boolean earlyEntryBlockEnabled) {
        this.earlyEntryBlockEnabled = earlyEntryBlockEnabled;
    }

    public LocalTime getEarlyEntryBlockStart() {
        return earlyEntryBlockStart;
    }

    public void setEarlyEntryBlockStart(LocalTime earlyEntryBlockStart) {
        this.earlyEntryBlockStart = earlyEntryBlockStart != null ? earlyEntryBlockStart : LocalTime.of(9, 0);
    }

    public LocalTime getEarlyEntryBlockEnd() {
        return earlyEntryBlockEnd;
    }

    public void setEarlyEntryBlockEnd(LocalTime earlyEntryBlockEnd) {
        this.earlyEntryBlockEnd = earlyEntryBlockEnd != null ? earlyEntryBlockEnd : LocalTime.of(9, 15);
    }

    public boolean isStopLossCooldownEnabled() {
        return stopLossCooldownEnabled;
    }

    public void setStopLossCooldownEnabled(boolean stopLossCooldownEnabled) {
        this.stopLossCooldownEnabled = stopLossCooldownEnabled;
    }

    public int getStopLossCooldownMinutes() {
        return stopLossCooldownMinutes;
    }

    public void setStopLossCooldownMinutes(int stopLossCooldownMinutes) {
        this.stopLossCooldownMinutes = Math.max(1, stopLossCooldownMinutes);
    }

    public LocalTime getLatestAutoEntryTime() {
        return latestAutoEntryTime;
    }

    public void setLatestAutoEntryTime(LocalTime latestAutoEntryTime) {
        this.latestAutoEntryTime = latestAutoEntryTime != null ? latestAutoEntryTime : LocalTime.of(13, 5);
    }

    public double getDailyMaxLoss() {
        return dailyMaxLoss;
    }

    public void setDailyMaxLoss(double dailyMaxLoss) {
        this.dailyMaxLoss = Math.min(0.0, dailyMaxLoss);
    }

    public int getDailyMaxStopLossCount() {
        return dailyMaxStopLossCount;
    }

    public void setDailyMaxStopLossCount(int dailyMaxStopLossCount) {
        this.dailyMaxStopLossCount = Math.max(0, dailyMaxStopLossCount);
    }

    public int getConsecutiveLossLimit() {
        return consecutiveLossLimit;
    }

    public void setConsecutiveLossLimit(int consecutiveLossLimit) {
        this.consecutiveLossLimit = Math.max(0, consecutiveLossLimit);
    }

    public boolean isDisableTradingAfterLossLimit() {
        return disableTradingAfterLossLimit;
    }

    public void setDisableTradingAfterLossLimit(boolean disableTradingAfterLossLimit) {
        this.disableTradingAfterLossLimit = disableTradingAfterLossLimit;
    }

    public int getDailyMaxAutoTrades() {
        return dailyMaxAutoTrades;
    }

    public void setDailyMaxAutoTrades(int dailyMaxAutoTrades) {
        this.dailyMaxAutoTrades = Math.max(1, dailyMaxAutoTrades);
    }

    public int getEntryPacingMinutes() {
        return entryPacingMinutes;
    }

    public void setEntryPacingMinutes(int entryPacingMinutes) {
        this.entryPacingMinutes = Math.max(0, entryPacingMinutes);
    }

    public int getPostExitCooldownMinutes() {
        return postExitCooldownMinutes;
    }

    public void setPostExitCooldownMinutes(int postExitCooldownMinutes) {
        this.postExitCooldownMinutes = Math.max(0, postExitCooldownMinutes);
    }

    public boolean isOneEntryPerFiveMinuteBar() {
        return oneEntryPerFiveMinuteBar;
    }

    public void setOneEntryPerFiveMinuteBar(boolean oneEntryPerFiveMinuteBar) {
        this.oneEntryPerFiveMinuteBar = oneEntryPerFiveMinuteBar;
    }

    public boolean isRangeFailureExitEnabled() {
        return rangeFailureExitEnabled;
    }

    public void setRangeFailureExitEnabled(boolean rangeFailureExitEnabled) {
        this.rangeFailureExitEnabled = rangeFailureExitEnabled;
    }

    public int getRangeFailureExitMinutes() {
        return rangeFailureExitMinutes;
    }

    public void setRangeFailureExitMinutes(int rangeFailureExitMinutes) {
        this.rangeFailureExitMinutes = Math.max(1, rangeFailureExitMinutes);
    }

    public double getRangeFailureMinR() {
        return rangeFailureMinR;
    }

    public void setRangeFailureMinR(double rangeFailureMinR) {
        this.rangeFailureMinR = Math.max(0.0, rangeFailureMinR);
    }

    public boolean isRangeFailureVolumeSustainExitEnabled() {
        return rangeFailureVolumeSustainExitEnabled;
    }

    public void setRangeFailureVolumeSustainExitEnabled(boolean rangeFailureVolumeSustainExitEnabled) {
        this.rangeFailureVolumeSustainExitEnabled = rangeFailureVolumeSustainExitEnabled;
    }

    public Integer getRsiPeriod() {
        return rsiPeriod;
    }

    public void setRsiPeriod(Integer rsiPeriod) {
        this.rsiPeriod = rsiPeriod;
    }

    public Double getRsiOversold() {
        return rsiOversold;
    }

    public void setRsiOversold(Double rsiOversold) {
        this.rsiOversold = rsiOversold;
    }

    public Double getRsiOverbought() {
        return rsiOverbought;
    }

    public void setRsiOverbought(Double rsiOverbought) {
        this.rsiOverbought = rsiOverbought;
    }
}
