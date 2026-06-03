package com.dreamhouse.trading.core.decision.risk;

import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account and setup-level risk checks.
 */
public class RiskManager {

    private final RiskConfig config;
    private final Portfolio portfolio;
    private final Map<String, Double> dailyPnL;
    private final Map<String, LocalDateTime> stopLossCooldownUntilBySymbol;
    private final List<RiskViolation> violations;

    private String currentDate;
    private double todayStartEquity;
    private boolean dailyLimitHit;
    private boolean allowNewPositions;

    public RiskManager(RiskConfig config, Portfolio portfolio) {
        this.config = config;
        this.portfolio = portfolio;
        this.dailyPnL = new HashMap<>();
        this.stopLossCooldownUntilBySymbol = new HashMap<>();
        this.violations = new ArrayList<>();
        this.currentDate = getTodayString();
        this.todayStartEquity = portfolio.getTotalValue();
        this.dailyLimitHit = false;
        this.allowNewPositions = true;
    }

    public void onNewDay(LocalDate date) {
        String dateStr = date.toString();
        if (!dateStr.equals(currentDate)) {
            currentDate = dateStr;
            todayStartEquity = portfolio.getTotalValue();
            dailyLimitHit = false;
            allowNewPositions = true;
            dailyPnL.put(currentDate, 0.0);
            stopLossCooldownUntilBySymbol.clear();
        }
    }

    public RiskViolation checkDayTradeOpenLong(
            String symbol,
            LocalDateTime signalTime,
            double entryPrice,
            int quantity) {
        LocalDateTime effectiveTime = signalTime != null ? signalTime : LocalDateTime.now();
        if (!effectiveTime.toLocalTime().isBefore(LocalTime.of(13, 25))) {
            return new RiskViolation(
                    RiskViolation.Type.DAY_TRADE_TIME_BLOCK,
                    symbol,
                    effectiveTime.toLocalTime().toSecondOfDay(),
                    LocalTime.of(13, 25).toSecondOfDay(),
                    "Open long is blocked at or after 13:25 for day trade",
                    false);
        }

        if (dailyLimitHit || !allowNewPositions) {
            return new RiskViolation(
                    RiskViolation.Type.DAILY_LOSS_LIMIT,
                    symbol,
                    getTodayPnL(),
                    -todayStartEquity * config.getMaxDailyLossPercent(),
                    "Open long is blocked after daily loss circuit breaker",
                    false);
        }

        LocalDateTime cooldownUntil = stopLossCooldownUntilBySymbol.get(symbol);
        if (cooldownUntil != null && effectiveTime.isBefore(cooldownUntil)) {
            return new RiskViolation(
                    RiskViolation.Type.STOP_LOSS_COOLDOWN,
                    symbol,
                    effectiveTime.toLocalTime().toSecondOfDay(),
                    cooldownUntil.toLocalTime().toSecondOfDay(),
                    "Open long is blocked during stop-loss cooldown",
                    false);
        }

        return checkNewPosition(symbol, entryPrice * quantity);
    }

    public void registerStopLossCooldown(String symbol, LocalDateTime exitTime, int cooldownMinutes) {
        if (symbol == null || symbol.isBlank() || exitTime == null || cooldownMinutes <= 0) {
            return;
        }
        stopLossCooldownUntilBySymbol.put(symbol, exitTime.plusMinutes(cooldownMinutes));
    }

    public RiskViolation checkAccountRisk() {
        RiskViolation violation = null;

        if (config.isDailyLossLimitEnabled()) {
            violation = checkDailyLossLimit();
            if (violation != null) {
                violations.add(violation);
                dailyLimitHit = true;
                allowNewPositions = false;
                return violation;
            }
        }

        if (config.isSymbolLossLimitEnabled()) {
            violation = checkSymbolLossLimit();
            if (violation != null) {
                violations.add(violation);
                return violation;
            }
        }

        return null;
    }

