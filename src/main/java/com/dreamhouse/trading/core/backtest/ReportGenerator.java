package com.dreamhouse.trading.core.backtest;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 回測報告生成器
 * 用於生成詳細的回測報告
 */
public class ReportGenerator {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * 生成完整的 HTML 報告
     */
    public static String generateHtmlReport(BacktestResult result, String strategyName) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang='zh-TW'>\n");
        html.append("<head>\n");
        html.append("    <meta charset='UTF-8'>\n");
        html.append("    <title>回測報告 - ").append(strategyName).append("</title>\n");
        html.append("    <style>\n");
        html.append(getHtmlStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // 標題
        html.append("    <div class='header'>\n");
        html.append("        <h1>回測報告</h1>\n");
        html.append("        <h2>").append(strategyName).append("</h2>\n");
        html.append("        <p>生成時間: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("</p>\n");
        html.append("    </div>\n");
        
        // 基本信息
        html.append("    <div class='section'>\n");
        html.append("        <h3>基本信息</h3>\n");
        html.append("        <div class='info-grid'>\n");
        html.append("            <div class='info-item'>\n");
        html.append("                <span class='label'>回測期間:</span>\n");
        html.append("                <span class='value'>").append(result.getStartDate().format(DATE_FORMAT))
               .append(" 至 ").append(result.getEndDate().format(DATE_FORMAT)).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class='info-item'>\n");
        html.append("                <span class='label'>初始資金:</span>\n");
        html.append("                <span class='value'>$").append(String.format("%.2f", result.getInitialCapital())).append("</span>\n");
        html.append("            </div>\n");
        html.append("            <div class='info-item'>\n");
        html.append("                <span class='label'>最終價值:</span>\n");
        html.append("                <span class='value'>$").append(String.format("%.2f", result.getFinalValue())).append("</span>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        
        // 績效指標
        html.append("    <div class='section'>\n");
        html.append("        <h3>績效指標</h3>\n");
        html.append("        <div class='metrics-grid'>\n");
        
        addMetricCard(html, "總收益率", String.format("%.2f%%", result.getTotalReturn() * 100), 
                     result.getTotalReturn() >= 0 ? "positive" : "negative");
        addMetricCard(html, "年化收益率", String.format("%.2f%%", result.getAnnualizedReturn() * 100), 
                     result.getAnnualizedReturn() >= 0 ? "positive" : "negative");
        addMetricCard(html, "最大回撤", String.format("%.2f%%", result.getMaxDrawdown() * 100), "negative");
        addMetricCard(html, "夏普比率", String.format("%.2f", result.getSharpeRatio()), 
                     result.getSharpeRatio() >= 1.0 ? "positive" : "neutral");
        addMetricCard(html, "波動率", String.format("%.2f%%", result.getVolatility() * 100), "neutral");
        addMetricCard(html, "總交易次數", String.valueOf(result.getTotalTrades()), "neutral");
        addMetricCard(html, "勝率", String.format("%.1f%%", result.getWinRate() * 100), 
                     result.getWinRate() >= 0.5 ? "positive" : "negative");
        addMetricCard(html, "盈虧比", String.format("%.2f", result.getProfitFactor()), 
                     result.getProfitFactor() >= 1.0 ? "positive" : "negative");
        
        html.append("        </div>\n");
        html.append("    </div>\n");
        
        // 交易記錄
        html.append("    <div class='section'>\n");
        html.append("        <h3>交易記錄</h3>\n");
        html.append(generateTradeTable(result.getTrades()));
        html.append("    </div>\n");
        
        // 月度統計
        html.append("    <div class='section'>\n");
        html.append("        <h3>月度統計</h3>\n");
        html.append(generateMonthlyStats(result));
        html.append("    </div>\n");
        
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * 生成文本報告
     */
    public static String generateTextReport(BacktestResult result, String strategyName) {
        StringBuilder report = new StringBuilder();
        
        report.append("=".repeat(60)).append("\n");
        report.append("                    回測報告\n");
        report.append("=".repeat(60)).append("\n");
        report.append("策略名稱: ").append(strategyName).append("\n");
        report.append("生成時間: ").append(LocalDateTime.now().format(DATE_FORMAT)).append("\n");
        report.append("回測期間: ").append(result.getStartDate().format(DATE_FORMAT))
               .append(" 至 ").append(result.getEndDate().format(DATE_FORMAT)).append("\n");
        report.append("\n");
        
        // 基本信息
        report.append("基本信息:\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("初始資金: $%.2f\n", result.getInitialCapital()));
        report.append(String.format("最終價值: $%.2f\n", result.getFinalValue()));
        report.append(String.format("絕對收益: $%.2f\n", result.getFinalValue() - result.getInitialCapital()));
        report.append("\n");
        
        // 績效指標
        report.append("績效指標:\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("總收益率: %.2f%%\n", result.getTotalReturn() * 100));
        report.append(String.format("年化收益率: %.2f%%\n", result.getAnnualizedReturn() * 100));
        report.append(String.format("最大回撤: %.2f%%\n", result.getMaxDrawdown() * 100));
        report.append(String.format("夏普比率: %.2f\n", result.getSharpeRatio()));
        report.append(String.format("波動率: %.2f%%\n", result.getVolatility() * 100));
        report.append("\n");
        
        // 交易統計
        report.append("交易統計:\n");
        report.append("-".repeat(40)).append("\n");
        report.append(String.format("總交易次數: %d\n", result.getTotalTrades()));
        report.append(String.format("獲利交易: %d\n", result.getWinningTrades()));
        report.append(String.format("虧損交易: %d\n", result.getLosingTrades()));
        report.append(String.format("勝率: %.1f%%\n", result.getWinRate() * 100));
        report.append(String.format("平均獲利: $%.2f\n", result.getAvgWin()));
        report.append(String.format("平均虧損: $%.2f\n", result.getAvgLoss()));
        report.append(String.format("盈虧比: %.2f\n", result.getProfitFactor()));
        report.append("\n");
        
        // 風險指標
        report.append("風險指標:\n");
        report.append("-".repeat(40)).append("\n");
        
        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        if (!snapshots.isEmpty()) {
            double maxValue = snapshots.stream().mapToDouble(PortfolioSnapshot::getTotalValue).max().orElse(0);
            double minValue = snapshots.stream().mapToDouble(PortfolioSnapshot::getTotalValue).min().orElse(0);
            
            report.append(String.format("最高淨值: $%.2f\n", maxValue));
            report.append(String.format("最低淨值: $%.2f\n", minValue));
            report.append(String.format("淨值波動: $%.2f (%.2f%%)\n", 
                         maxValue - minValue, (maxValue - minValue) / result.getInitialCapital() * 100));
        }
        
        report.append("\n");
        report.append("=".repeat(60)).append("\n");
        
        return report.toString();
    }
    
    /**
     * 保存報告到文件
     */
    public static void saveReportToFile(String content, String strategyName, String format) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        String fileName = String.format("backtest_report_%s_%s.%s", 
                                       strategyName.replaceAll("[^a-zA-Z0-9]", "_"), 
                                       timestamp, format);
        
        // 使用 UTF-8 編碼寫入文件，避免亂碼
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(fileName), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
    
    /**
     * 添加指標卡片
     */
    private static void addMetricCard(StringBuilder html, String title, String value, String type) {
        html.append("            <div class='metric-card ").append(type).append("'>\n");
        html.append("                <div class='metric-title'>").append(title).append("</div>\n");
        html.append("                <div class='metric-value'>").append(value).append("</div>\n");
        html.append("            </div>\n");
    }
    
    /**
     * 生成交易記錄表格
     */
    private static String generateTradeTable(List<Trade> trades) {
        StringBuilder table = new StringBuilder();
        
        table.append("        <div class='table-container'>\n");
        table.append("            <table class='trade-table'>\n");
        table.append("                <thead>\n");
        table.append("                    <tr>\n");
        table.append("                        <th>時間</th>\n");
        table.append("                        <th>商品</th>\n");
        table.append("                        <th>類型</th>\n");
        table.append("                        <th>數量</th>\n");
        table.append("                        <th>價格</th>\n");
        table.append("                        <th>金額</th>\n");
        table.append("                        <th>手續費</th>\n");
        table.append("                    </tr>\n");
        table.append("                </thead>\n");
        table.append("                <tbody>\n");
        
        for (Trade trade : trades) {
            String typeClass = trade.getType() == TradeType.BUY ? "buy" : "sell";
            table.append("                    <tr class='").append(typeClass).append("'>\n");
            table.append("                        <td>").append(trade.getTimestamp().format(DATE_FORMAT)).append("</td>\n");
            table.append("                        <td>").append(trade.getSymbol()).append("</td>\n");
            table.append("                        <td>").append(trade.getType().getDisplayName()).append("</td>\n");
            table.append("                        <td>").append(trade.getQuantity()).append("</td>\n");
            table.append("                        <td>$").append(String.format("%.2f", trade.getPrice())).append("</td>\n");
            table.append("                        <td>$").append(String.format("%.2f", trade.getTotalAmount())).append("</td>\n");
            table.append("                        <td>$").append(String.format("%.2f", trade.getCommissionAmount())).append("</td>\n");
            table.append("                    </tr>\n");
        }
        
        table.append("                </tbody>\n");
        table.append("            </table>\n");
        table.append("        </div>\n");
        
        return table.toString();
    }
    
    /**
     * 生成月度統計
     */
    private static String generateMonthlyStats(BacktestResult result) {
        StringBuilder stats = new StringBuilder();
        
        stats.append("        <div class='monthly-stats'>\n");
        stats.append("            <p>月度統計功能開發中...</p>\n");
        stats.append("            <p>將包含每月收益率、回撤、交易次數等詳細統計</p>\n");
        stats.append("        </div>\n");
        
        return stats.toString();
    }
    
    /**
     * 獲取 HTML 樣式
     */
    private static String getHtmlStyles() {
        return """
            body {
                font-family: 'Microsoft JhengHei', Arial, sans-serif;
                margin: 0;
                padding: 20px;
                background-color: #f5f5f5;
                color: #333;
            }
            
            .header {
                text-align: center;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 30px;
                border-radius: 10px;
                margin-bottom: 30px;
                box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            }
            
            .header h1 {
                margin: 0;
                font-size: 2.5em;
                font-weight: 300;
            }
            
            .header h2 {
                margin: 10px 0;
                font-size: 1.5em;
                opacity: 0.9;
            }
            
            .section {
                background: white;
                padding: 25px;
                margin-bottom: 25px;
                border-radius: 10px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            .section h3 {
                margin-top: 0;
                color: #2c3e50;
                border-bottom: 2px solid #3498db;
                padding-bottom: 10px;
            }
            
            .info-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 15px;
            }
            
            .info-item {
                display: flex;
                justify-content: space-between;
                padding: 10px;
                background: #f8f9fa;
                border-radius: 5px;
            }
            
            .label {
                font-weight: bold;
                color: #6c757d;
            }
            
            .value {
                color: #2c3e50;
            }
            
            .metrics-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 20px;
            }
            
            .metric-card {
                padding: 20px;
                border-radius: 10px;
                text-align: center;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            
            .metric-card.positive {
                background: linear-gradient(135deg, #28a745, #20c997);
                color: white;
            }
            
            .metric-card.negative {
                background: linear-gradient(135deg, #dc3545, #fd7e14);
                color: white;
            }
            
            .metric-card.neutral {
                background: linear-gradient(135deg, #6c757d, #adb5bd);
                color: white;
            }
            
            .metric-title {
                font-size: 0.9em;
                opacity: 0.9;
                margin-bottom: 10px;
            }
            
            .metric-value {
                font-size: 1.8em;
                font-weight: bold;
            }
            
            .table-container {
                overflow-x: auto;
            }
            
            .trade-table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 15px;
            }
            
            .trade-table th,
            .trade-table td {
                padding: 12px;
                text-align: left;
                border-bottom: 1px solid #dee2e6;
            }
            
            .trade-table th {
                background-color: #f8f9fa;
                font-weight: bold;
                color: #495057;
            }
            
            .trade-table tr.buy {
                background-color: #d4edda;
            }
            
            .trade-table tr.sell {
                background-color: #f8d7da;
            }
            
            .monthly-stats {
                text-align: center;
                padding: 40px;
                color: #6c757d;
                font-style: italic;
            }
            """;
    }
}
