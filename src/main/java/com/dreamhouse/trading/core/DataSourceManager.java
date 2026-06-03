package com.dreamhouse.trading.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Manages the active market data source and its local configuration.
 */
public class DataSourceManager {

    public enum DataSourceType {
        MARKET_COLLECTOR("MarketDataCollector", "MarketDataCollector"),
        FINMIND("FinMind", "FinMind"),
        YAHOO_FINANCE("Yahoo Finance", "Yahoo Finance");

        private final String displayNameZh;
        private final String displayNameEn;

        DataSourceType(String displayNameZh, String displayNameEn) {
            this.displayNameZh = displayNameZh;
            this.displayNameEn = displayNameEn;
        }

        public String getDisplayName(boolean useChinese) {
            return useChinese ? displayNameZh : displayNameEn;
        }

        public String getDisplayNameZh() {
            return displayNameZh;
        }

        public String getDisplayNameEn() {
            return displayNameEn;
        }
    }

    private static final String CONFIG_FILE = "datasource.properties";
    private static final String PROP_DATASOURCE_TYPE = "datasource.type";
    private static final String PROP_FINMIND_API_TOKEN = "finmind.apitoken";
    private static final String PROP_MARKET_COLLECTOR_JDBC_URL = "marketcollector.jdbc.url";
    private static final String PROP_MARKET_COLLECTOR_USER = "marketcollector.jdbc.user";
    private static final String PROP_MARKET_COLLECTOR_PASSWORD = "marketcollector.jdbc.password";

    private DataSourceType currentType;
    private MarketDataFeed currentFeed;
    private Properties config;

    public DataSourceManager() {
        loadConfig();
        currentType = parseDataSourceType(config.getProperty(PROP_DATASOURCE_TYPE, DataSourceType.MARKET_COLLECTOR.name()));
    }

    public MarketDataFeed getCurrentDataSource() {
        if (currentFeed == null) {
            currentFeed = createDataSource(currentType);
        }
        return currentFeed;
    }

    public synchronized MarketDataFeed switchDataSource(DataSourceType type) {
        System.out.println("[DataSourceManager] Switching data source: " + currentType + " -> " + type);

        if (currentFeed != null && currentFeed.isConnected()) {
            currentFeed.stop();
        }

        currentType = type != null ? type : DataSourceType.MARKET_COLLECTOR;
        currentFeed = createDataSource(currentType);

        config.setProperty(PROP_DATASOURCE_TYPE, currentType.name());
        saveConfig();

        return currentFeed;
    }

    private MarketDataFeed createDataSource(DataSourceType type) {
        return switch (type) {
            case MARKET_COLLECTOR -> createMarketCollectorFeed();
            case FINMIND -> {
                String apiToken = config.getProperty(PROP_FINMIND_API_TOKEN, "");
                System.out.println("[DataSourceManager] Creating FinMind data source (API token: "
                        + (apiToken.isBlank() ? "not configured" : "***") + ")");
                yield new FinMindFeed(apiToken);
            }
            case YAHOO_FINANCE -> {
                System.out.println("[DataSourceManager] Creating Yahoo Finance data source");
                yield new YahooFinanceFeed();
            }
        };
    }

    private MarketDataFeed createMarketCollectorFeed() {
        System.out.println("[DataSourceManager] Creating MarketDataCollector local data source");
        try {
            return new MarketDataCollectorFeed(new MarketDataCollectorRepository(
                    getMarketCollectorJdbcUrl(),
                    getMarketCollectorUser(),
                    getMarketCollectorPassword()));
        } catch (Exception e) {
            System.err.println("[DataSourceManager] MarketDataCollector database unavailable: " + e.getMessage());
            return new MarketDataCollectorFeed(MarketDataCollectorRepository.unavailable(e.getMessage()));
        }
    }

    public DataSourceType getCurrentType() {
        return currentType;
    }

    public void setFinMindApiToken(String apiToken) {
        config.setProperty(PROP_FINMIND_API_TOKEN, apiToken != null ? apiToken.trim() : "");
        saveConfig();
        System.out.println("[DataSourceManager] FinMind API token updated");

        if (currentType == DataSourceType.FINMIND && currentFeed != null) {
            switchDataSource(DataSourceType.FINMIND);
        }
    }

