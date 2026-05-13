package com.dreamhouse.trading.core.finmind;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

public class FinMindClient implements FinMindGateway {
    public static final String API_BASE_URL = "https://api.finmindtrade.com/api/v4";
    private static final String LOGIN_ENDPOINT = API_BASE_URL + "/login";
    private static final String DATA_ENDPOINT = API_BASE_URL + "/data";
    private static final String DATALIST_ENDPOINT = API_BASE_URL + "/datalist";
    private static final String TRANSLATION_ENDPOINT = API_BASE_URL + "/translation";
    private static final String STORAGE_OBJECTS_ENDPOINT = API_BASE_URL + "/storage_objects";
    private static final String USER_INFO_ENDPOINT = "https://api.web.finmindtrade.com/v2/user_info";
    private static final AtomicLong TOTAL_REQUEST_COUNT = new AtomicLong();

    private final String apiToken;
    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;
    private final FinMindAccessGuard accessGuard;

    public FinMindClient(String apiToken) {
        this(apiToken, new JavaNetHttpSender(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build()), new ObjectMapper());
    }

    public FinMindClient(String apiToken, HttpSender httpSender, ObjectMapper objectMapper) {
        this(apiToken, httpSender, objectMapper, new FinMindAccessGuard());
    }

    public FinMindClient(String apiToken, HttpSender httpSender, ObjectMapper objectMapper, FinMindAccessGuard accessGuard) {
        this.apiToken = apiToken != null ? apiToken.trim() : "";
        this.httpSender = httpSender;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.accessGuard = accessGuard != null ? accessGuard : new FinMindAccessGuard();
    }

