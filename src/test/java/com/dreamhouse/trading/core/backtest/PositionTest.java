package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.*;

/**
 * Position 類單元測試
 */
@DisplayName("Position 類測試")
class PositionTest {

    private static final String SYMBOL = "2330.TW";
    private static final double COMMISSION_RATE = 0.001425;

    @Test
    @DisplayName("創建持倉")
    void testCreatePosition() {
        // Given & When
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // Then
        assertThat(position.getSymbol()).isEqualTo(SYMBOL);
        assertThat(position.getQuantity()).isEqualTo(1000);
        assertThat(position.getAveragePrice()).isEqualTo(500.0);
        assertThat(position.getTotalCost()).isEqualTo(500712.5); // 1000 * 500 * 1.001425
        assertThat(position.getRealizedPnL()).isEqualTo(0.0);
        assertThat(position.getTotalCommission()).isEqualTo(712.5);
    }

    @Test
    @DisplayName("增加持倉數量 - 相同價格")
    void testAddQuantitySamePrice() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When
        position.addQuantity(1000, 500.0, COMMISSION_RATE);

        // Then
        assertThat(position.getQuantity()).isEqualTo(2000);
        assertThat(position.getAveragePrice()).isCloseTo(500.7125, within(0.0001));
        assertThat(position.getTotalCost()).isEqualTo(1001425.0); // 2000 * 500 * 1.001425
        assertThat(position.getTotalCommission()).isEqualTo(1425.0);
    }

    @Test
    @DisplayName("增加持倉數量 - 不同價格（成本提高）")
    void testAddQuantityHigherPrice() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 以更高價格加碼
        position.addQuantity(1000, 520.0, COMMISSION_RATE);

        // Then
        assertThat(position.getQuantity()).isEqualTo(2000);
        // 平均成本 = (500712.5 + 520741.0) / 2000 = 510.72675
        assertThat(position.getAveragePrice()).isCloseTo(510.72675, within(0.00001));
        assertThat(position.getTotalCost()).isCloseTo(1021453.5, within(0.1));
    }

    @Test
    @DisplayName("增加持倉數量 - 不同價格（成本降低）")
    void testAddQuantityLowerPrice() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 以更低價格加碼
        position.addQuantity(1000, 480.0, COMMISSION_RATE);

        // Then
        assertThat(position.getQuantity()).isEqualTo(2000);
        // 平均成本 = (500712.5 + 480684.0) / 2000 = 490.6982
        assertThat(position.getAveragePrice()).isCloseTo(490.6982, within(0.0001));
    }

    @Test
    @DisplayName("減少持倉數量 - 盈利賣出")
    void testReduceQuantityWithProfit() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 以更高價格賣出部分
        position.reduceQuantity(500, 520.0, COMMISSION_RATE);

        // Then
        assertThat(position.getQuantity()).isEqualTo(500);
        // 已實現盈虧 = 賣出收入 - 成本
        // 賣出收入 = 500 * 520 * (1 - 0.001425) = 259629.75
        // 成本 = 500712.5 / 2 = 250356.25
        // 已實現盈虧 = 259629.75 - 250356.25 = 9273.5
        assertThat(position.getRealizedPnL()).isCloseTo(9273.5, within(0.5));
    }

    @Test
    @DisplayName("減少持倉數量 - 虧損賣出")
    void testReduceQuantityWithLoss() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 以更低價格賣出部分
        position.reduceQuantity(500, 480.0, COMMISSION_RATE);

        // Then
        assertThat(position.getQuantity()).isEqualTo(500);
        // 賣出收入 = 500 * 480 * (1 - 0.001425) = 239658.0
        // 成本 = 500712.5 / 2 = 250356.25
        // 已實現盈虧 = 239658.0 - 250356.25 = -10698.25
        assertThat(position.getRealizedPnL()).isCloseTo(-10698.25, within(0.1));
    }

    @Test
    @DisplayName("減少持倉數量 - 超過持有量應拋出異常")
    void testReduceQuantityExceedsHolding() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThatThrownBy(() -> position.reduceQuantity(1001, 500.0, COMMISSION_RATE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot reduce more than current quantity");
    }

    @Test
    @DisplayName("計算未實現盈虧 - 盈利")
    void testGetUnrealizedPnLWithProfit() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When
        double unrealizedPnL = position.getUnrealizedPnL(520.0);

        // Then
        // 未實現盈虧 = (1000 * 520) - 500712.5 = 19287.5
        assertThat(unrealizedPnL).isCloseTo(19287.5, within(0.1));
    }

    @Test
    @DisplayName("計算未實現盈虧 - 虧損")
    void testGetUnrealizedPnLWithLoss() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When
        double unrealizedPnL = position.getUnrealizedPnL(480.0);

        // Then
        // 未實現盈虧 = (1000 * 480) - 500712.5 = -20712.5
        assertThat(unrealizedPnL).isCloseTo(-20712.5, within(0.1));
    }

    @Test
    @DisplayName("計算總盈虧 - 已實現 + 未實現")
    void testGetTotalPnL() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);
        position.reduceQuantity(500, 520.0, COMMISSION_RATE); // 已實現盈虧

        // When - 當前價格 510
        double totalPnL = position.getTotalPnL(510.0);

        // Then
        // 已實現盈虧 ≈ 9273.5
        // 未實現盈虧 = (500 * 510) - (500712.5 / 2) = 254643.75
        // 總盈虧 = 已實現 + 未實現
        assertThat(totalPnL).isGreaterThan(9000);
    }

    @Test
    @DisplayName("計算收益率")
    void testGetReturnRate() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 當前價格漲到 550
        double returnRate = position.getReturnRate(550.0);

        // Then
        // 收益率 = ((1000 * 550) - 500712.5) / 500712.5 = 49287.5 / 500712.5 ≈ 9.84%
        assertThat(returnRate).isCloseTo(0.0984, within(0.001));
    }

    @Test
    @DisplayName("計算市值")
    void testGetMarketValue() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When
        double marketValue = position.getMarketValue(520.0);

        // Then
        assertThat(marketValue).isEqualTo(520000.0);
    }

    @Test
    @DisplayName("計算部分賣出的盈虧")
    void testCalculateProfit() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 計算賣出 300 股的盈虧（不實際執行）
        double profit = position.calculateProfit(300, 520.0, COMMISSION_RATE);

        // Then
        // 成本基礎 = (500712.5 / 1000) * 300 = 150213.75
        // 賣出收入 = 300 * 520 * (1 - 0.001425) = 155777.85
        // 盈虧 = 155777.85 - 150213.75 = 5564.1
        assertThat(profit).isCloseTo(5564.1, within(0.5));
    }

    @Test
    @DisplayName("計算部分賣出盈虧 - 超過持有量應拋出異常")
    void testCalculateProfitExceedsHolding() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThatThrownBy(() -> position.calculateProfit(1001, 520.0, COMMISSION_RATE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sell quantity exceeds position quantity");
    }

    @Test
    @DisplayName("檢查是否為盈利持倉 - 盈利")
    void testIsProfitableWithProfit() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThat(position.isProfitable(520.0)).isTrue();
    }

    @Test
    @DisplayName("檢查是否為盈利持倉 - 虧損")
    void testIsProfitableWithLoss() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThat(position.isProfitable(480.0)).isFalse();
    }

    @Test
    @DisplayName("檢查是否為盈利持倉 - 平手")
    void testIsProfitableBreakEven() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When & Then - 成本價 500.7125
        assertThat(position.isProfitable(500.0)).isFalse();
        assertThat(position.isProfitable(500.72)).isTrue();
    }

    @Test
    @DisplayName("測試 equals 方法 - 相同股票代號")
    void testEqualsWithSameSymbol() {
        // Given
        Position position1 = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);
        Position position2 = new Position(SYMBOL, 2000, 520.0, COMMISSION_RATE);

        // When & Then
        assertThat(position1).isEqualTo(position2);
        assertThat(position1.hashCode()).isEqualTo(position2.hashCode());
    }

    @Test
    @DisplayName("測試 equals 方法 - 不同股票代號")
    void testEqualsWithDifferentSymbol() {
        // Given
        Position position1 = new Position("2330.TW", 1000, 500.0, COMMISSION_RATE);
        Position position2 = new Position("2317.TW", 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThat(position1).isNotEqualTo(position2);
    }

    @Test
    @DisplayName("測試 toString 方法")
    void testToString() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When
        String result = position.toString();

        // Then
        assertThat(result)
            .contains("Position{")
            .contains("symbol='2330.TW'")
            .contains("quantity=1000")
            .contains("avgPrice=500.00")
            .contains("realizedPnL=0.00");
    }

    @Test
    @DisplayName("複雜交易場景 - 多次買入賣出")
    void testComplexTradingScenario() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, COMMISSION_RATE);

        // When - 多次交易
        position.addQuantity(500, 510.0, COMMISSION_RATE);     // 加碼買入
        position.addQuantity(500, 490.0, COMMISSION_RATE);     // 再次加碼
        position.reduceQuantity(800, 520.0, COMMISSION_RATE);  // 部分獲利了結
        position.reduceQuantity(500, 515.0, COMMISSION_RATE);  // 再次賣出

        // Then
        assertThat(position.getQuantity()).isEqualTo(700); // 2000 - 800 - 500 = 700
        assertThat(position.getRealizedPnL()).isGreaterThan(0); // 應該有盈利
    }

    @Test
    @DisplayName("零成本率測試")
    void testZeroCommissionRate() {
        // Given
        Position position = new Position(SYMBOL, 1000, 500.0, 0.0);

        // When & Then
        assertThat(position.getTotalCost()).isEqualTo(500000.0);
        assertThat(position.getTotalCommission()).isEqualTo(0.0);
        assertThat(position.getUnrealizedPnL(520.0)).isEqualTo(20000.0);
    }

    @Test
    @DisplayName("高手續費率測試")
    void testHighCommissionRate() {
        // Given - 1% 手續費（非常高）
        Position position = new Position(SYMBOL, 1000, 500.0, 0.01);

        // When & Then
        assertThat(position.getTotalCost()).isEqualTo(505000.0);
        assertThat(position.getTotalCommission()).isEqualTo(5000.0);
    }
}
