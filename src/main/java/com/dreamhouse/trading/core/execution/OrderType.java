package com.dreamhouse.trading.core.execution;

/**
 * 訂單類型
 * 定義各種交易訂單類型
 */
public enum OrderType {

    /**
     * 市價單
     * 以當前市價立即成交
     */
    MARKET("市價單", "MKT"),

    /**
     * 限價單
     * 指定價格成交
     */
    LIMIT("限價單", "LMT"),

    /**
     * 停損單
     * 價格觸及停損價時以市價出場
     */
    STOP("停損單", "STP"),

    /**
     * 停損限價單
     * 價格觸及停損價時以限價出場
     */
    STOP_LIMIT("停損限價單", "STP_LMT"),

    /**
     * 移動停損單
     * 跟隨價格移動的停損單
     */
    TRAILING_STOP("移動停損單", "TSL"),

    /**
     * 條件單
     * 滿足特定條件時觸發
     */
    CONDITIONAL("條件單", "COND");

    private final String displayName;
    private final String shortCode;

    OrderType(String displayName, String shortCode) {
        this.displayName = displayName;
        this.shortCode = shortCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    /**
     * 是否為市價單類型（立即成交）
     */
    public boolean isMarketOrder() {
        return this == MARKET || this == STOP || this == TRAILING_STOP;
    }

    /**
     * 是否為限價單類型（需要指定價格）
     */
    public boolean isLimitOrder() {
        return this == LIMIT || this == STOP_LIMIT;
    }

    /**
     * 是否為停損單類型
     */
    public boolean isStopOrder() {
        return this == STOP || this == STOP_LIMIT || this == TRAILING_STOP;
    }

    @Override
    public String toString() {
        return displayName + " (" + shortCode + ")";
    }
}
