package com.dreamhouse.trading.core.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 交易日誌匯出器
 * 支援匯出為 CSV 格式
 */
public class LogExporter {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FILENAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 匯出交易記錄到 CSV 文件
     *
     * @param trades 交易記錄列表
     * @param outputPath 輸出路徑（如果為 null，使用預設路徑）
     * @return 實際輸出的文件路徑
     * @throws IOException 如果寫入失敗
     */
    public static String exportToCSV(List<TradeRecord> trades, String outputPath) throws IOException {
        if (trades == null || trades.isEmpty()) {
            throw new IllegalArgumentException("交易記錄列表不能為空");
        }

        // 如果未指定輸出路徑，使用預設路徑
        if (outputPath == null || outputPath.isEmpty()) {
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            outputPath = String.format("trades_%s.csv", timestamp);
        }

        // 確保目錄存在
        Path filePath = Paths.get(outputPath);
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 寫入 CSV
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // 寫入標題列
            writeHeader(writer);

            // 寫入資料列
            for (TradeRecord trade : trades) {
                writeTradeRecord(writer, trade);
            }
        }

        return outputPath;
    }

    /**
     * 寫入 CSV 標題列
     */
    private static void writeHeader(BufferedWriter writer) throws IOException {
        String[] headers = {
                "交易ID",
                "商品代碼",
                "交易模式",
                "策略名稱",
                "進場時間",
                "進場價格",
                "進場K線索引",
                "數量",
                "進場手續費",
                "出場時間",
                "出場價格",
                "出場K線索引",
                "出場手續費",
                "出場原因",
                "停損價格",
                "停利價格",
                "移動停損",
                "毛利",
                "淨利",
                "報酬率(%)",
                "風險報酬比",
                "MAE",
                "MFE",
                "持倉K線數",
                "持倉分鐘",
                "持倉天數",
                "備註"
        };

        writer.write(String.join(",", headers));
        writer.newLine();
    }

    /**
     * 寫入單筆交易記錄
     */
    private static void writeTradeRecord(BufferedWriter writer, TradeRecord trade) throws IOException {
        String[] values = {
                escapeCSV(trade.getTradeId()),
                escapeCSV(trade.getSymbol()),
                escapeCSV(trade.getTradeMode().getDisplayName()),
                escapeCSV(trade.getStrategyName()),
                formatDateTime(trade.getEntryTime()),
                String.format("%.2f", trade.getEntryPrice()),
                String.valueOf(trade.getEntryBarIndex()),
                String.valueOf(trade.getQuantity()),
                String.format("%.4f", trade.getEntryCommission()),
                formatDateTime(trade.getExitTime()),
                String.format("%.2f", trade.getExitPrice()),
                String.valueOf(trade.getExitBarIndex()),
                String.format("%.4f", trade.getExitCommission()),
                escapeCSV(trade.getExitReason().getDisplayName()),
                formatPrice(trade.getStopLoss()),
                formatPrice(trade.getTakeProfit()),
                formatPrice(trade.getTrailingStop()),
                String.format("%.2f", trade.getGrossProfit()),
                String.format("%.2f", trade.getNetProfit()),
                String.format("%.2f", trade.getReturnPercent() * 100),
                String.format("%.2f", trade.getRiskRewardRatio()),
                String.format("%.2f", trade.getMae()),
                String.format("%.2f", trade.getMfe()),
                String.valueOf(trade.getHoldingBars()),
                String.valueOf(trade.getHoldingMinutes()),
                String.valueOf(trade.getHoldingDays()),
                escapeCSV(trade.getNotes())
        };

        writer.write(String.join(",", values));
        writer.newLine();
    }

    /**
     * 格式化日期時間
     */
    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATETIME_FORMATTER);
    }

    /**
     * 格式化價格（處理 null）
     */
    private static String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        return String.format("%.2f", price);
    }

    /**
     * CSV 字串跳脫處理
     * 處理包含逗號、引號、換行的欄位
     */
    private static String escapeCSV(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        // 如果包含逗號、引號或換行，需要用引號包圍
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // 將引號替換為兩個引號
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }

    /**
     * 產生交易統計摘要（可選功能）
     *
     * @param trades 交易記錄列表
     * @return 統計摘要字串
     */
    public static String generateSummary(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) {
            return "無交易記錄";
        }

        int totalTrades = trades.size();
        long profitableTrades = trades.stream().filter(TradeRecord::isProfitable).count();
        long losingTrades = totalTrades - profitableTrades;
        double winRate = (double) profitableTrades / totalTrades * 100;

        double totalProfit = trades.stream()
                .filter(TradeRecord::isProfitable)
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();

        double totalLoss = trades.stream()
                .filter(t -> !t.isProfitable())
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();

        double netProfit = trades.stream()
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();

        double avgProfit = profitableTrades > 0 ? totalProfit / profitableTrades : 0;
        double avgLoss = losingTrades > 0 ? totalLoss / losingTrades : 0;

        double profitFactor = (totalLoss != 0) ? Math.abs(totalProfit / totalLoss) : 0;

        StringBuilder summary = new StringBuilder();
        summary.append("========== 交易統計摘要 ==========\n");
        summary.append(String.format("總交易數：%d\n", totalTrades));
        summary.append(String.format("獲利交易：%d (%.1f%%)\n", profitableTrades, winRate));
        summary.append(String.format("虧損交易：%d (%.1f%%)\n", losingTrades, 100 - winRate));
        summary.append(String.format("總獲利：%.2f\n", totalProfit));
        summary.append(String.format("總虧損：%.2f\n", totalLoss));
        summary.append(String.format("淨利潤：%.2f\n", netProfit));
        summary.append(String.format("平均獲利：%.2f\n", avgProfit));
        summary.append(String.format("平均虧損：%.2f\n", avgLoss));
        summary.append(String.format("獲利因子：%.2f\n", profitFactor));
        summary.append("==================================\n");

        return summary.toString();
    }

    /**
     * 匯出交易記錄並產生統計摘要
     *
     * @param trades 交易記錄列表
     * @param outputPath 輸出路徑
     * @return 實際輸出的文件路徑
     * @throws IOException 如果寫入失敗
     */
    public static String exportWithSummary(List<TradeRecord> trades, String outputPath) throws IOException {
        // 先匯出 CSV
        String csvPath = exportToCSV(trades, outputPath);

        // 產生統計摘要
        String summary = generateSummary(trades);

        // 將摘要寫入另一個文件
        String summaryPath = csvPath.replace(".csv", "_summary.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryPath))) {
            writer.write(summary);
        }

        System.out.println(summary);
        System.out.println("CSV 已匯出至：" + csvPath);
        System.out.println("統計摘要已匯出至：" + summaryPath);

        return csvPath;
    }
}
