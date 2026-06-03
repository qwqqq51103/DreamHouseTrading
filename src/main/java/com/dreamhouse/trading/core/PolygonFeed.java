package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
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
 * Polygon.io 數據源實作
 * 支持實時和歷史市場數據，包括股票、期貨、外匯、加密貨幣
 *
 * API文檔: https://polygon.io/docs/
 * 免費tier: 5 API calls/min, WebSocket支持
 */
public class PolygonFeed implements MarketDataFeed {

    private static final Logger logger = LoggerFactory.getLogger(PolygonFeed.class);
    private static final String REST_BASE_URL = "https://api.polygon.io";
    private static final String WS_URL = "wss://socket.polygon.io/stocks";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private WebSocketClient wsClient;
    private volatile boolean connected = false;
    private boolean paused = false;
    private ScheduledExecutorService executor;
    private final boolean connectOnStart;

    public PolygonFeed(String apiKey) {
        this(apiKey, true);
    }

    PolygonFeed(String apiKey, boolean connectOnStart) {
        this.apiKey = apiKey != null && !apiKey.trim().isEmpty() ? apiKey : "demo";
        this.connectOnStart = connectOnStart;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        logger.info("PolygonFeed initialized with API key: {}",
                   this.apiKey.equals("demo") ? "demo" : "***");
    }

    @Override
    public void subscribe(String symbol, MarketDataListener listener) {
        listeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        logger.info("Subscribed to symbol: {}", symbol);

        // 如果WebSocket已連接，訂閱實時數據
        if (wsClient != null && wsClient.isOpen()) {
            subscribeWebSocket(symbol);
        }
    }

    @Override
    public void unsubscribe(String symbol, MarketDataListener listener) {
        List<MarketDataListener> list = listeners.get(symbol);
        if (list != null) {
            list.remove(listener);
            logger.info("Unsubscribed from symbol: {}", symbol);

            if (list.isEmpty() && wsClient != null && wsClient.isOpen()) {
                unsubscribeWebSocket(symbol);
            }
        }
    }

    @Override
    public synchronized void start() {
        connected = true;
        logger.info("Starting Polygon.io feed...");
        ensureExecutor();

        if (!connectOnStart) {
            return;
        }

        // 1. 連接WebSocket
        connectWebSocket();

        // 2. 背景加載歷史數據
        executor.execute(this::loadHistoricalData);
    }

