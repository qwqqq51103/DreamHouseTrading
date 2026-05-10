package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.scanner.MarketScanResult;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OpportunityRadarDock extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, MarketScanResult> resultsBySymbol = new LinkedHashMap<>();
    private final RadarTableModel tableModel = new RadarTableModel();
    private final JTable table = new JTable(tableModel);
    private final JComboBox<TradeModeFilter> modeFilter = new JComboBox<>(TradeModeFilter.values());
    private final JLabel updateStatus = new JLabel("\u5c1a\u672a\u6383\u63cf");
    private Consumer<String> onSymbolSelected;

    public OpportunityRadarDock() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("\u4eca\u65e5\u6a5f\u6703\u96f7\u9054"));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("\u6a21\u5f0f"));
        toolbar.add(modeFilter);
        modeFilter.addActionListener(e -> {
            tableModel.refresh();
            updateStatusLabel(null);
        });
        add(toolbar, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);
        table.setDefaultRenderer(Object.class, new RadarCellRenderer());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onSymbolSelected != null) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        MarketScanResult result = tableModel.getResultAt(modelRow);
                        if (result != null) {
                            onSymbolSelected.accept(result.getSymbol());
                        }
                    }
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
        updateStatus.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(updateStatus, BorderLayout.SOUTH);
    }

    public void setOnSymbolSelected(Consumer<String> onSymbolSelected) {
        this.onSymbolSelected = onSymbolSelected;
    }

    public void updateScanResult(MarketScanResult result) {
        if (result == null || result.getSymbol() == null) {
            return;
        }
        updateScanResults(List.of(result));
    }

    public void updateScanResults(List<MarketScanResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            applyScanResults(results);
        } else {
            SwingUtilities.invokeLater(() -> applyScanResults(results));
        }
    }

    private void applyScanResults(List<MarketScanResult> results) {
        MarketScanResult latestResult = null;
        for (MarketScanResult result : results) {
            if (result == null || result.getSymbol() == null) {
                continue;
            }
            resultsBySymbol.put(result.getSymbol(), result);
            latestResult = result;
        }
        tableModel.refresh();
        updateStatusLabel(latestResult);
    }

    private void updateStatusLabel(MarketScanResult latestResult) {
        String time = latestResult != null && latestResult.getScannedAt() != null
            ? latestResult.getScannedAt().format(TIME_FMT)
            : "--:--:--";
        String symbol = latestResult != null ? latestResult.getSymbol() : "";
        updateStatus.setText(String.format(
            "\u6700\u5f8c\u66f4\u65b0 %s %s | \u986f\u793a %d \u6a94 / \u7e3d\u6383\u63cf %d \u6a94",
            time,
            symbol,
            tableModel.getRowCount(),
            resultsBySymbol.size()));
    }

    private List<MarketScanResult> getFilteredResults() {
        TradeModeFilter filter = (TradeModeFilter) modeFilter.getSelectedItem();
        return resultsBySymbol.values().stream()
            .filter(result -> filter == null || filter.accepts(result.getTradeMode()))
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private enum TradeModeFilter {
        ALL("\u5168\u90e8", null),
        DAY_TRADE("\u7576\u6c96", TradeMode.DAY_TRADE),
        SHORT_SWING("\u77ed\u7dda", TradeMode.SHORT_SWING),
        SWING_TRADE("\u6ce2\u6bb5", TradeMode.SWING_TRADE);

        private final String label;
        private final TradeMode mode;

        TradeModeFilter(String label, TradeMode mode) {
            this.label = label;
            this.mode = mode;
        }

        boolean accepts(TradeMode tradeMode) {
            return mode == null || mode == tradeMode;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class RadarTableModel extends AbstractTableModel {
        private final String[] columns = {
            "\u6392\u540d", "\u80a1\u7968", "\u6a21\u5f0f", "\u5206\u6578", "\u52d5\u4f5c", "\u4fe1\u5fc3", "R/R", "\u505c\u640d", "\u505c\u5229", "\u539f\u59cb\u7b56\u7565\u8a0a\u865f", "\u6700\u5f8c\u88ab\u64cb\u539f\u56e0", "\u6700\u7d42\u539f\u56e0", "\u6642\u9593"
        };
        private List<MarketScanResult> rows = new ArrayList<>();

        void refresh() {
            rows = getFilteredResults();
            fireTableDataChanged();
        }

        MarketScanResult getResultAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MarketScanResult result = rows.get(rowIndex);
            DecisionResult decision = result.getDecisionResult();
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> result.getSymbol();
                case 2 -> result.getTradeMode() != null ? result.getTradeMode().getDisplayName() : "";
                case 3 -> result.getScore();
                case 4 -> decision != null ? decision.getAction().getDisplayName() : "";
                case 5 -> result.getConfidence();
                case 6 -> result.getRiskRewardRatio();
                case 7 -> result.getSuggestedStopLoss();
                case 8 -> result.getSuggestedTakeProfit();
                case 9 -> result.getRawSignalSummary();
                case 10 -> result.getBlockReason();
                case 11 -> result.getReason();
                case 12 -> result.getScannedAt() != null ? result.getScannedAt().format(TIME_FMT) : "";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Integer.class;
                case 3, 5 -> Double.class;
                default -> Object.class;
            };
        }
    }

    private static class RadarCellRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat percentFmt = new DecimalFormat("0.0%");
        private final DecimalFormat numberFmt = new DecimalFormat("0.00");

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if ((column == 3 || column == 5) && value instanceof Double d) {
                setText(percentFmt.format(d));
            } else if ((column == 6 || column == 7 || column == 8) && value instanceof Double d) {
                setText(d > 0 ? numberFmt.format(d) : "--");
            } else if (value == null) {
                setText("--");
            }

            if (!isSelected) {
                setForeground(table.getForeground());
                if (column == 4 && value != null) {
                    String text = value.toString().toUpperCase();
                    if (text.contains("LONG")) {
                        setForeground(new Color(0, 150, 70));
                    } else if (text.contains("SHORT")) {
                        setForeground(new Color(190, 50, 50));
                    }
                }
            }
            return c;
        }
    }
}
