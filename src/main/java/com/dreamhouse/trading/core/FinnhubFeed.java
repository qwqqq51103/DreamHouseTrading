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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * Finnhub 數據源實作
 * 支持WebSocket實時串流和REST API
 *
 * API文檔: https://finnhub.io/docs/api
 * 免費tier: 60 API calls/minute
 */
public class FinnhubFeed implements MarketDataFeed {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubFeed.class);
    private static final String REST_BASE_URL = "https://finnhub.io/api/v1";
    private static final String WS_URL = "wss://ws.finnhub.io";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, List<MarketDataListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private WebSocketClient wsClient;
    private boolean connected = false;
    private boolean paused = false;
    private ScheduledExecutorService executor;

    public FinnhubFeed(String apiKey) {
        this.apiKey = apiKey != null && !apiKey.trim().isEmpty() ? apiKey : "demo";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // 支持Java 8時間API

        this.executor = Executors.newScheduledThreadPool(2);

        logger.info("FinnhubFeed initialized with API key: {}",
                   apiKey.equals("demo") ? "demo" : "***");
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

            // 如果WebSocket已連接，取消訂閱
            if (list.isEmpty() && wsClient != null && wsClient.isOpen()) {
                unsubscribeWebSocket(symbol);
            }
        }
    }

    @Override
    public void start() {
        connected = true;
        logger.info("Starting Finnhub feed...");

        // 1. 連接WebSocket
        connectWebSocket();

        // 2. 在背景加載歷史數據
        new Thread(() -> {
            loadHistoricalData();
        }, "Finnhub-HistoricalData").start();
    }

    @Override
    public void stop() {
        connected = false;

        // 關閉WebSocket
        if (wsClient != null) {
            wsClient.close();
        }

        // 關閉執行器
        if (executor != null) {
            executor.shutdown();
        }

        logger.info("Finnhub feed stopped");
    }

    @Override
    public boolean isConnected() {
        return connected && wsClient != null && wsClient.isOpen();
    }

    @Override
    public void pause() {
        paused = true;
        logger.info("Finnhub feed paused");
    }

    @Override
    public void resume() {
        paused = false;
        logger.info("Finnhub feed resumed");
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
            String wsUri = WS_URL + "?token=" + apiKey;

            wsClient = new WebSocketClient(new URI(wsUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    logger.info("WebSocket connected");

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

                    // 自動重連（如果仍然connected）
                    if (connected) {
                        executor.schedule(() -> connectWebSocket(), 5, TimeUnit.SECONDS);
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
            String msg = String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol);
            wsClient.send(msg);
            logger.debug("WebSocket subscribed to: {}", symbol);
        }
    }

    /**
     * 取消訂閱WebSocket商品
     */
    private void unsubscribeWebSocket(String symbol) {
        if (wsClient != null && wsClient.isOpen()) {
            String msg = String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol);
            wsClient.send(msg);
            logger.debug("WebSocket unsubscribed from: {}", symbol);
        }
    }

    /**
     * 處理WebSocket消息
     */
    private void processWebSocketMessage(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);

        String type = root.has("type") ? root.get("type").asText() : "";

        if ("trade".equals(type) && root.has("data")) {
            JsonNode data = root.get("data");

            for (JsonNode trade : data) {
                String symbol = trade.get("s").asText();
                double price = trade.get("p").asDouble();
                long volume = trade.get("v").asLong();
                long timestamp = trade.get("t").asLong();

                LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
                );

                // 更新最後價格
                lastPrices.put(symbol, price);

                // 創建Tick和Trade
                Tick tick = new Tick(symbol, time, price, volume);
                Trade.Side side = price > lastPrices.getOrDefault(symbol, price) ?
                                 Trade.Side.ASK : Trade.Side.BID;
                Trade tradeObj = new Trade(time, price, volume, side);

                // 生成模擬五檔
                List<DepthLevel> depth = generateMockDepth(price);

                // 通知監聽器
                List<MarketDataListener> symbolListeners = listeners.get(symbol);
                if (symbolListeners != null) {
                    SwingUtilities.invokeLater(() -> {
                        for (MarketDataListener listener : symbolListeners) {
                            listener.onTick(tick);
                            listener.onTrade(tradeObj);
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

                // 使用candles API獲取歷史數據
                long to = System.currentTimeMillis() / 1000;
                long from = to - (30 * 24 * 60 * 60); // 30天

                String url = REST_BASE_URL + "/stock/candle" +
                           "?symbol=" + symbol +
                           "&resolution=D" +
                           "&from=" + from +
                           "&to=" + to +
                           "&token=" + apiKey;

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

                // API限制：避免超過60次/分鐘
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

        String status = root.has("s") ? root.get("s").asText() : "";

        if (!"ok".equals(status)) {
            logger.warn("API returned status: {} for {}", status, symbol);
            generateFallbackData(symbol);
            return;
        }

        JsonNode timestamps = root.get("t");
        JsonNode opens = root.get("o");
        JsonNode highs = root.get("h");
        JsonNode lows = root.get("l");
        JsonNode closes = root.get("c");
        JsonNode volumes = root.get("v");

        if (timestamps == null || !timestamps.isArray()) {
            logger.warn("No data available for: {}", symbol);
            generateFallbackData(symbol);
            return;
        }

        List<MarketDataListener> symbolListeners = listeners.get(symbol);
        if (symbolListeners == null) return;

        int count = timestamps.size();
        int startIdx = Math.max(0, count - 50); // 最多50根K線

        for (int i = startIdx; i < count; i++) {
            LocalDateTime time = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamps.get(i).asLong()),
                ZoneId.systemDefault()
            );

            double open = opens.get(i).asDouble();
            double high = highs.get(i).asDouble();
            double low = lows.get(i).asDouble();
            double close = closes.get(i).asDouble();
            long volume = volumes.get(i).asLong();

            lastPrices.put(symbol, close);

            // 生成tick
            generateTicksFromBar(symbol, time, open, high, low, close, volume, symbolListeners);

            Thread.sleep(20);
        }

        logger.info("Loaded {} bars for {}", Math.min(50, count), symbol);
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