    public RiskViolation checkNewPosition(String symbol, double positionValue) {
        if (!allowNewPositions) {
            return new RiskViolation(
                    RiskViolation.Type.DAILY_LOSS_LIMIT,
                    symbol,
                    0,
                    0,
                    "New positions are disabled after a risk event",
                    false);
        }

        int currentPositions = portfolio.getPositions().size();
        if (currentPositions >= config.getMaxConcurrentPositions()) {
            return new RiskViolation(
                    RiskViolation.Type.MAX_POSITIONS_EXCEEDED,
                    symbol,
                    currentPositions,
                    config.getMaxConcurrentPositions(),
                    String.format("Open positions %d exceed limit %d",
                            currentPositions, config.getMaxConcurrentPositions()),
                    false);
        }

        double accountValue = portfolio.getTotalValue();
        double maxPositionValue = accountValue * config.getMaxPositionSizePercent();
        if (positionValue > maxPositionValue) {
            return new RiskViolation(
                    RiskViolation.Type.POSITION_SIZE_EXCEEDED,
                    symbol,
                    positionValue,
                    maxPositionValue,
                    String.format("Position value %.2f exceeds limit %.2f",
                            positionValue, maxPositionValue),
                    false);
        }

        double requiredReserve = accountValue * config.getMinCashReservePercent();
        double availableCash = portfolio.getCash() - positionValue;
        if (availableCash < requiredReserve) {
            return new RiskViolation(
                    RiskViolation.Type.INSUFFICIENT_CASH,
                    symbol,
                    availableCash,
                    requiredReserve,
                    String.format("Available cash %.2f below reserve %.2f",
                            availableCash, requiredReserve),
                    false);
        }

        return null;
    }

    public RiskViolation checkEntrySetup(
            String symbol,
            double entryPrice,
            double stopLossPrice,
            double takeProfitPrice,
            double volatilityPercent) {
        double risk = Math.abs(entryPrice - stopLossPrice);
        double reward = Math.abs(takeProfitPrice - entryPrice);

        if (risk <= 0.0) {
            return new RiskViolation(
                    RiskViolation.Type.MIN_RISK_REWARD,
                    symbol,
                    0.0,
                    config.getMinRiskRewardRatio(),
                    "Stop loss distance must be positive",
                    false);
        }

        double riskRewardRatio = reward / risk;
        if (riskRewardRatio < config.getMinRiskRewardRatio()) {
            return new RiskViolation(
                    RiskViolation.Type.MIN_RISK_REWARD,
                    symbol,
                    riskRewardRatio,
                    config.getMinRiskRewardRatio(),
                    String.format("Risk/reward %.2f below minimum %.2f",
                            riskRewardRatio, config.getMinRiskRewardRatio()),
                    false);
        }

        if (volatilityPercent < config.getMinVolatilityPercent()) {
            return new RiskViolation(
                    RiskViolation.Type.VOLATILITY_TOO_LOW,
                    symbol,
                    volatilityPercent,
                    config.getMinVolatilityPercent(),
                    String.format("Volatility %.4f below minimum %.4f",
                            volatilityPercent, config.getMinVolatilityPercent()),
                    false);
        }

        if (volatilityPercent > config.getMaxVolatilityPercent()) {
            return new RiskViolation(
                    RiskViolation.Type.VOLATILITY_TOO_HIGH,
                    symbol,
                    volatilityPercent,
                    config.getMaxVolatilityPercent(),
                    String.format("Volatility %.4f above maximum %.4f",
                            volatilityPercent, config.getMaxVolatilityPercent()),
                    false);
        }

        return null;
    }

    public int calculatePositionSize(double entryPrice, double stopLossPrice) {
        double accountEquity = portfolio.getTotalValue();
        double riskAmount = accountEquity * config.getRiskPercentPerTrade();
        double stopDistance = Math.abs(entryPrice - stopLossPrice);

        if (stopDistance <= 0.0) {
            return 100;
        }

        int quantity = (int) (riskAmount / stopDistance);
        double maxPositionValue = accountEquity * config.getMaxPositionSizePercent();
        int maxQuantity = (int) (maxPositionValue / entryPrice);

        quantity = Math.min(quantity, maxQuantity);
        return Math.max(quantity, 100);
    }

