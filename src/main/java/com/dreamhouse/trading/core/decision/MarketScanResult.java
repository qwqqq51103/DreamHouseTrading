package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.decision.classifier.TradeMode;

/**
 * Normalized scan payload aligned with DecisionResult.
 */
public class MarketScanResult {

    private final String symbol;
    private final TradeMode mode;
    private final DecisionResult.Action action;
    private final double confidence;
    private final String reason;
    private final Double stopLoss;
    private final Double takeProfit;
    private final Double riskRewardRatio;
    private final long timestamp;

    public MarketScanResult(
            String symbol,
            TradeMode mode,
            DecisionResult.Action action,
            double confidence,
            String reason,
            Double stopLoss,
            Double takeProfit,
            Double riskRewardRatio,
            long timestamp) {
        this.symbol = symbol;
        this.mode = mode;
        this.action = action;
        this.confidence = confidence;
        this.reason = reason;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.riskRewardRatio = riskRewardRatio;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeMode getMode() {
        return mode;
    }

    public DecisionResult.Action getAction() {
        return action;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public Double getStopLoss() {
        return stopLoss;
    }

    public Double getTakeProfit() {
        return takeProfit;
    }

    public Double getRiskRewardRatio() {
        return riskRewardRatio;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
