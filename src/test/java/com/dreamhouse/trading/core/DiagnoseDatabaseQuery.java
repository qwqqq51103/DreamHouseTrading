package com.dreamhouse.trading.core;

import java.sql.*;

/**
 * 診斷資料庫查詢問題
 */
public class DiagnoseDatabaseQuery {

    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/market_data?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=UTF-8";
        String user = "root";
        String password = "";

        System.out.println("========================================");
        System.out.println("資料庫查詢診斷工具");
        System.out.println("========================================");
        System.out.println();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✓ 資料庫連接成功");
            System.out.println();

            // 1. 檢查 ticks 表結構
            System.out.println("【步驟 1】檢查 ticks 表結構");
            System.out.println("----------------------------------------");
            checkTableStructure(conn);
            System.out.println();

            // 2. 查看 3706.TW 的原始數據（前 5 筆）
            System.out.println("【步驟 2】查看 3706.TW 的原始數據（前 5 筆）");
            System.out.println("----------------------------------------");
            showRawData(conn, "3706.TW", 5);
            System.out.println();

            // 3. 測試不同的查詢條件
            System.out.println("【步驟 3】測試不同的查詢條件");
            System.out.println("----------------------------------------");

            // 測試 1：使用字符串比較（原始方法）
            System.out.println("測試 1：使用字符串比較（原始方法）");
            String startTime1 = "2025-11-25 09:00:00";
            String endTime1 = "2025-11-25 13:30:00";
            testQuery1(conn, "3706.TW", startTime1, endTime1);
            System.out.println();

            // 測試 2：使用 DATE() 函數
            System.out.println("測試 2：使用 DATE() 和 TIME() 函數");
            testQuery2(conn, "3706.TW");
            System.out.println();

            // 測試 3：使用 CAST 轉換
            System.out.println("測試 3：使用 CAST 轉換時間戳");
            testQuery3(conn, "3706.TW", startTime1, endTime1);
            System.out.println();

            // 測試 4：查看 ts 欄位的實際格式
            System.out.println("測試 4：查看 ts 欄位的各種格式");
            testQuery4(conn, "3706.TW");
            System.out.println();

            System.out.println("========================================");
            System.out.println("診斷完成");
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("✗ 發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void checkTableStructure(Connection conn) throws SQLException {
        String sql = "DESCRIBE ticks";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.printf("%-15s %-20s %-10s %-10s %-10s %-10s%n",
                "欄位", "類型", "Null", "Key", "Default", "Extra");
            System.out.println("--------------------------------------------------------------------------------");

            while (rs.next()) {
                System.out.printf("%-15s %-20s %-10s %-10s %-10s %-10s%n",
                    rs.getString("Field"),
                    rs.getString("Type"),
                    rs.getString("Null"),
                    rs.getString("Key"),
                    rs.getString("Default"),
                    rs.getString("Extra"));
            }
        }
    }

    private static void showRawData(Connection conn, String symbol, int limit) throws SQLException {
        String sql = "SELECT id, symbol, ts, price, volume FROM ticks WHERE symbol = ? ORDER BY ts ASC LIMIT ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.printf("%d. ID=%d, Symbol=%s, TS=%s, Price=%.2f, Volume=%d%n",
                        count,
                        rs.getInt("id"),
                        rs.getString("symbol"),
                        rs.getString("ts"),
                        rs.getDouble("price"),
                        rs.getLong("volume"));
                }

                if (count == 0) {
                    System.out.println("⚠ 資料庫中沒有 " + symbol + " 的數據");
                }
            }
        }
    }

    // 測試 1：原始查詢方法（字符串比較）
    private static void testQuery1(Connection conn, String symbol, String startTime, String endTime) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM ticks WHERE symbol = ? AND ts >= ? AND ts < ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            stmt.setString(2, startTime);
            stmt.setString(3, endTime);

            System.out.println("SQL: " + sql);
            System.out.println("參數: symbol=" + symbol + ", startTime=" + startTime + ", endTime=" + endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println("結果: " + count + " 筆");
                }
            }
        }
    }

    // 測試 2：使用 DATE() 和 CURDATE()
    private static void testQuery2(Connection conn, String symbol) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM ticks WHERE symbol = ? AND DATE(ts) = CURDATE()";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);

            System.out.println("SQL: " + sql);
            System.out.println("參數: symbol=" + symbol);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println("結果: " + count + " 筆（今日所有數據）");
                }
            }
        }
    }

    // 測試 3：使用 CAST 轉換
    private static void testQuery3(Connection conn, String symbol, String startTime, String endTime) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM ticks " +
                     "WHERE symbol = ? " +
                     "AND CAST(ts AS DATETIME) >= CAST(? AS DATETIME) " +
                     "AND CAST(ts AS DATETIME) < CAST(? AS DATETIME)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            stmt.setString(2, startTime);
            stmt.setString(3, endTime);

            System.out.println("SQL: " + sql);
            System.out.println("參數: symbol=" + symbol + ", startTime=" + startTime + ", endTime=" + endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    System.out.println("結果: " + count + " 筆");
                }
            }
        }
    }

    // 測試 4：查看 ts 的各種格式
    private static void testQuery4(Connection conn, String symbol) throws SQLException {
        String sql = "SELECT " +
                     "ts, " +
                     "CAST(ts AS CHAR) as ts_char, " +
                     "DATE(ts) as ts_date, " +
                     "TIME(ts) as ts_time, " +
                     "UNIX_TIMESTAMP(ts) as ts_unix " +
                     "FROM ticks WHERE symbol = ? LIMIT 3";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("記錄 " + count + ":");
                    System.out.println("  原始 ts: " + rs.getString("ts"));
                    System.out.println("  CAST(ts AS CHAR): " + rs.getString("ts_char"));
                    System.out.println("  DATE(ts): " + rs.getString("ts_date"));
                    System.out.println("  TIME(ts): " + rs.getString("ts_time"));
                    System.out.println("  UNIX_TIMESTAMP(ts): " + rs.getLong("ts_unix"));
                    System.out.println();
                }
            }
        }
    }
}
