package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.backtest.Position;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExecutionStatusDock extends JPanel {

    private static final int MAX_DISPLAY_TRADES = 50;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private ExecutionEngine executionEngine;
    private JLabel executionModeLabel;
    private JLabel totalOrdersLabel;
    private JLabel successOrdersLabel;
    private JLabel failedOrdersLabel;
    private JLabel successRateLabel;
    private JLabel cashLabel;
    private JLabel positionValueLabel;
    private JLabel totalEquityLabel;
    private JLabel realizedPnLLabel;
    private JLabel unrealizedPnLLabel;
    private JLabel totalPnLLabel;
    private JLabel commissionLabel;
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
        mainPanel.add(createTradeHistoryPanel(), BorderLayout.CENTER);
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
        addStatRow(panel, gbc, 0, "訂單總數", totalOrdersLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 1, "成功訂單", successOrdersLabel = createValueLabel(new Color(80, 220, 120)));
        addStatRow(panel, gbc, 2, "失敗訂單", failedOrdersLabel = createValueLabel(new Color(255, 110, 110)));

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

        addStatRow(panel, gbc, 4, "可用現金", cashLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 5, "持倉市值", positionValueLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 6, "總資產估值", totalEquityLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 7, "已實現損益", realizedPnLLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 8, "未實現損益", unrealizedPnLLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 9, "總損益", totalPnLLabel = createValueLabel(Color.WHITE));
        addStatRow(panel, gbc, 10, "累計手續費", commissionLabel = createValueLabel(Color.LIGHT_GRAY));

        return panel;
    }

    private JPanel createTradeHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("交易紀錄（最近 " + MAX_DISPLAY_TRADES + " 筆）"));

        String[] columnNames = {
                "狀態", "交易ID", "商品", "方向", "開倉時間", "平倉時間", "數量",
                "進場價", "出場/現價", "停損", "停利", "損益", "報酬率", "平倉原因", "原始理由"
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable tradeHistoryTable = new JTable(tableModel);
        tradeHistoryTable.setBackground(new Color(40, 40, 40));
        tradeHistoryTable.setForeground(Color.LIGHT_GRAY);
        tradeHistoryTable.setGridColor(new Color(60, 60, 60));
        tradeHistoryTable.setSelectionBackground(new Color(70, 130, 180));
        tradeHistoryTable.setSelectionForeground(Color.WHITE);
        tradeHistoryTable.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 11));
        tradeHistoryTable.getTableHeader().setBackground(new Color(60, 60, 60));
        tradeHistoryTable.getTableHeader().setForeground(Color.WHITE);
        tradeHistoryTable.getTableHeader().setFont(new Font("Microsoft JhengHei", Font.BOLD, 11));
        tradeHistoryTable.setRowHeight(25);
        tradeHistoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        configureColumns(tradeHistoryTable);
        tradeHistoryTable.setDefaultRenderer(Object.class, new TradeRowRenderer());

        JScrollPane scrollPane = new JScrollPane(tradeHistoryTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void configureColumns(JTable table) {
        int[] widths = {74, 142, 86, 60, 116, 116, 56, 76, 76, 76, 76, 92, 76, 190, 360};
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
                resetPortfolioStats();
                tableModel.setRowCount(0);
                return;
            }

            ExecutionMode mode = executionEngine.getMode();
            executionModeLabel.setText(mode.getDisplayName());
            executionModeLabel.setForeground(getModeColor(mode));

            List<ExecutionResult> results = getSortedExecutionResults();
            long totalOrders = results.size();
            long successOrders = results.stream().filter(ExecutionResult::isSuccess).count();
            long failedOrders = results.stream().filter(ExecutionResult::isFailed).count();
            int successRate = totalOrders > 0 ? (int) ((successOrders * 100) / totalOrders) : 0;

            totalOrdersLabel.setText(String.valueOf(totalOrders));
            successOrdersLabel.setText(String.valueOf(successOrders));
            failedOrdersLabel.setText(String.valueOf(failedOrders));
            successRateLabel.setText(successRate + "%");
            successRateBar.setValue(successRate);
            successRateBar.setForeground(getSuccessRateColor(successRate));
            updatePortfolioStats();

            updateTradeHistoryTable(buildTradeLifecycleRows(results));
        });
    }

    private List<ExecutionResult> getSortedExecutionResults() {
        Map<String, ExecutionResult> history = executionEngine.getExecutionHistory();
        List<ExecutionResult> results = new ArrayList<>(history.values());
        results.sort(Comparator.comparing(
                ExecutionResult::getExecutionTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return results;
    }

    private List<TradeLifecycleRow> buildTradeLifecycleRows(List<ExecutionResult> results) {
        Map<String, TradeLifecycleRow> trades = new LinkedHashMap<>();
        List<TradeLifecycleRow> standaloneRows = new ArrayList<>();

        for (ExecutionResult result : results) {
            if (!result.isSuccess()) {
                standaloneRows.add(TradeLifecycleRow.rejected(result));
                continue;
            }

            if (result.getOrderSide() == OrderSide.BUY) {
                String tradeId = nonBlank(result.getPositionId(), result.getOrderId());
                trades.computeIfAbsent(tradeId, id -> new TradeLifecycleRow(id)).applyOpen(result);
            } else if (result.getOrderSide() == OrderSide.SELL) {
                String tradeId = nonBlank(result.getPositionId(), result.getOrderId());
                trades.computeIfAbsent(tradeId, id -> new TradeLifecycleRow(id)).applyClose(result);
            } else {
                standaloneRows.add(TradeLifecycleRow.rejected(result));
            }
        }

        List<TradeLifecycleRow> rows = new ArrayList<>(trades.values());
        rows.addAll(standaloneRows);
        rows.sort(Comparator.comparing(TradeLifecycleRow::getSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return rows;
    }

    private void updatePortfolioStats() {
        if (executionEngine == null || executionEngine.getPortfolio() == null) {
            resetPortfolioStats();
            return;
        }

        Map<String, Double> effectiveMarkPrices = new HashMap<>();
        double positionValue = 0.0;
        for (Position position : executionEngine.getPortfolio().getPositions()) {
            double markPrice = markPrices.getOrDefault(position.getSymbol(), position.getAveragePrice());
            effectiveMarkPrices.put(position.getSymbol(), markPrice);
            positionValue += position.getQuantity() * markPrice;
        }

        double cash = executionEngine.getPortfolio().getCash();
        double totalEquity = cash + positionValue;
        double realizedPnL = executionEngine.getPortfolio().getRealizedPnL();
        double unrealizedPnL = executionEngine.getPortfolio().getUnrealizedPnL(effectiveMarkPrices);
        double totalPnL = totalEquity - executionEngine.getPortfolio().getInitialCash();
        double commission = executionEngine.getPortfolio().getTotalCommission();

        cashLabel.setText(formatMoney(cash));
        positionValueLabel.setText(formatMoney(positionValue));
        totalEquityLabel.setText(formatMoney(totalEquity));
        realizedPnLLabel.setText(formatPnL(realizedPnL));
        unrealizedPnLLabel.setText(formatPnL(unrealizedPnL));
        totalPnLLabel.setText(formatPnL(totalPnL));
        commissionLabel.setText(formatMoney(commission));

        realizedPnLLabel.setForeground(getPnLColor(realizedPnL));
        unrealizedPnLLabel.setForeground(getPnLColor(unrealizedPnL));
        totalPnLLabel.setForeground(getPnLColor(totalPnL));
    }

    private void resetPortfolioStats() {
        cashLabel.setText("0.00");
        positionValueLabel.setText("0.00");
        totalEquityLabel.setText("0.00");
        realizedPnLLabel.setText("0.00");
        unrealizedPnLLabel.setText("0.00");
        totalPnLLabel.setText("0.00");
        commissionLabel.setText("0.00");
        realizedPnLLabel.setForeground(Color.WHITE);
        unrealizedPnLLabel.setForeground(Color.WHITE);
        totalPnLLabel.setForeground(Color.WHITE);
    }

    private void updateTradeHistoryTable(List<TradeLifecycleRow> rows) {
        tableModel.setRowCount(0);
        int endIndex = Math.min(rows.size(), MAX_DISPLAY_TRADES);
        for (int i = 0; i < endIndex; i++) {
            TradeLifecycleRow row = rows.get(i);
            double displayPnL = row.calculateDisplayPnL(markPrices);
            tableModel.addRow(new Object[]{
                    row.getStatusText(),
                    row.tradeId,
                    valueOrDash(row.symbol),
                    row.getDirectionText(),
                    formatTime(row.openTime),
                    formatTime(row.closeTime),
                    row.quantity > 0 ? String.valueOf(row.quantity) : "--",
                    formatPrice(row.entryPrice),
                    row.getExitOrMarkPriceText(markPrices),
                    formatNullablePrice(row.stopLoss),
                    formatNullablePrice(row.takeProfit),
                    formatPnL(displayPnL),
                    formatPercent(row.calculateReturnRate(markPrices)),
                    valueOrDash(row.getExitMessage(displayPnL)),
                    valueOrDash(row.reason)
            });
        }
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(TIME_FORMATTER) : "--";
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

    private String formatPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) < 0.00005) {
            return "0.00%";
        }
        return String.format("%+.2f%%", value * 100.0);
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

    private Color getPnLColor(double value) {
        if (value > 0.005) {
            return new Color(90, 230, 130);
        }
        if (value < -0.005) {
            return new Color(255, 120, 120);
        }
        return Color.WHITE;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static class TradeLifecycleRow {
        private final String tradeId;
        private String symbol;
        private int quantity;
        private double entryPrice;
        private double exitPrice;
        private Double stopLoss;
        private Double takeProfit;
        private double realizedPnL;
        private double totalCommission;
        private LocalDateTime openTime;
        private LocalDateTime closeTime;
        private String reason;
        private String closeReason;
        private String failedMessage;
        private boolean rejected;

        private TradeLifecycleRow(String tradeId) {
            this.tradeId = tradeId;
        }

        private static TradeLifecycleRow rejected(ExecutionResult result) {
            TradeLifecycleRow row = new TradeLifecycleRow(nonBlankStatic(result.getPositionId(), result.getOrderId()));
            row.symbol = result.getSymbol();
            row.quantity = result.getRequestedQuantity();
            row.entryPrice = result.getRequestedPrice();
            row.exitPrice = result.getExecutedPrice();
            row.stopLoss = result.getStopLoss();
            row.takeProfit = result.getTakeProfit();
            row.totalCommission = result.getCommission();
            row.openTime = result.getExecutionTime();
            row.reason = result.getDecisionReason();
            row.failedMessage = translateFailureMessage(result.getMessage());
            row.rejected = true;
            return row;
        }

        private void applyOpen(ExecutionResult result) {
            symbol = result.getSymbol();
            quantity = result.getExecutedQuantity();
            entryPrice = result.getExecutedPrice();
            stopLoss = result.getStopLoss();
            takeProfit = result.getTakeProfit();
            totalCommission += result.getCommission();
            openTime = result.getExecutionTime();
            reason = result.getDecisionReason();
        }

        private void applyClose(ExecutionResult result) {
            if (symbol == null || symbol.isBlank()) {
                symbol = result.getSymbol();
            }
            if (quantity <= 0) {
                quantity = result.getExecutedQuantity();
            }
            exitPrice = result.getExecutedPrice();
            stopLoss = result.getStopLoss() != null ? result.getStopLoss() : stopLoss;
            takeProfit = result.getTakeProfit() != null ? result.getTakeProfit() : takeProfit;
            realizedPnL += result.getRealizedPnL();
            totalCommission += result.getCommission();
            closeTime = result.getExecutionTime();
            closeReason = result.getDecisionReason();
            if (reason == null || reason.isBlank()) {
                reason = result.getDecisionReason();
            }
        }

        private String getStatusText() {
            if (rejected) {
                return "失敗";
            }
            return closeTime != null ? "已平倉" : "持倉中";
        }

        private String getDirectionText() {
            return "做多";
        }

        private LocalDateTime getSortTime() {
            if (closeTime != null) {
                return closeTime;
            }
            return openTime;
        }

        private double calculateDisplayPnL(Map<String, Double> markPrices) {
            if (rejected) {
                return 0.0;
            }
            if (closeTime != null) {
                return realizedPnL;
            }
            Double markPrice = markPrices.get(symbol);
            if (markPrice == null || markPrice <= 0.0 || entryPrice <= 0.0 || quantity <= 0) {
                return 0.0;
            }
            return (markPrice - entryPrice) * quantity - totalCommission;
        }

        private double calculateReturnRate(Map<String, Double> markPrices) {
            if (entryPrice <= 0.0 || quantity <= 0) {
                return 0.0;
            }
            return calculateDisplayPnL(markPrices) / (entryPrice * quantity);
        }

        private String getExitOrMarkPriceText(Map<String, Double> markPrices) {
            if (exitPrice > 0.0) {
                return String.format("%.2f", exitPrice);
            }
            Double markPrice = markPrices.get(symbol);
            return markPrice != null && markPrice > 0.0 ? String.format("%.2f", markPrice) : "--";
        }

        private String getExitMessage(double displayPnL) {
            if (rejected) {
                return failedMessage;
            }
            if (closeTime == null) {
                return "尚未平倉，損益為即時估算";
            }
            String reasonText = closeReason == null || closeReason.isBlank() ? "平倉完成" : closeReason;
            return reasonText + "，損益 " + String.format("%+.2f", displayPnL);
        }

        private static String nonBlankStatic(String preferred, String fallback) {
            return preferred != null && !preferred.isBlank() ? preferred : fallback;
        }

        private static String translateFailureMessage(String message) {
            if (message == null || message.isBlank()) {
                return "訂單失敗";
            }
            if (message.contains("Required cash")) {
                return "資金不足，無法開倉";
            }
            if (message.contains("No position")) {
                return "沒有可平倉部位";
            }
            if (message.contains("Short selling")) {
                return "目前版本不支援放空";
            }
            if (message.contains("quantity")) {
                return "訂單數量不正確";
            }
            if (message.contains("Decision")) {
                return "決策結果不符合執行條件";
            }
            if (message.contains("Live trading")) {
                return "目前不支援真實下單";
            }
            return message;
        }
    }

    private static class TradeRowRenderer extends DefaultTableCellRenderer {
        private static final Color DEFAULT_BG = new Color(40, 40, 40);
        private static final Color OPEN_BG = new Color(34, 56, 43);
        private static final Color CLOSE_BG = new Color(43, 50, 62);
        private static final Color FAILED_BG = new Color(64, 34, 34);
        private static final Color SELECTED_BG = new Color(70, 130, 180);
        private static final Color POSITIVE = new Color(90, 230, 130);
        private static final Color NEGATIVE = new Color(255, 120, 120);
        private static final Color CLOSED_TEXT = new Color(145, 190, 255);

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = String.valueOf(table.getValueAt(row, 0));

            if (isSelected) {
                component.setBackground(SELECTED_BG);
                component.setForeground(Color.WHITE);
            } else {
                if ("失敗".equals(status)) {
                    component.setBackground(FAILED_BG);
                } else if ("持倉中".equals(status)) {
                    component.setBackground(OPEN_BG);
                } else if ("已平倉".equals(status)) {
                    component.setBackground(CLOSE_BG);
                } else {
                    component.setBackground(DEFAULT_BG);
                }

                if ((column == 11 || column == 12) && value instanceof String text && text.startsWith("+")) {
                    component.setForeground(POSITIVE);
                } else if ((column == 11 || column == 12) && value instanceof String text && text.startsWith("-")) {
                    component.setForeground(NEGATIVE);
                } else if ("持倉中".equals(status) && column == 0) {
                    component.setForeground(POSITIVE);
                } else if ("已平倉".equals(status) && column == 0) {
                    component.setForeground(CLOSED_TEXT);
                } else if ("失敗".equals(status)) {
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
