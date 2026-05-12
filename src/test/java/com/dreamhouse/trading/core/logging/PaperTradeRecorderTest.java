package com.dreamhouse.trading.core.logging;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderStatus;
import com.dreamhouse.trading.core.execution.OrderType;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradeRecorderTest {

    @TempDir
    Path tempDir;

    @Test
    void recordsOrderEventsAndCompletedTrades() throws Exception {
        PaperTradeRecorder recorder = new PaperTradeRecorder(tempDir);
        DecisionResult entryDecision = new DecisionResult.Builder()
                .action(DecisionResult.Action.OPEN_LONG)
                .symbol("2330.TW")
                .tradeMode(TradeMode.DAY_TRADE)
                .confidence(0.82)
                .riskRewardRatio(2.1)
                .reason("entry signal")
                .build();
        MarketScanResult scanResult = MarketScanResult.builder("2330.TW")
                .tradeMode(TradeMode.DAY_TRADE)
                .decisionResult(entryDecision)
                .score(0.77)
                .riskRewardRatio(2.1)
                .rawSignalSummary("RSI:LONG 82%")
                .blockReason("")
                .build();
        ExecutionResult open = new ExecutionResult.Builder()
                .status(ExecutionResult.Status.SUCCESS)
                .orderId("OPEN-1")
                .positionId("OPEN-1")
                .symbol("2330.TW")
                .orderSide(OrderSide.BUY)
                .orderType(OrderType.MARKET)
                .orderStatus(OrderStatus.FILLED)
                .requestedQuantity(100)
                .executedQuantity(100)
                .requestedPrice(100.0)
                .executedPrice(100.0)
                .stopLoss(98.0)
                .takeProfit(104.0)
                .commission(10.0)
                .executionTime(LocalDateTime.of(2026, 5, 12, 9, 10))
                .decisionReason("entry signal")
                .message("open")
                .build();
        ExecutionResult close = new ExecutionResult.Builder()
                .status(ExecutionResult.Status.SUCCESS)
                .orderId("CLOSE-1")
                .positionId("OPEN-1")
                .symbol("2330.TW")
                .orderSide(OrderSide.SELL)
                .orderType(OrderType.MARKET)
                .orderStatus(OrderStatus.FILLED)
                .requestedQuantity(100)
                .executedQuantity(100)
                .requestedPrice(105.0)
                .executedPrice(105.0)
                .realizedPnL(480.0)
                .commission(10.5)
                .executionTime(LocalDateTime.of(2026, 5, 12, 9, 30))
                .decisionReason("exit signal")
                .message("close")
                .build();

        recorder.record(open, entryDecision, scanResult, "test");
        recorder.recordMarketPrice("2330.TW", 99.0, LocalDateTime.of(2026, 5, 12, 9, 15));
        recorder.recordMarketPrice("2330.TW", 106.0, LocalDateTime.of(2026, 5, 12, 9, 25));
        recorder.record(close, null, "test");

        List<String> orders = Files.readAllLines(recorder.getTodayOrderLogPath());
        List<String> setups = Files.readAllLines(recorder.getTodaySetupLogPath());
        List<String> trades = Files.readAllLines(recorder.getTodayCompletedTradeLogPath());

        assertThat(orders).hasSize(3);
        assertThat(orders.get(1)).contains("OPEN-1", "BUY", "entry signal");
        assertThat(orders.get(2)).contains("CLOSE-1", "SELL", "480.000000");
        assertThat(setups).hasSize(2);
        assertThat(setups.get(1)).contains("OPEN-1", "2330.TW", "0.770000", "RSI:LONG 82%");
        assertThat(trades).hasSize(2);
        assertThat(trades.get(1)).contains(
                "OPEN-1",
                "2330.TW",
                "100.000000",
                "105.000000",
                "true",
                "WIN",
                "480.000000",
                "-100.000000",
                "600.000000",
                "RSI:LONG 82%");
        assertThat(Files.readAllBytes(recorder.getTodayOrderLogPath()))
                .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(Files.readAllBytes(recorder.getTodaySetupLogPath()))
                .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(Files.readAllBytes(recorder.getTodayCompletedTradeLogPath()))
                .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }
}