    @Override
    public String login(String userId, String password) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "user_id", userId);
        putIfPresent(params, "password", password);
        JsonNode root = sendPostForm(LOGIN_ENDPOINT, params);
        String token = root.path("token").asText("");
        if (token.isBlank()) {
            throw new FinMindException("FinMind login response did not include token");
        }
        return token;
    }

    @Override
    public JsonNode queryData(FinMindRequest request) {
        if (request.getDataset() == FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT) {
            return queryTaiwanStockTradingDailyReport(request);
        }
        if (request.getDataset() == FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT_SEC_ID_AGG) {
            return queryTaiwanStockTradingDailyReportSecIdAgg(request);
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataset", request.getDataset().apiName());
        putIfPresent(params, "data_id", request.getDataId());
        putIfPresent(params, "start_date", formatDate(request.getStartDate()));
        putIfPresent(params, "end_date", formatDate(request.getEndDate()));
        params.putAll(request.getExtraParams());
        return sendGet(DATA_ENDPOINT, params);
    }

    @Override
    public JsonNode queryDataset(FinMindDataset dataset, String dataId, LocalDate startDate, LocalDate endDate) {
        return queryData(FinMindRequest.dataset(dataset)
                .dataId(dataId)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    @Override
    public JsonNode queryDatalist(FinMindDataset dataset) {
        return sendGet(DATALIST_ENDPOINT, Map.of("dataset", dataset.apiName()));
    }

    @Override
    public JsonNode queryTranslation(FinMindDataset dataset) {
        return sendGet(TRANSLATION_ENDPOINT, Map.of("dataset", dataset.apiName()));
    }

    @Override
    public FinMindApiUsage fetchApiUsage() {
        JsonNode root = sendGet(USER_INFO_ENDPOINT, Map.of());
        return new FinMindApiUsage(
                root.path("user_count").asInt(-1),
                root.path("api_request_limit").asInt(-1));
    }

    @Override
    public JsonNode fetchTaiwanStockTickSnapshot(Collection<String> symbols) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbols != null && symbols.size() == 1) {
            String onlySymbol = symbols.iterator().next();
            putIfPresent(params, "data_id", normalizeTaiwanStockId(onlySymbol));
        }
        return sendGet(API_BASE_URL + "/taiwan_stock_tick_snapshot", params);
    }

    @Override
    public JsonNode fetchTaiwanStockTradingDailyReport(String dataId, LocalDate date) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "data_id", normalizeTaiwanStockId(dataId));
        putIfPresent(params, "date", formatDate(date));
        return sendGet(API_BASE_URL + "/taiwan_stock_trading_daily_report", params);
    }

    @Override
    public JsonNode fetchTaiwanStockTradingDailyReportSecIdAgg(String dataId, LocalDate startDate, LocalDate endDate) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "data_id", normalizeTaiwanStockId(dataId));
        putIfPresent(params, "start_date", formatDate(startDate));
        putIfPresent(params, "end_date", formatDate(endDate));
        return sendGet(API_BASE_URL + "/taiwan_stock_trading_daily_report_secid_agg", params);
    }

    private JsonNode queryTaiwanStockTradingDailyReport(FinMindRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        String brokerId = request.getExtraParams().get("securities_trader_id");
        if (brokerId != null && !brokerId.isBlank()) {
            putIfPresent(params, "securities_trader_id", brokerId.trim());
        } else {
            putIfPresent(params, "data_id", normalizeTaiwanStockId(request.getDataId()));
        }
        putIfPresent(params, "date", formatDate(singleRequestDate(request)));
        return sendGet(API_BASE_URL + "/taiwan_stock_trading_daily_report", params);
    }

    private JsonNode queryTaiwanStockTradingDailyReportSecIdAgg(FinMindRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "data_id", normalizeTaiwanStockId(request.getDataId()));
        putIfPresent(params, "start_date", formatDate(request.getStartDate()));
        putIfPresent(params, "end_date", formatDate(request.getEndDate()));
        return sendGet(API_BASE_URL + "/taiwan_stock_trading_daily_report_secid_agg", params);
    }

    private LocalDate singleRequestDate(FinMindRequest request) {
        if (request.getStartDate() != null) {
            return request.getStartDate();
        }
        return request.getEndDate();
    }

    @Override
    public JsonNode fetchStorageObject(FinMindDataset dataset, LocalDate date) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataset", dataset.apiName());
        putIfPresent(params, "date", formatDate(date));
        return sendGet(STORAGE_OBJECTS_ENDPOINT, params);
    }

    @Override
    public boolean testToken() {
        if (apiToken.isBlank()) {
            return false;
        }
        try {
            fetchApiUsage();
            return true;
        } catch (FinMindException e) {
            try {
                queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));
                return true;
            } catch (FinMindException ignored) {
                return false;
            }
        }
    }

    public FinMindResponse toResponse(JsonNode root) {
        return new FinMindResponse(
                root.path("status").asInt(-1),
                root.path("msg").asText(""),
                root.path("data"),
                root);
    }

    public String normalizeTaiwanStockId(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase()
                .replace(".TW", "")
                .replace(".TWO", "");
    }

    public FinMindAccessGuard getAccessGuard() {
        return accessGuard;
    }

    public static long getTotalRequestCount() {
        return TOTAL_REQUEST_COUNT.get();
    }

    public static void resetTotalRequestCount() {
        TOTAL_REQUEST_COUNT.set(0);
    }

    private JsonNode sendGet(String endpoint, Map<String, String> params) {
        URI uri = URI.create(endpoint + toQueryString(params));
        accessGuard.beforeRequest();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (!apiToken.isBlank()) {
            builder.header("Authorization", "Bearer " + apiToken);
        }

        try {
            TOTAL_REQUEST_COUNT.incrementAndGet();
            HttpResponse<String> response = httpSender.send(builder.build());
            JsonNode root = parseBody(response.body());
            validateResponse(response.statusCode(), root, uri);
            accessGuard.recordSuccess();
            return root;
        } catch (FinMindException e) {
            accessGuard.recordFailure(e);
            throw e;
        } catch (IOException e) {
            throw new FinMindException("FinMind network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinMindException("FinMind request interrupted", e);
        }
    }

    private JsonNode sendPostForm(String endpoint, Map<String, String> params) {
        URI uri = URI.create(endpoint);
        accessGuard.beforeRequest();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", "Mozilla/5.0")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(params)))
                .build();

        try {
            TOTAL_REQUEST_COUNT.incrementAndGet();
            HttpResponse<String> response = httpSender.send(request);
            JsonNode root = parseBody(response.body());
            validateResponse(response.statusCode(), root, uri);
            accessGuard.recordSuccess();
            return root;
        } catch (FinMindException e) {
            accessGuard.recordFailure(e);
            throw e;
        } catch (IOException e) {
            throw new FinMindException("FinMind network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinMindException("FinMind request interrupted", e);
        }
    }

    private JsonNode parseBody(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private void validateResponse(int httpStatus, JsonNode root, URI uri) {
        int apiStatus = root.path("status").asInt(httpStatus);
        String message = root.path("msg").asText(root.path("message").asText(""));
        String normalizedMessage = message.toLowerCase();
        if (normalizedMessage.contains("sponsor")) {
            throw new FinMindUnsupportedDatasetException("FinMind dataset requires Sponsor permission: " + uri);
        }
        if (httpStatus == 402 || apiStatus == 402) {
            throw new FinMindQuotaExceededException("FinMind API quota exceeded: " + message, httpStatus, String.valueOf(apiStatus));
        }
        if (httpStatus == 401 || httpStatus == 403 || apiStatus == 401 || apiStatus == 403
                || normalizedMessage.contains("ip banned")) {
            throw new FinMindAccessDeniedException("FinMind authentication or permission failed: " + message, httpStatus, String.valueOf(apiStatus));
        }
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new FinMindException("FinMind HTTP " + httpStatus + ": " + message, httpStatus, String.valueOf(apiStatus));
        }
        if (root.has("status") && apiStatus != 200) {
            throw new FinMindException("FinMind API error: " + message, httpStatus, String.valueOf(apiStatus));
        }
    }

    private String toQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        return joiner.toString();
    }

    private String toFormBody(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                joiner.add(encode(key) + "=" + encode(value));
            }
        });
        return joiner.toString();
    }

    private void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static class JavaNetHttpSender implements HttpSender {
        private final HttpClient httpClient;

        private JavaNetHttpSender(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
