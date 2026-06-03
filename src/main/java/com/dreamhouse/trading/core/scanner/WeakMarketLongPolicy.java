package com.dreamhouse.trading.core.scanner;

public enum WeakMarketLongPolicy {
    BLOCK_ALL("弱勢盤禁止開多"),
    ALLOW_EXTREME_STRENGTH_ONLY("弱勢盤只允許極強股");

    private final String displayName;

    WeakMarketLongPolicy(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
