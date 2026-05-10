package com.dreamhouse.trading.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 數據源管理器
 * 負責創建和切換不同的市場數據源
 */
public class DataSourceManager {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);

    public enum DataSourceType {
        SIMULATOR("模擬數據", "Simulated Data"),
        FINMIND("FinMind", "FinMind"),
        YAHOO_FINANCE("Yahoo Finance", "Yahoo Finance"),
        ALPHA_VANTAGE("Alpha Vantage", "Alpha Vantage"),
        FINNHUB("Finnhub", "Finnhub"),
        IEX_CLOUD("IEX Cloud", "IEX Cloud"),
        POLYGON("Polygon.io", "Polygon.io");

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
    private static final String PROP_FINMIND_API_TOKEN = "finmind.apitoken";
    private static final String PROP_ALPHAVANTAGE_API_KEY = "alphavantage.apikey";
    private static final String PROP_FINNHUB_API_KEY = "finnhub.apikey";
    private static final String PROP_IEXCLOUD_API_KEY = "iexcloud.apikey";
    private static final String PROP_POLYGON_API_KEY = "polygon.apikey";

    private DataSourceType currentType;
    private MarketDataFeed currentFeed;
    private Properties config;
    private final File configFile;

    public DataSourceManager() {
        this(Path.of(CONFIG_FILE));
    }

    public DataSourceManager(Path configPath) {
        this.configFile = configPath != null ? configPath.toFile() : Path.of(CONFIG_FILE).toFile();
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

            case FINMIND:
                String fmApiToken = config.getProperty(PROP_FINMIND_API_TOKEN, "");
                System.out.println("[DataSourceManager] 創建FinMind數據源 (API Token: "
                                 + (fmApiToken.isEmpty() ? "未設定" : "***") + ")");
                return new FinMindFeed(fmApiToken);

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

            case IEX_CLOUD:
                String iexApiKey = config.getProperty(PROP_IEXCLOUD_API_KEY, "demo");
                System.out.println("[DataSourceManager] 創建IEX Cloud數據源 (API Key: "
                                 + (iexApiKey.equals("demo") ? "demo" : "***") + ")");
                return new IEXCloudFeed(iexApiKey);

            case POLYGON:
                String polyApiKey = config.getProperty(PROP_POLYGON_API_KEY, "demo");
                System.out.println("[DataSourceManager] 創建Polygon.io數據源 (API Key: "
                                 + (polyApiKey.equals("demo") ? "demo" : "***") + ")");
                return new PolygonFeed(polyApiKey);

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
     * 設置FinMind API Token
     */
    public void setFinMindApiToken(String apiToken) {
        config.setProperty(PROP_FINMIND_API_TOKEN, apiToken);
        saveConfig();
        System.out.println("[DataSourceManager] FinMind API Token已更新");

        if (currentType == DataSourceType.FINMIND && currentFeed != null) {
            switchDataSource(DataSourceType.FINMIND);
        }
    }

    /**
     * 獲取FinMind API Token
     */
    public String getFinMindApiToken() {
        return config.getProperty(PROP_FINMIND_API_TOKEN, "");
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
     * 設置IEX Cloud API密鑰
     */
    public void setIEXCloudApiKey(String apiKey) {
        config.setProperty(PROP_IEXCLOUD_API_KEY, apiKey);
        saveConfig();
        System.out.println("[DataSourceManager] IEX Cloud API密鑰已更新");

        if (currentType == DataSourceType.IEX_CLOUD && currentFeed != null) {
            switchDataSource(DataSourceType.IEX_CLOUD);
        }
    }

    /**
     * 獲取IEX Cloud API密鑰
     */
    public String getIEXCloudApiKey() {
        return config.getProperty(PROP_IEXCLOUD_API_KEY, "demo");
    }

    /**
     * 設置Polygon API密鑰
     */
    public void setPolygonApiKey(String apiKey) {
        config.setProperty(PROP_POLYGON_API_KEY, apiKey);
        saveConfig();
        System.out.println("[DataSourceManager] Polygon API密鑰已更新");

        if (currentType == DataSourceType.POLYGON && currentFeed != null) {
            switchDataSource(DataSourceType.POLYGON);
        }
    }

    /**
     * 獲取Polygon API密鑰
     */
    public String getPolygonApiKey() {
        return config.getProperty(PROP_POLYGON_API_KEY, "demo");
    }

    /**
     * 加載配置
     */
    private void loadConfig() {
        config = new Properties();
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
            config.setProperty(PROP_FINMIND_API_TOKEN, "");
            config.setProperty(PROP_ALPHAVANTAGE_API_KEY, "demo");
            config.setProperty(PROP_FINNHUB_API_KEY, "demo");
            config.setProperty(PROP_IEXCLOUD_API_KEY, "demo");
            config.setProperty(PROP_POLYGON_API_KEY, "demo");
            saveConfig();
        }
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
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
        return type == DataSourceType.FINMIND ||
               type == DataSourceType.ALPHA_VANTAGE ||
               type == DataSourceType.FINNHUB ||
               type == DataSourceType.IEX_CLOUD ||
               type == DataSourceType.POLYGON;
    }

    /**
     * 檢查API密鑰是否有效（不是空或demo）
     */
    public boolean hasValidApiKey(DataSourceType type) {
        if (!requiresApiKey(type)) {
            return true;
        }

        String apiKey = getApiKey(type);
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("demo");
    }

    /**
     * 根據類型獲取API密鑰
     */
    public String getApiKey(DataSourceType type) {
        return switch (type) {
            case FINMIND -> getFinMindApiToken();
            case ALPHA_VANTAGE -> getAlphaVantageApiKey();
            case FINNHUB -> getFinnhubApiKey();
            case IEX_CLOUD -> getIEXCloudApiKey();
            case POLYGON -> getPolygonApiKey();
            default -> "";
        };
    }

    /**
     * 根據類型設置API密鑰
     */
    public void setApiKey(DataSourceType type, String apiKey) {
        switch (type) {
            case FINMIND -> setFinMindApiToken(apiKey);
            case ALPHA_VANTAGE -> setAlphaVantageApiKey(apiKey);
            case FINNHUB -> setFinnhubApiKey(apiKey);
            case IEX_CLOUD -> setIEXCloudApiKey(apiKey);
            case POLYGON -> setPolygonApiKey(apiKey);
        }
    }
}
