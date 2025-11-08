package com.dreamhouse.trading.core.backtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.*;

/**
 * StrategyConfig 類單元測試
 */
@DisplayName("StrategyConfig 類測試")
class StrategyConfigTest {

    private StrategyConfig config;

    @BeforeEach
    void setUp() {
        config = new StrategyConfig();
    }

    @Test
    @DisplayName("創建空配置")
    void testCreateEmptyConfig() {
        // When & Then
        assertThat(config).isNotNull();
        assertThat(config.getParameterKeys()).isEmpty();
    }

    @Test
    @DisplayName("設置和獲取整數參數")
    void testSetAndGetIntParameter() {
        // Given
        String key = "shortPeriod";
        int value = 10;

        // When
        config.setParameter(key, value);

        // Then
        assertThat(config.getIntParameter(key, 0)).isEqualTo(10);
        assertThat(config.hasParameter(key)).isTrue();
    }

    @Test
    @DisplayName("設置和獲取雙精度參數")
    void testSetAndGetDoubleParameter() {
        // Given
        String key = "threshold";
        double value = 0.05;

        // When
        config.setParameter(key, value);

        // Then
        assertThat(config.getDoubleParameter(key, 0.0)).isEqualTo(0.05);
    }

    @Test
    @DisplayName("設置和獲取布林參數")
    void testSetAndGetBooleanParameter() {
        // Given
        String key = "debug";
        boolean value = true;

        // When
        config.setParameter(key, value);

        // Then
        assertThat(config.getBooleanParameter(key, false)).isTrue();
    }

    @Test
    @DisplayName("設置和獲取字串參數")
    void testSetAndGetStringParameter() {
        // Given
        String key = "symbol";
        String value = "2330.TW";

        // When
        config.setParameter(key, value);

        // Then
        assertThat(config.getStringParameter(key, "")).isEqualTo("2330.TW");
    }

    @Test
    @DisplayName("獲取不存在的參數返回默認值")
    void testGetNonExistentParameterReturnsDefault() {
        // When & Then
        assertThat(config.getIntParameter("nonexistent", 99)).isEqualTo(99);
        assertThat(config.getDoubleParameter("nonexistent", 3.14)).isEqualTo(3.14);
        assertThat(config.getBooleanParameter("nonexistent", true)).isTrue();
        assertThat(config.getStringParameter("nonexistent", "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("泛型 getParameter 方法")
    void testGenericGetParameter() {
        // Given
        config.setParameter("value", 42);

        // When
        Integer result = config.getParameter("value", 0);

        // Then
        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("hasParameter 檢查參數存在")
    void testHasParameter() {
        // Given
        config.setParameter("key1", "value1");

        // When & Then
        assertThat(config.hasParameter("key1")).isTrue();
        assertThat(config.hasParameter("key2")).isFalse();
    }

    @Test
    @DisplayName("移除參數")
    void testRemoveParameter() {
        // Given
        config.setParameter("key", "value");
        assertThat(config.hasParameter("key")).isTrue();

        // When
        config.removeParameter("key");

        // Then
        assertThat(config.hasParameter("key")).isFalse();
    }

    @Test
    @DisplayName("清空所有參數")
    void testClear() {
        // Given
        config.setParameter("key1", "value1");
        config.setParameter("key2", "value2");
        config.setParameter("key3", "value3");

        // When
        config.clear();

        // Then
        assertThat(config.getParameterKeys()).isEmpty();
        assertThat(config.hasParameter("key1")).isFalse();
    }

    @Test
    @DisplayName("獲取所有參數鍵")
    void testGetParameterKeys() {
        // Given
        config.setParameter("key1", 1);
        config.setParameter("key2", 2);
        config.setParameter("key3", 3);

        // When
        var keys = config.getParameterKeys();

        // Then
        assertThat(keys).hasSize(3);
        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    @DisplayName("複製配置")
    void testCopyConfig() {
        // Given
        config.setParameter("shortPeriod", 10);
        config.setParameter("longPeriod", 20);
        config.setParameter("symbol", "2330.TW");

        // When
        StrategyConfig copy = config.copy();

        // Then
        assertThat(copy).isNotSameAs(config);
        assertThat(copy.getIntParameter("shortPeriod", 0)).isEqualTo(10);
        assertThat(copy.getIntParameter("longPeriod", 0)).isEqualTo(20);
        assertThat(copy.getStringParameter("symbol", "")).isEqualTo("2330.TW");

        // 修改副本不應影響原配置
        copy.setParameter("shortPeriod", 15);
        assertThat(config.getIntParameter("shortPeriod", 0)).isEqualTo(10);
    }

    @Test
    @DisplayName("toString 方法")
    void testToString() {
        // Given
        config.setParameter("key", "value");

        // When
        String result = config.toString();

        // Then
        assertThat(result).contains("StrategyConfig");
        assertThat(result).contains("parameters");
    }

    @Test
    @DisplayName("類型不匹配時返回默認值")
    void testTypeMismatchReturnsDefault() {
        // Given - 設置字串，但嘗試以整數讀取
        config.setParameter("key", "not a number");

        // When & Then
        assertThat(config.getIntParameter("key", 999)).isEqualTo(999);
    }

    @Test
    @DisplayName("Number 類型自動轉換 - Integer 轉 Double")
    void testNumberTypeConversion() {
        // Given
        config.setParameter("value", 42);

        // When & Then
        assertThat(config.getDoubleParameter("value", 0.0)).isEqualTo(42.0);
    }

    @Test
    @DisplayName("覆蓋已存在的參數")
    void testOverwriteExistingParameter() {
        // Given
        config.setParameter("key", "oldValue");

        // When
        config.setParameter("key", "newValue");

        // Then
        assertThat(config.getStringParameter("key", "")).isEqualTo("newValue");
    }

    @Test
    @DisplayName("複雜場景 - SMA 策略配置")
    void testComplexScenarioSmaStrategy() {
        // Given - 模擬 SimpleMovingAverageStrategy 的配置
        config.setParameter("shortPeriod", 10);
        config.setParameter("longPeriod", 20);
        config.setParameter("symbol", "2330.TW");
        config.setParameter("maxPosition", 1000);
        config.setParameter("debug", false);

        // When & Then
        assertThat(config.getIntParameter("shortPeriod", 5)).isEqualTo(10);
        assertThat(config.getIntParameter("longPeriod", 30)).isEqualTo(20);
        assertThat(config.getStringParameter("symbol", "STOCK")).isEqualTo("2330.TW");
        assertThat(config.getIntParameter("maxPosition", 500)).isEqualTo(1000);
        assertThat(config.getBooleanParameter("debug", true)).isFalse();
    }

    @Test
    @DisplayName("複雜場景 - RSI 策略配置")
    void testComplexScenarioRsiStrategy() {
        // Given - 模擬 RSIStrategy 的配置
        config.setParameter("rsiPeriod", 14);
        config.setParameter("oversoldThreshold", 30.0);
        config.setParameter("overboughtThreshold", 70.0);
        config.setParameter("symbol", "2317.TW");

        // When & Then
        assertThat(config.getIntParameter("rsiPeriod", 14)).isEqualTo(14);
        assertThat(config.getDoubleParameter("oversoldThreshold", 20.0)).isEqualTo(30.0);
        assertThat(config.getDoubleParameter("overboughtThreshold", 80.0)).isEqualTo(70.0);
        assertThat(config.getStringParameter("symbol", "")).isEqualTo("2317.TW");
    }
}
