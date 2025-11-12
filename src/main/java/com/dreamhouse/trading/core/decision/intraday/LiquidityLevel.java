package com.dreamhouse.trading.core.decision.intraday;

/**
 * 流動性等級
 * 用於評估市場流動性狀況
 */
public enum LiquidityLevel {

    /**
     * 極高流動性
     * 大量成交、點差極小
     * 適合當沖、高頻交易
     */
    VERY_HIGH("極高", 5),

    /**
     * 高流動性
     * 成交活躍、點差小
     * 適合當沖交易
     */
    HIGH("高", 4),

    /**
     * 中等流動性
     * 成交正常、點差適中
     * 適合短線交易
     */
    MEDIUM("中等", 3),

    /**
     * 低流動性
     * 成交清淡、點差較大
     * 僅適合波段交易
     */
    LOW("低", 2),

    /**
     * 極低流動性
     * 成交稀少、點差很大
     * 不建議交易
     */
    VERY_LOW("極低", 1);

    private final String displayName;
    private final int level;

    LiquidityLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * 是否適合當沖
     */
    public boolean isSuitableForDayTrading() {
        return level >= 4;  // HIGH 或 VERY_HIGH
    }

    /**
     * 是否適合短線
     */
    public boolean isSuitableForShortSwing() {
        return level >= 3;  // MEDIUM 及以上
    }

    /**
     * 是否適合波段
     */
    public boolean isSuitableForSwingTrading() {
        return level >= 2;  // LOW 及以上
    }

    @Override
    public String toString() {
        return displayName + " (" + level + ")";
    }
}
