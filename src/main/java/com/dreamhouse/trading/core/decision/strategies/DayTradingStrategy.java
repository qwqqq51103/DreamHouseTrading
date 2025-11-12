package com.dreamhouse.trading.core.decision.strategies;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;

/**
 * 當沖交易策略
 *
 * 特點：
 * - 快進快出，當日平倉
 * - 小止損、小獲利
 * - 使用短週期指標（1-5 分鐘）
 * - 不考慮長期趨勢
 * - 嚴格風控
 */
public class DayTradingStrategy extends MultiTimeframeDecisionStrategy {

    public DayTradingStrategy() {
        this(createDayTradingConfig());
    }

    public DayTradingStrategy(DecisionConfig config) {
        super(config);
        setupDayTradingStrategies();
    }

    /**
     * 創建當沖交易配置
     */
    public static DecisionConfig createDayTradingConfig() {
        DecisionConfig config = DecisionConfig.createAggressive();

        // 設定時間週期
        config.setMainLoopTimeframe(Timeframe.M5);  // 5 分鐘主週期（實際可用M1）
        config.setRiskMonitorTimeframe(Timeframe.M1);  // 1 分鐘風控週期

        // 關閉長週期過濾（當沖不看大趨勢）
        config.setRegimeDetectionEnabled(false);  // 不看週線環境
        config.setTrendAnalysisEnabled(false);    // 不看日線趨勢

        // 調整投票配置（快速進出）
        VotingConfig votingConfig = config.getVotingConfig();
        votingConfig.setLongEntryThreshold(0.3);   // 低門檻，容易進場
        votingConfig.setShortEntryThreshold(0.3);
        votingConfig.setExitThreshold(0.3);        // 容易出場
        votingConfig.setMinVotingStrategies(1);    // 單一策略即可

        // 調整風險配置（嚴格風控）
        RiskConfig riskConfig = config.getRiskConfig();
        riskConfig.setMaxDailyLossPercent(0.01);        // 1% 日內止損
        riskConfig.setMaxPositionSizePercent(0.2);      // 單筆最多 20%

        // 當沖持倉時間限制（強制當日平倉）
        riskConfig.setForceCloseAtEndOfDay(true);       // 收盤前強制平倉
        riskConfig.setMaxHoldingBars(78);               // 最多持倉 78 根 K 線（約 6.5 小時）
        riskConfig.setCloseBeforeEndOfDayBars(3);       // 收盤前 3 根 K 線（15 分鐘）開始平倉

        // 注意：以下參數尚未在 RiskConfig 中實作
        // riskConfig.setMaxPositions(2);
        // riskConfig.setStopLossPercent(0.01);
        // riskConfig.setTakeProfitPercent(0.02);
        // riskConfig.setTrailingStopPercent(0.005);

        // 啟用詳細日誌
        config.setVerboseLogging(true);
        config.setLogLevel(2);

        return config;
    }

    /**
     * 設置當沖策略的子策略
     */
    private void setupDayTradingStrategies() {
        // 添加短週期 RSI 策略
        SignalRSIStrategy rsi = new SignalRSIStrategy();
        rsi.setRsiPeriod(5);                    // 短週期（5 根 K 線）
        rsi.setOversoldThreshold(40.0);         // 較寬鬆的超賣
        rsi.setOverboughtThreshold(60.0);       // 較寬鬆的超買
        rsi.setWeight(1.0);
        addStrategy(rsi);

        System.out.println("[DayTradingStrategy] ========== 當沖交易策略初始化 ==========");
        System.out.println("[DayTradingStrategy] 主要週期：" +
            getDecisionConfig().getMainLoopTimeframe().getLabel());
        System.out.println("[DayTradingStrategy] 進場閾值：" +
            getDecisionConfig().getVotingConfig().getLongEntryThreshold());
        System.out.println("[DayTradingStrategy] 日損限制：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxDailyLossPercent() * 100));
        System.out.println("[DayTradingStrategy] 最大倉位：" +
            String.format("%.1f%%", getDecisionConfig().getRiskConfig().getMaxPositionSizePercent() * 100));
        System.out.println("[DayTradingStrategy] ===========================================");
    }

    @Override
    public String toString() {
        return "當沖交易策略 (DayTrading)";
    }

    /**
     * 獲取策略描述
     */
    public String getDescription() {
        return "當沖交易策略：快進快出，當日平倉，小止損小獲利，適合全職交易者";
    }

    /**
     * 獲取建議的交易時段
     */
    public String getTradingHours() {
        return "09:00-13:30（避開開盤和收盤）";
    }

    /**
     * 獲取風險等級
     */
    public String getRiskLevel() {
        return "高";
    }

    /**
     * 獲取預期持倉時間
     */
    public String getExpectedHoldingPeriod() {
        return "數分鐘到數小時，當日平倉";
    }

    /**
     * 獲取適合對象
     */
    public String getSuitableFor() {
        return "全職交易者、有時間盯盤、追求短期獲利、不想留倉過夜";
    }
}
