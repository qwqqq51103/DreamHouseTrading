package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.logging.ExitReason;
import com.dreamhouse.trading.core.logging.TradeRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestTradeRecordMapperTest {

    @Test
    void shouldBuildTradeRecordWithBacktestDecisionSourceAndTimeframe() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 13, 9, 0);
        BacktestResult result = new BacktestResult(start, start.plusHours(1), 1_000_000.0);
        result.setTradeMode(TradeMode.DAY_TRADE);
        result.setTimeframe(Timeframe.M5);
        result.addTrade(new Trade(start.plusMinutes(5), "2330.TW", TradeType.BUY,
                1000, 100.0, 0.001, 0.0, 15.0, 95.0, 115.0, "radar entry"));
        result.addTrade(new Trade(start.plusMinutes(15), "2330.TW", TradeType.SELL,
                1000, 110.0, 0.001, 0.003, 20.0, null, null, "STOP_LOSS"));

        List<TradeRecord> records = BacktestTradeRecordMapper.toTradeRecords(
                result, "Backtest-A", "A template, M5");

        assertThat(records).hasSize(1);
        TradeRecord record = records.get(0);
        assertThat(record.getDecisionSource()).isEqualTo(DecisionResult.DecisionSource.BACKTEST);
        assertThat(record.getDecisionSource()).isNotEqualTo(DecisionResult.DecisionSource.MANUAL);
        assertThat(record.getTradeMode()).isEqualTo(TradeMode.DAY_TRADE);
        assertThat(record.getTimeframe()).isEqualTo(Timeframe.M5);
        assertThat(record.getQuantity()).isEqualTo(1000);
        assertThat(record.getGrossProfit()).isEqualTo(10_000.0);
        assertThat(record.getCommission()).isEqualTo(210.0);
        assertThat(record.getTax()).isEqualTo(330.0);
        assertThat(record.getSlippageCost()).isEqualTo(35.0);
        assertThat(record.getNetProfit()).isEqualTo(9_425.0);
        assertThat(record.getEntryReason()).isEqualTo("radar entry");
        assertThat(record.getExitReason()).isEqualTo(ExitReason.STOP_LOSS);
        assertThat(record.getExitReasonText()).isEqualTo("STOP_LOSS");
        assertThat(record.getStrategySettingSummary()).isEqualTo("A template, M5");
    }

    @Test
    void shouldLeaveUnavailableScoreAndBlockFieldsEmptyInsteadOfInventingValues() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 13, 9, 0);
        BacktestResult result = new BacktestResult(start, start.plusHours(1), 1_000_000.0);
        result.addTrade(new Trade(start.plusMinutes(5), "2317.TW", TradeType.BUY,
                1000, 50.0, 0.001, 0.0, 0.0, null, null, "entry"));
        result.addTrade(new Trade(start.plusMinutes(15), "2317.TW", TradeType.SELL,
                1000, 51.0, 0.001, 0.003, 0.0, null, null, "TAKE_PROFIT"));

        TradeRecord record = BacktestTradeRecordMapper.toTradeRecords(result, "Backtest", "").get(0);

        assertThat(record.getBlockReason()).isEmpty();
        assertThat(record.getRadarScoreComponents()).isEmpty();
    }
}
