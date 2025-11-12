package com.dreamhouse.trading.core.decision.risk;

import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;

import java.time.LocalDate;
import java.util.*;

/**
 * 帳戶級風險管理器
 * 負責監控帳戶整體風險，包括每日虧損限制、持倉數限制、部位大小限制等
 */
public class RiskManager {

    private final RiskConfig config;
    private final Portfolio portfolio;

    // 每日統計（key: date string "yyyy-MM-dd"）
    private final Map<String, Double> dailyPnL;
    private String currentDate;
    private double todayStartEquity;

    // 違規記錄
    private final List<RiskViolation> violations;

    // 狀態標誌
    private boolean dailyLimitHit;
    private boolean allowNewPositions;

    public RiskManager(RiskConfig config, Portfolio portfolio) {
        this.config = config;
        this.portfolio = portfolio;
        this.dailyPnL = new HashMap<>();
        this.violations = new ArrayList<>();
        this.currentDate = getTodayString();
        this.todayStartEquity = portfolio.getTotalValue();
        this.dailyLimitHit = false;
        this.allowNewPositions = true;
    }

    /**
     * 每日初始化（在每日開始時調用）
     */
    public void onNewDay(LocalDate date) {
        String dateStr = date.toString();
        if (!dateStr.equals(currentDate)) {
            // 新的一天
            currentDate = dateStr;
            todayStartEquity = portfolio.getTotalValue();
            dailyLimitHit = false;
            allowNewPositions = true;
            dailyPnL.put(currentDate, 0.0);

            System.out.println(String.format("[RiskManager] 新交易日 %s，起始資金: %.2f",
                    currentDate, todayStartEquity));
        }
    }

    /**
     * 檢查帳戶級風險
     * 在每次決策前調用，返回風險違規（如果有）
     *
     * @return 風險違規，null 表示無違規
     */
    public RiskViolation checkAccountRisk() {
        RiskViolation violation = null;

        // 1. 檢查每日虧損限制
        if (config.isDailyLossLimitEnabled()) {
            violation = checkDailyLossLimit();
            if (violation != null) {
                violations.add(violation);
                dailyLimitHit = true;
                if (config.isAllowExitOnlyAfterDailyLimit()) {
                    allowNewPositions = false;
                } else {
                    allowNewPositions = false;  // 完全停止
                }
                return violation;
            }
        }

        // 2. 檢查單檔虧損限制
        if (config.isSymbolLossLimitEnabled()) {
            violation = checkSymbolLossLimit();
            if (violation != null) {
                violations.add(violation);
                return violation;
            }
        }

        return null;
    }

    /**
     * 檢查是否可以開新倉
     *
     * @param symbol     商品代碼
     * @param positionValue 部位價值
     * @return 風險違規，null 表示可以開倉
     */
    public RiskViolation checkNewPosition(String symbol, double positionValue) {
        // 1. 檢查是否允許開新倉
        if (!allowNewPositions) {
            return new RiskViolation(
                    RiskViolation.Type.DAILY_LOSS_LIMIT,
                    symbol,
                    0, 0,
                    "已觸發每日虧損限制，禁止開新倉",
                    false
            );
        }

        // 2. 檢查最大持倉數量
        int currentPositions = portfolio.getPositions().size();
        if (currentPositions >= config.getMaxConcurrentPositions()) {
            return new RiskViolation(
                    RiskViolation.Type.MAX_POSITIONS_EXCEEDED,
                    symbol,
                    currentPositions,
                    config.getMaxConcurrentPositions(),
                    String.format("當前持倉%d個，已達上限%d", currentPositions, config.getMaxConcurrentPositions()),
                    false
            );
        }

        // 3. 檢查單一部位大小
        double accountValue = portfolio.getTotalValue();
        double maxPositionValue = accountValue * config.getMaxPositionSizePercent();
        if (positionValue > maxPositionValue) {
            return new RiskViolation(
                    RiskViolation.Type.POSITION_SIZE_EXCEEDED,
                    symbol,
                    positionValue,
                    maxPositionValue,
                    String.format("部位價值%.2f超過限制%.2f (帳戶%.0f%%)",
                            positionValue, maxPositionValue, config.getMaxPositionSizePercent() * 100),
                    false
            );
        }

        // 4. 檢查現金儲備
        double requiredReserve = accountValue * config.getMinCashReservePercent();
        double availableCash = portfolio.getCash() - positionValue;
        if (availableCash < requiredReserve) {
            return new RiskViolation(
                    RiskViolation.Type.INSUFFICIENT_CASH,
                    symbol,
                    availableCash,
                    requiredReserve,
                    String.format("剩餘現金%.2f < 最低儲備%.2f", availableCash, requiredReserve),
                    false
            );
        }

        return null;  // 無違規，可以開倉
    }

    /**
     * 計算建議的部位大小
     * 根據風險百分比和停損距離計算
     *
     * @param entryPrice     進場價格
     * @param stopLossPrice  停損價格
     * @return 建議的部位數量（股數）
     */
    public int calculatePositionSize(double entryPrice, double stopLossPrice) {
        double accountEquity = portfolio.getTotalValue();
        double riskAmount = accountEquity * config.getRiskPercentPerTrade();
        double stopDistance = Math.abs(entryPrice - stopLossPrice);

        if (stopDistance <= 0.0) {
            System.err.println("[RiskManager] 警告：停損距離 <= 0，使用最小部位");
            return 100;  // 預設最小部位
        }

        int quantity = (int) (riskAmount / stopDistance);

        // 限制最大部位價值
        double maxPositionValue = accountEquity * config.getMaxPositionSizePercent();
        int maxQuantity = (int) (maxPositionValue / entryPrice);

        quantity = Math.min(quantity, maxQuantity);

        // 確保至少有 100 股（或符合最小交易單位）
        quantity = Math.max(quantity, 100);

        return quantity;
    }

