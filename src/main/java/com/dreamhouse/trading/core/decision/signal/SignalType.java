package com.dreamhouse.trading.core.decision.signal;

/**
 * 策略信號類型
 * 定義策略可以輸出的所有信號類型
 */
public enum SignalType {
    /**
     * 做多信號 - 建議買入或持有多頭部位
     */
    LONG("做多", "Buy or hold long position"),

    /**
     * 做空信號 - 建議賣出或持有空頭部位
     */
    SHORT("做空", "Sell or hold short position"),

    /**
     * 出場信號 - 建議平倉當前部位
     */
    EXIT("出場", "Exit current position"),

    /**
     * 持有信號 - 維持當前狀態（有倉持倉，無倉觀望）
     */
    HOLD("持有", "Hold current state"),

    /**
     * 不交易信號 - 不適合進場的市場環境
     */
    NO_TRADE("不交易", "No trading opportunity"),

    /**
     * 反手信號 - 平倉並反向開倉（選用）
     */
    REVERSE("反手", "Reverse position");

    private final String displayName;
    private final String description;

    SignalType(String displayName, String description) {
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
     * 判斷是否為進場信號
     */
    public boolean isEntry() {
        return this == LONG || this == SHORT;
    }

    /**
     * 判斷是否為出場信號
     */
    public boolean isExit() {
        return this == EXIT || this == REVERSE;
    }

    /**
     * 判斷是否為多頭相關信號
     */
    public boolean isBullish() {
        return this == LONG;
    }

    /**
     * 判斷是否為空頭相關信號
     */
    public boolean isBearish() {
        return this == SHORT;
    }
}
