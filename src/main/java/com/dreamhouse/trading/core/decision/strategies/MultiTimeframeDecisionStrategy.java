package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.StrategyConfig;
import com.dreamhouse.trading.core.decision.*;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * 多週期決策策略
 *
 * 整合 DecisionEngine 與多個子策略，作為回測系統的介面
 * 這是用戶在回測時選擇的策略
 */
public class MultiTimeframeDecisionStrategy extends BaseStrategy {

    private final DecisionConfig decisionConfig;
    private DecisionEngine decisionEngine;
    private final List<DecisionBaseStrategy> strategies;

    private boolean initialized = false;
    private String currentSymbol = "";

    public MultiTimeframeDecisionStrategy() {
        this(DecisionConfig.createDefault());
    }

    public MultiTimeframeDecisionStrategy(DecisionConfig config) {
        super("多週期決策策略", "整合多策略的多週期決策系統");
        this.decisionConfig = config;
        this.strategies = new ArrayList<>();
    }

    /**
     * 添加子策略
     */
    public void addStrategy(DecisionBaseStrategy strategy) {
        strategies.add(strategy);
        log("已添加策略：" + strategy.getName());
    }

    @Override
    public void initialize(BarSeries barSeries) {
        super.initialize(barSeries);
        log("========== 多週期決策策略初始化 ==========");

        // 1. 初始化所有子策略
        for (DecisionBaseStrategy strategy : strategies) {
            strategy.setEngine(engine);
            strategy.initialize(barSeries);
            log("  - 初始化子策略：" + strategy.getName());
        }

        // 2. 初始化 DecisionEngine
        if (engine != null) {
            decisionEngine = new DecisionEngine(decisionConfig, engine.getPortfolio());
            decisionEngine.setSymbol(barSeries.getName());

            // 設定數據（使用配置中的主迴圈時間週期）
            Timeframe sourceTimeframe = decisionConfig.getMainLoopTimeframe();
            decisionEngine.setBarSeries(barSeries, sourceTimeframe);
            log(String.format("  - 數據週期: %s", sourceTimeframe.getLabel()));
            log("  - DecisionEngine 初始化完成");
        }

        currentSymbol = barSeries.getName();
        initialized = true;

        log("========== 初始化完成，共 " + strategies.size() + " 個策略 ==========");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        if (!initialized) {
            return;
        }

        // 1. 讓所有子策略更新其信號
        for (DecisionBaseStrategy strategy : strategies) {
            strategy.onBar(barIndex, bar);
        }

        // 2. 收集所有策略的信號
        List<IStrategySignal> signals = new ArrayList<>();
        for (DecisionBaseStrategy strategy : strategies) {
            signals.add(strategy);  // DecisionBaseStrategy 實作了 IStrategySignal
        }

        // 3. 將信號傳給 DecisionEngine
        decisionEngine.setStrategySignals(signals);

        // 4. 執行決策流程
        DecisionResult result = decisionEngine.onBar(convertToTa4jBar(bar));

        // 4. 根據決策結果執行交易
        executeDecision(result, bar);
    }

    /**
     * 執行決策結果
     */
    private void executeDecision(DecisionResult result, Bar bar) {
        double currentPrice = bar.getClosePrice().doubleValue();

        if (decisionConfig.isVerboseLogging()) {
            log(result.toString());
        }

        // 根據決策動作執行交易
        switch (result.getAction()) {
            case OPEN_LONG:
                if (!hasPosition(currentSymbol)) {
                    int quantity = result.getSuggestedQuantity() != null ? result.getSuggestedQuantity() : 100;
                    double stopLoss = result.getSuggestedStopLoss();
                    double takeProfit = result.getSuggestedTakeProfit();

                    // 調試輸出
                    System.out.println(String.format("[策略執行] 當前價格: %.2f", currentPrice));
                    System.out.println(String.format("[策略執行] 從DecisionResult獲取: 停損=%.2f, 停利=%.2f", stopLoss, takeProfit));

                    buyWithStops(currentSymbol, quantity, stopLoss, takeProfit,
                            "多週期決策：" + result.getReason());

                    // 通知 DecisionEngine
                    decisionEngine.onPositionOpened(currentSymbol, currentPrice, quantity,
                            stopLoss, takeProfit);

                    log(String.format("★ 開多：數量=%d, 停損=%.2f, 停利=%.2f - %s",
                            quantity, stopLoss, takeProfit, result.getReason()));
                }
                break;

            case OPEN_SHORT:
                if (!hasPosition(currentSymbol)) {
                    // 空單邏輯（簡化版，實際需要融券機制）
                    log("★ 開空信號（暫未實作）：" + result.getReason());
                }
                break;

            case CLOSE_POSITION:
                if (hasPosition(currentSymbol)) {
                    int quantity = getPositionQuantity(currentSymbol);
                    sellWithReason(currentSymbol, quantity, result.getReason());

                    // 通知 DecisionEngine
                    decisionEngine.onPositionClosed();

                    log(String.format("★ 平倉：數量=%d - %s [來源:%s]",
                            quantity, result.getReason(), result.getSource().getDisplayName()));
                }
                break;

            case HOLD:
                // 持有，不做任何事
                break;

            case NO_ACTION:
                // 無動作
                break;
        }

        // 輸出詳細報告（僅在有交易動作時）
        if (result.shouldTrade() && decisionConfig.getLogLevel() >= 2) {
            debug(result.toDetailedString());
        }
    }

    /**
     * 轉換 Bar 格式（已經是 org.ta4j.core.Bar，直接返回）
     */
    private org.ta4j.core.Bar convertToTa4jBar(Bar bar) {
        // bar 已經是 org.ta4j.core.Bar 類型，直接返回
        return bar;
    }

    @Override
    public void cleanup() {
        log("========== 多週期決策策略清理 ==========");

        for (DecisionBaseStrategy strategy : strategies) {
            strategy.cleanup();
        }

        log("========== 清理完成 ==========");
    }

    /**
     * 獲取 DecisionEngine（供外部查詢）
     */
    public DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    /**
     * 獲取所有子策略
     */
    public List<DecisionBaseStrategy> getStrategies() {
        return new ArrayList<>(strategies);
    }

    /**
     * 獲取決策配置
     */
    public DecisionConfig getDecisionConfig() {
        return decisionConfig;
    }
}
