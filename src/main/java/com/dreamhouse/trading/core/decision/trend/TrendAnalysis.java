package com.dreamhouse.trading.core.decision.trend;

import com.dreamhouse.trading.core.decision.regime.AllowedSide;

/**
 * 趨勢分析結果（日線層）
 * 由 TrendAnalyzer 產生，提供給決策引擎使用
 */
public class TrendAnalysis {

    private final TrendDirection direction;
    private final TrendStrength strength;
    private final AllowedSide biasSide;        // 趨勢偏好方向
    private final double confidence;           // 信心度 0.0 ~ 1.0
    private final double atr;                  // 平均真實波幅
    private final double rsi;                  // RSI 值
    private final double baseStopDistance;     // 建議的停損距離（基於 ATR）
    private final double baseTakeProfitRatio;  // 建議的停利比例
    private final long timestamp;
    private final String analysis;             // 分析說明

    private TrendAnalysis(Builder builder) {
        this.direction = builder.direction;
        this.strength = builder.strength;
        this.biasSide = builder.biasSide;
        this.confidence = builder.confidence;
        this.atr = builder.atr;
        this.rsi = builder.rsi;
        this.baseStopDistance = builder.baseStopDistance;
        this.baseTakeProfitRatio = builder.baseTakeProfitRatio;
        this.timestamp = builder.timestamp;
        this.analysis = builder.analysis;
    }

    public TrendDirection getDirection() {
        return direction;
    }

    public TrendStrength getStrength() {
        return strength;
    }

    public AllowedSide getBiasSide() {
        return biasSide;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getAtr() {
        return atr;
    }

    public double getRsi() {
        return rsi;
    }

    public double getBaseStopDistance() {
        return baseStopDistance;
    }

    public double getBaseTakeProfitRatio() {
        return baseTakeProfitRatio;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAnalysis() {
        return analysis;
    }

    /**
     * 判斷是否為明確趨勢
     */
    public boolean hasClearTrend() {
        return direction.isTrending() && strength.hasTrend();
    }

    /**
     * 判斷是否為強趨勢
     */
    public boolean isStrongTrend() {
        return direction.isTrending() && strength.isStrong();
    }

    /**
     * 判斷是否超買
     */
    public boolean isOverbought() {
        return rsi >= 70.0;
    }

    /**
     * 判斷是否超賣
     */
    public boolean isOversold() {
        return rsi <= 30.0;
    }

    /**
     * 計算建議的停損價格
     */
    public double calculateStopLoss(double entryPrice, boolean isLong) {
        if (isLong) {
            return entryPrice - baseStopDistance;
        } else {
            return entryPrice + baseStopDistance;
        }
    }

    /**
     * 計算建議的停利價格
     */
    public double calculateTakeProfit(double entryPrice, boolean isLong) {
        double takeProfitDistance = baseStopDistance * baseTakeProfitRatio;
        if (isLong) {
            return entryPrice + takeProfitDistance;
        } else {
            return entryPrice - takeProfitDistance;
        }
    }

    @Override
    public String toString() {
        return String.format("[日線趨勢] %s %s | 偏好:%s | ATR:%.2f | RSI:%.1f | 停損距離:%.2f | 信心度:%.2f - %s",
                direction.getDisplayName(),
                strength.getDisplayName(),
                biasSide.getDisplayName(),
                atr,
                rsi,
                baseStopDistance,
                confidence,
                analysis);
    }

    /**
     * Builder 建造者模式
     */
    public static class Builder {
        private TrendDirection direction = TrendDirection.UNCLEAR;
        private TrendStrength strength = TrendStrength.NONE;
        private AllowedSide biasSide = AllowedSide.BOTH;
        private double confidence = 0.5;
        private double atr = 0.0;
        private double rsi = 50.0;
        private double baseStopDistance = 0.0;
        private double baseTakeProfitRatio = 2.0;
        private long timestamp = System.currentTimeMillis();
        private String analysis = "";

        public Builder direction(TrendDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder strength(TrendStrength strength) {
            this.strength = strength;
            return this;
        }

        public Builder biasSide(AllowedSide biasSide) {
            this.biasSide = biasSide;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder atr(double atr) {
            this.atr = atr;
            return this;
        }

        public Builder rsi(double rsi) {
            this.rsi = rsi;
            return this;
        }

        public Builder baseStopDistance(double baseStopDistance) {
            this.baseStopDistance = baseStopDistance;
            return this;
        }

        public Builder baseTakeProfitRatio(double baseTakeProfitRatio) {
            this.baseTakeProfitRatio = baseTakeProfitRatio;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder analysis(String analysis) {
            this.analysis = analysis;
            return this;
        }

        public TrendAnalysis build() {
            // 自動推導 biasSide（如果未手動設定）
            if (biasSide == AllowedSide.BOTH) {
                if (direction == TrendDirection.UP) {
                    biasSide = AllowedSide.LONG_ONLY;
                } else if (direction == TrendDirection.DOWN) {
                    biasSide = AllowedSide.SHORT_ONLY;
                } else {
                    biasSide = AllowedSide.BOTH;
                }
            }
            return new TrendAnalysis(this);
        }
    }
}