    /**
     * 檢查每日虧損限制
     */
    private RiskViolation checkDailyLossLimit() {
        double currentEquity = portfolio.getTotalValue();
        double todayPnL = currentEquity - todayStartEquity;
        double todayLossPercent = -todayPnL / todayStartEquity;

        if (todayLossPercent >= config.getMaxDailyLossPercent()) {
            return new RiskViolation(
                    RiskViolation.Type.DAILY_LOSS_LIMIT,
                    null,
                    todayLossPercent,
                    config.getMaxDailyLossPercent(),
                    String.format("今日虧損%.2f%% >= 限制%.2f%% (虧損%.2f)",
                            todayLossPercent * 100,
                            config.getMaxDailyLossPercent() * 100,
                            todayPnL),
                    true  // 應強制平倉
            );
        }

        return null;
    }

    /**
     * 檢查單檔虧損限制
     */
    private RiskViolation checkSymbolLossLimit() {
        double accountEquity = portfolio.getTotalValue();
        double maxSymbolLoss = accountEquity * config.getMaxSymbolLossPercent();

        for (Position position : portfolio.getPositions()) {
            String symbol = position.getSymbol();

            // 計算當前未實現虧損
            double unrealizedPnL = position.getUnrealizedPnL(position.getAveragePrice());  // 需要當前價格，這裡簡化使用平均價
            double realizedPnL = position.getRealizedPnL();
            double totalPnL = unrealizedPnL + realizedPnL;

            if (totalPnL < 0 && Math.abs(totalPnL) >= maxSymbolLoss) {
                return new RiskViolation(
                        RiskViolation.Type.SYMBOL_LOSS_LIMIT,
                        symbol,
                        Math.abs(totalPnL),
                        maxSymbolLoss,
                        String.format("商品%s虧損%.2f >= 限制%.2f", symbol, Math.abs(totalPnL), maxSymbolLoss),
                        true  // 應強制平倉此商品
                );
            }
        }

        return null;
    }

    /**
     * 檢查持倉時間限制
     *
     * @param currentBarIndex 當前 K 線索引
     * @param totalBarsInDay 一天的總 K 線數（用於判斷是否接近收盤）
     * @return 持倉時間違規列表
     */
    public List<RiskViolation> checkHoldingPeriodViolations(int currentBarIndex, int totalBarsInDay) {
        List<RiskViolation> violations = new ArrayList<>();

        // 如果未啟用持倉時間限制，直接返回
        if (config.getMaxHoldingBars() == 0 && !config.isForceCloseAtEndOfDay()) {
            return violations;
        }

        for (Position position : portfolio.getPositions()) {
            String symbol = position.getSymbol();
            int holdingBars = position.getHoldingBars(currentBarIndex);

            // 如果未設置進場索引，跳過（可能是舊的持倉）
            if (holdingBars < 0) {
                continue;
            }

            // 檢查最大持倉 K 線數量
            if (config.getMaxHoldingBars() > 0 && holdingBars >= config.getMaxHoldingBars()) {
                violations.add(new RiskViolation(
                        RiskViolation.Type.HOLDING_PERIOD_EXCEEDED,
                        symbol,
                        holdingBars,
                        config.getMaxHoldingBars(),
                        String.format("持倉%d根K線 >= 限制%d根", holdingBars, config.getMaxHoldingBars()),
                        true  // 應強制平倉
                ));
                continue;  // 已經違規，不需要再檢查其他條件
            }

            // 檢查收盤前強制平倉（當沖專用）
            if (config.isForceCloseAtEndOfDay() && totalBarsInDay > 0) {
                // 計算當前是第幾根 K 線（當日內的索引）
                int barIndexInDay = currentBarIndex % totalBarsInDay;
                int barsUntilClose = totalBarsInDay - barIndexInDay;

                // 如果接近收盤（剩餘 K 線數 <= closeBeforeEndOfDayBars）
                if (barsUntilClose <= config.getCloseBeforeEndOfDayBars()) {
                    violations.add(new RiskViolation(
                            RiskViolation.Type.END_OF_DAY_CLOSE,
                            symbol,
                            barsUntilClose,
                            config.getCloseBeforeEndOfDayBars(),
                            String.format("收盤前%d根K線，需強制平倉（當沖策略）", barsUntilClose),
                            true  // 應強制平倉
                    ));
                }
            }
        }

        return violations;
    }

    /**
     * 更新每日損益
     */
    public void updateDailyPnL(double realizedPnL) {
        double currentPnL = dailyPnL.getOrDefault(currentDate, 0.0);
        dailyPnL.put(currentDate, currentPnL + realizedPnL);
    }

    /**
     * 獲取今日損益
     */
    public double getTodayPnL() {
        return dailyPnL.getOrDefault(currentDate, 0.0);
    }

    /**
     * 是否觸發每日虧損限制
     */
    public boolean isDailyLimitHit() {
        return dailyLimitHit;
    }

    /**
     * 是否允許開新倉
     */
    public boolean isAllowNewPositions() {
        return allowNewPositions;
    }

    /**
     * 獲取所有違規記錄
     */
    public List<RiskViolation> getViolations() {
        return new ArrayList<>(violations);
    }

    /**
     * 清空違規記錄
     */
    public void clearViolations() {
        violations.clear();
    }

    private String getTodayString() {
        return LocalDate.now().toString();
    }

    public RiskConfig getConfig() {
        return config;
    }
}
