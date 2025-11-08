package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.backtest.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Map;

/**
 * 回測結果對話框
 * 顯示詳細的回測結果和績效分析
 */
public class BacktestResultDialog extends JDialog {
    
    private final BacktestResult result;
    private final String strategyName;
    
    /**
     * 構造函數
     */
    public BacktestResultDialog(JFrame parent, BacktestResult result, String strategyName) {
        super(parent, "回測結果 - " + strategyName, true);
        this.result = result;
        this.strategyName = strategyName;
        
        initializeComponents();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(parent);
    }
    
    /**
     * 初始化組件
     */
    private void initializeComponents() {
        setLayout(new BorderLayout());
        
        // 創建選項卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 1. 概要標籤
        tabbedPane.addTab("概要", createSummaryPanel());
        
        // 2. 圖表標籤
        tabbedPane.addTab("圖表", PerformanceChart.createCombinedChartsPanel(result));
        
        // 3. 交易記錄標籤
        tabbedPane.addTab("交易記錄", createTradeHistoryPanel());
        
        // 4. 詳細報告標籤
        tabbedPane.addTab("詳細報告", createDetailedReportPanel());
        
        // 5. 統計分析標籤
        tabbedPane.addTab("統計分析", createStatisticsPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 底部按鈕面板
        add(createButtonPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * 創建概要面板
     */
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[grow]", ""));
        
        // 基本信息
        JPanel basicInfoPanel = createBasicInfoPanel();
        panel.add(basicInfoPanel, "wrap, growx");
        
        // 績效指標
        JPanel metricsPanel = createMetricsPanel();
        panel.add(metricsPanel, "wrap, growx");
        
        // 風險指標
        JPanel riskPanel = createRiskPanel();
        panel.add(riskPanel, "wrap, growx");
        
        JScrollPane scrollPane = new JScrollPane(panel);
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        return wrapperPanel;
    }
    
    /**
     * 創建基本信息面板
     */
    private JPanel createBasicInfoPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[right][fill][right][fill]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("基本信息"));
        
        panel.add(new JLabel("策略名稱:"), "");
        panel.add(new JLabel(strategyName), "");
        panel.add(new JLabel("回測期間:"), "");
        panel.add(new JLabel(String.format("%s 至 %s", 
                 result.getStartDate().toLocalDate(), 
                 result.getEndDate().toLocalDate())), "wrap");
        
        panel.add(new JLabel("初始資金:"), "");
        panel.add(new JLabel(String.format("$%.2f", result.getInitialCapital())), "");
        panel.add(new JLabel("最終價值:"), "");
        panel.add(new JLabel(String.format("$%.2f", result.getFinalValue())), "wrap");
        
        double absoluteReturn = result.getFinalValue() - result.getInitialCapital();
        panel.add(new JLabel("絕對收益:"), "");
        JLabel absoluteLabel = new JLabel(String.format("$%.2f", absoluteReturn));
        absoluteLabel.setForeground(absoluteReturn >= 0 ? new Color(0, 150, 0) : Color.RED);
        panel.add(absoluteLabel, "span 3, wrap");
        
        return panel;
    }
    
