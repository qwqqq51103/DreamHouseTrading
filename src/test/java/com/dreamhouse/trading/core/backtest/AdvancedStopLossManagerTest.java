package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.backtest.AdvancedStopLossManager.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * AdvancedStopLossManager 類單元測試
 */
@DisplayName("AdvancedStopLossManager 類測試")
class AdvancedStopLossManagerTest {

    private AdvancedStopLossManager manager;
    private static final String STRATEGY_NAME = "TestStrategy";
    private static final String SYMBOL = "2330.TW";

    @BeforeEach
    void setUp() {
        manager = new AdvancedStopLossManager();
    }

    @Test
    @DisplayName("創建默認停損配置")
    void testDefaultConfig() {
        // When
        StopLossConfig config = manager.getConfig(STRATEGY_NAME);

        // Then
        assertThat(config.getFixedStopLossPercent()).isEqualTo(0.05);
        assertThat(config.getTakeProfitPercent()).isEqualTo(0.10);
        assertThat(config.isTrailingStopEnabled()).isFalse();
        assertThat(config.isTimeBasedStopEnabled()).isFalse();
        assertThat(config.isVolatilityAdjustedEnabled()).isFalse();
        assertThat(config.isTechnicalStopEnabled()).isFalse();
    }

    @Test
    @DisplayName("設置並獲取停損配置")
    void testSetAndGetConfig() {
        // Given
        StopLossConfig config = new StopLossConfig();
        config.setFixedStopLossPercent(0.08);
        config.setTakeProfitPercent(0.15);
        config.setTrailingStopEnabled(true);

        // When
        manager.setConfig(STRATEGY_NAME, config);
        StopLossConfig retrieved = manager.getConfig(STRATEGY_NAME);

        // Then
        assertThat(retrieved.getFixedStopLossPercent()).isEqualTo(0.08);
        assertThat(retrieved.getTakeProfitPercent()).isEqualTo(0.15);
        assertThat(retrieved.isTrailingStopEnabled()).isTrue();
    }

    @Test
    @DisplayName("計算固定百分比停損價格")
    void testCalculateFixedStopLossPrices() {
        // Given
        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();

        // When
        StopLossPrices prices = manager.calculateStopLossPrices(
            STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null
        );

        // Then
        // 停損 = 500 * (1 - 0.05) = 475
        assertThat(prices.getStopLoss()).isEqualTo(475.0);
        // 停利 = 500 * (1 + 0.10) = 550
        assertThat(prices.getTakeProfit()).isEqualTo(550.0);
    }

    @Test
    @DisplayName("計算自定義百分比停損價格")
    void testCalculateCustomStopLossPrices() {
        // Given
        StopLossConfig config = new StopLossConfig();
        config.setFixedStopLossPercent(0.03);  // 3%
        config.setTakeProfitPercent(0.06);     // 6%
        manager.setConfig(STRATEGY_NAME, config);

        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();

        // When
        StopLossPrices prices = manager.calculateStopLossPrices(
            STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null
        );

        // Then
        assertThat(prices.getStopLoss()).isEqualTo(485.0);  // 500 * 0.97
        assertThat(prices.getTakeProfit()).isEqualTo(530.0); // 500 * 1.06
    }

