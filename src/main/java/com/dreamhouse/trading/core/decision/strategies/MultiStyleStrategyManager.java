package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.backtest.BaseStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多策略組合管理器
 *
 * 可以同時運行多個不同風格的策略（當沖、短線、波段）
 * 各策略獨立決策，共享資金池
 */
public class MultiStyleStrategyManager extends BaseStrategy {

    private final List<MultiTimeframeDecisionStrategy> strategies;
    private final Map<String, StrategyAllocation> allocations;
    private final boolean enableAutoBalancing;

    /**
     * 策略資金分配
     */
    public static class StrategyAllocation {
        private final String strategyName;
        private double allocationPercent;  // 資金分配百分比 (0.0-1.0)
        private double usedCapital;        // 已使用資金
        private int tradeCount;            // 交易次數

        public StrategyAllocation(String strategyName, double allocationPercent) {
            this.strategyName = strategyName;
            this.allocationPercent = allocationPercent;
            this.usedCapital = 0.0;
            this.tradeCount = 0;
        }

        public String getStrategyName() { return strategyName; }
        public double getAllocationPercent() { return allocationPercent; }
        public void setAllocationPercent(double percent) { this.allocationPercent = percent; }
        public double getUsedCapital() { return usedCapital; }
        public void setUsedCapital(double capital) { this.usedCapital = capital; }
        public int getTradeCount() { return tradeCount; }
        public void incrementTradeCount() { this.tradeCount++; }
    }

    public MultiStyleStrategyManager() {
        this(true);
    }

    public MultiStyleStrategyManager(boolean enableAutoBalancing) {
        super("多風格策略組合", "同時運行當沖、短線、波段策略");
        this.strategies = new ArrayList<>();
        this.allocations = new HashMap<>();
        this.enableAutoBalancing = enableAutoBalancing;
    }

    /**
     * 添加策略並設定資金分配
     */
    public void addStrategy(MultiTimeframeDecisionStrategy strategy, double allocationPercent) {
        if (allocationPercent <= 0 || allocationPercent > 1.0) {
            throw new IllegalArgumentException("資金分配百分比必須在 0 到 1 之間");
        }

        strategies.add(strategy);
        allocations.put(strategy.toString(), new StrategyAllocation(strategy.toString(), allocationPercent));

        log(String.format("添加策略：%s，資金分配：%.1f%%",
            strategy.toString(), allocationPercent * 100));
    }

    /**
     * 快速創建均衡配置（三種風格平均分配）
     */
    public static MultiStyleStrategyManager createBalancedManager() {
        MultiStyleStrategyManager manager = new MultiStyleStrategyManager();

        // 當沖 33%
        manager.addStrategy(new DayTradingStrategy(), 0.33);

        // 短線 34%
        manager.addStrategy(new SwingTradingStrategy(), 0.34);

        // 波段 33%
        manager.addStrategy(new PositionTradingStrategy(), 0.33);

        return manager;
    }

    /**
     * 快速創建激進配置（偏重當沖和短線）
     */
    public static MultiStyleStrategyManager createAggressiveManager() {
        MultiStyleStrategyManager manager = new MultiStyleStrategyManager();

        // 當沖 50%
        manager.addStrategy(new DayTradingStrategy(), 0.50);

        // 短線 35%
        manager.addStrategy(new SwingTradingStrategy(), 0.35);

        // 波段 15%
        manager.addStrategy(new PositionTradingStrategy(), 0.15);

        return manager;
    }

    /**
     * 快速創建保守配置（偏重波段）
     */
    public static MultiStyleStrategyManager createConservativeManager() {
        MultiStyleStrategyManager manager = new MultiStyleStrategyManager();

        // 當沖 15%
        manager.addStrategy(new DayTradingStrategy(), 0.15);

        // 短線 35%
        manager.addStrategy(new SwingTradingStrategy(), 0.35);

        // 波段 50%
        manager.addStrategy(new PositionTradingStrategy(), 0.50);

        return manager;
    }

    @Override
    public void initialize(BarSeries barSeries) {
        super.initialize(barSeries);

        log("========== 多風格策略組合管理器初始化 ==========");
        log("策略數量：" + strategies.size());
        log("自動平衡：" + (enableAutoBalancing ? "啟用" : "關閉"));

        // 初始化所有子策略
        for (MultiTimeframeDecisionStrategy strategy : strategies) {
            strategy.setEngine(engine);
            strategy.initialize(barSeries);

            String strategyName = strategy.toString();
            StrategyAllocation allocation = allocations.get(strategyName);

            log(String.format("  [%s] 資金分配：%.1f%%",
                strategyName, allocation.getAllocationPercent() * 100));
        }

        log("===============================================");
    }