    public String getFinMindApiToken() {
        return config.getProperty(PROP_FINMIND_API_TOKEN, "");
    }

    public String getMarketCollectorJdbcUrl() {
        return config.getProperty(PROP_MARKET_COLLECTOR_JDBC_URL, MarketDataCollectorRepository.DEFAULT_JDBC_URL);
    }

    public String getMarketCollectorUser() {
        return config.getProperty(PROP_MARKET_COLLECTOR_USER, MarketDataCollectorRepository.DEFAULT_USER);
    }

    public String getMarketCollectorPassword() {
        return config.getProperty(PROP_MARKET_COLLECTOR_PASSWORD, MarketDataCollectorRepository.DEFAULT_PASSWORD);
    }

    public void setMarketCollectorConfig(String jdbcUrl, String user, String password) {
        config.setProperty(PROP_MARKET_COLLECTOR_JDBC_URL,
                isBlank(jdbcUrl) ? MarketDataCollectorRepository.DEFAULT_JDBC_URL : jdbcUrl.trim());
        config.setProperty(PROP_MARKET_COLLECTOR_USER,
                user != null ? user.trim() : MarketDataCollectorRepository.DEFAULT_USER);
        config.setProperty(PROP_MARKET_COLLECTOR_PASSWORD,
                password != null ? password : MarketDataCollectorRepository.DEFAULT_PASSWORD);
        saveConfig();

        if (currentType == DataSourceType.MARKET_COLLECTOR && currentFeed != null) {
            switchDataSource(DataSourceType.MARKET_COLLECTOR);
        }
    }

    private void loadConfig() {
        config = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                ensureDefaultProperties();
                removeLegacyProperties();
                saveConfig();
                System.out.println("[DataSourceManager] Configuration loaded");
            } catch (IOException e) {
                System.err.println("[DataSourceManager] Failed to load configuration: " + e.getMessage());
                ensureDefaultProperties();
                removeLegacyProperties();
            }
        } else {
            ensureDefaultProperties();
            removeLegacyProperties();
            saveConfig();
        }
    }

    private void ensureDefaultProperties() {
        DataSourceType parsedType = parseDataSourceType(config.getProperty(PROP_DATASOURCE_TYPE));
        config.setProperty(PROP_DATASOURCE_TYPE, parsedType.name());
        config.putIfAbsent(PROP_FINMIND_API_TOKEN, "");
        config.putIfAbsent(PROP_MARKET_COLLECTOR_JDBC_URL, MarketDataCollectorRepository.DEFAULT_JDBC_URL);
        config.putIfAbsent(PROP_MARKET_COLLECTOR_USER, MarketDataCollectorRepository.DEFAULT_USER);
        config.putIfAbsent(PROP_MARKET_COLLECTOR_PASSWORD, MarketDataCollectorRepository.DEFAULT_PASSWORD);
    }

    private void removeLegacyProperties() {
        config.remove("alphavantage.apikey");
        config.remove("finnhub.apikey");
        config.remove("iexcloud.apikey");
        config.remove("polygon.apikey");
    }

    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "DreamHouse Trading - Data Source Configuration");
            System.out.println("[DataSourceManager] Configuration saved");
        } catch (IOException e) {
            System.err.println("[DataSourceManager] Failed to save configuration: " + e.getMessage());
        }
    }

    public boolean requiresApiKey(DataSourceType type) {
        return type == DataSourceType.FINMIND;
    }

    public boolean hasValidApiKey(DataSourceType type) {
        if (!requiresApiKey(type)) {
            return true;
        }
        String apiKey = getApiKey(type);
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String getApiKey(DataSourceType type) {
        return type == DataSourceType.FINMIND ? getFinMindApiToken() : "";
    }

    public void setApiKey(DataSourceType type, String apiKey) {
        if (type == DataSourceType.FINMIND) {
            setFinMindApiToken(apiKey);
        }
    }

    private DataSourceType parseDataSourceType(String value) {
        if (value == null || value.isBlank()) {
            return DataSourceType.MARKET_COLLECTOR;
        }
        try {
            return DataSourceType.valueOf(value);
        } catch (Exception e) {
            return DataSourceType.MARKET_COLLECTOR;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