    @Test
    @DisplayName("檢查固定停損觸發")
    void testCheckFixedStopLossTrigger() {
        // Given
        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 價格跌至停損價格
        double currentPrice = 475.0;
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, currentPrice, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.STOP_LOSS);
        assertThat(trigger.getPrice()).isEqualTo(475.0);
        assertThat(trigger.getReason()).contains("固定停損");
    }

    @Test
    @DisplayName("檢查固定停利觸發")
    void testCheckTakeProfitTrigger() {
        // Given
        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 價格漲至停利價格
        double currentPrice = 550.0;
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, currentPrice, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.TAKE_PROFIT);
        assertThat(trigger.getPrice()).isEqualTo(550.0);
        assertThat(trigger.getReason()).contains("固定停利");
    }

    @Test
    @DisplayName("價格在停損停利區間內不觸發")
    void testNoTriggerInNormalRange() {
        // Given
        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 價格正常波動
        double currentPrice = 510.0;
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, currentPrice, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNull();
    }

    @Test
    @DisplayName("檢查移動止損觸發")
    void testCheckTrailingStopTrigger() {
        // Given - 啟用移動止損
        StopLossConfig config = new StopLossConfig();
        config.setTrailingStopEnabled(true);
        config.setTrailingStopPercent(0.03);      // 移動止損 3%
        config.setTrailingStopActivation(0.05);   // 達到 5% 盈利後啟動
        manager.setConfig(STRATEGY_NAME, config);

        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 價格先漲到觸發移動止損
        manager.checkStopTrigger(STRATEGY_NAME, SYMBOL, 530.0, LocalDateTime.now(), null); // +6%，啟動移動止損

        // Then - 價格回落觸發移動止損
        // 移動止損價格 = 530 * (1 - 0.03) = 514.1
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 514.0, LocalDateTime.now(), null
        );

        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.TRAILING_STOP);
        assertThat(trigger.getReason()).contains("移動止損");
    }

    @Test
    @DisplayName("移動止損未達啟動條件不觸發")
    void testTrailingStopNotActivatedYet() {
        // Given
        StopLossConfig config = new StopLossConfig();
        config.setTrailingStopEnabled(true);
        config.setTrailingStopActivation(0.10);   // 達到 10% 盈利後啟動
        manager.setConfig(STRATEGY_NAME, config);

        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 價格僅漲到 5%，未達啟動條件
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 525.0, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNull();
    }

    @Test
    @DisplayName("檢查時間止損觸發")
    void testCheckTimeBasedStopTrigger() {
        // Given - 啟用時間止損
        StopLossConfig config = new StopLossConfig();
        config.setTimeBasedStopEnabled(true);
        config.setMaxHoldingHours(24);  // 最大持有 24 小時
        manager.setConfig(STRATEGY_NAME, config);

        LocalDateTime entryTime = LocalDateTime.now().minusHours(25);  // 25 小時前開倉
        double entryPrice = 500.0;
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When - 檢查停損
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 510.0, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.TIME_BASED);
        assertThat(trigger.getReason()).contains("時間止損");
    }

    @Test
    @DisplayName("時間止損未超時不觸發")
    void testTimeBasedStopNotTriggeredYet() {
        // Given
        StopLossConfig config = new StopLossConfig();
        config.setTimeBasedStopEnabled(true);
        config.setMaxHoldingHours(48);
        manager.setConfig(STRATEGY_NAME, config);

        LocalDateTime entryTime = LocalDateTime.now().minusHours(24);  // 僅 24 小時
        double entryPrice = 500.0;
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 510.0, LocalDateTime.now(), null
        );

        // Then
        assertThat(trigger).isNull();
    }

    @Test
    @DisplayName("移除停損記錄")
    void testRemoveStop() {
        // Given
        double entryPrice = 500.0;
        LocalDateTime entryTime = LocalDateTime.now();
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, entryTime, null);

        // When
        manager.removeStop(STRATEGY_NAME, SYMBOL);

        // Then - 停損已移除，不應觸發
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 475.0, LocalDateTime.now(), null
        );
        assertThat(trigger).isNull();
    }

    @Test
    @DisplayName("多個策略獨立配置")
    void testMultipleStrategiesIndependentConfig() {
        // Given
        String strategy1 = "Strategy1";
        String strategy2 = "Strategy2";

        StopLossConfig config1 = new StopLossConfig();
        config1.setFixedStopLossPercent(0.05);

        StopLossConfig config2 = new StopLossConfig();
        config2.setFixedStopLossPercent(0.10);

        // When
        manager.setConfig(strategy1, config1);
        manager.setConfig(strategy2, config2);

        // Then
        assertThat(manager.getConfig(strategy1).getFixedStopLossPercent()).isEqualTo(0.05);
        assertThat(manager.getConfig(strategy2).getFixedStopLossPercent()).isEqualTo(0.10);
    }

    @Test
    @DisplayName("多個持倉獨立管理")
    void testMultiplePositionsIndependentManagement() {
        // Given
        String symbol1 = "2330.TW";
        String symbol2 = "2317.TW";

        // When
        StopLossPrices prices1 = manager.calculateStopLossPrices(
            STRATEGY_NAME, symbol1, 500.0, LocalDateTime.now(), null
        );
        StopLossPrices prices2 = manager.calculateStopLossPrices(
            STRATEGY_NAME, symbol2, 100.0, LocalDateTime.now(), null
        );

        // Then
        assertThat(prices1.getStopLoss()).isEqualTo(475.0);
        assertThat(prices2.getStopLoss()).isEqualTo(95.0);

        // 檢查各自獨立觸發
        StopTrigger trigger1 = manager.checkStopTrigger(
            STRATEGY_NAME, symbol1, 475.0, LocalDateTime.now(), null
        );
        assertThat(trigger1).isNotNull();
        assertThat(trigger1.getType()).isEqualTo(StopTriggerType.STOP_LOSS);

        // symbol2 仍然有效
        StopTrigger trigger2 = manager.checkStopTrigger(
            STRATEGY_NAME, symbol2, 98.0, LocalDateTime.now(), null
        );
        assertThat(trigger2).isNull();
    }

    @Test
    @DisplayName("StopLossConfig 所有 Setter 測試")
    void testStopLossConfigSetters() {
        // Given
        StopLossConfig config = new StopLossConfig();

        // When
        config.setFixedStopLossPercent(0.08);
        config.setTakeProfitPercent(0.12);
        config.setTrailingStopEnabled(true);
        config.setTrailingStopPercent(0.04);
        config.setTrailingStopActivation(0.06);
        config.setTimeBasedStopEnabled(true);
        config.setMaxHoldingHours(48);
        config.setVolatilityAdjustedEnabled(true);
        config.setAtrMultiplier(2.5);
        config.setAtrPeriod(20);
        config.setTechnicalStopEnabled(true);
        config.setSmaBreakPeriod(30);

        // Then
        assertThat(config.getFixedStopLossPercent()).isEqualTo(0.08);
        assertThat(config.getTakeProfitPercent()).isEqualTo(0.12);
        assertThat(config.isTrailingStopEnabled()).isTrue();
        assertThat(config.getTrailingStopPercent()).isEqualTo(0.04);
        assertThat(config.getTrailingStopActivation()).isEqualTo(0.06);
        assertThat(config.isTimeBasedStopEnabled()).isTrue();
        assertThat(config.getMaxHoldingHours()).isEqualTo(48);
        assertThat(config.isVolatilityAdjustedEnabled()).isTrue();
        assertThat(config.getAtrMultiplier()).isEqualTo(2.5);
        assertThat(config.getAtrPeriod()).isEqualTo(20);
        assertThat(config.isTechnicalStopEnabled()).isTrue();
        assertThat(config.getSmaBreakPeriod()).isEqualTo(30);
    }

    @Test
    @DisplayName("StopLossPrices Getters 測試")
    void testStopLossPricesGetters() {
        // Given
        StopLossPrices prices = new StopLossPrices(475.0, 550.0);

        // When & Then
        assertThat(prices.getStopLoss()).isEqualTo(475.0);
        assertThat(prices.getTakeProfit()).isEqualTo(550.0);
    }

    @Test
    @DisplayName("StopTrigger Getters 測試")
    void testStopTriggerGetters() {
        // Given
        StopTrigger trigger = new StopTrigger(
            StopTriggerType.STOP_LOSS,
            475.0,
            "固定停損"
        );

        // When & Then
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.STOP_LOSS);
        assertThat(trigger.getPrice()).isEqualTo(475.0);
        assertThat(trigger.getReason()).isEqualTo("固定停損");
    }

    @Test
    @DisplayName("所有 StopTriggerType 枚舉值測試")
    void testAllStopTriggerTypes() {
        // When & Then
        assertThat(StopTriggerType.values()).containsExactlyInAnyOrder(
            StopTriggerType.STOP_LOSS,
            StopTriggerType.TAKE_PROFIT,
            StopTriggerType.TRAILING_STOP,
            StopTriggerType.TIME_BASED,
            StopTriggerType.TECHNICAL_STOP
        );
    }

    @Test
    @DisplayName("邊界測試 - 停損價格恰好等於當前價格")
    void testBoundaryStopLossPriceEquals() {
        // Given
        double entryPrice = 500.0;
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, LocalDateTime.now(), null);

        // When - 價格恰好等於停損價格
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 475.0, LocalDateTime.now(), null
        );

        // Then - 應該觸發（使用 <= 判斷）
        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.STOP_LOSS);
    }

    @Test
    @DisplayName("邊界測試 - 停利價格恰好等於當前價格")
    void testBoundaryTakeProfitPriceEquals() {
        // Given
        double entryPrice = 500.0;
        manager.calculateStopLossPrices(STRATEGY_NAME, SYMBOL, entryPrice, LocalDateTime.now(), null);

        // When - 價格恰好等於停利價格
        StopTrigger trigger = manager.checkStopTrigger(
            STRATEGY_NAME, SYMBOL, 550.0, LocalDateTime.now(), null
        );

        // Then - 應該觸發（使用 >= 判斷）
        assertThat(trigger).isNotNull();
        assertThat(trigger.getType()).isEqualTo(StopTriggerType.TAKE_PROFIT);
    }
}
