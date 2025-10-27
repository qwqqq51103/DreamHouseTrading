package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.backtest.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

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
        String[] columnNames = {"時間", "商品", "類型", "數量", "價格", "金額", "手續費"};
        
        java.util.List<Trade> trades = result.getTrades();
        Object[][] data = new Object[trades.size()][7];
        
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            data[i][0] = trade.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
            data[i][1] = trade.getSymbol();
            data[i][2] = trade.getType().getDisplayName();
            data[i][3] = trade.getQuantity();
            data[i][4] = String.format("%.2f", trade.getPrice());
            data[i][5] = String.format("%.2f", trade.getTotalAmount());
            data[i][6] = String.format("%.2f", trade.getCommissionAmount());
        }
        
        JTable table = new JTable(data, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowHeight(25);
        
        // 設定行顏色和文字顏色
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                         boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    String type = (String) table.getValueAt(row, 2);
                    if ("買入".equals(type)) {
                        c.setBackground(new Color(200, 255, 200)); // 淺綠色
                        c.setForeground(new Color(0, 100, 0)); // 深綠色文字
                    } else if ("賣出".equals(type)) {
                        c.setBackground(new Color(255, 200, 200)); // 淺紅色
                        c.setForeground(new Color(150, 0, 0)); // 深紅色文字
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
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