    @Override
    public synchronized void stop() {
        connected = false;

        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.debug("Polygon executor did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        logger.info("Polygon.io feed stopped");
    }

    private void ensureExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newScheduledThreadPool(2);
        }
    }

    @Override
    public boolean isConnected() {
        return connected && wsClient != null && wsClient.isOpen();
    }

    @Override
    public void pause() {
        paused = true;
        logger.info("Polygon.io feed paused");
    }

    @Override
    public void resume() {
        paused = false;
        logger.info("Polygon.io feed resumed");
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    /**
     * 連接WebSocket
     */
    private void connectWebSocket() {
        try {
            String wsUri = WS_URL;

            wsClient = new WebSocketClient(new URI(wsUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    logger.info("WebSocket connected");

                    // 認證
                    String authMsg = String.format("{\"action\":\"auth\",\"params\":\"%s\"}", apiKey);
                    send(authMsg);

                    // 訂閱所有已註冊的商品
                    for (String symbol : listeners.keySet()) {
                        subscribeWebSocket(symbol);
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (paused) return;

                    try {
                        processWebSocketMessage(message);
                    } catch (Exception e) {
                        logger.error("Error processing WebSocket message", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.warn("WebSocket closed: {} - {}", code, reason);

                    // 自動重連
                    if (connected && executor != null && !executor.isShutdown()) {
                        try {
                            executor.schedule(() -> connectWebSocket(), 5, TimeUnit.SECONDS);
                        } catch (RejectedExecutionException e) {
                            if (connected) {
                                logger.warn("Polygon reconnect scheduling rejected", e);
                            }
                        }
                    }
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("WebSocket error", ex);
                }
            };

            wsClient.connect();

        } catch (Exception e) {
            logger.error("Failed to connect WebSocket", e);
        }
    }

    /**
     * 訂閱WebSocket商品
     */
    private void subscribeWebSocket(String symbol) {
        if (wsClient != null && wsClient.isOpen()) {
            String msg = String.format("{\"action\":\"subscribe\",\"params\":\"T.%s\"}", symbol);
            wsClient.send(msg);
            logger.debug("WebSocket subscribed to: {}", symbol);
        }
    }

    /**
     * 取消訂閱WebSocket商品
     */
    private void unsubscribeWebSocket(String symbol) {
        if (wsClient != null && wsClient.isOpen()) {
            String msg = String.format("{\"action\":\"unsubscribe\",\"params\":\"T.%s\"}", symbol);
            wsClient.send(msg);
            logger.debug("WebSocket unsubscribed from: {}", symbol);
        }
    }

    /**
     * 處理WebSocket消息
     */
    private void processWebSocketMessage(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);

        if (!root.isArray()) {
            return;
        }

        for (JsonNode event : root) {
            String eventType = event.has("ev") ? event.get("ev").asText() : "";

            if ("T".equals(eventType)) { // Trade事件
                String symbol = event.get("sym").asText();
                double price = event.get("p").asDouble();
                long volume = event.get("s").asLong();
                long timestamp = event.get("t").asLong();

                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
                );

                lastPrices.put(symbol, price);

                // 創建Tick和Trade
                Tick tick = new Tick(symbol, time, price, volume);
                Trade.Side side = price > lastPrices.getOrDefault(symbol, price) ?
                                 Trade.Side.ASK : Trade.Side.BID;
                Trade trade = new Trade(time, price, volume, side);

                // 生成模擬五檔
                List<DepthLevel> depth = generateMockDepth(price);

                // 通知監聽器
                List<MarketDataListener> symbolListeners = listeners.get(symbol);
                if (symbolListeners != null) {
                    SwingUtilities.invokeLater(() -> {
                        for (MarketDataListener listener : symbolListeners) {
                            listener.onTick(tick);
                            listener.onTrade(trade);
                            listener.onDepthUpdate(depth);
                        }
                    });
                }
            }
        }
    }

    /**
     * 加載歷史數據
     */
    private void loadHistoricalData() {
        for (String symbol : listeners.keySet()) {
            try {
                logger.info("Loading historical data for: {}", symbol);

                // 使用 aggregates API 獲取歷史數據
                long to = System.currentTimeMillis();
                long from = to - (30L * 24 * 60 * 60 * 1000); // 30天

                String url = REST_BASE_URL + "/v2/aggs/ticker/" +
                           URLEncoder.encode(symbol, StandardCharsets.UTF_8) +
                           "/range/1/day/" + from + "/" + to +
                           "?adjusted=true&sort=asc&limit=50&apiKey=" + apiKey;

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

                // API限制：避免超過5次/分
                Thread.sleep(12000);

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

        if (!root.has("results") || !root.get("results").isArray()) {
            logger.warn("No data available for: {}", symbol);
            generateFallbackData(symbol);
            return;
        }

        JsonNode results = root.get("results");
        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        for (int i = 0; i < results.size(); i++) {
            JsonNode bar = results.get(i);

            long timestamp = bar.get("t").asLong();
            LocalDateTime time = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            );

            double open = bar.get("o").asDouble();
            double high = bar.get("h").asDouble();
            double low = bar.get("l").asDouble();
            double close = bar.get("c").asDouble();
            long volume = bar.get("v").asLong();

            lastPrices.put(symbol, close);

            // 生成tick
            generateTicksFromBar(symbol, time, open, high, low, close, volume, symbolListeners);

            Thread.sleep(20);
        }

        logger.info("Loaded {} bars for {}", results.size(), symbol);
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
