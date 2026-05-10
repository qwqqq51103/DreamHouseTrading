package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketScannerServiceTest {

    @Test
    void scanReturnsDiagnosticResultWhenFeedDoesNotSupportHistoricalBars() {
        MarketScannerService scanner = new MarketScannerService(new UnsupportedFeed());

        MarketScanResult result = scanner.scan("2330.TW", MarketScannerService.ScanRequest.createDefault());

        assertEquals("2330.TW", result.getSymbol());
        assertEquals(DecisionResult.Action.NO_ACTION, result.getDecisionResult().getAction());
        assertEquals(0.0, result.getScore());
        assertTrue(result.getReason().contains("K"));
    }

    @Test
    void scanCollectionSortsByScoreDescending() {
        MarketScannerService scanner = new MarketScannerService(new UnsupportedFeed()) {
            @Override
            public MarketScanResult scan(String symbol, ScanRequest request) {
                double score = switch (symbol) {
                    case "HIGH" -> 0.9;
                    case "MID" -> 0.5;
                    default -> 0.1;
                };
                return MarketScanResult.builder(symbol)
                    .tradeMode(TradeMode.DAY_TRADE)
                    .decisionResult(new DecisionResult.Builder()
                        .action(DecisionResult.Action.HOLD)
                        .source(DecisionResult.Source.TECHNICAL)
                        .reason(symbol)
                        .confidence(score)
                        .build())
                    .score(score)
                    .build();
            }
        };

        List<MarketScanResult> results = scanner.scan(List.of("LOW", "HIGH", "MID"),
            MarketScannerService.ScanRequest.createDefault());

        assertEquals(List.of("HIGH", "MID", "LOW"),
            results.stream().map(MarketScanResult::getSymbol).toList());
    }

    @Test
    void scanUsesRequestedBarsAndReturnsResultModel() {
        Map<String, List<Bar>> barsBySymbol = Map.of(
            "TEST", createBars(LocalDateTime.of(2026, 1, 5, 9, 0), 40)
        );
        RecordingFeed feed = new RecordingFeed(barsBySymbol);
        MarketScannerService scanner = new MarketScannerService(feed);

        MarketScanResult result = scanner.scan("TEST", MarketScannerService.ScanRequest.createDefault()
            .timeframe(Timeframe.M5)
            .barCount(30)
            .tradeMode(TradeMode.DAY_TRADE));

        assertEquals("TEST", feed.lastSymbol);
        assertEquals(Timeframe.M5, feed.lastTimeframe);
        assertEquals(30, feed.lastBarCount);
        assertEquals("TEST", result.getSymbol());
        assertEquals(TradeMode.DAY_TRADE, result.getTradeMode());
        assertNotNull(result.getDecisionResult());
        assertTrue(result.getScore() >= 0.0 && result.getScore() <= 1.0);
        assertNotNull(result.getRawSignalSummary());
        assertNotNull(result.getBlockReason());
    }

    private static List<Bar> createBars(LocalDateTime start, int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                double close = 100 + Math.sin(i / 3.0) * 2 + i * 0.05;
                return new Bar(
                    start.plusMinutes(i * 5L),
                    close - 0.4,
                    close + 0.8,
                    close - 0.8,
                    close,
                    1000 + i
                );
            })
            .toList();
    }

    private static class UnsupportedFeed implements MarketDataFeed {
        @Override public void subscribe(String symbol, MarketDataListener listener) {}
        @Override public void unsubscribe(String symbol, MarketDataListener listener) {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isConnected() { return false; }
    }

    private static class RecordingFeed extends UnsupportedFeed {
        private final Map<String, List<Bar>> barsBySymbol;
        private String lastSymbol;
        private Timeframe lastTimeframe;
        private int lastBarCount;

        private RecordingFeed(Map<String, List<Bar>> barsBySymbol) {
            this.barsBySymbol = barsBySymbol;
        }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            this.lastSymbol = symbol;
            this.lastTimeframe = timeframe;
            this.lastBarCount = barCount;
            return barsBySymbol.getOrDefault(symbol, List.of());
        }
    }
}
