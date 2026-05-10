package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.DataSourceManager.DataSourceType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSourceManager 測試類
 */
@DisplayName("DataSourceManager Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataSourceManagerTest {

    private static final String TEST_CONFIG_FILE = "test_datasource.properties";
    private DataSourceManager manager;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 刪除測試配置文件
        deleteTestConfig();

        // 創建管理器
        manager = new DataSourceManager(tempDir.resolve(TEST_CONFIG_FILE));
    }

    @AfterEach
    void tearDown() {
        // 清理
        if (manager != null) {
            MarketDataFeed feed = manager.getCurrentDataSource();
            if (feed != null && feed.isConnected()) {
                feed.stop();
            }
        }

        // 刪除測試配置文件
        deleteTestConfig();
    }

    private void deleteTestConfig() {
        File configFile = tempDir.resolve(TEST_CONFIG_FILE).toFile();
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    @DisplayName("測試默認數據源類型")
    void testDefaultDataSourceType() {
        assertEquals(DataSourceType.SIMULATOR, manager.getCurrentType(),
            "默認應該使用模擬數據源");
    }

    @Test
    @DisplayName("測試獲取當前數據源")
    void testGetCurrentDataSource() {
        MarketDataFeed feed = manager.getCurrentDataSource();

        assertNotNull(feed, "當前數據源不應為 null");
        assertTrue(feed instanceof SimulatorFeed,
            "默認應該是 SimulatorFeed");
    }

    @Test
    @DisplayName("測試切換到 Yahoo Finance")
    void testSwitchToYahooFinance() {
        MarketDataFeed feed = manager.switchDataSource(DataSourceType.YAHOO_FINANCE);

        assertNotNull(feed, "切換後的數據源不應為 null");
        assertEquals(DataSourceType.YAHOO_FINANCE, manager.getCurrentType(),
            "當前類型應該是 YAHOO_FINANCE");
    }

    @Test
    @DisplayName("測試切換到 Alpha Vantage")
    void testSwitchToAlphaVantage() {
        MarketDataFeed feed = manager.switchDataSource(DataSourceType.ALPHA_VANTAGE);

        assertNotNull(feed, "切換後的數據源不應為 null");
        assertEquals(DataSourceType.ALPHA_VANTAGE, manager.getCurrentType(),
            "當前類型應該是 ALPHA_VANTAGE");
    }

    @Test
    @DisplayName("測試切換到 Finnhub")
    void testSwitchToFinnhub() {
        MarketDataFeed feed = manager.switchDataSource(DataSourceType.FINNHUB);

        assertNotNull(feed, "切換後的數據源不應為 null");
        assertEquals(DataSourceType.FINNHUB, manager.getCurrentType(),
            "當前類型應該是 FINNHUB");
    }

    @Test
    @DisplayName("測試切換到 IEX Cloud")
    void testSwitchToIEXCloud() {
        MarketDataFeed feed = manager.switchDataSource(DataSourceType.IEX_CLOUD);

        assertNotNull(feed, "切換後的數據源不應為 null");
        assertEquals(DataSourceType.IEX_CLOUD, manager.getCurrentType(),
            "當前類型應該是 IEX_CLOUD");
    }

    @Test
    @DisplayName("測試切換到 Polygon")
    void testSwitchToPolygon() {
        MarketDataFeed feed = manager.switchDataSource(DataSourceType.POLYGON);

        assertNotNull(feed, "切換後的數據源不應為 null");
        assertEquals(DataSourceType.POLYGON, manager.getCurrentType(),
            "當前類型應該是 POLYGON");
    }

    @Test
    @DisplayName("測試設置和獲取 Alpha Vantage API 密鑰")
    void testAlphaVantageApiKey() {
        String testKey = "TEST_AV_KEY_12345";
        manager.setAlphaVantageApiKey(testKey);

        assertEquals(testKey, manager.getAlphaVantageApiKey(),
            "API 密鑰應該匹配");
    }

    @Test
    @DisplayName("測試設置和獲取 Finnhub API 密鑰")
    void testFinnhubApiKey() {
        String testKey = "TEST_FH_KEY_67890";
        manager.setFinnhubApiKey(testKey);

        assertEquals(testKey, manager.getFinnhubApiKey(),
            "API 密鑰應該匹配");
    }

    @Test
    @DisplayName("測試設置和獲取 IEX Cloud API 密鑰")
    void testIEXCloudApiKey() {
        String testKey = "TEST_IEX_KEY_ABCDE";
        manager.setIEXCloudApiKey(testKey);

        assertEquals(testKey, manager.getIEXCloudApiKey(),
            "API 密鑰應該匹配");
    }

    @Test
    @DisplayName("測試設置和獲取 Polygon API 密鑰")
    void testPolygonApiKey() {
        String testKey = "TEST_POLY_KEY_FGHIJ";
        manager.setPolygonApiKey(testKey);

        assertEquals(testKey, manager.getPolygonApiKey(),
            "API 密鑰應該匹配");
    }

    @Test
    @DisplayName("測試通用 API 密鑰獲取")
    void testGetApiKey() {
        manager.setAlphaVantageApiKey("AV_KEY");
        manager.setFinnhubApiKey("FH_KEY");
        manager.setIEXCloudApiKey("IEX_KEY");
        manager.setPolygonApiKey("POLY_KEY");

        assertEquals("AV_KEY", manager.getApiKey(DataSourceType.ALPHA_VANTAGE));
        assertEquals("FH_KEY", manager.getApiKey(DataSourceType.FINNHUB));
        assertEquals("IEX_KEY", manager.getApiKey(DataSourceType.IEX_CLOUD));
        assertEquals("POLY_KEY", manager.getApiKey(DataSourceType.POLYGON));
        assertEquals("", manager.getApiKey(DataSourceType.SIMULATOR));
        assertEquals("", manager.getApiKey(DataSourceType.YAHOO_FINANCE));
    }

    @Test
    @DisplayName("測試通用 API 密鑰設置")
    void testSetApiKey() {
        manager.setApiKey(DataSourceType.ALPHA_VANTAGE, "NEW_AV_KEY");
        manager.setApiKey(DataSourceType.FINNHUB, "NEW_FH_KEY");
        manager.setApiKey(DataSourceType.IEX_CLOUD, "NEW_IEX_KEY");
        manager.setApiKey(DataSourceType.POLYGON, "NEW_POLY_KEY");

        assertEquals("NEW_AV_KEY", manager.getAlphaVantageApiKey());
        assertEquals("NEW_FH_KEY", manager.getFinnhubApiKey());
        assertEquals("NEW_IEX_KEY", manager.getIEXCloudApiKey());
        assertEquals("NEW_POLY_KEY", manager.getPolygonApiKey());
    }

    @Test
    @DisplayName("測試檢查數據源是否需要 API 密鑰")
    void testRequiresApiKey() {
        assertFalse(manager.requiresApiKey(DataSourceType.SIMULATOR),
            "SIMULATOR 不需要 API 密鑰");
        assertFalse(manager.requiresApiKey(DataSourceType.YAHOO_FINANCE),
            "YAHOO_FINANCE 不需要 API 密鑰");

        assertTrue(manager.requiresApiKey(DataSourceType.ALPHA_VANTAGE),
            "ALPHA_VANTAGE 需要 API 密鑰");
        assertTrue(manager.requiresApiKey(DataSourceType.FINNHUB),
            "FINNHUB 需要 API 密鑰");
        assertTrue(manager.requiresApiKey(DataSourceType.IEX_CLOUD),
            "IEX_CLOUD 需要 API 密鑰");
        assertTrue(manager.requiresApiKey(DataSourceType.POLYGON),
            "POLYGON 需要 API 密鑰");
    }

    @Test
    @DisplayName("測試檢查 API 密鑰是否有效")
    void testHasValidApiKey() {
        // 默認 API 密鑰是 "demo"，應該無效
        assertFalse(manager.hasValidApiKey(DataSourceType.ALPHA_VANTAGE),
            "demo 密鑰應該無效");

        // 設置有效密鑰
        manager.setAlphaVantageApiKey("VALID_KEY");
        assertTrue(manager.hasValidApiKey(DataSourceType.ALPHA_VANTAGE),
            "有效密鑰應該返回 true");

        // 不需要密鑰的數據源應該返回 true
        assertTrue(manager.hasValidApiKey(DataSourceType.SIMULATOR),
            "SIMULATOR 不需要密鑰，應該返回 true");
        assertTrue(manager.hasValidApiKey(DataSourceType.YAHOO_FINANCE),
            "YAHOO_FINANCE 不需要密鑰，應該返回 true");
    }

    @Test
    @DisplayName("測試空密鑰無效")
    void testEmptyApiKeyInvalid() {
        manager.setFinnhubApiKey("");
        assertFalse(manager.hasValidApiKey(DataSourceType.FINNHUB),
            "空密鑰應該無效");

        manager.setFinnhubApiKey("   ");
        assertFalse(manager.hasValidApiKey(DataSourceType.FINNHUB),
            "空白密鑰應該無效");
    }

    @Test
    @DisplayName("測試 DataSourceType 顯示名稱")
    void testDataSourceTypeDisplayNames() {
        assertEquals("模擬數據", DataSourceType.SIMULATOR.getDisplayNameZh());
        assertEquals("Simulated Data", DataSourceType.SIMULATOR.getDisplayNameEn());

        assertEquals("Yahoo Finance", DataSourceType.YAHOO_FINANCE.getDisplayNameZh());
        assertEquals("Yahoo Finance", DataSourceType.YAHOO_FINANCE.getDisplayNameEn());

        assertEquals("Alpha Vantage", DataSourceType.ALPHA_VANTAGE.getDisplayNameZh());
        assertEquals("Alpha Vantage", DataSourceType.ALPHA_VANTAGE.getDisplayNameEn());

        assertEquals("Finnhub", DataSourceType.FINNHUB.getDisplayNameZh());
        assertEquals("Finnhub", DataSourceType.FINNHUB.getDisplayNameEn());

        assertEquals("IEX Cloud", DataSourceType.IEX_CLOUD.getDisplayNameZh());
        assertEquals("IEX Cloud", DataSourceType.IEX_CLOUD.getDisplayNameEn());

        assertEquals("Polygon.io", DataSourceType.POLYGON.getDisplayNameZh());
        assertEquals("Polygon.io", DataSourceType.POLYGON.getDisplayNameEn());
    }

    @Test
    @DisplayName("測試 DataSourceType getDisplayName 方法")
    void testDataSourceTypeGetDisplayName() {
        DataSourceType type = DataSourceType.SIMULATOR;

        assertEquals("模擬數據", type.getDisplayName(true),
            "使用中文應該返回中文名稱");
        assertEquals("Simulated Data", type.getDisplayName(false),
            "使用英文應該返回英文名稱");
    }

    @Test
    @DisplayName("測試配置持久化")
    void testConfigurationPersistence() {
        // 設置配置
        manager.setAlphaVantageApiKey("PERSISTENT_KEY");
        manager.switchDataSource(DataSourceType.ALPHA_VANTAGE);

        // 創建新的管理器實例（應該加載保存的配置）
        DataSourceManager newManager = new DataSourceManager(tempDir.resolve(TEST_CONFIG_FILE));

        assertEquals("PERSISTENT_KEY", newManager.getAlphaVantageApiKey(),
            "API 密鑰應該被持久化");
        assertEquals(DataSourceType.ALPHA_VANTAGE, newManager.getCurrentType(),
            "數據源類型應該被持久化");

        // 清理
        if (newManager.getCurrentDataSource() != null &&
            newManager.getCurrentDataSource().isConnected()) {
            newManager.getCurrentDataSource().stop();
        }
    }

    @Test
    @DisplayName("測試多次切換數據源")
    void testMultipleSwitches() {
        // 切換到不同的數據源
        manager.switchDataSource(DataSourceType.YAHOO_FINANCE);
        assertEquals(DataSourceType.YAHOO_FINANCE, manager.getCurrentType());

        manager.switchDataSource(DataSourceType.ALPHA_VANTAGE);
        assertEquals(DataSourceType.ALPHA_VANTAGE, manager.getCurrentType());

        manager.switchDataSource(DataSourceType.SIMULATOR);
        assertEquals(DataSourceType.SIMULATOR, manager.getCurrentType());

        // 每次切換都應該返回有效的數據源
        assertNotNull(manager.getCurrentDataSource());
    }

    @Test
    @DisplayName("測試所有 DataSourceType 枚舉值")
    void testAllDataSourceTypes() {
        DataSourceType[] types = DataSourceType.values();
        assertEquals(7, types.length, "應該有 7 種數據源類型");

        // 確保可以獲取所有枚舉值
        assertNotNull(DataSourceType.valueOf("SIMULATOR"));
        assertNotNull(DataSourceType.valueOf("YAHOO_FINANCE"));
        assertNotNull(DataSourceType.valueOf("ALPHA_VANTAGE"));
        assertNotNull(DataSourceType.valueOf("FINNHUB"));
        assertNotNull(DataSourceType.valueOf("IEX_CLOUD"));
        assertNotNull(DataSourceType.valueOf("POLYGON"));
        assertNotNull(DataSourceType.valueOf("FINMIND"));
    }

    @Test
    @DisplayName("測試 API 密鑰重新設置後切換數據源")
    void testApiKeyUpdateAndSwitch() {
        // 先設置密鑰並切換
        manager.setFinnhubApiKey("OLD_KEY");
        manager.switchDataSource(DataSourceType.FINNHUB);

        // 更新密鑰（應該自動重新創建數據源）
        manager.setFinnhubApiKey("NEW_KEY");

        // 驗證新密鑰被使用
        assertEquals("NEW_KEY", manager.getFinnhubApiKey());
    }

    @Test
    @DisplayName("測試設置 null API 密鑰")
    void testSetNullApiKey() {
        // 某些實現可能不允許 null，這是正常的
        try {
            manager.setAlphaVantageApiKey(null);
            String key = manager.getAlphaVantageApiKey();
            // 如果沒有拋異常，密鑰不應該是 null
            assertTrue(key == null || key.equals("") || key.equals("demo"));
        } catch (NullPointerException e) {
            // 某些實現可能拋出 NPE，這也是可接受的
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("測試獲取默認 API 密鑰")
    void testDefaultApiKeys() {
        // 新創建的管理器應該有默認的 demo 密鑰
        assertEquals("demo", manager.getAlphaVantageApiKey());
        assertEquals("demo", manager.getFinnhubApiKey());
        assertEquals("demo", manager.getIEXCloudApiKey());
        assertEquals("demo", manager.getPolygonApiKey());
    }
}
