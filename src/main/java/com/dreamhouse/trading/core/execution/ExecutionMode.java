package com.dreamhouse.trading.core.execution;

/**
 * 執行模式
 * 定義交易執行的環境模式
 */
public enum ExecutionMode {

    /**
     * 回測模式
     * 使用歷史數據模擬交易
     */
    BACKTEST("回測模式", "BT"),

    /**
     * 模擬盤模式
     * 使用即時數據但不實際下單
     */
    PAPER_TRADING("模擬盤", "PAPER"),

    /**
     * 實盤模式
     * 真實交易環境
     */
    LIVE_TRADING("實盤交易", "LIVE"),

    /**
     * 乾跑模式
     * 測試策略邏輯但不執行交易
     */
    DRY_RUN("乾跑模式", "DRY");

    private final String displayName;
    private final String shortCode;

    ExecutionMode(String displayName, String shortCode) {
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
     * 是否為真實交易模式
     */
    public boolean isLiveTrading() {
        return this == LIVE_TRADING;
    }

    /**
     * 是否為模擬模式（不實際下單）
     */
    public boolean isSimulation() {
        return this == BACKTEST || this == PAPER_TRADING || this == DRY_RUN;
    }

    /**
     * 是否允許執行交易
     */
    public boolean canExecuteTrades() {
        return this != DRY_RUN;
    }

    @Override
    public String toString() {
        return displayName + " (" + shortCode + ")";
    }
}
