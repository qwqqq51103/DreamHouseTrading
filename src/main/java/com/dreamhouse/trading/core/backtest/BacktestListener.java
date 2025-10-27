package com.dreamhouse.trading.core.backtest;

/**
 * 回測監聽器接口
 * 用於監聽回測過程中的事件
 */
public interface BacktestListener {
    
    /**
     * 回測開始時調用
     */
    void onBacktestStarted();
    
    /**
     * 進度更新時調用
     * @param progress 進度百分比 (0.0 - 1.0)
     */
    void onProgressUpdate(double progress);
    
    /**
     * 交易執行時調用
     */
    void onTradeExecuted(String symbol, TradeType type, int quantity, double price);
    
    /**
     * 回測完成時調用
     */
    void onBacktestCompleted(BacktestResult result);
    
    /**
     * 回測錯誤時調用
     */
    default void onBacktestError(Exception error) {
        // 默認實現：輸出錯誤信息
        System.err.println("Backtest error: " + error.getMessage());
        error.printStackTrace();
    }
}
