package com.dreamhouse.trading.examples;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.BacktestEngine;
import com.dreamhouse.trading.core.backtest.BacktestResult;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy;
import com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy;
import com.dreamhouse.trading.core.model.Bar;

import java.util.List;

/**
 * 多週期決策系統回測範例
 *
 * 展示如何使用 MultiTimeframeDecisionStrategy 進行回測
 */
public class MultiTimeframeBacktestExample {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   多週期決策系統回測範例");
        System.out.println("========================================\n");

        // 1. 創建決策配置
        DecisionConfig decisionConfig = createDecisionConfig();

        // 2. 創建多週期決策策略
        MultiTimeframeDecisionStrategy strategy = new MultiTimeframeDecisionStrategy(decisionConfig);

        // 3. 添加子策略（可以添加多個）
        SignalRSIStrategy rsiStrategy = new SignalRSIStrategy();
        rsiStrategy.setWeight(1.0);  // 設定權重
        strategy.addStrategy(rsiStrategy);

        // TODO: 可以添加更多策略
        // SignalMACDStrategy macdStrategy = new SignalMACDStrategy();
        // macdStrategy.setWeight(0.8);
        // strategy.addStrategy(macdStrategy);

        // 4. 創建回測引擎並設定參數
        BacktestEngine engine = new BacktestEngine();
        engine.setInitialCapital(100000.0);     // 初始資金 10萬
        engine.setCommission(0.001425);         // 手續費率 0.1425%
        engine.setSlippage(0.0005);             // 滑價 0.05%

        // 5. 添加策略到引擎
        engine.addStrategy(strategy);

        // 6. 載入數據並執行回測
        System.out.println("開始回測...\n");

        try {
            // TODO: 實際使用時需要準備數據並載入
            // List<Bar> bars = loadDataFromFile("sample_data.csv");
            // engine.setData(bars);

            // 執行回測
            BacktestResult result = engine.runBacktest();

            // 7. 顯示回測結果
            printBacktestResult(result);

        } catch (Exception e) {
            System.err.println("回測執行失敗：" + e.getMessage());
            System.err.println("\n注意：需要先載入數據才能執行回測");
            System.err.println("請參考 BacktestEngine.setData() 方法");
            e.printStackTrace();
        }

        System.out.println("\n========================================");
        System.out.println("   回測完成");
        System.out.println("========================================");
    }

    /**
     * 創建決策配置
     */
    private static DecisionConfig createDecisionConfig() {
        DecisionConfig config = DecisionConfig.createDefault();

        // 可以調整配置參數
        config.setVerboseLogging(true);   // 啟用詳細日誌
        config.setLogLevel(2);             // 日誌級別 0-3

        System.out.println("決策配置：");
        System.out.println("  - 詳細日誌：" + config.isVerboseLogging());
        System.out.println("  - 日誌級別：" + config.getLogLevel());
        System.out.println("  - 投票閾值：" + config.getVotingConfig().getLongEntryThreshold());
        System.out.println("  - 風險限制：每日最大虧損 " +
                          (config.getRiskConfig().getMaxDailyLossPercent() * 100) + "%\n");

        return config;
    }

    /**
     * 顯示回測結果
     */
    private static void printBacktestResult(BacktestResult result) {
        System.out.println("\n========================================");
        System.out.println("   回測結果統計");
        System.out.println("========================================\n");

        System.out.println("【資金績效】");
        System.out.println("  初始資金：" + String.format("%.2f", result.getInitialCapital()));
        System.out.println("  最終資金：" + String.format("%.2f", result.getFinalValue()));
        System.out.println("  總報酬率：" + String.format("%.2f%%", result.getTotalReturn() * 100));
        System.out.println("  年化報酬率：" + String.format("%.2f%%", result.getAnnualizedReturn() * 100));
        System.out.println();

        System.out.println("【交易統計】");
        System.out.println("  總交易次數：" + result.getTotalTrades());
        System.out.println("  獲利次數：" + result.getWinningTrades());
        System.out.println("  虧損次數：" + result.getLosingTrades());
        System.out.println("  勝率：" + String.format("%.2f%%", result.getWinRate() * 100));
        System.out.println();

        System.out.println("【風險指標】");
        System.out.println("  最大回撤：" + String.format("%.2f%%", result.getMaxDrawdown() * 100));
        System.out.println("  夏普比率：" + String.format("%.2f", result.getSharpeRatio()));
        System.out.println("  獲利因子：" + String.format("%.2f", result.getProfitFactor()));
        System.out.println();

        if (result.getTotalTrades() > 0) {
            System.out.println("【平均績效】");
            System.out.println("  平均獲利：" + String.format("%.2f", result.getAvgWin()));
            System.out.println("  平均虧損：" + String.format("%.2f", result.getAvgLoss()));
            System.out.println("  波動率：" + String.format("%.2f%%", result.getVolatility() * 100));
            System.out.println();
        }

        // 績效評級
        String rating = evaluatePerformance(result);
        System.out.println("【綜合評級】");
        System.out.println("  " + rating);
        System.out.println();
    }

    /**
     * 評估績效
     */
    private static String evaluatePerformance(BacktestResult result) {
        int score = 0;

        // 報酬率評分
        if (result.getTotalReturn() > 0.3) score += 3;
        else if (result.getTotalReturn() > 0.1) score += 2;
        else if (result.getTotalReturn() > 0) score += 1;

        // 勝率評分
        if (result.getWinRate() > 0.6) score += 3;
        else if (result.getWinRate() > 0.5) score += 2;
        else if (result.getWinRate() > 0.4) score += 1;

        // 回撤評分
        if (result.getMaxDrawdown() < 0.1) score += 3;
        else if (result.getMaxDrawdown() < 0.2) score += 2;
        else if (result.getMaxDrawdown() < 0.3) score += 1;

        // 夏普比率評分
        if (result.getSharpeRatio() > 2.0) score += 3;
        else if (result.getSharpeRatio() > 1.0) score += 2;
        else if (result.getSharpeRatio() > 0.5) score += 1;

        // 評級
        if (score >= 10) return "★★★★★ 優秀";
        else if (score >= 8) return "★★★★☆ 良好";
        else if (score >= 6) return "★★★☆☆ 中等";
        else if (score >= 4) return "★★☆☆☆ 普通";
        else return "★☆☆☆☆ 需改進";
    }

    /**
     * 快速創建範例（使用保守配置）
     */
    public static MultiTimeframeDecisionStrategy createConservativeStrategy() {
        DecisionConfig config = DecisionConfig.createConservative();
        MultiTimeframeDecisionStrategy strategy = new MultiTimeframeDecisionStrategy(config);

        SignalRSIStrategy rsiStrategy = new SignalRSIStrategy();
        rsiStrategy.setWeight(1.0);
        strategy.addStrategy(rsiStrategy);

        return strategy;
    }

    /**
     * 快速創建範例（使用激進配置）
     */
    public static MultiTimeframeDecisionStrategy createAggressiveStrategy() {
        DecisionConfig config = DecisionConfig.createAggressive();
        MultiTimeframeDecisionStrategy strategy = new MultiTimeframeDecisionStrategy(config);

        SignalRSIStrategy rsiStrategy = new SignalRSIStrategy();
        rsiStrategy.setWeight(1.0);
        strategy.addStrategy(rsiStrategy);

        return strategy;
    }
}