    private RiskViolation checkDailyLossLimit() {
        double currentEquity = portfolio.getTotalValue();
        double todayPnLValue = currentEquity - todayStartEquity;
        double todayLossPercent = -todayPnLValue / todayStartEquity;

        if (todayLossPercent >= config.getMaxDailyLossPercent()) {
            return new RiskViolation(
                    RiskViolation.Type.DAILY_LOSS_LIMIT,
                    null,
                    todayLossPercent,
                    config.getMaxDailyLossPercent(),
                    String.format("Daily loss %.2f%% reached %.2f%%",
                            todayLossPercent * 100,
                            config.getMaxDailyLossPercent() * 100),
                    true);
        }

        return null;
    }

    private RiskViolation checkSymbolLossLimit() {
        double accountEquity = portfolio.getTotalValue();
        double maxSymbolLoss = accountEquity * config.getMaxSymbolLossPercent();

        for (Position position : portfolio.getPositions()) {
            double totalPnL = position.getRealizedPnL() + position.getUnrealizedPnL(position.getAveragePrice());
            if (totalPnL < 0 && Math.abs(totalPnL) >= maxSymbolLoss) {
                return new RiskViolation(
                        RiskViolation.Type.SYMBOL_LOSS_LIMIT,
                        position.getSymbol(),
                        Math.abs(totalPnL),
                        maxSymbolLoss,
                        String.format("Symbol loss %.2f reached limit %.2f",
                                Math.abs(totalPnL), maxSymbolLoss),
                        true);
            }
        }

        return null;
    }

    public List<RiskViolation> checkHoldingPeriodViolations(int currentBarIndex, int totalBarsInDay) {
        List<RiskViolation> results = new ArrayList<>();

        if (config.getMaxHoldingBars() == 0 && !config.isForceCloseAtEndOfDay()) {
            return results;
        }

        for (Position position : portfolio.getPositions()) {
            int holdingBars = position.getHoldingBars(currentBarIndex);
            if (holdingBars < 0) {
                continue;
            }

            if (config.getMaxHoldingBars() > 0 && holdingBars >= config.getMaxHoldingBars()) {
                results.add(new RiskViolation(
                        RiskViolation.Type.HOLDING_PERIOD_EXCEEDED,
                        position.getSymbol(),
                        holdingBars,
                        config.getMaxHoldingBars(),
                        String.format("Holding bars %d reached limit %d",
                                holdingBars, config.getMaxHoldingBars()),
                        true));
                continue;
            }

            if (config.isForceCloseAtEndOfDay() && totalBarsInDay > 0) {
                int barIndexInDay = currentBarIndex % totalBarsInDay;
                int barsUntilClose = totalBarsInDay - barIndexInDay;
                if (barsUntilClose <= config.getCloseBeforeEndOfDayBars()) {
                    results.add(new RiskViolation(
                            RiskViolation.Type.END_OF_DAY_CLOSE,
                            position.getSymbol(),
                            barsUntilClose,
                            config.getCloseBeforeEndOfDayBars(),
                            String.format("Bars until close %d <= %d",
                                    barsUntilClose, config.getCloseBeforeEndOfDayBars()),
                            true));
                }
            }
        }

        return results;
    }

    public void updateDailyPnL(double realizedPnL) {
        double currentPnL = dailyPnL.getOrDefault(currentDate, 0.0);
        double updatedPnL = currentPnL + realizedPnL;
        dailyPnL.put(currentDate, updatedPnL);
        if (config.isDailyLossLimitEnabled()
                && todayStartEquity > 0.0
                && -updatedPnL / todayStartEquity >= config.getMaxDailyLossPercent()) {
            dailyLimitHit = true;
            allowNewPositions = false;
        }
    }

    public double getTodayPnL() {
        return dailyPnL.getOrDefault(currentDate, 0.0);
    }

    public RiskConfig getConfig() {
        return config;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public List<RiskViolation> getViolations() {
        return new ArrayList<>(violations);
    }

    public boolean isDailyLimitHit() {
        return dailyLimitHit;
    }

    public boolean isAllowNewPositions() {
        return allowNewPositions;
    }

    private String getTodayString() {
        return LocalDate.now().toString();
    }
}
