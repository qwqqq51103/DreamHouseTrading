package com.dreamhouse.trading.core.backtest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Exports backtest reports with the full entry -> exit lifecycle.
 */
public final class BacktestReportExporter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private BacktestReportExporter() {
    }

    public static String generateTextReport(BacktestResult result, String strategyName, String configurationSummary) {
        StringBuilder report = new StringBuilder();
        report.append("============================================================\n");
        report.append("回測報告\n");
        report.append("============================================================\n");
        report.append("策略名稱: ").append(strategyName).append('\n');
        report.append("產生時間: ").append(LocalDateTime.now().format(DATE_TIME)).append('\n');
        report.append("回測期間: ").append(format(result.getStartDate())).append(" ~ ")
                .append(format(result.getEndDate())).append("\n\n");

        appendSummary(report, result);
        appendConfiguration(report, configurationSummary);

        report.append("完整交易紀錄:\n");
        report.append("-".repeat(120)).append('\n');
        report.append("ID, 股票, 開倉時間, 開倉價, 平倉時間, 平倉價, 數量, 停損, 停利, 出場原因, 毛損益, 淨損益, 報酬率, 持倉分鐘, 開單理由\n");
        for (TradeLifecycle lifecycle : pairTrades(result.getTrades())) {
            report.append(String.format(
                    "%s, %s, %s, %.2f, %s, %.2f, %d, %s, %s, %s, %.2f, %.2f, %.2f%%, %d, %s%n",
                    lifecycle.tradeId(),
                    lifecycle.symbol(),
                    format(lifecycle.entryTime()),
                    lifecycle.entryPrice(),
                    format(lifecycle.exitTime()),
                    lifecycle.exitPrice(),
                    lifecycle.quantity(),
                    formatNullable(lifecycle.stopLoss()),
                    formatNullable(lifecycle.takeProfit()),
                    lifecycle.exitReason(),
                    lifecycle.grossProfit(),
                    lifecycle.netProfit(),
                    lifecycle.returnPercent() * 100,
                    lifecycle.holdingMinutes(),
                    lifecycle.entryReason()));
        }
        return report.toString();
    }

    public static String generateHtmlReport(BacktestResult result, String strategyName, String configurationSummary) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-TW\"><head><meta charset=\"UTF-8\">");
        html.append("<title>回測報告 - ").append(escapeHtml(strategyName)).append("</title>");
        html.append("""
                <style>
                body{font-family:'Microsoft JhengHei',Arial,sans-serif;margin:24px;color:#1f2933;background:#f7f8fa}
                h1,h2,h3{margin:0 0 12px}
                section{background:#fff;border:1px solid #d8dde3;border-radius:6px;margin:0 0 18px;padding:16px}
                .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px}
                .item{background:#f1f5f9;border-radius:4px;padding:10px}.label{color:#52606d;font-size:12px}.value{font-size:18px;font-weight:600}
                pre{white-space:pre-wrap;background:#111827;color:#f9fafb;padding:12px;border-radius:4px;overflow:auto}
                table{width:100%;border-collapse:collapse;font-size:12px}th,td{border:1px solid #d8dde3;padding:6px;vertical-align:top}
                th{background:#263238;color:#fff;position:sticky;top:0}.profit{color:#087f23;font-weight:700}.loss{color:#c62828;font-weight:700}
                .reason{min-width:360px}
                </style>
                """);
        html.append("</head><body>");
        html.append("<h1>回測報告</h1><h2>").append(escapeHtml(strategyName)).append("</h2>");
        html.append("<p>產生時間: ").append(LocalDateTime.now().format(DATE_TIME)).append("</p>");

        html.append("<section><h3>績效摘要</h3><div class=\"grid\">");
        addMetric(html, "起始資金", money(result.getInitialCapital()));
        addMetric(html, "結束資金", money(result.getFinalValue()));
        addMetric(html, "淨損益", money(result.getFinalValue() - result.getInitialCapital()));
        addMetric(html, "總報酬率", percent(result.getTotalReturn()));
        addMetric(html, "最大回撤", percent(result.getMaxDrawdown()));
        addMetric(html, "交易筆數", String.valueOf(result.getTotalTrades()));
        addMetric(html, "勝率", percent(result.getWinRate()));
        addMetric(html, "Profit Factor", String.format("%.2f", result.getProfitFactor()));
        html.append("</div></section>");

        html.append("<section><h3>當沖與雷達配置</h3><pre>")
                .append(escapeHtml(normalize(configurationSummary)))
                .append("</pre></section>");

        html.append("<section><h3>完整交易紀錄</h3><table><thead><tr>");
        String[] headers = {
                "ID", "股票", "開倉時間", "開倉價", "平倉時間", "平倉價", "數量",
                "停損", "停利", "出場原因", "毛損益", "淨損益", "報酬率", "持倉分鐘", "開單理由"
        };
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (TradeLifecycle lifecycle : pairTrades(result.getTrades())) {
            String profitClass = lifecycle.netProfit() >= 0 ? "profit" : "loss";
            html.append("<tr>");
            html.append(td(lifecycle.tradeId()));
            html.append(td(lifecycle.symbol()));
            html.append(td(format(lifecycle.entryTime())));
            html.append(td(String.format("%.2f", lifecycle.entryPrice())));
            html.append(td(format(lifecycle.exitTime())));
            html.append(td(String.format("%.2f", lifecycle.exitPrice())));
            html.append(td(String.valueOf(lifecycle.quantity())));
            html.append(td(formatNullable(lifecycle.stopLoss())));
            html.append(td(formatNullable(lifecycle.takeProfit())));
            html.append(td(lifecycle.exitReason()));
            html.append(td(String.format("%.2f", lifecycle.grossProfit())));
            html.append("<td class=\"").append(profitClass).append("\">")
                    .append(String.format("%.2f", lifecycle.netProfit())).append("</td>");
            html.append(td(String.format("%.2f%%", lifecycle.returnPercent() * 100)));
            html.append(td(String.valueOf(lifecycle.holdingMinutes())));
            html.append("<td class=\"reason\">").append(escapeHtml(lifecycle.entryReason())).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
        html.append("</body></html>");
        return html.toString();
    }

    public static String exportDetailedCsv(BacktestResult result, String strategyName, String configurationSummary)
            throws IOException {
        String safeStrategy = strategyName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        String fileName = String.format("backtest_detailed_report_%s_%s.csv", safeStrategy,
                LocalDateTime.now().format(FILE_TIME));
        File output = new File(System.getProperty("user.dir"), fileName);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("區塊,欄位,內容\n");
            writer.write(csvLine("回測配置", "策略名稱", strategyName));
            writer.write(csvLine("回測配置", "回測期間", format(result.getStartDate()) + " ~ " + format(result.getEndDate())));
            writer.write(csvLine("回測配置", "起始資金", money(result.getInitialCapital())));
            writer.write(csvLine("回測配置", "結束資金", money(result.getFinalValue())));
            writer.write(csvLine("回測配置", "淨損益", money(result.getFinalValue() - result.getInitialCapital())));
            writer.write(csvLine("回測配置", "總報酬率", percent(result.getTotalReturn())));
            for (String line : normalize(configurationSummary).split("\\R")) {
                if (!line.isBlank()) {
                    writer.write(csvLine("當沖配置", "", line));
                }
            }
            writer.write('\n');
            writer.write(String.join(",",
                    "交易ID", "股票", "開倉時間", "開倉價", "平倉時間", "平倉價", "數量",
                    "停損", "停利", "出場原因", "毛損益", "手續費", "淨損益",
                    "報酬率", "持倉分鐘", "開單理由") + "\n");
            for (TradeLifecycle lifecycle : pairTrades(result.getTrades())) {
                writer.write(String.join(",",
                        csv(lifecycle.tradeId()),
                        csv(lifecycle.symbol()),
                        csv(format(lifecycle.entryTime())),
                        csv(String.format("%.2f", lifecycle.entryPrice())),
                        csv(format(lifecycle.exitTime())),
                        csv(String.format("%.2f", lifecycle.exitPrice())),
                        csv(String.valueOf(lifecycle.quantity())),
                        csv(formatNullable(lifecycle.stopLoss())),
                        csv(formatNullable(lifecycle.takeProfit())),
                        csv(lifecycle.exitReason()),
                        csv(String.format("%.2f", lifecycle.grossProfit())),
                        csv(String.format("%.2f", lifecycle.commission())),
                        csv(String.format("%.2f", lifecycle.netProfit())),
                        csv(String.format("%.2f%%", lifecycle.returnPercent() * 100)),
                        csv(String.valueOf(lifecycle.holdingMinutes())),
                        csv(lifecycle.entryReason())) + "\n");
            }
        }
        return output.getAbsolutePath();
    }

    public static void saveReportToFile(String content, String strategyName, String format) throws IOException {
        String safeStrategy = strategyName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        String fileName = String.format("backtest_report_%s_%s.%s", safeStrategy,
                LocalDateTime.now().format(FILE_TIME), format);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(fileName), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    public static List<TradeLifecycle> pairTrades(List<Trade> trades) {
        Map<String, Queue<Trade>> openBySymbol = new HashMap<>();
        List<TradeLifecycle> lifecycles = new ArrayList<>();
        int tradeId = 1;
        for (Trade trade : trades) {
            if (trade.getType() == TradeType.BUY) {
                openBySymbol.computeIfAbsent(trade.getSymbol(), ignored -> new ArrayDeque<>()).add(trade);
            } else if (trade.getType() == TradeType.SELL) {
                Queue<Trade> queue = openBySymbol.get(trade.getSymbol());
                Trade buy = queue != null ? queue.poll() : null;
                if (buy != null) {
                    lifecycles.add(new TradeLifecycle(String.format("T%05d", tradeId++), buy, trade));
                }
            }
        }
        return lifecycles;
    }

    private static void appendSummary(StringBuilder report, BacktestResult result) {
        report.append("績效摘要:\n");
        report.append("-".repeat(40)).append('\n');
        report.append("起始資金: ").append(money(result.getInitialCapital())).append('\n');
        report.append("結束資金: ").append(money(result.getFinalValue())).append('\n');
        report.append("淨損益: ").append(money(result.getFinalValue() - result.getInitialCapital())).append('\n');
        report.append("總報酬率: ").append(percent(result.getTotalReturn())).append('\n');
        report.append("最大回撤: ").append(percent(result.getMaxDrawdown())).append('\n');
        report.append("交易筆數: ").append(result.getTotalTrades()).append('\n');
        report.append("勝率: ").append(percent(result.getWinRate())).append('\n');
        report.append("Profit Factor: ").append(String.format("%.2f", result.getProfitFactor())).append("\n\n");
    }

    private static void appendConfiguration(StringBuilder report, String configurationSummary) {
        report.append("當沖與雷達配置:\n");
        report.append("-".repeat(40)).append('\n');
        report.append(normalize(configurationSummary)).append("\n\n");
    }

    private static void addMetric(StringBuilder html, String label, String value) {
        html.append("<div class=\"item\"><div class=\"label\">").append(escapeHtml(label))
                .append("</div><div class=\"value\">").append(escapeHtml(value)).append("</div></div>");
    }

    private static String td(String value) {
        return "<td>" + escapeHtml(value) + "</td>";
    }

    private static String csvLine(String section, String field, String content) {
        return csv(section) + "," + csv(field) + "," + csv(content) + "\n";
    }

    private static String csv(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "未提供配置快照" : value;
    }

    private static String format(LocalDateTime time) {
        return time != null ? time.format(DATE_TIME) : "";
    }

    private static String money(double value) {
        return String.format("%.2f", value);
    }

    private static String percent(double value) {
        return String.format("%.2f%%", value * 100);
    }

    private static String formatNullable(Double value) {
        return value != null ? String.format("%.2f", value) : "-";
    }

    public record TradeLifecycle(String tradeId, Trade entry, Trade exit) {
        public String symbol() {
            return entry.getSymbol();
        }

        public LocalDateTime entryTime() {
            return entry.getTimestamp();
        }

        public LocalDateTime exitTime() {
            return exit.getTimestamp();
        }

        public double entryPrice() {
            return entry.getPrice();
        }

        public double exitPrice() {
            return exit.getPrice();
        }

        public int quantity() {
            return entry.getQuantity();
        }

        public Double stopLoss() {
            return entry.getStopLoss();
        }

        public Double takeProfit() {
            return entry.getTakeProfit();
        }

        public String entryReason() {
            return entry.getExitReason() != null && !entry.getExitReason().isBlank()
                    ? entry.getExitReason()
                    : "-";
        }

        public String exitReason() {
            return exit.getExitReason() != null && !exit.getExitReason().isBlank()
                    ? exit.getExitReason()
                    : "-";
        }

        public double grossProfit() {
            return (exit.getPrice() - entry.getPrice()) * entry.getQuantity();
        }

        public double commission() {
            return entry.getCommissionAmount() + exit.getCommissionAmount();
        }

        public double netProfit() {
            return exit.getNetProceeds() - entry.getTotalCost();
        }

        public double returnPercent() {
            double cost = entry.getTotalCost();
            return cost > 0 ? netProfit() / cost : 0.0;
        }

        public long holdingMinutes() {
            return ChronoUnit.MINUTES.between(entry.getTimestamp(), exit.getTimestamp());
        }
    }
}
