package com.dreamhouse.trading.core.execution;

import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 執行引擎
 * 統一管理交易執行，支援回測與實盤
 */
public class ExecutionEngine {

    private final ExecutionMode mode;
    private final Portfolio portfolio;
    private final double commissionRate;
    private final AtomicLong orderIdCounter;
    private final AtomicLong tradeIdCounter;
    private final Map<String, ExecutionResult> executionHistory;
    private final Map<String, Double> activeStopLosses;
    private final Map<String, Double> activeTakeProfits;
    private final Map<String, String> activeTradeIds;

    /**
     * 建構子
     *
     * @param mode 執行模式
     * @param portfolio 投資組合
     * @param commissionRate 手續費率
     */
    public ExecutionEngine(ExecutionMode mode, Portfolio portfolio, double commissionRate) {
        this.mode = mode;
        this.portfolio = portfolio;
        this.commissionRate = commissionRate;
        this.orderIdCounter = new AtomicLong(1);
        this.tradeIdCounter = new AtomicLong(1);
        this.executionHistory = new HashMap<>();
        this.activeStopLosses = new HashMap<>();
        this.activeTakeProfits = new HashMap<>();
        this.activeTradeIds = new HashMap<>();
    }

    /**
     * 開倉（買入）
     *
     * @param symbol 商品代碼
     * @param quantity 數量
     * @param price 價格
     * @return 執行結果
     */
    public ExecutionResult openPosition(String symbol, int quantity, double price) {
        return openPosition(symbol, quantity, price, OrderType.MARKET, null, null);
    }

    /**
     * 開倉（買入）- 指定訂單類型
     *
     * @param symbol 商品代碼
     * @param quantity 數量
     * @param price 價格
     * @param orderType 訂單類型
     * @return 執行結果
     */
    public ExecutionResult openPosition(String symbol, int quantity, double price, OrderType orderType) {
        return openPosition(symbol, quantity, price, orderType, null, null);
    }

    public ExecutionResult openPosition(String symbol, int quantity, double price, Double stopLoss, Double takeProfit) {
        return openPosition(symbol, quantity, price, OrderType.MARKET, stopLoss, takeProfit);
    }

    public ExecutionResult openPosition(String symbol, int quantity, double price, OrderType orderType,
                                        Double stopLoss, Double takeProfit) {
        String orderId = generateOrderId();

        // 乾跑模式：不執行交易
        if (mode == ExecutionMode.DRY_RUN) {
            String tradeId = generateTradeId();
            return new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(orderId)
                    .symbol(symbol)
                    .orderType(orderType)
                    .tradeId(tradeId)
                    .requestedQuantity(quantity)
                    .executedQuantity(0)
                    .requestedPrice(price)
                    .executedPrice(0.0)
                    .message("乾跑模式：不執行實際交易")
                    .build();
        }

        try {
            // 檢查資金是否足夠
            double requiredCash = quantity * price * (1 + commissionRate);
            if (portfolio.getCash() < requiredCash) {
                String msg = String.format("資金不足：需要%.2f，可用%.2f", requiredCash, portfolio.getCash());
                return ExecutionResult.failure(orderId, symbol, msg);
            }

            String tradeId = activeTradeIds.computeIfAbsent(symbol, key -> generateTradeId());

            // 執行買入
            if (mode == ExecutionMode.BACKTEST || mode == ExecutionMode.PAPER_TRADING) {
                // 回測模式或模擬盤：直接更新 Portfolio
                portfolio.addPosition(symbol, quantity, price, commissionRate);
                if (stopLoss != null) {
                    activeStopLosses.put(symbol, stopLoss);
                }
                if (takeProfit != null) {
                    activeTakeProfits.put(symbol, takeProfit);
                }
            } else if (mode == ExecutionMode.LIVE_TRADING) {
                // 實盤模式：調用實盤 API（未實作）
                throw new UnsupportedOperationException("實盤交易功能尚未實作");
            }

            // 創建成功結果
            ExecutionResult result = new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(orderId)
                    .symbol(symbol)
                    .orderType(orderType)
                    .requestedQuantity(quantity)
                    .executedQuantity(quantity)
                    .requestedPrice(price)
                    .executedPrice(price)
                    .commission(commissionRate)
                    .executionTime(LocalDateTime.now())
                    .action("開倉")
                    .tradeId(tradeId)
                    .realizedPnL(0.0)
                    .stopLoss(stopLoss)
                    .takeProfit(takeProfit)
                    .message("開倉成功")
                    .build();

            executionHistory.put(orderId, result);
            return result;

        } catch (Exception e) {
            return new ExecutionResult.Builder()
                    .orderId(orderId)
                    .symbol(symbol)
                    .error(e)
                    .build();
        }
    }

