package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.signal.StrategySignal;
import org.ta4j.core.Bar;

/**
 * 支援信號輸出的策略基類
 *
 * 擴展 BaseStrategy，加入信號生成功能
 * 所有支援多週期決策系統的策略都應該繼承此類
 */
public abstract class DecisionBaseStrategy extends BaseStrategy implements IStrategySignal {

    protected Timeframe timeframe;
    protected SignalType currentSignal;
    protected double confidence;
    protected double weight;
    protected String signalReason;
    protected long signalTimestamp;
    protected Double signalStopLoss;
    protected Double signalTakeProfit;

    public DecisionBaseStrategy(String name, String description) {
        super(name, description);
        this.timeframe = Timeframe.M5;  // 預設 5 分鐘
        this.currentSignal = SignalType.NO_TRADE;
        this.confidence = 0.5;
        this.weight = 1.0;
        this.signalReason = "";
        this.signalTimestamp = System.currentTimeMillis();
        this.signalStopLoss = null;
        this.signalTakeProfit = null;
    }

    /**
     * 生成策略信號（需要子類實作）
     *
     * @param barIndex 當前 K 線索引
     * @param bar 當前 K 線
     * @return 策略信號
     */
    protected abstract IStrategySignal generateSignal(int barIndex, Bar bar);

    /**
     * 更新信號狀態
     */
    protected void updateSignal(SignalType signal, double confidence, String reason) {
        updateSignal(signal, confidence, reason, null, null);
    }

    protected void updateSignal(SignalType signal, double confidence, String reason,
                                Double stopLoss, Double takeProfit) {
        this.currentSignal = signal;
        this.confidence = confidence;
        this.signalReason = reason;
        this.signalTimestamp = System.currentTimeMillis();
        this.signalStopLoss = stopLoss;
        this.signalTakeProfit = takeProfit;
    }

    // ===== IStrategySignal 介面實作 =====

    @Override
    public String getStrategyName() {
        return getName();
    }

    @Override
    public Timeframe getTimeframe() {
        return timeframe;
    }

    @Override
    public SignalType getSignal() {
        return currentSignal;
    }

    @Override
    public double getConfidence() {
        return confidence;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public long getTimestamp() {
        return signalTimestamp;
    }

    @Override
    public String getReason() {
        return signalReason;
    }

    @Override
    public Double getSuggestedStopLoss() {
        return signalStopLoss;
    }

    @Override
    public Double getSuggestedTakeProfit() {
        return signalTakeProfit;
    }

    /**
     * 設定策略權重
     */
    public void setWeight(double weight) {
        this.weight = Math.max(0.0, Math.min(1.0, weight));
    }

    /**
     * 設定時間週期
     */
    public void setTimeframe(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    /**
     * 建立 StrategySignal 物件（工具方法）
     */
    protected StrategySignal buildSignal(SignalType signal, double confidence, String reason) {
        return buildSignal(signal, confidence, reason, signalStopLoss, signalTakeProfit);
    }

    protected StrategySignal buildSignal(SignalType signal, double confidence, String reason,
                                         Double stopLoss, Double takeProfit) {
        return new StrategySignal.Builder(getName(), timeframe, signal)
                .confidence(confidence)
                .weight(weight)
                .reason(reason)
                .timestamp(System.currentTimeMillis())
                .suggestedStopLoss(stopLoss)
                .suggestedTakeProfit(takeProfit)
                .build();
    }
}
