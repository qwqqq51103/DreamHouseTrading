package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.DataSourceManager.DataSourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DataSourceManager Tests")
class DataSourceManagerTest {

    private DataSourceManager manager;

    @BeforeEach
    void setUp() {
        deleteConfig();
        manager = new DataSourceManager();
        manager.setMarketCollectorConfig(
                "jdbc:h2:mem:datasource_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            MarketDataFeed feed = manager.getCurrentDataSource();
            if (feed != null) {
                feed.stop();
            }
        }
        deleteConfig();
    }

    @Test
    void defaultDataSourceIsMarketCollector() {
        assertEquals(DataSourceType.MARKET_COLLECTOR, manager.getCurrentType());
        assertInstanceOf(MarketDataCollectorFeed.class, manager.getCurrentDataSource());
    }

    @Test
    void canSwitchAmongSupportedDataSources() {
        assertInstanceOf(FinMindFeed.class, manager.switchDataSource(DataSourceType.FINMIND));
        assertEquals(DataSourceType.FINMIND, manager.getCurrentType());

        assertInstanceOf(YahooFinanceFeed.class, manager.switchDataSource(DataSourceType.YAHOO_FINANCE));
        assertEquals(DataSourceType.YAHOO_FINANCE, manager.getCurrentType());

        assertInstanceOf(MarketDataCollectorFeed.class, manager.switchDataSource(DataSourceType.MARKET_COLLECTOR));
        assertEquals(DataSourceType.MARKET_COLLECTOR, manager.getCurrentType());
    }

    @Test
    void onlyFinMindRequiresApiKey() {
        assertTrue(manager.requiresApiKey(DataSourceType.FINMIND));
        assertFalse(manager.requiresApiKey(DataSourceType.MARKET_COLLECTOR));
        assertFalse(manager.requiresApiKey(DataSourceType.YAHOO_FINANCE));
    }

    @Test
    void finMindApiKeyAccessorsRemainCompatible() {
        manager.setFinMindApiToken("FM");

        assertEquals("FM", manager.getApiKey(DataSourceType.FINMIND));
        assertEquals("", manager.getApiKey(DataSourceType.MARKET_COLLECTOR));
        assertEquals("", manager.getApiKey(DataSourceType.YAHOO_FINANCE));
    }

    @Test
    void marketCollectorJdbcConfigCanBeUpdated() {
        String jdbcUrl = "jdbc:h2:mem:market_config_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        manager.setMarketCollectorConfig(jdbcUrl, "sa", "");

        assertEquals(jdbcUrl, manager.getMarketCollectorJdbcUrl());
        assertEquals("sa", manager.getMarketCollectorUser());
        assertEquals("", manager.getMarketCollectorPassword());
    }

    @Test
    void dataSourceTypeListOnlyIncludesSupportedSources() {
        assertEquals(3, DataSourceType.values().length);
        assertNotNull(DataSourceType.valueOf("MARKET_COLLECTOR"));
        assertNotNull(DataSourceType.valueOf("FINMIND"));
        assertNotNull(DataSourceType.valueOf("YAHOO_FINANCE"));
    }

    @Test
    void displayNamesAreStable() {
        assertEquals("MarketDataCollector", DataSourceType.MARKET_COLLECTOR.getDisplayNameZh());
        assertEquals("MarketDataCollector", DataSourceType.MARKET_COLLECTOR.getDisplayNameEn());
        assertEquals("FinMind", DataSourceType.FINMIND.getDisplayName(false));
        assertEquals("Yahoo Finance", DataSourceType.YAHOO_FINANCE.getDisplayName(false));
    }

    private void deleteConfig() {
        File configFile = new File("datasource.properties");
        if (configFile.exists()) {
            configFile.delete();
        }
    }
}
