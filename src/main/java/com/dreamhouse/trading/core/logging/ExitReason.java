package com.dreamhouse.trading.core.logging;

/**
 * 出場原因
 * 詳細分類交易結束的原因
 */
public enum ExitReason {

    // ========== 停損相關 ==========

    /**
     * 固定停損觸發
     */
    STOP_LOSS("固定停損", "SL", true),

    /**
     * 移動停損觸發
     */
    TRAILING_STOP("移動停損", "TSL", true),

    /**
     * ATR 動態停損觸發
     */
    ATR_STOP("ATR停損", "ATR", true),

    // ========== 停利相關 ==========

    /**
     * 固定停利觸發
     */
    TAKE_PROFIT("固定停利", "TP", false),

    /**
     * 部分停利
     */
    PARTIAL_PROFIT("部分停利", "PP", false),

    // ========== 時間相關 ==========

    /**
     * 時間停損（持倉超時）
     */
    TIME_STOP("時間停損", "TIME", true),

    /**
     * 收盤前強制平倉（當沖）
     */
    FORCE_CLOSE_EOD("收盤強平", "EOD", true),

    /**
     * 最大持倉時間到期
     */
    MAX_HOLDING_PERIOD("超過持倉時間", "MHP", true),

    // ========== 策略相關 ==========

    /**
     * 策略信號出場
     */
    STRATEGY_SIGNAL("策略信號", "SIGNAL", false),

    /**
     * 趨勢反轉出場
     */
    TREND_REVERSAL("趨勢反轉", "REV", false),

    // ========== 風控相關 ==========

    /**
     * 每日虧損限制觸發
     */
    DAILY_LOSS_LIMIT("每日虧損限制", "DAILY", true),

    /**
     * 單檔虧損限制觸發
     */
    SYMBOL_LOSS_LIMIT("單檔虧損限制", "SYMBOL", true),

    /**
     * 資金不足
     */
    INSUFFICIENT_FUNDS("資金不足", "FUND", true),

    // ========== 手動相關 ==========

    /**
     * 手動平倉
     */
    MANUAL_EXIT("手動平倉", "MANUAL", false),

    /**
     * 其他原因
     */
    OTHER("其他", "OTHER", false);

    private final String displayName;
    private final String shortCode;
    private final boolean isLoss;  // 是否為虧損出場

    ExitReason(String displayName, String shortCode, boolean isLoss) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.isLoss = isLoss;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    public boolean isLoss() {
        return isLoss;
    }

    /**
     * 是否為停損類型
     */
    public boolean isStopLoss() {
        return this == STOP_LOSS ||
               this == TRAILING_STOP ||
               this == ATR_STOP ||
               this == TIME_STOP;
    }

    /**
     * 是否為停利類型
     */
    public boolean isTakeProfit() {
        return this == TAKE_PROFIT ||
               this == PARTIAL_PROFIT;
    }

    /**
     * 是否為強制平倉
     */
    public boolean isForcedExit() {
        return this == FORCE_CLOSE_EOD ||
               this == MAX_HOLDING_PERIOD ||
               this == DAILY_LOSS_LIMIT ||
               this == SYMBOL_LOSS_LIMIT ||
               this == INSUFFICIENT_FUNDS;
    }

    @Override
    public String toString() {
        return displayName + " (" + shortCode + ")";
    }

    /**
     * 從字串解析出場原因
     */
    public static ExitReason fromString(String str) {
        if (str == null || str.isEmpty()) {
            return OTHER;
        }

        // 嘗試從 shortCode 匹配
        for (ExitReason reason : values()) {
            if (reason.shortCode.equalsIgnoreCase(str) ||
                reason.displayName.equalsIgnoreCase(str) ||
                reason.name().equalsIgnoreCase(str)) {
                return reason;
            }
        }

        return OTHER;
    }
}
