package com.dreamhouse.trading.core.finmind;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

class FinMindClientTest {

    @Test
    void quotaExceededBecomesDiagnosticException() {
        FinMindClient client = new FinMindClient("token",
                request -> response(request, 402, "{\"status\":402,\"msg\":\"Requests reach the upper limit.\"}"),
                new ObjectMapper());

        FinMindQuotaExceededException ex = assertThrows(FinMindQuotaExceededException.class,
                () -> client.queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));

        assertTrue(ex.getMessage().contains("quota"));
    }

    @Test
    void accessDeniedBecomesDiagnosticException() {
        FinMindClient client = new FinMindClient("token",
                request -> response(request, 403, "{\"status\":403,\"msg\":\"ip banned\"}"),
                new ObjectMapper());

        FinMindAccessDeniedException ex = assertThrows(FinMindAccessDeniedException.class,
                () -> client.queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));

        assertTrue(ex.getMessage().contains("ip banned"));
    }

    @Test
    void apiStatusAccessDeniedBecomesDiagnosticException() {
        FinMindClient client = new FinMindClient("token",
                request -> response(request, 200, "{\"status\":403,\"msg\":\"ip banned\"}"),
                new ObjectMapper());

        assertThrows(FinMindAccessDeniedException.class,
                () -> client.queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));
    }

    @Test
    void accessGuardBlocksLaterRequestsAfterIpBan() {
        CountingSender sender = new CountingSender(403, "{\"status\":403,\"msg\":\"ip banned\"}");
        FinMindClient client = new FinMindClient("token", sender, new ObjectMapper());

        assertThrows(FinMindAccessDeniedException.class,
                () -> client.queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));
        assertThrows(FinMindRequestBlockedException.class,
                () -> client.queryDataset(FinMindDataset.TAIWAN_STOCK_PRICE, "2330",
                        LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)));
        assertEquals(1, sender.requestCount);
    }

    @Test
    void tokenTestUsesRealFinMindUsageEndpointShape() {
        FinMindClient client = new FinMindClient("token",
                request -> response(request, 200, "{\"user_count\":12,\"api_request_limit\":600}"),
                new ObjectMapper());

        assertTrue(client.testToken());
        assertEquals("12 / 600", client.fetchApiUsage().displayText());
    }

    @Test
    void batchSnapshotOmitsDataIdForMultipleSymbols() {
        CapturingSender sender = new CapturingSender("{\"status\":200,\"data\":[]}");
        FinMindClient client = new FinMindClient("token", sender, new ObjectMapper());

        client.fetchTaiwanStockTickSnapshot(List.of("2330.TW", "2317.TW"));

        assertTrue(sender.lastUri.toString().contains("taiwan_stock_tick_snapshot"));
        assertFalse(sender.lastUri.toString().contains("data_id="));
    }

    @Test
    void loginPostsCredentialsAndReturnsToken() {
        CapturingSender sender = new CapturingSender("{\"status\":200,\"token\":\"new-token\"}");
        FinMindClient client = new FinMindClient("", sender, new ObjectMapper());

        assertEquals("new-token", client.login("user@example.com", "secret"));
        assertTrue(sender.lastUri.toString().endsWith("/login"));
        assertEquals("POST", sender.lastRequest.method());
    }

    @Test
    void storageObjectEndpointUsesDatasetAndDate() {
        CapturingSender sender = new CapturingSender("{\"status\":200,\"data\":[]}");
        FinMindClient client = new FinMindClient("token", sender, new ObjectMapper());

        client.fetchStorageObject(FinMindDataset.TAIWAN_STOCK_PRICE_TICK, LocalDate.of(2024, 1, 2));

        String uri = sender.lastUri.toString();
        assertTrue(uri.contains("/storage_objects"));
        assertTrue(uri.contains("dataset=TaiwanStockPriceTick"));
        assertTrue(uri.contains("date=2024-01-02"));
    }

    @Test
    void tradingDailyReportDatasetUsesDedicatedEndpointAndDate() {
        CapturingSender sender = new CapturingSender("{\"status\":200,\"data\":[]}");
        FinMindClient client = new FinMindClient("token", sender, new ObjectMapper());

        client.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT)
                .dataId("2330.TW")
                .startDate(LocalDate.of(2024, 7, 1))
                .build());

        String uri = sender.lastUri.toString();
        assertTrue(uri.contains("/taiwan_stock_trading_daily_report"));
        assertTrue(uri.contains("data_id=2330"));
        assertTrue(uri.contains("date=2024-07-01"));
        assertFalse(uri.contains("dataset="));
        assertFalse(uri.contains("start_date="));
    }

    @Test
    void tradingDailyReportSecIdAggDatasetUsesDedicatedEndpoint() {
        CapturingSender sender = new CapturingSender("{\"status\":200,\"data\":[]}");
        FinMindClient client = new FinMindClient("token", sender, new ObjectMapper());

        client.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT_SEC_ID_AGG)
                .dataId("2330.TW")
                .startDate(LocalDate.of(2024, 7, 1))
                .endDate(LocalDate.of(2024, 7, 15))
                .build());

        String uri = sender.lastUri.toString();
        assertTrue(uri.contains("/taiwan_stock_trading_daily_report_secid_agg"));
        assertTrue(uri.contains("data_id=2330"));
        assertTrue(uri.contains("start_date=2024-07-01"));
        assertTrue(uri.contains("end_date=2024-07-15"));
        assertFalse(uri.contains("dataset="));
    }

    @Test
    void singleDayDatasetRejectsDateRange() {
        assertThrows(IllegalArgumentException.class,
                () -> FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_K_BAR)
                        .dataId("2330")
                        .startDate(LocalDate.of(2024, 1, 1))
                        .endDate(LocalDate.of(2024, 1, 2))
                        .build());
    }

    @Test
    void datasetRegistryFindsCapabilities() {
        FinMindDatasetRegistry registry = new FinMindDatasetRegistry();

        assertEquals(Optional.of(FinMindDataset.TAIWAN_STOCK_K_BAR), registry.findByApiName("TaiwanStockKBar"));
        assertTrue(registry.listSingleDayDatasets().contains(FinMindDataset.TAIWAN_STOCK_K_BAR));
        assertTrue(registry.requiresPaidTier(FinMindDataset.TAIWAN_STOCK_TICK_SNAPSHOT));
    }

    private static HttpResponse<String> response(HttpRequest request, int status, String body) {
        return new SimpleResponse(request, status, body);
    }

    private static class CapturingSender implements FinMindClient.HttpSender {
        private final String body;
        private URI lastUri;
        private HttpRequest lastRequest;

        private CapturingSender(String body) {
            this.body = body;
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) {
            lastRequest = request;
            lastUri = request.uri();
            return response(request, 200, body);
        }
    }

    private static class CountingSender implements FinMindClient.HttpSender {
        private final int status;
        private final String body;
        private int requestCount;

        private CountingSender(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) {
            requestCount++;
            return response(request, status, body);
        }
    }

    private record SimpleResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
        @Override
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override
        public URI uri() { return request.uri(); }
        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
