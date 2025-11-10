package com.dreamhouse.trading.core.cache;

/**
 * 緩存配置類
 * 定義LRU和TTL策略的配置參數
 */
public class CacheConfig {

    // LRU緩存最大條目數
    private int maxCacheSize = 10000;

    // TTL（Time To Live）- 數據過期時間（秒）
    private long ttlSeconds = 3600; // 1小時

    // 是否啟用LRU緩存
    private boolean lruEnabled = true;

    // 是否啟用TTL過期
    private boolean ttlEnabled = true;

    // 自動清理間隔（秒）
    private long cleanupIntervalSeconds = 600; // 10分鐘

    // 保留歷史數據天數
    private int retentionDays = 90;

    public CacheConfig() {
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isLruEnabled() {
        return lruEnabled;
    }

    public void setLruEnabled(boolean lruEnabled) {
        this.lruEnabled = lruEnabled;
    }

    public boolean isTtlEnabled() {
        return ttlEnabled;
    }

    public void setTtlEnabled(boolean ttlEnabled) {
        this.ttlEnabled = ttlEnabled;
    }

    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
               "maxCacheSize=" + maxCacheSize +
               ", ttlSeconds=" + ttlSeconds +
               ", lruEnabled=" + lruEnabled +
               ", ttlEnabled=" + ttlEnabled +
               ", cleanupIntervalSeconds=" + cleanupIntervalSeconds +
               ", retentionDays=" + retentionDays +
               '}';
    }

    /**
     * 創建默認配置
     */
    public static CacheConfig defaultConfig() {
        return new CacheConfig();
    }

    /**
     * 創建高性能配置（較短TTL，較大緩存）
     */
    public static CacheConfig highPerformanceConfig() {
        CacheConfig config = new CacheConfig();
        config.setMaxCacheSize(50000);
        config.setTtlSeconds(1800); // 30分鐘
        config.setCleanupIntervalSeconds(300); // 5分鐘清理
        return config;
    }

    /**
     * 創建低內存配置（較小緩存，較短保留期）
     */
    public static CacheConfig lowMemoryConfig() {
        CacheConfig config = new CacheConfig();
        config.setMaxCacheSize(5000);
        config.setTtlSeconds(1800); // 30分鐘
        config.setRetentionDays(30); // 只保留30天
        return config;
    }
}
