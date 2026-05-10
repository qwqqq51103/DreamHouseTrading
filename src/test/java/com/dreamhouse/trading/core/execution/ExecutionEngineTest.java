package com.dreamhouse.trading.core.execution;

import com.dreamhouse.trading.core.backtest.Portfolio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {

    @Test
    void paperTradingRecordsOpenCloseActionsAndRealizedPnL() {
        Portfolio portfolio = new Portfolio(1_000_000.0);
        ExecutionEngine engine = new ExecutionEngine(ExecutionMode.PAPER_TRADING, portfolio, 0.001);

        ExecutionResult open = engine.openPosition("2330.TW", 1_000, 100.0, 98.0, 104.0);
        ExecutionResult close = engine.closePosition("2330.TW", 1_000, 110.0);

        assertThat(open.isSuccess()).isTrue();
        assertThat(open.getAction()).isEqualTo("開倉");
        assertThat(open.getRealizedPnL()).isZero();
        assertThat(open.getStopLoss()).isEqualTo(98.0);
        assertThat(open.getTakeProfit()).isEqualTo(104.0);
        assertThat(open.getTradeId()).startsWith("T");

        assertThat(close.isSuccess()).isTrue();
        assertThat(close.getAction()).isEqualTo("平倉");
        assertThat(close.getTradeId()).isEqualTo(open.getTradeId());
        assertThat(close.getRealizedPnL()).isCloseTo(9_790.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(close.getStopLoss()).isEqualTo(98.0);
        assertThat(close.getTakeProfit()).isEqualTo(104.0);
        assertThat(close.getMessage()).contains("損益");
        assertThat(engine.getExecutionHistory()).hasSize(2);
    }
}
