package com.dreamhouse.trading.core.decision.regime;

/**
 * 市場環境檢測配置
 * 定義週線層市場環境分析的參數
 */
public class RegimeConfig {

    /**
     * 長期移動平均週期（用於判斷大趨勢）
     * 預設 50 根週線 K 棒
     */
    private int longMaPeriod = 50;

    /**
     * 中期移動平均週期
     * 預設 20 根週線 K 棒
     */
    private int mediumMaPeriod = 20;

    /**
     * 短期移動平均週期
     * 預設 10 根週線 K 棒
     */
    private int shortMaPeriod = 10;

    /**
     * ADX 週期（平均趨向指標，用於判斷趨勢強度）
     * 預設 14
     */
    private int adxPeriod = 14;

    /**
     * 趨勢判斷閾值（ADX）
     * ADX >= 此值視為有明確趨勢
     * 預設 25
     */
    private double trendThreshold = 25.0;

    /**
     * 強趨勢判斷閾值（ADX）
     * ADX >= 此值視為強趨勢
     * 預設 40
     */
    private double strongTrendThreshold = 40.0;

    /**
     * 波動率過濾閾值（標準差倍數）
     * 當前波動率 < 平均波動率 * 此值時，視為低波動環境（NoTrade）
     * 預設 0.3 (30%)
     */
    private double lowVolatilityThreshold = 0.3;

    /**
     * 最大回撤容忍度（用於 NoTrade 判斷）
     * 當回撤 > 此值時，考慮進入 NoTrade 狀態
     * 預設 20% = 0.20
     */
    private double maxDrawdownTolerance = 0.20;

    /**
     * 牛市確認所需的連續上漲週數
     * 預設 3 週
     */
    private int bullConfirmWeeks = 3;

    /**
     * 熊市確認所需的連續下跌週數
     * 預設 3 週
     */
    private int bearConfirmWeeks = 3;

    // Getters and Setters

    public int getLongMaPeriod() {
        return longMaPeriod;
    }

    public void setLongMaPeriod(int longMaPeriod) {
        if (longMaPeriod < 10) {
            throw new IllegalArgumentException("Long MA period must be at least 10");
        }
        this.longMaPeriod = longMaPeriod;
    }

    public int getMediumMaPeriod() {
        return mediumMaPeriod;
    }

    public void setMediumMaPeriod(int mediumMaPeriod) {
        if (mediumMaPeriod < 5) {
            throw new IllegalArgumentException("Medium MA period must be at least 5");
        }
        this.mediumMaPeriod = mediumMaPeriod;
    }

    public int getShortMaPeriod() {
        return shortMaPeriod;
    }

    public void setShortMaPeriod(int shortMaPeriod) {
        if (shortMaPeriod < 3) {
            throw new IllegalArgumentException("Short MA period must be at least 3");
        }
        this.shortMaPeriod = shortMaPeriod;
    }

    public int getAdxPeriod() {
        return adxPeriod;
    }

    public void setAdxPeriod(int adxPeriod) {
        if (adxPeriod < 5) {
            throw new IllegalArgumentException("ADX period must be at least 5");
        }
        this.adxPeriod = adxPeriod;
    }

    public double getTrendThreshold() {
        return trendThreshold;
    }

    public void setTrendThreshold(double trendThreshold) {
        if (trendThreshold < 0 || trendThreshold > 100) {
            throw new IllegalArgumentException("Trend threshold must be between 0 and 100");
        }
        this.trendThreshold = trendThreshold;
    }

    public double getStrongTrendThreshold() {
        return strongTrendThreshold;
    }

    public void setStrongTrendThreshold(double strongTrendThreshold) {
        if (strongTrendThreshold < 0 || strongTrendThreshold > 100) {
            throw new IllegalArgumentException("Strong trend threshold must be between 0 and 100");
        }
        this.strongTrendThreshold = strongTrendThreshold;
    }

    public double getLowVolatilityThreshold() {
        return lowVolatilityThreshold;
    }

    public void setLowVolatilityThreshold(double lowVolatilityThreshold) {
        if (lowVolatilityThreshold <= 0.0) {
            throw new IllegalArgumentException("Low volatility threshold must be positive");
        }
        this.lowVolatilityThreshold = lowVolatilityThreshold;
    }

    public double getMaxDrawdownTolerance() {
        return maxDrawdownTolerance;
    }

    public void setMaxDrawdownTolerance(double maxDrawdownTolerance) {
        if (maxDrawdownTolerance <= 0.0 || maxDrawdownTolerance > 1.0) {
            throw new IllegalArgumentException("Max drawdown tolerance must be between 0.0 and 1.0");
        }
        this.maxDrawdownTolerance = maxDrawdownTolerance;
    }

    public int getBullConfirmWeeks() {
        return bullConfirmWeeks;
    }

    public void setBullConfirmWeeks(int bullConfirmWeeks) {
        if (bullConfirmWeeks < 1) {
            throw new IllegalArgumentException("Bull confirm weeks must be at least 1");
        }
        this.bullConfirmWeeks = bullConfirmWeeks;
    }

    public int getBearConfirmWeeks() {
        return bearConfirmWeeks;
    }

    public void setBearConfirmWeeks(int bearConfirmWeeks) {
        if (bearConfirmWeeks < 1) {
            throw new IllegalArgumentException("Bear confirm weeks must be at least 1");
        }
        this.bearConfirmWeeks = bearConfirmWeeks;
    }

    @Override
    public String toString() {
        return String.format("RegimeConfig[MA=%d/%d/%d, ADX=%d, trendThreshold=%.1f]",
                shortMaPeriod, mediumMaPeriod, longMaPeriod, adxPeriod, trendThreshold);
    }
}
