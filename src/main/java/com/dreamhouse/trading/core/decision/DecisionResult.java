package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.risk.RiskViolation;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.voting.VotingResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderType;

/**
 * Normalized decision payload produced by DecisionEngine.
 */
public class DecisionResult {

    public enum Action {
        OPEN_LONG("做多進場"),
        OPEN_SHORT("做空訊號"),
        CLOSE_POSITION("平倉"),
        HOLD("持有"),
        NO_ACTION("不動作");

        private final String displayName;

        Action(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Source {
        RISK_MANAGER("風控管理"),
        STOP_MANAGER("停損停利"),
        VOTING_EXIT("投票出場"),
        VOTING_ENTRY("投票進場"),
        REGIME_FILTER("市場狀態過濾"),
        TREND_FILTER("趨勢過濾"),
        TECHNICAL("技術面"),
        MANUAL("手動");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Action action;
    private final Source source;
    private final String symbol;
    private final TradeMode tradeMode;
    private final String reason;
    private final long timestamp;
    private final OrderType orderType;
    private final OrderSide orderSide;
    private final Double suggestedStopLoss;
    private final Double suggestedTakeProfit;
    private final Integer suggestedQuantity;
    private final Double riskRewardRatio;
    private final RegimeAnalysis regimeAnalysis;
    private final TrendAnalysis trendAnalysis;
    private final VotingResult votingResult;
    private final RiskViolation riskViolation;
    private final double confidence;

    private DecisionResult(Builder builder) {
        this.action = builder.action;
        this.source = builder.source;
        this.symbol = builder.symbol;
        this.tradeMode = builder.tradeMode;
        this.reason = builder.reason;
        this.timestamp = builder.timestamp;
        this.orderType = builder.orderType;
        this.orderSide = builder.orderSide;
        this.suggestedStopLoss = builder.suggestedStopLoss;
        this.suggestedTakeProfit = builder.suggestedTakeProfit;
        this.suggestedQuantity = builder.suggestedQuantity;
        this.riskRewardRatio = builder.riskRewardRatio;
        this.regimeAnalysis = builder.regimeAnalysis;
        this.trendAnalysis = builder.trendAnalysis;
        this.votingResult = builder.votingResult;
        this.riskViolation = builder.riskViolation;
        this.confidence = builder.confidence;
    }

    public Action getAction() {
        return action;
    }

    public Source getSource() {
        return source;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeMode getTradeMode() {
        return tradeMode;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderSide getOrderSide() {
        return orderSide;
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

    public RegimeAnalysis getRegimeAnalysis() {
        return regimeAnalysis;
    }

    public TrendAnalysis getTrendAnalysis() {
        return trendAnalysis;
    }

    public VotingResult getVotingResult() {
        return votingResult;
    }

    public RiskViolation getRiskViolation() {
        return riskViolation;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean shouldTrade() {
        return action == Action.OPEN_LONG || action == Action.OPEN_SHORT || action == Action.CLOSE_POSITION;
    }

    public boolean isEntry() {
        return action == Action.OPEN_LONG || action == Action.OPEN_SHORT;
    }

    public boolean isExit() {
        return action == Action.CLOSE_POSITION;
    }

    public boolean isLong() {
        return action == Action.OPEN_LONG;
    }

    public boolean isShort() {
        return action == Action.OPEN_SHORT;
    }

    public MarketScanResult toMarketScanResult() {
        return new MarketScanResult(
                symbol,
                tradeMode,
                action,
                confidence,
                reason,
                suggestedStopLoss,
                suggestedTakeProfit,
                riskRewardRatio,
                timestamp);
    }

    @Override
    public String toString() {
        return String.format(
                "[Decision] %s %s | mode=%s | source=%s | confidence=%.2f | %s",
                symbol != null ? symbol : "-",
                action.getDisplayName(),
                tradeMode != null ? tradeMode.getShortCode() : "NONE",
                source.getDisplayName(),
                confidence,
                reason);
    }

    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== Decision ==========\n");
        sb.append(String.format("symbol: %s%n", symbol));
        sb.append(String.format("mode: %s%n",
                tradeMode != null ? tradeMode.getDisplayName() : "NONE"));
        sb.append(String.format("action: %s%n", action.getDisplayName()));
        sb.append(String.format("source: %s%n", source.getDisplayName()));
        sb.append(String.format("order: %s %s%n", orderSide, orderType));
        sb.append(String.format("reason: %s%n", reason));
        sb.append(String.format("confidence: %.2f%n", confidence));

        if (suggestedStopLoss != null) {
            sb.append(String.format("stop loss: %.2f%n", suggestedStopLoss));
        }
        if (suggestedTakeProfit != null) {
            sb.append(String.format("take profit: %.2f%n", suggestedTakeProfit));
        }
        if (suggestedQuantity != null) {
            sb.append(String.format("quantity: %d%n", suggestedQuantity));
        }
        if (riskRewardRatio != null) {
            sb.append(String.format("risk/reward: %.2f%n", riskRewardRatio));
        }
        if (regimeAnalysis != null) {
            sb.append("\n--- Regime ---\n").append(regimeAnalysis).append('\n');
        }
        if (trendAnalysis != null) {
            sb.append("\n--- Trend ---\n").append(trendAnalysis).append('\n');
        }
        if (votingResult != null) {
            sb.append("\n--- Voting ---\n").append(votingResult).append('\n');
        }
        if (riskViolation != null) {
            sb.append("\n--- Risk ---\n").append(riskViolation).append('\n');
        }
        sb.append("==============================\n");
        return sb.toString();
    }

    public static class Builder {
        private Action action = Action.NO_ACTION;
        private Source source = Source.MANUAL;
        private String symbol = "";
        private TradeMode tradeMode = TradeMode.NO_TRADE;
        private String reason = "";
        private long timestamp = System.currentTimeMillis();
        private OrderType orderType = OrderType.MARKET;
        private OrderSide orderSide = OrderSide.BUY;
        private Double suggestedStopLoss;
        private Double suggestedTakeProfit;
        private Integer suggestedQuantity;
        private Double riskRewardRatio;
        private RegimeAnalysis regimeAnalysis;
        private TrendAnalysis trendAnalysis;
        private VotingResult votingResult;
        private RiskViolation riskViolation;
        private double confidence = 0.5;

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder orderType(OrderType orderType) {
            this.orderType = orderType;
            return this;
        }

        public Builder orderSide(OrderSide orderSide) {
            this.orderSide = orderSide;
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

        public Builder regimeAnalysis(RegimeAnalysis regimeAnalysis) {
            this.regimeAnalysis = regimeAnalysis;
            return this;
        }

        public Builder trendAnalysis(TrendAnalysis trendAnalysis) {
            this.trendAnalysis = trendAnalysis;
            return this;
        }

        public Builder votingResult(VotingResult votingResult) {
            this.votingResult = votingResult;
            return this;
        }

        public Builder riskViolation(RiskViolation riskViolation) {
            this.riskViolation = riskViolation;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public DecisionResult build() {
            return new DecisionResult(this);
        }
    }
}
