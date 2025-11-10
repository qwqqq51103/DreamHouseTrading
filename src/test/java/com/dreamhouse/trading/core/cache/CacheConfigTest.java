package com.dreamhouse.trading.core.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheConfig 測試類
 */
@DisplayName("CacheConfig Tests")
class CacheConfigTest {

    @Test
    @DisplayName("測試默認配置")
    void testDefaultConfig() {
        CacheConfig config = CacheConfig.defaultConfig();

        assertNotNull(config, "默認配置不應為 null");
        assertEquals(10000, config.getMaxCacheSize(), "默認最大緩存大小應為 10000");
        assertEquals(3600, config.getTtlSeconds(), "默認 TTL 應為 3600 秒");
        assertTrue(config.isLruEnabled(), "默認應啟用 LRU");
        assertTrue(config.isTtlEnabled(), "默認應啟用 TTL");
        assertEquals(600, config.getCleanupIntervalSeconds(), "默認清理間隔應為 600 秒");
        assertEquals(90, config.getRetentionDays(), "默認保留天數應為 90 天");
    }

    @Test
    @DisplayName("測試高性能配置")
    void testHighPerformanceConfig() {
        CacheConfig config = CacheConfig.highPerformanceConfig();

        assertNotNull(config, "高性能配置不應為 null");
        assertEquals(50000, config.getMaxCacheSize(), "高性能配置的最大緩存大小應為 50000");
        assertEquals(7200, config.getTtlSeconds(), "高性能配置的 TTL 應為 7200 秒");
        assertTrue(config.isLruEnabled(), "高性能配置應啟用 LRU");
        assertTrue(config.isTtlEnabled(), "高性能配置應啟用 TTL");
        assertEquals(1800, config.getCleanupIntervalSeconds(), "高性能配置的清理間隔應為 1800 秒");
        assertEquals(180, config.getRetentionDays(), "高性能配置的保留天數應為 180 天");
    }

    @Test
    @DisplayName("測試低內存配置")
    void testLowMemoryConfig() {
        CacheConfig config = CacheConfig.lowMemoryConfig();

        assertNotNull(config, "低內存配置不應為 null");
        assertEquals(1000, config.getMaxCacheSize(), "低內存配置的最大緩存大小應為 1000");
        assertEquals(1800, config.getTtlSeconds(), "低內存配置的 TTL 應為 1800 秒");
        assertTrue(config.isLruEnabled(), "低內存配置應啟用 LRU");
        assertTrue(config.isTtlEnabled(), "低內存配置應啟用 TTL");
        assertEquals(300, config.getCleanupIntervalSeconds(), "低內存配置的清理間隔應為 300 秒");
        assertEquals(30, config.getRetentionDays(), "低內存配置的保留天數應為 30 天");
    }

    @Test
    @DisplayName("測試配置構建器 - 設置最大緩存大小")
    void testBuilder_SetMaxCacheSize() {
        CacheConfig config = new CacheConfig()
            .setMaxCacheSize(20000);

        assertEquals(20000, config.getMaxCacheSize(), "應該設置最大緩存大小");
    }

    @Test
    @DisplayName("測試配置構建器 - 設置 TTL")
    void testBuilder_SetTtlSeconds() {
        CacheConfig config = new CacheConfig()
            .setTtlSeconds(5000);

        assertEquals(5000, config.getTtlSeconds(), "應該設置 TTL");
    }

    @Test
    @DisplayName("測試配置構建器 - 啟用/禁用 LRU")
    void testBuilder_SetLruEnabled() {
        CacheConfig config = new CacheConfig()
            .setLruEnabled(false);

        assertFalse(config.isLruEnabled(), "應該禁用 LRU");

        config.setLruEnabled(true);
        assertTrue(config.isLruEnabled(), "應該啟用 LRU");
    }

    @Test
    @DisplayName("測試配置構建器 - 啟用/禁用 TTL")
    void testBuilder_SetTtlEnabled() {
        CacheConfig config = new CacheConfig()
            .setTtlEnabled(false);

        assertFalse(config.isTtlEnabled(), "應該禁用 TTL");

        config.setTtlEnabled(true);
        assertTrue(config.isTtlEnabled(), "應該啟用 TTL");
    }

    @Test
    @DisplayName("測試配置構建器 - 設置清理間隔")
    void testBuilder_SetCleanupInterval() {
        CacheConfig config = new CacheConfig()
            .setCleanupIntervalSeconds(900);

        assertEquals(900, config.getCleanupIntervalSeconds(), "應該設置清理間隔");
    }

    @Test
    @DisplayName("測試配置構建器 - 設置保留天數")
    void testBuilder_SetRetentionDays() {
        CacheConfig config = new CacheConfig()
            .setRetentionDays(120);

        assertEquals(120, config.getRetentionDays(), "應該設置保留天數");
    }

    @Test
    @DisplayName("測試鏈式調用")
    void testBuilder_ChainedCalls() {
        CacheConfig config = new CacheConfig()
            .setMaxCacheSize(15000)
            .setTtlSeconds(4500)
            .setLruEnabled(false)
            .setTtlEnabled(false)
            .setCleanupIntervalSeconds(450)
            .setRetentionDays(60);

        assertEquals(15000, config.getMaxCacheSize());
        assertEquals(4500, config.getTtlSeconds());
        assertFalse(config.isLruEnabled());
        assertFalse(config.isTtlEnabled());
        assertEquals(450, config.getCleanupIntervalSeconds());
        assertEquals(60, config.getRetentionDays());
    }

