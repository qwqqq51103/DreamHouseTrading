package com.dreamhouse.trading.core.decision.regime;

/**
 * 市場環境分析結果
 * 由 MarketRegimeDetector 產生，提供給決策引擎使用
 */
public class RegimeAnalysis {

    private final MarketRegime marketRegime;
    private final AllowedSide allowedSide;
    private final double confidence;           // 信心度 0.0 ~ 1.0
    private final double trendStrength;        // 趨勢強度（ADX值）
    private final double volatility;           // 波動率
    private final double maxRiskLevel;         // 建議的最大風險等級 0.0 ~ 1.0
    private final long timestamp;
    private final String analysis;             // 分析說明

    private RegimeAnalysis(Builder builder) {
        this.marketRegime = builder.marketRegime;
        this.allowedSide = builder.allowedSide;
        this.confidence = builder.confidence;
        this.trendStrength = builder.trendStrength;
        this.volatility = builder.volatility;
        this.maxRiskLevel = builder.maxRiskLevel;
        this.timestamp = builder.timestamp;
        this.analysis = builder.analysis;
    }

    public MarketRegime getMarketRegime() {
        return marketRegime;
    }

    public AllowedSide getAllowedSide() {
        return allowedSide;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getTrendStrength() {
        return trendStrength;
    }

    public double getVolatility() {
        return volatility;
    }

    public double getMaxRiskLevel() {
        return maxRiskLevel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAnalysis() {
        return analysis;
    }

    /**
     * 判斷是否允許交易
     */
    public boolean isTradeable() {
        return marketRegime.isTradeable() && allowedSide.allowsAnyTrade();
    }

    /**
     * 判斷是否為強趨勢環境
     */
    public boolean isStrongTrend() {
        return trendStrength >= 40.0;  // ADX >= 40
    }

    @Override
    public String toString() {
        return String.format("[週線環境] %s | 允許方向: %s | ADX:%.1f | 波動率:%.2f | 風險等級:%.2f | 信心度:%.2f - %s",
                marketRegime.getDisplayName(),
                allowedSide.getDisplayName(),
                trendStrength,
                volatility,
                maxRiskLevel,
                confidence,
                analysis);
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private MarketRegime marketRegime = MarketRegime.NEUTRAL;
        private AllowedSide allowedSide = AllowedSide.BOTH;
        private double confidence = 0.5;
        private double trendStrength = 0.0;
        private double volatility = 0.0;
        private double maxRiskLevel = 0.5;
        private long timestamp = System.currentTimeMillis();
        private String analysis = "";

        public Builder marketRegime(MarketRegime marketRegime) {
            this.marketRegime = marketRegime;
            return this;
        }

        public Builder allowedSide(AllowedSide allowedSide) {
            this.allowedSide = allowedSide;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder trendStrength(double trendStrength) {
            this.trendStrength = trendStrength;
            return this;
        }

        public Builder volatility(double volatility) {
            this.volatility = volatility;
            return this;
        }

        public Builder maxRiskLevel(double maxRiskLevel) {
            this.maxRiskLevel = Math.max(0.0, Math.min(1.0, maxRiskLevel));
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder analysis(String analysis) {
            this.analysis = analysis;
            return this;
        }

        public RegimeAnalysis build() {
            // 自動推導 allowedSide（如果未手動設定）
            if (allowedSide == AllowedSide.BOTH) {
                allowedSide = AllowedSide.fromRegime(marketRegime);
            }
            return new RegimeAnalysis(this);
        }
    }
}
