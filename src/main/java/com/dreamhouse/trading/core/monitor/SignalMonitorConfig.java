package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

/**
 * 信號監控配置
 */
public class SignalMonitorConfig {

    // 掃描間隔（秒）
    private int scanIntervalSeconds = 10;

    // 時間週期
    private Timeframe timeframe = Timeframe.M5;

    // 最小信號間隔（分鐘）- 避免重複提醒
    private int minSignalIntervalMinutes = 5;

    // K 線數量（用於技術分析）
    private int barCount = 100;

    // 掃描交易模式
    private TradeMode tradeMode = TradeMode.DAY_TRADE;

    // 策略參數（可選）
    private Integer rsiPeriod;
    private Double rsiOversold;
    private Double rsiOverbought;

    /**
     * 創建預設配置
     */
    public static SignalMonitorConfig createDefault() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(10);
        config.setTimeframe(Timeframe.M5);
        config.setMinSignalIntervalMinutes(5);
        return config;
    }

    /**
     * 創建快速配置（更頻繁的掃描）
     */
    public static SignalMonitorConfig createFast() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(5);
        config.setTimeframe(Timeframe.M1);
        config.setMinSignalIntervalMinutes(3);
        return config;
    }

    /**
     * 創建慢速配置（較少的掃描）
     */
    public static SignalMonitorConfig createSlow() {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(30);
        config.setTimeframe(Timeframe.M15);
        config.setMinSignalIntervalMinutes(10);
        return config;
    }

    // Getters and Setters

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public void setScanIntervalSeconds(int scanIntervalSeconds) {
        this.scanIntervalSeconds = scanIntervalSeconds;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    public int getMinSignalIntervalMinutes() {
        return minSignalIntervalMinutes;
    }

    public void setMinSignalIntervalMinutes(int minSignalIntervalMinutes) {
        this.minSignalIntervalMinutes = minSignalIntervalMinutes;
    }

    public int getBarCount() {
        return barCount;
    }

    public void setBarCount(int barCount) {
        this.barCount = barCount;
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public void setTradeMode(TradeMode tradeMode) {
        this.tradeMode = tradeMode != null ? tradeMode : TradeMode.NO_TRADE;
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
