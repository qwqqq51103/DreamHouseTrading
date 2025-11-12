package com.dreamhouse.trading.core.decision.classifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易模式分類結果
 * 包含建議的交易模式、信心度、理由等資訊
 */
public class ClassificationResult {

    private final TradeMode primaryMode;
    private final TradeMode secondaryMode;
    private final double confidence;
    private final List<String> reasons;
    private final String summary;

    /**
     * 建構子（使用 Builder 模式）
     */
    private ClassificationResult(Builder builder) {
        this.primaryMode = builder.primaryMode;
        this.secondaryMode = builder.secondaryMode;
        this.confidence = builder.confidence;
        this.reasons = new ArrayList<>(builder.reasons);
        this.summary = builder.summary;
    }

    // Getters

    public TradeMode getPrimaryMode() {
        return primaryMode;
    }

    public TradeMode getSecondaryMode() {
        return secondaryMode;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return new ArrayList<>(reasons);
    }

    public String getSummary() {
        return summary;
    }

    /**
     * 是否有次要建議模式
     */
    public boolean hasSecondaryMode() {
        return secondaryMode != null && secondaryMode != TradeMode.NO_TRADE;
    }

    /**
     * 是否為高信心度（>= 0.7）
     */
    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }

    /**
     * 是否為中信心度（0.5 - 0.7）
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.5 && confidence < 0.7;
    }

    /**
     * 是否為低信心度（< 0.5）
     */
    public boolean isLowConfidence() {
        return confidence < 0.5;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClassificationResult[");
        sb.append("primary=").append(primaryMode.getDisplayName());
        if (hasSecondaryMode()) {
            sb.append(", secondary=").append(secondaryMode.getDisplayName());
        }
        sb.append(", confidence=").append(String.format("%.1f%%", confidence * 100));
        sb.append("]");
        return sb.toString();
    }

    /**
     * Builder 類別
     */
    public static class Builder {
        private TradeMode primaryMode = TradeMode.NO_TRADE;
        private TradeMode secondaryMode = null;
        private double confidence = 0.0;
        private List<String> reasons = new ArrayList<>();
        private String summary = "";

        public Builder primaryMode(TradeMode primaryMode) {
            this.primaryMode = primaryMode;
            return this;
        }

        public Builder secondaryMode(TradeMode secondaryMode) {
            this.secondaryMode = secondaryMode;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder addReason(String reason) {
            this.reasons.add(reason);
            return this;
        }

        public Builder reasons(List<String> reasons) {
            this.reasons = new ArrayList<>(reasons);
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public ClassificationResult build() {
            // 自動生成摘要（如果未設置）
            if (summary == null || summary.isEmpty()) {
                summary = generateSummary();
            }
            return new ClassificationResult(this);
        }

        private String generateSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("建議交易模式：").append(primaryMode.getDisplayName());

            if (secondaryMode != null && secondaryMode != TradeMode.NO_TRADE) {
                sb.append("，次要選擇：").append(secondaryMode.getDisplayName());
            }

            sb.append(String.format("（信心度：%.0f%%）", confidence * 100));

            if (!reasons.isEmpty()) {
                sb.append(" - ").append(String.join("；", reasons));
            }

            return sb.toString();
        }
    }
}
