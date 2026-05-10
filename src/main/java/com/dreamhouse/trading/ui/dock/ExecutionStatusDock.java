package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.execution.ExecutionEngine;
import com.dreamhouse.trading.core.execution.ExecutionMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.backtest.Position;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 執行狀態面板
 * 顯示訂單執行狀態與歷史記錄
 */
public class ExecutionStatusDock extends JPanel {

    // 執行引擎
    private ExecutionEngine executionEngine;

    // 執行模式顯示
    private JLabel executionModeLabel;

    // 統計顯示
    private JLabel totalOrdersLabel;
    private JLabel successOrdersLabel;
    private JLabel failedOrdersLabel;
    private JLabel successRateLabel;
    private JProgressBar successRateBar;

    // 訂單歷史表格
    private DefaultTableModel tableModel;
    private JTable orderHistoryTable;
    private final Map<String, Double> latestPrices = new ConcurrentHashMap<>();

    // 最大顯示筆數
    private static final int MAX_DISPLAY_ORDERS = 50;

    // DateTimeFormatter
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    /**
     * 建構子
     */
    public ExecutionStatusDock() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(40, 40, 40));

        // 創建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(new Color(40, 40, 40));

        // 上方面板（模式 + 統計）
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(new Color(40, 40, 40));
        topPanel.add(createModePanel());
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(createStatisticsPanel());

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 下方面板（訂單歷史）
        mainPanel.add(createOrderHistoryPanel(), BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);

        // 初始化顯示
        updateDisplay();
    }

    /**
     * 創建執行模式面板
     */
    private JPanel createModePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "⚙ 執行模式",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label = new JLabel("當前模式：");
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        executionModeLabel = new JLabel("未初始化");
        executionModeLabel.setForeground(Color.WHITE);
        executionModeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        panel.add(executionModeLabel, gbc);

        return panel;
    }

    /**
     * 創建統計面板
     */
    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "📊 執行統計",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 總訂單數
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("總訂單數：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        totalOrdersLabel = new JLabel("0");
        totalOrdersLabel.setForeground(Color.WHITE);
        panel.add(totalOrdersLabel, gbc);

        // 成功訂單數
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel label2 = new JLabel("成功訂單：");
        label2.setForeground(Color.LIGHT_GRAY);
        panel.add(label2, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        successOrdersLabel = new JLabel("0");
        successOrdersLabel.setForeground(new Color(0, 255, 0));
        panel.add(successOrdersLabel, gbc);

        // 失敗訂單數
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        JLabel label3 = new JLabel("失敗訂單：");
        label3.setForeground(Color.LIGHT_GRAY);
        panel.add(label3, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        failedOrdersLabel = new JLabel("0");
        failedOrdersLabel.setForeground(new Color(255, 0, 0));
        panel.add(failedOrdersLabel, gbc);

        // 成功率
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.3;
        JLabel label4 = new JLabel("成功率：");
        label4.setForeground(Color.LIGHT_GRAY);
        panel.add(label4, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel ratePanel = new JPanel(new BorderLayout(5, 0));
        ratePanel.setBackground(new Color(50, 50, 50));
        successRateLabel = new JLabel("0%");
        successRateLabel.setForeground(Color.WHITE);
        successRateBar = new JProgressBar(0, 100);
        successRateBar.setStringPainted(false);
        successRateBar.setPreferredSize(new Dimension(150, 20));
        ratePanel.add(successRateLabel, BorderLayout.WEST);
        ratePanel.add(successRateBar, BorderLayout.CENTER);
        panel.add(ratePanel, gbc);

        return panel;
    }

    /**
     * 創建訂單歷史面板
     */
    private JPanel createOrderHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "📋 訂單歷史（最近 " + MAX_DISPLAY_ORDERS + " 筆）",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        // 創建表格模型
        String[] columnNames = {"狀態", "交易組", "時間", "商品", "動作", "類型", "數量", "價格", "停損", "停利", "損益", "訊息"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;  // 所有單元格不可編輯
            }
        };

        // 創建表格
        orderHistoryTable = new JTable(tableModel);
        orderHistoryTable.setBackground(new Color(40, 40, 40));
        orderHistoryTable.setForeground(Color.LIGHT_GRAY);
        orderHistoryTable.setGridColor(new Color(60, 60, 60));
        orderHistoryTable.setSelectionBackground(new Color(70, 130, 180));
        orderHistoryTable.setSelectionForeground(Color.WHITE);
        orderHistoryTable.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
        orderHistoryTable.getTableHeader().setBackground(new Color(60, 60, 60));
        orderHistoryTable.getTableHeader().setForeground(Color.WHITE);
        orderHistoryTable.getTableHeader().setFont(new Font("Microsoft JhengHei", Font.BOLD, 11));
        orderHistoryTable.setRowHeight(25);

        // 設定列寬
        orderHistoryTable.getColumnModel().getColumn(0).setPreferredWidth(60);   // 狀態
        orderHistoryTable.getColumnModel().getColumn(1).setPreferredWidth(80);   // 交易組
        orderHistoryTable.getColumnModel().getColumn(2).setPreferredWidth(120);  // 時間
        orderHistoryTable.getColumnModel().getColumn(3).setPreferredWidth(80);   // 商品
        orderHistoryTable.getColumnModel().getColumn(4).setPreferredWidth(60);   // 動作
        orderHistoryTable.getColumnModel().getColumn(5).setPreferredWidth(60);   // 類型
        orderHistoryTable.getColumnModel().getColumn(6).setPreferredWidth(60);   // 數量
        orderHistoryTable.getColumnModel().getColumn(7).setPreferredWidth(80);   // 價格
        orderHistoryTable.getColumnModel().getColumn(8).setPreferredWidth(80);   // 停損
        orderHistoryTable.getColumnModel().getColumn(9).setPreferredWidth(80);   // 停利
        orderHistoryTable.getColumnModel().getColumn(10).setPreferredWidth(80);  // 損益
        orderHistoryTable.getColumnModel().getColumn(11).setPreferredWidth(220); // 訊息

        // 自定義單元格渲染器（為狀態列添加顏色）
        orderHistoryTable.getColumnModel().getColumn(0).setCellRenderer(new StatusCellRenderer());
        TradeGroupCellRenderer tradeRenderer = new TradeGroupCellRenderer();
        for (int i = 1; i < orderHistoryTable.getColumnCount(); i++) {
            orderHistoryTable.getColumnModel().getColumn(i).setCellRenderer(tradeRenderer);
        }

        // 滾動面板
        JScrollPane scrollPane = new JScrollPane(orderHistoryTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 設定執行引擎
     */
    public void setExecutionEngine(ExecutionEngine engine) {
        this.executionEngine = engine;
        updateDisplay();
    }

    public void updateMarketPrice(String symbol, double price) {
        if (symbol != null && !symbol.isBlank() && price > 0.0) {
            latestPrices.put(symbol, price);
            updateDisplay();
        }
    }

    /**
     * 更新顯示
     */
    public void updateDisplay() {
        SwingUtilities.invokeLater(() -> {
            if (executionEngine == null) {
                executionModeLabel.setText("未初始化");
                executionModeLabel.setForeground(Color.GRAY);
                totalOrdersLabel.setText("0");
                successOrdersLabel.setText("0");
                failedOrdersLabel.setText("0");
                successRateLabel.setText("0%");
                successRateBar.setValue(0);
                tableModel.setRowCount(0);
            } else {
                // 更新執行模式
                ExecutionMode mode = executionEngine.getMode();
                executionModeLabel.setText(getModeDisplayName(mode));
                executionModeLabel.setForeground(getModeColor(mode));

                // 獲取執行歷史
                Map<String, ExecutionResult> history = executionEngine.getExecutionHistory();
                List<ExecutionResult> results = new ArrayList<>(history.values());
                results.sort(Comparator
                    .comparing(ExecutionResult::getExecutionTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ExecutionResult::getOrderId, Comparator.nullsLast(Comparator.naturalOrder())));

                // 計算統計
                long totalOrders = results.size();
                long successOrders = results.stream().filter(ExecutionResult::isSuccess).count();
                long failedOrders = results.stream().filter(ExecutionResult::isFailed).count();
                int successRate = totalOrders > 0 ? (int) ((successOrders * 100) / totalOrders) : 0;

                // 更新統計顯示
                totalOrdersLabel.setText(String.valueOf(totalOrders));
                successOrdersLabel.setText(String.valueOf(successOrders));
                failedOrdersLabel.setText(String.valueOf(failedOrders));
                successRateLabel.setText(successRate + "%");
                successRateBar.setValue(successRate);
                successRateBar.setForeground(getSuccessRateColor(successRate));

                // 更新訂單歷史表格
                updateOrderHistoryTable(results);
            }
        });
    }

    /**
     * 更新訂單歷史表格
     */
    private void updateOrderHistoryTable(List<ExecutionResult> results) {
        tableModel.setRowCount(0);

        // 只顯示最近的訂單（倒序）
        int startIndex = Math.max(0, results.size() - MAX_DISPLAY_ORDERS);
        for (int i = results.size() - 1; i >= startIndex; i--) {
            ExecutionResult result = results.get(i);

            String status = getStatusIcon(result.getStatus());
            String tradeId = result.getTradeId() != null && !result.getTradeId().isBlank()
                    ? result.getTradeId() : "--";
            String time = result.getExecutionTime() != null ?
                    result.getExecutionTime().format(TIME_FORMATTER) : "--";
            String symbol = result.getSymbol();
            String action = result.getAction() != null && !result.getAction().isBlank()
                    ? result.getAction() : "--";
            String type = result.getOrderType() != null ?
                    result.getOrderType().getShortCode() : "--";
            String quantity = String.valueOf(result.getExecutedQuantity());
            String price = String.format("%.2f", result.getExecutedPrice());
            String stopLoss = result.getStopLoss() != null ? String.format("%.2f", result.getStopLoss()) : "--";
            String takeProfit = result.getTakeProfit() != null ? String.format("%.2f", result.getTakeProfit()) : "--";
            String pnl = String.format("%.2f", calculateDisplayedPnL(result));
            String message = result.getMessage() != null ? result.getMessage() : "";

            tableModel.addRow(new Object[]{status, tradeId, time, symbol, action, type, quantity, price, stopLoss, takeProfit, pnl, message});
        }
    }

    private double calculateDisplayedPnL(ExecutionResult result) {
        if (executionEngine == null || result == null) {
            return 0.0;
        }
        if (!"開倉".equals(result.getAction())) {
            return result.getRealizedPnL();
        }

        Position position = executionEngine.getPortfolio().getPosition(result.getSymbol());
        Double latestPrice = latestPrices.get(result.getSymbol());
        if (position != null && latestPrice != null && latestPrice > 0.0) {
            return position.getUnrealizedPnL(latestPrice);
        }
        return result.getRealizedPnL();
    }

    // ==================== 輔助方法 ====================

    private String getModeDisplayName(ExecutionMode mode) {
        if (mode == null) return "未知";
        switch (mode) {
            case BACKTEST: return "📊 回測模式";
            case PAPER_TRADING: return "📝 模擬盤";
            case LIVE_TRADING: return "💰 實盤交易";
            case DRY_RUN: return "🧪 乾跑模式";
            default: return "未知";
        }
    }

    private Color getModeColor(ExecutionMode mode) {
        if (mode == null) return Color.GRAY;
        switch (mode) {
            case BACKTEST: return new Color(100, 149, 237);  // 藍色
            case PAPER_TRADING: return new Color(255, 215, 0);  // 金色
            case LIVE_TRADING: return new Color(255, 0, 0);  // 紅色
            case DRY_RUN: return new Color(128, 128, 128);  // 灰色
            default: return Color.GRAY;
        }
    }

    private String getStatusIcon(ExecutionResult.Status status) {
        switch (status) {
            case SUCCESS: return "✓";
            case FAILED: return "✗";
            case PARTIAL: return "◐";
            case PENDING: return "○";
            case CANCELLED: return "⊘";
            case REJECTED: return "⊗";
            default: return "?";
        }
    }

    private Color getSuccessRateColor(int rate) {
        if (rate >= 80) {
            return new Color(0, 200, 0);  // 綠色
        } else if (rate >= 60) {
            return new Color(144, 238, 144);  // 淺綠色
        } else if (rate >= 40) {
            return new Color(255, 215, 0);  // 黃色
        } else {
            return new Color(255, 100, 0);  // 橘色
        }
    }

    /**
     * 狀態單元格渲染器
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                Object tradeValue = table.getValueAt(row, 1);
                String tradeId = tradeValue != null ? tradeValue.toString() : "";
                c.setBackground(TradeGroupCellRenderer.resolveGroupColor(tradeId));
                String status = (String) value;
                if ("✓".equals(status)) {
                    c.setForeground(new Color(0, 255, 0));
                } else if ("✗".equals(status) || "⊗".equals(status)) {
                    c.setForeground(new Color(255, 0, 0));
                } else if ("◐".equals(status)) {
                    c.setForeground(new Color(255, 215, 0));
                } else if ("○".equals(status)) {
                    c.setForeground(new Color(135, 206, 250));
                } else if ("⊘".equals(status)) {
                    c.setForeground(Color.GRAY);
                } else {
                    c.setForeground(Color.LIGHT_GRAY);
                }
            }

            setHorizontalAlignment(CENTER);
            return c;
        }
    }

    private static class TradeGroupCellRenderer extends DefaultTableCellRenderer {
        private static final Color[] GROUP_COLORS = {
            new Color(46, 55, 64),
            new Color(55, 48, 64),
            new Color(48, 60, 50),
            new Color(62, 53, 43),
            new Color(50, 58, 66)
        };

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                Object tradeValue = table.getValueAt(row, 1);
                String tradeId = tradeValue != null ? tradeValue.toString() : "";
                c.setBackground(resolveGroupColor(tradeId));
                c.setForeground(Color.LIGHT_GRAY);
            }
            setHorizontalAlignment(column == 11 ? LEFT : CENTER);
            return c;
        }

        private static Color resolveGroupColor(String tradeId) {
            if (tradeId == null || tradeId.isBlank() || "--".equals(tradeId)) {
                return new Color(40, 40, 40);
            }
            int index = Math.abs(tradeId.hashCode()) % GROUP_COLORS.length;
            return GROUP_COLORS[index];
        }
    }
}
