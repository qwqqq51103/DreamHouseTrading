package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataCollectorFeedTest {
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

    @Test
    void repositoryReadsLatestCandlesInAscendingOrder() throws Exception {
        try (Connection connection = createSchema()) {
            insertCandle(connection, "2330.TW", "1m", "2026-01-02 09:00:00", 100, 102, 99, 101, 10);
            insertCandle(connection, "2330.TW", "1m", "2026-01-02 09:01:00", 101, 103, 100, 102, 15);

            MarketDataCollectorRepository repository = new MarketDataCollectorRepository(connection);

            List<Bar> bars = repository.findLatestCandles("2330.TW", "1m", 2);

            assertThat(bars).hasSize(2);
            assertThat(bars.get(0).getTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 2, 9, 0));
            assertThat(bars.get(1).getClose()).isEqualTo(102.0);
        }
    }

    @Test
    void feedAggregatesTodayTicksWhenStoredCandlesAreInsufficient() throws Exception {
        try (Connection connection = createSchema()) {
            LocalDateTime today = LocalDateTime.now(TAIPEI_ZONE).toLocalDate().atTime(9, 0);
            insertTick(connection, "2330.TW", today.plusSeconds(10), 100.0, 10);
            insertTick(connection, "2330.TW", today.plusSeconds(30), 102.0, 18);
            insertTick(connection, "2330.TW", today.plusMinutes(1).plusSeconds(5), 101.0, 25);
            insertTick(connection, "2330.TW", today.plusMinutes(1).plusSeconds(20), 104.0, 40);

            MarketDataCollectorFeed feed = new MarketDataCollectorFeed(
                    new MarketDataCollectorRepository(connection),
                    Duration.ofDays(1));

            List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M1, 20);

            assertThat(bars).hasSize(2);
            assertThat(bars.get(0).getOpen()).isEqualTo(100.0);
            assertThat(bars.get(0).getHigh()).isEqualTo(102.0);
            assertThat(bars.get(0).getLow()).isEqualTo(100.0);
            assertThat(bars.get(0).getClose()).isEqualTo(102.0);
            assertThat(bars.get(0).getVolume()).isEqualTo(18L);
            assertThat(bars.get(1).getOpen()).isEqualTo(101.0);
            assertThat(bars.get(1).getClose()).isEqualTo(104.0);
            assertThat(bars.get(1).getVolume()).isEqualTo(22L);
        }
    }

    @Test
    void feedReadsTodayIntradayCandlesOnlyWithinRegularSession() throws Exception {
        try (Connection connection = createSchema()) {
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            insertCandle(connection, "2330.TW", "1m", today.atTime(8, 59).toString(), 90, 91, 89, 90, 10);
            insertCandle(connection, "2330.TW", "1m", today.atTime(9, 0).toString(), 100, 102, 99, 101, 20);
            insertCandle(connection, "2330.TW", "1m", today.atTime(13, 30).toString(), 120, 121, 119, 120, 30);
            insertCandle(connection, "2330.TW", "1m", today.atTime(13, 31).toString(), 130, 131, 129, 130, 40);

            MarketDataCollectorFeed feed = new MarketDataCollectorFeed(
                    new MarketDataCollectorRepository(connection),
                    Duration.ofDays(1));

            List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M1, 1);

            assertThat(bars).extracting(Bar::getTimestamp)
                    .containsExactly(today.atTime(9, 0), today.atTime(13, 30));
            assertThat(bars).extracting(Bar::getClose).containsExactly(101.0, 120.0);
        }
    }

    @Test
    void feedReadsRequestedSessionDateAndCanonicalImportedIntervals() throws Exception {
        try (Connection connection = createSchema()) {
            LocalDate importedDate = LocalDate.of(2026, 5, 12);
            insertCandle(connection, "2330.TW", "1m", importedDate.atTime(9, 0).toString(), 1, 1, 1, 1, 1);
            insertCandle(connection, "2330.TW", "M1", importedDate.atTime(9, 0).toString(), 100, 102, 99, 101, 20);
            insertCandle(connection, "2330.TW", "M1", importedDate.atTime(9, 1).toString(), 101, 103, 100, 102, 30);
            insertCandle(connection, "2330.TW", "M1", importedDate.atTime(13, 31).toString(), 130, 131, 129, 130, 40);

            MarketDataCollectorFeed feed = new MarketDataCollectorFeed(
                    new MarketDataCollectorRepository(connection),
                    Duration.ofDays(1));

            List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M1, 100, importedDate);

            assertThat(bars).extracting(Bar::getTimestamp)
                    .containsExactly(importedDate.atTime(9, 0), importedDate.atTime(9, 1));
            assertThat(bars).extracting(Bar::getClose).containsExactly(101.0, 102.0);
        }
    }

    @Test
    void feedReadsCrossDayWarmupBarsBeforeRequestedSessionOnly() throws Exception {
        try (Connection connection = createSchema()) {
            LocalDate sessionDate = LocalDate.of(2026, 5, 21);
            insertCandle(connection, "2330.TW", "M5", sessionDate.minusDays(1).atTime(13, 20).toString(), 100, 101, 99, 100, 10);
            insertCandle(connection, "2330.TW", "M5", sessionDate.minusDays(1).atTime(13, 25).toString(), 101, 102, 100, 101, 10);
            insertCandle(connection, "2330.TW", "M5", sessionDate.atTime(9, 0).toString(), 102, 103, 101, 102, 10);

            MarketDataCollectorFeed feed = new MarketDataCollectorFeed(
                    new MarketDataCollectorRepository(connection),
                    Duration.ofDays(1));

            List<Bar> bars = feed.fetchWarmupBarsBeforeSession("2330.TW", Timeframe.M5, sessionDate, 1);

            assertThat(bars).hasSize(1);
            assertThat(bars.get(0).getTimestamp()).isEqualTo(sessionDate.minusDays(1).atTime(13, 25));
        }
    }

    @Test
    void closingAuctionTimeDoesNotCountAsCollectorFailureWindow() {
        LocalDate today = LocalDate.of(2026, 5, 13);

        assertThat(MarketDataCollectorFeed.isClosingAuctionTime(today.atTime(13, 24, 59))).isFalse();
        assertThat(MarketDataCollectorFeed.isClosingAuctionTime(today.atTime(13, 25))).isTrue();
        assertThat(MarketDataCollectorFeed.isClosingAuctionTime(today.atTime(13, 30))).isTrue();
        assertThat(MarketDataCollectorFeed.isClosingAuctionTime(today.atTime(13, 31))).isFalse();
    }

    @Test
    void loadHistoricalDataNotifiesSubscribedListeners() throws Exception {
        try (Connection connection = createSchema()) {
            String timestamp = LocalDate.now(TAIPEI_ZONE).atTime(9, 0).toString();
            insertCandle(connection, "2317.TW", "1m", timestamp, 100, 103, 99, 102, 40);

            MarketDataCollectorFeed feed = new MarketDataCollectorFeed(
                    new MarketDataCollectorRepository(connection),
                    Duration.ofDays(1));
            CountDownLatch latch = new CountDownLatch(4);
            List<Tick> ticks = Collections.synchronizedList(new ArrayList<>());
            feed.subscribe("2317.TW", new MarketDataListener() {
                @Override
                public void onTick(Tick tick) {
                    ticks.add(tick);
                    latch.countDown();
                }
            });

            feed.loadHistoricalData("2317.TW", Timeframe.M1, 1);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(ticks).extracting(Tick::getPrice).containsExactly(100.0, 103.0, 99.0, 102.0);
        }
    }

    private Connection createSchema() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ticks (
                        symbol VARCHAR(32),
                        ts VARCHAR(64),
                        price DOUBLE,
                        volume BIGINT,
                        bid DOUBLE,
                        ask DOUBLE,
                        created_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE candlesticks (
                        symbol VARCHAR(32),
                        ts TIMESTAMP,
                        interval_type VARCHAR(16),
                        open_price DOUBLE,
                        high_price DOUBLE,
                        low_price DOUBLE,
                        close_price DOUBLE,
                        volume BIGINT,
                        amount DOUBLE,
                        created_at TIMESTAMP
                    )
                    """);
        }
        return connection;
    }

    private void insertTick(Connection connection, String symbol, LocalDateTime timestamp, double price, long volume)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ticks(symbol, ts, price, volume, bid, ask, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, symbol);
            statement.setString(2, timestamp.toString());
            statement.setDouble(3, price);
            statement.setLong(4, volume);
            statement.setDouble(5, price - 0.1);
            statement.setDouble(6, price + 0.1);
            statement.executeUpdate();
        }
    }

    private void insertCandle(
            Connection connection,
            String symbol,
            String interval,
            String timestamp,
            double open,
            double high,
            double low,
            double close,
            long volume) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO candlesticks(symbol, ts, interval_type, open_price, high_price, low_price, close_price, volume, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, symbol);
            statement.setString(2, timestamp);
            statement.setString(3, interval);
            statement.setDouble(4, open);
            statement.setDouble(5, high);
            statement.setDouble(6, low);
            statement.setDouble(7, close);
            statement.setLong(8, volume);
            statement.setDouble(9, close * volume);
            statement.executeUpdate();
        }
    }
}
