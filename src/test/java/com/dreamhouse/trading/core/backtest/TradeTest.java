package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Trade 類單元測試
 */
@DisplayName("Trade 類測試")
class TradeTest {

    @Test
    @DisplayName("創建買入交易 - 不含停利停損")
    void testCreateBuyTradeWithoutStops() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2024, 10, 1, 10, 30);
        String symbol = "2330.TW";
        int quantity = 1000;
        double price = 500.0;
        double commission = 0.001425;

        // When
        Trade trade = new Trade(timestamp, symbol, TradeType.BUY, quantity, price, commission);

        // Then
        assertThat(trade.getTimestamp()).isEqualTo(timestamp);
        assertThat(trade.getSymbol()).isEqualTo(symbol);
        assertThat(trade.getType()).isEqualTo(TradeType.BUY);
        assertThat(trade.getQuantity()).isEqualTo(quantity);
        assertThat(trade.getPrice()).isEqualTo(price);
        assertThat(trade.getCommission()).isEqualTo(commission);
        assertThat(trade.getTotalAmount()).isEqualTo(500000.0);
        assertThat(trade.getStopLoss()).isNull();
        assertThat(trade.getTakeProfit()).isNull();
        assertThat(trade.getExitReason()).isNull();
    }

    @Test
    @DisplayName("創建賣出交易 - 含停利停損")
    void testCreateSellTradeWithStops() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2024, 10, 2, 14, 0);
        String symbol = "2330.TW";
        int quantity = 1000;
        double price = 520.0;
        double commission = 0.004425;
        Double stopLoss = 490.0;
        Double takeProfit = 550.0;
        String exitReason = "停利出場";

        // When
        Trade trade = new Trade(timestamp, symbol, TradeType.SELL, quantity, price, commission,
                               stopLoss, takeProfit, exitReason);

        // Then
        assertThat(trade.getTimestamp()).isEqualTo(timestamp);
        assertThat(trade.getSymbol()).isEqualTo(symbol);
        assertThat(trade.getType()).isEqualTo(TradeType.SELL);
        assertThat(trade.getQuantity()).isEqualTo(quantity);
        assertThat(trade.getPrice()).isEqualTo(price);
        assertThat(trade.getCommission()).isEqualTo(commission);
        assertThat(trade.getTotalAmount()).isEqualTo(520000.0);
        assertThat(trade.getStopLoss()).isEqualTo(stopLoss);
        assertThat(trade.getTakeProfit()).isEqualTo(takeProfit);
        assertThat(trade.getExitReason()).isEqualTo(exitReason);
    }

    @Test
    @DisplayName("計算買入交易總成本 - 包含手續費")
    void testGetTotalCostForBuy() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.BUY,
            1000,
            500.0,
            0.001425
        );

        // When
        double totalCost = trade.getTotalCost();

        // Then
        // 總成本 = 500000 + (500000 * 0.001425) = 500712.5
        assertThat(totalCost).isEqualTo(500712.5);
    }

    @Test
    @DisplayName("計算賣出交易淨收入 - 扣除手續費")
    void testGetNetProceedsForSell() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.SELL,
            1000,
            520.0,
            0.004425  // 賣出手續費 0.1425% + 證交稅 0.3%
        );

        // When
        double netProceeds = trade.getNetProceeds();

        // Then
        // 淨收入 = 520000 - (520000 * 0.004425) = 517699.0
        assertThat(netProceeds).isEqualTo(517699.0);
    }

    @Test
    @DisplayName("計算手續費金額")
    void testGetCommissionAmount() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.BUY,
            1000,
            500.0,
            0.001425
        );

        // When
        double commissionAmount = trade.getCommissionAmount();

        // Then
        // 手續費 = 500000 * 0.001425 = 712.5
        assertThat(commissionAmount).isEqualTo(712.5);
    }

    @Test
    @DisplayName("測試 equals 方法 - 相同交易")
    void testEqualsWithSameTrade() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2024, 10, 1, 10, 30);
        Trade trade1 = new Trade(timestamp, "2330.TW", TradeType.BUY, 1000, 500.0, 0.001425);
        Trade trade2 = new Trade(timestamp, "2330.TW", TradeType.BUY, 2000, 510.0, 0.001425);

        // When & Then
        assertThat(trade1).isEqualTo(trade2);
        assertThat(trade1.hashCode()).isEqualTo(trade2.hashCode());
    }

    @Test
    @DisplayName("測試 equals 方法 - 不同交易")
    void testEqualsWithDifferentTrade() {
        // Given
        Trade trade1 = new Trade(LocalDateTime.now(), "2330.TW", TradeType.BUY, 1000, 500.0, 0.001425);
        Trade trade2 = new Trade(LocalDateTime.now(), "2317.TW", TradeType.BUY, 1000, 500.0, 0.001425);

        // When & Then
        assertThat(trade1).isNotEqualTo(trade2);
    }

    @Test
    @DisplayName("測試 toString 方法")
    void testToString() {
        // Given
        LocalDateTime timestamp = LocalDateTime.of(2024, 10, 1, 10, 30);
        Trade trade = new Trade(timestamp, "2330.TW", TradeType.BUY, 1000, 500.0, 0.001425);

        // When
        String result = trade.toString();

        // Then
        assertThat(result)
            .contains("Trade{")
            .contains("1000@500.00")
            .contains("commission=0.0014")
            .contains("total=500000.00");
    }

    @Test
    @DisplayName("小數量交易測試")
    void testSmallQuantityTrade() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.BUY,
            1,
            500.0,
            0.001425
        );

        // When & Then
        assertThat(trade.getTotalAmount()).isEqualTo(500.0);
        assertThat(trade.getTotalCost()).isEqualTo(500.7125);
        assertThat(trade.getCommissionAmount()).isEqualTo(0.7125);
    }

    @Test
    @DisplayName("大數量交易測試")
    void testLargeQuantityTrade() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.BUY,
            100000,
            500.0,
            0.001425
        );

        // When & Then
        assertThat(trade.getTotalAmount()).isEqualTo(50000000.0);
        assertThat(trade.getTotalCost()).isEqualTo(50071250.0);
        assertThat(trade.getCommissionAmount()).isEqualTo(71250.0);
    }

    @Test
    @DisplayName("高價股交易測試")
    void testHighPriceStockTrade() {
        // Given - 股王大立光假設價格 2000
        Trade trade = new Trade(
            LocalDateTime.now(),
            "3008.TW",
            TradeType.BUY,
            100,
            2000.0,
            0.001425
        );

        // When & Then
        assertThat(trade.getTotalAmount()).isEqualTo(200000.0);
        assertThat(trade.getTotalCost()).isEqualTo(200285.0);
    }

    @Test
    @DisplayName("零手續費交易測試")
    void testZeroCommissionTrade() {
        // Given
        Trade trade = new Trade(
            LocalDateTime.now(),
            "2330.TW",
            TradeType.BUY,
            1000,
            500.0,
            0.0
        );

        // When & Then
        assertThat(trade.getCommissionAmount()).isEqualTo(0.0);
        assertThat(trade.getTotalCost()).isEqualTo(500000.0);
        assertThat(trade.getNetProceeds()).isEqualTo(500000.0);
    }
}