    /**
     * 創建績效指標面板
     */
    private JPanel createMetricsPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[right][fill][right][fill]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("績效指標"));
        
        // 收益指標
        panel.add(new JLabel("總收益率:"), "");
        JLabel totalReturnLabel = new JLabel(String.format("%.2f%%", result.getTotalReturn() * 100));
        totalReturnLabel.setForeground(result.getTotalReturn() >= 0 ? new Color(0, 150, 0) : Color.RED);
        panel.add(totalReturnLabel, "");
        
        panel.add(new JLabel("年化收益率:"), "");
        JLabel annualReturnLabel = new JLabel(String.format("%.2f%%", result.getAnnualizedReturn() * 100));
        annualReturnLabel.setForeground(result.getAnnualizedReturn() >= 0 ? new Color(0, 150, 0) : Color.RED);
        panel.add(annualReturnLabel, "wrap");
        
        // 風險指標
        panel.add(new JLabel("夏普比率:"), "");
        JLabel sharpeLabel = new JLabel(String.format("%.2f", result.getSharpeRatio()));
        sharpeLabel.setForeground(result.getSharpeRatio() >= 1.0 ? new Color(0, 150, 0) : 
                                 result.getSharpeRatio() >= 0 ? Color.ORANGE : Color.RED);
        panel.add(sharpeLabel, "");
        
        panel.add(new JLabel("波動率:"), "");
        panel.add(new JLabel(String.format("%.2f%%", result.getVolatility() * 100)), "wrap");
        
        return panel;
    }
    
    /**
     * 創建風險指標面板
     */
    private JPanel createRiskPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[right][fill][right][fill]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("風險與交易統計"));
        
        panel.add(new JLabel("最大回撤:"), "");
        JLabel drawdownLabel = new JLabel(String.format("%.2f%%", result.getMaxDrawdown() * 100));
        drawdownLabel.setForeground(Color.RED);
        panel.add(drawdownLabel, "");
        
        panel.add(new JLabel("總交易次數:"), "");
        panel.add(new JLabel(String.valueOf(result.getTotalTrades())), "wrap");
        
        panel.add(new JLabel("獲利交易:"), "");
        panel.add(new JLabel(String.valueOf(result.getWinningTrades())), "");
        
        panel.add(new JLabel("虧損交易:"), "");
        panel.add(new JLabel(String.valueOf(result.getLosingTrades())), "wrap");
        
        panel.add(new JLabel("勝率:"), "");
        JLabel winRateLabel = new JLabel(String.format("%.1f%%", result.getWinRate() * 100));
        winRateLabel.setForeground(result.getWinRate() >= 0.5 ? new Color(0, 150, 0) : Color.RED);
        panel.add(winRateLabel, "");
        
        panel.add(new JLabel("盈虧比:"), "");
        JLabel profitFactorLabel = new JLabel(String.format("%.2f", result.getProfitFactor()));
        profitFactorLabel.setForeground(result.getProfitFactor() >= 1.0 ? new Color(0, 150, 0) : Color.RED);
        panel.add(profitFactorLabel, "wrap");
        
        return panel;
    }
    
    /**
     * 創建交易記錄面板
     */
    private JPanel createTradeHistoryPanel() {
        String[] columnNames = {"時間", "商品", "類型", "數量", "價格", "金額", "手續費", "停損", "停利", "出場原因", "持倉狀態", "盈虧", "結果"};
        
        java.util.List<Trade> trades = result.getTrades();
        Object[][] data = new Object[trades.size()][13];  // 增加到13欄
        
        // 追蹤持倉和計算盈虧
        Trade lastBuy = null;
        int positionCount = 0;
        
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            data[i][0] = trade.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
            data[i][1] = trade.getSymbol();
            data[i][2] = trade.getType().getDisplayName();
            data[i][3] = trade.getQuantity();
            data[i][4] = String.format("%.2f", trade.getPrice());
            data[i][5] = String.format("%.2f", trade.getTotalAmount());
            data[i][6] = String.format("%.2f", trade.getCommissionAmount());
            
            // 停利停損資訊
            data[i][7] = trade.getStopLoss() != null ? String.format("%.2f", trade.getStopLoss()) : "-";
            data[i][8] = trade.getTakeProfit() != null ? String.format("%.2f", trade.getTakeProfit()) : "-";
            data[i][9] = trade.getExitReason() != null ? trade.getExitReason() : "-";
            
            // 計算持倉狀態、盈虧、結果
            if (trade.getType() == TradeType.BUY) {
                lastBuy = trade;
                positionCount++;
                data[i][10] = "持倉中 (" + positionCount + ")";
                data[i][11] = "-";  // 買入時沒有盈虧
                data[i][12] = "-";  // 買入時沒有結果
            } else if (trade.getType() == TradeType.SELL) {
                positionCount--;
                data[i][10] = positionCount > 0 ? "持倉中 (" + positionCount + ")" : "空倉";
                
                // 計算盈虧
                if (lastBuy != null) {
                    double buyTotal = lastBuy.getTotalCost();
                    double sellTotal = trade.getNetProceeds();
                    double profit = sellTotal - buyTotal;
                    
                    data[i][11] = String.format("%.2f", profit);
                    
                    // 判斷結果
                    if (profit > 0) {
                        data[i][12] = "獲利";
                    } else if (profit < 0) {
                        data[i][12] = "虧損";
                    } else {
                        data[i][12] = "持平";
                    }
                    
                    lastBuy = null;
                } else {
                    data[i][11] = "-";
                    data[i][12] = "-";
                }
            }
        }
        
        // 重新計算所有行的持倉狀態（確保每一行顯示該交易執行後的狀態）
        positionCount = 0;
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            if (trade.getType() == TradeType.BUY) {
                positionCount++;
            } else if (trade.getType() == TradeType.SELL) {
                positionCount--;
            }
            
            // 更新持倉狀態顯示（交易執行後的狀態）
            if (positionCount > 0) {
                data[i][10] = "持倉中 (" + positionCount + ")";
            } else {
                data[i][10] = "空倉";
            }
        }
        
        JTable table = new JTable(data, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowHeight(25);
        
        // 設定行顏色和文字顏色（根據交易類型和結果）
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                         boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    String type = (String) table.getValueAt(row, 2);
                    String result = (String) table.getValueAt(row, 12);  // 結果欄位 (調整為第12欄)
                    
                    // 根據交易類型設定背景色
                    if ("買入".equals(type)) {
                        c.setBackground(new Color(220, 255, 220)); // 淺綠色背景
                        c.setForeground(new Color(0, 100, 0)); // 深綠色文字
                    } else if ("賣出".equals(type)) {
                        // 根據結果設定不同的背景色
                        if ("獲利".equals(result)) {
                            c.setBackground(new Color(200, 255, 200)); // 亮綠色 - 獲利
                            c.setForeground(new Color(0, 120, 0));
                        } else if ("虧損".equals(result)) {
                            c.setBackground(new Color(255, 200, 200)); // 淺紅色 - 虧損
                            c.setForeground(new Color(150, 0, 0));
                        } else {
                            c.setBackground(new Color(255, 240, 200)); // 淺黃色 - 持平
                            c.setForeground(new Color(100, 100, 0));
                        }
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    }
                    
                    // 特殊欄位顏色
                    if (column == 11) { // 盈虧欄位 (調整為第11欄)
                        String profitStr = (String) value;
                        if (!"-".equals(profitStr)) {
                            try {
                                double profit = Double.parseDouble(profitStr);
                                if (profit > 0) {
                                    c.setForeground(new Color(0, 150, 0)); // 綠色
                                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                                } else if (profit < 0) {
                                    c.setForeground(Color.RED); // 紅色
                                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                                }
                            } catch (NumberFormatException e) {
                                // 忽略
                            }
                        }
                    }
                    
                    if (column == 12) { // 結果欄位 (調整為第12欄)
                        if ("獲利".equals(value)) {
                            c.setForeground(new Color(0, 150, 0));
                            setFont(getFont().deriveFont(java.awt.Font.BOLD));
                        } else if ("虧損".equals(value)) {
                            c.setForeground(Color.RED);
                            setFont(getFont().deriveFont(java.awt.Font.BOLD));
                        }
                    }
                } else {
                    c.setForeground(Color.WHITE); // 選中時文字為白色
                }
                
                return c;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        return wrapperPanel;
    }
    
    /**
     * 創建詳細報告面板
     */
    private JPanel createDetailedReportPanel() {
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setEditable(false);
        
        // 生成文本報告
        String report = ReportGenerator.generateTextReport(result, strategyName);
        textArea.setText(report);
        textArea.setCaretPosition(0);
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(800, 500));
        
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        return wrapperPanel;
    }
    
    /**
     * 創建按鈕面板
     */
    /**
     * 創建統計分析面板
     */
    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 生成統計分析
        TradeStatisticsAnalyzer analyzer = new TradeStatisticsAnalyzer(
            result.getTrades(), result);
        TradeStatisticsAnalyzer.StatisticsReport report = analyzer.generateReport();
        
        // 創建選項卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 1. 停損停利分析
        tabbedPane.addTab("停損停利", createStopLossAnalysisPanel(report));
        
        // 2. 出場原因分析
        tabbedPane.addTab("出場原因", createExitReasonPanel(report));
        
        // 3. 風險收益比分析
        tabbedPane.addTab("風險收益比", createRiskRewardPanel(report));
        
        // 4. 優化建議
        tabbedPane.addTab("優化建議", createOptimizationPanel(report));
        
        panel.add(tabbedPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 創建停損停利分析面板
     */
    private JPanel createStopLossAnalysisPanel(TradeStatisticsAnalyzer.StatisticsReport report) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 統計信息
        JPanel statsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("觸發率統計"));
        
        addStatRow(statsPanel, "完整交易對:", String.valueOf(report.completedPairs));
        addStatRow(statsPanel, "停損觸發次數:", 
            String.format("%d (%.1f%%)", report.stopLossTriggered, report.stopLossRate * 100));
        addStatRow(statsPanel, "停利觸發次數:", 
            String.format("%d (%.1f%%)", report.takeProfitTriggered, report.takeProfitRate * 100));
        addStatRow(statsPanel, "其他出場次數:", 
            String.format("%d (%.1f%%)", report.otherExits, report.otherExitRate * 100));
        
        // 平均盈虧
        if (!report.stopLossProfits.isEmpty()) {
            double avgStopLoss = report.stopLossProfits.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
            addStatRow(statsPanel, "停損平均虧損:", String.format("%.2f%%", avgStopLoss * 100));
        }
        
        if (!report.takeProfitProfits.isEmpty()) {
            double avgTakeProfit = report.takeProfitProfits.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
            addStatRow(statsPanel, "停利平均獲利:", String.format("%.2f%%", avgTakeProfit * 100));
        }
        
        panel.add(statsPanel, BorderLayout.NORTH);
        
        // 圖表區域（使用文字表示）
        JTextArea chartArea = new JTextArea();
        chartArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        chartArea.setEditable(false);
        
        StringBuilder chart = new StringBuilder();
        chart.append("\n觸發率分布圖:\n\n");
        
        int maxWidth = 50;
        int stopLossBar = (int) (report.stopLossRate * maxWidth);
        int takeProfitBar = (int) (report.takeProfitRate * maxWidth);
        int otherExitBar = (int) (report.otherExitRate * maxWidth);
        
        chart.append("停損觸發 [").append("█".repeat(Math.max(0, stopLossBar)))
             .append(" ".repeat(Math.max(0, maxWidth - stopLossBar)))
             .append(String.format("] %.1f%%\n", report.stopLossRate * 100));
        
        chart.append("停利觸發 [").append("█".repeat(Math.max(0, takeProfitBar)))
             .append(" ".repeat(Math.max(0, maxWidth - takeProfitBar)))
             .append(String.format("] %.1f%%\n", report.takeProfitRate * 100));
        
        chart.append("其他出場 [").append("█".repeat(Math.max(0, otherExitBar)))
             .append(" ".repeat(Math.max(0, maxWidth - otherExitBar)))
             .append(String.format("] %.1f%%\n", report.otherExitRate * 100));
        
        chartArea.setText(chart.toString());
        chartArea.setBackground(new Color(40, 40, 40));
        chartArea.setForeground(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(chartArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 創建出場原因面板
     */
    private JPanel createExitReasonPanel(TradeStatisticsAnalyzer.StatisticsReport report) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 創建表格
        String[] columnNames = {"出場原因", "次數", "比例", "平均盈虧"};
        Object[][] data = new Object[report.exitReasonDistribution.size()][4];
        
        int row = 0;
        for (Map.Entry<String, Integer> entry : report.exitReasonDistribution.entrySet()) {
            String reason = entry.getKey();
            int count = entry.getValue();
            double ratio = (double) count / report.completedPairs * 100;
            double avgProfit = report.exitReasonProfits.getOrDefault(reason, 0.0) / count;
            
            data[row][0] = reason;
            data[row][1] = count;
            data[row][2] = String.format("%.1f%%", ratio);
            data[row][3] = String.format("%.2f", avgProfit);
            row++;
        }
        
        JTable table = new JTable(data, columnNames);
        table.setRowHeight(25);
        table.setFont(new Font("微軟正黑體", Font.PLAIN, 12));
        
        // 設置列寬
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 創建風險收益比面板
     */
    private JPanel createRiskRewardPanel(TradeStatisticsAnalyzer.StatisticsReport report) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 統計信息
        JPanel statsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        statsPanel.setBorder(BorderFactory.createTitledBorder("風險收益比統計"));
        
        addStatRow(statsPanel, "計劃風險收益比:", 
            String.format("%.2f : 1", report.avgPlannedRiskReward));
        addStatRow(statsPanel, "實際風險收益比:", 
            String.format("%.2f : 1", report.avgActualRiskReward));
        
        double efficiency = report.avgPlannedRiskReward > 0 ? 
            (report.avgActualRiskReward / report.avgPlannedRiskReward * 100) : 0;
        addStatRow(statsPanel, "執行效率:", String.format("%.1f%%", efficiency));
        
        panel.add(statsPanel, BorderLayout.NORTH);
        
        // 詳細說明
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        StringBuilder text = new StringBuilder();
        text.append("【風險收益比說明】\n\n");
        text.append("計劃風險收益比 = (停利價格 - 入場價格) / (入場價格 - 停損價格)\n");
        text.append("實際風險收益比 = (實際盈虧) / (入場價格 - 停損價格)\n\n");
        text.append("執行效率 = 實際風險收益比 / 計劃風險收益比 × 100%\n\n");
        
        text.append("【評估標準】\n\n");
        text.append("優秀: 風險收益比 ≥ 2.0\n");
        text.append("良好: 風險收益比在 1.5 ~ 2.0 之間\n");
        text.append("普通: 風險收益比在 1.0 ~ 1.5 之間\n");
        text.append("較差: 風險收益比 < 1.0\n\n");
        
        text.append("【當前評估】\n\n");
        if (report.avgPlannedRiskReward >= 2.0) {
            text.append("✓ 計劃風險收益比優秀，風險管理策略合理\n");
        } else if (report.avgPlannedRiskReward >= 1.5) {
            text.append("✓ 計劃風險收益比良好\n");
        } else if (report.avgPlannedRiskReward >= 1.0) {
            text.append("⚠ 計劃風險收益比普通，建議提高停利目標\n");
        } else {
            text.append("⚠ 計劃風險收益比較差，急需調整停損停利設定\n");
        }
        
        if (efficiency >= 80) {
            text.append("✓ 執行效率高，策略執行良好\n");
        } else if (efficiency >= 50) {
            text.append("⚠ 執行效率中等，有優化空間\n");
        } else {
            text.append("⚠ 執行效率偏低，建議檢討出場邏輯\n");
        }
        
        textArea.setText(text.toString());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 創建優化建議面板
     */
    private JPanel createOptimizationPanel(TradeStatisticsAnalyzer.StatisticsReport report) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextArea textArea = new JTextArea();
        textArea.setFont(new Font("微軟正黑體", Font.PLAIN, 14));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        StringBuilder text = new StringBuilder();
        text.append("【策略優化建議】\n\n");
        
        for (int i = 0; i < report.optimizationSuggestions.size(); i++) {
            text.append((i + 1)).append(". ");
            text.append(report.optimizationSuggestions.get(i));
            text.append("\n\n");
        }
        
        textArea.setText(text.toString());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 添加統計行
     */
    private void addStatRow(JPanel panel, String label, String value) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD));
        panel.add(labelComp);
        
        JLabel valueComp = new JLabel(value);
        panel.add(valueComp);
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton exportHtmlButton = new JButton("匯出 HTML 報告");
        JButton exportTextButton = new JButton("匯出文字報告");
        JButton closeButton = new JButton("關閉");
        
        exportHtmlButton.addActionListener(e -> exportReport("html"));
        exportTextButton.addActionListener(e -> exportReport("txt"));
        closeButton.addActionListener(e -> dispose());
        
        panel.add(exportHtmlButton);
        panel.add(exportTextButton);
        panel.add(closeButton);
        
        return panel;
    }
    
    /**
     * 匯出報告
     */
    private void exportReport(String format) {
        try {
            String content;
            if ("html".equals(format)) {
                content = ReportGenerator.generateHtmlReport(result, strategyName);
            } else {
                content = ReportGenerator.generateTextReport(result, strategyName);
            }
            
            ReportGenerator.saveReportToFile(content, strategyName, format);
            
            JOptionPane.showMessageDialog(this, 
                "報告已成功匯出！", 
                "匯出成功", 
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, 
                "匯出報告時發生錯誤:\n" + ex.getMessage(), 
                "匯出錯誤", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
