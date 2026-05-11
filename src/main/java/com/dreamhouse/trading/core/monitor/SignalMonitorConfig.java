package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

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
        return config;
    }

    public static SignalMonitorConfig createSimulationTestTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(5);
        config.setTimeframe(Timeframe.M1);
        config.setMinSignalIntervalMinutes(1);
        config.setBarCount(220);
        config.setTradeMode(TradeMode.DAY_TRADE);
        return config;
    }

    public static SignalMonitorConfig createBalancedTemplate() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(15);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(5);
        config.setBarCount(160);
        config.setTradeMode(TradeMode.DAY_TRADE);
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
