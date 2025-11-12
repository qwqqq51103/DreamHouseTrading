package com.dreamhouse.trading.core.decision.intraday;

/**
 * 盤中分析結果
 * 包含流動性、波動度、時段等分析資訊
 */
public class IntradayAnalysis {

    private final LiquidityLevel liquidityLevel;
    private final double averageVolume;
    private final double spreadPercent;
    private final double atrPercent;
    private final boolean isActiveTime;
    private final boolean suitableForDayTrade;
    private final boolean suitableForShortSwing;
    private final boolean suitableForSwingTrade;
    private final String analysis;

    /**
     * 建構子（使用 Builder 模式）
     */
    private IntradayAnalysis(Builder builder) {
        this.liquidityLevel = builder.liquidityLevel;
        this.averageVolume = builder.averageVolume;
        this.spreadPercent = builder.spreadPercent;
        this.atrPercent = builder.atrPercent;
        this.isActiveTime = builder.isActiveTime;
        this.suitableForDayTrade = builder.suitableForDayTrade;
        this.suitableForShortSwing = builder.suitableForShortSwing;
        this.suitableForSwingTrade = builder.suitableForSwingTrade;
        this.analysis = builder.analysis;
    }

    // Getters

    public LiquidityLevel getLiquidityLevel() {
        return liquidityLevel;
    }

    public double getAverageVolume() {
        return averageVolume;
    }

    public double getSpreadPercent() {
        return spreadPercent;
    }

    public double getAtrPercent() {
        return atrPercent;
    }

    public boolean isActiveTime() {
        return isActiveTime;
    }

    public boolean isSuitableForDayTrade() {
        return suitableForDayTrade;
    }

    public boolean isSuitableForShortSwing() {
        return suitableForShortSwing;
    }

    public boolean isSuitableForSwingTrade() {
        return suitableForSwingTrade;
    }

    public String getAnalysis() {
        return analysis;
    }

    @Override
    public String toString() {
        return String.format("IntradayAnalysis[liquidity=%s, volume=%.0f, spread=%.2f%%, ATR=%.2f%%, activeTime=%s]",
                liquidityLevel.getDisplayName(), averageVolume, spreadPercent * 100,
                atrPercent * 100, isActiveTime);
    }

    /**
     * Builder 類別
     */
    public static class Builder {
        private LiquidityLevel liquidityLevel = LiquidityLevel.MEDIUM;
        private double averageVolume = 0.0;
        private double spreadPercent = 0.0;
        private double atrPercent = 0.0;
        private boolean isActiveTime = true;
        private boolean suitableForDayTrade = false;
        private boolean suitableForShortSwing = false;
        private boolean suitableForSwingTrade = false;
        private String analysis = "";

        public Builder liquidityLevel(LiquidityLevel liquidityLevel) {
            this.liquidityLevel = liquidityLevel;
            return this;
        }

        public Builder averageVolume(double averageVolume) {
            this.averageVolume = averageVolume;
            return this;
        }

        public Builder spreadPercent(double spreadPercent) {
            this.spreadPercent = spreadPercent;
            return this;
        }

        public Builder atrPercent(double atrPercent) {
            this.atrPercent = atrPercent;
            return this;
        }

        public Builder isActiveTime(boolean isActiveTime) {
            this.isActiveTime = isActiveTime;
            return this;
        }

        public Builder suitableForDayTrade(boolean suitableForDayTrade) {
            this.suitableForDayTrade = suitableForDayTrade;
            return this;
        }

        public Builder suitableForShortSwing(boolean suitableForShortSwing) {
            this.suitableForShortSwing = suitableForShortSwing;
            return this;
        }

        public Builder suitableForSwingTrade(boolean suitableForSwingTrade) {
            this.suitableForSwingTrade = suitableForSwingTrade;
            return this;
        }

        public Builder analysis(String analysis) {
            this.analysis = analysis;
            return this;
        }

        public IntradayAnalysis build() {
            return new IntradayAnalysis(this);
        }
    }
}
