package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Tick;

import java.util.List;

/**
 * 測試資料庫數據載入功能
 * 用於診斷為什麼資料庫數據沒有自動導入
 */
public class TestDatabaseLoad {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("測試資料庫數據載入");
        System.out.println("========================================");
        System.out.println();

        MarketDataLoader loader = new MarketDataLoader();

        try {
            // 步驟 1：初始化
            System.out.println("[步驟 1] 初始化 MarketDataLoader...");
            loader.initialize();
            System.out.println("✓ 初始化成功");
            System.out.println();

            // 步驟 2：測試載入 6770.TW
            String testSymbol = "6770.TW";
            System.out.println("[步驟 2] 載入 " + testSymbol + " 今日開盤至今的數據...");
            System.out.println();

            List<Tick> ticks = loader.loadTodayMarketOpenToNow(testSymbol);

            System.out.println("========================================");
            System.out.println("查詢結果");
            System.out.println("========================================");
            System.out.println("符號: " + testSymbol);
            System.out.println("數據筆數: " + ticks.size());
            System.out.println();

            if (ticks.isEmpty()) {
                System.out.println("⚠ 警告：未找到任何數據！");
                System.out.println();
                System.out.println("可能原因：");
                System.out.println("1. 資料庫中沒有 " + testSymbol + " 的數據");
                System.out.println("2. 資料庫中沒有今日的數據");
                System.out.println("3. 時間範圍查詢條件有問題");
                System.out.println();
                System.out.println("請檢查：");
                System.out.println("- MarketDataCollector 是否正在運行");
                System.out.println("- 資料庫表 ticks 是否有 " + testSymbol + " 的記錄");
                System.out.println("- 使用 SQL 手動查詢確認：");
                System.out.println("  SELECT * FROM ticks WHERE symbol='6770.TW' AND DATE(ts) = CURDATE();");
            } else {
                System.out.println("✓ 成功載入 " + ticks.size() + " 筆數據");
                System.out.println();
                System.out.println("前 10 筆數據：");
                System.out.println("----------------------------------------");

                int displayCount = Math.min(10, ticks.size());
                for (int i = 0; i < displayCount; i++) {
                    Tick tick = ticks.get(i);
                    System.out.printf("%d. 時間: %s | 價格: %.2f | 成交量: %d%n",
                            i + 1,
                            tick.getTimestamp(),
                            tick.getPrice(),
                            tick.getVolume());
                }

                if (ticks.size() > 10) {
                    System.out.println("...");
                    System.out.println("(還有 " + (ticks.size() - 10) + " 筆數據)");
                }
            }

            System.out.println();
            System.out.println("========================================");
            System.out.println("統計資訊");
            System.out.println("========================================");

            // 步驟 3：查詢統計資訊
            int totalCount = loader.getTickCount(testSymbol);
            String timeRange = loader.getDataTimeRange(testSymbol);

            System.out.println("資料庫總筆數: " + totalCount);
            System.out.println("數據時間範圍: " + timeRange);
            System.out.println();

            // 步驟 4：列出所有股票
            System.out.println("[步驟 3] 資料庫中的所有股票代號：");
            List<String> allSymbols = loader.getAllSymbols();
            for (String symbol : allSymbols) {
                int count = loader.getTickCount(symbol);
                System.out.println("  - " + symbol + " (" + count + " 筆)");
            }

        } catch (Exception e) {
            System.err.println();
            System.err.println("✗ 錯誤：" + e.getMessage());
            System.err.println();
            System.err.println("完整錯誤訊息：");
            e.printStackTrace();
            System.err.println();
            System.err.println("請檢查：");
            System.err.println("1. MySQL 服務是否啟動");
            System.err.println("2. 資料庫 market_data 是否存在");
            System.err.println("3. 連接資訊是否正確（localhost:3306, root, 無密碼）");
        } finally {
            loader.close();
        }

        System.out.println();
        System.out.println("測試完成");
    }
}
