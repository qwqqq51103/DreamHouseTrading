package com.dreamhouse.trading.core.decision.regime;

/**
 * 市場環境類型（週線層）
 * 定義市場的整體趨勢環境
 */
public enum MarketRegime {
    /**
     * 牛市 - 明確上升趨勢，適合做多
     */
    BULL("牛市", "Strong uptrend, favor long positions"),

    /**
     * 熊市 - 明確下降趨勢，適合做空
     */
    BEAR("熊市", "Strong downtrend, favor short positions"),

    /**
     * 中性 - 震盪整理，雙向交易
     */
    NEUTRAL("中性", "Ranging market, both directions possible"),

    /**
     * 禁止交易 - 不適合交易的市場環境（如極端波動、低流動性）
     */
    NO_TRADE("禁止交易", "Unfavorable market conditions, no trading");

    private final String displayName;
    private final String description;

    MarketRegime(String displayName, String description) {
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
     * 判斷是否允許交易
     */
    public boolean isTradeable() {
        return this != NO_TRADE;
    }

    /**
     * 判斷是否為趨勢市場
     */
    public boolean isTrending() {
        return this == BULL || this == BEAR;
    }

    /**
     * 判斷是否為多頭市場
     */
    public boolean isBullish() {
        return this == BULL;
    }

    /**
     * 判斷是否為空頭市場
     */
    public boolean isBearish() {
        return this == BEAR;
    }
}
