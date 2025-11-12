package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * RSI 信號策略
 *
 * 基於 RSI 指標生成交易信號，支援多週期決策系統
 */
public class SignalRSIStrategy extends DecisionBaseStrategy {

    private int rsiPeriod = 14;
    private double overboughtThreshold = 70.0;
    private double oversoldThreshold = 30.0;

    private RSIIndicator rsi;
    private boolean initialized = false;

    public SignalRSIStrategy() {
        super("SignalRSI", "RSI 信號策略");
    }

    @Override
    public void initialize(BarSeries barSeries) {
        super.initialize(barSeries);
        // 初始化 RSI 指標
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        this.rsi = new RSIIndicator(closePrice, rsiPeriod);
        this.initialized = true;

        log("RSI 信號策略初始化完成");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        if (!initialized || barIndex < rsiPeriod) {
            updateSignal(SignalType.NO_TRADE, 0.0, "數據不足");
            return;
        }

        // 生成信號
        IStrategySignal signal = generateSignal(barIndex, bar);

        // 記錄信號（可選）
        if (signal.getSignal() != SignalType.NO_TRADE && signal.getSignal() != SignalType.HOLD) {
            log(String.format("[K線#%d] %s", barIndex, signal.toString()));
        }

        // 注意：這裡只生成信號，不執行實際交易
        // 實際交易由 MultiTimeframeDecisionStrategy 根據投票結果決定
    }

    @Override
    protected IStrategySignal generateSignal(int barIndex, Bar bar) {
        double currentRSI = rsi.getValue(barIndex).doubleValue();
        double currentPrice = bar.getClosePrice().doubleValue();

        SignalType signal;
        double confidence;
        String reason;

        // 判斷信號
        if (currentRSI >= overboughtThreshold) {
            // 超買，看空信號
            signal = SignalType.SHORT;
            confidence = calculateConfidence(currentRSI, overboughtThreshold, 100.0);
            reason = String.format("RSI超買 %.1f >= %.1f", currentRSI, overboughtThreshold);
        } else if (currentRSI <= oversoldThreshold) {
            // 超賣，看多信號
            signal = SignalType.LONG;
            confidence = calculateConfidence(currentRSI, 0.0, oversoldThreshold);
            reason = String.format("RSI超賣 %.1f <= %.1f", currentRSI, oversoldThreshold);
        } else if (currentRSI > 50) {
            // 偏強但未超買
            signal = SignalType.HOLD;
            confidence = 0.3;
            reason = String.format("RSI偏強 %.1f", currentRSI);
        } else if (currentRSI < 50) {
            // 偏弱但未超賣
            signal = SignalType.HOLD;
            confidence = 0.3;
            reason = String.format("RSI偏弱 %.1f", currentRSI);
        } else {
            signal = SignalType.NO_TRADE;
            confidence = 0.0;
            reason = String.format("RSI中性 %.1f", currentRSI);
        }

        // 更新內部狀態
        updateSignal(signal, confidence, reason);

        // 返回信號物件
        return buildSignal(signal, confidence, reason);
    }

    /**
     * 計算信心度
     * 根據 RSI 值距離閾值的距離來計算
     */
    private double calculateConfidence(double rsiValue, double minThreshold, double maxThreshold) {
        double range = maxThreshold - minThreshold;
        double distance = Math.abs(rsiValue - ((minThreshold + maxThreshold) / 2.0));
        double normalizedDistance = distance / (range / 2.0);

        // 距離中心越遠，信心度越高
        return Math.min(0.9, normalizedDistance * 0.8 + 0.1);
    }

    @Override
    public void cleanup() {
        log("RSI 信號策略清理");
    }

    // 配置方法

    public void setRsiPeriod(int rsiPeriod) {
        this.rsiPeriod = rsiPeriod;
    }

    public void setOverboughtThreshold(double overboughtThreshold) {
        this.overboughtThreshold = overboughtThreshold;
    }

    public void setOversoldThreshold(double oversoldThreshold) {
        this.oversoldThreshold = oversoldThreshold;
    }
}
