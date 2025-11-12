package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;

/**
 * 波段交易策略（中長期持倉）
 *
 * 特點：
 * - 持倉 1-4 週
 * - 跟隨週線和日線大趨勢
 * - 使用長週期指標（1小時-日線）
 * - 大止損、大獲利
 */
public class PositionTradingStrategy extends MultiTimeframeDecisionStrategy {

    public PositionTradingStrategy() {
        this(createPositionTradingConfig());
    }

    public PositionTradingStrategy(DecisionConfig config) {
        super(config);
        setupPositionTradingStrategies();
    }

    /**
     * 創建波段交易配置
     */
    public static DecisionConfig createPositionTradingConfig() {
        DecisionConfig config = DecisionConfig.createConservative();

        // 設定時間週期
        config.setMainLoopTimeframe(Timeframe.H1);      // 1 小時主週期
        config.setRiskMonitorTimeframe(Timeframe.M15);  // 15 分鐘風控週期

        // 啟用所有過濾（波段看大趨勢）
        config.setRegimeDetectionEnabled(true);   // 看週線環境
        config.setTrendAnalysisEnabled(true);     // 看日線趨勢

        // 調整投票配置（嚴格進場，寬鬆出場）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.7);   // 高門檻，精選機會
        votingConfig.setShortEntryThreshold(0.7);
        votingConfig.setExitThreshold(0.4);        // 低門檻，讓利潤奔跑
        votingConfig.setMinVotingStrategies(1);    // 至少 1 個策略確認

        // 調整風險配置（寬鬆風控，容忍回撤）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.05);        // 5% 日損限制
        riskConfig.setMaxPositionSizePercent(0.5);      // 單筆最多 50%

        // 波段持倉時間限制（1-4 週，至少 7 天）
        riskConfig.setForceCloseAtEndOfDay(false);      // 允許留倉過夜
        riskConfig.setMaxHoldingBars(672);              // 最多持倉 672 根 K 線（1小時週期 × 4週）

        // 注意：以下參數尚未在 RiskConfig 中實作
        // riskConfig.setMaxPositions(5);
        // riskConfig.setStopLossPercent(0.05);
        // riskConfig.setTakeProfitPercent(0.15);
        // riskConfig.setTrailingStopPercent(0.03);

        // 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        return config;
    }

    /**
     * 設置波段策略的子策略
     */
    private void setupPositionTradingStrategies() {
        // 添加長週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(21);                   // 長週期（21 根 K 線）
        rsi.setOversoldThreshold(25.0);         // 更嚴格的超賣
        rsi.setOverboughtThreshold(75.0);       // 更嚴格的超買
        rsi.setWeight(1.0);
        addStrategy(rsi);

        // TODO: 可添加 MA 策略（50/200 經典系統）
        // SignalMAStrategy ma = new SignalMAStrategy();
        // ma.setFastPeriod(50);
        // ma.setSlowPeriod(200);
        // ma.setWeight(0.7);
        // addStrategy(ma);

        System.out.println("[PositionTradingStrategy] ========== 波段交易策略初始化 ==========");
        System.out.println("[PositionTradingStrategy] 主要週期：" +
            getDecisionConfig().getMainLoopTimeframe().getLabel());
        System.out.println("[PositionTradingStrategy] 進場閾值：" +
            getDecisionConfig().getVotingConfig().getLongEntryThreshold());
        System.out.println("[PositionTradingStrategy] 出場閾值：" +
            getDecisionConfig().getVotingConfig().getExitThreshold());
        System.out.println("[PositionTradingStrategy] 日損限制：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxDailyLossPercent() * 100));
        System.out.println("[PositionTradingStrategy] 最大倉位：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxPositionSizePercent() * 100));
        System.out.println("[PositionTradingStrategy] ===========================================");
    }

    @Override
    public String toString() {
        return "波段交易策略 (PositionTrading)";
    }

    /**
     * 獲取策略描述
     */
    public String getDescription() {
        return "波段交易策略：持倉1-4週，跟隨週線和日線大趨勢，大止損大獲利，適合長期投資者";
    }

    /**
     * 獲取建議的交易時段
     */
    public String getTradingHours() {
        return "每週看盤2-3次即可";
    }

    /**
     * 獲取風險等級
     */
    public String getRiskLevel() {
        return "中低";
    }

    /**
     * 獲取預期持倉時間
     */
    public String getExpectedHoldingPeriod() {
        return "1-4 週";
    }

    /**
     * 獲取適合對象
     */
    public String getSuitableFor() {
        return "長期投資者、每週看盤2-3次、追求大趨勢行情、容忍較大回撤";
    }
}
