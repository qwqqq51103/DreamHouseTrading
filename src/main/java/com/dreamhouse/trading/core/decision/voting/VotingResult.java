package com.dreamhouse.trading.core.decision.voting;

import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;

import java.util.ArrayList;
import java.util.List;

/**
 * 投票結果
 * 儲存多策略投票的結果與詳細資訊
 */
public class VotingResult {

    /**
     * 投票決策
     */
    public enum Decision {
        ENTER_LONG("進場做多"),
        ENTER_SHORT("進場做空"),
        EXIT("出場平倉"),
        REVERSE_LONG("反手做多"),
        REVERSE_SHORT("反手做空"),
        HOLD("持有不動"),
        NO_ACTION("無動作");

        private final String displayName;

        Decision(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Decision decision;
    private final double longScore;
    private final double shortScore;
    private final double exitScore;
    private final double reverseScore;
    private final int votingStrategyCount;
    private final List<IStrategySignal> signals;
    private final long timestamp;
    private final String reason;

    private VotingResult(Builder builder) {
        this.decision = builder.decision;
        this.longScore = builder.longScore;
        this.shortScore = builder.shortScore;
        this.exitScore = builder.exitScore;
        this.reverseScore = builder.reverseScore;
        this.votingStrategyCount = builder.votingStrategyCount;
        this.signals = new ArrayList<>(builder.signals);
        this.timestamp = builder.timestamp;
        this.reason = builder.reason;
    }

    public Decision getDecision() {
        return decision;
    }

    public double getLongScore() {
        return longScore;
    }

    public double getShortScore() {
        return shortScore;
    }

    public double getExitScore() {
        return exitScore;
    }

    public double getReverseScore() {
        return reverseScore;
    }

    public int getVotingStrategyCount() {
        return votingStrategyCount;
    }

    public List<IStrategySignal> getSignals() {
        return new ArrayList<>(signals);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }

    /**
     * 判斷是否為進場決策
     */
    public boolean isEntry() {
        return decision == Decision.ENTER_LONG || decision == Decision.ENTER_SHORT ||
               decision == Decision.REVERSE_LONG || decision == Decision.REVERSE_SHORT;
    }

    /**
     * 判斷是否為出場決策
     */
    public boolean isExit() {
        return decision == Decision.EXIT || isReverse();
    }

    /**
     * 判斷是否為反手決策
     */
    public boolean isReverse() {
        return decision == Decision.REVERSE_LONG || decision == Decision.REVERSE_SHORT;
    }

    /**
     * 判斷是否為多頭方向
     */
    public boolean isLong() {
        return decision == Decision.ENTER_LONG || decision == Decision.REVERSE_LONG;
    }

    /**
     * 判斷是否為空頭方向
     */
    public boolean isShort() {
        return decision == Decision.ENTER_SHORT || decision == Decision.REVERSE_SHORT;
    }

    /**
     * 獲取決策方向的信號類型
     */
    public SignalType getSignalType() {
        if (isLong()) return SignalType.LONG;
        if (isShort()) return SignalType.SHORT;
        if (decision == Decision.EXIT) return SignalType.EXIT;
        if (decision == Decision.HOLD) return SignalType.HOLD;
        return SignalType.NO_TRADE;
    }

    @Override
    public String toString() {
        return String.format("[VotingResult] %s (多頭:%.3f 空頭:%.3f 出場:%.3f) - %s [%d策略投票]",
                decision.getDisplayName(),
                longScore, shortScore, exitScore,
                reason, votingStrategyCount);
    }

    /**
     * 詳細報告（包含所有策略信號）
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toString()).append("\n");
        sb.append("  策略信號詳情:\n");
        for (IStrategySignal signal : signals) {
            sb.append("    - ").append(signal.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private Decision decision = Decision.NO_ACTION;
        private double longScore = 0.0;
        private double shortScore = 0.0;
        private double exitScore = 0.0;
        private double reverseScore = 0.0;
        private int votingStrategyCount = 0;
        private List<IStrategySignal> signals = new ArrayList<>();
        private long timestamp = System.currentTimeMillis();
        private String reason = "";

        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        public Builder longScore(double longScore) {
            this.longScore = longScore;
            return this;
        }

        public Builder shortScore(double shortScore) {
            this.shortScore = shortScore;
            return this;
        }

        public Builder exitScore(double exitScore) {
            this.exitScore = exitScore;
            return this;
        }

        public Builder reverseScore(double reverseScore) {
            this.reverseScore = reverseScore;
            return this;
        }

        public Builder votingStrategyCount(int count) {
            this.votingStrategyCount = count;
            return this;
        }

        public Builder signals(List<IStrategySignal> signals) {
            this.signals = new ArrayList<>(signals);
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public VotingResult build() {
            return new VotingResult(this);
        }
    }
}
