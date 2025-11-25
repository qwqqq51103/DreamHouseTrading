package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * FinMind 數據源實作
 * 專門提供台灣股市數據，支持即時報價和歷史數據
 *
 * API 文檔: https://finmind.github.io/
 * 免費會員: 600 次/小時
 */
public class FinMindFeed implements MarketDataFeed {

    private static final Logger logger = LoggerFactory.getLogger(FinMindFeed.class);
    private static final String API_BASE_URL = "https://api.finmindtrade.com/api/v4";
    private static final String LOGIN_ENDPOINT = API_BASE_URL + "/login";
    private static final String DATA_ENDPOINT = API_BASE_URL + "/data";
    private static final int UPDATE_INTERVAL_MS = 10000; // 10秒更新一次（避免超過限制）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final RealtimeBarBuilder realtimeBarBuilder = new RealtimeBarBuilder();
    private MarketDataLoader marketDataLoader; // 歷史數據載入器

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> updateTask;
    private boolean connected = false;
    private boolean paused = false;
    private java.time.LocalDate queryDate = java.time.LocalDate.now(); // 查詢日期

    public FinMindFeed(String apiToken) {
        this.apiToken = apiToken != null && !apiToken.trim().isEmpty() ? apiToken : "";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();

        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newScheduledThreadPool(2);

        // 初始化 MarketDataLoader（如果資料庫可用）
        initializeMarketDataLoader();

        logger.info("FinMindFeed 初始化完成 (API Token: {})",
                   apiToken.isEmpty() ? "未設定" : "***");

        // 測試 Token 有效性（非同步執行，不阻塞啟動）
        if (!apiToken.isEmpty()) {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 延遲2秒後測試
                    testToken();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "FinMind-TokenTest").start();
        }
    }

    /**
     * 初始化歷史數據載入器
     */
    private void initializeMarketDataLoader() {
        try {
            marketDataLoader = new MarketDataLoader();
            marketDataLoader.initialize();
            logger.info("MarketDataLoader 初始化成功 - 可從資料庫載入歷史數據");
        } catch (Exception e) {
            logger.warn("MarketDataLoader 初始化失敗 - 將僅使用 FinMind API: {}", e.getMessage());
            marketDataLoader = null;
        }
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        logger.info("訂閱商品: {}", symbol);

        // ⭐ 訂閱新商品時，嘗試從資料庫載入今日歷史數據
        if (connected && marketDataLoader != null && marketDataLoader.isInitialized()) {
            // 在背景執行緒中載入，避免阻塞 UI
            new Thread(() -> {
                try {
                    logger.info("🔄 訂閱 {} 時，嘗試從資料庫載入歷史數據...", symbol);
                    loadHistoricalDataFromDatabase(symbol);
                } catch (Exception e) {
                    logger.warn("從資料庫載入 {} 數據失敗: {}", symbol, e.getMessage());
                }
            }, "FinMind-Subscribe-" + symbol).start();
        }

        // 如果數據源已啟動且即時輪詢尚未運行，檢查是否需要啟動
        if (connected && updateTask == null) {
            if (isMarketOpen()) {
                logger.info("當前為交易時間，啟動即時數據輪詢（每 {} 秒）...", UPDATE_INTERVAL_MS / 1000);
                updateTask = executor.scheduleAtFixedRate(
                    this::updateRealTimeData,
                    UPDATE_INTERVAL_MS,  // 延遲啟動
                    UPDATE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                );
            } else {
                logger.info("當前為非交易時間,即時數據輪詢已禁用 - 僅顯示歷史數據");
            }
        }
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            logger.info("取消訂閱商品: {}", symbol);
        }
    }

    @Override
    public void start() {
        connected = true;
        logger.info("啟動 FinMind 數據源...");

        // 1. 先加載歷史數據
        new Thread(() -> {
            loadHistoricalData();

            // 2. 智能即時更新：只在交易時間內啟動
            if (isMarketOpen()) {
                logger.info("當前為交易時間，啟動即時數據輪詢（每 {} 秒）...", UPDATE_INTERVAL_MS / 1000);
                updateTask = executor.scheduleAtFixedRate(
                    this::updateRealTimeData,
                    UPDATE_INTERVAL_MS,  // 延遲啟動，避免與歷史數據衝突
                    UPDATE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                );
            } else {
                logger.info("當前為非交易時間，即時數據輪詢已禁用 - 僅顯示歷史數據");
            }
        }, "FinMind-Startup").start();
    }

    /**
     * 檢測當前是否為台灣股市交易時間
     * 台灣股市交易時間：週一到週五 09:00-13:30
     */
    private boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now();

        // 檢查是否為週末
        java.time.DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            logger.debug("今天是週末，市場未開盤");
            return false;
        }

        // 檢查時間是否在 09:00-13:30 之間
        int hour = now.getHour();
        int minute = now.getMinute();
        int timeInMinutes = hour * 60 + minute;

        int marketOpenTime = 9 * 60;      // 09:00
        int marketCloseTime = 13 * 60 + 30; // 13:30

        boolean isOpen = timeInMinutes >= marketOpenTime && timeInMinutes <= marketCloseTime;

        if (isOpen) {
            logger.info("當前時間 {}:{} 在交易時間內（09:00-13:30）",
                String.format("%02d", hour), String.format("%02d", minute));
        } else {
            logger.debug("當前時間 {}:{} 不在交易時間內（09:00-13:30）",
                String.format("%02d", hour), String.format("%02d", minute));
        }

        return isOpen;
    }

    @Override
    public void stop() {
        connected = false;
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
        }
        // 關閉 MarketDataLoader
        if (marketDataLoader != null) {
            marketDataLoader.close();
        }
        logger.info("FinMind 數據源已停止");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void pause() {
        paused = true;
        logger.info("FinMind 數據更新已暫停");
    }

    @Override
    public void resume() {
        paused = false;
        logger.info("FinMind 數據更新已恢復");
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * 設定查詢日期
     */
    public void setQueryDate(java.time.LocalDate date) {
        if (date == null) {
            date = java.time.LocalDate.now();
        }
        this.queryDate = date;
        logger.info("查詢日期已設置為: {}", date.format(DATE_FORMATTER));

        // 重新載入數據
        if (connected && !listeners.isEmpty()) {
            new Thread(this::loadHistoricalData, "FinMind-Reload").start();
        }
    }

    /**
     * 取得查詢日期
     */
    public java.time.LocalDate getQueryDate() {
        return queryDate;
    }

    /**
     * 加載歷史數據
     */
    private void loadHistoricalData() {
        for (String symbol : listeners.keySet()) {
            // ⭐ 優先從資料庫載入今日開盤到現在的數據
            if (marketDataLoader != null && marketDataLoader.isInitialized() && isMarketOpen()) {
                try {
                    loadHistoricalDataFromDatabase(symbol);
                } catch (Exception e) {
                    logger.warn("從資料庫載入 {} 數據失敗，改用 FinMind API: {}", symbol, e.getMessage());
                }
            }

            // 1. 加載歷史 K 線
            loadHistoricalDataForSymbol(symbol, Timeframe.M1, 50);

            // 2. 加載逐筆成交數據（Sponsor 會員）
            try {
                Thread.sleep(1000); // 避免 API 限流
                loadTickData(symbol);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 3. 加載市場消息
            try {
                Thread.sleep(1000); // 避免 API 限流
                loadNewsData(symbol);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 從資料庫載入歷史數據（補充開盤後的缺失數據）
     */
    private void loadHistoricalDataFromDatabase(String symbol) {
        logger.info("========================================");
        logger.info("嘗試從資料庫載入 {} 的歷史數據", symbol);
        logger.info("========================================");

        try {
            // 先用原始 symbol 查詢
            List<Tick> ticks = marketDataLoader.loadTodayMarketOpenToNow(symbol);

            // 如果沒有數據且 symbol 不包含 ".TW"，嘗試添加 ".TW" 再查詢一次
            if (ticks.isEmpty() && !symbol.contains(".TW") && !symbol.contains(".")) {
                String symbolWithTW = symbol + ".TW";
                logger.info("🔄 未找到 {} 的數據，嘗試查詢 {} ...", symbol, symbolWithTW);
                ticks = marketDataLoader.loadTodayMarketOpenToNow(symbolWithTW);

                if (!ticks.isEmpty()) {
                    logger.info("✓ 使用 {} 格式找到數據", symbolWithTW);
                    // 更新 symbol 為實際找到數據的格式
                    symbol = symbolWithTW;
                }
            }

            if (ticks.isEmpty()) {
                logger.warn("⚠ 資料庫中沒有 {} 的今日數據", symbol);
                logger.warn("請檢查：");
                logger.warn("  1. MarketDataCollector 是否正在運行");
                logger.warn("  2. 資料庫表 ticks 中是否有 {} 的記錄", symbol);
                logger.warn("  3. 使用 SQL 確認：SELECT COUNT(*) FROM ticks WHERE symbol='{}' AND DATE(ts) = CURDATE();", symbol);
                return;
            }

            logger.info("✓ 從資料庫成功載入了 {} 筆tick數據", ticks.size());

            if (ticks.size() > 0) {
                Tick firstTick = ticks.get(0);
                Tick lastTick = ticks.get(ticks.size() - 1);
                logger.info("  時間範圍: {} ~ {}", firstTick.getTimestamp(), lastTick.getTimestamp());
                logger.info("  價格範圍: {} ~ {}",
                    String.format("%.2f", firstTick.getPrice()),
                    String.format("%.2f", lastTick.getPrice()));
            }

            // 通知所有監聽器
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners != null) {
                int notifiedCount = 0;
                for (Tick tick : ticks) {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                        notifiedCount++;
                    }
                }
                logger.info("✓ 已通知 {} 個監聽器，共 {} 次", symbolListeners.size(), notifiedCount);
            } else {
                logger.warn("⚠ 沒有監聽器訂閱 {}", symbol);
            }

            // 更新最後價格
            if (!ticks.isEmpty()) {
                Tick lastTick = ticks.get(ticks.size() - 1);
                lastPrices.put(symbol, lastTick.getPrice());
                logger.info("✓ 更新最後價格: {}", lastTick.getPrice());
            }

            logger.info("========================================");
            logger.info("資料庫數據載入完成");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("✗ 從資料庫載入數據時發生錯誤", e);
            logger.error("錯誤訊息: {}", e.getMessage());
        }
    }

    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe) {
        int defaultBarCount = switch (timeframe) {
            case M1 -> 50;
            case M5 -> 100;
            case M15, M30 -> 100;
            case H1 -> 120;
            case D1 -> 200;
            case W1 -> 100;
        };
        loadHistoricalData(symbol, timeframe, defaultBarCount);
    }

    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        new Thread(() -> {
            // 1. 加載歷史 K 線
            loadHistoricalDataForSymbol(symbol, timeframe, barCount);

            // 2. 加載逐筆成交數據（Sponsor 會員）
            try {
                Thread.sleep(1000); // 避免 API 限流
                loadTickData(symbol);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 3. 加載市場消息
            try {
                Thread.sleep(1000); // 避免 API 限流
                loadNewsData(symbol);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "FinMind-HistoricalData").start();
    }

    /**
     * 為特定商品加載歷史數據
     * Sponsor 會員：使用 TaiwanStockKBar (分K線) 或 TaiwanStockPrice (日線)
     */
    private void loadHistoricalDataForSymbol(String symbol, Timeframe timeframe, int barCount) {
        logger.info("正在加載 {} 的 {} 根 {} 歷史數據...", symbol, barCount, timeframe.getLabel());

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        // ⭐ 盤中時段且查詢今日的分時K線：直接使用即時快照組合K線
        if (isMarketOpen() &&
            this.queryDate.equals(java.time.LocalDate.now()) &&
            (timeframe == Timeframe.M1 || timeframe == Timeframe.M5 ||
             timeframe == Timeframe.M15 || timeframe == Timeframe.M30 || timeframe == Timeframe.H1)) {

            logger.info("{} - 盤中時段查詢今日分時K線，直接使用即時快照 API", symbol);

            // 主動調用即時快照 API
            fetchAndNotifyRealTimeData(symbol);

            // 稍微等待讓快照數據被處理
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 從即時K線建構器獲取K線
            List<Bar> realtimeBars = realtimeBarBuilder.buildBars(symbol, timeframe, barCount);

            if (!realtimeBars.isEmpty()) {
                logger.info("{} - 成功組合 {} 根即時K線", symbol, realtimeBars.size());

                // 通知監聽器
                for (Bar bar : realtimeBars) {
                    generateTicksFromBar(symbol, bar.getTimestamp(),
                            bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(),
                            bar.getVolume(), symbolListeners);

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return; // ⭐ 成功組合K線後直接返回，不再調用 TaiwanStockKBar
            } else {
                logger.warn("{} - 即時快照尚未累積足夠數據，嘗試使用歷史數據", symbol);
                // 繼續執行下面的歷史數據加載邏輯
            }
        }

        try {
            // 轉換商品代號（移除 .TW 或 .TWO）
            String stockId = convertToFinMindSymbol(symbol);

            // 根據週期選擇 dataset 和參數
            String dataset;
            LocalDateTime startDate;
            LocalDateTime endDate = this.queryDate.atTime(23, 59, 59); // 使用查詢日期

            if (timeframe == Timeframe.D1 || timeframe == Timeframe.W1) {
                // 日線或週線：使用 TaiwanStockPrice（免費）
                dataset = "TaiwanStockPrice";
                startDate = endDate.minusDays(Math.max(barCount, 30));
            } else {
                // 分K線：使用 TaiwanStockKBar（Sponsor專屬）
                // ⚠️ TaiwanStockKBar 固定返回 1 分鐘數據，無 period 參數
                // 我們會在客戶端聚合成 5/15/30/60 分鐘 K 線
                dataset = "TaiwanStockKBar";

                // ⚠️ TaiwanStockKBar 限制：一次只能請求一天數據
                // 請求當天（最近交易日）的數據
                startDate = endDate;
            }

            // 構建 API URL
            String url = DATA_ENDPOINT +
                        "?dataset=" + dataset +
                        "&data_id=" + URLEncoder.encode(stockId, StandardCharsets.UTF_8) +
                        "&start_date=" + startDate.format(DATE_FORMATTER);

            // ⚠️ TaiwanStockKBar 不能有 end_date 參數
            if (!dataset.equals("TaiwanStockKBar")) {
                url += "&end_date=" + endDate.format(DATE_FORMATTER);
            }

            // ⭐ Token 不需要 URL 編碼（直接附加）
            if (!apiToken.isEmpty()) {
                url += "&token=" + apiToken;
            }

            logger.info("請求 FinMind API ({}): {}", dataset, url.replace(apiToken, "***"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                if (dataset.equals("TaiwanStockKBar")) {
                    parseAndNotifyKBarData(symbol, response.body(), timeframe, barCount);
                } else {
                    parseAndNotifyHistoricalData(symbol, response.body(), timeframe, barCount);
                }
            } else {
                logger.warn("FinMind API HTTP 錯誤 {}: {}", response.statusCode(), response.body());
                logger.warn("{} - 無法從 FinMind 獲取 {} 數據", symbol, timeframe.getLabel());

                // 診斷建議
                if (response.statusCode() == 400) {
                    try {
                        JsonNode errorResponse = objectMapper.readTree(response.body());
                        String errorMsg = errorResponse.path("msg").asText("未知錯誤");

                        if (errorMsg.contains("Token is illegal") || errorMsg.contains("token")) {
                            logger.warn("{} - Token 認證失敗！", symbol);
                            logger.warn("  ▶ 請檢查 Token 是否正確：{}", apiToken.isEmpty() ? "未設定" : apiToken.substring(0, Math.min(20, apiToken.length())) + "...");
                            logger.warn("  ▶ 請到 https://finmindtrade.com/ 重新取得 Token");
                            logger.warn("  ▶ 確認 Token 是否已過期或被撤銷");
                        } else if (dataset.equals("TaiwanStockKBar")) {
                            logger.warn("{} - TaiwanStockKBar API 錯誤", symbol);
                            logger.warn("  ▶ 此 API 需要 Sponsor 會員權限");
                            logger.warn("  ▶ 請確認您的 FinMind 帳號是否為 Sponsor 會員");
                            logger.warn("  ▶ 或改用 TaiwanStockPrice（免費但僅日線數據）");
                        }
                    } catch (Exception e) {
                        logger.debug("無法解析錯誤響應", e);
                    }
                }

                logger.warn("{} - 請確認：", symbol);
                logger.warn("  1. API Token 是否正確");
                logger.warn("  2. 股票代碼是否正確");
                logger.warn("  3. 時間週期是否支援（分K需 Sponsor 會員）");
                logger.warn("  4. 今日是否為交易日");

                if (isMarketOpen()) {
                    logger.warn("  5. 盤中時段：請等待即時快照數據累積");
                }

                generateFallbackHistoricalData(symbol, timeframe, barCount);
            }

        } catch (Exception e) {
            logger.error("加載歷史數據失敗: {}", e.getMessage(), e);
            generateFallbackHistoricalData(symbol, timeframe, barCount);
        }
    }

    /**
     * K線數據類（用於聚合）
     */
    private static class KBarData {
        LocalDateTime time;
        double open;
        double high;
        double low;
        double close;
        long volume;

        KBarData(LocalDateTime time, double open, double high, double low, double close, long volume) {
            this.time = time;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    /**
     * 解析並通知分K線數據（TaiwanStockKBar）
     */
    private void parseAndNotifyKBarData(String symbol, String jsonResponse,
                                        Timeframe timeframe, int barCount) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // 檢查狀態
            int status = root.path("status").asInt(-1);
            if (status != 200) {
                String msg = root.path("msg").asText("未知錯誤");
                logger.warn("{} - FinMind API 錯誤: {}", symbol, msg);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            // 解析數據
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.size() == 0) {
                logger.warn("{} - 無分K線數據", symbol);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // Step 1: 解析所有 1 分鐘 K 線到 List
            List<KBarData> oneMinuteBars = new ArrayList<>();

            logger.info("{} - 開始解析 {} 根 1 分鐘 K 線", symbol, dataArray.size());

            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode bar = dataArray.get(i);

                String dateStr = bar.path("date").asText();
                String timeStr = bar.path("minute").asText("").trim();

                // 解析時間
                LocalDateTime time;
                try {
                    if (timeStr.isEmpty()) {
                        time = LocalDateTime.parse(dateStr + " 09:00:00",
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else if (timeStr.length() == 5) {
                        time = LocalDateTime.parse(dateStr + " " + timeStr + ":00",
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else if (timeStr.length() == 8) {
                        time = LocalDateTime.parse(dateStr + " " + timeStr,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else {
                        try {
                            time = LocalDateTime.parse(dateStr + " " + timeStr,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        } catch (Exception e) {
                            time = LocalDateTime.parse(dateStr + "T09:00:00");
                        }
                    }
                } catch (Exception e) {
                    logger.warn("解析時間失敗 (date={}, minute={}): {}", dateStr, timeStr, e.getMessage());
                    continue;
                }

                double open = bar.path("open").asDouble();
                double high = bar.path("high").asDouble();
                double low = bar.path("low").asDouble();
                double close = bar.path("close").asDouble();
                long volume = bar.path("volume").asLong();

                oneMinuteBars.add(new KBarData(time, open, high, low, close, volume));
            }

            if (oneMinuteBars.isEmpty()) {
                logger.warn("{} - 無有效的 1 分鐘 K 線數據", symbol);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            // Step 2: 根據時間週期聚合 K 線
            List<KBarData> finalBars;
            if (timeframe == Timeframe.M1) {
                // 1 分鐘：直接使用
                finalBars = oneMinuteBars;
                logger.info("{} - 使用 {} 根 1 分鐘 K 線", symbol, finalBars.size());
            } else {
                // 5/15/30/60 分鐘：聚合
                finalBars = aggregateBars(oneMinuteBars, timeframe.getMinutes());
                logger.info("{} - 將 {} 根 1 分鐘 K 線聚合為 {} 根 {} K 線",
                           symbol, oneMinuteBars.size(), finalBars.size(), timeframe.getLabel());
            }

            // Step 3: 取最後 N 根 K 線
            int startIdx = Math.max(0, finalBars.size() - barCount);
            int actualCount = 0;

            for (int i = startIdx; i < finalBars.size(); i++) {
                KBarData bar = finalBars.get(i);
                lastPrices.put(symbol, bar.close);

                // 生成 tick
                generateTicksFromBar(symbol, bar.time, bar.open, bar.high, bar.low, bar.close,
                                   bar.volume, symbolListeners);

                actualCount++;
                Thread.sleep(20);
            }

            logger.info("{} 分K線數據加載完成，共 {} 根 {} K線",
                       symbol, actualCount, timeframe.getLabel());

        } catch (Exception e) {
            logger.error("解析分K線數據失敗: {}", e.getMessage(), e);
            generateFallbackHistoricalData(symbol, timeframe, barCount);
        }
    }

    /**
     * 聚合 K 線
     * 將 1 分鐘 K 線聚合為指定週期的 K 線
     *
     * @param oneMinuteBars 1 分鐘 K 線列表
     * @param periodMinutes 週期（分鐘數，例如 5、15、30、60）
     * @return 聚合後的 K 線列表
     */
    private List<KBarData> aggregateBars(List<KBarData> oneMinuteBars, int periodMinutes) {
        List<KBarData> aggregatedBars = new ArrayList<>();

        if (oneMinuteBars.isEmpty()) {
            return aggregatedBars;
        }

        // 按時間窗口分組聚合
        KBarData currentBar = null;
        LocalDateTime currentWindowStart = null;

        for (KBarData bar : oneMinuteBars) {
            // 計算這根 bar 屬於哪個時間窗口
            LocalDateTime windowStart = alignToTimeWindow(bar.time, periodMinutes);

            if (currentWindowStart == null || !windowStart.equals(currentWindowStart)) {
                // 開始新的時間窗口
                if (currentBar != null) {
                    aggregatedBars.add(currentBar);
                }

                // 創建新的聚合 K 線
                currentBar = new KBarData(windowStart, bar.open, bar.high, bar.low, bar.close, bar.volume);
                currentWindowStart = windowStart;
            } else {
                // 在同一時間窗口內，更新 OHLC
                currentBar.high = Math.max(currentBar.high, bar.high);
                currentBar.low = Math.min(currentBar.low, bar.low);
                currentBar.close = bar.close; // 使用最後的收盤價
                currentBar.volume += bar.volume; // 累加成交量
            }
        }

        // 加入最後一根 bar
        if (currentBar != null) {
            aggregatedBars.add(currentBar);
        }

        return aggregatedBars;
    }

    /**
     * 將時間對齊到時間窗口起點
     * 例如：09:07 在 5 分鐘週期下對齊到 09:05
     *      10:23 在 15 分鐘週期下對齊到 10:15
     */
    private LocalDateTime alignToTimeWindow(LocalDateTime time, int periodMinutes) {
        int minute = time.getMinute();
        int alignedMinute = (minute / periodMinutes) * periodMinutes;
        return time.withMinute(alignedMinute).withSecond(0).withNano(0);
    }

    /**
     * 將日K線聚合為週K線
     *
     * @param dailyBars 日K線列表
     * @return 週K線列表
     */
    private List<KBarData> aggregateDailyToWeekly(List<KBarData> dailyBars) {
        List<KBarData> weeklyBars = new ArrayList<>();

        if (dailyBars.isEmpty()) {
            return weeklyBars;
        }

        KBarData currentWeekBar = null;
        int currentWeekOfYear = -1;
        int currentYear = -1;

        for (KBarData bar : dailyBars) {
            // 獲取當前日期的週數和年份
            int weekOfYear = bar.time.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int year = bar.time.get(java.time.temporal.WeekFields.ISO.weekBasedYear());

            if (currentWeekOfYear != weekOfYear || currentYear != year) {
                // 開始新的一週
                if (currentWeekBar != null) {
                    weeklyBars.add(currentWeekBar);
                }

                // 創建新的週K線，使用本週第一天（週一）的時間
                LocalDateTime weekStart = bar.time.with(java.time.DayOfWeek.MONDAY);
                currentWeekBar = new KBarData(weekStart, bar.open, bar.high, bar.low, bar.close, bar.volume);
                currentWeekOfYear = weekOfYear;
                currentYear = year;
            } else {
                // 在同一週內，更新 OHLC
                currentWeekBar.high = Math.max(currentWeekBar.high, bar.high);
                currentWeekBar.low = Math.min(currentWeekBar.low, bar.low);
                currentWeekBar.close = bar.close; // 使用最後的收盤價
                currentWeekBar.volume += bar.volume; // 累加成交量
            }
        }

        // 加入最後一週
        if (currentWeekBar != null) {
            weeklyBars.add(currentWeekBar);
        }

        return weeklyBars;
    }

    /**
     * 解析並通知歷史數據（日線）
     */
    private void parseAndNotifyHistoricalData(String symbol, String jsonResponse,
                                              Timeframe timeframe, int barCount) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // 檢查狀態
            int status = root.path("status").asInt(-1);
            if (status != 200) {
                String msg = root.path("msg").asText("未知錯誤");
                logger.warn("{} - FinMind API 錯誤: {}", symbol, msg);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            // 解析數據
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.size() == 0) {
                logger.warn("{} - 無歷史數據", symbol);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // Step 1: 解析所有日K線到 List
            List<KBarData> dailyBars = new ArrayList<>();

            logger.info("{} - 開始解析 {} 根日K線", symbol, dataArray.size());

            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode bar = dataArray.get(i);

                String dateStr = bar.path("date").asText();
                LocalDateTime time = LocalDateTime.parse(dateStr + "T09:00:00");

                double open = bar.path("open").asDouble();
                double high = bar.path("max").asDouble();
                double low = bar.path("min").asDouble();
                double close = bar.path("close").asDouble();
                long volume = bar.path("Trading_Volume").asLong();

                dailyBars.add(new KBarData(time, open, high, low, close, volume));
            }

            if (dailyBars.isEmpty()) {
                logger.warn("{} - 無有效的日K線數據", symbol);
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            // Step 2: 根據時間週期處理
            List<KBarData> finalBars;
            if (timeframe == Timeframe.W1) {
                // 週線：聚合日K線
                finalBars = aggregateDailyToWeekly(dailyBars);
                logger.info("{} - 將 {} 根日K線聚合為 {} 根週K線",
                           symbol, dailyBars.size(), finalBars.size());
            } else {
                // 日線：直接使用
                finalBars = dailyBars;
                logger.info("{} - 使用 {} 根日K線", symbol, finalBars.size());
            }

            // Step 3: 取最後 N 根 K 線
            int startIdx = Math.max(0, finalBars.size() - barCount);
            int actualCount = 0;

            for (int i = startIdx; i < finalBars.size(); i++) {
                KBarData bar = finalBars.get(i);
                lastPrices.put(symbol, bar.close);

                // 生成 tick
                generateTicksFromBar(symbol, bar.time, bar.open, bar.high, bar.low, bar.close,
                                   bar.volume, symbolListeners);

                actualCount++;
                Thread.sleep(20);
            }

            logger.info("{} 歷史數據加載完成，共 {} 根 {} K線",
                       symbol, actualCount, timeframe.getLabel());

        } catch (Exception e) {
            logger.error("解析歷史數據失敗: {}", e.getMessage(), e);
            generateFallbackHistoricalData(symbol, timeframe, barCount);
        }
    }

    /**
     * 更新即時數據
     */
    private void updateRealTimeData() {
        if (paused || !connected) {
            return;
        }

        for (String symbol : listeners.keySet()) {
            try {
                fetchAndNotifyRealTimeData(symbol);

                // 避免超過限流
                Thread.sleep(2000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.error("獲取 {} 即時數據失敗: {}", symbol, e.getMessage());
            }
        }
    }

    /**
     * 獲取並通知即時數據（使用 taiwan_stock_tick_snapshot）
     * Sponsor 會員專屬，約 10 秒更新一次的盤中即時快照
     */
    private void fetchAndNotifyRealTimeData(String symbol) {
        try {
            String stockId = convertToFinMindSymbol(symbol);

            // ⭐ 使用 taiwan_stock_tick_snapshot 獲取即時快照（Sponsor 專屬）
            // 規則：盤中使用 Bearer token 在 headers，參數是 data_id
            String url = API_BASE_URL + "/taiwan_stock_tick_snapshot" +
                        "?data_id=" + URLEncoder.encode(stockId, StandardCharsets.UTF_8);

            logger.info("{} - 請求即時快照 API: {}", symbol, url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET();

            // ⭐ 使用 Bearer token（不是 URL 參數）
            if (!apiToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiToken);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("{} - API 響應: {}", symbol, response.body());
                parseAndNotifyRealTimeSnapshot(symbol, response.body());
            } else {
                logger.warn("{} - 獲取即時快照失敗: HTTP {}", symbol, response.statusCode());
            }

        } catch (Exception e) {
            logger.error("{} - 調用即時快照 API 異常: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * 解析並通知即時快照數據（taiwan_stock_tick_snapshot）
     */
    private void parseAndNotifyRealTimeSnapshot(String symbol, String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            int status = root.path("status").asInt(-1);
            if (status != 200) {
                logger.warn("{} - API 狀態碼: {}", symbol, status);
                return;
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.size() == 0) {
                logger.warn("{} - 無即時快照數據", symbol);
                return;
            }

            // 取最新的快照數據
            JsonNode snapshot = dataArray.get(dataArray.size() - 1);

            // 解析即時價格
            double close = snapshot.path("close").asDouble();
            double open = snapshot.path("open").asDouble();
            double high = snapshot.path("high").asDouble();
            double low = snapshot.path("low").asDouble();
            long volume = snapshot.path("volume").asLong();

            // 委買委賣資訊
            double buyPrice = snapshot.path("buy_price").asDouble();
            long buyVolume = snapshot.path("buy_volume").asLong();
            double sellPrice = snapshot.path("sell_price").asDouble();
            long sellVolume = snapshot.path("sell_volume").asLong();

            // TickType: 1=外盤(買盤), 2=內盤(賣盤)
            int tickType = snapshot.path("TickType").asInt(0);

            if (close <= 0) {
                logger.warn("{} - 價格無效: close={}", symbol, close);
                return;
            }

            logger.info("{} - 即時快照: 價格={}, 量={}, 委買={}/{}, 委賣={}/{}",
                       symbol, close, volume, buyPrice, buyVolume, sellPrice, sellVolume);

            // 更新最後價格
            lastPrices.put(symbol, close);

            // ⭐ 添加到即時K線建構器（使用快照的真實OHLC）
            realtimeBarBuilder.addSnapshot(symbol, LocalDateTime.now(), open, high, low, close, volume);

            // 創建 Tick
            Tick tick = new Tick(symbol, LocalDateTime.now(), close, volume);

            // 創建 Trade（使用真實的 TickType）
            Trade trade = null;
            if (tickType == 1) {
                // 外盤（買盤）
                trade = new Trade(LocalDateTime.now(), close, volume, Trade.Side.BID);
            } else if (tickType == 2) {
                // 內盤（賣盤）
                trade = new Trade(LocalDateTime.now(), close, volume, Trade.Side.ASK);
            }

            // 生成五檔掛單（使用真實的委買委賣資訊）
            List<DepthLevel> depth = generateRealDepth(buyPrice, buyVolume, sellPrice, sellVolume);

            // 通知所有監聽器
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners != null) {
                Trade finalTrade = trade;
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                        if (finalTrade != null) {
                            listener.onTrade(finalTrade);
                        }
                        if (depth != null && !depth.isEmpty()) {
                            listener.onDepthUpdate(depth);
                        }
                    }
                });
            }

        } catch (Exception e) {
            logger.warn("{} - 解析即時快照失敗: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * 從即時快照生成真實的五檔掛單
     * 注意：taiwan_stock_tick_snapshot 只提供最佳買賣價，不是完整五檔
     */
    private List<DepthLevel> generateRealDepth(double buyPrice, long buyVolume,
                                               double sellPrice, long sellVolume) {
        List<DepthLevel> depth = new ArrayList<>();

        if (sellPrice > 0 && sellVolume > 0) {
            // 賣一（最佳賣價）
            depth.add(new DepthLevel(DepthLevel.Side.ASK, sellPrice, sellVolume, 0));
        }

        if (buyPrice > 0 && buyVolume > 0) {
            // 買一（最佳買價）
            depth.add(new DepthLevel(DepthLevel.Side.BID, buyPrice, buyVolume, 0));
        }

        return depth;
    }

    /**
     * 將商品代號轉換為 FinMind 格式
     * 例如：2330.TW -> 2330
     */
    private String convertToFinMindSymbol(String symbol) {
        return symbol.replace(".TW", "").replace(".TWO", "");
    }

    /**
     * 測試 FinMind Token 是否有效
     * 使用一個簡單的 API 調用來驗證 Token
     */
    public boolean testToken() {
        if (apiToken.isEmpty()) {
            logger.warn("未設定 API Token");
            return false;
        }

        try {
            // 使用 TaiwanStockPrice 測試（免費API，僅測試 Token 有效性）
            String testUrl = DATA_ENDPOINT +
                    "?dataset=TaiwanStockPrice" +
                    "&data_id=2330" +
                    "&start_date=2025-11-01" +
                    "&end_date=2025-11-01" +
                    "&token=" + apiToken;

            logger.info("測試 Token 有效性...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                int status = root.path("status").asInt(-1);

                if (status == 200) {
                    logger.info("✓ Token 驗證成功！");
                    return true;
                } else {
                    String msg = root.path("msg").asText("未知錯誤");
                    logger.warn("✗ Token 驗證失敗: {}", msg);
                    return false;
                }
            } else {
                logger.warn("✗ HTTP 錯誤 {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("測試 Token 時發生錯誤: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 從K線生成多個tick（已禁用 - 避免生成模擬數據）
     * 註解原因：此方法會從一根 K 線生成 4 個模擬 tick，這不是真實的逐筆數據
     */
    private void generateTicksFromBar(String symbol, LocalDateTime barTime,
                                      double open, double high, double low, double close,
                                      long volume, List<MarketDataListener> listeners) {
        // ⭐ 生成 4 個 tick 來模擬 K 線的 OHLC，以正確顯示上下影線
        double[] prices = {open, high, low, close};

        for (int j = 0; j < 4; j++) {
            LocalDateTime tickTime = barTime.plusSeconds(j * 15);
            long tickVolume = volume / 4;

            final Tick tick = new Tick(symbol, tickTime, prices[j], tickVolume);

            SwingUtilities.invokeLater(() -> {
                for (MarketDataListener listener : listeners) {
                    listener.onTick(tick);
                }
            });
        }
    }

    /**
     * 生成備用歷史數據（已禁用 - 不再生成模擬數據）
     * 注意：即時快照邏輯已移至 loadHistoricalDataForSymbol 開頭優先處理
     */
    private void generateFallbackHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        // 顯示警告訊息
        logger.warn("{} - 無法從 FinMind 獲取 {} 數據", symbol, timeframe.getLabel());
        logger.warn("{} - 請確認：", symbol);
        logger.warn("  1. API Token 是否正確");
        logger.warn("  2. 股票代碼是否正確");
        logger.warn("  3. 時間週期是否支援（分K需 Sponsor 會員）");
        logger.warn("  4. 今日是否為交易日");

        if (isMarketOpen() && this.queryDate.equals(java.time.LocalDate.now())) {
            logger.warn("  5. 盤中時段：請等待即時快照累積足夠數據");
        }
    }

    /**
     * 加載逐筆成交數據（TaiwanStockPriceTick）
     * 僅限 Backer/Sponsor 會員使用
     */
    private void loadTickData(String symbol) {
        try {
            String stockId = convertToFinMindSymbol(symbol);
            String today = LocalDateTime.now().format(DATE_FORMATTER);

            // ⭐ TaiwanStockPriceTick 只能請求一天數據
            String url = DATA_ENDPOINT +
                        "?dataset=TaiwanStockPriceTick" +
                        "&data_id=" + URLEncoder.encode(stockId, StandardCharsets.UTF_8) +
                        "&start_date=" + today;

            logger.info("{} - 請求逐筆成交數據: {}", symbol, url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET();

            // ⭐ 使用 Bearer token（不是 URL 參數）
            if (!apiToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiToken);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                parseTickData(symbol, response.body());
            } else {
                logger.warn("{} - 獲取逐筆成交數據失敗: HTTP {}", symbol, response.statusCode());
            }

        } catch (Exception e) {
            logger.error("{} - 加載逐筆成交數據異常: {}", symbol, e.getMessage());
        }
    }

    /**
     * 解析逐筆成交數據
     */
    private void parseTickData(String symbol, String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            int status = root.path("status").asInt(-1);
            if (status != 200) {
                String msg = root.path("msg").asText("未知錯誤");
                logger.warn("{} - FinMind API 錯誤: {}", symbol, msg);
                return;
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.size() == 0) {
                logger.info("{} - 今日無逐筆成交數據", symbol);
                return;
            }

            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 只取最近的 100 筆（避免過多數據）
            int startIdx = Math.max(0, dataArray.size() - 100);
            int count = 0;

            // DEBUG: 顯示前3筆數據的原始格式
            if (dataArray.size() > 0) {
                logger.info("=== DEBUG: TaiwanStockPriceTick 前3筆原始數據 ===");
                for (int d = 0; d < Math.min(3, dataArray.size()); d++) {
                    logger.info("數據 #{}: {}", d, dataArray.get(d).toString());
                }
            }

            for (int i = startIdx; i < dataArray.size(); i++) {
                JsonNode tick = dataArray.get(i);

                String dateStr = tick.path("date").asText();
                String timeStr = tick.path("Time").asText("").trim();

                // DEBUG: 顯示解析的時間
                if (i < startIdx + 3) {
                    logger.info("解析 Tick #{}: date='{}', Time='{}', length={}", i, dateStr, timeStr, timeStr.length());
                }

                // 解析時間
                LocalDateTime timestamp;
                try {
                    if (timeStr.isEmpty()) {
                        logger.warn("時間字段為空，跳過此筆數據");
                        continue;
                    } else if (timeStr.length() == 8) {
                        // HH:mm:ss 格式
                        timestamp = LocalDateTime.parse(dateStr + " " + timeStr,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else if (timeStr.length() == 5) {
                        // HH:mm 格式（沒有秒）
                        timestamp = LocalDateTime.parse(dateStr + " " + timeStr + ":00",
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else if (timeStr.length() > 8 && timeStr.contains(".")) {
                        // HH:mm:ss.SSSSSS 格式（帶微秒）- 截取前 8 個字符
                        String timeWithoutMicros = timeStr.substring(0, 8);
                        timestamp = LocalDateTime.parse(dateStr + " " + timeWithoutMicros,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } else {
                        logger.warn("時間格式不正確: '{}', 跳過此筆數據", timeStr);
                        continue;
                    }
                } catch (Exception e) {
                    logger.warn("解析時間失敗 (date={}, Time={}): {}", dateStr, timeStr, e.getMessage());
                    continue;
                }

                double price = tick.path("deal_price").asDouble();
                long volume = tick.path("volume").asLong();
                int tickType = tick.path("TickType").asInt(0);

                // TickType: 0=無法判斷、1=賣盤成交、2=買盤成交
                // 註解：當 TickType = 0 時跳過該筆數據，不使用隨機值
                Trade.Side side;
                if (tickType == 2) {
                    side = Trade.Side.BID;
                } else if (tickType == 1) {
                    side = Trade.Side.ASK;
                } else {
                    // TickType = 0（無法判斷），跳過此筆數據
                    continue;
                }

                Trade trade = new Trade(timestamp, price, volume, side);

                // 通知監聽器
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTrade(trade);
                    }
                });

                count++;
                Thread.sleep(10); // 避免過快
            }

            logger.info("{} - 逐筆成交數據加載完成，共 {} 筆", symbol, count);

        } catch (Exception e) {
            logger.error("{} - 解析逐筆成交數據失敗: {}", symbol, e.getMessage());
        }
    }

    /**
     * 加載市場消息（TaiwanStockNews）
     */
    private void loadNewsData(String symbol) {
        try {
            String stockId = convertToFinMindSymbol(symbol);

            // ⭐ 獲取最近 7 天的新聞
            // 注意：TaiwanStockNews 不接受 end_date 參數（API 限制）
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);

            String url = DATA_ENDPOINT +
                        "?dataset=TaiwanStockNews" +
                        "&data_id=" + URLEncoder.encode(stockId, StandardCharsets.UTF_8) +
                        "&start_date=" + startDate.format(DATE_FORMATTER);
            // ⭐ 不能加 end_date

            logger.info("{} - 請求市場消息: {}", symbol, url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET();

            // ⭐ 使用 Bearer token（不是 URL 參數）
            if (!apiToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiToken);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                parseNewsData(symbol, response.body());
            } else {
                logger.warn("{} - 獲取市場消息失敗: HTTP {} - 回應: {}", symbol, response.statusCode(), response.body());
                logger.warn("{} - 可能原因: TaiwanStockNews API 可能不支援此股票或參數格式不正確", symbol);
            }

        } catch (Exception e) {
            logger.error("{} - 加載市場消息異常: {}", symbol, e.getMessage());
        }
    }

    /**
     * 解析市場消息數據
     */
    private void parseNewsData(String symbol, String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            int status = root.path("status").asInt(-1);
            if (status != 200) {
                String msg = root.path("msg").asText("未知錯誤");
                logger.warn("{} - FinMind API 錯誤: {}", symbol, msg);
                return;
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.size() == 0) {
                logger.info("{} - 無市場消息", symbol);
                return;
            }

            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 最多顯示 20 則新聞
            int maxNews = Math.min(20, dataArray.size());
            int count = 0;

            for (int i = 0; i < maxNews; i++) {
                JsonNode news = dataArray.get(i);

                String dateStr = news.path("date").asText();
                String source = news.path("source").asText("FinMind");
                String title = news.path("title").asText();
                String link = news.path("link").asText("");

                // 解析日期
                LocalDateTime timestamp;
                try {
                    timestamp = LocalDateTime.parse(dateStr + "T00:00:00");
                } catch (Exception e) {
                    timestamp = LocalDateTime.now();
                }

                NewsItem newsItem = new NewsItem(timestamp, source, title, link);

                // 通知監聽器
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onNews(newsItem);
                    }
                });

                count++;
            }

            logger.info("{} - 市場消息加載完成，共 {} 則", symbol, count);

        } catch (Exception e) {
            logger.error("{} - 解析市場消息失敗: {}", symbol, e.getMessage());
        }
    }
}
