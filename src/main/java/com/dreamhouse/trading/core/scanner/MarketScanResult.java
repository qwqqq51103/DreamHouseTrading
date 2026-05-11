package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

import java.time.LocalDateTime;

/**
 * Normalized scanner result used by the opportunity radar UI.
 */
public class MarketScanResult implements Comparable<MarketScanResult> {

    private final String symbol;
    private final TradeMode tradeMode;
    private final double score;
    private final DecisionResult decisionResult;
    private final String reason;
    private final double confidence;
    private final Double suggestedStopLoss;
    private final Double suggestedTakeProfit;
    private final Integer suggestedQuantity;
    private final Double riskRewardRatio;
    private final String rawSignalSummary;
    private final String blockReason;
    private final LocalDateTime scannedAt;

    private MarketScanResult(Builder builder) {
        this.symbol = builder.symbol;
        this.tradeMode = builder.tradeMode;
        this.score = builder.score;
        this.decisionResult = builder.decisionResult;
        this.reason = builder.reason;
        this.confidence = builder.confidence;
        this.suggestedStopLoss = builder.suggestedStopLoss;
        this.suggestedTakeProfit = builder.suggestedTakeProfit;
        this.suggestedQuantity = builder.suggestedQuantity;
        this.riskRewardRatio = builder.riskRewardRatio;
        this.rawSignalSummary = builder.rawSignalSummary;
        this.blockReason = builder.blockReason;
        this.scannedAt = builder.scannedAt;
    }

    public static Builder builder(String symbol) {
        return new Builder(symbol);
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public double getScore() {
        return score;
    }

    public DecisionResult getDecisionResult() {
        return decisionResult;
    }

    public String getReason() {
        return reason;
    }

    public double getConfidence() {
        return confidence;
    }

    public Double getSuggestedStopLoss() {
        return suggestedStopLoss;
    }

    public Double getSuggestedTakeProfit() {
        return suggestedTakeProfit;
    }

    public Integer getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public Double getRiskRewardRatio() {
        return riskRewardRatio;
    }

    public String getRawSignalSummary() {
        return rawSignalSummary;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public boolean hasTradeSignal() {
        return decisionResult != null && decisionResult.shouldTrade();
    }

    @Override
    public int compareTo(MarketScanResult other) {
        return Double.compare(other.score, score);
    }

    public static final class Builder {
        private final String symbol;
        private TradeMode tradeMode = TradeMode.NO_TRADE;
        private double score;
        private DecisionResult decisionResult;
        private String reason = "";
        private double confidence;
        private Double suggestedStopLoss;
        private Double suggestedTakeProfit;
        private Integer suggestedQuantity;
        private Double riskRewardRatio;
        private String rawSignalSummary = "";
        private String blockReason = "";
        private LocalDateTime scannedAt = LocalDateTime.now();

        private Builder(String symbol) {
            this.symbol = symbol;
        }

        public Builder tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode != null ? tradeMode : TradeMode.NO_TRADE;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder decisionResult(DecisionResult decisionResult) {
            this.decisionResult = decisionResult;
            if (decisionResult != null) {
                this.reason = decisionResult.getReason();
                this.confidence = decisionResult.getConfidence();
                this.suggestedStopLoss = decisionResult.getSuggestedStopLoss();
                this.suggestedTakeProfit = decisionResult.getSuggestedTakeProfit();
                this.suggestedQuantity = decisionResult.getSuggestedQuantity();
                this.riskRewardRatio = decisionResult.getRiskRewardRatio();
            }
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason != null ? reason : "";
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder suggestedStopLoss(Double suggestedStopLoss) {
            this.suggestedStopLoss = suggestedStopLoss;
            return this;
        }

        public Builder suggestedTakeProfit(Double suggestedTakeProfit) {
            this.suggestedTakeProfit = suggestedTakeProfit;
            return this;
        }

        public Builder suggestedQuantity(Integer suggestedQuantity) {
            this.suggestedQuantity = suggestedQuantity;
            return this;
        }

        public Builder riskRewardRatio(Double riskRewardRatio) {
            this.riskRewardRatio = riskRewardRatio;
            return this;
        }

        public Builder rawSignalSummary(String rawSignalSummary) {
            this.rawSignalSummary = rawSignalSummary != null ? rawSignalSummary : "";
            return this;
        }

        public Builder blockReason(String blockReason) {
            this.blockReason = blockReason != null ? blockReason : "";
            return this;
        }

        public Builder scannedAt(LocalDateTime scannedAt) {
            this.scannedAt = scannedAt != null ? scannedAt : LocalDateTime.now();
            return this;
        }

        public MarketScanResult build() {
            return new MarketScanResult(this);
        }
    }
}
