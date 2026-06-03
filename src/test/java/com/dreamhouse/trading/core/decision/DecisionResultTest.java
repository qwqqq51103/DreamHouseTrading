package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionResultTest {

    @Test
    void convertsToMarketScanResultWithAlignedFields() {
        DecisionResult result = new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_LONG)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol("2330.TW")
                .tradeMode(TradeMode.SWING_TRADE)
                .orderType(OrderType.MARKET)
                .orderSide(OrderSide.BUY)
                .reason("trend and voting aligned")
                .suggestedStopLoss(980.0)
                .suggestedTakeProfit(1080.0)
                .suggestedQuantity(200)
                .riskRewardRatio(2.0)
                .confidence(0.84)
                .build();

        MarketScanResult scanResult = result.toMarketScanResult();

        assertThat(scanResult.getSymbol()).isEqualTo("2330.TW");
        assertThat(scanResult.getMode()).isEqualTo(TradeMode.SWING_TRADE);
        assertThat(scanResult.getAction()).isEqualTo(DecisionResult.Action.OPEN_LONG);
        assertThat(scanResult.getConfidence()).isEqualTo(0.84);
        assertThat(scanResult.getReason()).isEqualTo("trend and voting aligned");
        assertThat(scanResult.getStopLoss()).isEqualTo(980.0);
        assertThat(scanResult.getTakeProfit()).isEqualTo(1080.0);
        assertThat(scanResult.getRiskRewardRatio()).isEqualTo(2.0);
    }
}
