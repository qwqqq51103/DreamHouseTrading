package com.dreamhouse.trading.core.decision.risk;

/**
 * Risk configuration for decision and execution checks.
 */
public class RiskConfig {

    private double maxDailyLossPercent = 0.03;
    private double maxSymbolLossPercent = 0.02;
    private double riskPercentPerTrade = 0.01;
    private int maxConcurrentPositions = 3;
    private double maxPositionSizePercent = 0.30;
    private double minCashReservePercent = 0.10;
    private boolean dailyLossLimitEnabled = true;
    private boolean symbolLossLimitEnabled = true;
    private boolean allowExitOnlyAfterDailyLimit = true;
    private int maxHoldingBars = 0;
    private boolean forceCloseAtEndOfDay = false;
    private int closeBeforeEndOfDayBars = 3;
    private double minRiskRewardRatio = 1.5;
    private double minVolatilityPercent = 0.0;
    private double maxVolatilityPercent = 1.0;
    private boolean allowShortSelling = false;

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
            throw new IllegalArgumentException("Risk percent per trade must be between 0.0 and 0.1");
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
            throw new IllegalArgumentException("Max holding bars must be non-negative");
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

    public double getMinRiskRewardRatio() {
        return minRiskRewardRatio;
    }

    public void setMinRiskRewardRatio(double minRiskRewardRatio) {
        if (minRiskRewardRatio < 0.0) {
            throw new IllegalArgumentException("Min risk reward ratio must be non-negative");
        }
        this.minRiskRewardRatio = minRiskRewardRatio;
    }

    public double getMinVolatilityPercent() {
        return minVolatilityPercent;
    }

    public void setMinVolatilityPercent(double minVolatilityPercent) {
        if (minVolatilityPercent < 0.0 || minVolatilityPercent > 1.0) {
            throw new IllegalArgumentException("Min volatility percent must be between 0.0 and 1.0");
        }
        this.minVolatilityPercent = minVolatilityPercent;
    }

    public double getMaxVolatilityPercent() {
        return maxVolatilityPercent;
    }

    public void setMaxVolatilityPercent(double maxVolatilityPercent) {
        if (maxVolatilityPercent < 0.0 || maxVolatilityPercent > 1.0) {
            throw new IllegalArgumentException("Max volatility percent must be between 0.0 and 1.0");
        }
        this.maxVolatilityPercent = maxVolatilityPercent;
    }

    public boolean isAllowShortSelling() {
        return allowShortSelling;
    }

    public void setAllowShortSelling(boolean allowShortSelling) {
        this.allowShortSelling = allowShortSelling;
    }

    @Override
    public String toString() {
        return String.format(
                "RiskConfig[dailyLoss=%.1f%%, symbolLoss=%.1f%%, riskPerTrade=%.1f%%, maxPositions=%d, minRR=%.2f, volRange=%.2f%%-%.2f%%, longOnly=%s]",
                maxDailyLossPercent * 100,
                maxSymbolLossPercent * 100,
                riskPercentPerTrade * 100,
                maxConcurrentPositions,
                minRiskRewardRatio,
                minVolatilityPercent * 100,
                maxVolatilityPercent * 100,
                !allowShortSelling);
    }
}
