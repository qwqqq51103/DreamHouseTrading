package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.StockNameResolver;
import com.dreamhouse.trading.core.cache.MarketDataCache;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class WatchlistPanel extends JPanel {
    private static final DateTimeFormatter SCAN_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EventList<WatchlistItem> items = new BasicEventList<>();
    private final EventTableModel<WatchlistItem> tableModel;
    private final JTable table;

    private Consumer<String> onSymbolDoubleClick;
    private Consumer<String> onSymbolAdded;
    private Consumer<String> onSymbolRemoved;
    private MarketDataCache cache;

    public WatchlistPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("觀察清單"));

        try {
            cache = new MarketDataCache();
            loadWatchlistFromCache();
        } catch (Exception e) {
            System.err.println("Failed to initialize cache: " + e.getMessage());
        }

        tableModel = new EventTableModel<>(items, new WatchlistTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setAutoCreateRowSorter(true);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onSymbolDoubleClick != null) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        WatchlistItem item = items.get(modelRow);
                        onSymbolDoubleClick.accept(item.symbol);
                    }
                }
            }
        });
        table.setDefaultRenderer(Object.class, new WatchlistCellRenderer());

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton(I18n.get("watchlist.add"));
        JButton batchAddBtn = new JButton("批量新增");
        JButton removeBtn = new JButton(I18n.get("watchlist.remove"));
        JButton batchRemoveBtn = new JButton("批量刪除");
        addBtn.addActionListener(e -> addSymbol());
        batchAddBtn.addActionListener(e -> addSymbolsBatch());
        removeBtn.addActionListener(e -> removeSelectedSymbols(false));
        batchRemoveBtn.addActionListener(e -> removeSelectedSymbols(true));
        buttonPanel.add(addBtn);
        buttonPanel.add(batchAddBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(batchRemoveBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadWatchlistFromCache() {
        if (cache == null) {
            return;
        }
        List<String> symbols = cache.getWatchlist();
        for (String symbol : symbols) {
            items.add(new WatchlistItem(symbol, 0, 0, 0));
        }
        System.out.println("Loaded " + symbols.size() + " symbols from watchlist cache");
    }

    private void addSymbol() {
        String symbol = JOptionPane.showInputDialog(
                this,
                I18n.get("watchlist.add.prompt"),
                I18n.get("watchlist.add.title"),
                JOptionPane.PLAIN_MESSAGE);

        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }

        symbol = symbol.trim().toUpperCase();
        for (WatchlistItem item : items) {
            if (item.symbol.equals(symbol)) {
                JOptionPane.showMessageDialog(
                        this,
                        I18n.get("watchlist.add.duplicate"),
                        I18n.get("watchlist.add.title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (addSymbolInternal(symbol, true)) {
            System.out.println("Added " + symbol + " to watchlist");
        }
    }

    private void addSymbolsBatch() {
        JTextArea input = new JTextArea(8, 36);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        int option = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(input),
                "批量新增觀察清單",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }

        List<String> symbols = parseSymbols(input.getText());
        if (symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請輸入股票代碼，可用換行、逗號或空白分隔。", "批量新增", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int added = 0;
        for (String symbol : symbols) {
            if (addSymbolInternal(symbol, true)) {
                added++;
            }
        }
        JOptionPane.showMessageDialog(this, "批量新增完成，新增 " + added + " 檔。", "批量新增", JOptionPane.INFORMATION_MESSAGE);
    }

    private void removeSelectedSymbols(boolean batchMode) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    I18n.get("watchlist.remove.noselection"),
                    I18n.get("watchlist.remove.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> symbols = new ArrayList<>();
        if (batchMode) {
            for (int selectedRow : selectedRows) {
                int modelRow = table.convertRowIndexToModel(selectedRow);
                symbols.add(items.get(modelRow).symbol);
            }
        } else {
            int modelRow = table.convertRowIndexToModel(selectedRows[0]);
            symbols.add(items.get(modelRow).symbol);
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "確定要刪除 " + symbols.size() + " 檔觀察清單股票？\n" + String.join(", ", symbols),
                I18n.get("watchlist.remove.title"),
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            List<String> removed = removeSymbolsProgrammatically(symbols, true);
            System.out.println("Removed " + removed.size() + " symbols from watchlist");
        }
    }

    public void setOnSymbolDoubleClick(Consumer<String> callback) {
        this.onSymbolDoubleClick = callback;
    }

    public void setOnSymbolAdded(Consumer<String> callback) {
        this.onSymbolAdded = callback;
    }

    public void setOnSymbolRemoved(Consumer<String> callback) {
        this.onSymbolRemoved = callback;
    }

    public List<String> getSymbols() {
        return items.stream().map(item -> item.symbol).toList();
    }

    public boolean addSymbolProgrammatically(String symbol) {
        return addSymbolInternal(symbol, false);
    }

    private boolean addSymbolInternal(String symbol, boolean notify) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }

        symbol = symbol.trim().toUpperCase();
        for (WatchlistItem item : items) {
            if (item.symbol.equals(symbol)) {
                return false;
            }
        }

        items.add(new WatchlistItem(symbol, 0, 0, 0));
        tableModel.fireTableDataChanged();
        if (cache != null) {
            cache.addToWatchlist(symbol);
        }
        if (notify && onSymbolAdded != null) {
            onSymbolAdded.accept(symbol);
            items.get(items.size() - 1).refreshChineseName();
            tableModel.fireTableDataChanged();
        }
        return true;
    }

    public List<String> removeSymbolsProgrammatically(List<String> symbols, boolean notify) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedTargets = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                normalizedTargets.add(StockNameResolver.normalize(symbol));
            }
        }
        List<String> removed = new ArrayList<>();
        for (int index = items.size() - 1; index >= 0; index--) {
            WatchlistItem item = items.get(index);
            if (normalizedTargets.contains(StockNameResolver.normalize(item.symbol))) {
                removed.add(0, item.symbol);
                items.remove(index);
                if (cache != null) {
                    cache.removeFromWatchlist(item.symbol);
                }
            }
        }
        if (!removed.isEmpty()) {
            tableModel.fireTableDataChanged();
            if (notify && onSymbolRemoved != null) {
                for (String symbol : removed) {
                    onSymbolRemoved.accept(symbol);
                }
            }
        }
        return removed;
    }

    private List<String> parseSymbols(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> symbols = new LinkedHashSet<>();
        for (String token : text.split("[,，;；\\s]+")) {
            String symbol = token.trim().toUpperCase();
            if (symbol.matches("\\d{4}")) {
                symbol += ".TW";
            }
            if (!symbol.isBlank()) {
                symbols.add(symbol);
            }
        }
        return new ArrayList<>(symbols);
    }

    public void updateItem(String symbol, double last, double changePct, long volume) {
        String normalized = StockNameResolver.normalize(symbol);
        for (WatchlistItem item : items) {
            if (StockNameResolver.normalize(item.symbol).equals(normalized)) {
                item.last = last;
                item.changePct = changePct;
                item.volume = volume;
                tableModel.fireTableDataChanged();
                return;
            }
        }
    }

    public void updateScanResult(MarketScanResult result) {
        if (result == null || result.getSymbol() == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            applyScanResult(result);
        } else {
            SwingUtilities.invokeLater(() -> applyScanResult(result));
        }
    }

    private void applyScanResult(MarketScanResult result) {
        String normalized = StockNameResolver.normalize(result.getSymbol());
        for (WatchlistItem item : items) {
            if (StockNameResolver.normalize(item.symbol).equals(normalized)) {
                item.tradeMode = result.getTradeMode() != null ? result.getTradeMode().getDisplayName() : "";
                item.scanScore = result.getScore();
                DecisionResult decision = result.getDecisionResult();
                item.signal = decision != null ? decision.getAction().getDisplayName() : "";
                item.confidence = result.getConfidence();
                item.riskRewardRatio = result.getRiskRewardRatio() != null ? result.getRiskRewardRatio() : 0.0;
                item.lastScanTime = result.getScannedAt() != null ? result.getScannedAt().format(SCAN_TIME_FMT) : "";
                tableModel.fireTableDataChanged();
                return;
            }
        }
    }

    public static class WatchlistItem {
        String symbol;
        String chineseName;
        double last;
        double changePct;
        long volume;
        String tradeMode;
        double scanScore;
        String signal;
        double confidence;
        double riskRewardRatio;
        String lastScanTime;

        public WatchlistItem(String symbol, double last, double changePct, long volume) {
            this.symbol = symbol;
            this.chineseName = StockNameResolver.resolveChineseName(symbol);
            this.last = last;
            this.changePct = changePct;
            this.volume = volume;
            this.tradeMode = "";
            this.scanScore = 0.0;
            this.signal = "";
            this.confidence = 0.0;
            this.riskRewardRatio = 0.0;
            this.lastScanTime = "";
        }

        void refreshChineseName() {
            this.chineseName = StockNameResolver.resolveChineseName(symbol);
        }
    }

    private static class WatchlistTableFormat implements TableFormat<WatchlistItem> {
        @Override
        public int getColumnCount() {
            return 11;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("watchlist.symbol");
                case 1 -> "中文名稱";
                case 2 -> I18n.get("watchlist.last");
                case 3 -> I18n.get("watchlist.change");
                case 4 -> I18n.get("watchlist.volume");
                case 5 -> "交易模式";
                case 6 -> "訊號";
                case 7 -> "分數";
                case 8 -> "信心";
                case 9 -> "風報比";
                case 10 -> "掃描時間";
                default -> "";
            };
        }

        @Override
        public Object getColumnValue(WatchlistItem item, int column) {
            return switch (column) {
                case 0 -> item.symbol;
                case 1 -> item.chineseName;
                case 2 -> item.last;
                case 3 -> item.changePct;
                case 4 -> item.volume;
                case 5 -> item.tradeMode;
                case 6 -> item.signal;
                case 7 -> item.scanScore;
                case 8 -> item.confidence;
                case 9 -> item.riskRewardRatio;
                case 10 -> item.lastScanTime;
                default -> null;
            };
        }
    }

    private static class WatchlistCellRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat priceFmt = new DecimalFormat("0.00");
        private final DecimalFormat pctFmt = new DecimalFormat("0.00%");
        private final DecimalFormat scoreFmt = new DecimalFormat("0.0%");
        private final DecimalFormat ratioFmt = new DecimalFormat("0.00");

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                setForeground(table.getForeground());
            }

            if (column == 2 && value instanceof Double last) {
                setText(priceFmt.format(last));
            } else if (column == 3 && value instanceof Double chg) {
                setText(pctFmt.format(chg / 100.0));
                if (!isSelected) {
                    setForeground(chg >= 0 ? new Color(34, 177, 76) : new Color(237, 28, 36));
                }
            } else if ((column == 7 || column == 8) && value instanceof Double score) {
                setText(scoreFmt.format(score));
            } else if (column == 9 && value instanceof Double ratio) {
                setText(ratio > 0 ? ratioFmt.format(ratio) : "--");
            } else if (value == null || value.toString().isBlank()) {
                setText("--");
            }

            if (!isSelected && column == 6 && value != null) {
                String text = value.toString().toUpperCase();
                if (text.contains("LONG") || text.contains("買")) {
                    setForeground(new Color(0, 150, 70));
                } else if (text.contains("SHORT") || text.contains("賣")) {
                    setForeground(new Color(190, 50, 50));
                }
            }

            return component;
        }
    }
}