    /**
     * 平倉（賣出）
     *
     * @param symbol 商品代碼
     * @param quantity 數量
     * @param price 價格
     * @return 執行結果
     */
    public ExecutionResult closePosition(String symbol, int quantity, double price) {
        return closePosition(symbol, quantity, price, OrderType.MARKET);
    }

    /**
     * 平倉（賣出）- 指定訂單類型
     *
     * @param symbol 商品代碼
     * @param quantity 數量
     * @param price 價格
     * @param orderType 訂單類型
     * @return 執行結果
     */
    public ExecutionResult closePosition(String symbol, int quantity, double price, OrderType orderType) {
        String orderId = generateOrderId();

        // 乾跑模式：不執行交易
        if (mode == ExecutionMode.DRY_RUN) {
            return new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(orderId)
                    .symbol(symbol)
                    .orderType(orderType)
                    .requestedQuantity(quantity)
                    .executedQuantity(0)
                    .requestedPrice(price)
                    .executedPrice(0.0)
                    .message("乾跑模式：不執行實際交易")
                    .build();
        }

        try {
            // 檢查是否有持倉
            Position position = portfolio.getPosition(symbol);
            if (position == null) {
                String msg = String.format("無持倉：%s", symbol);
                return ExecutionResult.failure(orderId, symbol, msg);
            }

            // 檢查數量是否足夠
            if (position.getQuantity() < quantity) {
                String msg = String.format("持倉數量不足：持有%d，嘗試賣出%d", position.getQuantity(), quantity);
                return ExecutionResult.failure(orderId, symbol, msg);
            }

            double realizedPnL = position.calculateProfit(quantity, price, commissionRate);
            Double stopLoss = activeStopLosses.get(symbol);
            Double takeProfit = activeTakeProfits.get(symbol);
            String tradeId = activeTradeIds.getOrDefault(symbol, "");

            // 執行賣出
            if (mode == ExecutionMode.BACKTEST || mode == ExecutionMode.PAPER_TRADING) {
                // 回測模式或模擬盤：直接更新 Portfolio
                portfolio.reducePosition(symbol, quantity, price, commissionRate);
                Position remainingPosition = portfolio.getPosition(symbol);
                if (remainingPosition == null || remainingPosition.getQuantity() <= 0) {
                    activeStopLosses.remove(symbol);
                    activeTakeProfits.remove(symbol);
                    activeTradeIds.remove(symbol);
                }
            } else if (mode == ExecutionMode.LIVE_TRADING) {
                // 實盤模式：調用實盤 API（未實作）
                throw new UnsupportedOperationException("實盤交易功能尚未實作");
            }

            // 創建成功結果
            ExecutionResult result = new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(orderId)
                    .symbol(symbol)
                    .orderType(orderType)
                    .requestedQuantity(quantity)
                    .executedQuantity(quantity)
                    .requestedPrice(price)
                    .executedPrice(price)
                    .commission(commissionRate)
                    .executionTime(LocalDateTime.now())
                    .action("平倉")
                    .tradeId(tradeId)
                    .realizedPnL(realizedPnL)
                    .stopLoss(stopLoss)
                    .takeProfit(takeProfit)
                    .message(String.format("平倉成功，損益 %.2f", realizedPnL))
                    .build();

            executionHistory.put(orderId, result);
            return result;

        } catch (Exception e) {
            return new ExecutionResult.Builder()
                    .orderId(orderId)
                    .symbol(symbol)
                    .error(e)
                    .build();
        }
    }

