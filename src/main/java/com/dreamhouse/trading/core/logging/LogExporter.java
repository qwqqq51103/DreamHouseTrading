package com.dreamhouse.trading.core.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports semantic trade records to UTF-8 BOM CSV.
 */
public class LogExporter {

    private static final char UTF8_BOM = '\ufeff';
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILENAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static String exportToCSV(List<TradeRecord> trades, String outputPath) throws IOException {
        if (trades == null || trades.isEmpty()) {
            throw new IllegalArgumentException("Trade records must not be empty");
        }
        if (outputPath == null || outputPath.isEmpty()) {
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            outputPath = String.format("trades_%s.csv", timestamp);
        }

        Path filePath = Paths.get(outputPath);
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(UTF8_BOM);
            writeHeader(writer);
            for (TradeRecord trade : trades) {
                writeTradeRecord(writer, trade);
            }
        }

        return outputPath;
    }

    private static void writeHeader(BufferedWriter writer) throws IOException {
        String[] headers = {
                "trade_id",
                "symbol",
                "trade_mode",
                "strategy_name",
                "entry_time",
                "entry_price",
                "entry_bar_index",
                "quantity",
                "entry_commission_rate",
                "exit_time",
                "exit_price",
                "exit_bar_index",
                "exit_commission_rate",
                "exit_reason",
                "stop_loss",
                "take_profit",
                "trailing_stop",
                "gross_profit",
                "net_profit",
                "return_percent",
                "risk_reward_ratio",
                "mae",
                "mfe",
                "holding_bars",
                "holding_minutes",
                "holding_days",
                "notes",
                "decision_source",
                "auto_managed",
                "timeframe",
                "commission",
                "tax",
                "slippage_cost",
                "entry_reason",
                "exit_reason_text",
                "block_reason",
                "setup_score",
                "radar_score_components",
                "strategy_setting_summary"
        };

        writer.write(String.join(",", headers));
        writer.newLine();
    }

    private static void writeTradeRecord(BufferedWriter writer, TradeRecord trade) throws IOException {
        String[] values = {
                escapeCSV(trade.getTradeId()),
                escapeCSV(trade.getSymbol()),
                escapeCSV(trade.getTradeMode() != null ? trade.getTradeMode().getDisplayName() : ""),
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
                escapeCSV(trade.getExitReason() != null ? trade.getExitReason().getDisplayName() : ""),
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
                escapeCSV(trade.getNotes()),
                escapeCSV(trade.getDecisionSource() != null ? trade.getDecisionSource().name() : ""),
                Boolean.toString(trade.isAutoManaged()),
                escapeCSV(trade.getTimeframe() != null ? trade.getTimeframe().name() : ""),
                String.format("%.2f", trade.getCommission()),
                String.format("%.2f", trade.getTax()),
                String.format("%.2f", trade.getSlippageCost()),
                escapeCSV(trade.getEntryReason()),
                escapeCSV(trade.getExitReasonText()),
                escapeCSV(trade.getBlockReason()),
                String.format("%.2f", trade.getSetupScore()),
                escapeCSV(trade.getRadarScoreComponents()),
                escapeCSV(trade.getStrategySettingSummary())
        };

        writer.write(String.join(",", values));
        writer.newLine();
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATETIME_FORMATTER);
    }

    private static String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        return String.format("%.2f", price);
    }

    private static String escapeCSV(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static String generateSummary(List<TradeRecord> trades) {
        if (trades == null || trades.isEmpty()) {
            return "No trade records.";
        }

        int totalTrades = trades.size();
        long profitableTrades = trades.stream().filter(TradeRecord::isProfitable).count();
        long losingTrades = totalTrades - profitableTrades;
        double winRate = (double) profitableTrades / totalTrades * 100.0;

        double totalProfit = trades.stream()
                .filter(TradeRecord::isProfitable)
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();
        double totalLoss = trades.stream()
                .filter(trade -> !trade.isProfitable())
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();
        double netProfit = trades.stream()
                .mapToDouble(TradeRecord::getNetProfit)
                .sum();

        double avgProfit = profitableTrades > 0 ? totalProfit / profitableTrades : 0.0;
        double avgLoss = losingTrades > 0 ? totalLoss / losingTrades : 0.0;
        double profitFactor = totalLoss != 0.0 ? Math.abs(totalProfit / totalLoss) : 0.0;

        StringBuilder summary = new StringBuilder();
        summary.append("========== Trade Summary ==========\n");
        summary.append(String.format("Total trades: %d\n", totalTrades));
        summary.append(String.format("Profitable trades: %d (%.1f%%)\n", profitableTrades, winRate));
        summary.append(String.format("Losing trades: %d (%.1f%%)\n", losingTrades, 100.0 - winRate));
        summary.append(String.format("Total profit: %.2f\n", totalProfit));
        summary.append(String.format("Total loss: %.2f\n", totalLoss));
        summary.append(String.format("Net profit: %.2f\n", netProfit));
        summary.append(String.format("Average profit: %.2f\n", avgProfit));
        summary.append(String.format("Average loss: %.2f\n", avgLoss));
        summary.append(String.format("Profit factor: %.2f\n", profitFactor));
        summary.append("===================================\n");
        return summary.toString();
    }

    public static String exportWithSummary(List<TradeRecord> trades, String outputPath) throws IOException {
        String csvPath = exportToCSV(trades, outputPath);
        String summary = generateSummary(trades);
        String summaryPath = csvPath.replace(".csv", "_summary.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(summaryPath), StandardCharsets.UTF_8)) {
            writer.write(summary);
        }

        System.out.println(summary);
        System.out.println("CSV exported: " + csvPath);
        System.out.println("Summary exported: " + summaryPath);
        return csvPath;
    }
}
