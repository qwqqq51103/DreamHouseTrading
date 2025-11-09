package com.dreamhouse.trading.core.cache;

import com.dreamhouse.trading.core.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 市場數據緩存層
 * 使用H2數據庫進行本地緩存，提高性能並支持離線查看
 */
public class MarketDataCache {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCache.class);
    private static final String DB_URL = "jdbc:h2:./data/marketdata;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;

    public MarketDataCache() {
        initDatabase();
    }

    /**
     * 初始化數據庫
     */
    private void initDatabase() {
        try {
            // 加載H2驅動
            Class.forName("org.h2.Driver");

            // 連接數據庫
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // 創建表結構
            createTables();

            logger.info("Market data cache initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize market data cache", e);
        }
    }

    /**
     * 創建表結構
     */
    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // K線數據表
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS bars (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  symbol VARCHAR(20) NOT NULL," +
            "  timestamp TIMESTAMP NOT NULL," +
            "  open DOUBLE NOT NULL," +
            "  high DOUBLE NOT NULL," +
            "  low DOUBLE NOT NULL," +
            "  close DOUBLE NOT NULL," +
            "  volume BIGINT NOT NULL," +
            "  timeframe VARCHAR(10) NOT NULL," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  UNIQUE(symbol, timestamp, timeframe)" +
            ")"
        );

        // 商品信息表
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS symbols (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  symbol VARCHAR(20) UNIQUE NOT NULL," +
            "  name VARCHAR(200)," +
            "  exchange VARCHAR(50)," +
            "  type VARCHAR(50)," +
            "  currency VARCHAR(10)," +
            "  last_updated TIMESTAMP," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );

        // 觀察清單表
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS watchlist (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  symbol VARCHAR(20) UNIQUE NOT NULL," +
            "  added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  sort_order INT DEFAULT 0" +
            ")"
        );

        // 創建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_bars_symbol ON bars(symbol)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_bars_timestamp ON bars(timestamp)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_bars_symbol_time ON bars(symbol, timestamp)");

        stmt.close();
    }

    /**
     * 保存K線數據
     */
    public void saveBars(String symbol, List<Bar> bars, String timeframe) {
        if (bars == null || bars.isEmpty()) {
            return;
        }

        String sql = "MERGE INTO bars (symbol, timestamp, open, high, low, close, volume, timeframe) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            for (Bar bar : bars) {
                pstmt.setString(1, symbol);
                pstmt.setTimestamp(2, Timestamp.valueOf(bar.getTimestamp()));
                pstmt.setDouble(3, bar.getOpen());
                pstmt.setDouble(4, bar.getHigh());
                pstmt.setDouble(5, bar.getLow());
                pstmt.setDouble(6, bar.getClose());
                pstmt.setLong(7, bar.getVolume());
                pstmt.setString(8, timeframe);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);

            logger.debug("Saved {} bars for {} ({})", bars.size(), symbol, timeframe);

        } catch (SQLException e) {
            logger.error("Failed to save bars", e);
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                logger.error("Failed to rollback", ex);
            }
        }
    }

    /**
     * 獲取K線數據
     */
    public List<Bar> getBars(String symbol, String timeframe, LocalDateTime from, LocalDateTime to) {
        List<Bar> bars = new ArrayList<>();

        String sql = "SELECT timestamp, open, high, low, close, volume " +
                    "FROM bars " +
                    "WHERE symbol = ? AND timeframe = ? " +
                    "AND timestamp >= ? AND timestamp <= ? " +
                    "ORDER BY timestamp ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setString(2, timeframe);
            pstmt.setTimestamp(3, Timestamp.valueOf(from));
            pstmt.setTimestamp(4, Timestamp.valueOf(to));

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Bar bar = new Bar(
                    rs.getTimestamp("timestamp").toLocalDateTime(),
                    rs.getDouble("open"),
                    rs.getDouble("high"),
                    rs.getDouble("low"),
                    rs.getDouble("close"),
                    rs.getLong("volume")
                );
                bars.add(bar);
            }

            rs.close();
            logger.debug("Retrieved {} bars for {} ({}) from cache", bars.size(), symbol, timeframe);

        } catch (SQLException e) {
            logger.error("Failed to retrieve bars", e);
        }

        return bars;
    }

    /**
     * 獲取最新的K線數據
     */
    public List<Bar> getRecentBars(String symbol, String timeframe, int limit) {
        List<Bar> bars = new ArrayList<>();

        String sql = "SELECT timestamp, open, high, low, close, volume " +
                    "FROM bars " +
                    "WHERE symbol = ? AND timeframe = ? " +
                    "ORDER BY timestamp DESC " +
                    "LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setString(2, timeframe);
            pstmt.setInt(3, limit);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Bar bar = new Bar(
                    rs.getTimestamp("timestamp").toLocalDateTime(),
                    rs.getDouble("open"),
                    rs.getDouble("high"),
                    rs.getDouble("low"),
                    rs.getDouble("close"),
                    rs.getLong("volume")
                );
                bars.add(bar);
            }

            rs.close();

            // 反轉順序（最舊到最新）
            List<Bar> reversed = new ArrayList<>();
            for (int i = bars.size() - 1; i >= 0; i--) {
                reversed.add(bars.get(i));
            }

            logger.debug("Retrieved {} recent bars for {} ({})", reversed.size(), symbol, timeframe);
            return reversed;

        } catch (SQLException e) {
            logger.error("Failed to retrieve recent bars", e);
        }

        return bars;
    }

    /**
     * 添加商品到觀察清單
     */
    public boolean addToWatchlist(String symbol) {
        String sql = "INSERT INTO watchlist (symbol, sort_order) " +
                    "SELECT ?, COALESCE(MAX(sort_order), 0) + 1 FROM watchlist";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.toUpperCase());
            pstmt.executeUpdate();
            logger.info("Added {} to watchlist", symbol);
            return true;

        } catch (SQLException e) {
            if (e.getErrorCode() == 23505) { // Duplicate key
                logger.warn("Symbol {} already in watchlist", symbol);
            } else {
                logger.error("Failed to add to watchlist", e);
            }
            return false;
        }
    }

    /**
     * 從觀察清單中移除商品
     */
    public boolean removeFromWatchlist(String symbol) {
        String sql = "DELETE FROM watchlist WHERE symbol = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.toUpperCase());
            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                logger.info("Removed {} from watchlist", symbol);
                return true;
            }
            return false;

        } catch (SQLException e) {
            logger.error("Failed to remove from watchlist", e);
            return false;
        }
    }

    /**
     * 獲取觀察清單
     */
    public List<String> getWatchlist() {
        List<String> symbols = new ArrayList<>();

        String sql = "SELECT symbol FROM watchlist ORDER BY sort_order ASC, added_at ASC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                symbols.add(rs.getString("symbol"));
            }

            logger.debug("Retrieved {} symbols from watchlist", symbols.size());

        } catch (SQLException e) {
            logger.error("Failed to retrieve watchlist", e);
        }

        return symbols;
    }

    /**
     * 檢查商品是否在觀察清單中
     */
    public boolean isInWatchlist(String symbol) {
        String sql = "SELECT COUNT(*) FROM watchlist WHERE symbol = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, symbol.toUpperCase());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            logger.error("Failed to check watchlist", e);
        }

        return false;
    }

    /**
     * 清理過期數據
     */
    public void cleanupOldData(int daysToKeep) {
        String sql = "DELETE FROM bars WHERE timestamp < DATEADD('DAY', ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, -daysToKeep);
            int deleted = pstmt.executeUpdate();
            logger.info("Cleaned up {} old bars (older than {} days)", deleted, daysToKeep);

        } catch (SQLException e) {
            logger.error("Failed to cleanup old data", e);
        }
    }

    /**
     * 關閉數據庫連接
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Market data cache closed");
            } catch (SQLException e) {
                logger.error("Failed to close market data cache", e);
            }
        }
    }

    /**
     * 獲取緩存統計信息
     */
    public CacheStats getStats() {
        CacheStats stats = new CacheStats();

        try (Statement stmt = connection.createStatement()) {
            // 統計K線數據
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) as count, " +
                "MIN(timestamp) as earliest, " +
                "MAX(timestamp) as latest " +
                "FROM bars"
            );

            if (rs.next()) {
                stats.totalBars = rs.getLong("count");
                Timestamp earliest = rs.getTimestamp("earliest");
                Timestamp latest = rs.getTimestamp("latest");
                stats.earliestData = earliest != null ? earliest.toLocalDateTime() : null;
                stats.latestData = latest != null ? latest.toLocalDateTime() : null;
            }
            rs.close();

            // 統計商品數量
            rs = stmt.executeQuery("SELECT COUNT(DISTINCT symbol) FROM bars");
            if (rs.next()) {
                stats.symbolCount = rs.getInt(1);
            }
            rs.close();

            // 統計觀察清單
            rs = stmt.executeQuery("SELECT COUNT(*) FROM watchlist");
            if (rs.next()) {
                stats.watchlistSize = rs.getInt(1);
            }
            rs.close();

        } catch (SQLException e) {
            logger.error("Failed to get cache stats", e);
        }

        return stats;
    }

    /**
     * 緩存統計信息
     */
    public static class CacheStats {
        public long totalBars;
        public int symbolCount;
        public int watchlistSize;
        public LocalDateTime earliestData;
        public LocalDateTime latestData;

        @Override
        public String toString() {
            return String.format(
                "CacheStats{bars=%d, symbols=%d, watchlist=%d, earliest=%s, latest=%s}",
                totalBars, symbolCount, watchlistSize, earliestData, latestData
            );
        }
    }
}
