package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;

import javax.swing.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yahoo Finance 數據源實作
 * 提供實時報價和歷史數據
 *
 * 注意：這是使用非官方API，可能會有限制或變動
 */
public class YahooFinanceFeed implements MarketDataFeed {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final int UPDATE_INTERVAL_MS = 5000; // 5秒更新一次（避免過於頻繁請求）

    private final HttpClient httpClient;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    private boolean connected = false;
    private boolean paused = false;
    private ScheduledFuture<?> updateTask;

    public YahooFinanceFeed() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        System.out.println("[YahooFinanceFeed] 訂閱商品: " + symbol);
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            // ⭐ 如果列表為空，從 Map 中移除該商品，避免繼續呼叫 API
            if (list.isEmpty()) {
                listeners.remove(symbol);
                System.out.println("[YahooFinanceFeed] 取消訂閱商品（已移除）: " + symbol);
            } else {
                System.out.println("[YahooFinanceFeed] 取消訂閱商品（還有其他監聽器）: " + symbol);
            }
        }
    }

    @Override
    public void start() {
        ensureExecutor();
        connected = true;
        System.out.println("[YahooFinanceFeed] 正在啟動...");

        // 在背景執行緒中加載歷史數據
        new Thread(() -> {
            loadHistoricalData();

            // 開始定期更新實時數據
            if (connected && !executor.isShutdown()) {
                try {
                    updateTask = executor.scheduleAtFixedRate(
                        this::updateRealTimeData,
                        0,
                        UPDATE_INTERVAL_MS,
                        TimeUnit.MILLISECONDS
                    );
                } catch (RejectedExecutionException e) {
                    System.out.println("[YahooFinanceFeed] 即時排程已在停止後略過");
                }
            }
        }, "YahooFinance-Startup").start();
    }

    @Override
    public void stop() {
        connected = false;
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        executor.shutdown();
        System.out.println("[YahooFinanceFeed] 已停止");
    }

    private void ensureExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newScheduledThreadPool(2);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void pause() {
        paused = true;
        System.out.println("[YahooFinanceFeed] 數據更新已暫停");
    }

    @Override
    public void resume() {
        paused = false;
        System.out.println("[YahooFinanceFeed] 數據更新已恢復");
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * 加載歷史K線數據
     */
    private void loadHistoricalData() {
        for (String symbol : listeners.keySet()) {
            loadHistoricalDataForSymbol(symbol);
        }
    }

    /**
     * 為特定商品加載歷史數據（預設使用1分鐘週期）
     * @param symbol 商品代號
     */
    public void loadHistoricalDataForSymbol(String symbol) {
        loadHistoricalData(symbol, Timeframe.M1);
    }

    /**
     * 根據週期加載歷史數據（使用預設K線數量）
     */
    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe) {
        // 根據週期決定預設的K線數量
        int defaultBarCount;
        switch (timeframe) {
            case M1:
                defaultBarCount = 50;
                break;
            case M5:
                defaultBarCount = 100;
                break;
            case M15:
                defaultBarCount = 100;
                break;
            case M30:
                defaultBarCount = 100;
                break;
            case H1:
                defaultBarCount = 120;
                break;
            case D1:
                defaultBarCount = 200;
                break;
            case W1:
                defaultBarCount = 100;
                break;
            default:
                defaultBarCount = 50;
        }
        loadHistoricalData(symbol, timeframe, defaultBarCount);
    }

    /**
     * 根據週期和K線數量加載歷史數據
     */
    @Override
    public void loadHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        try {
            System.out.println("[YahooFinanceFeed] 正在加載 " + symbol + " 的 " + barCount + " 根 " + timeframe.getLabel() + " 歷史數據...");

            // 根據週期選擇Yahoo Finance的interval參數
            String interval;
            String range;

            switch (timeframe) {
                case M1:
                    interval = "1m";
                    // Yahoo Finance限制：1m只能取7天內數據
                    range = Math.min(barCount / (60 * 24), 7) + "d";
                    break;
                case M5:
                    interval = "5m";
                    // 5m可以取60天內數據
                    range = Math.min(barCount / (12 * 24), 60) + "d";
                    break;
                case M15:
                    interval = "15m";
                    range = Math.min(barCount / (4 * 24), 60) + "d";
                    break;
                case M30:
                    interval = "30m";
                    range = Math.min(barCount / (2 * 24), 60) + "d";
                    break;
                case H1:
                    interval = "1h";
                    // 1h可以取730天內數據
                    int hDays = Math.min(barCount / 24, 730);
                    range = hDays > 365 ? (hDays / 365) + "y" : hDays + "d";
                    break;
                case D1:
                    interval = "1d";
                    // 日線可以取更長時間
                    int dDays = barCount;
                    if (dDays > 1825) {
                        range = "10y";
                    } else if (dDays > 730) {
                        range = (dDays / 365) + "y";
                    } else {
                        range = dDays + "d";
                    }
                    break;
                case W1:
                    interval = "1wk";
                    // 週線
                    int weeks = barCount;
                    int wYears = weeks / 52;
                    range = Math.min(wYears + 1, 10) + "y";
                    break;
                default:
                    interval = "1m";
                    range = "1d";
            }

            String url = BASE_URL + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                       + "?interval=" + interval + "&range=" + range;

            System.out.println("[YahooFinanceFeed] 請求URL: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                parseAndNotifyHistoricalData(symbol, response.body(), timeframe, barCount);
            } else {
                System.err.println("[YahooFinanceFeed] HTTP錯誤: " + response.statusCode());
                // 如果失敗，生成模擬數據
                generateFallbackHistoricalData(symbol, timeframe, barCount);
            }

        } catch (Exception e) {
            System.err.println("[YahooFinanceFeed] 加載歷史數據失敗: " + e.getMessage());
            e.printStackTrace();
            // 發生異常時使用模擬數據
            generateFallbackHistoricalData(symbol, timeframe, barCount);
        }
    }

    /**
     * 更新實時數據
     */
    private void updateRealTimeData() {
        if (paused || !connected) {
            return;
        }

        for (String symbol : listeners.keySet()) {
            try {
                // 請求實時報價（1分鐘間隔，最近1天）
                String url = BASE_URL + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                           + "?interval=1m&range=1d";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    parseAndNotifyRealTimeData(symbol, response.body());
                } else {
                    System.err.println("[YahooFinanceFeed] 實時數據HTTP錯誤: " + response.statusCode());
                }

            } catch (Exception e) {
                // 靜默處理實時更新錯誤（避免控制台污染）
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 解析並通知歷史數據
     */
    private void parseAndNotifyHistoricalData(String symbol, String jsonResponse, Timeframe timeframe, int barCount) {
        try {
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 簡化的JSON解析（使用正則表達式）
            // 注意：生產環境建議使用Jackson或Gson

            // 提取timestamp數組
            List<Long> timestamps = extractLongArray(jsonResponse, "\"timestamp\":\\[([^\\]]+)\\]");

            // 提取quote數據
            List<Double> opens = extractDoubleArray(jsonResponse, "\"open\":\\[([^\\]]+)\\]");
            List<Double> highs = extractDoubleArray(jsonResponse, "\"high\":\\[([^\\]]+)\\]");
            List<Double> lows = extractDoubleArray(jsonResponse, "\"low\":\\[([^\\]]+)\\]");
            List<Double> closes = extractDoubleArray(jsonResponse, "\"close\":\\[([^\\]]+)\\]");
            List<Long> volumes = extractLongArray(jsonResponse, "\"volume\":\\[([^\\]]+)\\]");

            if (timestamps.isEmpty() || closes.isEmpty()) {
                System.err.println("[YahooFinanceFeed] 無法解析數據，使用備用方案");
                generateFallbackHistoricalData(symbol, timeframe, barCount);
                return;
            }

            // 更新最後價格
            if (!closes.isEmpty()) {
                lastPrices.put(symbol, closes.get(closes.size() - 1));
            }

            // 發送歷史K線數據（使用用戶指定的K線數量）
            int dataSize = Math.min(timestamps.size(), barCount);
            for (int i = Math.max(0, timestamps.size() - dataSize); i < timestamps.size(); i++) {
                if (i >= opens.size() || i >= highs.size() || i >= lows.size()
                    || i >= closes.size() || i >= volumes.size()) {
                    continue;
                }

                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.get(i)),
                    ZoneId.systemDefault()
                );

                double open = opens.get(i);
                double high = highs.get(i);
                double low = lows.get(i);
                double close = closes.get(i);
                long volume = volumes.get(i);

                // 為每根K線生成多個tick以模擬過程
                generateTicksFromBar(symbol, time, open, high, low, close, volume, symbolListeners);

                // 延遲以便UI更新
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            System.out.println("[YahooFinanceFeed] " + symbol + " 歷史數據加載完成 (" + dataSize + " 根K線)");

        } catch (Exception e) {
            System.err.println("[YahooFinanceFeed] 解析歷史數據失敗: " + e.getMessage());
            generateFallbackHistoricalData(symbol, timeframe, barCount);
        }
    }

    /**
     * 解析並通知實時數據
     */
    private void parseAndNotifyRealTimeData(String symbol, String jsonResponse) {
        try {
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 提取最新的收盤價
            List<Double> closes = extractDoubleArray(jsonResponse, "\"close\":\\[([^\\]]+)\\]");
            List<Long> volumes = extractLongArray(jsonResponse, "\"volume\":\\[([^\\]]+)\\]");

            if (closes.isEmpty()) return;

            double latestPrice = closes.get(closes.size() - 1);
            long latestVolume = volumes.isEmpty() ? 1000 : volumes.get(volumes.size() - 1);

            // 更新最後價格
            lastPrices.put(symbol, latestPrice);

            // 生成Tick
            Tick tick = new Tick(symbol, LocalDateTime.now(), latestPrice, latestVolume);

            // 生成Trade
            Trade.Side side = random.nextBoolean() ? Trade.Side.BID : Trade.Side.ASK;
            Trade trade = new Trade(LocalDateTime.now(), latestPrice,
                                   100 + random.nextInt(500), side);

            // 生成模擬五檔（Yahoo Finance API不提供orderbook）
            List<DepthLevel> depth = generateMockDepth(latestPrice);

            // 通知所有監聽器
            SwingUtilities.invokeLater(() -> {
                for (MarketDataListener listener : symbolListeners) {
                    listener.onTick(tick);
                    listener.onTrade(trade);
                    listener.onDepthUpdate(depth);
                }
            });

        } catch (Exception e) {
            // 靜默處理
        }
    }

    /**
     * 從K線生成多個tick
     */
    private void generateTicksFromBar(String symbol, LocalDateTime barTime,
                                      double open, double high, double low, double close,
                                      long volume, List<MarketDataListener> listeners) {
        // 為每根K線生成4個tick: 開、高、低、收
        double[] prices = {open, high, low, close};

        for (int j = 0; j < 4; j++) {
            LocalDateTime tickTime = barTime.plusSeconds(j * 15);
            long tickVolume = volume / 4;

            Tick tick = new Tick(symbol, tickTime, prices[j], tickVolume);

            SwingUtilities.invokeLater(() -> {
                for (MarketDataListener listener : listeners) {
                    listener.onTick(tick);
                }
            });
        }
    }

    /**
     * 根據商品代號獲取合理的基準價格
     */
    private double getReasonableBasePrice(String symbol) {
        // 台股（.TW 或 .TWO 結尾）
        if (symbol.endsWith(".TW") || symbol.endsWith(".TWO")) {
            String code = symbol.split("\\.")[0];
            // 高價股（台積電、大立光等）
            if (code.equals("2330") || code.equals("3008")) {
                return 800.0 + random.nextDouble() * 400.0;  // 800-1200
            }
            // 中高價股
            else if (code.startsWith("23") || code.startsWith("24")) {
                return 400.0 + random.nextDouble() * 400.0;  // 400-800
            }
            // 一般台股
            else {
                return 50.0 + random.nextDouble() * 100.0;  // 50-150
            }
        }
        // 港股（.HK 結尾）
        else if (symbol.endsWith(".HK")) {
            return 50.0 + random.nextDouble() * 200.0;  // 50-250
        }
        // A股（.SS 上海 或 .SZ 深圳）
        else if (symbol.endsWith(".SS") || symbol.endsWith(".SZ")) {
            return 20.0 + random.nextDouble() * 80.0;  // 20-100
        }
        // 美股及其他
        else {
            // 高價股（如 Amazon, Google, Tesla 等）
            if (symbol.equals("AMZN") || symbol.equals("GOOGL") ||
                symbol.equals("TSLA") || symbol.equals("NVDA")) {
                return 200.0 + random.nextDouble() * 600.0;  // 200-800
            }
            // 一般美股
            else {
                return 80.0 + random.nextDouble() * 120.0;  // 80-200
            }
        }
    }

    /**
     * 生成備用歷史數據（當API失敗時）
     */
    private void generateFallbackHistoricalData(String symbol, Timeframe timeframe, int barCount) {
        System.out.println("[YahooFinanceFeed] 使用模擬數據作為備用方案，週期: " + timeframe.getLabel() + "，數量: " + barCount);

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        double basePrice = getReasonableBasePrice(symbol);
        lastPrices.put(symbol, basePrice);
        System.out.println("[YahooFinanceFeed] " + symbol + " 備用數據基準價格: " + basePrice);

        int intervalMinutes = timeframe.getMinutes();
        LocalDateTime startTime = LocalDateTime.now().minusMinutes((long) barCount * intervalMinutes);
        double currentPrice = basePrice;

        for (int i = 0; i < barCount; i++) {
            LocalDateTime barTime = startTime.plusMinutes((long) i * intervalMinutes);

            // 價格變化幅度根據週期調整
            double maxChange = currentPrice * 0.01 * Math.sqrt(intervalMinutes / 60.0);
            double open = currentPrice;
            double change = (random.nextDouble() - 0.5) * 2.0 * maxChange;
            double close = open + change;

            // 高低價（最大波動根據週期調整）
            double maxWick = currentPrice * 0.005 * Math.sqrt(intervalMinutes / 60.0);
            double high = Math.max(open, close) + random.nextDouble() * maxWick;
            double low = Math.min(open, close) - random.nextDouble() * maxWick;

            long baseVolume = 1000000 + random.nextInt(5000000);
            long volume = (long) (baseVolume * (intervalMinutes / 1.0));

            generateTicksFromBar(symbol, barTime, open, high, low, close, volume, symbolListeners);

            currentPrice = close;

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println("[YahooFinanceFeed] " + symbol + " 備用數據生成完成，共 " + barCount + " 根K線");
    }

    /**
     * 生成模擬五檔數據
     */
    private List<DepthLevel> generateMockDepth(double midPrice) {
        List<DepthLevel> depth = new ArrayList<>();

        // 5檔ASK
        for (int i = 0; i < 5; i++) {
            double price = midPrice + (i + 1) * 0.2;
            long qty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.ASK, price, qty, i));
        }

        // 5檔BID
        for (int i = 0; i < 5; i++) {
            double price = midPrice - (i + 1) * 0.2;
            long qty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.BID, price, qty, i));
        }

        return depth;
    }

    /**
     * 提取JSON中的double數組（簡化版解析）
     */
    private List<Double> extractDoubleArray(String json, String regex) {
        List<Double> result = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            String[] parts = arrayContent.split(",");

            for (String part : parts) {
                part = part.trim();
                if (!part.equals("null")) {
                    try {
                        result.add(Double.parseDouble(part));
                    } catch (NumberFormatException e) {
                        // 跳過無效值
                    }
                }
            }
        }

        return result;
    }

    /**
     * 提取JSON中的long數組（簡化版解析）
     */
    private List<Long> extractLongArray(String json, String regex) {
        List<Long> result = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            String[] parts = arrayContent.split(",");

            for (String part : parts) {
                part = part.trim();
                if (!part.equals("null")) {
                    try {
                        result.add(Long.parseLong(part));
                    } catch (NumberFormatException e) {
                        // 跳過無效值
                    }
                }
            }
        }

        return result;
    }
}
