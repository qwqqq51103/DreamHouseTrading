package com.dreamhouse.trading.core.decision.voting;

/**
 * 投票引擎配置
 * 定義多策略投票的閾值與規則
 */
public class VotingConfig {

    /**
     * 做多進場閾值（加權分數）
     * 當多頭分數 >= 此值時，觸發做多信號
     */
    private double longEntryThreshold = 0.6;

    /**
     * 做空進場閾值（加權分數）
     * 當空頭分數 >= 此值時，觸發做空信號
     */
    private double shortEntryThreshold = 0.6;

    /**
     * 出場閾值（加權分數）
     * 當出場分數 >= 此值時，觸發平倉信號
     */
    private double exitThreshold = 0.5;

    /**
     * 反手閾值（加權分數）
     * 當反手分數 >= 此值時，觸發反手信號（選用）
     */
    private double reverseThreshold = 0.8;

    /**
     * 是否啟用反手功能
     */
    private boolean reverseEnabled = false;

    /**
     * 最小投票策略數量
     * 至少需要多少個策略參與投票才能產生有效信號
     */
    private int minVotingStrategies = 2;

    /**
     * 是否要求策略一致性
     * true: 所有參與投票的策略必須方向一致
     * false: 允許策略方向不一致，以加權分數決定
     */
    private boolean requireConsensus = false;

    /**
     * 信號有效時間（毫秒）
     * 超過此時間的信號將被視為過時而忽略
     * 預設 5 分鐘 = 300,000 毫秒
     */
    private long signalValidityMs = 300_000;

    // Getters and Setters

    public double getLongEntryThreshold() {
        return longEntryThreshold;
    }

    public void setLongEntryThreshold(double longEntryThreshold) {
        if (longEntryThreshold < 0.0 || longEntryThreshold > 1.0) {
            throw new IllegalArgumentException("Long entry threshold must be between 0.0 and 1.0");
        }
        this.longEntryThreshold = longEntryThreshold;
    }

    public double getShortEntryThreshold() {
        return shortEntryThreshold;
    }

    public void setShortEntryThreshold(double shortEntryThreshold) {
        if (shortEntryThreshold < 0.0 || shortEntryThreshold > 1.0) {
            throw new IllegalArgumentException("Short entry threshold must be between 0.0 and 1.0");
        }
        this.shortEntryThreshold = shortEntryThreshold;
    }

    public double getExitThreshold() {
        return exitThreshold;
    }

    public void setExitThreshold(double exitThreshold) {
        if (exitThreshold < 0.0 || exitThreshold > 1.0) {
            throw new IllegalArgumentException("Exit threshold must be between 0.0 and 1.0");
        }
        this.exitThreshold = exitThreshold;
    }

    public double getReverseThreshold() {
        return reverseThreshold;
    }

    public void setReverseThreshold(double reverseThreshold) {
        if (reverseThreshold < 0.0 || reverseThreshold > 1.0) {
            throw new IllegalArgumentException("Reverse threshold must be between 0.0 and 1.0");
        }
        this.reverseThreshold = reverseThreshold;
    }

    public boolean isReverseEnabled() {
        return reverseEnabled;
    }

    public void setReverseEnabled(boolean reverseEnabled) {
        this.reverseEnabled = reverseEnabled;
    }

    public int getMinVotingStrategies() {
        return minVotingStrategies;
    }

    public void setMinVotingStrategies(int minVotingStrategies) {
        if (minVotingStrategies < 1) {
            throw new IllegalArgumentException("Minimum voting strategies must be at least 1");
        }
        this.minVotingStrategies = minVotingStrategies;
    }

    public boolean isRequireConsensus() {
        return requireConsensus;
    }

    public void setRequireConsensus(boolean requireConsensus) {
        this.requireConsensus = requireConsensus;
    }

    public long getSignalValidityMs() {
        return signalValidityMs;
    }

    public void setSignalValidityMs(long signalValidityMs) {
        if (signalValidityMs <= 0) {
            throw new IllegalArgumentException("Signal validity must be positive");
        }
        this.signalValidityMs = signalValidityMs;
    }

    @Override
    public String toString() {
        return String.format("VotingConfig[longEntry=%.2f, shortEntry=%.2f, exit=%.2f, reverse=%.2f, minStrategies=%d]",
                longEntryThreshold, shortEntryThreshold, exitThreshold, reverseThreshold, minVotingStrategies);
    }
}
