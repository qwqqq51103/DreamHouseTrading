package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoMonitorExitPolicyTest {

    @Test
    void shouldRegisterStopLossCooldownForStopLossExitReasonMetadata() {
        ExecutionResult result = closeResult(OrderType.STOP, "profit target touched");

        assertThat(AutoMonitorExitPolicy.shouldRegisterStopLossCooldown(result, null)).isTrue();
    }

    @Test
    void shouldNotRegisterStopLossCooldownForTakeProfitEvenWhenReasonMentionsStop() {
        ExecutionResult result = closeResult(OrderType.TAKE_PROFIT, "STOP wording in user text");

        assertThat(AutoMonitorExitPolicy.shouldRegisterStopLossCooldown(result, null)).isFalse();
    }

    @Test
    void shouldIgnoreReasonTextWhenOrderTypeIsMarket() {
        ExecutionResult result = closeResult(OrderType.MARKET, "停損文字只供顯示");

        assertThat(AutoMonitorExitPolicy.shouldRegisterStopLossCooldown(result, null)).isFalse();
    }

    @Test
    void shouldUseSignalOrderTypeOnlyWhenResultHasNoOrderType() {
        ExecutionResult result = new ExecutionResult.Builder()
                .orderSide(OrderSide.SELL)
                .orderType(null)
                .decisionReason("human text")
                .build();
        DecisionResult signal = new DecisionResult.Builder()
                .action(DecisionResult.Action.CLOSE_POSITION)
                .orderType(OrderType.TRAILING_STOP)
                .reason("renamed message")
                .build();

        assertThat(AutoMonitorExitPolicy.shouldRegisterStopLossCooldown(result, signal)).isTrue();
    }

    private ExecutionResult closeResult(OrderType orderType, String reason) {
        return new ExecutionResult.Builder()
                .orderSide(OrderSide.SELL)
                .orderType(orderType)
                .decisionReason(reason)
                .build();
    }
}
