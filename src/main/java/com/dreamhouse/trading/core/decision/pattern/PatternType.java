package com.dreamhouse.trading.core.decision.pattern;

/**
 * 技術型態類型
 * 定義常見的技術分析型態
 */
public enum PatternType {
    /**
     * 雙底（看多反轉）
     */
    DOUBLE_BOTTOM("雙底", "Double bottom - bullish reversal", true),

    /**
     * 雙頂（看空反轉）
     */
    DOUBLE_TOP("雙頂", "Double top - bearish reversal", false),

    /**
     * 頭肩底（看多反轉）
     */
    HEAD_AND_SHOULDERS_BOTTOM("頭肩底", "Inverse head and shoulders - bullish", true),

    /**
     * 頭肩頂（看空反轉）
     */
    HEAD_AND_SHOULDERS_TOP("頭肩頂", "Head and shoulders - bearish", false),

    /**
     * 上升三角形（看多持續）
     */
    ASCENDING_TRIANGLE("上升三角", "Ascending triangle - bullish continuation", true),

    /**
     * 下降三角形（看空持續）
     */
    DESCENDING_TRIANGLE("下降三角", "Descending triangle - bearish continuation", false),

    /**
     * 對稱三角形（方向不明）
     */
    SYMMETRICAL_TRIANGLE("對稱三角", "Symmetrical triangle - direction unclear", null),

    /**
     * 上升楔形（看空反轉）
     */
    RISING_WEDGE("上升楔形", "Rising wedge - bearish reversal", false),

    /**
     * 下降楔形（看多反轉）
     */
    FALLING_WEDGE("下降楔形", "Falling wedge - bullish reversal", true),

    /**
     * 矩形整理（方向不明）
     */
    RECTANGLE("矩形", "Rectangle - ranging", null),

    /**
     * 上升通道（看多持續）
     */
    ASCENDING_CHANNEL("上升通道", "Ascending channel - bullish", true),

    /**
     * 下降通道（看空持續）
     */
    DESCENDING_CHANNEL("下降通道", "Descending channel - bearish", false),

    // === K線型態 ===

    /**
     * 錘子線（看多反轉）
     */
    HAMMER("錘子線", "Hammer - bullish reversal", true),

    /**
     * 吊人線（看空反轉）
     */
    HANGING_MAN("吊人線", "Hanging man - bearish reversal", false),

    /**
     * 十字星（中性）
     */
    DOJI("十字星", "Doji - neutral indecision", null),

    /**
     * 看漲吞沒（看多反轉）
     */
    BULLISH_ENGULFING("看漲吞沒", "Bullish engulfing - reversal", true),

    /**
     * 看跌吞沒（看空反轉）
     */
    BEARISH_ENGULFING("看跌吞沒", "Bearish engulfing - reversal", false),

    /**
     * 無型態
     */
    NONE("無型態", "No pattern detected", null);

    private final String displayName;
    private final String description;
    private final Boolean isBullish;  // null = 中性

    PatternType(String displayName, String description, Boolean isBullish) {
        this.displayName = displayName;
        this.description = description;
        this.isBullish = isBullish;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判斷是否為看多型態
     */
    public boolean isBullish() {
        return Boolean.TRUE.equals(isBullish);
    }

    /**
     * 判斷是否為看空型態
     */
    public boolean isBearish() {
        return Boolean.FALSE.equals(isBullish);
    }

    /**
     * 判斷是否為中性型態
     */
    public boolean isNeutral() {
        return isBullish == null;
    }

    /**
     * 判斷是否有型態
     */
    public boolean hasPattern() {
        return this != NONE;
    }

    /**
     * 判斷是否為反轉型態
     */
    public boolean isReversalPattern() {
        return this == DOUBLE_BOTTOM || this == DOUBLE_TOP ||
               this == HEAD_AND_SHOULDERS_BOTTOM || this == HEAD_AND_SHOULDERS_TOP ||
               this == RISING_WEDGE || this == FALLING_WEDGE ||
               this == HAMMER || this == HANGING_MAN ||
               this == BULLISH_ENGULFING || this == BEARISH_ENGULFING;
    }

    /**
     * 判斷是否為持續型態
     */
    public boolean isContinuationPattern() {
        return this == ASCENDING_TRIANGLE || this == DESCENDING_TRIANGLE ||
               this == ASCENDING_CHANNEL || this == DESCENDING_CHANNEL;
    }

    /**
     * 判斷是否為反轉型態（簡寫方法）
     */
    public boolean isReversal() {
        return isReversalPattern();
    }

    /**
     * 判斷是否為持續型態（簡寫方法）
     */
    public boolean isContinuation() {
        return isContinuationPattern();
    }
}
