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
}
