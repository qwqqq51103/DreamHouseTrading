package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.regime.RegimeConfig;
import com.dreamhouse.trading.core.decision.trend.TrendConfig;

/**
 * 多週期決策系統總配置
 * 整合所有子模組的配置
 */
public class DecisionConfig {

    /**
     * 主迴圈執行週期
     * 決策引擎的主要運行週期，預設為 M5（5分鐘）
     */
    private Timeframe mainLoopTimeframe = Timeframe.M5;

    /**
     * 高頻風控週期
     * 用於即時監控停損，預設為 M1（1分鐘）
     */
    private Timeframe riskMonitorTimeframe = Timeframe.M1;

    /**
     * 投票引擎配置
     */
    private VotingConfig votingConfig = new VotingConfig();

    /**
     * 風險管理配置
     */
    private RiskConfig riskConfig = new RiskConfig();

    /**
     * 市場環境檢測配置（週線層）
     */
    private RegimeConfig regimeConfig = new RegimeConfig();

    /**
     * 趨勢分析配置（日線層）
     */
    private TrendConfig trendConfig = new TrendConfig();

    /**
     * 是否啟用週線環境檢測
     */
    private boolean regimeDetectionEnabled = true;

    /**
     * 是否啟用日線趨勢分析
     */
    private boolean trendAnalysisEnabled = true;

    /**
     * 是否啟用型態檢測
     */
    private boolean patternDetectionEnabled = false;  // POC 階段預設關閉

    /**
     * 是否啟用多策略投票
     */
    private boolean votingEnabled = true;

    /**
     * 是否啟用帳戶級風控
     */
    private boolean riskManagementEnabled = true;

    /**
     * 日誌詳細程度
     * 0 = 關閉, 1 = 基本, 2 = 詳細, 3 = 除錯
     */
    private int logLevel = 2;

    /**
     * 是否在控制台輸出決策過程
     */
    private boolean verboseLogging = true;

    // Getters and Setters

    public Timeframe getMainLoopTimeframe() {
        return mainLoopTimeframe;
    }

    public void setMainLoopTimeframe(Timeframe mainLoopTimeframe) {
        if (mainLoopTimeframe == null) {
            throw new IllegalArgumentException("Main loop timeframe cannot be null");
        }
        this.mainLoopTimeframe = mainLoopTimeframe;
    }

    public Timeframe getRiskMonitorTimeframe() {
        return riskMonitorTimeframe;
    }

    public void setRiskMonitorTimeframe(Timeframe riskMonitorTimeframe) {
        if (riskMonitorTimeframe == null) {
            throw new IllegalArgumentException("Risk monitor timeframe cannot be null");
        }
        this.riskMonitorTimeframe = riskMonitorTimeframe;
    }

    public VotingConfig getVotingConfig() {
        return votingConfig;
    }

    public void setVotingConfig(VotingConfig votingConfig) {
        if (votingConfig == null) {
            throw new IllegalArgumentException("Voting config cannot be null");
        }
        this.votingConfig = votingConfig;
    }

    public RiskConfig getRiskConfig() {
        return riskConfig;
    }

    public void setRiskConfig(RiskConfig riskConfig) {
        if (riskConfig == null) {
            throw new IllegalArgumentException("Risk config cannot be null");
        }
        this.riskConfig = riskConfig;
    }

    public RegimeConfig getRegimeConfig() {
        return regimeConfig;
    }

    public void setRegimeConfig(RegimeConfig regimeConfig) {
        if (regimeConfig == null) {
            throw new IllegalArgumentException("Regime config cannot be null");
        }
        this.regimeConfig = regimeConfig;
    }

    public TrendConfig getTrendConfig() {
        return trendConfig;
    }

    public void setTrendConfig(TrendConfig trendConfig) {
        if (trendConfig == null) {
            throw new IllegalArgumentException("Trend config cannot be null");
        }
        this.trendConfig = trendConfig;
    }

    public boolean isRegimeDetectionEnabled() {
        return regimeDetectionEnabled;
    }

    public void setRegimeDetectionEnabled(boolean regimeDetectionEnabled) {
        this.regimeDetectionEnabled = regimeDetectionEnabled;
    }

    public boolean isTrendAnalysisEnabled() {
        return trendAnalysisEnabled;
    }

    public void setTrendAnalysisEnabled(boolean trendAnalysisEnabled) {
        this.trendAnalysisEnabled = trendAnalysisEnabled;
    }

    public boolean isPatternDetectionEnabled() {
        return patternDetectionEnabled;
    }

    public void setPatternDetectionEnabled(boolean patternDetectionEnabled) {
        this.patternDetectionEnabled = patternDetectionEnabled;
    }

    public boolean isVotingEnabled() {
        return votingEnabled;
    }

    public void setVotingEnabled(boolean votingEnabled) {
        this.votingEnabled = votingEnabled;
    }

    public boolean isRiskManagementEnabled() {
        return riskManagementEnabled;
    }

    public void setRiskManagementEnabled(boolean riskManagementEnabled) {
        this.riskManagementEnabled = riskManagementEnabled;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        if (logLevel < 0 || logLevel > 3) {
            throw new IllegalArgumentException("Log level must be between 0 and 3");
        }
        this.logLevel = logLevel;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    /**
     * 創建預設配置
     */
    public static DecisionConfig createDefault() {
        return new DecisionConfig();
    }

    /**
     * 創建保守配置（較嚴格的風控）
     */
    public static DecisionConfig createConservative() {
        DecisionConfig config = new DecisionConfig();
        config.getRiskConfig().setRiskPercentPerTrade(0.005);  // 0.5%
        config.getRiskConfig().setMaxDailyLossPercent(0.02);   // 2%
        config.getVotingConfig().setLongEntryThreshold(0.7);   // 提高進場門檻
        config.getVotingConfig().setShortEntryThreshold(0.7);
        config.getVotingConfig().setMinVotingStrategies(3);    // 需要更多策略確認
        return config;
    }

    /**
     * 創建激進配置（較寬鬆的風控，更積極的進場）
     */
    public static DecisionConfig createAggressive() {
        DecisionConfig config = new DecisionConfig();
        config.getRiskConfig().setRiskPercentPerTrade(0.02);   // 2%
        config.getRiskConfig().setMaxDailyLossPercent(0.05);   // 5%
        config.getVotingConfig().setLongEntryThreshold(0.5);   // 降低進場門檻
        config.getVotingConfig().setShortEntryThreshold(0.5);
        config.getVotingConfig().setMinVotingStrategies(1);    // 單一策略即可
        return config;
    }

    @Override
    public String toString() {
        return String.format("DecisionConfig[mainLoop=%s, regimeEnabled=%b, trendEnabled=%b, votingEnabled=%b, riskEnabled=%b]",
                mainLoopTimeframe.getLabel(),
                regimeDetectionEnabled,
                trendAnalysisEnabled,
                votingEnabled,
                riskManagementEnabled);
    }
}
