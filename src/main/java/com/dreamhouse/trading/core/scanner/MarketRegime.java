package com.dreamhouse.trading.core.scanner;

public enum MarketRegime {
    TREND_UP("趨勢偏多"),
    RANGE("震盪"),
    WEAK("弱勢"),
    DATA_MISSING("資料不足");

    private final String displayName;

    MarketRegime(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
