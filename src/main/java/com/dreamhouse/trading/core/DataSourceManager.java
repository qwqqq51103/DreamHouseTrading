package com.dreamhouse.trading.core;

import java.io.*;
import java.util.Properties;

/**
 * 數據源管理器
 * 負責創建和切換不同的市場數據源
 */
public class DataSourceManager {

    public enum DataSourceType {
        SIMULATOR("模擬數據", "Simulated Data"),
        YAHOO_FINANCE("Yahoo Finance", "Yahoo Finance"),
        ALPHA_VANTAGE("Alpha Vantage", "Alpha Vantage"),
        FINNHUB("Finnhub", "Finnhub");

        private final String displayNameZh;
        private final String displayNameEn;

        DataSourceType(String displayNameZh, String displayNameEn) {
            this.displayNameZh = displayNameZh;
            this.displayNameEn = displayNameEn;
        }

        public String getDisplayName(boolean useChinese) {
            return useChinese ? displayNameZh : displayNameEn;
        }

        public String getDisplayNameZh() { return displayNameZh; }
        public String getDisplayNameEn() { return displayNameEn; }
    }

    private static final String CONFIG_FILE = "datasource.properties";
    private static final String PROP_DATASOURCE_TYPE = "datasource.type";
    private static final String PROP_ALPHAVANTAGE_API_KEY = "alphavantage.apikey";
    private static final String PROP_FINNHUB_API_KEY = "finnhub.apikey";

    private DataSourceType currentType;
    private MarketDataFeed currentFeed;
    private Properties config;

    public DataSourceManager() {
        loadConfig();
        currentType = DataSourceType.valueOf(
            config.getProperty(PROP_DATASOURCE_TYPE, DataSourceType.SIMULATOR.name())
        );
    }

    /**
     * 獲取當前數據源
     */
    public MarketDataFeed getCurrentDataSource() {
        if (currentFeed == null) {
            currentFeed = createDataSource(currentType);
        }
        return currentFeed;
    }

    /**
     * 切換數據源
     */
    public synchronized MarketDataFeed switchDataSource(DataSourceType type) {
        System.out.println("[DataSourceManager] 切換數據源: " + currentType + " -> " + type);

        // 停止當前數據源
        if (currentFeed != null && currentFeed.isConnected()) {
            currentFeed.stop();
        }

        // 創建新數據源
        currentType = type;
        currentFeed = createDataSource(type);

        // 保存配置
        config.setProperty(PROP_DATASOURCE_TYPE, type.name());
        saveConfig();

        return currentFeed;
    }

    /**
     * 創建指定類型的數據源
     */
    private MarketDataFeed createDataSource(DataSourceType type) {
        switch (type) {
            case SIMULATOR:
                System.out.println("[DataSourceManager] 創建模擬數據源");
                return new SimulatorFeed();

            case YAHOO_FINANCE:
                System.out.println("[DataSourceManager] 創建Yahoo Finance數據源");
                return new YahooFinanceFeed();

            case ALPHA_VANTAGE:
                String avApiKey = config.getProperty(PROP_ALPHAVANTAGE_API_KEY, "demo");
                System.out.println("[DataSourceManager] 創建Alpha Vantage數據源 (API Key: "
                                 + (avApiKey.equals("demo") ? "demo" : "***") + ")");
                return new AlphaVantageFeed(avApiKey);

            case FINNHUB:
                String fhApiKey = config.getProperty(PROP_FINNHUB_API_KEY, "demo");
                System.out.println("[DataSourceManager] 創建Finnhub數據源 (API Key: "
                                 + (fhApiKey.equals("demo") ? "demo" : "***") + ")");
                return new FinnhubFeed(fhApiKey);

            default:
                System.err.println("[DataSourceManager] 未知數據源類型: " + type);
                return new SimulatorFeed();
        }
    }

    /**
     * 獲取當前數據源類型
     */
    public DataSourceType getCurrentType() {
        return currentType;
    }

    /**
     * 設置Alpha Vantage API密鑰
     */
    public void setAlphaVantageApiKey(String apiKey) {
        config.setProperty(PROP_ALPHAVANTAGE_API_KEY, apiKey);
        saveConfig();
        System.out.println("[DataSourceManager] Alpha Vantage API密鑰已更新");

        // 如果當前正在使用Alpha Vantage，需要重新創建
        if (currentType == DataSourceType.ALPHA_VANTAGE && currentFeed != null) {
            switchDataSource(DataSourceType.ALPHA_VANTAGE);
        }
    }

    /**
     * 獲取Alpha Vantage API密鑰
     */
    public String getAlphaVantageApiKey() {
        return config.getProperty(PROP_ALPHAVANTAGE_API_KEY, "demo");
    }

    /**
     * 設置Finnhub API密鑰
     */
    public void setFinnhubApiKey(String apiKey) {
        config.setProperty(PROP_FINNHUB_API_KEY, apiKey);
        saveConfig();
        System.out.println("[DataSourceManager] Finnhub API密鑰已更新");

        if (currentType == DataSourceType.FINNHUB && currentFeed != null) {
            switchDataSource(DataSourceType.FINNHUB);
        }
    }

    /**
     * 獲取Finnhub API密鑰
     */
    public String getFinnhubApiKey() {
        return config.getProperty(PROP_FINNHUB_API_KEY, "demo");
    }

    /**
     * 加載配置
     */
    private void loadConfig() {
        config = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                System.out.println("[DataSourceManager] 配置已加載");
            } catch (IOException e) {
                System.err.println("[DataSourceManager] 無法加載配置: " + e.getMessage());
            }
        } else {
            // 創建默認配置
            config.setProperty(PROP_DATASOURCE_TYPE, DataSourceType.SIMULATOR.name());
            config.setProperty(PROP_ALPHAVANTAGE_API_KEY, "demo");
            config.setProperty(PROP_FINNHUB_API_KEY, "demo");
            saveConfig();
        }
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "DreamHouse Trading - Data Source Configuration");
            System.out.println("[DataSourceManager] 配置已保存");
        } catch (IOException e) {
            System.err.println("[DataSourceManager] 無法保存配置: " + e.getMessage());
        }
    }

    /**
     * 檢查數據源是否需要API密鑰
     */
    public boolean requiresApiKey(DataSourceType type) {
        return type == DataSourceType.ALPHA_VANTAGE || type == DataSourceType.FINNHUB;
    }

    /**
     * 檢查API密鑰是否有效（不是空或demo）
     */
    public boolean hasValidApiKey(DataSourceType type) {
        if (!requiresApiKey(type)) {
            return true;
        }

        String apiKey;
        if (type == DataSourceType.ALPHA_VANTAGE) {
            apiKey = getAlphaVantageApiKey();
        } else if (type == DataSourceType.FINNHUB) {
            apiKey = getFinnhubApiKey();
        } else {
            return true;
        }

        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("demo");
    }

    /**
     * 根據類型獲取API密鑰
     */
    public String getApiKey(DataSourceType type) {
        if (type == DataSourceType.ALPHA_VANTAGE) {
            return getAlphaVantageApiKey();
        } else if (type == DataSourceType.FINNHUB) {
            return getFinnhubApiKey();
        }
        return "";
    }

    /**
     * 根據類型設置API密鑰
     */
    public void setApiKey(DataSourceType type, String apiKey) {
        if (type == DataSourceType.ALPHA_VANTAGE) {
            setAlphaVantageApiKey(apiKey);
        } else if (type == DataSourceType.FINNHUB) {
            setFinnhubApiKey(apiKey);
        }
    }
}
