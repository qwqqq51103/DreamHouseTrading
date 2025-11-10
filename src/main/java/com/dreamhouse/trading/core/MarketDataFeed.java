package com.dreamhouse.trading.core;

public interface MarketDataFeed {
    void subscribe(String symbol, MarketDataListener listener);
    void unsubscribe(String symbol, MarketDataListener listener);
    void start();
    void stop();
    boolean isConnected();

    // 新增：暫停/恢復控制
    default void pause() {
        // 預設實現為空，子類可選擇實現
    }

    default void resume() {
        // 預設實現為空，子類可選擇實現
    }

    default boolean isPaused() {
        return false;  // 預設為未暫停
    }

    /**
     * 根據週期載入歷史數據
     * @param symbol 商品代號
     * @param timeframe 時間週期
     */
    default void loadHistoricalData(String symbol, Timeframe timeframe) {
        // 預設實現為空，子類可選擇實現
    }
}

