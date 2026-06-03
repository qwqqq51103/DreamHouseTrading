package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final MarketRegime marketRegime;
    private final String benchmarkSymbol;
    private final Double relativeToBenchmarkPercent;
    private final String industry;
    private final Double industryStrength;
    private final Double relativeToIndustryPercent;
    private final Double watchlistRankPercent;
    private final Double watchlistVolumeRankPercent;
    private final Double vwap;
    private final Double vwapSlopePercent;
    private final Boolean volumeSustain;
    private final Double atrStopLoss;
    private final Double atrTakeProfit;
    private final List<RadarScoreComponent> scoreComponents;
    private final MarketDecision marketDecision;
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
        this.marketRegime = builder.marketRegime;
        this.benchmarkSymbol = builder.benchmarkSymbol;
        this.relativeToBenchmarkPercent = builder.relativeToBenchmarkPercent;
        this.industry = builder.industry;
        this.industryStrength = builder.industryStrength;
        this.relativeToIndustryPercent = builder.relativeToIndustryPercent;
        this.watchlistRankPercent = builder.watchlistRankPercent;
        this.watchlistVolumeRankPercent = builder.watchlistVolumeRankPercent;
        this.vwap = builder.vwap;
        this.vwapSlopePercent = builder.vwapSlopePercent;
        this.volumeSustain = builder.volumeSustain;
        this.atrStopLoss = builder.atrStopLoss;
        this.atrTakeProfit = builder.atrTakeProfit;
        this.scoreComponents = Collections.unmodifiableList(new ArrayList<>(builder.scoreComponents));
        this.marketDecision = builder.marketDecision;
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

    public MarketRegime getMarketRegime() {
        return marketRegime;
    }

    public String getBenchmarkSymbol() {
        return benchmarkSymbol;
    }

    public Double getRelativeToBenchmarkPercent() {
        return relativeToBenchmarkPercent;
    }

    public String getIndustry() {
        return industry;
    }

    public Double getIndustryStrength() {
        return industryStrength;
    }

    public Double getRelativeToIndustryPercent() {
        return relativeToIndustryPercent;
    }

    public Double getWatchlistRankPercent() {
        return watchlistRankPercent;
    }

    public Double getWatchlistVolumeRankPercent() {
        return watchlistVolumeRankPercent;
    }

    public Double getVwap() {
        return vwap;
    }

    public Double getVwapSlopePercent() {
        return vwapSlopePercent;
    }

    public Boolean getVolumeSustain() {
        return volumeSustain;
    }

    public Double getAtrStopLoss() {
        return atrStopLoss;
    }

    public Double getAtrTakeProfit() {
        return atrTakeProfit;
    }

    public List<RadarScoreComponent> getScoreComponents() {
        return scoreComponents;
    }

    public String getScoreComponentSummary() {
        if (scoreComponents.isEmpty()) {
            return "";
        }
        return scoreComponents.stream()
                .map(RadarScoreComponent::toCompactText)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    public String getLongBonusSummary() {
        if (scoreComponents.isEmpty()) {
            return "";
        }
        return scoreComponents.stream()
                .filter(RadarScoreComponent::contributesToLong)
                .map(RadarScoreComponent::toCompactText)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    public MarketDecision getMarketDecision() {
        return marketDecision;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public MarketScanResult withScannedAt(LocalDateTime scannedAt) {
        Builder builder = builder(symbol)
                .tradeMode(tradeMode)
                .score(score)
                .decisionResult(decisionResult)
                .reason(reason)
                .confidence(confidence)
                .suggestedStopLoss(suggestedStopLoss)
                .suggestedTakeProfit(suggestedTakeProfit)
                .suggestedQuantity(suggestedQuantity)
                .riskRewardRatio(riskRewardRatio)
                .rawSignalSummary(rawSignalSummary)
                .marketDecision(marketDecision)
                .scoreComponents(scoreComponents)
                .scannedAt(scannedAt);
        if (blockReason != null && !blockReason.isBlank()) {
            builder.blockReason(blockReason);
        }
        builder.marketRegime = marketRegime;
        builder.benchmarkSymbol = benchmarkSymbol;
        builder.relativeToBenchmarkPercent = relativeToBenchmarkPercent;
        builder.industry = industry;
        builder.industryStrength = industryStrength;
        builder.relativeToIndustryPercent = relativeToIndustryPercent;
        builder.watchlistRankPercent = watchlistRankPercent;
        builder.watchlistVolumeRankPercent = watchlistVolumeRankPercent;
        builder.vwap = vwap;
        builder.vwapSlopePercent = vwapSlopePercent;
        builder.volumeSustain = volumeSustain;
        builder.atrStopLoss = atrStopLoss;
        builder.atrTakeProfit = atrTakeProfit;
        return builder.build();
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
        private MarketRegime marketRegime;
        private String benchmarkSymbol = "";
        private Double relativeToBenchmarkPercent;
        private String industry = "";
        private Double industryStrength;
        private Double relativeToIndustryPercent;
        private Double watchlistRankPercent;
        private Double watchlistVolumeRankPercent;
        private Double vwap;
        private Double vwapSlopePercent;
        private Boolean volumeSustain;
        private Double atrStopLoss;
        private Double atrTakeProfit;
        private List<RadarScoreComponent> scoreComponents = List.of();
        private MarketDecision marketDecision = MarketDecision.ALLOW_LONG;
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
            if (!this.blockReason.isBlank()) {
                this.marketDecision = MarketDecision.BLOCK_LONG;
            }
            return this;
        }

        public Builder marketContext(MarketContextSnapshot snapshot, SymbolMarketContext context) {
            if (snapshot != null) {
                this.marketRegime = snapshot.regime();
            }
            if (context != null) {
                this.benchmarkSymbol = context.benchmarkSymbol();
                this.relativeToBenchmarkPercent = context.relativeToBenchmarkPercent();
                this.industry = context.industry();
                this.relativeToIndustryPercent = context.relativeToIndustryPercent();
                this.watchlistRankPercent = context.watchlistReturnRankPercent();
                this.watchlistVolumeRankPercent = context.watchlistVolumeRankPercent();
                this.vwap = context.vwap();
                this.vwapSlopePercent = context.vwapSlopePercent();
                this.volumeSustain = context.volumeSustain();
                if (snapshot != null && context.industry() != null) {
                    IndustryStrength strength = snapshot.industries().get(context.industry());
                    this.industryStrength = strength != null ? strength.score() : null;
                }
            }
            return this;
        }

        public Builder atrLevels(Double stopLoss, Double takeProfit) {
            this.atrStopLoss = stopLoss;
            this.atrTakeProfit = takeProfit;
            return this;
        }

        public Builder scoreComponents(List<RadarScoreComponent> scoreComponents) {
            this.scoreComponents = scoreComponents != null ? new ArrayList<>(scoreComponents) : List.of();
            return this;
        }

        public Builder marketDecision(MarketDecision marketDecision) {
            this.marketDecision = marketDecision != null ? marketDecision : MarketDecision.ALLOW_LONG;
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
