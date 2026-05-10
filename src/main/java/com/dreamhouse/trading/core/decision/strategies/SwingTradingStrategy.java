package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;

/**
 * 短線交易策略（波段交易的一種）
 *
 * 特點：
 * - 持倉 1-5 天
 * - 跟隨日線趨勢
 * - 使用標準週期指標（15分鐘-1小時）
 * - 中等風險報酬比
 */
public class SwingTradingStrategy extends MultiTimeframeDecisionStrategy {

    public SwingTradingStrategy() {
        this(createSwingTradingConfig());
    }

    public SwingTradingStrategy(DecisionConfig config) {
        super(config);
        setupSwingTradingStrategies();
    }

    /**
     * 創建短線交易配置
     */
    public static DecisionConfig createSwingTradingConfig() {
        DecisionConfig config = DecisionConfig.createDefault();

        // 設定時間週期
        config.setMainLoopTimeframe(Timeframe.M15);     // 15 分鐘主週期
        config.setRiskMonitorTimeframe(Timeframe.M5);   // 5 分鐘風控週期

        // 啟用日線過濾，關閉週線（短線跟隨日線趨勢）
        config.setShortSellingEnabled(false);
        config.setMinRiskRewardRatio(1.4);
        config.setMinEntryAtrPercent(0.005);
        config.setMaxEntryAtrPercent(0.12);
        config.setRegimeDetectionEnabled(false);  // 不看週線環境
        config.setTrendAnalysisEnabled(true);     // 看日線趨勢

        // 調整投票配置（平衡進出）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.5);   // 中等門檻
        votingConfig.setShortEntryThreshold(0.5);
        votingConfig.setExitThreshold(0.5);
        votingConfig.setMinVotingStrategies(1);    // 至少 1 個策略確認

        // 調整風險配置（中等風控）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.03);        // 3% 日損限制
        riskConfig.setMaxPositionSizePercent(0.3);      // 單筆最多 30%

        // 短線持倉時間限制（1-3 天）
        riskConfig.setForceCloseAtEndOfDay(false);      // 允許留倉過夜
        riskConfig.setMaxHoldingBars(288);              // 最多持倉 288 根 K 線（15分鐘週期 × 3天）

        // 注意：以下參數尚未在 RiskConfig 中實作
        // riskConfig.setMaxPositions(3);
        // riskConfig.setStopLossPercent(0.02);
        // riskConfig.setTakeProfitPercent(0.06);
        // riskConfig.setTrailingStopPercent(0.01);

        // 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        return config;
    }

    /**
     * 設置短線策略的子策略
     */
    private void setupSwingTradingStrategies() {
        // 添加標準週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(14);                   // 標準週期
        rsi.setOversoldThreshold(30.0);         // 標準超賣
        rsi.setOverboughtThreshold(70.0);       // 標準超買
        rsi.setWeight(1.0);
        addStrategy(rsi);

        MovingAverageAlignmentStrategy maAlignment = new MovingAverageAlignmentStrategy();
        maAlignment.setPeriods(5, 10, 20);
        maAlignment.setWeight(0.9);
        addStrategy(maAlignment);

        PlatformBreakoutStrategy platformBreakout = new PlatformBreakoutStrategy();
        platformBreakout.setPlatformLookbackBars(16);
        platformBreakout.setMaxPlatformRangePercent(0.07);
        platformBreakout.setWeight(0.85);
        addStrategy(platformBreakout);

        LowVolumeConsolidationBreakoutStrategy lowVolumeBreakout = new LowVolumeConsolidationBreakoutStrategy();
        lowVolumeBreakout.setContractionBars(8);
        lowVolumeBreakout.setBaselineBars(20);
        lowVolumeBreakout.setWeight(0.8);
        addStrategy(lowVolumeBreakout);

        AtrVolatilityFilterStrategy atrFilter = new AtrVolatilityFilterStrategy();
        atrFilter.setMinAtrPercent(0.005);
        atrFilter.setMaxAtrPercent(0.12);
        atrFilter.setWeight(0.4);
        addStrategy(atrFilter);

        // 擴展建議: 添加 MACD 策略以增強交易信號
        // 需要先實現 SignalMACDStrategy 類 (目前專案中尚未實現)
        // 實現後可這樣添加:
        // SignalMACDStrategy macd = new SignalMACDStrategy();
        // macd.setFastPeriod(12);   // 快線週期
        // macd.setSlowPeriod(26);   // 慢線週期
        // macd.setSignalPeriod(9);  // 信號線週期
        // macd.setWeight(0.6);
        // addStrategy(macd);

        System.out.println("[SwingTradingStrategy] ========== 短線交易策略初始化 ==========");
        System.out.println("[SwingTradingStrategy] 主要週期：" +
            getDecisionConfig().getMainLoopTimeframe().getLabel());
        System.out.println("[SwingTradingStrategy] 進場閾值：" +
            getDecisionConfig().getVotingConfig().getLongEntryThreshold());
        System.out.println("[SwingTradingStrategy] 日損限制：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxDailyLossPercent() * 100));
        System.out.println("[SwingTradingStrategy] 最大倉位：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxPositionSizePercent() * 100));
        System.out.println("[SwingTradingStrategy] ===========================================");
    }

    @Override
    public String toString() {
        return "短線交易策略 (SwingTrading)";
    }

    /**
     * 獲取策略描述
     */
    public String getDescription() {
        return "短線交易策略：持倉1-5天，跟隨日線趨勢，中等風險報酬比，適合兼職交易者";
    }

    /**
     * 獲取建議的交易時段
     */
    public String getTradingHours() {
        return "開盤後和收盤前查看即可";
    }

    /**
     * 獲取風險等級
     */
    public String getRiskLevel() {
        return "中";
    }

    /**
     * 獲取預期持倉時間
     */
    public String getExpectedHoldingPeriod() {
        return "1-5 天";
    }

    /**
     * 獲取適合對象
     */
    public String getSuitableFor() {
        return "兼職交易者、每天看盤1-2次、跟隨日線趨勢、平衡風險與報酬";
    }
}
