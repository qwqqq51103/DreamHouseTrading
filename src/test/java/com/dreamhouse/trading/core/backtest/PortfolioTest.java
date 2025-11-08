package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.*;

/**
 * Portfolio 類單元測試
 */
@DisplayName("Portfolio 類測試")
class PortfolioTest {

    private static final double INITIAL_CASH = 1000000.0;
    private static final double COMMISSION_RATE = 0.001425;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio(INITIAL_CASH);
    }

    @Test
    @DisplayName("創建投資組合")
    void testCreatePortfolio() {
        // When & Then
        assertThat(portfolio.getInitialCash()).isEqualTo(INITIAL_CASH);
        assertThat(portfolio.getCash()).isEqualTo(INITIAL_CASH);
        assertThat(portfolio.getTotalValue()).isEqualTo(INITIAL_CASH);
        assertThat(portfolio.getPositionCount()).isEqualTo(0);
        assertThat(portfolio.getTotalTrades()).isEqualTo(0);
        assertThat(portfolio.getWinningTrades()).isEqualTo(0);
    }

    @Test
    @DisplayName("添加持倉 - 買入股票")
    void testAddPosition() {
        // When
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(1);
        assertThat(portfolio.hasPosition("2330.TW")).isTrue();
        assertThat(portfolio.getCash()).isCloseTo(499287.5, within(0.1)); // 1000000 - 500712.5
        assertThat(portfolio.getTotalTrades()).isEqualTo(1);

        Position position = portfolio.getPosition("2330.TW");
        assertThat(position).isNotNull();
        assertThat(position.getQuantity()).isEqualTo(1000);
    }

    @Test
    @DisplayName("添加持倉 - 多個不同股票")
    void testAddMultiplePositions() {
        // When
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.addPosition("2317.TW", 2000, 100.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(2);
        assertThat(portfolio.hasPosition("2330.TW")).isTrue();
        assertThat(portfolio.hasPosition("2317.TW")).isTrue();
        assertThat(portfolio.getTotalTrades()).isEqualTo(2);
    }

    @Test
    @DisplayName("添加持倉 - 同一股票加碼")
    void testAddPositionSameSymbol() {
        // When
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.addPosition("2330.TW", 500, 510.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(1); // 仍然只有一個持倉
        assertThat(portfolio.getTotalTrades()).isEqualTo(2);

        Position position = portfolio.getPosition("2330.TW");
        assertThat(position.getQuantity()).isEqualTo(1500); // 加碼後總數量
    }

    @Test
    @DisplayName("添加持倉 - 現金不足應拋出異常")
    void testAddPositionInsufficientCash() {
        // When & Then
        assertThatThrownBy(() ->
            portfolio.addPosition("2330.TW", 10000, 500.0, COMMISSION_RATE)
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insufficient cash for purchase");
    }

    @Test
    @DisplayName("減少持倉 - 賣出股票")
    void testReducePosition() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        double cashBeforeSell = portfolio.getCash();

        // When - 以更高價格賣出
        portfolio.reducePosition("2330.TW", 500, 520.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(1); // 還有剩餘持倉
        assertThat(portfolio.getCash()).isGreaterThan(cashBeforeSell); // 現金增加

        Position position = portfolio.getPosition("2330.TW");
        assertThat(position.getQuantity()).isEqualTo(500); // 剩餘 500 股
        assertThat(portfolio.getTotalTrades()).isEqualTo(2);
    }

    @Test
    @DisplayName("減少持倉 - 全部賣出")
    void testReducePositionFull() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When - 全部賣出
        portfolio.reducePosition("2330.TW", 1000, 520.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(0); // 持倉已清空
        assertThat(portfolio.hasPosition("2330.TW")).isFalse();
        assertThat(portfolio.getCash()).isGreaterThan(INITIAL_CASH); // 盈利
        assertThat(portfolio.getTotalTrades()).isEqualTo(2);
    }

    @Test
    @DisplayName("減少持倉 - 盈利交易")
    void testReducePositionWinningTrade() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When - 以高價賣出（盈利）
        portfolio.reducePosition("2330.TW", 1000, 550.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getWinningTrades()).isEqualTo(1);
        assertThat(portfolio.getWinRate()).isCloseTo(0.5, within(0.01)); // 1勝/2交易
    }

    @Test
    @DisplayName("減少持倉 - 虧損交易")
    void testReducePositionLosingTrade() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When - 以低價賣出（虧損）
        portfolio.reducePosition("2330.TW", 1000, 450.0, COMMISSION_RATE);

        // Then
        assertThat(portfolio.getWinningTrades()).isEqualTo(0);
        assertThat(portfolio.getWinRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("減少持倉 - 無此持倉應拋出異常")
    void testReducePositionNotFound() {
        // When & Then
        assertThatThrownBy(() ->
            portfolio.reducePosition("2330.TW", 100, 500.0, COMMISSION_RATE)
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No position found for symbol: 2330.TW");
    }

    @Test
    @DisplayName("減少持倉 - 數量不足應拋出異常")
    void testReducePositionInsufficientQuantity() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThatThrownBy(() ->
            portfolio.reducePosition("2330.TW", 1001, 500.0, COMMISSION_RATE)
        )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insufficient position quantity");
    }

    @Test
    @DisplayName("平倉所有持倉")
    void testCloseAllPositions() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.addPosition("2317.TW", 2000, 100.0, COMMISSION_RATE);

        // When
        portfolio.closeAllPositions(520.0);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(0);
        assertThat(portfolio.getCash()).isGreaterThan(0);
    }

    @Test
    @DisplayName("更新市值 - 價格上漲")
    void testUpdateMarketValuePriceUp() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When
        portfolio.updateMarketValue(550.0);

        // Then
        // 總市值 = 現金 + 持倉市值
        // 現金 ≈ 499287.5, 持倉市值 = 1000 * 550 = 550000
        assertThat(portfolio.getTotalValue()).isCloseTo(1049287.5, within(1.0));
        assertThat(portfolio.getTotalReturn()).isGreaterThan(0); // 正收益
    }

    @Test
    @DisplayName("更新市值 - 價格下跌")
    void testUpdateMarketValuePriceDown() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When
        portfolio.updateMarketValue(450.0);

        // Then
        // 總市值 = 現金 + 持倉市值
        // 現金 ≈ 499287.5, 持倉市值 = 1000 * 450 = 450000
        assertThat(portfolio.getTotalValue()).isCloseTo(949287.5, within(1.0));
        assertThat(portfolio.getTotalReturn()).isLessThan(0); // 負收益
    }

    @Test
    @DisplayName("計算最大回撤")
    void testMaxDrawdown() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When - 價格波動
        portfolio.updateMarketValue(600.0); // 上漲，更新最大值
        portfolio.updateMarketValue(550.0); // 下跌
        portfolio.updateMarketValue(500.0); // 繼續下跌

        // Then
        assertThat(portfolio.getMaxDrawdown()).isGreaterThan(0);
    }

    @Test
    @DisplayName("計算總收益率 - 盈利")
    void testGetTotalReturnProfit() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.updateMarketValue(600.0);

        // When
        double totalReturn = portfolio.getTotalReturn();

        // Then
        // 總市值 ≈ 499287.5 + 600000 = 1099287.5
        // 收益率 = (1099287.5 - 1000000) / 1000000 ≈ 9.93%
        assertThat(totalReturn).isCloseTo(0.0993, within(0.001));
    }

    @Test
    @DisplayName("計算總收益率 - 虧損")
    void testGetTotalReturnLoss() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.updateMarketValue(400.0);

        // When
        double totalReturn = portfolio.getTotalReturn();

        // Then
        // 總市值 ≈ 499287.5 + 400000 = 899287.5
        // 收益率 = (899287.5 - 1000000) / 1000000 ≈ -10.07%
        assertThat(totalReturn).isCloseTo(-0.1007, within(0.001));
    }

    @Test
    @DisplayName("計算勝率 - 無交易")
    void testGetWinRateNoTrades() {
        // When & Then
        assertThat(portfolio.getWinRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("計算勝率 - 有勝有敗")
    void testGetWinRateMixedResults() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.reducePosition("2330.TW", 500, 550.0, COMMISSION_RATE); // 勝

        portfolio.addPosition("2317.TW", 1000, 100.0, COMMISSION_RATE);
        portfolio.reducePosition("2317.TW", 500, 90.0, COMMISSION_RATE);  // 敗

        // When
        double winRate = portfolio.getWinRate();

        // Then
        // 總交易 4 次（2買 + 2賣），1 勝
        assertThat(winRate).isCloseTo(0.25, within(0.01));
    }

    @Test
    @DisplayName("獲取持倉總價值")
    void testGetPositionValue() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.updateMarketValue(550.0);

        // When
        double positionValue = portfolio.getPositionValue();

        // Then
        assertThat(positionValue).isCloseTo(550000.0, within(1.0));
    }

    @Test
    @DisplayName("檢查是否有足夠現金")
    void testHasEnoughCash() {
        // When & Then
        assertThat(portfolio.hasEnoughCash(500000.0)).isTrue();
        assertThat(portfolio.hasEnoughCash(1000000.0)).isTrue();
        assertThat(portfolio.hasEnoughCash(1000001.0)).isFalse();
    }

    @Test
    @DisplayName("檢查是否有持倉")
    void testHasPosition() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When & Then
        assertThat(portfolio.hasPosition("2330.TW")).isTrue();
        assertThat(portfolio.hasPosition("2317.TW")).isFalse();
    }

    @Test
    @DisplayName("重置投資組合")
    void testReset() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);
        portfolio.updateMarketValue(550.0);

        // When
        portfolio.reset(2000000.0);

        // Then
        assertThat(portfolio.getInitialCash()).isEqualTo(2000000.0);
        assertThat(portfolio.getCash()).isEqualTo(2000000.0);
        assertThat(portfolio.getTotalValue()).isEqualTo(2000000.0);
        assertThat(portfolio.getPositionCount()).isEqualTo(0);
        assertThat(portfolio.getTotalTrades()).isEqualTo(0);
        assertThat(portfolio.getWinningTrades()).isEqualTo(0);
        assertThat(portfolio.getMaxDrawdown()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("測試 toString 方法")
    void testToString() {
        // Given
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // When
        String result = portfolio.toString();

        // Then
        assertThat(result)
            .contains("Portfolio{")
            .contains("cash=")
            .contains("totalValue=")
            .contains("positions=1");
    }

    @Test
    @DisplayName("複雜交易場景 - 多股票多次買賣")
    void testComplexTradingScenario() {
        // Given & When
        // 買入 2330
        portfolio.addPosition("2330.TW", 1000, 500.0, COMMISSION_RATE);

        // 買入 2317
        portfolio.addPosition("2317.TW", 2000, 100.0, COMMISSION_RATE);

        // 2330 加碼
        portfolio.addPosition("2330.TW", 500, 510.0, COMMISSION_RATE);

        // 賣出部分 2330（盈利）
        portfolio.reducePosition("2330.TW", 800, 550.0, COMMISSION_RATE);

        // 賣出部分 2317（虧損）
        portfolio.reducePosition("2317.TW", 1000, 95.0, COMMISSION_RATE);

        // 更新市值
        portfolio.updateMarketValue(520.0);

        // Then
        assertThat(portfolio.getPositionCount()).isEqualTo(2);
        assertThat(portfolio.getTotalTrades()).isEqualTo(5);
        assertThat(portfolio.getWinningTrades()).isGreaterThanOrEqualTo(1);
        assertThat(portfolio.getCash()).isGreaterThan(0);
        assertThat(portfolio.getTotalValue()).isGreaterThan(0);
    }

    @Test
    @DisplayName("小額投資組合測試")
    void testSmallPortfolio() {
        // Given
        Portfolio smallPortfolio = new Portfolio(100000.0);

        // When
        smallPortfolio.addPosition("2330.TW", 100, 500.0, COMMISSION_RATE);

        // Then
        assertThat(smallPortfolio.getCash()).isCloseTo(49928.75, within(0.1));
        assertThat(smallPortfolio.getPositionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("大額投資組合測試")
    void testLargePortfolio() {
        // Given
        Portfolio largePortfolio = new Portfolio(100000000.0);

        // When
        largePortfolio.addPosition("2330.TW", 100000, 500.0, COMMISSION_RATE);

        // Then
        assertThat(largePortfolio.getCash()).isCloseTo(49928750.0, within(1.0));
        assertThat(largePortfolio.getPositionCount()).isEqualTo(1);
    }
}
