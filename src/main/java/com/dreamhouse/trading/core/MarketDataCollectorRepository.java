package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Local JDBC reader for data collected by the MarketDataCollector project.
 */
public class MarketDataCollectorRepository implements AutoCloseable {

    public static final String DEFAULT_JDBC_URL =
            "jdbc:mysql://localhost:3306/market_data?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=UTF-8";
    public static final String DEFAULT_USER = "root";
    public static final String DEFAULT_PASSWORD = "";

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCollectorRepository.class);
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

    private final Connection connection;
    private final boolean closeConnection;
    private final String unavailableReason;

    public MarketDataCollectorRepository(String jdbcUrl, String username, String password) throws SQLException {
        this(DriverManager.getConnection(jdbcUrl, username, password), true);
    }

    public MarketDataCollectorRepository(Connection connection) {
        this(connection, false);
    }

    private MarketDataCollectorRepository(Connection connection, boolean closeConnection) {
        this.connection = connection;
        this.closeConnection = closeConnection;
        this.unavailableReason = null;
    }

    private MarketDataCollectorRepository(String unavailableReason) {
        this.connection = null;
        this.closeConnection = false;
        this.unavailableReason = unavailableReason;
    }

    public static MarketDataCollectorRepository unavailable(String reason) {
        return new MarketDataCollectorRepository(reason);
    }

    public boolean isAvailable() {
        if (connection == null) {
            logger.warn("MarketDataCollector database is unavailable: {}", unavailableReason);
            return false;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            logger.warn("MarketDataCollector database is unavailable: {}", e.getMessage());
            return false;
        }
    }

    public List<Tick> findTodayMarketOpenTicks(String symbol) {
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        return findSessionTicks(symbol, today);
    }

    public List<Tick> findSessionTicks(String symbol, LocalDate date) {
        LocalDate sessionDate = date != null ? date : LocalDate.now(TAIPEI_ZONE);
        return findTicksByTimeRange(symbol, sessionDate.atTime(9, 0), sessionDate.atTime(13, 31));
    }

    public List<Tick> findTicksByTimeRange(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        List<Tick> ticks = new ArrayList<>();
        if (connection == null) {
            return ticks;
        }
        String sql = """
                SELECT symbol, ts, price, volume
                FROM ticks
                WHERE symbol = ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') < ?
                ORDER BY ts ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, formatSqlTime(startTime));
            statement.setString(3, formatSqlTime(endTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ticks.add(parseTick(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector ticks for {}: {}", symbol, e.getMessage());
        }
        return ticks;
    }

    public List<Tick> findLatestTicks(String symbol, int limit) {
        List<Tick> ticks = new ArrayList<>();
        if (connection == null) {
            return ticks;
        }
        String sql = """
                SELECT symbol, ts, price, volume
                FROM ticks
                WHERE symbol = ?
                ORDER BY ts DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ticks.add(0, parseTick(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read latest MarketDataCollector ticks for {}: {}", symbol, e.getMessage());
        }
        return ticks;
    }

    public List<Bar> findLatestCandles(String symbol, String interval, int limit) {
        List<Bar> bars = new ArrayList<>();
        if (connection == null) {
            return bars;
        }
        String sql = """
                SELECT ts, open_price, high_price, low_price, close_price, volume
                FROM candlesticks
                WHERE symbol = ? AND interval_type = ?
                ORDER BY ts DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, interval);
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bars.add(0, parseBar(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector candles for {} {}: {}", symbol, interval, e.getMessage());
        }
        return bars;
    }

    public List<Bar> findTodaySessionCandles(String symbol, String interval) {
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        return findSessionCandles(symbol, interval, today);
    }

    public List<Bar> findSessionCandles(String symbol, String interval, LocalDate date) {
        LocalDate sessionDate = date != null ? date : LocalDate.now(TAIPEI_ZONE);
        return findCandlesByTimeRange(symbol, interval, sessionDate.atTime(9, 0), sessionDate.atTime(13, 30));
    }

    public List<Bar> findCandlesByTimeRange(String symbol, String interval, LocalDateTime startTime, LocalDateTime endTime) {
        List<Bar> bars = new ArrayList<>();
        if (connection == null) {
            return bars;
        }
        String sql = """
                SELECT ts, open_price, high_price, low_price, close_price, volume
                FROM candlesticks
                WHERE symbol = ? AND interval_type = ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') <= ?
                ORDER BY ts ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, interval);
            statement.setString(3, formatSqlTime(startTime));
            statement.setString(4, formatSqlTime(endTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bars.add(parseBar(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector session candles for {} {}: {}", symbol, interval, e.getMessage());
        }
        return bars;
    }

    public int replaceCandlesForDate(String symbol, String interval, LocalDate date, List<Bar> bars) throws SQLException {
        if (connection == null || symbol == null || symbol.isBlank()
                || interval == null || interval.isBlank() || date == null) {
            return 0;
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            deleteCandlesForDate(symbol, interval, date);
            int inserted = insertCandles(symbol, interval, bars);
            connection.commit();
            return inserted;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void deleteCandlesForDate(String symbol, String interval, LocalDate date) throws SQLException {
        String sql = """
                DELETE FROM candlesticks
                WHERE symbol = ? AND interval_type = ?
                  AND (
                    (ts >= ? AND ts < ?)
                    OR (
                      REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') >= ?
                      AND REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') < ?
                    )
                  )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, interval);
            statement.setTimestamp(3, Timestamp.valueOf(date.atStartOfDay()));
            statement.setTimestamp(4, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            statement.setString(5, formatSqlTime(date.atStartOfDay()));
            statement.setString(6, formatSqlTime(date.plusDays(1).atStartOfDay()));
            statement.executeUpdate();
        }
    }

    private int insertCandles(String symbol, String interval, List<Bar> bars) throws SQLException {
        if (bars == null || bars.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO candlesticks
                    (symbol, ts, interval_type, open_price, high_price, low_price, close_price, volume, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        int inserted = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Bar bar : bars) {
                if (bar == null || bar.getTimestamp() == null
                        || bar.getOpen() <= 0.0 || bar.getHigh() <= 0.0
                        || bar.getLow() <= 0.0 || bar.getClose() <= 0.0) {
                    continue;
                }
                statement.setString(1, symbol);
                statement.setTimestamp(2, Timestamp.valueOf(bar.getTimestamp()));
                statement.setString(3, interval);
                statement.setDouble(4, bar.getOpen());
                statement.setDouble(5, bar.getHigh());
                statement.setDouble(6, bar.getLow());
                statement.setDouble(7, bar.getClose());
                statement.setLong(8, Math.max(0L, bar.getVolume()));
                statement.setDouble(9, bar.getClose() * Math.max(0L, bar.getVolume()));
                statement.addBatch();
                inserted++;
            }
            statement.executeBatch();
        }
        return inserted;
    }

    public LocalDateTime findLatestDataTime(String symbol) {
        if (connection == null) {
            return null;
        }
        String sql = """
                SELECT MAX(latest_time) AS latest_time
                FROM (
                    SELECT MAX(REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ')) AS latest_time
                    FROM ticks
                    WHERE symbol = ?
                    UNION ALL
                    SELECT MAX(REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ')) AS latest_time
                    FROM candlesticks
                    WHERE symbol = ?
                ) latest
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, symbol);
            statement.setString(2, symbol);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString("latest_time");
                    return value == null || value.isBlank() ? null : parseTimestamp(value);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector latest timestamp for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private Tick parseTick(ResultSet resultSet) throws SQLException {
        return new Tick(
                resultSet.getString("symbol"),
                parseTimestamp(resultSet.getString("ts")),
                resultSet.getDouble("price"),
                resultSet.getLong("volume"));
    }

    private Bar parseBar(ResultSet resultSet) throws SQLException {
        return new Bar(
                parseTimestamp(resultSet.getString("ts")),
                resultSet.getDouble("open_price"),
                resultSet.getDouble("high_price"),
                resultSet.getDouble("low_price"),
                resultSet.getDouble("close_price"),
                resultSet.getLong("volume"));
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now(TAIPEI_ZONE);
        }
        try {
            return ZonedDateTime.parse(value).withZoneSameInstant(TAIPEI_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            String normalized = value.substring(0, Math.min(19, value.length())).replace('T', ' ');
            return Timestamp.valueOf(normalized).toLocalDateTime();
        }
    }

    private String formatSqlTime(LocalDateTime time) {
        return Timestamp.valueOf(time).toString().substring(0, 19);
    }

    @Override
    public void close() {
        if (!closeConnection || connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logger.warn("Failed to close MarketDataCollector connection: {}", e.getMessage());
        }
    }
}
