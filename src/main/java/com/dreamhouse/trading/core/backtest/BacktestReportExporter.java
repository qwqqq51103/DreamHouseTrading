package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.scanner.RadarScoreComponent;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        appendWarmupDiagnostics(report, result);

        report.append("完整交易紀錄:\n");
        report.append("-".repeat(120)).append('\n');
        report.append("ID, 股票, 開倉時間, 開倉價, 平倉時間, 平倉價, 數量, 停損, 停利, 出場原因, 毛損益, 手續費, 證交稅, 滑價成本, 淨損益, 報酬率, 持倉分鐘, 開單理由\n");
        for (TradeLifecycle lifecycle : pairTrades(result.getTrades())) {
            report.append(String.format(
                    "%s, %s, %s, %.2f, %s, %.2f, %d, %s, %s, %s, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f%%, %d, %s%n",
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
                    lifecycle.commission(),
                    lifecycle.tax(),
                    lifecycle.slippageCost(),
                    lifecycle.netProfit(),
                    lifecycle.returnPercent() * 100,
                    lifecycle.holdingMinutes(),
                    lifecycle.entryReason()));
        }
        appendSignalObservations(report, result);
        appendConditionContributionStats(report, result);
        appendBlockedSignalStats(report, result);
        return report.toString();
    }

    public static String generateHtmlReport(BacktestResult result, String strategyName, String configurationSummary) {
        List<TradeLifecycle> lifecycles = pairTrades(result.getTrades());
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
        addMetric(html, "訂單筆數", String.valueOf(result.getTotalTrades()));
        addMetric(html, "完成交易數", String.valueOf(lifecycles.size()));
        addMetric(html, "勝率", percent(result.getWinRate()));
        addMetric(html, "Profit Factor", String.format("%.2f", result.getProfitFactor()));
        html.append("</div></section>");

        html.append("<section><h3>當沖與雷達配置</h3><pre>")
                .append(escapeHtml(normalize(configurationSummary)))
                .append("</pre></section>");
        appendWarmupDiagnosticsHtml(html, result);

        html.append("<section><h3>完整交易紀錄</h3><table><thead><tr>");
        String[] headers = {
                "ID", "股票", "開倉時間", "開倉價", "平倉時間", "平倉價", "數量",
                "停損", "停利", "出場原因", "毛損益", "手續費", "證交稅", "滑價成本", "淨損益", "報酬率", "持倉分鐘", "開單理由"
        };
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (TradeLifecycle lifecycle : lifecycles) {
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
            html.append(td(String.format("%.2f", lifecycle.commission())));
            html.append(td(String.format("%.2f", lifecycle.tax())));
            html.append(td(String.format("%.2f", lifecycle.slippageCost())));
            html.append("<td class=\"").append(profitClass).append("\">")
                    .append(String.format("%.2f", lifecycle.netProfit())).append("</td>");
            html.append(td(String.format("%.2f%%", lifecycle.returnPercent() * 100)));
            html.append(td(String.valueOf(lifecycle.holdingMinutes())));
            html.append("<td class=\"reason\">").append(escapeHtml(lifecycle.entryReason())).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
        appendSignalObservationsHtml(html, result);
        appendConditionContributionStatsHtml(html, result);
        appendBlockedSignalStatsHtml(html, result);
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
            for (BacktestResult.WarmupDiagnostic diagnostic : result.getWarmupDiagnostics()) {
                writer.write(csvLine(
                        "warmup_diagnostic",
                        diagnostic.symbol(),
                        "date=" + diagnostic.sessionDate()
                                + "; timeframe=" + diagnostic.timeframe()
                                + "; enabled=" + diagnostic.enabled()
                                + "; requested=" + diagnostic.requestedBars()
                                + "; loaded=" + diagnostic.loadedBars()
                                + "; first=" + format(diagnostic.firstWarmupTime())
                                + "; last=" + format(diagnostic.lastWarmupTime())));
            }
            writer.write('\n');
            writer.write(String.join(",",
                    "交易ID", "股票", "開倉時間", "開倉價", "平倉時間", "平倉價", "數量",
                    "停損", "停利", "出場原因", "毛損益", "手續費", "證交稅", "滑價成本", "淨損益",
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
                        csv(String.format("%.2f", lifecycle.tax())),
                        csv(String.format("%.2f", lifecycle.slippageCost())),
                        csv(String.format("%.2f", lifecycle.netProfit())),
                        csv(String.format("%.2f%%", lifecycle.returnPercent() * 100)),
                        csv(String.valueOf(lifecycle.holdingMinutes())),
                        csv(lifecycle.entryReason())) + "\n");
            }
            writer.write('\n');
            writer.write(String.join(",",
                    "訊號時間", "股票", "動作", "是否阻擋", "分數",
                    "MarketDecision", "MarketRegime", "內部市場狀態", "族群", "觀察清單排名%",
                    "VWAP", "VWAP斜率%", "量能延續", "相對大盤%", "相對族群%",
                    "加分明細", "做多加分項", "後續最大漲幅%", "後續最大回撤%", "收盤報酬%", "理由") + "\n");
            for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
                writer.write(String.join(",",
                        csv(format(observation.timestamp())),
                        csv(observation.symbol()),
                        csv(observation.action()),
                        csv(observation.blocked() ? "Y" : "N"),
                        csv(String.format("%.3f", observation.score())),
                        csv(observation.marketDecision()),
                        csv(observation.marketRegime()),
                        csv(observation.internalMarketState()),
                        csv(observation.industry()),
                        csv(formatNullable(observation.watchlistRankPercent())),
                        csv(formatNullable(observation.vwap())),
                        csv(formatNullable(observation.vwapSlopePercent())),
                        csv(String.valueOf(observation.volumeSustain())),
                        csv(formatNullable(observation.relativeToBenchmarkPercent())),
                        csv(formatNullable(observation.relativeToIndustryPercent())),
                        csv(observation.scoreComponents()),
                        csv(observation.longBonusComponents()),
                        csv(String.format("%.2f", observation.maxFavorablePercent())),
                        csv(String.format("%.2f", observation.maxAdversePercent())),
                        csv(String.format("%.2f", observation.closeReturnPercent())),
                        csv(observation.reason())) + "\n");
            }
            writeConditionContributionCsv(writer, result);
            writeBlockedSignalStatsCsv(writer, result);
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
        report.append("訂單筆數: ").append(result.getTotalTrades()).append('\n');
        report.append("完成交易數: ").append(pairTrades(result.getTrades()).size()).append('\n');
        report.append("勝率: ").append(percent(result.getWinRate())).append('\n');
        report.append("Profit Factor: ").append(String.format("%.2f", result.getProfitFactor())).append("\n\n");
    }

    private static void appendConfiguration(StringBuilder report, String configurationSummary) {
        report.append("當沖與雷達配置:\n");
        report.append("-".repeat(40)).append('\n');
        report.append(normalize(configurationSummary)).append("\n\n");
    }

    private static void appendWarmupDiagnostics(StringBuilder report, BacktestResult result) {
        if (result.getWarmupDiagnostics().isEmpty()) {
            return;
        }
        report.append("SQL warmup diagnostics:\n");
        report.append("-".repeat(80)).append('\n');
        report.append("symbol, date, timeframe, enabled, requested_bars, loaded_bars, first_warmup, last_warmup\n");
        for (BacktestResult.WarmupDiagnostic diagnostic : result.getWarmupDiagnostics()) {
            report.append(String.format(
                    "%s, %s, %s, %s, %d, %d, %s, %s%n",
                    diagnostic.symbol(),
                    diagnostic.sessionDate(),
                    diagnostic.timeframe(),
                    diagnostic.enabled(),
                    diagnostic.requestedBars(),
                    diagnostic.loadedBars(),
                    format(diagnostic.firstWarmupTime()),
                    format(diagnostic.lastWarmupTime())));
        }
        report.append('\n');
    }

    private static void appendWarmupDiagnosticsHtml(StringBuilder html, BacktestResult result) {
        if (result.getWarmupDiagnostics().isEmpty()) {
            return;
        }
        html.append("<section><h3>SQL warmup diagnostics</h3><table><thead><tr>");
        String[] headers = {"symbol", "date", "timeframe", "enabled", "requested_bars", "loaded_bars",
                "first_warmup", "last_warmup"};
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (BacktestResult.WarmupDiagnostic diagnostic : result.getWarmupDiagnostics()) {
            html.append("<tr>");
            html.append(td(diagnostic.symbol()));
            html.append(td(String.valueOf(diagnostic.sessionDate())));
            html.append(td(diagnostic.timeframe()));
            html.append(td(String.valueOf(diagnostic.enabled())));
            html.append(td(String.valueOf(diagnostic.requestedBars())));
            html.append(td(String.valueOf(diagnostic.loadedBars())));
            html.append(td(format(diagnostic.firstWarmupTime())));
            html.append(td(format(diagnostic.lastWarmupTime())));
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendSignalObservations(StringBuilder report, BacktestResult result) {
        if (result.getSignalObservations().isEmpty()) {
            return;
        }
        report.append("\n訊號與阻擋後續表現:\n");
        report.append("-".repeat(120)).append('\n');
        report.append("時間, 股票, 動作, 是否阻擋, 分數, MarketDecision, MarketRegime, 內部市場狀態, 族群, 觀察清單排名%, VWAP, VWAP斜率%, 量能延續, 相對大盤%, 相對族群%, 加分明細, 做多加分項, 後續最大漲幅%, 後續最大回撤%, 收盤報酬%, 理由\n");
        for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
            report.append(String.format(
                    "%s, %s, %s, %s, %.3f, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %.2f, %.2f, %.2f, %s%n",
                    format(observation.timestamp()),
                    observation.symbol(),
                    observation.action(),
                    observation.blocked() ? "Y" : "N",
                    observation.score(),
                    observation.marketDecision(),
                    observation.marketRegime(),
                    observation.internalMarketState(),
                    observation.industry(),
                    formatNullable(observation.watchlistRankPercent()),
                    formatNullable(observation.vwap()),
                    formatNullable(observation.vwapSlopePercent()),
                    observation.volumeSustain(),
                    formatNullable(observation.relativeToBenchmarkPercent()),
                    formatNullable(observation.relativeToIndustryPercent()),
                    observation.scoreComponents(),
                    observation.longBonusComponents(),
                    observation.maxFavorablePercent(),
                    observation.maxAdversePercent(),
                    observation.closeReturnPercent(),
                    observation.reason()));
        }
    }

    private static void appendConditionContributionStats(StringBuilder report, BacktestResult result) {
        List<ConditionContributionStat> stats = conditionContributionStats(result);
        if (stats.isEmpty()) {
            return;
        }
        report.append("\n加分條件貢獻統計:\n");
        report.append("-".repeat(120)).append('\n');
        report.append("條件, 評估數, 做多通過, 未通過, 平均LONG加分, 通過後最大漲幅%, 通過後最大回撤%, 通過後收盤報酬%, 未通過後最大漲幅%, 未通過後最大回撤%, 未通過後收盤報酬%\n");
        for (ConditionContributionStat stat : stats) {
            report.append(String.format(
                    "%s, %d, %d, %d, %.3f, %.2f, %.2f, %.2f, %.2f, %.2f, %.2f%n",
                    stat.condition(),
                    stat.evaluatedCount(),
                    stat.longPassCount(),
                    stat.nonLongCount(),
                    stat.averageLongContribution(),
                    stat.passPerformance().averageMaxFavorable(),
                    stat.passPerformance().averageMaxAdverse(),
                    stat.passPerformance().averageCloseReturn(),
                    stat.nonLongPerformance().averageMaxFavorable(),
                    stat.nonLongPerformance().averageMaxAdverse(),
                    stat.nonLongPerformance().averageCloseReturn()));
        }
    }

    private static void appendBlockedSignalStats(StringBuilder report, BacktestResult result) {
        List<BlockedSignalStat> stats = blockedSignalStats(result);
        if (stats.isEmpty()) {
            return;
        }
        report.append("\n硬阻擋後續統計:\n");
        report.append("-".repeat(120)).append('\n');
        report.append("阻擋類型, 阻擋數, 後續最大漲幅%, 後續最大回撤%, 收盤報酬%\n");
        for (BlockedSignalStat stat : stats) {
            report.append(String.format(
                    "%s, %d, %.2f, %.2f, %.2f%n",
                    stat.reason(),
                    stat.performance().count(),
                    stat.performance().averageMaxFavorable(),
                    stat.performance().averageMaxAdverse(),
                    stat.performance().averageCloseReturn()));
        }
    }

    private static void appendSignalObservationsHtml(StringBuilder html, BacktestResult result) {
        if (result.getSignalObservations().isEmpty()) {
            return;
        }
        html.append("<section><h3>訊號與阻擋後續表現</h3><table><thead><tr>");
        String[] headers = {"時間", "股票", "動作", "阻擋", "分數", "MarketDecision", "MarketRegime",
                "內部市場狀態", "族群", "觀察清單排名%", "VWAP", "VWAP斜率%", "量能延續",
                "相對大盤%", "相對族群%", "加分明細", "做多加分項",
                "後續最大漲幅%", "後續最大回撤%", "收盤報酬%", "理由"};
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
            html.append("<tr>");
            html.append(td(format(observation.timestamp())));
            html.append(td(observation.symbol()));
            html.append(td(observation.action()));
            html.append(td(observation.blocked() ? "Y" : "N"));
            html.append(td(String.format("%.3f", observation.score())));
            html.append(td(observation.marketDecision()));
            html.append(td(observation.marketRegime()));
            html.append(td(observation.internalMarketState()));
            html.append(td(observation.industry()));
            html.append(td(formatNullable(observation.watchlistRankPercent())));
            html.append(td(formatNullable(observation.vwap())));
            html.append(td(formatNullable(observation.vwapSlopePercent())));
            html.append(td(String.valueOf(observation.volumeSustain())));
            html.append(td(formatNullable(observation.relativeToBenchmarkPercent())));
            html.append(td(formatNullable(observation.relativeToIndustryPercent())));
            html.append("<td class=\"reason\">").append(escapeHtml(observation.scoreComponents())).append("</td>");
            html.append("<td class=\"reason\">").append(escapeHtml(observation.longBonusComponents())).append("</td>");
            html.append(td(String.format("%.2f", observation.maxFavorablePercent())));
            html.append(td(String.format("%.2f", observation.maxAdversePercent())));
            html.append(td(String.format("%.2f", observation.closeReturnPercent())));
            html.append("<td class=\"reason\">").append(escapeHtml(observation.reason())).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendConditionContributionStatsHtml(StringBuilder html, BacktestResult result) {
        List<ConditionContributionStat> stats = conditionContributionStats(result);
        if (stats.isEmpty()) {
            return;
        }
        html.append("<section><h3>加分條件貢獻統計</h3><table><thead><tr>");
        String[] headers = {"條件", "評估數", "做多通過", "未通過", "平均LONG加分",
                "通過後最大漲幅%", "通過後最大回撤%", "通過後收盤報酬%",
                "未通過後最大漲幅%", "未通過後最大回撤%", "未通過後收盤報酬%"};
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (ConditionContributionStat stat : stats) {
            html.append("<tr>");
            html.append(td(stat.condition()));
            html.append(td(String.valueOf(stat.evaluatedCount())));
            html.append(td(String.valueOf(stat.longPassCount())));
            html.append(td(String.valueOf(stat.nonLongCount())));
            html.append(td(String.format("%.3f", stat.averageLongContribution())));
            html.append(td(String.format("%.2f", stat.passPerformance().averageMaxFavorable())));
            html.append(td(String.format("%.2f", stat.passPerformance().averageMaxAdverse())));
            html.append(td(String.format("%.2f", stat.passPerformance().averageCloseReturn())));
            html.append(td(String.format("%.2f", stat.nonLongPerformance().averageMaxFavorable())));
            html.append(td(String.format("%.2f", stat.nonLongPerformance().averageMaxAdverse())));
            html.append(td(String.format("%.2f", stat.nonLongPerformance().averageCloseReturn())));
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendBlockedSignalStatsHtml(StringBuilder html, BacktestResult result) {
        List<BlockedSignalStat> stats = blockedSignalStats(result);
        if (stats.isEmpty()) {
            return;
        }
        html.append("<section><h3>硬阻擋後續統計</h3><table><thead><tr>");
        String[] headers = {"阻擋類型", "阻擋數", "後續最大漲幅%", "後續最大回撤%", "收盤報酬%"};
        for (String header : headers) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (BlockedSignalStat stat : stats) {
            html.append("<tr>");
            html.append("<td class=\"reason\">").append(escapeHtml(stat.reason())).append("</td>");
            html.append(td(String.valueOf(stat.performance().count())));
            html.append(td(String.format("%.2f", stat.performance().averageMaxFavorable())));
            html.append(td(String.format("%.2f", stat.performance().averageMaxAdverse())));
            html.append(td(String.format("%.2f", stat.performance().averageCloseReturn())));
            html.append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void writeConditionContributionCsv(OutputStreamWriter writer, BacktestResult result) throws IOException {
        List<ConditionContributionStat> stats = conditionContributionStats(result);
        if (stats.isEmpty()) {
            return;
        }
        writer.write('\n');
        writer.write(String.join(",",
                "條件", "評估數", "做多通過", "未通過", "平均LONG加分",
                "通過後最大漲幅%", "通過後最大回撤%", "通過後收盤報酬%",
                "未通過後最大漲幅%", "未通過後最大回撤%", "未通過後收盤報酬%") + "\n");
        for (ConditionContributionStat stat : stats) {
            writer.write(String.join(",",
                    csv(stat.condition()),
                    csv(String.valueOf(stat.evaluatedCount())),
                    csv(String.valueOf(stat.longPassCount())),
                    csv(String.valueOf(stat.nonLongCount())),
                    csv(String.format("%.3f", stat.averageLongContribution())),
                    csv(String.format("%.2f", stat.passPerformance().averageMaxFavorable())),
                    csv(String.format("%.2f", stat.passPerformance().averageMaxAdverse())),
                    csv(String.format("%.2f", stat.passPerformance().averageCloseReturn())),
                    csv(String.format("%.2f", stat.nonLongPerformance().averageMaxFavorable())),
                    csv(String.format("%.2f", stat.nonLongPerformance().averageMaxAdverse())),
                    csv(String.format("%.2f", stat.nonLongPerformance().averageCloseReturn()))) + "\n");
        }
    }

    private static void writeBlockedSignalStatsCsv(OutputStreamWriter writer, BacktestResult result) throws IOException {
        List<BlockedSignalStat> stats = blockedSignalStats(result);
        if (stats.isEmpty()) {
            return;
        }
        writer.write('\n');
        writer.write(String.join(",", "阻擋類型", "阻擋數", "後續最大漲幅%", "後續最大回撤%", "收盤報酬%") + "\n");
        for (BlockedSignalStat stat : stats) {
            writer.write(String.join(",",
                    csv(stat.reason()),
                    csv(String.valueOf(stat.performance().count())),
                    csv(String.format("%.2f", stat.performance().averageMaxFavorable())),
                    csv(String.format("%.2f", stat.performance().averageMaxAdverse())),
                    csv(String.format("%.2f", stat.performance().averageCloseReturn()))) + "\n");
        }
    }

    private static List<ConditionContributionStat> conditionContributionStats(BacktestResult result) {
        if (result == null || result.getSignalObservations().isEmpty()) {
            return List.of();
        }
        Map<String, ConditionContributionAccumulator> accumulators = new LinkedHashMap<>();
        for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
            for (var component : observation.scoreComponentDetails()) {
                if (component == null || component.name() == null || component.name().isBlank()) {
                    continue;
                }
                accumulators.computeIfAbsent(component.name(), ConditionContributionAccumulator::new)
                        .add(component, observation);
            }
        }
        return accumulators.values().stream()
                .map(ConditionContributionAccumulator::toStat)
                .sorted(Comparator.comparingInt(ConditionContributionStat::evaluatedCount).reversed()
                        .thenComparing(ConditionContributionStat::condition))
                .toList();
    }

    private static List<BlockedSignalStat> blockedSignalStats(BacktestResult result) {
        if (result == null || result.getSignalObservations().isEmpty()) {
            return List.of();
        }
        Map<String, PerformanceAccumulator> accumulators = new HashMap<>();
        for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
            if (!observation.blocked()) {
                continue;
            }
            String reason = normalizeBlockReason(observation.reason());
            accumulators.computeIfAbsent(reason, ignored -> new PerformanceAccumulator()).add(observation);
        }
        return accumulators.entrySet().stream()
                .map(entry -> new BlockedSignalStat(entry.getKey(), entry.getValue().toStat()))
                .sorted(Comparator.comparingInt((BlockedSignalStat stat) -> stat.performance().count()).reversed()
                        .thenComparing(BlockedSignalStat::reason))
                .toList();
    }

    private static String normalizeBlockReason(String reason) {
        String text = reason != null && !reason.isBlank() ? reason : "未提供阻擋原因";
        if (text.contains("SignalRSI=SHORT")) {
            return "SignalRSI=SHORT";
        }
        if (text.contains("RSI 超賣訊號缺少")) {
            return "RSI 接刀缺少確認";
        }
        if (text.contains("Volume Sustain") || text.contains("量能延續")) {
            return "Volume Sustain 未通過";
        }
        if (text.contains("VWAP 斜率") || text.contains("VWAP斜率")) {
            return "VWAP 斜率未向上";
        }
        if (text.contains("未站上 VWAP") || text.contains("低於 VWAP")) {
            return "未站上 VWAP";
        }
        if (text.contains("ATR") && text.contains("追")) {
            return "ATR 追價限制";
        }
        if (text.contains("追價限制")) {
            return "近期低點追價限制";
        }
        if (text.contains("最低進場分數") || (text.contains("分數") && text.contains("低於"))) {
            return "最低進場分數";
        }
        if (text.contains("弱勢盤")) {
            return "弱勢盤阻擋";
        }
        if (text.contains("突破後")) {
            return "突破確認未通過";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private static final class ConditionContributionAccumulator {
        private final String condition;
        private final PerformanceAccumulator passPerformance = new PerformanceAccumulator();
        private final PerformanceAccumulator nonLongPerformance = new PerformanceAccumulator();
        private int evaluatedCount;
        private int longPassCount;
        private double longContributionSum;

        private ConditionContributionAccumulator(String condition) {
            this.condition = condition;
        }

        private void add(RadarScoreComponent component, BacktestResult.SignalObservation observation) {
            evaluatedCount++;
            if (component.contributesToLong()) {
                longPassCount++;
                longContributionSum += component.longContribution();
                passPerformance.add(observation);
            } else {
                nonLongPerformance.add(observation);
            }
        }

        private ConditionContributionStat toStat() {
            return new ConditionContributionStat(
                    condition,
                    evaluatedCount,
                    longPassCount,
                    Math.max(0, evaluatedCount - longPassCount),
                    longPassCount > 0 ? longContributionSum / longPassCount : 0.0,
                    passPerformance.toStat(),
                    nonLongPerformance.toStat());
        }
    }

    private static final class PerformanceAccumulator {
        private int count;
        private double maxFavorableSum;
        private double maxAdverseSum;
        private double closeReturnSum;

        private void add(BacktestResult.SignalObservation observation) {
            if (observation == null) {
                return;
            }
            count++;
            maxFavorableSum += observation.maxFavorablePercent();
            maxAdverseSum += observation.maxAdversePercent();
            closeReturnSum += observation.closeReturnPercent();
        }

        private PerformanceStat toStat() {
            if (count == 0) {
                return new PerformanceStat(0, 0.0, 0.0, 0.0);
            }
            return new PerformanceStat(
                    count,
                    maxFavorableSum / count,
                    maxAdverseSum / count,
                    closeReturnSum / count);
        }
    }

    private record ConditionContributionStat(
            String condition,
            int evaluatedCount,
            int longPassCount,
            int nonLongCount,
            double averageLongContribution,
            PerformanceStat passPerformance,
            PerformanceStat nonLongPerformance) {
    }

    private record BlockedSignalStat(String reason, PerformanceStat performance) {
    }

    private record PerformanceStat(
            int count,
            double averageMaxFavorable,
            double averageMaxAdverse,
            double averageCloseReturn) {
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

        public double tax() {
            return entry.getTaxAmount() + exit.getTaxAmount();
        }

        public double slippageCost() {
            return entry.getSlippageCost() + exit.getSlippageCost();
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
