package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.util.BarAggregator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        List<String> aliases = symbolAliases(symbol);
        String sql = """
                SELECT symbol, ts, price, volume
                FROM ticks
                WHERE symbol IN (%s)
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') < ?
                ORDER BY ts ASC
                """.formatted(placeholders(aliases.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindStrings(statement, 1, aliases);
            statement.setString(parameterIndex++, formatSqlTime(startTime));
            statement.setString(parameterIndex, formatSqlTime(endTime));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ticks.add(parseTick(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector ticks for {}: {}", symbol, e.getMessage());
        }
        if (isIndexSymbol(symbol)) {
            List<Tick> rawIndexTicks = findRawIndexTicksByTimeRange(symbol, startTime, endTime);
            if (!rawIndexTicks.isEmpty()) {
                return rawIndexTicks;
            }
            if (!ticks.isEmpty()) {
                ticks = expandDuplicateIndexTimestamps(ticks);
            }
        }
        return ticks;
    }

    public List<Tick> findLatestTicks(String symbol, int limit) {
        List<Tick> ticks = new ArrayList<>();
        if (connection == null) {
            return ticks;
        }
        List<String> aliases = symbolAliases(symbol);
        String sql = """
                SELECT symbol, ts, price, volume
                FROM ticks
                WHERE symbol IN (%s)
                ORDER BY ts DESC
                LIMIT ?
                """.formatted(placeholders(aliases.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindStrings(statement, 1, aliases);
            statement.setInt(parameterIndex, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ticks.add(0, parseTick(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read latest MarketDataCollector ticks for {}: {}", symbol, e.getMessage());
        }
        if (isIndexSymbol(symbol)) {
            List<Tick> rawIndexTicks = findLatestRawIndexTicks(symbol, limit);
            if (!rawIndexTicks.isEmpty()) {
                return rawIndexTicks;
            }
        }
        return ticks;
    }

    public List<Bar> findLatestCandles(String symbol, String interval, int limit) {
        List<Bar> bars = new ArrayList<>();
        if (connection == null) {
            return bars;
        }
        List<String> aliases = symbolAliases(symbol);
        String sql = """
                SELECT ts, open_price, high_price, low_price, close_price, volume
                FROM candlesticks
                WHERE symbol IN (%s) AND interval_type = ?
                ORDER BY ts DESC
                LIMIT ?
                """.formatted(placeholders(aliases.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindStrings(statement, 1, aliases);
            statement.setString(parameterIndex++, interval);
            statement.setInt(parameterIndex, Math.max(1, limit));
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
        List<String> aliases = symbolAliases(symbol);
        String sql = """
                SELECT ts, open_price, high_price, low_price, close_price, volume
                FROM candlesticks
                WHERE symbol IN (%s) AND interval_type = ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') <= ?
                ORDER BY ts ASC
                """.formatted(placeholders(aliases.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindStrings(statement, 1, aliases);
            statement.setString(parameterIndex++, interval);
            statement.setString(parameterIndex++, formatSqlTime(startTime));
            statement.setString(parameterIndex, formatSqlTime(endTime));
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

    public FinMindSqlWriteResult writeFinMindDatasetRows(
            FinMindDataset dataset,
            String dataId,
            LocalDate startDate,
            LocalDate endDate,
            JsonNode dataArray) throws SQLException {
        if (connection == null || dataset == null || dataArray == null || !dataArray.isArray()) {
            return new FinMindSqlWriteResult("", 0, 0, 0);
        }

        String tableName = finMindTableName(dataset);
        int rawRows = dataArray.size();
        int cachedRows = upsertFinMindRawRows(tableName, dataset, dataId, startDate, endDate, dataArray);
        int marketRows = switch (dataset) {
            case TAIWAN_STOCK_K_BAR -> writeKBarRowsToCandlesticks(dataArray, dataId);
            case TAIWAN_STOCK_EVERY_5_SECONDS_INDEX, TAIWAN_VARIOUS_INDICATORS_5_SECONDS,
                    TAIWAN_STOCK_TOTAL_RETURN_INDEX -> writeIndexRowsToTicks(dataArray, dataset);
            case TAIWAN_STOCK_INDUSTRY_CHAIN -> replaceIndustryChain(parseIndustryInfos(dataArray));
            default -> 0;
        };
        return new FinMindSqlWriteResult(tableName, rawRows, cachedRows, marketRows);
    }

    private int upsertFinMindRawRows(
            String tableName,
            FinMindDataset dataset,
            String dataId,
            LocalDate startDate,
            LocalDate endDate,
            JsonNode dataArray) throws SQLException {
        ensureFinMindRawTable(tableName);
        String selectSql = "SELECT id FROM " + tableName + " WHERE row_hash = ?";
        String insertSql = "INSERT INTO " + tableName + " "
                + "(dataset, data_id, query_start_date, query_end_date, row_date, row_time, stock_id, row_hash, raw_json, imported_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        String updateSql = "UPDATE " + tableName + " SET raw_json = ?, imported_at = CURRENT_TIMESTAMP WHERE row_hash = ?";

        int written = 0;
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             PreparedStatement insert = connection.prepareStatement(insertSql);
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            for (JsonNode row : dataArray) {
                if (row == null || row.isNull()) {
                    continue;
                }
                String rawJson = row.toString();
                String hash = hash(dataset.apiName() + "|" + rawJson);
                select.setString(1, hash);
                boolean exists;
                try (ResultSet resultSet = select.executeQuery()) {
                    exists = resultSet.next();
                }
                if (exists) {
                    update.setString(1, rawJson);
                    update.setString(2, hash);
                    update.executeUpdate();
                } else {
                    insert.setString(1, dataset.apiName());
                    insert.setString(2, blankToNull(dataId));
                    setDate(insert, 3, startDate);
                    setDate(insert, 4, endDate);
                    setDate(insert, 5, readDate(row, "date", "Date"));
                    insert.setString(6, blankToNull(readText(row,
                            "time", "Time", "minute", "datetime", "DateTime",
                            "timestamp", "Timestamp", "created_at", "date", "Date")));
                    insert.setString(7, blankToNull(readText(row, "stock_id", "index_id", "data_id")));
                    insert.setString(8, hash);
                    insert.setString(9, rawJson);
                    insert.executeUpdate();
                }
                written++;
            }
        }
        return written;
    }

    private void ensureFinMindRawTable(String tableName) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    dataset VARCHAR(128) NOT NULL,
                    data_id VARCHAR(64),
                    query_start_date DATE,
                    query_end_date DATE,
                    row_date DATE,
                    row_time VARCHAR(32),
                    stock_id VARCHAR(64),
                    row_hash VARCHAR(64) NOT NULL,
                    raw_json TEXT NOT NULL,
                    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(row_hash)
                )
                """.formatted(tableName);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String finMindTableName(FinMindDataset dataset) {
        if (dataset == FinMindDataset.TAIWAN_VARIOUS_INDICATORS_5_SECONDS) {
            return "finmind_taiwan_various_indicators5seconds";
        }
        String apiName = dataset.apiName().replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toLowerCase(Locale.ROOT);
        return "finmind_" + apiName;
    }

    private int writeKBarRowsToCandlesticks(JsonNode dataArray, String fallbackSymbol) throws SQLException {
        Map<SymbolDateKey, List<Bar>> barsBySymbolDate = new LinkedHashMap<>();
        for (JsonNode row : dataArray) {
            LocalDate date = readDate(row, "date", "Date");
            String symbol = normalizeTaiwanStockSymbol(readText(row, "stock_id", "data_id"));
            if (symbol.isBlank()) {
                symbol = normalizeTaiwanStockSymbol(fallbackSymbol);
            }
            if (date == null || symbol.isBlank()) {
                continue;
            }
            LocalDateTime timestamp = parseDateTime(date, readText(row, "minute", "time", "Time"), date.atTime(9, 0));
            double open = readDouble(row, "open", "Open");
            double high = readDouble(row, "high", "max", "Max", "High");
            double low = readDouble(row, "low", "min", "Min", "Low");
            double close = readDouble(row, "close", "Close");
            long volume = Math.round(readDouble(row, "volume", "Trading_Volume", "Trading_Volume_K"));
            if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
                continue;
            }
            barsBySymbolDate.computeIfAbsent(new SymbolDateKey(symbol, date), ignored -> new ArrayList<>())
                    .add(new Bar(timestamp, open, high, low, close, Math.max(0L, volume)));
        }

        int inserted = 0;
        for (Map.Entry<SymbolDateKey, List<Bar>> entry : barsBySymbolDate.entrySet()) {
            List<Bar> oneMinuteBars = entry.getValue().stream()
                    .sorted(Comparator.comparing(Bar::getTimestamp))
                    .toList();
            inserted += replaceCandlesForDate(entry.getKey().symbol(), "M1", entry.getKey().date(), oneMinuteBars);
            inserted += replaceCandlesForDate(entry.getKey().symbol(), "M5", entry.getKey().date(),
                    BarAggregator.aggregate(oneMinuteBars, Timeframe.M5));
            inserted += replaceCandlesForDate(entry.getKey().symbol(), "M15", entry.getKey().date(),
                    BarAggregator.aggregate(oneMinuteBars, Timeframe.M15));
            inserted += replaceCandlesForDate(entry.getKey().symbol(), "M30", entry.getKey().date(),
                    BarAggregator.aggregate(oneMinuteBars, Timeframe.M30));
            inserted += replaceCandlesForDate(entry.getKey().symbol(), "H1", entry.getKey().date(),
                    BarAggregator.aggregate(oneMinuteBars, Timeframe.H1));
        }
        return inserted;
    }

    private int writeIndexRowsToTicks(JsonNode dataArray, FinMindDataset dataset) throws SQLException {
        ensureTicksTable();
        Map<SymbolDateKey, List<Tick>> ticksBySymbolDate = new LinkedHashMap<>();
        for (JsonNode row : dataArray) {
            LocalDate date = readDate(row, "date", "Date");
            if (date == null) {
                continue;
            }
            String symbol = resolveIndexSymbol(row, dataset);
            double price = readIndexPrice(row, symbol);
            if (symbol.isBlank() || price <= 0.0) {
                continue;
            }
            String timeText = readText(row,
                    "time", "Time", "minute", "datetime", "DateTime",
                    "timestamp", "Timestamp", "created_at", "date", "Date");
            LocalDateTime timestamp = parseDateTime(date, timeText, date.atTime(13, 30));
            long volume = Math.round(readDouble(row, "volume", "Volume", "Trading_Volume"));
            ticksBySymbolDate.computeIfAbsent(new SymbolDateKey(symbol, date), ignored -> new ArrayList<>())
                    .add(new Tick(symbol, timestamp, price, Math.max(0L, volume)));
        }

        int inserted = 0;
        for (Map.Entry<SymbolDateKey, List<Tick>> entry : ticksBySymbolDate.entrySet()) {
            List<Tick> dayTicks = expandDuplicateIndexTimestamps(entry.getValue());
            replaceTicksForDate(entry.getKey().symbol(), entry.getKey().date(), dayTicks);
            inserted += dayTicks.size();
        }
        return inserted;
    }

    private void ensureTicksTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS ticks (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    symbol VARCHAR(32),
                    ts VARCHAR(64),
                    price DOUBLE,
                    volume BIGINT,
                    bid DOUBLE,
                    ask DOUBLE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void replaceTicksForDate(String symbol, LocalDate date, List<Tick> ticks) throws SQLException {
        String deleteSql = """
                DELETE FROM ticks
                WHERE symbol = ?
                  AND REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') < ?
                """;
        String insertSql = """
                INSERT INTO ticks(symbol, ts, price, volume, bid, ask, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement delete = connection.prepareStatement(deleteSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            delete.setString(1, symbol);
            delete.setString(2, formatSqlTime(date.atStartOfDay()));
            delete.setString(3, formatSqlTime(date.plusDays(1).atStartOfDay()));
            delete.executeUpdate();

            for (Tick tick : ticks.stream().sorted(Comparator.comparing(Tick::getTimestamp)).toList()) {
                insert.setString(1, symbol);
                insert.setString(2, tick.getTimestamp().toString());
                insert.setDouble(3, tick.getPrice());
                insert.setLong(4, Math.max(0L, tick.getVolume()));
                insert.setDouble(5, tick.getPrice());
                insert.setDouble(6, tick.getPrice());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<Tick> findRawIndexTicksByTimeRange(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        List<Tick> ticks = new ArrayList<>();
        for (String tableName : rawIndexTableNames()) {
            if (!tableExists(tableName)) {
                continue;
            }
            String sql = """
                    SELECT row_date, row_time, stock_id, raw_json
                    FROM %s
                    WHERE row_date >= ? AND row_date <= ?
                    ORDER BY row_date ASC, row_time ASC, id ASC
                    """.formatted(tableName);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDate(1, java.sql.Date.valueOf(startTime.toLocalDate()));
                statement.setDate(2, java.sql.Date.valueOf(endTime.toLocalDate()));
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        Tick tick = parseRawIndexTick(symbol, resultSet);
                        if (tick != null
                                && !tick.getTimestamp().isBefore(startTime)
                                && tick.getTimestamp().isBefore(endTime)) {
                            ticks.add(tick);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("Failed to read raw FinMind index table {} for {}: {}", tableName, symbol, e.getMessage());
            }
            if (!ticks.isEmpty()) {
                ticks = expandDuplicateIndexTimestamps(ticks);
                break;
            }
        }
        ticks.sort(Comparator.comparing(Tick::getTimestamp));
        return ticks;
    }

    private List<Tick> findLatestRawIndexTicks(String symbol, int limit) {
        LocalDateTime end = LocalDateTime.now(TAIPEI_ZONE).plusDays(1);
        LocalDateTime start = end.minusDays(7);
        List<Tick> ticks = findRawIndexTicksByTimeRange(symbol, start, end);
        if (ticks.size() <= limit) {
            return ticks;
        }
        return ticks.subList(Math.max(0, ticks.size() - limit), ticks.size());
    }

    private LocalDateTime findLatestRawIndexDataTime(String symbol) {
        List<Tick> ticks = findLatestRawIndexTicks(symbol, 1);
        return ticks.isEmpty() ? null : ticks.get(ticks.size() - 1).getTimestamp();
    }

    private Tick parseRawIndexTick(String requestedSymbol, ResultSet resultSet) {
        try {
            String rawJson = resultSet.getString("raw_json");
            if (rawJson == null || rawJson.isBlank()) {
                return null;
            }
            JsonNode row = OBJECT_MAPPER.readTree(rawJson);
            LocalDate date = null;
            java.sql.Date rowDate = resultSet.getDate("row_date");
            if (rowDate != null) {
                date = rowDate.toLocalDate();
            }
            if (date == null) {
                date = readDate(row, "date", "Date");
            }
            if (date == null) {
                return null;
            }
            String timeText = resultSet.getString("row_time");
            if (timeText == null || timeText.isBlank()) {
                timeText = readText(row,
                        "time", "Time", "minute", "datetime", "DateTime",
                        "timestamp", "Timestamp", "created_at", "date", "Date");
            }
            String normalizedSymbol = normalizeTaiwanStockSymbol(requestedSymbol);
            double price = readIndexPrice(row, normalizedSymbol);
            if (price <= 0.0) {
                return null;
            }
            LocalDateTime timestamp = parseDateTime(date, timeText, date.atTime(13, 30));
            long volume = Math.round(readDouble(row, "volume", "Volume", "Trading_Volume", "trading_volume"));
            return new Tick(normalizedSymbol, timestamp, price, Math.max(0L, volume));
        } catch (Exception e) {
            logger.debug("Failed to parse raw FinMind index tick for {}: {}", requestedSymbol, e.getMessage());
            return null;
        }
    }

    private double readIndexPrice(JsonNode row, String requestedSymbol) {
        double price;
        if ("TPEX".equalsIgnoreCase(requestedSymbol) || "TPEx".equals(requestedSymbol)) {
            price = readDouble(row, "TPEx", "TPEX", "OTC", "otc", "櫃買指數", "櫃買");
            return price > 0.0 ? price : readDouble(row, "price", "close", "Close", "index", "value");
        }
        if ("TAIEX".equalsIgnoreCase(requestedSymbol)) {
            price = readDouble(row, "TAIEX", "taiex", "TWSE", "twse", "發行量加權股價指數", "加權指數");
            return price > 0.0 ? price : readDouble(row, "price", "close", "Close", "index", "value");
        }
        return readDouble(row, "price", "close", "Close", "index", "value");
    }

    private List<String> rawIndexTableNames() {
        return List.of(
                "finmind_taiwan_various_indicators5seconds",
                "finmind_taiwan_various_indicators5_seconds",
                "finmind_taiwan_stock_every5_seconds_index",
                "finmind_taiwan_stock_every_5_seconds_index");
    }

    private List<Tick> expandDuplicateIndexTimestamps(List<Tick> ticks) {
        if (ticks == null || ticks.size() <= 1) {
            return ticks;
        }
        Map<LocalDate, List<Tick>> byDate = ticks.stream()
                .filter(tick -> tick != null && tick.getTimestamp() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        tick -> tick.getTimestamp().toLocalDate(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<Tick> expanded = new ArrayList<>();
        boolean changed = false;
        for (Map.Entry<LocalDate, List<Tick>> entry : byDate.entrySet()) {
            List<Tick> dayTicks = entry.getValue();
            long distinctTimes = dayTicks.stream().map(Tick::getTimestamp).distinct().count();
            if (dayTicks.size() > 1 && distinctTimes <= 1) {
                changed = true;
                LocalDateTime sessionStart = entry.getKey().atTime(9, 0);
                for (int index = 0; index < dayTicks.size(); index++) {
                    Tick tick = dayTicks.get(index);
                    expanded.add(new Tick(
                            tick.getSymbol(),
                            sessionStart.plusSeconds(index * 5L),
                            tick.getPrice(),
                            tick.getVolume()));
                }
            } else {
                expanded.addAll(dayTicks);
            }
        }
        return changed ? expanded : ticks;
    }

    public LocalDateTime findLatestDataTime(String symbol) {
        if (connection == null) {
            return null;
        }
        List<String> aliases = symbolAliases(symbol);
        String sql = """
                SELECT MAX(latest_time) AS latest_time
                FROM (
                    SELECT MAX(REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ')) AS latest_time
                    FROM ticks
                    WHERE symbol IN (%s)
                    UNION ALL
                    SELECT MAX(REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ')) AS latest_time
                    FROM candlesticks
                    WHERE symbol IN (%s)
                ) latest
                """.formatted(placeholders(aliases.size()), placeholders(aliases.size()));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = bindStrings(statement, 1, aliases);
            bindStrings(statement, parameterIndex, aliases);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString("latest_time");
                    if (value != null && !value.isBlank()) {
                        LocalDateTime latest = parseTimestamp(value);
                        if (isIndexSymbol(symbol)) {
                            LocalDateTime rawLatest = findLatestRawIndexDataTime(symbol);
                            return rawLatest != null && rawLatest.isAfter(latest) ? rawLatest : latest;
                        }
                        return latest;
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read MarketDataCollector latest timestamp for {}: {}", symbol, e.getMessage());
        }
        if (isIndexSymbol(symbol)) {
            return findLatestRawIndexDataTime(symbol);
        }
        return null;
    }

    public void ensureIndustryChainTable() throws SQLException {
        if (connection == null) {
            return;
        }
        String sql = """
                CREATE TABLE IF NOT EXISTS industry_chain (
                    stock_id VARCHAR(32) PRIMARY KEY,
                    stock_name VARCHAR(128),
                    industry VARCHAR(128),
                    sub_industry VARCHAR(128),
                    chain_name VARCHAR(128),
                    source_date DATE,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            if (!columnExists("industry_chain", "stock_name")) {
                try {
                    statement.execute("ALTER TABLE industry_chain ADD COLUMN stock_name VARCHAR(128) AFTER stock_id");
                } catch (SQLException e) {
                    if (!e.getMessage().toLowerCase(Locale.ROOT).contains("duplicate")) {
                        throw e;
                    }
                }
            }
        }
        backfillIndustryStockNames();
    }

    public int replaceIndustryChain(List<IndustryInfo> industryInfos) throws SQLException {
        if (connection == null || industryInfos == null || industryInfos.isEmpty()) {
            return 0;
        }
        List<IndustryInfo> uniqueIndustryInfos = mergeDuplicateIndustryInfos(industryInfos);
        ensureIndustryChainTable();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO industry_chain
                         (stock_id, stock_name, industry, sub_industry, chain_name, source_date, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """)) {
            delete.executeUpdate("DELETE FROM industry_chain");
            int inserted = 0;
            for (IndustryInfo info : uniqueIndustryInfos) {
                if (info == null || info.stockId() == null || info.stockId().isBlank()) {
                    continue;
                }
                insert.setString(1, normalizeStockId(info.stockId()));
                insert.setString(2, blankToNull(resolveStockNameForSql(info.stockId(), info.stockName())));
                insert.setString(3, blankToNull(info.industry()));
                insert.setString(4, blankToNull(info.subIndustry()));
                insert.setString(5, blankToNull(info.chainName()));
                if (info.sourceDate() != null) {
                    insert.setDate(6, java.sql.Date.valueOf(info.sourceDate()));
                } else {
                    insert.setDate(6, null);
                }
                insert.addBatch();
                inserted++;
            }
            insert.executeBatch();
            connection.commit();
            return inserted;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void backfillIndustryStockNames() {
        String selectSql = """
                SELECT stock_id
                FROM industry_chain
                WHERE stock_name IS NULL OR stock_name = ''
                """;
        String updateSql = "UPDATE industry_chain SET stock_name = ?, updated_at = CURRENT_TIMESTAMP WHERE stock_id = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             PreparedStatement update = connection.prepareStatement(updateSql);
             ResultSet resultSet = select.executeQuery()) {
            int updated = 0;
            while (resultSet.next()) {
                String stockId = normalizeStockId(resultSet.getString("stock_id"));
                String stockName = resolveStockNameForSql(stockId, null);
                if (stockName == null || stockName.isBlank()) {
                    continue;
                }
                update.setString(1, stockName);
                update.setString(2, stockId);
                update.addBatch();
                updated++;
            }
            if (updated > 0) {
                update.executeBatch();
                logger.info("Backfilled {} industry_chain stock names", updated);
            }
        } catch (SQLException e) {
            logger.warn("Failed to backfill industry_chain stock names: {}", e.getMessage());
        }
    }

    private String resolveStockNameForSql(String stockId, String explicitName) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName.trim();
        }
        String normalized = normalizeStockId(stockId);
        String sqlName = findStockNameFromCachedStockInfo(normalized);
        if (sqlName != null && !sqlName.isBlank()) {
            return sqlName;
        }
        String knownName = StockNameResolver.resolveChineseName(normalized);
        return knownName == null || knownName.isBlank() ? null : knownName;
    }

    private String findStockNameFromCachedStockInfo(String stockId) {
        if (stockId == null || stockId.isBlank() || !tableExists("finmind_taiwan_stock_info")) {
            return null;
        }
        String sql = """
                SELECT raw_json
                FROM finmind_taiwan_stock_info
                WHERE stock_id = ? OR data_id = ?
                ORDER BY imported_at DESC, id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stockId);
            statement.setString(2, stockId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    JsonNode row = OBJECT_MAPPER.readTree(resultSet.getString("raw_json"));
                    String name = readText(row, "stock_name", "stockName", "name", "security_name");
                    return name.isBlank() ? null : name;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve stock name from cached TaiwanStockInfo for {}: {}", stockId, e.getMessage());
        }
        return null;
    }

    private List<IndustryInfo> findRawIndustryInfos() {
        List<IndustryInfo> rows = new ArrayList<>();
        for (String tableName : rawIndustryTableNames()) {
            if (!tableExists(tableName)) {
                continue;
            }
            String sql = """
                    SELECT raw_json
                    FROM %s
                    ORDER BY id ASC
                    """.formatted(tableName);
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        JsonNode row = OBJECT_MAPPER.readTree(resultSet.getString("raw_json"));
                        String stockId = readText(row, "stock_id", "stockId", "code", "security_id");
                        if (stockId.isBlank()) {
                            continue;
                        }
                        rows.add(new IndustryInfo(
                                normalizeStockId(stockId),
                                readText(row, "stock_name", "stockName", "security_name", "name"),
                                readText(row, "industry", "industry_category", "industry_name", "industry_group", "category"),
                                readText(row, "sub_industry", "subIndustry", "subindustry", "sub_category"),
                                readText(row, "chain_name", "chainName", "industry_chain", "industry_chain_name"),
                                readDate(row, "date", "source_date", "update_date")));
                    } catch (Exception ignored) {
                        // Skip malformed raw rows.
                    }
                }
            } catch (SQLException e) {
                logger.warn("Failed to read raw industry table {}: {}", tableName, e.getMessage());
            }
            if (!rows.isEmpty()) {
                break;
            }
        }
        return mergeDuplicateIndustryInfos(rows);
    }

    private List<String> rawIndustryTableNames() {
        return List.of(
                "finmind_taiwan_stock_industry_chain",
                "finmind_taiwan_stockindustrychain",
                "finmind_taiwanstockindustrychain");
    }

    private List<IndustryInfo> mergeDuplicateIndustryInfos(List<IndustryInfo> industryInfos) {
        Map<String, IndustryInfo> mergedByStockId = new LinkedHashMap<>();
        for (IndustryInfo info : industryInfos) {
            if (info == null || info.stockId() == null || info.stockId().isBlank()) {
                continue;
            }
            String stockId = normalizeStockId(info.stockId());
            IndustryInfo normalized = new IndustryInfo(
                    stockId,
                    resolveStockNameForSql(stockId, info.stockName()),
                    info.industry(),
                    info.subIndustry(),
                    info.chainName(),
                    info.sourceDate());
            IndustryInfo existing = mergedByStockId.get(stockId);
            mergedByStockId.put(stockId, existing == null ? normalized : mergeIndustryInfo(existing, normalized));
        }
        return new ArrayList<>(mergedByStockId.values());
    }

    private IndustryInfo mergeIndustryInfo(IndustryInfo left, IndustryInfo right) {
        return new IndustryInfo(
                left.stockId(),
                mergeText(left.stockName(), right.stockName()),
                mergeText(left.industry(), right.industry()),
                mergeText(left.subIndustry(), right.subIndustry()),
                mergeText(left.chainName(), right.chainName()),
                latestDate(left.sourceDate(), right.sourceDate()));
    }

    private String mergeText(String left, String right) {
        Set<String> values = new LinkedHashSet<>();
        addTextPart(values, left);
        addTextPart(values, right);
        if (values.isEmpty()) {
            return null;
        }
        String merged = String.join(" / ", values);
        return merged.length() <= 128 ? merged : merged.substring(0, 128);
    }

    private void addTextPart(Set<String> values, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split("/")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
    }

    private LocalDate latestDate(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return right.isAfter(left) ? right : left;
    }

    public Map<String, IndustryInfo> findIndustryInfoForSymbols(Collection<String> symbols) {
        Map<String, IndustryInfo> result = new HashMap<>();
        if (connection == null || symbols == null || symbols.isEmpty()) {
            return result;
        }
        try {
            ensureIndustryChainTable();
        } catch (SQLException e) {
            logger.warn("Failed to ensure industry_chain table: {}", e.getMessage());
            return result;
        }
        String sql = """
                SELECT %s
                FROM industry_chain
                WHERE stock_id = ?
                """.formatted(industrySelectColumns());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String symbol : symbols) {
                String stockId = normalizeStockId(symbol);
                if (stockId.isBlank() || result.containsKey(symbol)) {
                    continue;
                }
                statement.setString(1, stockId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        IndustryInfo info = parseIndustryInfo(resultSet);
                        result.put(symbol, info);
                        result.put(stockId, info);
                        result.put(stockId + ".TW", info);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read industry_chain: {}", e.getMessage());
        }
        return result;
    }

    public IndustryInfo findIndustryInfo(String symbol) {
        Map<String, IndustryInfo> infos = findIndustryInfoForSymbols(List.of(symbol));
        IndustryInfo info = infos.get(symbol);
        if (info != null) {
            return info;
        }
        return infos.get(normalizeStockId(symbol));
    }

    public List<String> findSymbolsByIndustry(String industry) {
        List<String> symbols = new ArrayList<>();
        if (connection == null || industry == null || industry.isBlank()) {
            return symbols;
        }
        try {
            ensureIndustryChainTable();
        } catch (SQLException e) {
            logger.warn("Failed to ensure industry_chain table: {}", e.getMessage());
            return symbols;
        }
        String value = industry.trim();
        String sql = """
                SELECT %s
                FROM industry_chain
                ORDER BY stock_id ASC
                """.formatted(industrySelectColumns());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    IndustryInfo info = parseIndustryInfo(resultSet);
                    String symbol = normalizeTaiwanStockSymbol(info.stockId());
                    if (matchesIndustryFilter(info, value) && !symbol.isBlank() && !symbols.contains(symbol)) {
                        symbols.add(symbol);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read symbols by industry {}: {}", industry, e.getMessage());
        }
        if (symbols.isEmpty()) {
            for (IndustryInfo info : findRawIndustryInfos()) {
                String symbol = normalizeTaiwanStockSymbol(info.stockId());
                if (matchesIndustryFilter(info, value) && !symbol.isBlank() && !symbols.contains(symbol)) {
                    symbols.add(symbol);
                }
            }
        }
        return symbols;
    }

    public List<IndustrySummary> findIndustrySummaries() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (connection == null) {
            return List.of();
        }
        try {
            ensureIndustryChainTable();
        } catch (SQLException e) {
            logger.warn("Failed to ensure industry_chain table: {}", e.getMessage());
            return List.of();
        }
        String sql = """
                SELECT %s
                FROM industry_chain
                ORDER BY industry, sub_industry, chain_name, stock_id
                """.formatted(industrySelectColumns());
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                IndustryInfo info = parseIndustryInfo(resultSet);
                String industry = info.industryGroup();
                counts.merge(industry, 1, Integer::sum);
            }
        } catch (SQLException e) {
            logger.warn("Failed to read industry summaries: {}", e.getMessage());
        }
        if (counts.isEmpty()) {
            for (IndustryInfo info : findRawIndustryInfos()) {
                counts.merge(info.industryGroup(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new IndustrySummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<IndustryStockInfo> findIndustryStockRows() {
        List<IndustryStockInfo> rows = new ArrayList<>();
        if (connection == null) {
            return rows;
        }
        try {
            ensureIndustryChainTable();
        } catch (SQLException e) {
            logger.warn("Failed to ensure industry_chain table: {}", e.getMessage());
            return rows;
        }
        String sql = """
                SELECT %s
                FROM industry_chain
                ORDER BY stock_id ASC
                """.formatted(industrySelectColumns());
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                IndustryInfo info = parseIndustryInfo(resultSet);
                String symbol = normalizeTaiwanStockSymbol(info.stockId());
                rows.add(new IndustryStockInfo(
                        symbol,
                        nullToEmpty(info.stockName()),
                        info.industryGroup(),
                        nullToEmpty(info.industry()),
                        nullToEmpty(info.subIndustry()),
                        nullToEmpty(info.chainName()),
                        info.sourceDate()));
            }
        } catch (SQLException e) {
            logger.warn("Failed to read industry stock rows: {}", e.getMessage());
        }
        if (rows.isEmpty()) {
            for (IndustryInfo info : findRawIndustryInfos()) {
                String symbol = normalizeTaiwanStockSymbol(info.stockId());
                rows.add(new IndustryStockInfo(
                        symbol,
                        nullToEmpty(resolveStockNameForSql(info.stockId(), info.stockName())),
                        info.industryGroup(),
                        nullToEmpty(info.industry()),
                        nullToEmpty(info.subIndustry()),
                        nullToEmpty(info.chainName()),
                        info.sourceDate()));
            }
        }
        return rows;
    }

    public Set<String> findSymbolsWithSessionData(LocalDate date) {
        Set<String> symbols = new LinkedHashSet<>();
        if (connection == null || date == null) {
            return symbols;
        }
        String start = formatSqlTime(date.atTime(9, 0));
        String end = formatSqlTime(date.atTime(13, 31));
        String tickSql = """
                SELECT DISTINCT symbol
                FROM ticks
                WHERE REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') < ?
                """;
        String candleSql = """
                SELECT DISTINCT symbol
                FROM candlesticks
                WHERE REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') >= ?
                  AND REPLACE(SUBSTRING(CAST(ts AS CHAR), 1, 19), 'T', ' ') < ?
                """;
        addSymbolsWithSessionData(symbols, tickSql, start, end);
        addSymbolsWithSessionData(symbols, candleSql, start, end);
        return symbols;
    }

    private void addSymbolsWithSessionData(Set<String> symbols, String sql, String start, String end) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start);
            statement.setString(2, end);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String symbol = normalizeTaiwanStockSymbol(resultSet.getString("symbol"));
                    if (!symbol.isBlank()) {
                        symbols.add(symbol);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to read available session symbols: {}", e.getMessage());
        }
    }

    private Tick parseTick(ResultSet resultSet) throws SQLException {
        return new Tick(
                resultSet.getString("symbol"),
                parseTimestamp(resultSet.getString("ts")),
                resultSet.getDouble("price"),
                resultSet.getLong("volume"));
    }

    private IndustryInfo parseIndustryInfo(ResultSet resultSet) throws SQLException {
        java.sql.Date sourceDate = resultSet.getDate("source_date");
        return new IndustryInfo(
                resultSet.getString("stock_id"),
                getOptionalString(resultSet, "stock_name"),
                resultSet.getString("industry"),
                resultSet.getString("sub_industry"),
                resultSet.getString("chain_name"),
                sourceDate != null ? sourceDate.toLocalDate() : null);
    }

    private String industrySelectColumns() {
        try {
            if (columnExists("industry_chain", "stock_name")) {
                return "stock_id, stock_name, industry, sub_industry, chain_name, source_date";
            }
        } catch (SQLException ignored) {
            // Use the legacy column set below.
        }
        return "stock_id, NULL AS stock_name, industry, sub_industry, chain_name, source_date";
    }

    private String getOptionalString(ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
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
        String safeValue = value.trim();
        try {
            return ZonedDateTime.parse(safeValue).withZoneSameInstant(TAIPEI_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            // Try the local SQL timestamp formats below.
        }
        try {
            String normalized = safeValue.substring(0, Math.min(19, safeValue.length())).replace('T', ' ');
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized).atStartOfDay();
            }
            if (normalized.length() == 16) {
                normalized = normalized + ":00";
            }
            return Timestamp.valueOf(normalized).toLocalDateTime();
        } catch (Exception ignored) {
            // Try compact FinMind time formats below.
        }
        try {
            if (safeValue.matches("\\d{8}")) {
                return LocalDate.parse(
                        safeValue.substring(0, 4) + "-" + safeValue.substring(4, 6) + "-" + safeValue.substring(6, 8))
                        .atStartOfDay();
            }
            if (safeValue.matches("\\d{6}")) {
                LocalDate today = LocalDate.now(TAIPEI_ZONE);
                return today.atTime(
                        Integer.parseInt(safeValue.substring(0, 2)),
                        Integer.parseInt(safeValue.substring(2, 4)),
                        Integer.parseInt(safeValue.substring(4, 6)));
            }
        } catch (Exception ignored) {
            // Fall through to a safe fallback.
        }
        logger.debug("Unrecognized timestamp format: {}", value);
        return LocalDateTime.now(TAIPEI_ZONE);
    }

    private String formatSqlTime(LocalDateTime time) {
        return Timestamp.valueOf(time).toString().substring(0, 19);
    }

    private List<String> symbolAliases(String symbol) {
        String normalized = normalizeTaiwanStockSymbol(symbol);
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (!normalized.isBlank()) {
            aliases.add(normalized);
        }
        if (symbol != null && !symbol.isBlank()) {
            aliases.add(symbol.trim());
            aliases.add(symbol.trim().toUpperCase(Locale.ROOT));
        }
        if ("TAIEX".equalsIgnoreCase(normalized)
                || "^TWII".equalsIgnoreCase(symbol)
                || "TWSE".equalsIgnoreCase(symbol)) {
            aliases.add("TAIEX");
            aliases.add("TAIEX.TW");
            aliases.add("^TWII");
            aliases.add("TWSE");
            aliases.add("發行量加權股價指數");
            aliases.add("加權指數");
            aliases.add("台股加權指數");
        }
        if ("TPEX".equalsIgnoreCase(normalized)
                || "TPEx".equals(normalized)
                || "OTC".equalsIgnoreCase(symbol)) {
            aliases.add("TPEx");
            aliases.add("TPEX");
            aliases.add("TPEx.TW");
            aliases.add("OTC");
            aliases.add("櫃買指數");
            aliases.add("櫃買");
        }
        if (aliases.isEmpty()) {
            aliases.add("");
        }
        return new ArrayList<>(aliases);
    }

    private boolean isIndexSymbol(String symbol) {
        String normalized = normalizeTaiwanStockSymbol(symbol);
        return "TAIEX".equalsIgnoreCase(normalized)
                || "TPEX".equalsIgnoreCase(normalized)
                || "TPEx".equals(normalized)
                || "^TWII".equalsIgnoreCase(symbol)
                || "OTC".equalsIgnoreCase(symbol);
    }

    private boolean tableExists(String tableName) {
        if (connection == null || tableName == null || tableName.isBlank()) {
            return false;
        }
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException ignored) {
            // Fallback below for drivers that do not expose metadata consistently.
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            statement.executeQuery();
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        for (String tableCandidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
            for (String columnCandidate : List.of(columnName, columnName.toUpperCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))) {
                try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableCandidate, columnCandidate)) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(Math.max(1, count), "?"));
    }

    private int bindStrings(PreparedStatement statement, int startIndex, List<String> values) throws SQLException {
        int index = startIndex;
        for (String value : values) {
            statement.setString(index++, value);
        }
        return index;
    }

    private String normalizeStockId(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.trim().toUpperCase();
        int dot = normalized.indexOf('.');
        return dot >= 0 ? normalized.substring(0, dot) : normalized;
    }

    private String normalizeTaiwanStockSymbol(String symbol) {
        String stockId = normalizeStockId(symbol);
        if (stockId.isBlank()) {
            return "";
        }
        if ("TAIEX".equalsIgnoreCase(stockId) || "^TWII".equalsIgnoreCase(stockId)) {
            return "TAIEX";
        }
        if ("TPEX".equalsIgnoreCase(stockId) || "OTC".equalsIgnoreCase(stockId)) {
            return "TPEx";
        }
        return stockId.matches("\\d{4}[A-Z]?") ? stockId + ".TW" : stockId;
    }

    private boolean matchesIndustryFilter(IndustryInfo info, String value) {
        if (info == null || value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.trim();
        return equalsOrContains(info.industryGroup(), normalizedValue)
                || equalsOrContains(info.industry(), normalizedValue)
                || equalsOrContains(info.subIndustry(), normalizedValue)
                || equalsOrContains(info.chainName(), normalizedValue);
    }

    private boolean equalsOrContains(String source, String value) {
        if (source == null || value == null || value.isBlank()) {
            return false;
        }
        String sourceText = source.trim();
        String valueText = value.trim();
        return sourceText.equals(valueText) || sourceText.contains(valueText) || valueText.contains(sourceText);
    }

    public static String classifyIndustryGroup(String industry, String subIndustry, String chainName) {
        String text = String.join(" ",
                nullToEmptyStatic(industry),
                nullToEmptyStatic(subIndustry),
                nullToEmptyStatic(chainName)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "半導體", "晶圓", "ic", "封測", "矽", "記憶體", "二極體")) {
            return "半導體";
        }
        if (containsAny(text, "ai", "伺服器", "server", "電腦", "筆電", "主機板", "板卡", "ipc", "雲端")) {
            return "AI/伺服器/電腦週邊";
        }
        if (containsAny(text, "電子零組件", "pcb", "印刷電路", "被動元件", "連接器", "電源", "散熱", "機殼")) {
            return "電子零組件";
        }
        if (containsAny(text, "通訊", "通信", "網路", "電信", "光通訊")) {
            return "通信網路";
        }
        if (containsAny(text, "光電", "面板", "led", "太陽能")) {
            return "光電";
        }
        if (containsAny(text, "金融", "銀行", "保險", "證券")) {
            return "金融";
        }
        if (containsAny(text, "航運", "海運", "空運", "貨櫃", "航空")) {
            return "航運";
        }
        if (containsAny(text, "生技", "醫療", "製藥", "藥", "保健")) {
            return "生技醫療";
        }
        if (containsAny(text, "電機", "機械", "自動化", "工具機")) {
            return "電機機械";
        }
        if (containsAny(text, "汽車", "車用", "電動車")) {
            return "汽車";
        }
        if (containsAny(text, "建材", "營造", "水泥", "玻璃", "陶瓷")) {
            return "建材營造";
        }
        if (containsAny(text, "鋼鐵")) {
            return "鋼鐵";
        }
        if (containsAny(text, "塑膠", "化工", "石化", "橡膠")) {
            return "塑化";
        }
        if (containsAny(text, "食品")) {
            return "食品";
        }
        if (containsAny(text, "觀光", "餐旅", "飯店")) {
            return "觀光餐旅";
        }
        if (containsAny(text, "紡織")) {
            return "紡織";
        }
        if (containsAny(text, "百貨", "貿易", "零售")) {
            return "貿易百貨";
        }
        if (containsAny(text, "文創", "遊戲", "資訊服務", "軟體", "數位")) {
            return "文化創意/資訊服務";
        }
        return "其他";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolveIndexSymbol(JsonNode row, FinMindDataset dataset) {
        String raw = readText(row, "stock_id", "index_id", "data_id", "kind");
        String normalized = raw != null ? raw.trim() : "";
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("tpex") || lower.contains("otc") || normalized.contains("櫃")) {
            return "TPEx";
        }
        if (lower.contains("taiex") || lower.contains("twii") || normalized.contains("加權")) {
            return "TAIEX";
        }
        if (dataset == FinMindDataset.TAIWAN_VARIOUS_INDICATORS_5_SECONDS && row.has("TAIEX")) {
            return "TAIEX";
        }
        return normalizeTaiwanStockSymbol(normalized);
    }

    private List<IndustryInfo> parseIndustryInfos(JsonNode dataArray) {
        List<IndustryInfo> rows = new ArrayList<>();
        if (dataArray == null || !dataArray.isArray()) {
            return rows;
        }
        for (JsonNode row : dataArray) {
            String stockId = readText(row, "stock_id", "stockId", "code", "security_id");
            if (stockId.isBlank()) {
                continue;
            }
            rows.add(new IndustryInfo(
                    stockId,
                    readText(row, "stock_name", "stockName", "name", "security_name"),
                    readText(row, "industry", "industry_category", "category"),
                    readText(row, "sub_industry", "subIndustry", "sub_category"),
                    readText(row, "chain_name", "chainName", "industry_chain"),
                    readDate(row, "date", "source_date")));
        }
        return rows;
    }

    private String readText(JsonNode row, String... keys) {
        if (row == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value != null && !value.isNull()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private double readDouble(JsonNode row, String... keys) {
        if (row == null || keys == null) {
            return 0.0;
        }
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            try {
                return Double.parseDouble(value.asText().trim().replace(",", ""));
            } catch (Exception ignored) {
                // Try the next candidate key.
            }
        }
        return 0.0;
    }

    private LocalDate readDate(JsonNode row, String... keys) {
        String value = readText(row, keys);
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(LocalDate date, String timeText, LocalDateTime fallback) {
        if (date == null) {
            return fallback;
        }
        if (timeText == null || timeText.isBlank()) {
            return fallback != null ? fallback : date.atStartOfDay();
        }
        String safeTime = timeText.trim();
        try {
            if (safeTime.contains("T")
                    || safeTime.matches("\\d{4}-\\d{2}-\\d{2}.*")
                    || safeTime.matches("\\d{4}/\\d{2}/\\d{2}.*")) {
                safeTime = safeTime.replace('/', '-');
                return parseTimestamp(safeTime);
            }
            if (safeTime.matches("\\d{8}\\s+\\d{2}:\\d{2}(:\\d{2})?.*")) {
                return parseTimestamp(safeTime.substring(0, 4) + "-" + safeTime.substring(4, 6)
                        + "-" + safeTime.substring(6));
            }
            if (safeTime.length() == 5) {
                return Timestamp.valueOf(date + " " + safeTime + ":00").toLocalDateTime();
            }
            if (safeTime.length() >= 8) {
                return Timestamp.valueOf(date + " " + safeTime.substring(0, 8)).toLocalDateTime();
            }
        } catch (Exception ignored) {
            return fallback != null ? fallback : date.atStartOfDay();
        }
        return fallback != null ? fallback : date.atStartOfDay();
    }

    private void setDate(PreparedStatement statement, int index, LocalDate date) throws SQLException {
        if (date == null) {
            statement.setDate(index, null);
        } else {
            statement.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return nullToEmptyStatic(value);
    }

    private static String nullToEmptyStatic(String value) {
        return value == null ? "" : value.trim();
    }

    public record FinMindSqlWriteResult(
            String tableName,
            int rawRows,
            int cachedRows,
            int marketRows) {
    }

    public record IndustrySummary(String industry, int symbolCount) {
    }

    public record IndustryStockInfo(
            String symbol,
            String stockName,
            String industryGroup,
            String industry,
            String subIndustry,
            String chainName,
            LocalDate sourceDate) {
    }

    private record SymbolDateKey(String symbol, LocalDate date) {
    }

    public record IndustryInfo(
            String stockId,
            String stockName,
            String industry,
            String subIndustry,
            String chainName,
            LocalDate sourceDate) {

        public String displayIndustry() {
            return industryGroup();
        }

        public String industryGroup() {
            return classifyIndustryGroup(industry, subIndustry, chainName);
        }

        public String rawIndustryLabel() {
            if (subIndustry != null && !subIndustry.isBlank()) {
                return subIndustry;
            }
            if (industry != null && !industry.isBlank()) {
                return industry;
            }
            if (chainName != null && !chainName.isBlank()) {
                return chainName;
            }
            return "未分類";
        }
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
