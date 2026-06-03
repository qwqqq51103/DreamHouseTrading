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

class MarketContextServiceTest {

    @Test
    void shouldBuildMarketAndIndustryContextFromLocalBars() {
        FakeFeed feed = new FakeFeed(Map.of(
                "TAIEX", risingBars(18_000.0, 2.0),
                "TPEX", risingBars(240.0, 0.04),
                "2330.TW", risingBars(600.0, 0.35),
                "2454.TW", risingBars(900.0, 0.50),
                "2603.TW", fallingBars(180.0, 0.15)
        ));
        MarketContextService service = new MarketContextService(feed, symbol -> "Unknown");

        MarketContextSnapshot snapshot = service.build(
                List.of("2330.TW", "2454.TW", "2603.TW"),
                Timeframe.M5,
                24,
                null,
                Map.of(
                        "2330.TW", "Semiconductor",
                        "2454.TW", "Semiconductor",
                        "2603.TW", "Shipping"),
                Map.of(
                        "Semiconductor", 4,
                        "Shipping", 2));

        assertThat(snapshot.regime()).isEqualTo(MarketRegime.TREND_UP);
        assertThat(snapshot.symbols()).containsKeys("2330.TW", "2454.TW", "2603.TW");
        assertThat(snapshot.symbolContext("2330.TW").benchmarkSymbol()).isEqualTo(MarketContextService.TAIEX_SYMBOL);
        assertThat(snapshot.symbolContext("2330.TW").relativeToBenchmarkPercent()).isGreaterThan(0.0);
        assertThat(snapshot.symbolContext("2330.TW").volumeSustain()).isTrue();
        assertThat(snapshot.industries().get("Semiconductor").dataSymbolCount()).isEqualTo(2);
        assertThat(snapshot.industries().get("Semiconductor").coveragePercent()).isEqualTo(50.0);
        assertThat(snapshot.industries().get("Semiconductor").vwapPassPercent()).isEqualTo(100.0);
    }

    @Test
    void shouldReturnDataMissingWhenIndexBarsAreUnavailable() {
        FakeFeed feed = new FakeFeed(Map.of(
                "2330.TW", risingBars(600.0, 0.35)
        ));
        MarketContextService service = new MarketContextService(feed, symbol -> "Semiconductor");

        MarketContextSnapshot snapshot = service.build(List.of("2330.TW"), Timeframe.M5, 24);

        assertThat(snapshot.regime()).isEqualTo(MarketRegime.DATA_MISSING);
        assertThat(snapshot.status()).contains("TAIEX/TPEx data missing");
        assertThat(snapshot.preloadedBars()).containsKey("2330.TW");
    }

    @Test
    void shouldQualifyStrongStockInWeakMarketOnlyWhenItOutperformsAndStaysAboveVwap() {
        FakeFeed feed = new FakeFeed(Map.of(
                "TAIEX", fallingBars(18_000.0, 3.0),
                "TPEX", fallingBars(240.0, 0.05),
                "2330.TW", risingBars(600.0, 0.60),
                "2317.TW", belowVwapBars(110.0)
        ));
        MarketContextService service = new MarketContextService(feed, symbol -> "Electronics");

        MarketContextSnapshot snapshot = service.build(
                List.of("2330.TW", "2317.TW"),
                Timeframe.M5,
                24,
                null,
                Map.of(
                        "2330.TW", "Electronics",
                        "2317.TW", "Electronics"),
                Map.of("Electronics", 2));

        assertThat(snapshot.regime()).isEqualTo(MarketRegime.WEAK);
        assertThat(snapshot.symbolContext("2330.TW").weakMarketQualified()).isTrue();
        assertThat(snapshot.symbolContext("2317.TW").weakMarketQualified()).isFalse();
        assertThat(snapshot.symbolContext("2317.TW").weakMarketReason()).contains("VWAP");
    }

    private static List<Bar> risingBars(double start, double step) {
        LocalDateTime base = LocalDateTime.of(2026, 5, 13, 9, 0);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> {
                    double open = start + i * step;
                    double close = open + step * 0.70;
                    long volume = i < 19 ? 1_000 : 1_300;
                    return new Bar(base.plusMinutes(i * 5L), open, close + step * 0.20, open - step * 0.20, close, volume);
                })
                .toList();
    }

    private static List<Bar> fallingBars(double start, double step) {
        LocalDateTime base = LocalDateTime.of(2026, 5, 13, 9, 0);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> {
                    double open = start - i * step;
                    double close = open - step * 0.70;
                    long volume = i < 19 ? 1_000 : 900;
                    return new Bar(base.plusMinutes(i * 5L), open, open + step * 0.20, close - step * 0.20, close, volume);
                })
                .toList();
    }

    private static List<Bar> belowVwapBars(double start) {
        LocalDateTime base = LocalDateTime.of(2026, 5, 13, 9, 0);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(i -> {
                    if (i < 20) {
                        return new Bar(base.plusMinutes(i * 5L), start + 10.0, start + 10.3, start + 9.8, start + 10.0, 5_000);
                    }
                    double open = start - i * 0.05;
                    double close = open - 0.10;
                    return new Bar(base.plusMinutes(i * 5L), open, open + 0.1, close - 0.1, close, 800);
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
