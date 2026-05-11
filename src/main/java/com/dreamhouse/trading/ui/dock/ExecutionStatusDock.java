package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.execution.ExecutionEngine;
import com.dreamhouse.trading.core.execution.ExecutionMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderStatus;
import com.dreamhouse.trading.core.execution.OrderType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionStatusDock extends JPanel {

    private static final int MAX_DISPLAY_ORDERS = 50;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private ExecutionEngine executionEngine;
    private JLabel executionModeLabel;
    private JLabel totalOrdersLabel;
    private JLabel successOrdersLabel;
    private JLabel failedOrdersLabel;
    private JLabel successRateLabel;
    private JProgressBar successRateBar;
    private DefaultTableModel tableModel;
    private final Map<String, Double> markPrices = new HashMap<>();

    public ExecutionStatusDock() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(40, 40, 40));

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(new Color(40, 40, 40));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(new Color(40, 40, 40));
        topPanel.add(createModePanel());
        topPanel.add(Box.createVerticalStrut(10));
        topPanel.add(createStatisticsPanel());

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(createOrderHistoryPanel(), BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        updateDisplay();
    }

    private JPanel createModePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("執行模式"));

        GridBagConstraints gbc = createConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label = new JLabel("目前模式：");
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        executionModeLabel = new JLabel("尚未啟動");
        executionModeLabel.setForeground(Color.WHITE);
        executionModeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        panel.add(executionModeLabel, gbc);

        return panel;
    }

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("執行統計"));

        GridBagConstraints gbc = createConstraints();
        addStatRow(panel, gbc, 0, "總委託數", totalOrdersLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 1, "成功筆數", successOrdersLabel = createValueLabel(new Color(80, 220, 120)));
        addStatRow(panel, gbc, 2, "失敗筆數", failedOrdersLabel = createValueLabel(new Color(255, 110, 110)));

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.3;
        JLabel rateLabel = new JLabel("成功率：");
        rateLabel.setForeground(Color.LIGHT_GRAY);
        panel.add(rateLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel ratePanel = new JPanel(new BorderLayout(5, 0));
        ratePanel.setBackground(new Color(50, 50, 50));
        successRateLabel = createValueLabel(Color.WHITE);
        successRateBar = new JProgressBar(0, 100);
        successRateBar.setStringPainted(false);
        successRateBar.setPreferredSize(new Dimension(150, 20));
        ratePanel.add(successRateLabel, BorderLayout.WEST);
        ratePanel.add(successRateBar, BorderLayout.CENTER);
        panel.add(ratePanel, gbc);

        return panel;
    }

    private JPanel createOrderHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("訂單歷史（最近 " + MAX_DISPLAY_ORDERS + " 筆）"));

        String[] columnNames = {
                "狀態", "配對", "時間", "商品", "動作", "類型", "數量",
                "委託價", "成交價", "停損", "停利", "手續費", "損益", "訊息", "策略理由"
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable orderHistoryTable = new JTable(tableModel);
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
        orderHistoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        configureColumns(orderHistoryTable);
        orderHistoryTable.setDefaultRenderer(Object.class, new ExecutionRowRenderer());

        JScrollPane scrollPane = new JScrollPane(orderHistoryTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void configureColumns(JTable table) {
        int[] widths = {54, 130, 108, 82, 72, 56, 70, 82, 82, 82, 82, 86, 90, 190, 360};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    public void setExecutionEngine(ExecutionEngine engine) {
        this.executionEngine = engine;
        updateDisplay();
    }

    public void updateMarketPrice(String symbol, double price) {
        if (symbol == null || symbol.isBlank() || price <= 0.0) {
            return;
        }
        markPrices.put(symbol, price);
        updateDisplay();
    }

    public void updateMarketPrices(Map<String, Double> prices) {
        if (prices == null || prices.isEmpty()) {
            return;
        }
        prices.forEach((symbol, price) -> {
            if (symbol != null && !symbol.isBlank() && price != null && price > 0.0) {
                markPrices.put(symbol, price);
            }
        });
        updateDisplay();
    }

    public void updateDisplay() {
        SwingUtilities.invokeLater(() -> {
            if (executionEngine == null) {
                executionModeLabel.setText("尚未啟動");
                executionModeLabel.setForeground(Color.GRAY);
                totalOrdersLabel.setText("0");
                successOrdersLabel.setText("0");
                failedOrdersLabel.setText("0");
                successRateLabel.setText("0%");
                successRateBar.setValue(0);
                tableModel.setRowCount(0);
                return;
            }

            ExecutionMode mode = executionEngine.getMode();
            executionModeLabel.setText(mode.getDisplayName());
            executionModeLabel.setForeground(getModeColor(mode));

            Map<String, ExecutionResult> history = executionEngine.getExecutionHistory();
            List<ExecutionResult> results = new ArrayList<>(history.values());
            results.sort(Comparator.comparing(
                    ExecutionResult::getExecutionTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            long totalOrders = results.size();
            long successOrders = results.stream().filter(ExecutionResult::isSuccess).count();
            long failedOrders = results.stream().filter(result -> !result.isSuccess()).count();
            int successRate = totalOrders > 0 ? (int) ((successOrders * 100) / totalOrders) : 0;

            totalOrdersLabel.setText(String.valueOf(totalOrders));
            successOrdersLabel.setText(String.valueOf(successOrders));
            failedOrdersLabel.setText(String.valueOf(failedOrders));
            successRateLabel.setText(successRate + "%");
            successRateBar.setValue(successRate);
            successRateBar.setForeground(getSuccessRateColor(successRate));

            updateOrderHistoryTable(results);
        });
    }

    private void updateOrderHistoryTable(List<ExecutionResult> results) {
        tableModel.setRowCount(0);
        int startIndex = Math.max(0, results.size() - MAX_DISPLAY_ORDERS);
        for (int i = results.size() - 1; i >= startIndex; i--) {
            ExecutionResult result = results.get(i);
            tableModel.addRow(new Object[]{
                    getStatusText(result),
                    valueOrDash(result.getPositionId()),
                    formatTime(result),
                    valueOrDash(result.getSymbol()),
                    getSideText(result.getOrderSide()),
                    getOrderTypeText(result.getOrderType()),
                    formatQuantity(result.getExecutedQuantity(), result.getRequestedQuantity()),
                    formatPrice(result.getRequestedPrice()),
                    formatPrice(result.getExecutedPrice()),
                    formatNullablePrice(result.getStopLoss()),
                    formatNullablePrice(result.getTakeProfit()),
                    formatMoney(result.getCommission()),
                    formatPnL(calculateDisplayPnL(result)),
                    valueOrDash(toChineseMessage(result)),
                    valueOrDash(result.getDecisionReason())
            });
        }
    }

    private String getStatusText(ExecutionResult result) {
        if (result.getOrderStatus() == OrderStatus.REJECTED || result.isFailed()) {
            return "失敗";
        }
        return switch (result.getStatus()) {
            case SUCCESS -> "成功";
            case FAILED -> "失敗";
            case PARTIAL -> "部分成交";
            case PENDING -> "等待中";
            case CANCELLED -> "已取消";
            case REJECTED -> "已拒絕";
        };
    }

    private String getSideText(OrderSide side) {
        if (side == null) {
            return "--";
        }
        return switch (side) {
            case BUY -> "開倉";
            case SELL -> "平倉";
            case SHORT -> "放空";
            case COVER -> "回補";
        };
    }

    private String getOrderTypeText(OrderType type) {
        return type != null ? type.getDisplayName() : "--";
    }

    private String formatTime(ExecutionResult result) {
        return result.getExecutionTime() != null ? result.getExecutionTime().format(TIME_FORMATTER) : "--";
    }

    private String formatQuantity(int executedQuantity, int requestedQuantity) {
        if (executedQuantity == requestedQuantity || requestedQuantity <= 0) {
            return String.valueOf(executedQuantity);
        }
        return executedQuantity + "/" + requestedQuantity;
    }

    private String formatPrice(double price) {
        return price > 0.0 ? String.format("%.2f", price) : "--";
    }

    private String formatNullablePrice(Double price) {
        return price != null && price > 0.0 ? String.format("%.2f", price) : "--";
    }

    private String formatMoney(double value) {
        return String.format("%.2f", value);
    }

    private String formatPnL(double value) {
        if (Math.abs(value) < 0.005) {
            return "0.00";
        }
        return String.format("%+.2f", value);
    }

    private double calculateDisplayPnL(ExecutionResult result) {
        if (result.getOrderSide() != OrderSide.BUY || !result.isSuccess()) {
            return result.getRealizedPnL();
        }
        Double markPrice = markPrices.get(result.getSymbol());
        if (markPrice == null || markPrice <= 0.0 || result.getExecutedPrice() <= 0.0) {
            return result.getRealizedPnL();
        }
        return (markPrice - result.getExecutedPrice()) * result.getExecutedQuantity() - result.getCommission();
    }

    private String toChineseMessage(ExecutionResult result) {
        String message = valueOrDash(result.getMessage());
        if (!"--".equals(message) && !isKnownEnglishMessage(message)) {
            return message;
        }
        if (result.isFailed()) {
            return translateFailureMessage(message);
        }
        if (result.getOrderSide() == OrderSide.BUY) {
            return "開倉成功";
        }
        if (result.getOrderSide() == OrderSide.SELL) {
            return String.format("平倉成功，損益 %s", formatPnL(calculateDisplayPnL(result)));
        }
        return message;
    }

    private boolean isKnownEnglishMessage(String message) {
        return message.contains("Opened")
                || message.contains("Closed")
                || message.contains("Required cash")
                || message.contains("No position")
                || message.contains("Short selling")
                || message.contains("Decision")
                || message.contains("quantity")
                || message.contains("Live trading")
                || message.contains("Dry-run");
    }

    private String translateFailureMessage(String message) {
        if (message.contains("Required cash")) {
            return "資金不足，委託已拒絕";
        }
        if (message.contains("No position")) {
            return "沒有可平倉部位";
        }
        if (message.contains("Short selling")) {
            return "目前版本不支援放空";
        }
        if (message.contains("quantity")) {
            return "委託數量不合法";
        }
        if (message.contains("Decision")) {
            return "策略決策不可執行";
        }
        if (message.contains("Live trading")) {
            return "真實下單尚未支援";
        }
        return "--".equals(message) ? "委託失敗" : message;
    }

    private TitledBorder createBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE);
    }

    private GridBagConstraints createConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private void addStatRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, JLabel valueLabel) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel label = new JLabel(labelText + "：");
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        panel.add(valueLabel, gbc);
    }

    private JLabel createValueLabel(Color color) {
        JLabel label = new JLabel("0");
        label.setForeground(color);
        return label;
    }

    private Color getModeColor(ExecutionMode mode) {
        return switch (mode) {
            case BACKTEST -> new Color(100, 149, 237);
            case PAPER_TRADING -> new Color(255, 215, 0);
            case LIVE_TRADING -> new Color(255, 90, 90);
            case DRY_RUN -> new Color(160, 160, 160);
        };
    }

    private Color getSuccessRateColor(int rate) {
        if (rate >= 80) {
            return new Color(0, 200, 0);
        }
        if (rate >= 60) {
            return new Color(144, 238, 144);
        }
        if (rate >= 40) {
            return new Color(255, 215, 0);
        }
        return new Color(255, 100, 0);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }

    private static class ExecutionRowRenderer extends DefaultTableCellRenderer {
        private static final Color DEFAULT_BG = new Color(40, 40, 40);
        private static final Color OPEN_BG = new Color(34, 56, 43);
        private static final Color CLOSE_BG = new Color(58, 43, 34);
        private static final Color FAILED_BG = new Color(64, 34, 34);
        private static final Color SELECTED_BG = new Color(70, 130, 180);
        private static final Color POSITIVE = new Color(90, 230, 130);
        private static final Color NEGATIVE = new Color(255, 120, 120);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = String.valueOf(table.getValueAt(row, 0));
            String side = String.valueOf(table.getValueAt(row, 4));

            if (isSelected) {
                component.setBackground(SELECTED_BG);
                component.setForeground(Color.WHITE);
            } else {
                if ("失敗".equals(status) || "已拒絕".equals(status)) {
                    component.setBackground(FAILED_BG);
                } else if ("開倉".equals(side)) {
                    component.setBackground(OPEN_BG);
                } else if ("平倉".equals(side)) {
                    component.setBackground(CLOSE_BG);
                } else {
                    component.setBackground(DEFAULT_BG);
                }

                if (column == 12 && value instanceof String text && text.startsWith("+")) {
                    component.setForeground(POSITIVE);
                } else if (column == 12 && value instanceof String text && text.startsWith("-")) {
                    component.setForeground(NEGATIVE);
                } else if ("開倉".equals(side) && column == 4) {
                    component.setForeground(POSITIVE);
                } else if ("平倉".equals(side) && column == 4) {
                    component.setForeground(new Color(255, 190, 110));
                } else if ("失敗".equals(status) || "已拒絕".equals(status)) {
                    component.setForeground(NEGATIVE);
                } else {
                    component.setForeground(Color.LIGHT_GRAY);
                }
            }

            setHorizontalAlignment(column >= 6 && column <= 12 ? RIGHT : CENTER);
            if (column == 13 || column == 14) {
                setHorizontalAlignment(LEFT);
            }
            return component;
        }
    }
}
