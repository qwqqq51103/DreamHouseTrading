package com.dreamhouse.trading.examples;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.BacktestEngine;
import com.dreamhouse.trading.core.backtest.BacktestResult;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy;
import com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;

/**
 * 交易風格配置範例
 *
 * 展示如何配置不同的交易風格：當沖、短線、波段
 */
public class 交易風格配置範例 {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   交易風格配置範例");
        System.out.println("========================================\n");

        // 示範三種交易風格
        demonstrateDayTrading();
        demonstrateSwingTrading();
        demonstratePositionTrading();
    }

    /**
     * 🔸 當沖交易配置
     *
     * 特點：
     * - 快進快出，當日平倉
     * - 小止損、小獲利
     * - 使用短週期指標
     * - 不考慮長期趨勢
     */
    private static void demonstrateDayTrading() {
        System.out.println("【當沖交易配置】");
        System.out.println("================\n");

        // 1. 創建激進配置作為基礎
        DecisionConfig config = DecisionConfig.createAggressive();

        // 2. 調整時間週期（理想情況，目前數據限制可能無法使用）
        config.setMainLoopTimeframe(Timeframe.M1);      // 1 分鐘主週期
        config.setRiskMonitorTimeframe(Timeframe.M1);   // 1 分鐘風控週期

        // 3. 關閉長週期過濾（當沖不看大趨勢）
        config.setRegimeDetectionEnabled(false);  // 不看週線環境
        config.setTrendAnalysisEnabled(false);    // 不看日線趨勢

        // 4. 調整投票配置（快速進出）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.3);   // 較低門檻，容易進場
        votingConfig.setShortEntryThreshold(0.3);
        votingConfig.setExitThreshold(0.3);        // 容易出場
        votingConfig.setMinVotingStrategies(1);    // 單一策略即可

        // 5. 調整風險配置（嚴格風控）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.01);        // 1% 日內止損
        riskConfig.setMaxPositionSizePercent(0.2);      // 單筆最多 20%
        riskConfig.setStopLossPercent(0.01);            // 1% 停損
        riskConfig.setTakeProfitPercent(0.02);          // 2% 停利
        riskConfig.setTrailingStopPercent(0.005);       // 0.5% 移動止損

        // 6. 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        // 7. 創建策略
        MultiTimeframeDecisionStrategy strategy =
            new MultiTimeframeDecisionStrategy(config);

        // 8. 添加短週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(5);                    // 短週期（5 根 K 線）
        rsi.setOversoldThreshold(40.0);         // 較寬鬆的超賣
        rsi.setOverboughtThreshold(60.0);       // 較寬鬆的超買
        rsi.setWeight(1.0);
        strategy.addStrategy(rsi);

        // 9. 輸出配置摘要
        printConfigSummary("當沖交易", config, rsi);

        System.out.println("【適用場景】");
        System.out.println("✅ 全職交易者");
        System.out.println("✅ 有時間盯盤");
        System.out.println("✅ 追求短期獲利");
        System.out.println("✅ 不想留倉過夜\n");
    }

    /**
     * 🔹 短線交易配置（波段交易的一種）
     *
     * 特點：
     * - 持倉 1-5 天
     * - 跟隨日線趨勢
     * - 使用標準週期指標
     * - 中等風險報酬比
     */
    private static void demonstrateSwingTrading() {
        System.out.println("【短線交易配置】");
        System.out.println("================\n");

        // 1. 創建預設配置作為基礎
        DecisionConfig config = DecisionConfig.createDefault();

        // 2. 調整時間週期
        config.setMainLoopTimeframe(Timeframe.M15);     // 15 分鐘主週期
        config.setRiskMonitorTimeframe(Timeframe.M5);   // 5 分鐘風控週期

        // 3. 啟用日線過濾，關閉週線（短線跟隨日線趨勢）
        config.setRegimeDetectionEnabled(false);  // 不看週線環境
        config.setTrendAnalysisEnabled(true);     // 看日線趨勢

        // 4. 調整投票配置（平衡進出）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.5);   // 中等門檻
        votingConfig.setShortEntryThreshold(0.5);
        votingConfig.setExitThreshold(0.5);
        votingConfig.setMinVotingStrategies(2);    // 需要 2 個策略確認

        // 5. 調整風險配置（中等風控）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.03);        // 3% 日損限制
        riskConfig.setMaxPositionSizePercent(0.3);      // 單筆最多 30%
        riskConfig.setStopLossPercent(0.02);            // 2% 停損
        riskConfig.setTakeProfitPercent(0.06);          // 6% 停利
        riskConfig.setTrailingStopPercent(0.01);        // 1% 移動止損

        // 6. 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        // 7. 創建策略
        MultiTimeframeDecisionStrategy strategy =
            new MultiTimeframeDecisionStrategy(config);

        // 8. 添加標準週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(14);                   // 標準週期
        rsi.setOversoldThreshold(30.0);         // 標準超賣
        rsi.setOverboughtThreshold(70.0);       // 標準超買
        rsi.setWeight(0.6);
        strategy.addStrategy(rsi);

        // TODO: 添加 MACD 策略（權重 0.4）
        // SignalMACDStrategy macd = new SignalMACDStrategy();
        // macd.setFastPeriod(12);
        // macd.setSlowPeriod(26);
        // macd.setSignalPeriod(9);
        // macd.setWeight(0.4);
        // strategy.addStrategy(macd);

        // 9. 輸出配置摘要
        printConfigSummary("短線交易", config, rsi);

        System.out.println("【適用場景】");
        System.out.println("✅ 兼職交易者");
        System.out.println("✅ 每天看盤 1-2 次");
        System.out.println("✅ 跟隨日線趨勢");
        System.out.println("✅ 平衡風險與報酬\n");
    }

    /**
     * 🔺 波段交易配置（中長期持倉）
     *
     * 特點：
     * - 持倉 1-4 週
     * - 跟隨週線和日線大趨勢
     * - 使用長週期指標
     * - 大止損、大獲利
     */
    private static void demonstratePositionTrading() {
        System.out.println("【波段交易配置】");
        System.out.println("================\n");

        // 1. 創建保守配置作為基礎
        DecisionConfig config = DecisionConfig.createConservative();

        // 2. 調整時間週期
        config.setMainLoopTimeframe(Timeframe.H1);      // 1 小時主週期
        config.setRiskMonitorTimeframe(Timeframe.M15);  // 15 分鐘風控週期

        // 3. 啟用所有過濾（波段看大趨勢）
        config.setRegimeDetectionEnabled(true);   // 看週線環境
        config.setTrendAnalysisEnabled(true);     // 看日線趨勢

        // 4. 調整投票配置（嚴格進場，寬鬆出場）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.7);   // 高門檻，精選機會
        votingConfig.setShortEntryThreshold(0.7);
        votingConfig.setExitThreshold(0.4);        // 低門檻，讓利潤奔跑
        votingConfig.setMinVotingStrategies(3);    // 需要 3 個策略確認

        // 5. 調整風險配置（寬鬆風控，容忍回撤）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.05);        // 5% 日損限制
        riskConfig.setMaxPositionSizePercent(0.5);      // 單筆最多 50%
        riskConfig.setStopLossPercent(0.05);            // 5% 停損
        riskConfig.setTakeProfitPercent(0.15);          // 15% 停利
        riskConfig.setTrailingStopPercent(0.03);        // 3% 移動止損

        // 6. 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        // 7. 創建策略
        MultiTimeframeDecisionStrategy strategy =
            new MultiTimeframeDecisionStrategy(config);

        // 8. 添加長週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(21);                   // 長週期（21 根 K 線）
        rsi.setOversoldThreshold(25.0);         // 更嚴格的超賣
        rsi.setOverboughtThreshold(75.0);       // 更嚴格的超買
        rsi.setWeight(0.3);
        strategy.addStrategy(rsi);

        // TODO: 添加 MA 策略（權重 0.7）
        // SignalMAStrategy ma = new SignalMAStrategy();
        // ma.setFastPeriod(50);      // 50/200 MA 經典系統
        // ma.setSlowPeriod(200);
        // ma.setWeight(0.7);
        // strategy.addStrategy(ma);

        // 9. 輸出配置摘要
        printConfigSummary("波段交易", config, rsi);

        System.out.println("【適用場景】");
        System.out.println("✅ 長期投資者");
        System.out.println("✅ 每週看盤 2-3 次");
        System.out.println("✅ 追求大趨勢行情");
        System.out.println("✅ 容忍較大回撤\n");
    }

    /**
     * 輸出配置摘要
     */
    private static void printConfigSummary(String styleName,
                                          DecisionConfig config,
                                          SignalRSIStrategy rsi) {
        System.out.println("【配置摘要】");
        System.out.printf("交易風格：%s\n", styleName);
        System.out.printf("主要週期：%s\n", config.getMainLoopTimeframe().getLabel());
        System.out.printf("週線過濾：%s\n", config.isRegimeDetectionEnabled() ? "啟用" : "關閉");
        System.out.printf("日線過濾：%s\n", config.isTrendAnalysisEnabled() ? "啟用" : "關閉");
        System.out.println();

        System.out.println("【投票配置】");
        VotingConfig voting = config.getVotingConfig();
        System.out.printf("做多進場閾值：%.1f\n", voting.getLongEntryThreshold());
        System.out.printf("出場閾值：%.1f\n", voting.getExitThreshold());
        System.out.printf("最少策略數：%d\n", voting.getMinVotingStrategies());
        System.out.println();

        System.out.println("【風險配置】");
        RiskConfig risk = config.getRiskConfig();
        System.out.printf("每日最大虧損：%.1f%%\n", risk.getMaxDailyLossPercent() * 100);
        System.out.printf("最大倉位：%.1f%%\n", risk.getMaxPositionSizePercent() * 100);
        System.out.printf("停損：%.1f%%\n", risk.getStopLossPercent() * 100);
        System.out.printf("停利：%.1f%%\n", risk.getTakeProfitPercent() * 100);
        System.out.println();

        System.out.println("【指標配置】");
        System.out.printf("RSI 週期：%d\n", rsi.getRsiPeriod());
        System.out.printf("RSI 超賣：%.1f\n", rsi.getOversoldThreshold());
        System.out.printf("RSI 超買：%.1f\n", rsi.getOverboughtThreshold());
        System.out.printf("策略權重：%.1f\n", rsi.getWeight());
        System.out.println();
    }

    /**
     * 執行回測的完整範例
     */
    public static BacktestResult runBacktest(MultiTimeframeDecisionStrategy strategy,
                                             String dataFile) {
        // 創建回測引擎
        BacktestEngine engine = new BacktestEngine();
        engine.setInitialCapital(100000.0);     // 10 萬初始資金
        engine.setCommission(0.001425);         // 0.1425% 手續費
        engine.setSlippage(0.0005);             // 0.05% 滑價

        // 添加策略
        engine.addStrategy(strategy);

        // TODO: 載入數據
        // List<Bar> bars = loadDataFromFile(dataFile);
        // engine.setData(bars);

        // 執行回測
        BacktestResult result = engine.runBacktest();

        // 輸出結果
        printBacktestResult(result);

        return result;
    }

    /**
     * 輸出回測結果
     */
    private static void printBacktestResult(BacktestResult result) {
        System.out.println("\n========================================");
        System.out.println("   回測結果");
        System.out.println("========================================\n");

        System.out.printf("初始資金：%.2f\n", result.getInitialCapital());
        System.out.printf("最終資金：%.2f\n", result.getFinalValue());
        System.out.printf("總報酬率：%.2f%%\n", result.getTotalReturn() * 100);
        System.out.printf("年化報酬率：%.2f%%\n", result.getAnnualizedReturn() * 100);
        System.out.println();

        System.out.printf("總交易次數：%d\n", result.getTotalTrades());
        System.out.printf("獲利次數：%d\n", result.getWinningTrades());
        System.out.printf("虧損次數：%d\n", result.getLosingTrades());
        System.out.printf("勝率：%.2f%%\n", result.getWinRate() * 100);
        System.out.println();

        System.out.printf("最大回撤：%.2f%%\n", result.getMaxDrawdown() * 100);
        System.out.printf("夏普比率：%.2f\n", result.getSharpeRatio());
        System.out.printf("獲利因子：%.2f\n", result.getProfitFactor());
        System.out.println();
    }
}
