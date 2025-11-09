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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alpha Vantage API 數據源實作
 * 提供股票實時報價和歷史數據
 *
 * API文檔: https://www.alphavantage.co/documentation/
 * 免費API限制: 5 calls/min, 500 calls/day
 */
public class AlphaVantageFeed implements MarketDataFeed {

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final int UPDATE_INTERVAL_MS = 60000; // 1分鐘更新一次（避免超過API限制）

    private final String apiKey;
    private final HttpClient httpClient;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    private boolean connected = false;
    private boolean paused = false;
    private ScheduledFuture<?> updateTask;

    /**
     * 構造函數
     * @param apiKey Alpha Vantage API密鑰，可從 https://www.alphavantage.co/support/#api-key 免費獲取
     */
    public AlphaVantageFeed(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("demo")) {
            System.out.println("[AlphaVantageFeed] 警告：使用demo API key，功能受限");
            this.apiKey = "demo"; // Demo key只支持特定商品
        } else {
            this.apiKey = apiKey;
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        System.out.println("[AlphaVantageFeed] 訂閱商品: " + symbol);
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            System.out.println("[AlphaVantageFeed] 取消訂閱商品: " + symbol);
        }
    }

    @Override
    public void start() {
        connected = true;
        System.out.println("[AlphaVantageFeed] 正在啟動...");

        // 在背景執行緒中加載歷史數據
        new Thread(() -> {
            loadHistoricalData();

            // 開始定期更新實時數據（注意API限制）
            updateTask = executor.scheduleAtFixedRate(
                this::updateRealTimeData,
                UPDATE_INTERVAL_MS, // 延遲1分鐘開始
                UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        }, "AlphaVantage-Startup").start();
    }

    @Override
    public void stop() {
        connected = false;
        if (updateTask != null) {
            updateTask.cancel(false);
        }
        executor.shutdown();
        System.out.println("[AlphaVantageFeed] 已停止");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void pause() {
        paused = true;
        System.out.println("[AlphaVantageFeed] 數據更新已暫停");
    }

    @Override
    public void resume() {
        paused = false;
        System.out.println("[AlphaVantageFeed] 數據更新已恢復");
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
            try {
                System.out.println("[AlphaVantageFeed] 正在加載 " + symbol + " 的歷史數據...");

                // 使用TIME_SERIES_DAILY獲取每日數據
                String url = BASE_URL + "?function=TIME_SERIES_DAILY"
                           + "&symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                           + "&outputsize=compact" // compact=100天, full=20年
                           + "&apikey=" + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();

                    // 檢查是否超過API限制
                    if (body.contains("API call frequency")) {
                        System.err.println("[AlphaVantageFeed] API調用頻率超限，使用備用數據");
                        generateFallbackHistoricalData(symbol);
                    } else if (body.contains("Invalid API call") || body.contains("Error Message")) {
                        System.err.println("[AlphaVantageFeed] API調用無效，使用備用數據");
                        generateFallbackHistoricalData(symbol);
                    } else {
                        parseAndNotifyHistoricalData(symbol, body);
                    }
                } else {
                    System.err.println("[AlphaVantageFeed] HTTP錯誤: " + response.statusCode());
                    generateFallbackHistoricalData(symbol);
                }

                // API限制：避免連續請求
                if (listeners.size() > 1) {
                    Thread.sleep(12000); // 12秒間隔（5 calls/min）
                }

            } catch (Exception e) {
                System.err.println("[AlphaVantageFeed] 加載歷史數據失敗: " + e.getMessage());
                generateFallbackHistoricalData(symbol);
            }
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
                // 使用GLOBAL_QUOTE獲取實時報價
                String url = BASE_URL + "?function=GLOBAL_QUOTE"
                           + "&symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                           + "&apikey=" + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(java.time.Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (!body.contains("API call frequency") && !body.contains("Error Message")) {
                        parseAndNotifyRealTimeData(symbol, body);
                    }
                }

                // API限制：避免連續請求
                if (listeners.size() > 1) {
                    Thread.sleep(12000);
                }

            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 解析並通知歷史數據
     */
    private void parseAndNotifyHistoricalData(String symbol, String jsonResponse) {
        try {
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 使用正則表達式解析JSON (簡化版)
            // 格式: "2024-01-15": { "1. open": "150.00", "2. high": "152.00", ... }

            Pattern datePattern = Pattern.compile("\"(\\d{4}-\\d{2}-\\d{2})\"\\s*:\\s*\\{");
            Pattern openPattern = Pattern.compile("\"1\\. open\"\\s*:\\s*\"([\\d.]+)\"");
            Pattern highPattern = Pattern.compile("\"2\\. high\"\\s*:\\s*\"([\\d.]+)\"");
            Pattern lowPattern = Pattern.compile("\"3\\. low\"\\s*:\\s*\"([\\d.]+)\"");
            Pattern closePattern = Pattern.compile("\"4\\. close\"\\s*:\\s*\"([\\d.]+)\"");
            Pattern volumePattern = Pattern.compile("\"5\\. volume\"\\s*:\\s*\"([\\d]+)\"");

            Matcher dateMatcher = datePattern.matcher(jsonResponse);

            List<BarData> bars = new ArrayList<>();

            // 解析所有K線
            while (dateMatcher.find()) {
                String dateStr = dateMatcher.group(1);
                int startPos = dateMatcher.end();

                // 找到這個日期對應的數據
                String dataBlock = extractJsonBlock(jsonResponse, startPos);

                Matcher openMatcher = openPattern.matcher(dataBlock);
                Matcher highMatcher = highPattern.matcher(dataBlock);
                Matcher lowMatcher = lowPattern.matcher(dataBlock);
                Matcher closeMatcher = closePattern.matcher(dataBlock);
                Matcher volumeMatcher = volumePattern.matcher(dataBlock);

                if (openMatcher.find() && highMatcher.find() && lowMatcher.find()
                    && closeMatcher.find() && volumeMatcher.find()) {

                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.of(9, 30)); // 假設9:30開盤

                    BarData bar = new BarData(
                        dateTime,
                        Double.parseDouble(openMatcher.group(1)),
                        Double.parseDouble(highMatcher.group(1)),
                        Double.parseDouble(lowMatcher.group(1)),
                        Double.parseDouble(closeMatcher.group(1)),
                        Long.parseLong(volumeMatcher.group(1))
                    );

                    bars.add(bar);
                }
            }

            if (bars.isEmpty()) {
                System.err.println("[AlphaVantageFeed] 無法解析數據，使用備用方案");
                generateFallbackHistoricalData(symbol);
                return;
            }

            // 按時間排序（舊到新）
            bars.sort(Comparator.comparing(b -> b.timestamp));

            // 更新最後價格
            if (!bars.isEmpty()) {
                lastPrices.put(symbol, bars.get(bars.size() - 1).close);
            }

            // 發送最多50根K線
            int startIdx = Math.max(0, bars.size() - 50);
            for (int i = startIdx; i < bars.size(); i++) {
                BarData bar = bars.get(i);
                generateTicksFromBar(symbol, bar.timestamp, bar.open, bar.high, bar.low,
                                   bar.close, bar.volume, symbolListeners);

                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            System.out.println("[AlphaVantageFeed] " + symbol + " 歷史數據加載完成 ("
                             + Math.min(50, bars.size()) + " 根K線)");

        } catch (Exception e) {
            System.err.println("[AlphaVantageFeed] 解析歷史數據失敗: " + e.getMessage());
            generateFallbackHistoricalData(symbol);
        }
    }

    /**
     * 解析並通知實時數據
     */
    private void parseAndNotifyRealTimeData(String symbol, String jsonResponse) {
        try {
            List<MarketDataListener> symbolListeners = listeners.get(symbol);
            if (symbolListeners == null) return;

            // 解析GLOBAL_QUOTE響應
            // "05. price": "150.25"
            Pattern pricePattern = Pattern.compile("\"05\\. price\"\\s*:\\s*\"([\\d.]+)\"");
            Pattern volumePattern = Pattern.compile("\"06\\. volume\"\\s*:\\s*\"([\\d]+)\"");

            Matcher priceMatcher = pricePattern.matcher(jsonResponse);
            Matcher volumeMatcher = volumePattern.matcher(jsonResponse);

            if (priceMatcher.find()) {
                double price = Double.parseDouble(priceMatcher.group(1));
                long volume = volumeMatcher.find() ? Long.parseLong(volumeMatcher.group(1)) : 1000;

                lastPrices.put(symbol, price);

                // 生成Tick
                Tick tick = new Tick(symbol, LocalDateTime.now(), price, volume);

                // 生成Trade
                Trade.Side side = random.nextBoolean() ? Trade.Side.BID : Trade.Side.ASK;
                Trade trade = new Trade(LocalDateTime.now(), price, 100 + random.nextInt(500), side);

                // 生成模擬五檔
                List<DepthLevel> depth = generateMockDepth(price);

                // 通知所有監聽器
                SwingUtilities.invokeLater(() -> {
                    for (MarketDataListener listener : symbolListeners) {
                        listener.onTick(tick);
                        listener.onTrade(trade);
                        listener.onDepthUpdate(depth);
                    }
                });
            }

        } catch (Exception e) {
            // 靜默處理
        }
    }

    /**
     * 從JSON中提取一個對象塊
     */
    private String extractJsonBlock(String json, int startPos) {
        int braceCount = 0;
        int endPos = startPos;

        for (int i = startPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') braceCount++;
            if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    endPos = i + 1;
                    break;
                }
            }
        }

        return json.substring(startPos, endPos);
    }

    /**
     * 從K線生成多個tick
     */
    private void generateTicksFromBar(String symbol, LocalDateTime barTime,
                                      double open, double high, double low, double close,
                                      long volume, List<MarketDataListener> listeners) {
        double[] prices = {open, high, low, close};

        for (int j = 0; j < 4; j++) {
            LocalDateTime tickTime = barTime.plusMinutes(j * 15);
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
     * 生成備用歷史數據（當API失敗時）
     */
    private void generateFallbackHistoricalData(String symbol) {
        System.out.println("[AlphaVantageFeed] 使用模擬數據作為備用方案");

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        double basePrice = 100.0 + random.nextDouble() * 50;
        lastPrices.put(symbol, basePrice);
        LocalDateTime startTime = LocalDateTime.now().minusDays(50);

        for (int i = 0; i < 50; i++) {
            LocalDateTime barTime = startTime.plusDays(i).withHour(9).withMinute(30);

            double open = basePrice + (random.nextDouble() - 0.5) * 10;
            double change = (random.nextDouble() - 0.5) * 5;
            double close = open + change;
            double high = Math.max(open, close) + random.nextDouble() * 2;
            double low = Math.min(open, close) - random.nextDouble() * 2;
            long volume = 1000000 + random.nextInt(5000000);

            generateTicksFromBar(symbol, barTime, open, high, low, close, volume, symbolListeners);

            basePrice = close;

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 生成模擬五檔數據
     */
    private List<DepthLevel> generateMockDepth(double midPrice) {
        List<DepthLevel> depth = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            double askPrice = midPrice + (i + 1) * 0.2;
            long askQty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.ASK, askPrice, askQty, i));
        }

        for (int i = 0; i < 5; i++) {
            double bidPrice = midPrice - (i + 1) * 0.2;
            long bidQty = 1000 + random.nextInt(4000);
            depth.add(new DepthLevel(DepthLevel.Side.BID, bidPrice, bidQty, i));
        }

        return depth;
    }

    /**
     * 內部類：K線數據
     */
    private static class BarData {
        final LocalDateTime timestamp;
        final double open, high, low, close;
        final long volume;

        BarData(LocalDateTime timestamp, double open, double high, double low, double close, long volume) {
            this.timestamp = timestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}
