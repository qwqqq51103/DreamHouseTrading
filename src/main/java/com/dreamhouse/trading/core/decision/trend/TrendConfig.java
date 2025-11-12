package com.dreamhouse.trading.core.decision.trend;

/**
 * 趨勢分析配置
 * 定義日線層趨勢分析的參數
 */
public class TrendConfig {

    /**
     * 快速移動平均週期
     * 預設 10 根日線 K 棒
     */
    private int fastMaPeriod = 10;

    /**
     * 慢速移動平均週期
     * 預設 30 根日線 K 棒
     */
    private int slowMaPeriod = 30;

    /**
     * ATR 週期（Average True Range，用於波動度計算）
     * 預設 14 根日線 K 棒
     */
    private int atrPeriod = 14;

    /**
     * RSI 週期（相對強弱指標）
     * 預設 14
     */
    private int rsiPeriod = 14;

    /**
     * 上升趨勢確認所需的連續上漲天數
     * 預設 3 天
     */
    private int uptrendConfirmDays = 3;

    /**
     * 下降趨勢確認所需的連續下跌天數
     * 預設 3 天
     */
    private int downtrendConfirmDays = 3;

    /**
     * 橫向整理判斷的價格波動範圍（百分比）
     * 當價格在此範圍內波動時，視為橫向整理
     * 預設 5% = 0.05
     */
    private double sidewayRangePercent = 0.05;

    /**
     * 趨勢強度判斷的斜率閾值
     * MA 斜率絕對值 >= 此值時，視為有趨勢
     * 預設 0.001 (0.1%)
     */
    private double trendSlopeThreshold = 0.001;

    /**
     * 強趨勢的斜率閾值
     * MA 斜率絕對值 >= 此值時，視為強趨勢
     * 預設 0.005 (0.5%)
     */
    private double strongTrendSlopeThreshold = 0.005;

    /**
     * 基礎停損距離倍數（ATR 倍數）
     * 預設 2.0 倍 ATR
     */
    private double baseStopLossAtrMultiplier = 2.0;

    /**
     * 基礎停利比例（風險報酬比）
     * 預設 2.0 (停利距離 = 停損距離 * 2)
     */
    private double baseTakeProfitRatio = 2.0;

    /**
     * 超買閾值（RSI）
     * RSI >= 此值視為超買
     * 預設 70
     */
    private double overboughtThreshold = 70.0;

    /**
     * 超賣閾值（RSI）
     * RSI <= 此值視為超賣
     * 預設 30
     */
    private double oversoldThreshold = 30.0;

    // Getters and Setters

    public int getFastMaPeriod() {
        return fastMaPeriod;
    }

    public void setFastMaPeriod(int fastMaPeriod) {
        if (fastMaPeriod < 3) {
            throw new IllegalArgumentException("Fast MA period must be at least 3");
        }
        this.fastMaPeriod = fastMaPeriod;
    }

    public int getSlowMaPeriod() {
        return slowMaPeriod;
    }

    public void setSlowMaPeriod(int slowMaPeriod) {
        if (slowMaPeriod < 10) {
            throw new IllegalArgumentException("Slow MA period must be at least 10");
        }
        this.slowMaPeriod = slowMaPeriod;
    }

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        if (atrPeriod < 5) {
            throw new IllegalArgumentException("ATR period must be at least 5");
        }
        this.atrPeriod = atrPeriod;
    }

    public int getRsiPeriod() {
        return rsiPeriod;
    }

    public void setRsiPeriod(int rsiPeriod) {
        if (rsiPeriod < 5) {
            throw new IllegalArgumentException("RSI period must be at least 5");
        }
        this.rsiPeriod = rsiPeriod;
    }

    public int getUptrendConfirmDays() {
        return uptrendConfirmDays;
    }

    public void setUptrendConfirmDays(int uptrendConfirmDays) {
        if (uptrendConfirmDays < 1) {
            throw new IllegalArgumentException("Uptrend confirm days must be at least 1");
        }
        this.uptrendConfirmDays = uptrendConfirmDays;
    }

    public int getDowntrendConfirmDays() {
        return downtrendConfirmDays;
    }

    public void setDowntrendConfirmDays(int downtrendConfirmDays) {
        if (downtrendConfirmDays < 1) {
            throw new IllegalArgumentException("Downtrend confirm days must be at least 1");
        }
        this.downtrendConfirmDays = downtrendConfirmDays;
    }

    public double getSidewayRangePercent() {
        return sidewayRangePercent;
    }

    public void setSidewayRangePercent(double sidewayRangePercent) {
        if (sidewayRangePercent <= 0.0 || sidewayRangePercent > 0.5) {
            throw new IllegalArgumentException("Sideway range percent must be between 0.0 and 0.5");
        }
        this.sidewayRangePercent = sidewayRangePercent;
    }

    public double getTrendSlopeThreshold() {
        return trendSlopeThreshold;
    }

    public void setTrendSlopeThreshold(double trendSlopeThreshold) {
        if (trendSlopeThreshold < 0.0) {
            throw new IllegalArgumentException("Trend slope threshold must be non-negative");
        }
        this.trendSlopeThreshold = trendSlopeThreshold;
    }

    public double getStrongTrendSlopeThreshold() {
        return strongTrendSlopeThreshold;
    }

    public void setStrongTrendSlopeThreshold(double strongTrendSlopeThreshold) {
        if (strongTrendSlopeThreshold < 0.0) {
            throw new IllegalArgumentException("Strong trend slope threshold must be non-negative");
        }
        this.strongTrendSlopeThreshold = strongTrendSlopeThreshold;
    }

    public double getBaseStopLossAtrMultiplier() {
        return baseStopLossAtrMultiplier;
    }

    public void setBaseStopLossAtrMultiplier(double baseStopLossAtrMultiplier) {
        if (baseStopLossAtrMultiplier <= 0.0) {
            throw new IllegalArgumentException("Base stop loss ATR multiplier must be positive");
        }
        this.baseStopLossAtrMultiplier = baseStopLossAtrMultiplier;
    }

    public double getBaseTakeProfitRatio() {
        return baseTakeProfitRatio;
    }

    public void setBaseTakeProfitRatio(double baseTakeProfitRatio) {
        if (baseTakeProfitRatio <= 0.0) {
            throw new IllegalArgumentException("Base take profit ratio must be positive");
        }
        this.baseTakeProfitRatio = baseTakeProfitRatio;
    }

    public double getOverboughtThreshold() {
        return overboughtThreshold;
    }

    public void setOverboughtThreshold(double overboughtThreshold) {
        if (overboughtThreshold < 0 || overboughtThreshold > 100) {
            throw new IllegalArgumentException("Overbought threshold must be between 0 and 100");
        }
        this.overboughtThreshold = overboughtThreshold;
    }

    public double getOversoldThreshold() {
        return oversoldThreshold;
    }

    public void setOversoldThreshold(double oversoldThreshold) {
        if (oversoldThreshold < 0 || oversoldThreshold > 100) {
            throw new IllegalArgumentException("Oversold threshold must be between 0 and 100");
        }
        this.oversoldThreshold = oversoldThreshold;
    }

    @Override
    public String toString() {
        return String.format("TrendConfig[MA=%d/%d, ATR=%d, RSI=%d, stopLoss=%.1fxATR]",
                fastMaPeriod, slowMaPeriod, atrPeriod, rsiPeriod, baseStopLossAtrMultiplier);
    }
}
