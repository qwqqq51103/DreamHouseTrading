package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.finmind.FinMindClient;
import com.dreamhouse.trading.core.model.Bar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

class FinMindFeedTest {

    @Test
    void fetchHistoricalBarsParsesDailyBarsSortedAndLimited() {
        FinMindFeed feed = feedWithResponder(request -> """
                {"status":200,"data":[
                  {"date":"2024-01-03","stock_id":"2330","Trading_Volume":3000,"open":103,"max":108,"min":101,"close":106},
                  {"date":"2024-01-02","stock_id":"2330","Trading_Volume":2000,"open":100,"max":105,"min":99,"close":104},
                  {"date":"2024-01-04","stock_id":"2330","Trading_Volume":4000,"open":106,"max":110,"min":105,"close":109}
                ]}
                """);

        feed.setQueryDate(LocalDate.of(2024, 1, 4));
        List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.D1, 2);

        assertEquals(2, bars.size());
        assertEquals(LocalDate.of(2024, 1, 3), bars.get(0).getTimestamp().toLocalDate());
        assertEquals(LocalDate.of(2024, 1, 4), bars.get(1).getTimestamp().toLocalDate());
        assertEquals(109.0, bars.get(1).getClose());
    }

    @Test
    void fetchHistoricalBarsAggregatesMinuteBars() {
        FinMindFeed feed = feedWithResponder(request -> """
                {"status":200,"data":[
                  {"date":"2024-01-05","minute":"09:00","stock_id":"2330","open":100,"high":101,"low":99,"close":100.5,"volume":10},
                  {"date":"2024-01-05","minute":"09:01","stock_id":"2330","open":100.5,"high":102,"low":100,"close":101.5,"volume":20},
                  {"date":"2024-01-05","minute":"09:02","stock_id":"2330","open":101.5,"high":103,"low":101,"close":102.5,"volume":30},
                  {"date":"2024-01-05","minute":"09:03","stock_id":"2330","open":102.5,"high":104,"low":102,"close":103.5,"volume":40},
                  {"date":"2024-01-05","minute":"09:04","stock_id":"2330","open":103.5,"high":105,"low":103,"close":104.5,"volume":50}
                ]}
                """);

        feed.setQueryDate(LocalDate.of(2024, 1, 5));
        List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M5, 1);

        assertEquals(1, bars.size());
        Bar bar = bars.get(0);
        assertEquals(100.0, bar.getOpen());
        assertEquals(105.0, bar.getHigh());
        assertEquals(99.0, bar.getLow());
        assertEquals(104.5, bar.getClose());
        assertEquals(150, bar.getVolume());
    }

    @Test
    void weeklyBarsFallbackToDailyAggregationWhenWeeklyEndpointFails() {
        FinMindFeed feed = feedWithResponder(request -> {
            String uri = request.uri().toString();
            if (uri.contains("TaiwanStockWeekPrice")) {
                return "{\"status\":403,\"msg\":\"Sponsor permission required\"}";
            }
            return """
                    {"status":200,"data":[
                      {"date":"2024-01-02","stock_id":"2330","Trading_Volume":100,"open":10,"max":12,"min":9,"close":11},
                      {"date":"2024-01-03","stock_id":"2330","Trading_Volume":200,"open":11,"max":13,"min":10,"close":12},
                      {"date":"2024-01-08","stock_id":"2330","Trading_Volume":300,"open":12,"max":15,"min":11,"close":14}
                    ]}
                    """;
        });

        feed.setQueryDate(LocalDate.of(2024, 1, 8));
        List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.W1, 2);

        assertEquals(2, bars.size());
        assertEquals(10.0, bars.get(0).getOpen());
        assertEquals(13.0, bars.get(0).getHigh());
        assertEquals(9.0, bars.get(0).getLow());
        assertEquals(12.0, bars.get(0).getClose());
        assertEquals(300, bars.get(0).getVolume());
    }

    @Test
    void startDoesNotCallFinMindApiAutomatically() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        FinMindClient client = new FinMindClient("token",
                request -> {
                    requestCount.incrementAndGet();
                    return new SimpleResponse(request, 200, "{\"status\":200,\"data\":[]}");
                },
                new ObjectMapper());
        FinMindFeed feed = new FinMindFeed("", client);

        feed.start();

        assertEquals(0, requestCount.get());
        feed.stop();
    }

    private FinMindFeed feedWithResponder(Responder responder) {
        FinMindClient client = new FinMindClient("token",
                request -> new SimpleResponse(request, 200, responder.respond(request)),
                new ObjectMapper());
        return new FinMindFeed("", client);
    }

    @FunctionalInterface
    private interface Responder {
        String respond(HttpRequest request);
    }

    private record SimpleResponse(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override
        public URI uri() { return request.uri(); }
        @Override
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
