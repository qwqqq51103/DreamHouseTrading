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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * IEX Cloud 數據源實作
 * 提供企業級市場數據和財務數據
 *
 * API文檔: https://iexcloud.io/docs/api/
 * 免費tier: 50,000 messages/month
 */
public class IEXCloudFeed implements MarketDataFeed {

    private static final Logger logger = LoggerFactory.getLogger(IEXCloudFeed.class);
    private static final String BASE_URL = "https://cloud.iexapis.com/stable";
    private static final int UPDATE_INTERVAL_MS = 5000; // 5秒更新

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    private boolean connected = false;
    private boolean paused = false;
    private ScheduledFuture<?> updateTask;

    public IEXCloudFeed(String apiKey) {
        this.apiKey = apiKey != null && !apiKey.trim().isEmpty() ? apiKey : "demo";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        logger.info("IEXCloudFeed initialized with API key: {}",
                   apiKey.equals("demo") ? "demo" : "***");
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        logger.info("Subscribed to symbol: {}", symbol);
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            logger.info("Unsubscribed from symbol: {}", symbol);
        }
    }

    @Override
    public void start() {
        connected = true;
        logger.info("Starting IEX Cloud feed...");

        // 背景加載歷史數據
        new Thread(() -> {
            loadHistoricalData();

            // 開始定期更新實時數據
            updateTask = executor.scheduleAtFixedRate(
                this::updateRealTimeData,
                0,
                UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        }, "IEXCloud-Startup").start();
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

        logger.info("IEX Cloud feed stopped");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void pause() {
        paused = true;
        logger.info("IEX Cloud feed paused");
    }

    @Override
    public void resume() {
        paused = false;
        logger.info("IEX Cloud feed resumed");
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * 加載歷史數據
     */
    private void loadHistoricalData() {
        for (String symbol : listeners.keySet()) {
            try {
                logger.info("Loading historical data for: {}", symbol);

                // 使用 chart API 獲取歷史數據
                String url = BASE_URL + "/stock/" +
                           URLEncoder.encode(symbol, StandardCharsets.UTF_8) +
                           "/chart/1m?token=" + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    parseHistoricalData(symbol, response.body());
                } else {
                    logger.error("HTTP error {}: {}", response.statusCode(), response.body());
                    generateFallbackData(symbol);
                }

                // API限制：避免過於頻繁
                Thread.sleep(1000);

            } catch (Exception e) {
                logger.error("Failed to load historical data for " + symbol, e);
                generateFallbackData(symbol);
            }
        }
    }

    /**
     * 解析歷史數據
     */
    private void parseHistoricalData(String symbol, String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);

        if (!root.isArray() || root.size() == 0) {
            logger.warn("No data available for: {}", symbol);
            generateFallbackData(symbol);
            return;
        }

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        int startIdx = Math.max(0, root.size() - 50); // 最多50根K線

        for (int i = startIdx; i < root.size(); i++) {
            JsonNode bar = root.get(i);

            String dateStr = bar.get("date").asText();
            LocalDateTime time = LocalDateTime.parse(dateStr + "T09:30:00");

            double open = bar.get("open").asDouble();
            double high = bar.get("high").asDouble();
            double low = bar.get("low").asDouble();
            double close = bar.get("close").asDouble();
            long volume = bar.get("volume").asLong();

            lastPrices.put(symbol, close);

            // 生成tick
            generateTicksFromBar(symbol, time, open, high, low, close, volume, symbolListeners);

            Thread.sleep(20);
        }

        logger.info("Loaded {} bars for {}", Math.min(50, root.size()), symbol);
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
                // 使用 quote API 獲取實時報價
                String url = BASE_URL + "/stock/" +
                           URLEncoder.encode(symbol, StandardCharsets.UTF_8) +
                           "/quote?token=" + apiKey;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    parseRealTimeData(symbol, response.body());
                }

            } catch (Exception e) {
                // 靜默處理實時更新錯誤
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 解析實時數據
     */
    private void parseRealTimeData(String symbol, String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        double latestPrice = root.get("latestPrice").asDouble();
        long latestVolume = root.get("latestVolume").asLong();

        lastPrices.put(symbol, latestPrice);

        // 生成Tick
        Tick tick = new Tick(symbol, LocalDateTime.now(), latestPrice, latestVolume);

        // 生成Trade
        Trade.Side side = random.nextBoolean() ? Trade.Side.BID : Trade.Side.ASK;
        Trade trade = new Trade(LocalDateTime.now(), latestPrice,
                               100 + random.nextInt(500), side);

        // 生成模擬五檔
        List<DepthLevel> depth = generateMockDepth(latestPrice);

        // 通知所有監聽器
        SwingUtilities.invokeLater(() -> {
            for (MarketDataListener listener : symbolListeners) {
                listener.onTick(tick);
                listener.onTrade(trade);
                listener.onDepthUpdate(depth);
            }
        });
    }

    /**
     * 從K線生成tick
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
     * 生成備用數據
     */
    private void generateFallbackData(String symbol) {
        logger.info("Using fallback data for: {}", symbol);

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        double basePrice = 100.0 + random.nextDouble() * 50;
        lastPrices.put(symbol, basePrice);
        LocalDateTime startTime = LocalDateTime.now().minusDays(30);

        for (int i = 0; i < 50; i++) {
            LocalDateTime barTime = startTime.plusDays(i);

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
     * 生成模擬五檔
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
}
