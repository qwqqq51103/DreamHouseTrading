package com.dreamhouse.trading.core.execution;

import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.backtest.Position;
import com.dreamhouse.trading.core.decision.DecisionResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Execution engine for backtest, paper and dry-run workflows.
 */
public class ExecutionEngine {

    private final ExecutionMode mode;
    private final Portfolio portfolio;
    private final double commissionRate;
    private final double sellTaxRate;
    private final AtomicLong orderIdCounter;
    private final Map<String, ExecutionResult> executionHistory;
    private final Map<String, String> activePositionIds;
    private final Map<String, Double> activeStopLosses;
    private final Map<String, Double> activeTakeProfits;
    private final Map<String, Boolean> activeAutoManagedPositions;

    public ExecutionEngine(ExecutionMode mode, Portfolio portfolio, double commissionRate) {
        this(mode, portfolio, commissionRate, 0.0);
    }

    public ExecutionEngine(ExecutionMode mode, Portfolio portfolio, double commissionRate, double sellTaxRate) {
        this.mode = mode;
        this.portfolio = portfolio;
        this.commissionRate = commissionRate;
        this.sellTaxRate = Math.max(0.0, sellTaxRate);
        this.orderIdCounter = new AtomicLong(1);
        this.executionHistory = new HashMap<>();
        this.activePositionIds = new HashMap<>();
        this.activeStopLosses = new HashMap<>();
        this.activeTakeProfits = new HashMap<>();
        this.activeAutoManagedPositions = new HashMap<>();
    }

    public ExecutionResult openPosition(String symbol, int quantity, double price) {
        return openPosition(symbol, quantity, price, OrderType.MARKET);
    }

    public ExecutionResult openPosition(String symbol, int quantity, double price, OrderType orderType) {
        return openPosition(symbol, quantity, price, orderType, null, null, "Open position");
    }

    public ExecutionResult openPosition(String symbol, int quantity, double price, OrderType orderType,
                                        Double stopLoss, Double takeProfit, String reason) {
        Order order = new Order.Builder()
                .orderId(generateOrderId())
                .symbol(symbol)
                .side(OrderSide.BUY)
                .type(orderType)
                .quantity(quantity)
                .requestedPrice(price)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .reason(reason)
                .build();
        return executeOrder(order);
    }

    public ExecutionResult closePosition(String symbol, int quantity, double price) {
        return closePosition(symbol, quantity, price, OrderType.MARKET);
    }

    public ExecutionResult closePosition(String symbol, int quantity, double price, OrderType orderType) {
        return closePosition(symbol, quantity, price, orderType, "Close position");
    }

    public ExecutionResult closePosition(String symbol, int quantity, double price, OrderType orderType, String reason) {
        Order order = new Order.Builder()
                .orderId(generateOrderId())
                .symbol(symbol)
                .side(OrderSide.SELL)
                .type(orderType)
                .quantity(quantity)
                .requestedPrice(price)
                .reason(reason)
                .build();
        return executeOrder(order);
    }

    public ExecutionResult executeDecision(DecisionResult decision, double price) {
        if (decision == null) {
            return rejectedResult("", "", "Decision is null", DecisionResult.DecisionSource.SYSTEM);
        }
        if (!decision.shouldTrade()) {
            return rejectedResult("", decision.getSymbol(), "Decision does not request execution", decision.getDecisionSource());
        }
        if (decision.isShort()) {
            return rejectedResult("", decision.getSymbol(), "Short selling is disabled in v1", decision.getDecisionSource());
        }

        String symbol = decision.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            return rejectedResult("", "", "Decision symbol is required", decision.getDecisionSource());
        }

