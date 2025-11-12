package com.dreamhouse.trading.core.decision.signal;

import com.dreamhouse.trading.core.Timeframe;

/**
 * 策略信號實作類
 * 實作 IStrategySignal 介面，提供完整的信號資訊
 */
public class StrategySignal implements IStrategySignal {

    private final String strategyName;
    private final Timeframe timeframe;
    private final SignalType signal;
    private final double confidence;
    private final double weight;
    private final long timestamp;
    private final String reason;
    private final Double suggestedStopLoss;
    private final Double suggestedTakeProfit;

    /**
     * 建構子 - 使用 Builder 模式創建
     */
    private StrategySignal(Builder builder) {
        this.strategyName = builder.strategyName;
        this.timeframe = builder.timeframe;
        this.signal = builder.signal;
        this.confidence = builder.confidence;
        this.weight = builder.weight;
        this.timestamp = builder.timestamp;
        this.reason = builder.reason;
        this.suggestedStopLoss = builder.suggestedStopLoss;
        this.suggestedTakeProfit = builder.suggestedTakeProfit;
    }

    @Override
    public String getStrategyName() {
        return strategyName;
    }

    @Override
    public Timeframe getTimeframe() {
        return timeframe;
    }

    @Override
    public SignalType getSignal() {
        return signal;
    }

    @Override
    public double getConfidence() {
        return confidence;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public Double getSuggestedStopLoss() {
        return suggestedStopLoss;
    }

    @Override
    public Double getSuggestedTakeProfit() {
        return suggestedTakeProfit;
    }

    /**
     * 計算加權分數
     * @return confidence × weight
     */
    public double getWeightedScore() {
        return confidence * weight;
    }

    @Override
    public String toString() {
        return String.format("[%s@%s] %s (信心:%.2f, 權重:%.2f, 得分:%.3f) - %s",
                strategyName,
                timeframe.getLabel(),
                signal.getDisplayName(),
                confidence,
                weight,
                getWeightedScore(),
                reason);
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private String strategyName;
        private Timeframe timeframe;
        private SignalType signal;
        private double confidence = 0.5; // 預設信心度
        private double weight = 1.0;     // 預設權重
        private long timestamp = System.currentTimeMillis();
        private String reason = "";
        private Double suggestedStopLoss = null;
        private Double suggestedTakeProfit = null;

        public Builder(String strategyName, Timeframe timeframe, SignalType signal) {
            this.strategyName = strategyName;
            this.timeframe = timeframe;
            this.signal = signal;
        }

        public Builder confidence(double confidence) {
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
            }
            this.confidence = confidence;
            return this;
        }

        public Builder weight(double weight) {
            if (weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("Weight must be between 0.0 and 1.0");
            }
            this.weight = weight;
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

        public Builder suggestedStopLoss(Double stopLoss) {
            this.suggestedStopLoss = stopLoss;
            return this;
        }

        public Builder suggestedTakeProfit(Double takeProfit) {
            this.suggestedTakeProfit = takeProfit;
            return this;
        }

        public StrategySignal build() {
            if (strategyName == null || strategyName.isEmpty()) {
                throw new IllegalStateException("Strategy name cannot be null or empty");
            }
            if (timeframe == null) {
                throw new IllegalStateException("Timeframe cannot be null");
            }
            if (signal == null) {
                throw new IllegalStateException("Signal type cannot be null");
            }
            return new StrategySignal(this);
        }
    }
}
