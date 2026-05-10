package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataNormalizerTest {

    @Test
    void normalizeBarsSortsDeduplicatesAndTrims() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 5, 9, 0);
        Bar later = bar(base.plusMinutes(10), 102);
        Bar duplicateReplacement = bar(base.plusMinutes(5), 103);

        List<Bar> normalized = MarketDataNormalizer.normalizeBars(Arrays.asList(
            bar(base.plusMinutes(5), 101),
            null,
            later,
            bar(base, 100),
            invalidBar(base.plusMinutes(15)),
            duplicateReplacement
        ), 2);

        assertEquals(2, normalized.size());
        assertEquals(base.plusMinutes(5), normalized.get(0).getTimestamp());
        assertEquals(103.0, normalized.get(0).getClose());
        assertEquals(base.plusMinutes(10), normalized.get(1).getTimestamp());
    }

    @Test
    void defaultFetchHistoricalBarsFailsLoudly() {
        MarketDataFeed feed = new MarketDataFeed() {
            @Override public void subscribe(String symbol, MarketDataListener listener) {}
            @Override public void unsubscribe(String symbol, MarketDataListener listener) {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public boolean isConnected() { return false; }
        };

        UnsupportedOperationException error = assertThrows(
            UnsupportedOperationException.class,
            () -> feed.fetchHistoricalBars("2330.TW", Timeframe.M5, 20)
        );
        assertTrue(error.getMessage().contains("K"));
    }

    @Test
    void simulatorFetchHistoricalBarsIsSortedAndRestartable() {
        SimulatorFeed feed = new SimulatorFeed();

        feed.start();
        feed.stop();
        feed.start();

        List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M5, 30);
        feed.stop();

        assertEquals(30, bars.size());
        for (int i = 1; i < bars.size(); i++) {
            assertTrue(!bars.get(i).getTimestamp().isBefore(bars.get(i - 1).getTimestamp()));
        }
    }

    private Bar bar(LocalDateTime timestamp, double close) {
        return new Bar(timestamp, close - 1, close + 1, close - 2, close, 1000);
    }

    private Bar invalidBar(LocalDateTime timestamp) {
        return new Bar(timestamp, 100, 99, 98, 101, 1000);
    }
}