        if (decision.isEntry()) {
            Integer quantity = decision.getSuggestedQuantity();
            if (quantity == null || quantity <= 0) {
                return rejectedResult("", symbol, "Decision quantity is required for entry", decision.getDecisionSource());
            }
            Order order = new Order.Builder()
                    .orderId(generateOrderId())
                    .symbol(symbol)
                    .side(OrderSide.BUY)
                    .type(decision.getOrderType())
                    .quantity(quantity)
                    .requestedPrice(price)
                    .stopLoss(decision.getSuggestedStopLoss())
                    .takeProfit(decision.getSuggestedTakeProfit())
                    .reason(decision.getReason())
                    .build();
            return executeOrder(order, decision.getDecisionSource() == DecisionResult.DecisionSource.AUTO_MONITOR,
                    decision.getDecisionSource());
        }

        Position position = portfolio.getPosition(symbol);
        if (position == null) {
            return rejectedResult("", symbol, "No position available to close", decision.getDecisionSource());
        }

        int quantity = decision.getSuggestedQuantity() != null
                ? Math.min(decision.getSuggestedQuantity(), position.getQuantity())
                : position.getQuantity();
        Order order = new Order.Builder()
                .orderId(generateOrderId())
                .symbol(symbol)
                .side(OrderSide.SELL)
                .type(decision.getOrderType())
                .quantity(quantity)
                .requestedPrice(price)
                .reason(decision.getReason())
                .build();
        return executeOrder(order, false, decision.getDecisionSource());
    }

    public ExecutionResult executeOrder(Order order) {
        return executeOrder(order, false, DecisionResult.DecisionSource.MANUAL);
    }

    private ExecutionResult executeOrder(Order order, boolean requestedAutoManaged) {
        return executeOrder(order, requestedAutoManaged, DecisionResult.DecisionSource.MANUAL);
    }

    private ExecutionResult executeOrder(
            Order order,
            boolean requestedAutoManaged,
            DecisionResult.DecisionSource decisionSource) {
        if (order.getQuantity() <= 0) {
            return rejectedResult(order.getOrderId(), order.getSymbol(), "Order quantity must be positive", decisionSource);
        }
        boolean autoManaged = requestedAutoManaged || Boolean.TRUE.equals(activeAutoManagedPositions.get(order.getSymbol()));

        if (mode == ExecutionMode.DRY_RUN) {
            ExecutionResult dryRunResult = new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(order.getOrderId())
                    .symbol(order.getSymbol())
                    .orderType(order.getType())
                    .orderSide(order.getSide())
                    .orderStatus(OrderStatus.ACCEPTED)
                    .requestedQuantity(order.getQuantity())
                    .executedQuantity(0)
                    .requestedPrice(order.getRequestedPrice())
                    .executedPrice(0.0)
                    .decisionReason(order.getReason())
                    .decisionSource(decisionSource)
                    .autoManaged(autoManaged)
                    .message("Dry-run accepted")
                    .build();
            executionHistory.put(order.getOrderId(), dryRunResult);
            return dryRunResult;
        }

        try {
            double realizedPnL = 0.0;
            double tax = 0.0;
            String positionId = activePositionIds.getOrDefault(order.getSymbol(), "");
            Double stopLoss = order.getStopLoss();
            Double takeProfit = order.getTakeProfit();
            if (order.getSide() == OrderSide.BUY) {
                double requiredCash = order.getQuantity() * order.getRequestedPrice() * (1 + commissionRate);
                if (portfolio.getCash() < requiredCash) {
                    return rejectedResult(order.getOrderId(), order.getSymbol(),
                            String.format("Required cash %.2f exceeds available cash %.2f",
                                    requiredCash, portfolio.getCash()),
                            decisionSource);
                }
                if (mode == ExecutionMode.LIVE_TRADING) {
                    throw new UnsupportedOperationException("Live trading is not supported");
                }
                portfolio.addPosition(order.getSymbol(), order.getQuantity(), order.getRequestedPrice(), commissionRate);
                positionId = activePositionIds.computeIfAbsent(order.getSymbol(), key -> order.getOrderId());
                if (requestedAutoManaged) {
                    activeAutoManagedPositions.put(order.getSymbol(), true);
                    autoManaged = true;
                }
                if (order.getStopLoss() != null) {
                    activeStopLosses.put(order.getSymbol(), order.getStopLoss());
                }
                if (order.getTakeProfit() != null) {
                    activeTakeProfits.put(order.getSymbol(), order.getTakeProfit());
                }
            } else {
                stopLoss = activeStopLosses.get(order.getSymbol());
                takeProfit = activeTakeProfits.get(order.getSymbol());
                Position position = portfolio.getPosition(order.getSymbol());
                if (position == null) {
                    return rejectedResult(order.getOrderId(), order.getSymbol(), "No position available", decisionSource);
                }
                if (position.getQuantity() < order.getQuantity()) {
                    return rejectedResult(order.getOrderId(), order.getSymbol(),
                            String.format("Requested close quantity %d exceeds held quantity %d",
                                    order.getQuantity(), position.getQuantity()),
                            decisionSource);
                }
                if (mode == ExecutionMode.LIVE_TRADING) {
                    throw new UnsupportedOperationException("Live trading is not supported");
                }
                tax = order.getQuantity() * order.getRequestedPrice() * sellTaxRate;
                realizedPnL = position.calculateProfit(order.getQuantity(), order.getRequestedPrice(), commissionRate, sellTaxRate);
                portfolio.reducePosition(order.getSymbol(), order.getQuantity(), order.getRequestedPrice(), commissionRate, sellTaxRate);
                if (portfolio.getPosition(order.getSymbol()) == null) {
                    activePositionIds.remove(order.getSymbol());
                    activeStopLosses.remove(order.getSymbol());
                    activeTakeProfits.remove(order.getSymbol());
                    activeAutoManagedPositions.remove(order.getSymbol());
                }
            }

            ExecutionResult result = new ExecutionResult.Builder()
                    .status(ExecutionResult.Status.SUCCESS)
                    .orderId(order.getOrderId())
                    .symbol(order.getSymbol())
                    .orderType(order.getType())
                    .orderSide(order.getSide())
                    .orderStatus(OrderStatus.FILLED)
                    .requestedQuantity(order.getQuantity())
                    .executedQuantity(order.getQuantity())
                    .requestedPrice(order.getRequestedPrice())
                    .executedPrice(order.getRequestedPrice())
                    .stopLoss(stopLoss)
                    .takeProfit(takeProfit)
                    .realizedPnL(realizedPnL)
                    .commission(order.getQuantity() * order.getRequestedPrice() * commissionRate)
                    .tax(tax)
                    .executionTime(LocalDateTime.now())
                    .positionId(positionId)
                    .decisionReason(order.getReason())
                    .decisionSource(decisionSource)
                    .autoManaged(autoManaged)
                    .message(order.getSide().opensExposure() ? "開倉成功" : String.format("平倉成功，損益 %.2f", realizedPnL))
                    .build();
            executionHistory.put(order.getOrderId(), result);
            return result;
        } catch (Exception e) {
            ExecutionResult errorResult = new ExecutionResult.Builder()
                    .orderId(order.getOrderId())
                    .symbol(order.getSymbol())
                    .orderType(order.getType())
                    .orderSide(order.getSide())
                    .decisionReason(order.getReason())
                    .decisionSource(decisionSource)
                    .error(e)
                    .build();
            executionHistory.put(order.getOrderId(), errorResult);
            return errorResult;
        }
    }

    public Map<String, ExecutionResult> forceCloseAll(double price) {
        Map<String, ExecutionResult> results = new HashMap<>();
        for (Position position : portfolio.getPositions().toArray(new Position[0])) {
            ExecutionResult result = closePosition(position.getSymbol(), position.getQuantity(), price, OrderType.MARKET);
            results.put(position.getSymbol(), result);
        }
        return results;
    }

    public Map<String, ExecutionResult> forceCloseAutoManagedPositions(
            Function<String, Double> priceProvider,
            OrderType orderType,
            String reason,
            DecisionResult.DecisionSource decisionSource) {
        Map<String, ExecutionResult> results = new HashMap<>();
        for (String symbol : activeAutoManagedPositions.keySet().toArray(new String[0])) {
            if (!isAutoManagedPosition(symbol)) {
                continue;
            }
            Position position = portfolio.getPosition(symbol);
            if (position == null || position.getQuantity() <= 0) {
                activeAutoManagedPositions.remove(symbol);
                continue;
            }
            Double closePrice = priceProvider != null ? priceProvider.apply(symbol) : null;
            if (closePrice == null || closePrice <= 0.0) {
                results.put(symbol, rejectedResult(generateOrderId(), symbol, "Close price is required", decisionSource));
                continue;
            }
            Order order = new Order.Builder()
                    .orderId(generateOrderId())
                    .symbol(symbol)
                    .side(OrderSide.SELL)
                    .type(orderType != null ? orderType : OrderType.MARKET)
                    .quantity(position.getQuantity())
                    .requestedPrice(closePrice)
                    .reason(reason != null ? reason : "Force close auto-managed position")
                    .build();
            results.put(symbol, executeOrder(order, true, decisionSource));
        }
        return results;
    }

    public ExecutionResult partialClose(String symbol, double percentage, double price) {
        if (percentage <= 0.0 || percentage > 1.0) {
            throw new IllegalArgumentException("Close percentage must be in (0.0, 1.0]");
        }
        Position position = portfolio.getPosition(symbol);
        if (position == null) {
            return rejectedResult(generateOrderId(), symbol, "No position available");
        }
        int closeQuantity = Math.max(1, (int) (position.getQuantity() * percentage));
        return closePosition(symbol, closeQuantity, price, OrderType.MARKET, "Partial close");
    }

    public boolean isAutoManagedPosition(String symbol) {
        return Boolean.TRUE.equals(activeAutoManagedPositions.get(symbol));
    }

    public ExecutionResult[] reversePosition(String symbol, int newQuantity, double price) {
        ExecutionResult[] results = new ExecutionResult[2];
        results[0] = rejectedResult(generateOrderId(), symbol, "Reverse position is disabled in long-only mode");
        results[1] = rejectedResult(generateOrderId(), symbol, "Short selling is disabled in v1");
        return results;
    }

    private String generateOrderId() {
        return String.format(
                "%s_%s_%06d",
                mode.getShortCode(),
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                orderIdCounter.getAndIncrement());
    }

    private ExecutionResult rejectedResult(String orderId, String symbol, String message) {
        return rejectedResult(orderId, symbol, message, DecisionResult.DecisionSource.MANUAL);
    }

    private ExecutionResult rejectedResult(
            String orderId,
            String symbol,
            String message,
            DecisionResult.DecisionSource decisionSource) {
        return new ExecutionResult.Builder()
                .status(ExecutionResult.Status.REJECTED)
                .orderId(orderId.isBlank() ? generateOrderId() : orderId)
                .symbol(symbol)
                .orderStatus(OrderStatus.REJECTED)
                .decisionSource(decisionSource)
                .message(message)
                .build();
    }

    public Map<String, ExecutionResult> getExecutionHistory() {
        return new HashMap<>(executionHistory);
    }

    public ExecutionResult getExecutionResult(String orderId) {
        return executionHistory.get(orderId);
    }

    public void clearHistory() {
        executionHistory.clear();
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public double getCommissionRate() {
        return commissionRate;
    }

    public double getSellTaxRate() {
        return sellTaxRate;
    }

    public String getStatistics() {
        long totalOrders = executionHistory.size();
        long successOrders = executionHistory.values().stream().filter(ExecutionResult::isSuccess).count();
        long failedOrders = executionHistory.values().stream().filter(ExecutionResult::isFailed).count();
        return String.format(
                "ExecutionEngine[mode=%s,totalOrders=%d,success=%d,failed=%d]",
                mode.getDisplayName(),
                totalOrders,
                successOrders,
                failedOrders);
    }

    @Override
    public String toString() {
        return String.format(
                "ExecutionEngine[mode=%s,portfolio=%s,commission=%.4f%%]",
                mode.getDisplayName(),
                portfolio,
                commissionRate * 100);
    }
}