    @Override
    public void onBar(int barIndex, Bar bar) {
        // 檢查資金分配
        if (enableAutoBalancing && barIndex % 50 == 0) {
            rebalanceAllocations();
        }

        // 讓所有策略獨立執行決策
        for (MultiTimeframeDecisionStrategy strategy : strategies) {
            String strategyName = strategy.toString();
            StrategyAllocation allocation = allocations.get(strategyName);

            // 設定該策略可使用的資金上限
            double totalCapital = getPortfolio().getInitialCash();
            double allocatedCapital = totalCapital * allocation.getAllocationPercent();

            // TODO: 實際應該限制策略的資金使用
            // 目前簡化處理，直接調用策略的 onBar
            strategy.onBar(barIndex, bar);

            // 更新已使用資金（簡化計算）
            // 注意：hasPosition 是 protected 方法，無法直接訪問
            // TODO: 未來可以通過策略接口提供持倉查詢方法
            allocation.incrementTradeCount();
        }

        // 每100根K線輸出一次統計
        if (barIndex % 100 == 0) {
            printStatistics();
        }
    }

    /**
     * 重新平衡資金分配（根據策略表現）
     */
    private void rebalanceAllocations() {
        if (!enableAutoBalancing) {
            return;
        }

        // TODO: 根據策略的實際表現動態調整資金分配
        // 例如：表現好的策略增加分配，表現差的減少分配

        log("[自動平衡] 重新評估策略資金分配");
    }

    /**
     * 輸出統計資訊
     */
    private void printStatistics() {
        log("\n========== 策略組合統計 ==========");

        for (MultiTimeframeDecisionStrategy strategy : strategies) {
            String strategyName = strategy.toString();
            StrategyAllocation allocation = allocations.get(strategyName);

            log(String.format("[%s]", strategyName));
            log(String.format("  資金分配：%.1f%%", allocation.getAllocationPercent() * 100));
            log(String.format("  已使用資金：%.2f", allocation.getUsedCapital()));
            log(String.format("  交易次數：%d", allocation.getTradeCount()));
        }

        log("=====================================\n");
    }

    @Override
    public void cleanup() {
        log("========== 多風格策略組合管理器清理 ==========");

        // 清理所有子策略
        for (MultiTimeframeDecisionStrategy strategy : strategies) {
            strategy.cleanup();
        }

        // 輸出最終統計
        printFinalStatistics();

        log("=============================================");
    }

    /**
     * 輸出最終統計
     */
    private void printFinalStatistics() {
        log("\n========== 最終統計報告 ==========");

        Portfolio portfolio = getPortfolio();
        double totalProfit = portfolio.getTotalValue() - portfolio.getInitialCash();
        double totalReturn = totalProfit / portfolio.getInitialCash();

        log(String.format("初始資金：%.2f", portfolio.getInitialCash()));
        log(String.format("最終資金：%.2f", portfolio.getTotalValue()));
        log(String.format("總獲利：%.2f", totalProfit));
        log(String.format("報酬率：%.2f%%", totalReturn * 100));
        log("");

        log("【各策略表現】");
        for (MultiTimeframeDecisionStrategy strategy : strategies) {
            String strategyName = strategy.toString();
            StrategyAllocation allocation = allocations.get(strategyName);

            log(String.format("[%s]", strategyName));
            log(String.format("  交易次數：%d", allocation.getTradeCount()));
            log(String.format("  資金使用：%.2f (%.1f%%)",
                allocation.getUsedCapital(),
                allocation.getAllocationPercent() * 100));
        }

        log("===================================\n");
    }

    /**
     * 獲取所有策略
     */
    public List<MultiTimeframeDecisionStrategy> getStrategies() {
        return new ArrayList<>(strategies);
    }

    /**
     * 獲取資金分配資訊
     */
    public Map<String, StrategyAllocation> getAllocations() {
        return new HashMap<>(allocations);
    }

    /**
     * 調整特定策略的資金分配
     */
    public void setAllocation(String strategyName, double allocationPercent) {
        StrategyAllocation allocation = allocations.get(strategyName);
        if (allocation != null) {
            allocation.setAllocationPercent(allocationPercent);
            log(String.format("調整 [%s] 資金分配為 %.1f%%",
                strategyName, allocationPercent * 100));
        }
    }
}
