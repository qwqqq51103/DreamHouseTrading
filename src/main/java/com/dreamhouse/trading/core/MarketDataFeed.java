package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import java.util.List;

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
     * 根據週期載入歷史數據（使用預設K線數量）
     * @param symbol 商品代號
     * @param timeframe 時間週期
     */
    default void loadHistoricalData(String symbol, Timeframe timeframe) {
        // 預設實現為空，子類可選擇實現
    }

    /**
     * 根據週期和K線數量載入歷史數據
     * @param symbol 商品代號
     * @param timeframe 時間週期
     * @param barCount 要載入的K線數量
     */
    default void loadHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        // 預設調用不帶K線數量的方法
        loadHistoricalData(symbol, timeframe);
    }

    /**
     * 同步獲取歷史K線數據（用於監控服務）
     * 注意：此方法是同步的，會阻塞直到數據返回
     *
     * @param symbol 商品代號
     * @param timeframe 時間週期
     * @param barCount 要獲取的K線數量
     * @return K線列表，必須依時間遞增排序
     */
    default List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " 尚未實作同步歷史K線查詢");
    }
}