    /**
     * 強制平倉所有持倉
     *
     * @param price 平倉價格
     * @return 執行結果列表（每個持倉一個結果）
     */
    public Map<String, ExecutionResult> forceCloseAll(double price) {
        Map<String, ExecutionResult> results = new HashMap<>();

        for (Position position : portfolio.getPositions()) {
            String symbol = position.getSymbol();
            int quantity = position.getQuantity();

            ExecutionResult result = closePosition(symbol, quantity, price, OrderType.MARKET);
            results.put(symbol, result);
        }

        return results;
    }

    /**
     * 部分平倉
     *
     * @param symbol 商品代碼
     * @param percentage 平倉百分比（0.0 - 1.0）
     * @param price 價格
     * @return 執行結果
     */
    public ExecutionResult partialClose(String symbol, double percentage, double price) {
        if (percentage <= 0.0 || percentage > 1.0) {
            throw new IllegalArgumentException("平倉百分比必須在 0.0 到 1.0 之間");
        }

        Position position = portfolio.getPosition(symbol);
        if (position == null) {
            String orderId = generateOrderId();
            return ExecutionResult.failure(orderId, symbol, "無持倉");
        }

        int closeQuantity = (int) (position.getQuantity() * percentage);
        if (closeQuantity == 0) {
            closeQuantity = 1;  // 至少平倉 1 股
        }

        return closePosition(symbol, closeQuantity, price, OrderType.MARKET);
    }

    /**
     * 反手（平倉後立即反向開倉）
     *
     * @param symbol 商品代碼
     * @param newQuantity 新持倉數量
     * @param price 價格
     * @return 執行結果陣列 [平倉結果, 開倉結果]
     */
    public ExecutionResult[] reversePosition(String symbol, int newQuantity, double price) {
        ExecutionResult[] results = new ExecutionResult[2];

        // 1. 平倉現有持倉
        Position position = portfolio.getPosition(symbol);
        if (position != null) {
            results[0] = closePosition(symbol, position.getQuantity(), price);
        } else {
            String orderId = generateOrderId();
            results[0] = ExecutionResult.failure(orderId, symbol, "無持倉可平");
        }

        // 2. 開新倉
        results[1] = openPosition(symbol, newQuantity, price);

        return results;
    }

    /**
     * 生成訂單 ID
     */
    private String generateOrderId() {
        return String.format("%s_%s_%06d",
                mode.getShortCode(),
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                orderIdCounter.getAndIncrement());
    }

    private String generateTradeId() {
        return String.format("T%04d", tradeIdCounter.getAndIncrement());
    }

    /**
     * 獲取執行歷史
     */
    public Map<String, ExecutionResult> getExecutionHistory() {
        return new HashMap<>(executionHistory);
    }

    /**
     * 獲取執行結果
     */
    public ExecutionResult getExecutionResult(String orderId) {
        return executionHistory.get(orderId);
    }

    /**
     * 清空執行歷史
     */
    public void clearHistory() {
        executionHistory.clear();
        activeStopLosses.clear();
        activeTakeProfits.clear();
        activeTradeIds.clear();
    }

    // Getters

    public ExecutionMode getMode() {
        return mode;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public double getCommissionRate() {
        return commissionRate;
    }

    /**
     * 獲取統計資訊
     */
    public String getStatistics() {
        long totalOrders = executionHistory.size();
        long successOrders = executionHistory.values().stream()
                .filter(ExecutionResult::isSuccess)
                .count();
        long failedOrders = executionHistory.values().stream()
                .filter(ExecutionResult::isFailed)
                .count();

        return String.format("ExecutionEngine[mode=%s, totalOrders=%d, success=%d, failed=%d]",
                mode.getDisplayName(), totalOrders, successOrders, failedOrders);
    }

    @Override
    public String toString() {
        return String.format("ExecutionEngine[mode=%s, portfolio=%s, commission=%.4f%%]",
                mode.getDisplayName(), portfolio.toString(), commissionRate * 100);
    }
}
