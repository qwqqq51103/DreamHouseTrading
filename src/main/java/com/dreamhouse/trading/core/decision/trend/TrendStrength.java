package com.dreamhouse.trading.core.decision.trend;

/**
 * 趨勢強度
 * 量化趨勢的力度
 */
public enum TrendStrength {
    /**
     * 強勢趨勢
     */
    STRONG("強勢", "Strong trend", 0.7, 1.0),

    /**
     * 中等趨勢
     */
    MEDIUM("中等", "Medium trend", 0.4, 0.7),

    /**
     * 弱勢趨勢
     */
    WEAK("弱勢", "Weak trend", 0.2, 0.4),

    /**
     * 無趨勢
     */
    NONE("無", "No trend", 0.0, 0.2);

    private final String displayName;
    private final String description;
    private final double minScore;  // 最小分數
    private final double maxScore;  // 最大分數

    TrendStrength(String displayName, String description, double minScore, double maxScore) {
        this.displayName = displayName;
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getMinScore() {
        return minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    /**
     * 根據分數獲取趨勢強度
     * @param score 趨勢分數 (0.0 ~ 1.0)
     * @return 對應的趨勢強度
     */
    public static TrendStrength fromScore(double score) {
        if (score >= STRONG.minScore) {
            return STRONG;
        } else if (score >= MEDIUM.minScore) {
            return MEDIUM;
        } else if (score >= WEAK.minScore) {
            return WEAK;
        } else {
            return NONE;
        }
    }

    /**
     * 判斷是否為強趨勢
     */
    public boolean isStrong() {
        return this == STRONG;
    }

    /**
     * 判斷是否有趨勢
     */
    public boolean hasTrend() {
        return this != NONE;
    }

    /**
     * 獲取權重係數（用於信號加權）
     */
    public double getWeightFactor() {
        return (minScore + maxScore) / 2.0;
    }
}
