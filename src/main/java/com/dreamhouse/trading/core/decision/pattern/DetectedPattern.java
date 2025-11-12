package com.dreamhouse.trading.core.decision.pattern;

/**
 * 檢測到的型態
 *
 * 記錄單一型態的詳細資訊
 */
public class DetectedPattern {

    private final PatternType patternType;
    private final double confidence;
    private final int startBarIndex;
    private final int endBarIndex;
    private final String description;

    // 型態特徵值
    private final Double targetPrice;      // 目標價位（選填）
    private final Double stopLossPrice;    // 建議停損（選填）
    private final Double breakoutPrice;    // 突破價位（選填）

    private DetectedPattern(Builder builder) {
        this.patternType = builder.patternType;
        this.confidence = builder.confidence;
        this.startBarIndex = builder.startBarIndex;
        this.endBarIndex = builder.endBarIndex;
        this.description = builder.description;
        this.targetPrice = builder.targetPrice;
        this.stopLossPrice = builder.stopLossPrice;
        this.breakoutPrice = builder.breakoutPrice;
    }

    // Getters

    public PatternType getPatternType() {
        return patternType;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getStartBarIndex() {
        return startBarIndex;
    }

    public int getEndBarIndex() {
        return endBarIndex;
    }

    public String getDescription() {
        return description;
    }

    public Double getTargetPrice() {
        return targetPrice;
    }

    public Double getStopLossPrice() {
        return stopLossPrice;
    }

    public Double getBreakoutPrice() {
        return breakoutPrice;
    }

    /**
     * 型態是否完成（當前 bar 達到或超過 endBarIndex）
     */
    public boolean isCompleted(int currentBarIndex) {
        return currentBarIndex >= endBarIndex;
    }

    @Override
    public String toString() {
        return String.format("%s (信心度:%.2f, K線:%d-%d)",
                patternType.getDisplayName(),
                confidence,
                startBarIndex,
                endBarIndex);
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private PatternType patternType;
        private double confidence = 0.5;
        private int startBarIndex = -1;
        private int endBarIndex = -1;
        private String description = "";
        private Double targetPrice = null;
        private Double stopLossPrice = null;
        private Double breakoutPrice = null;

        public Builder(PatternType patternType) {
            this.patternType = patternType;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder startBarIndex(int index) {
            this.startBarIndex = index;
            return this;
        }

        public Builder endBarIndex(int index) {
            this.endBarIndex = index;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder targetPrice(double price) {
            this.targetPrice = price;
            return this;
        }

        public Builder stopLossPrice(double price) {
            this.stopLossPrice = price;
            return this;
        }

        public Builder breakoutPrice(double price) {
            this.breakoutPrice = price;
            return this;
        }

        public DetectedPattern build() {
            if (patternType == null) {
                throw new IllegalStateException("PatternType 不可為空");
            }
            if (startBarIndex < 0 || endBarIndex < 0) {
                throw new IllegalStateException("startBarIndex 和 endBarIndex 必須 >= 0");
            }
            if (endBarIndex < startBarIndex) {
                throw new IllegalStateException("endBarIndex 必須 >= startBarIndex");
            }
            return new DetectedPattern(this);
        }
    }
}
