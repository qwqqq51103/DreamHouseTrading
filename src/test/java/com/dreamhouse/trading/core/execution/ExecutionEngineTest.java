package com.dreamhouse.trading.core.execution;

import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.decision.DecisionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {

    private Portfolio portfolio;
    private ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio(1_000_000.0);
        executionEngine = new ExecutionEngine(ExecutionMode.BACKTEST, portfolio, 0.001);
    }

    @Test
    void executesLongEntryFromDecisionResult() {
        DecisionResult decision = new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_LONG)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol("2330.TW")
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.BUY)
                .suggestedQuantity(100)
                .reason("entry setup valid")
                .build();

        ExecutionResult result = executionEngine.executeDecision(decision, 100.0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderSide()).isEqualTo(OrderSide.BUY);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(portfolio.hasPosition("2330.TW")).isTrue();
    }

    @Test
    void rejectsShortEntryFromDecisionResult() {
        DecisionResult decision = new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_SHORT)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol("2330.TW")
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.SHORT)
                .suggestedQuantity(100)
                .reason("short setup")
                .build();

        ExecutionResult result = executionEngine.executeDecision(decision, 100.0);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
    }

    @Test
    void closesExistingPositionFromDecisionResult() {
        executionEngine.openPosition("2330.TW", 100, 100.0);
        DecisionResult decision = new DecisionResult.Builder()
                .action(DecisionResult.Action.CLOSE_POSITION)
                .source(DecisionResult.Source.VOTING_EXIT)
                .symbol("2330.TW")
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.SELL)
                .reason("exit signal")
                .build();

        ExecutionResult result = executionEngine.executeDecision(decision, 105.0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderSide()).isEqualTo(OrderSide.SELL);
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
        assertThat(portfolio.getRealizedPnL()).isGreaterThan(0.0);
    }

    @Test
    void keepsLifecycleFieldsForOpenAndCloseRows() {
        ExecutionResult open = executionEngine.openPosition(
                "2330.TW",
                100,
                100.0,
                OrderType.MARKET,
                98.0,
                104.0,
                "測試開倉");

        ExecutionResult close = executionEngine.closePosition("2330.TW", 100, 106.0);

        assertThat(open.getPositionId()).isEqualTo(open.getOrderId());
        assertThat(open.getStopLoss()).isEqualTo(98.0);
        assertThat(open.getTakeProfit()).isEqualTo(104.0);
        assertThat(open.getMessage()).isEqualTo("開倉成功");

        assertThat(close.getPositionId()).isEqualTo(open.getOrderId());
        assertThat(close.getStopLoss()).isEqualTo(98.0);
        assertThat(close.getTakeProfit()).isEqualTo(104.0);
        assertThat(close.getRealizedPnL()).isGreaterThan(0.0);
        assertThat(close.getMessage()).contains("平倉成功");
    }

    @Test
    void closePositionKeepsChineseExitReason() {
        executionEngine.openPosition("2330.TW", 100, 100.0, OrderType.MARKET, 98.0, 104.0, "測試開倉");

        ExecutionResult close = executionEngine.closePosition(
                "2330.TW",
                100,
                98.0,
                OrderType.MARKET,
                "停損觸發：最新價 98.00 <= 停損 98.00");

        assertThat(close.isSuccess()).isTrue();
        assertThat(close.getOrderSide()).isEqualTo(OrderSide.SELL);
        assertThat(close.getDecisionReason()).contains("停損觸發");
        assertThat(close.getMessage()).contains("平倉成功");
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
    }

    @Test
    void closePositionDeductsDayTradeSellTax() {
        ExecutionEngine dayTradeEngine = new ExecutionEngine(ExecutionMode.BACKTEST, new Portfolio(1_000_000.0), 0.001425, 0.0015);
        dayTradeEngine.openPosition("2330.TW", 1000, 100.0);

        ExecutionResult close = dayTradeEngine.closePosition("2330.TW", 1000, 101.0);

        assertThat(close.getTax()).isEqualTo(151.5);
        assertThat(close.getRealizedPnL()).isLessThan(1000.0 - 100.0 * 1000 * 0.001425 - 101.0 * 1000 * 0.001425);
    }

    @Test
    void shouldRejectOpenLongWhenCashIsInsufficientForDecisionQuantity() {
        ExecutionEngine smallAccountEngine = new ExecutionEngine(ExecutionMode.BACKTEST, new Portfolio(50_000.0), 0.001425);
        DecisionResult decision = openLongDecision(1_000, "entry setup valid");

        ExecutionResult result = smallAccountEngine.executeDecision(decision, 100.0);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.getMessage()).contains("Required cash");
        assertThat(smallAccountEngine.getPortfolio().hasPosition("2330.TW")).isFalse();
    }

    @Test
    void shouldExecuteOpenLongThroughDecisionResult() {
        DecisionResult decision = openLongDecision(1_000, "auto monitor entry setup valid");

        ExecutionResult result = executionEngine.executeDecision(decision, 100.0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderSide()).isEqualTo(OrderSide.BUY);
        assertThat(result.getRequestedQuantity()).isEqualTo(1_000);
        assertThat(result.getExecutedPrice()).isEqualTo(100.0);
        assertThat(portfolio.getPosition("2330.TW").getQuantity()).isEqualTo(1_000);
    }

    @Test
    void shouldMarkAutoManagedPositionWhenDecisionIsAutoMonitor() {
        DecisionResult decision = openLongDecision(1_000, "entry setup from radar",
                DecisionResult.DecisionSource.AUTO_MONITOR);

        ExecutionResult result = executionEngine.executeDecision(decision, 100.0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isAutoManaged()).isTrue();
        assertThat(executionEngine.isAutoManagedPosition("2330.TW")).isTrue();
    }

    @Test
    void shouldNotCreateShortPositionInLongOnlyMode() {
        DecisionResult decision = new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_SHORT)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol("2330.TW")
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.SHORT)
                .suggestedQuantity(1_000)
                .reason("short setup")
                .build();

        ExecutionResult result = executionEngine.executeDecision(decision, 100.0);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.getMessage()).contains("Short selling is disabled");
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
    }

    @Test
    void shouldPartialClosePositionAndPreserveRemainingQuantity() {
        executionEngine.openPosition("2330.TW", 1_000, 100.0);

        ExecutionResult result = executionEngine.partialClose("2330.TW", 0.4, 110.0);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedQuantity()).isEqualTo(400);
        assertThat(result.getDecisionReason()).isEqualTo("Partial close");
        assertThat(result.getRealizedPnL()).isGreaterThan(0.0);
        assertThat(portfolio.getPosition("2330.TW").getQuantity()).isEqualTo(600);
    }

    @Test
    void shouldForceCloseAllAutoManagedPositions() {
        executionEngine.executeDecision(openLongDecision(
                1_000,
                "auto entry",
                DecisionResult.DecisionSource.AUTO_MONITOR), 100.0);
        executionEngine.openPosition("2317.TW", 1_000, 50.0);

        var results = executionEngine.forceCloseAutoManagedPositions(
                symbol -> 101.0,
                OrderType.MARKET,
                "FORCE_CLOSE",
                DecisionResult.DecisionSource.AUTO_MONITOR);

        assertThat(results).containsOnlyKeys("2330.TW");
        assertThat(results.get("2330.TW").isSuccess()).isTrue();
        assertThat(results.get("2330.TW").isAutoManaged()).isTrue();
        assertThat(results.get("2330.TW").getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.AUTO_MONITOR);
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
        assertThat(portfolio.hasPosition("2317.TW")).isTrue();
    }

    @Test
    void shouldRejectReversePositionInLongOnlyMode() {
        executionEngine.openPosition("2330.TW", 1_000, 100.0);

        ExecutionResult[] results = executionEngine.reversePosition("2330.TW", 1_000, 99.0);

        assertThat(results).hasSize(2);
        assertThat(results[0].isFailed()).isTrue();
        assertThat(results[0].getMessage()).contains("Reverse position is disabled");
        assertThat(results[1].isFailed()).isTrue();
        assertThat(results[1].getMessage()).contains("Short selling is disabled");
        assertThat(portfolio.getPosition("2330.TW").getQuantity()).isEqualTo(1_000);
    }

    @Test
    void shouldNotLoseDecisionSourceWhenClosingPosition() {
        executionEngine.executeDecision(openLongDecision(
                1_000,
                "auto entry",
                DecisionResult.DecisionSource.AUTO_MONITOR), 100.0);
        DecisionResult closeDecision = new DecisionResult.Builder()
                .action(DecisionResult.Action.CLOSE_POSITION)
                .source(DecisionResult.Source.STOP_MANAGER)
                .decisionSource(DecisionResult.DecisionSource.AUTO_MONITOR)
                .symbol("2330.TW")
                .orderType(OrderType.STOP)
                .orderSide(OrderSide.SELL)
                .suggestedQuantity(1_000)
                .reason("STOP_LOSS")
                .build();

        ExecutionResult close = executionEngine.executeDecision(closeDecision, 98.0);

        assertThat(close.isSuccess()).isTrue();
        assertThat(close.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.AUTO_MONITOR);
        assertThat(close.isAutoManaged()).isTrue();
        assertThat(close.getOrderType()).isEqualTo(OrderType.STOP);
    }

    private DecisionResult openLongDecision(int quantity, String reason) {
        return openLongDecision(quantity, reason, DecisionResult.DecisionSource.SYSTEM);
    }

    private DecisionResult openLongDecision(int quantity, String reason, DecisionResult.DecisionSource decisionSource) {
        return new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_LONG)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .decisionSource(decisionSource)
                .symbol("2330.TW")
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.BUY)
                .suggestedQuantity(quantity)
                .suggestedStopLoss(98.0)
                .suggestedTakeProfit(104.0)
                .reason(reason)
                .build();
    }
}
