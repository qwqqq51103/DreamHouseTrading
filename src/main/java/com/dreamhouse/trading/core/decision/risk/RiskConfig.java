package com.dreamhouse.trading.core.decision.risk;

/**
 * 風險管理配置
 * 定義帳戶級風控參數
 */
public class RiskConfig {

    /**
     * 每日最大虧損百分比
     * 當日實現虧損超過此百分比時，停止所有交易
     * 預設 3% = 0.03
     */
    private double maxDailyLossPercent = 0.03;

    /**
     * 單檔最大虧損百分比
     * 單一商品虧損超過此百分比時，強制平倉
     * 預設 2% = 0.02
     */
    private double maxSymbolLossPercent = 0.02;

    /**
     * 單筆交易風險百分比
     * 每筆交易願意承擔的帳戶資金百分比
     * 預設 1% = 0.01
     */
    private double riskPercentPerTrade = 0.01;

    /**
     * 最大同時持倉數量
     * 預設 3 個部位
     */
    private int maxConcurrentPositions = 3;

    /**
     * 最大單一商品部位大小（佔帳戶百分比）
     * 預設 30% = 0.30
     */
    private double maxPositionSizePercent = 0.30;

    /**
     * 最小帳戶餘額百分比
     * 保留的最小現金百分比，避免全部投入
     * 預設 10% = 0.10
     */
    private double minCashReservePercent = 0.10;

    /**
     * 是否啟用每日虧損限制
     */
    private boolean dailyLossLimitEnabled = true;

    /**
     * 是否啟用單檔虧損限制
     */
    private boolean symbolLossLimitEnabled = true;

    /**
     * 觸發每日虧損限制後，是否只平倉不開新倉（true）還是完全停止（false）
     */
    private boolean allowExitOnlyAfterDailyLimit = true;

    /**
     * 最大持倉 K 線數量
     * 當持倉時間超過此 K 線數量時，強制平倉
     * 0 = 不限制
     * 當沖：建議設為當日 K 線數（例如 5 分鐘 K 線，一天約 78 根）
     * 短線：建議設為 300-600 根（1-3 天）
     * 波段：建議設為 2000+ 根（7+ 天）
     */
    private int maxHoldingBars = 0;

    /**
     * 收盤前強制平倉（當沖專用）
     * true = 當日收盤前強制平倉所有持倉
     * false = 允許留倉過夜
     */
    private boolean forceCloseAtEndOfDay = false;

    /**
     * 收盤前幾根 K 線開始強制平倉
     * 預設為收盤前 3 根 K 線（避免流動性問題）
     * 例如：5 分鐘 K 線，3 根 = 收盤前 15 分鐘
     */
    private int closeBeforeEndOfDayBars = 3;

    // Getters and Setters

    public double getMaxDailyLossPercent() {
        return maxDailyLossPercent;
    }

    public void setMaxDailyLossPercent(double maxDailyLossPercent) {
        if (maxDailyLossPercent <= 0.0 || maxDailyLossPercent > 1.0) {
            throw new IllegalArgumentException("Max daily loss percent must be between 0.0 and 1.0");
        }
        this.maxDailyLossPercent = maxDailyLossPercent;
    }

    public double getMaxSymbolLossPercent() {
        return maxSymbolLossPercent;
    }

    public void setMaxSymbolLossPercent(double maxSymbolLossPercent) {
        if (maxSymbolLossPercent <= 0.0 || maxSymbolLossPercent > 1.0) {
            throw new IllegalArgumentException("Max symbol loss percent must be between 0.0 and 1.0");
        }
        this.maxSymbolLossPercent = maxSymbolLossPercent;
    }

    public double getRiskPercentPerTrade() {
        return riskPercentPerTrade;
    }

    public void setRiskPercentPerTrade(double riskPercentPerTrade) {
        if (riskPercentPerTrade <= 0.0 || riskPercentPerTrade > 0.1) {
            throw new IllegalArgumentException("Risk percent per trade must be between 0.0 and 0.1 (10%)");
        }
        this.riskPercentPerTrade = riskPercentPerTrade;
    }

    public int getMaxConcurrentPositions() {
        return maxConcurrentPositions;
    }

    public void setMaxConcurrentPositions(int maxConcurrentPositions) {
        if (maxConcurrentPositions < 1) {
            throw new IllegalArgumentException("Max concurrent positions must be at least 1");
        }
        this.maxConcurrentPositions = maxConcurrentPositions;
    }

    public double getMaxPositionSizePercent() {
        return maxPositionSizePercent;
    }

    public void setMaxPositionSizePercent(double maxPositionSizePercent) {
        if (maxPositionSizePercent <= 0.0 || maxPositionSizePercent > 1.0) {
            throw new IllegalArgumentException("Max position size percent must be between 0.0 and 1.0");
        }
        this.maxPositionSizePercent = maxPositionSizePercent;
    }

    public double getMinCashReservePercent() {
        return minCashReservePercent;
    }

    public void setMinCashReservePercent(double minCashReservePercent) {
        if (minCashReservePercent < 0.0 || minCashReservePercent > 0.5) {
            throw new IllegalArgumentException("Min cash reserve percent must be between 0.0 and 0.5");
        }
        this.minCashReservePercent = minCashReservePercent;
    }

    public boolean isDailyLossLimitEnabled() {
        return dailyLossLimitEnabled;
    }

    public void setDailyLossLimitEnabled(boolean dailyLossLimitEnabled) {
        this.dailyLossLimitEnabled = dailyLossLimitEnabled;
    }

    public boolean isSymbolLossLimitEnabled() {
        return symbolLossLimitEnabled;
    }

    public void setSymbolLossLimitEnabled(boolean symbolLossLimitEnabled) {
        this.symbolLossLimitEnabled = symbolLossLimitEnabled;
    }

    public boolean isAllowExitOnlyAfterDailyLimit() {
        return allowExitOnlyAfterDailyLimit;
    }

    public void setAllowExitOnlyAfterDailyLimit(boolean allowExitOnlyAfterDailyLimit) {
        this.allowExitOnlyAfterDailyLimit = allowExitOnlyAfterDailyLimit;
    }

    public int getMaxHoldingBars() {
        return maxHoldingBars;
    }

    public void setMaxHoldingBars(int maxHoldingBars) {
        if (maxHoldingBars < 0) {
            throw new IllegalArgumentException("Max holding bars must be non-negative (0 = unlimited)");
        }
        this.maxHoldingBars = maxHoldingBars;
    }

    public boolean isForceCloseAtEndOfDay() {
        return forceCloseAtEndOfDay;
    }

    public void setForceCloseAtEndOfDay(boolean forceCloseAtEndOfDay) {
        this.forceCloseAtEndOfDay = forceCloseAtEndOfDay;
    }

    public int getCloseBeforeEndOfDayBars() {
        return closeBeforeEndOfDayBars;
    }

    public void setCloseBeforeEndOfDayBars(int closeBeforeEndOfDayBars) {
        if (closeBeforeEndOfDayBars < 0) {
            throw new IllegalArgumentException("Close before end of day bars must be non-negative");
        }
        this.closeBeforeEndOfDayBars = closeBeforeEndOfDayBars;
    }

    @Override
    public String toString() {
        return String.format("RiskConfig[dailyLoss=%.1f%%, symbolLoss=%.1f%%, riskPerTrade=%.1f%%, maxPositions=%d]",
                maxDailyLossPercent * 100, maxSymbolLossPercent * 100,
                riskPercentPerTrade * 100, maxConcurrentPositions);
    }
}