    @Test
    @DisplayName("測試配置toString")
    void testToString() {
        CacheConfig config = CacheConfig.defaultConfig();
        String str = config.toString();

        assertNotNull(str, "toString 不應為 null");
        assertTrue(str.contains("CacheConfig"), "toString 應該包含類名");
        assertTrue(str.contains("maxCacheSize"), "toString 應該包含 maxCacheSize");
        assertTrue(str.contains("10000"), "toString 應該包含默認值");
    }

    @Test
    @DisplayName("測試配置複製")
    void testConfigCopy() {
        CacheConfig original = CacheConfig.defaultConfig();
        CacheConfig copy = new CacheConfig()
            .setMaxCacheSize(original.getMaxCacheSize())
            .setTtlSeconds(original.getTtlSeconds())
            .setLruEnabled(original.isLruEnabled())
            .setTtlEnabled(original.isTtlEnabled())
            .setCleanupIntervalSeconds(original.getCleanupIntervalSeconds())
            .setRetentionDays(original.getRetentionDays());

        assertEquals(original.getMaxCacheSize(), copy.getMaxCacheSize());
        assertEquals(original.getTtlSeconds(), copy.getTtlSeconds());
        assertEquals(original.isLruEnabled(), copy.isLruEnabled());
        assertEquals(original.isTtlEnabled(), copy.isTtlEnabled());
        assertEquals(original.getCleanupIntervalSeconds(), copy.getCleanupIntervalSeconds());
        assertEquals(original.getRetentionDays(), copy.getRetentionDays());
    }

    @Test
    @DisplayName("測試極端值配置")
    void testExtremeValues() {
        CacheConfig config = new CacheConfig()
            .setMaxCacheSize(Integer.MAX_VALUE)
            .setTtlSeconds(Long.MAX_VALUE)
            .setCleanupIntervalSeconds(Long.MAX_VALUE)
            .setRetentionDays(Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, config.getMaxCacheSize());
        assertEquals(Long.MAX_VALUE, config.getTtlSeconds());
        assertEquals(Long.MAX_VALUE, config.getCleanupIntervalSeconds());
        assertEquals(Integer.MAX_VALUE, config.getRetentionDays());
    }

    @Test
    @DisplayName("測試最小值配置")
    void testMinimumValues() {
        CacheConfig config = new CacheConfig()
            .setMaxCacheSize(1)
            .setTtlSeconds(1)
            .setCleanupIntervalSeconds(1)
            .setRetentionDays(1);

        assertEquals(1, config.getMaxCacheSize());
        assertEquals(1, config.getTtlSeconds());
        assertEquals(1, config.getCleanupIntervalSeconds());
        assertEquals(1, config.getRetentionDays());
    }

    @Test
    @DisplayName("測試零值配置")
    void testZeroValues() {
        CacheConfig config = new CacheConfig()
            .setMaxCacheSize(0)
            .setTtlSeconds(0)
            .setCleanupIntervalSeconds(0)
            .setRetentionDays(0);

        assertEquals(0, config.getMaxCacheSize());
        assertEquals(0, config.getTtlSeconds());
        assertEquals(0, config.getCleanupIntervalSeconds());
        assertEquals(0, config.getRetentionDays());
    }

    @Test
    @DisplayName("測試預設配置的不可變性")
    void testPresetConfigImmutability() {
        CacheConfig defaultConfig = CacheConfig.defaultConfig();
        int originalSize = defaultConfig.getMaxCacheSize();

        // 修改配置
        defaultConfig.setMaxCacheSize(99999);

        // 再次獲取預設配置應該是新實例
        CacheConfig newDefaultConfig = CacheConfig.defaultConfig();

        // 注意：由於我們直接返回新實例，所以這個測試確保每次調用都返回新配置
        assertNotSame(defaultConfig, newDefaultConfig, "每次調用應該返回新實例");
    }

    @Test
    @DisplayName("測試配置的邏輯一致性")
    void testConfigurationLogicalConsistency() {
        // 高性能配置應該有更大的緩存和更長的保留時間
        CacheConfig highPerf = CacheConfig.highPerformanceConfig();
        CacheConfig defaultConf = CacheConfig.defaultConfig();
        CacheConfig lowMem = CacheConfig.lowMemoryConfig();

        assertTrue(highPerf.getMaxCacheSize() > defaultConf.getMaxCacheSize(),
            "高性能配置的緩存應該更大");
        assertTrue(defaultConf.getMaxCacheSize() > lowMem.getMaxCacheSize(),
            "默認配置的緩存應該大於低內存配置");

        assertTrue(highPerf.getRetentionDays() > defaultConf.getRetentionDays(),
            "高性能配置的保留天數應該更長");
        assertTrue(defaultConf.getRetentionDays() > lowMem.getRetentionDays(),
            "默認配置的保留天數應該大於低內存配置");
    }
}
