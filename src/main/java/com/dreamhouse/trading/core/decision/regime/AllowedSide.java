package com.dreamhouse.trading.core.decision.regime;

/**
 * 允許的交易方向
 * 由市場環境檢測器決定當前可以進行的交易方向
 */
public enum AllowedSide {
    /**
     * 僅允許做多
     */
    LONG_ONLY("僅做多", "Only long positions allowed"),

    /**
     * 僅允許做空
     */
    SHORT_ONLY("僅做空", "Only short positions allowed"),

    /**
     * 雙向皆可
     */
    BOTH("雙向", "Both long and short allowed"),

    /**
     * 禁止交易
     */
    NONE("禁止", "No trading allowed");

    private final String displayName;
    private final String description;

    AllowedSide(String displayName, String description) {
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
     * 判斷是否允許做多
     */
    public boolean allowsLong() {
        return this == LONG_ONLY || this == BOTH;
    }

    /**
     * 判斷是否允許做空
     */
    public boolean allowsShort() {
        return this == SHORT_ONLY || this == BOTH;
    }

    /**
     * 判斷是否允許任何交易
     */
    public boolean allowsAnyTrade() {
        return this != NONE;
    }

    /**
     * 根據市場環境獲取建議的允許方向
     */
    public static AllowedSide fromRegime(MarketRegime regime) {
        switch (regime) {
            case BULL:
                return LONG_ONLY;
            case BEAR:
                return SHORT_ONLY;
            case NEUTRAL:
                return BOTH;
            case NO_TRADE:
                return NONE;
            default:
                return NONE;
        }
    }
}
