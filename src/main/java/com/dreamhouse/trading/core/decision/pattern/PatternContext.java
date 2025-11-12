package com.dreamhouse.trading.core.decision.pattern;

import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * 型態上下文
 *
 * 記錄當前市場型態資訊，供決策引擎使用
 */
public class PatternContext {

    private final List<DetectedPattern> detectedPatterns;
    private final long timestamp;
    private final String symbol;

    // 當前主要型態
    private PatternType primaryPattern;
    private double patternConfidence;

    // 型態建議
    private String suggestion;
    private double riskMultiplier;  // 根據型態調整風險

    private PatternContext(Builder builder) {
        this.detectedPatterns = builder.detectedPatterns;
        this.timestamp = builder.timestamp;
        this.symbol = builder.symbol;
        this.primaryPattern = builder.primaryPattern;
        this.patternConfidence = builder.patternConfidence;
        this.suggestion = builder.suggestion;
        this.riskMultiplier = builder.riskMultiplier;
    }

    /**
     * 是否檢測到型態
     */
    public boolean hasPattern() {
        return !detectedPatterns.isEmpty() && primaryPattern != PatternType.NONE;
    }

    /**
     * 是否為看漲型態
     */
    public boolean isBullishPattern() {
        return primaryPattern != null && primaryPattern.isBullish();
    }

    /**
     * 是否為看跌型態
     */
    public boolean isBearishPattern() {
        return primaryPattern != null && primaryPattern.isBearish();
    }

    /**
     * 是否為反轉型態
     */
    public boolean isReversalPattern() {
        return primaryPattern != null && primaryPattern.isReversal();
    }

    /**
     * 是否為持續型態
     */
    public boolean isContinuationPattern() {
        return primaryPattern != null && primaryPattern.isContinuation();
    }

    /**
     * 獲取型態強度（信心度）
     */
    public double getStrength() {
        return patternConfidence;
    }

    /**
     * 獲取風險調整倍數
     *
     * 根據型態可靠性調整風險大小
     * 高可靠性型態可以增加風險，低可靠性降低風險
     */
    public double getRiskMultiplier() {
        return riskMultiplier;
    }

    // Getters

    public List<DetectedPattern> getDetectedPatterns() {
        return new ArrayList<>(detectedPatterns);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public PatternType getPrimaryPattern() {
        return primaryPattern;
    }

    public double getPatternConfidence() {
        return patternConfidence;
    }

    public String getSuggestion() {
        return suggestion;
    }

    @Override
    public String toString() {
        if (!hasPattern()) {
            return "[型態] 未檢測到明顯型態";
        }

        return String.format("[型態] %s | 信心度:%.2f | 風險倍數:%.2fx - %s",
                primaryPattern.getDisplayName(),
                patternConfidence,
                riskMultiplier,
                suggestion);
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private List<DetectedPattern> detectedPatterns = new ArrayList<>();
        private long timestamp = System.currentTimeMillis();
        private String symbol = "";
        private PatternType primaryPattern = PatternType.NONE;
        private double patternConfidence = 0.0;
        private String suggestion = "";
        private double riskMultiplier = 1.0;

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder addPattern(DetectedPattern pattern) {
            this.detectedPatterns.add(pattern);
            return this;
        }

        public Builder primaryPattern(PatternType pattern) {
            this.primaryPattern = pattern;
            return this;
        }

        public Builder patternConfidence(double confidence) {
            this.patternConfidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder riskMultiplier(double multiplier) {
            this.riskMultiplier = Math.max(0.5, Math.min(2.0, multiplier));
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PatternContext build() {
            return new PatternContext(this);
        }
    }
}
