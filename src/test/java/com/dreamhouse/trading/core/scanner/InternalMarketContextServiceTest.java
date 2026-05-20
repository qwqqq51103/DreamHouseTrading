package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InternalMarketContextServiceTest {

    @Test
    void allowsLongWhenWatchlistBreadthIsStrong() {
        FakeFeed feed = new FakeFeed(Map.of(
                "2330.TW", risingBars(100),
                "2317.TW", risingBars(80),
                "2454.TW", risingBars(900),
                "3008.TW", risingBars(2000),
                "2379.TW", risingBars(500)
        ));

        MarketContextSnapshot snapshot = new InternalMarketContextService(feed)
                .build(List.of("2330.TW", "2317.TW", "2454.TW", "3008.TW", "2379.TW"), Timeframe.M5, 20);

        assertThat(snapshot.regime()).isEqualTo(MarketRegime.TREND_UP);
        assertThat(snapshot.status()).contains("InternalMarketDecision=ALLOW_LONG");
        assertThat(snapshot.symbols().get("2330.TW").benchmarkSymbol())
                .isEqualTo(InternalMarketContextService.INTERNAL_BENCHMARK);
    }

    @Test
    void blocksLongWhenMostSymbolsFailVwapAndTrend() {
        FakeFeed feed = new FakeFeed(Map.of(
                "2330.TW", fallingBars(100),
                "2317.TW", fallingBars(80),
                "2454.TW", fallingBars(900),
                "3008.TW", fallingBars(2000),
                "2379.TW", risingBars(500)
        ));

        MarketContextSnapshot snapshot = new InternalMarketContextService(feed)
                .build(List.of("2330.TW", "2317.TW", "2454.TW", "3008.TW", "2379.TW"), Timeframe.M5, 20);

        assertThat(snapshot.regime()).isEqualTo(MarketRegime.WEAK);
        assertThat(snapshot.status()).contains("InternalMarketDecision=BLOCK_LONG");
    }

    private static List<Bar> risingBars(double start) {
        LocalDateTime base = LocalDateTime.of(2026, 5, 13, 9, 0);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> {
                    double open = start + i * 0.2;
                    double close = open + 0.15;
                    long volume = i < 18 ? 1_000 : 1_500;
                    return new Bar(base.plusMinutes(i * 5L), open, close + 0.05, open - 0.05, close, volume);
                })
                .toList();
    }

    private static List<Bar> fallingBars(double start) {
        LocalDateTime base = LocalDateTime.of(2026, 5, 13, 9, 0);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> {
                    double open = start - i * 0.2;
                    double close = open - 0.15;
                    long volume = i < 18 ? 1_000 : 800;
                    return new Bar(base.plusMinutes(i * 5L), open, open + 0.05, close - 0.05, close, volume);
                })
                .toList();
    }

    private record FakeFeed(Map<String, List<Bar>> barsBySymbol) implements MarketDataFeed {
        @Override
        public void subscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void unsubscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            return barsBySymbol.getOrDefault(symbol, List.of());
        }
    }
}
