package com.dreamhouse.trading.core.decision.classifier;

/**
 * 交易模式
 * 根據多週期分析結果，分類適合的交易模式
 */
public enum TradeMode {

    /**
     * 當沖交易（Day Trading）
     * 持倉時間：數分鐘到數小時，當日必須平倉
     * 適用條件：高流動性、適度波動、活躍時段
     */
    DAY_TRADE("當沖交易", "DAY", 1),

    /**
     * 短線交易（Short Swing）
     * 持倉時間：1-3 天
     * 適用條件：日線趨勢明確、中等流動性
     */
    SHORT_SWING("短線交易", "SHORT", 2),

    /**
     * 波段交易（Swing Trade）
     * 持倉時間：1-4 週
     * 適用條件：週線多頭、日線趨勢強、有型態支撐
     */
    SWING_TRADE("波段交易", "SWING", 3),

    /**
     * 不建議交易（No Trade）
     * 市場條件不適合交易，建議觀望
     */
    NO_TRADE("不建議交易", "NONE", 0);

    private final String displayName;
    private final String shortCode;
    private final int level;

    TradeMode(String displayName, String shortCode, int level) {
        this.displayName = displayName;
        this.shortCode = shortCode;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortCode() {
        return shortCode;
    }

    public int getLevel() {
        return level;
    }

    /**
     * 是否為有效交易模式（非 NO_TRADE）
     */
    public boolean isValidTradeMode() {
        return this != NO_TRADE;
    }

    /**
     * 獲取建議持倉時間（天數）
     */
    public String getSuggestedHoldingPeriod() {
        switch (this) {
            case DAY_TRADE:
                return "數分鐘到數小時（當日平倉）";
            case SHORT_SWING:
                return "1-3 天";
            case SWING_TRADE:
                return "1-4 週";
            case NO_TRADE:
            default:
                return "不適用";
        }
    }

    /**
     * 獲取風險等級
     */
    public String getRiskLevel() {
        switch (this) {
            case DAY_TRADE:
                return "高";
            case SHORT_SWING:
                return "中";
            case SWING_TRADE:
                return "中低";
            case NO_TRADE:
            default:
                return "無";
        }
    }

    /**
     * 獲取適合對象
     */
    public String getSuitableFor() {
        switch (this) {
            case DAY_TRADE:
                return "全職交易者、有時間盯盤";
            case SHORT_SWING:
                return "兼職交易者、每天看盤 1-2 次";
            case SWING_TRADE:
                return "長期投資者、每週看盤 2-3 次";
            case NO_TRADE:
            default:
                return "無";
        }
    }

    @Override
    public String toString() {
        return displayName + " (" + shortCode + ")";
    }
}
