package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import com.market.collector.model.Candlestick;
import com.market.collector.model.Quote;
import com.market.collector.query.MarketDataQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 市場數據載入器
 *
 * 用途：從 MarketDataCollector 資料庫載入歷史數據
 *
 * 核心功能：
 * 1. 補充開盤後的缺失數據（例如 10點開程式，需要 9點-10點的數據）
 * 2. 載入歷史K線數據用於回測
 * 3. 載入特定時間範圍的數據
 * 4. 多股票批量載入
 */
public class MarketDataLoader {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataLoader.class);

    private MarketDataQueryHelper queryHelper;
    private volatile boolean initialized = false;

    /**
     * 初始化數據載入器（使用預設MySQL配置）
     */
    public void initialize() {
        initialize("jdbc:mysql://localhost:3306/market_data?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=UTF-8",
                   "root", "");
    }

    /**
     * 初始化數據載入器（自訂資料庫配置）
     */
    public void initialize(String jdbcUrl, String username, String password) {
        if (initialized) {
            logger.warn("MarketDataLoader 已經初始化，跳過重複初始化");
            return;
        }

        try {
            queryHelper = new MarketDataQueryHelper(jdbcUrl, username, password);
            initialized = true;
            logger.info("MarketDataLoader 初始化成功");
        } catch (SQLException e) {
            logger.error("MarketDataLoader 初始化失敗", e);
            throw new RuntimeException("無法連接到 MarketDataCollector 資料庫", e);
        }
    }

    /**
     * 檢查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ==================== 場景 1: 補充開盤後的缺失數據 ⭐ ====================

    /**
     * 載入今日從開盤(9:00)到現在的所有tick數據
     *
     * 使用場景：10點開啟程式，需要補充 9點-10點 的缺失數據
     *
     * @param symbol 股票代號（例如 "2330.TW"）
     * @return 今日開盤至今的所有tick數據
     */
    public List<Tick> loadTodayMarketOpenToNow(String symbol) {
        checkInitialized();

        logger.info("載入 {} 今日開盤至今的數據", symbol);
        List<Quote> quotes = queryHelper.getTodayMarketOpenToNow(symbol);

        List<Tick> ticks = convertQuotesToTicks(quotes);
        logger.info("成功載入 {} 筆tick數據", ticks.size());

        return ticks;
    }

    // ==================== 場景 2: 載入今日所有數據 ====================

    /**
     * 載入今日所有tick數據
     */
    public List<Tick> loadTodayTicks(String symbol) {
        checkInitialized();

        logger.info("載入 {} 今日所有數據", symbol);
        List<Quote> quotes = queryHelper.getTodayTicks(symbol);

        return convertQuotesToTicks(quotes);
    }

    // ==================== 場景 3: 載入特定時間範圍數據 ====================

    /**
     * 載入特定時間範圍的tick數據
     *
     * @param symbol 股票代號
     * @param startTime 開始時間（格式: "2025-11-21 09:00:00"）
     * @param endTime 結束時間（格式: "2025-11-21 12:00:00"）
     */
    public List<Tick> loadTicksByTimeRange(String symbol, String startTime, String endTime) {
        checkInitialized();

        logger.info("載入 {} 在 {} ~ {} 的數據", symbol, startTime, endTime);
        List<Quote> quotes = queryHelper.getTicksByTimeRange(symbol, startTime, endTime);

        return convertQuotesToTicks(quotes);
    }

    // ==================== 場景 4: 載入最新N筆數據 ====================

    /**
     * 載入最新N筆tick數據（用於實時顯示）
     */
    public List<Tick> loadLatestTicks(String symbol, int count) {
        checkInitialized();

        logger.info("載入 {} 最新 {} 筆數據", symbol, count);
        List<Quote> quotes = queryHelper.getLatestTicks(symbol, count);

        return convertQuotesToTicks(quotes);
    }

    // ==================== 場景 5: 載入K線數據（圖表顯示） ====================

    /**
     * 載入今日K線數據
     *
     * @param symbol 股票代號
     * @param interval K線週期（"1m", "5m", "15m", "1h", "1d"）
     */
    public List<Bar> loadTodayBars(String symbol, String interval) {
        checkInitialized();

        logger.info("載入 {} 今日 {} K線數據", symbol, interval);
        List<Candlestick> candles = queryHelper.getTodayCandles(symbol, interval);

        return convertCandlesToBars(candles);
    }

    /**
     * 載入最新N根K線
     */
    public List<Bar> loadLatestBars(String symbol, String interval, int count) {
        checkInitialized();

        logger.info("載入 {} 最新 {} 根 {} K線", symbol, count, interval);
        List<Candlestick> candles = queryHelper.getLatestCandles(symbol, interval, count);

        return convertCandlesToBars(candles);
    }

    // ==================== 場景 6: 載入歷史數據（回測） ====================

    /**
     * 載入歷史K線數據（用於回測）
     *
     * @param symbol 股票代號
     * @param interval K線週期
     * @param startDate 開始日期
     * @param endDate 結束日期
     */
    public List<Bar> loadHistoricalBars(String symbol, String interval, LocalDate startDate, LocalDate endDate) {
        checkInitialized();

        logger.info("載入 {} 在 {} ~ {} 的 {} 歷史K線", symbol, startDate, endDate, interval);
        List<Candlestick> candles = queryHelper.getCandlesByDateRange(symbol, interval, startDate, endDate);

        return convertCandlesToBars(candles);
    }

    /**
     * 載入過去N天的K線數據
     */
    public List<Bar> loadHistoricalBars(String symbol, String interval, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        return loadHistoricalBars(symbol, interval, startDate, endDate);
    }

    // ==================== 場景 7: 多股票監控 ====================

    /**
     * 批量載入多支股票的今日數據
     */
    public java.util.Map<String, List<Tick>> loadMultipleSymbolsToday(List<String> symbols) {
        checkInitialized();

        logger.info("批量載入 {} 支股票的今日數據", symbols.size());

        java.util.Map<String, List<Tick>> result = new java.util.HashMap<>();

        for (String symbol : symbols) {
            try {
                List<Tick> ticks = loadTodayMarketOpenToNow(symbol);
                result.put(symbol, ticks);
            } catch (Exception e) {
                logger.error("載入 {} 數據失敗", symbol, e);
                result.put(symbol, new ArrayList<>());
            }
        }

        return result;
    }

    // ==================== 統計資訊 ====================

    /**
     * 取得指定股票的資料筆數
     */
    public int getTickCount(String symbol) {
        checkInitialized();
        return queryHelper.getTickCount(symbol);
    }

    /**
     * 取得資料庫中所有股票代號
     */
    public List<String> getAllSymbols() {
        checkInitialized();
        return queryHelper.getAllSymbols();
    }

    /**
     * 取得指定股票的數據時間範圍
     */
    public String getDataTimeRange(String symbol) {
        checkInitialized();
        return queryHelper.getDataTimeRange(symbol);
    }

    // ==================== 輔助方法 ====================

    /**
     * 將 Quote 轉換為 Tick
     * 注意：資料庫中的 volume 是累積成交量，需要轉換為分時成交量
     */
    private List<Tick> convertQuotesToTicks(List<Quote> quotes) {
        if (quotes.isEmpty()) {
            return new ArrayList<>();
        }

        List<Tick> ticks = new ArrayList<>();
        long previousVolume = 0; // 前一筆的累積成交量

        for (Quote quote : quotes) {
            long cumulativeVolume = quote.getVolume(); // 當前累積成交量

            // 計算該時刻的成交量 = 當前累積量 - 前一筆累積量
            long tickVolume = cumulativeVolume - previousVolume;

            // 防止負數（數據異常或跨日時）
            if (tickVolume < 0) {
                logger.debug("檢測到成交量重置（可能是跨日）：當前累積量 {} < 前一筆累積量 {}，重新開始計算",
                           cumulativeVolume, previousVolume);
                tickVolume = cumulativeVolume; // 跨日時重新開始
            }

            ticks.add(new Tick(
                    quote.getSymbol(),
                    quote.getTimestamp().toLocalDateTime(),
                    quote.getPrice(),
                    tickVolume  // 使用分時成交量
            ));

            previousVolume = cumulativeVolume; // 更新前一筆的累積量
        }

        logger.debug("轉換 {} 筆 Tick，累積成交量 → 分時成交量", ticks.size());
        return ticks;
    }

    /**
     * 將 Candlestick 轉換為 Bar
     * 注意：資料庫中的 volume 是累積成交量，需要轉換為分時成交量
     */
    private List<Bar> convertCandlesToBars(List<Candlestick> candles) {
        if (candles.isEmpty()) {
            return new ArrayList<>();
        }

        List<Bar> bars = new ArrayList<>();
        long previousVolume = 0; // 前一根 K 線的累積成交量

        for (Candlestick candle : candles) {
            long cumulativeVolume = candle.getVolume(); // 當前累積成交量

            // 計算分時成交量 = 當前累積量 - 前一根累積量
            long periodVolume = cumulativeVolume - previousVolume;

            // 防止負數（數據異常時）
            if (periodVolume < 0) {
                logger.warn("檢測到異常成交量：當前累積量 {} < 前一根累積量 {}，使用累積量",
                           cumulativeVolume, previousVolume);
                periodVolume = cumulativeVolume;
            }

            bars.add(new Bar(
                    candle.getTimestamp().toLocalDateTime(),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    periodVolume  // 使用分時成交量
            ));

            previousVolume = cumulativeVolume; // 更新前一根的累積量
        }

        logger.debug("轉換 {} 根 K 線，累積成交量 → 分時成交量", bars.size());
        return bars;
    }

    /**
     * 檢查是否已初始化
     */
    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("MarketDataLoader 尚未初始化，請先調用 initialize() 方法");
        }
    }

    /**
     * 關閉資源
     */
    public void close() {
        if (queryHelper != null) {
            queryHelper.close();
            initialized = false;
            logger.info("MarketDataLoader 已關閉");
        }
    }
}
