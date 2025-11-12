package com.dreamhouse.trading.core.decision.signal;

import com.dreamhouse.trading.core.Timeframe;

/**
 * 策略信號介面
 * 所有支援多週期決策系統的策略都必須實作此介面
 *
 * 統一的信號輸出格式，用於 VotingEngine 進行多策略投票
 */
public interface IStrategySignal {

    /**
     * 獲取策略名稱
     * @return 策略唯一識別名稱
     */
    String getStrategyName();

    /**
     * 獲取策略運行的時間週期
     * @return 時間週期（M1, M5, M15, M30, H1, D1, W1）
     */
    Timeframe getTimeframe();

    /**
     * 獲取當前信號類型
     * @return 信號類型（LONG, SHORT, EXIT, HOLD, NO_TRADE, REVERSE）
     */
    SignalType getSignal();

    /**
     * 獲取信號信心度
     * @return 0.0 ~ 1.0，表示策略對此信號的信心程度
     *         1.0 = 非常確定，0.0 = 完全不確定
     */
    double getConfidence();

    /**
     * 獲取策略權重
     * @return 0.0 ~ 1.0，表示此策略在投票系統中的權重
     *         權重由策略的歷史表現、適用市場環境等因素決定
     */
    double getWeight();

    /**
     * 獲取信號產生的時間戳記
     * @return Unix timestamp (milliseconds)
     */
    long getTimestamp();

    /**
     * 獲取信號的詳細原因或說明（選用）
     * @return 說明文字，用於日誌記錄和除錯
     */
    default String getReason() {
        return "";
    }

    /**
     * 獲取建議的停損價格（選用）
     * @return 停損價格，null 表示使用預設停損設定
     */
    default Double getSuggestedStopLoss() {
        return null;
    }

    /**
     * 獲取建議的停利價格（選用）
     * @return 停利價格，null 表示使用預設停利設定
     */
    default Double getSuggestedTakeProfit() {
        return null;
    }
}
