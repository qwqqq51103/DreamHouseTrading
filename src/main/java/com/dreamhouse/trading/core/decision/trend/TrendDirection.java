package com.dreamhouse.trading.core.decision.trend;

/**
 * 趨勢方向（日線層）
 * 定義短中期趨勢的方向
 */
public enum TrendDirection {
    /**
     * 上升趨勢
     */
    UP("上升", "Uptrend"),

    /**
     * 下降趨勢
     */
    DOWN("下降", "Downtrend"),

    /**
     * 橫向整理
     */
    SIDEWAY("橫向", "Sideways / Ranging"),

    /**
     * 不明確
     */
    UNCLEAR("不明確", "Unclear trend");

    private final String displayName;
    private final String description;

    TrendDirection(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判斷是否為明確趨勢
     */
    public boolean isTrending() {
        return this == UP || this == DOWN;
    }

    /**
     * 判斷是否為多頭趨勢
     */
    public boolean isBullish() {
        return this == UP;
    }

    /**
     * 判斷是否為空頭趨勢
     */
    public boolean isBearish() {
        return this == DOWN;
    }

    /**
     * 獲取相反方向
     */
    public TrendDirection reverse() {
        switch (this) {
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case SIDEWAY:
                return SIDEWAY;
            case UNCLEAR:
                return UNCLEAR;
            default:
                return UNCLEAR;
        }
    }
}
