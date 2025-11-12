package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.voting.VotingResult;
import com.dreamhouse.trading.core.decision.risk.RiskViolation;

/**
 * 決策結果
 * DecisionEngine 的最終輸出，包含所有決策資訊
 */
public class DecisionResult {

    /**
     * 決策動作
     */
    public enum Action {
        OPEN_LONG("開多"),
        OPEN_SHORT("開空"),
        CLOSE_POSITION("平倉"),
        HOLD("持有"),
        NO_ACTION("無動作");

        private final String displayName;

        Action(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 決策來源（是哪個模組觸發的）
     */
    public enum Source {
        RISK_MANAGER("風險管理"),
        STOP_MANAGER("停損停利"),
        VOTING_EXIT("策略出場投票"),
        VOTING_ENTRY("策略進場投票"),
        REGIME_FILTER("週線濾網"),
        TREND_FILTER("日線濾網"),
        TECHNICAL("技術性"),
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
    private final String reason;
    private final long timestamp;

    // 價格建議
    private final Double suggestedStopLoss;
    private final Double suggestedTakeProfit;
    private final Integer suggestedQuantity;

    // 各模組狀態
    private final RegimeAnalysis regimeAnalysis;
    private final TrendAnalysis trendAnalysis;
    private final VotingResult votingResult;
    private final RiskViolation riskViolation;

    // 信心度
    private final double confidence;

    private DecisionResult(Builder builder) {
        this.action = builder.action;
        this.source = builder.source;
        this.reason = builder.reason;
        this.timestamp = builder.timestamp;
        this.suggestedStopLoss = builder.suggestedStopLoss;
        this.suggestedTakeProfit = builder.suggestedTakeProfit;
        this.suggestedQuantity = builder.suggestedQuantity;
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

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
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

    /**
     * 是否應該執行交易
     */
    public boolean shouldTrade() {
        return action == Action.OPEN_LONG || action == Action.OPEN_SHORT || action == Action.CLOSE_POSITION;
    }

    /**
     * 是否為開倉動作
     */
    public boolean isEntry() {
        return action == Action.OPEN_LONG || action == Action.OPEN_SHORT;
    }

    /**
     * 是否為平倉動作
     */
    public boolean isExit() {
        return action == Action.CLOSE_POSITION;
    }

    /**
     * 是否為做多
     */
    public boolean isLong() {
        return action == Action.OPEN_LONG;
    }

    /**
     * 是否為做空
     */
    public boolean isShort() {
        return action == Action.OPEN_SHORT;
    }

    @Override
    public String toString() {
        return String.format("[決策] %s | 來源:%s | 信心度:%.2f - %s",
                action.getDisplayName(),
                source.getDisplayName(),
                confidence,
                reason);
    }

    /**
     * 詳細報告
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 決策報告 ==========\n");
        sb.append(String.format("動作: %s\n", action.getDisplayName()));
        sb.append(String.format("來源: %s\n", source.getDisplayName()));
        sb.append(String.format("原因: %s\n", reason));
        sb.append(String.format("信心度: %.2f\n", confidence));

        if (suggestedStopLoss != null) {
            sb.append(String.format("建議停損: %.2f\n", suggestedStopLoss));
        }
        if (suggestedTakeProfit != null) {
            sb.append(String.format("建議停利: %.2f\n", suggestedTakeProfit));
        }
        if (suggestedQuantity != null) {
            sb.append(String.format("建議數量: %d\n", suggestedQuantity));
        }

        sb.append("\n--- 週線環境 ---\n");
        if (regimeAnalysis != null) {
            sb.append(regimeAnalysis.toString()).append("\n");
        } else {
            sb.append("未分析\n");
        }

        sb.append("\n--- 日線趨勢 ---\n");
        if (trendAnalysis != null) {
            sb.append(trendAnalysis.toString()).append("\n");
        } else {
            sb.append("未分析\n");
        }

        sb.append("\n--- 投票結果 ---\n");
        if (votingResult != null) {
            sb.append(votingResult.toString()).append("\n");
        } else {
            sb.append("無投票\n");
        }

        sb.append("\n--- 風險違規 ---\n");
        if (riskViolation != null) {
            sb.append(riskViolation.toString()).append("\n");
        } else {
            sb.append("無違規\n");
        }

        sb.append("==============================\n");
        return sb.toString();
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private Action action = Action.NO_ACTION;
        private Source source = Source.MANUAL;
        private String reason = "";
        private long timestamp = System.currentTimeMillis();
        private Double suggestedStopLoss = null;
        private Double suggestedTakeProfit = null;
        private Integer suggestedQuantity = null;
        private RegimeAnalysis regimeAnalysis = null;
        private TrendAnalysis trendAnalysis = null;
        private VotingResult votingResult = null;
        private RiskViolation riskViolation = null;
        private double confidence = 0.5;

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
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

        public Builder suggestedStopLoss(Double stopLoss) {
            this.suggestedStopLoss = stopLoss;
            return this;
        }

        public Builder suggestedTakeProfit(Double takeProfit) {
            this.suggestedTakeProfit = takeProfit;
            return this;
        }

        public Builder suggestedQuantity(Integer quantity) {
            this.suggestedQuantity = quantity;
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
