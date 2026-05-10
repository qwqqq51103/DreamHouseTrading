package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.cache.MarketDataCache;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.util.I18n;
import com.dreamhouse.trading.util.TaiwanStockNameService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
        setBorder(BorderFactory.createTitledBorder("Watchlist"));

        // ???楨摮?
        try {
            cache = new MarketDataCache();
            loadWatchlistFromCache();
        } catch (Exception e) {
            System.err.println("Failed to initialize cache: " + e.getMessage());
            // ????靘???
//            items.add(new WatchlistItem("AAPL", 180.50, 2.5, 1500000));
//            items.add(new WatchlistItem("TSLA", 245.30, -1.2, 2300000));
//            items.add(new WatchlistItem("MSFT", 380.20, 0.8, 980000));
        }
        
        tableModel = new EventTableModel<>(items, new WatchlistTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        
        // ??????
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onSymbolDoubleClick != null) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        WatchlistItem item = items.get(row);
                        onSymbolDoubleClick.accept(item.symbol);
                    }
                }
            }
        });
        
        // ?芾?皜脫??剁?憿嚗?
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final DecimalFormat priceFmt = new DecimalFormat("0.00");
            private final DecimalFormat pctFmt = new DecimalFormat("0.00%");
            private final DecimalFormat scoreFmt = new DecimalFormat("0.0%");
            private final DecimalFormat ratioFmt = new DecimalFormat("0.00");

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                // ?蔭??莎??踹?銋????脣蔣?選?
                if (!isSelected) {
                    setForeground(table.getForeground());
                }

                if (column == 3 && value instanceof Double) { // Chg% (?唬?蝵?column 3)
                    double chg = (Double) value;
                    setText(pctFmt.format(chg / 100.0));
                    if (!isSelected) {
                        setForeground(chg >= 0 ? new Color(34, 177, 76) : new Color(237, 28, 36));
                    }
                } else if (column == 2 && value instanceof Double) { // Last (?唬?蝵?column 2)
                    setText(priceFmt.format((Double) value));
                } else if ((column == 6 || column == 8) && value instanceof Double) {
                    setText(scoreFmt.format((Double) value));
                } else if (column == 9 && value instanceof Double) {
                    Double ratio = (Double) value;
                    setText(ratio > 0 ? ratioFmt.format(ratio) : "--");
                }

                return c;
            }
        });
        
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 摨??
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton(I18n.get("watchlist.add"));
        JButton removeBtn = new JButton(I18n.get("watchlist.remove"));

        addBtn.addActionListener(e -> addSymbol());
        removeBtn.addActionListener(e -> removeSelectedSymbol());

        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * 敺楨摮?頛?撖???
     */
    private void loadWatchlistFromCache() {
        if (cache == null) return;

        List<String> symbols = cache.getWatchlist();
        for (String symbol : symbols) {
            items.add(new WatchlistItem(symbol, 0, 0, 0));
        }
        System.out.println("Loaded " + symbols.size() + " symbols from watchlist cache");
    }

    /**
     * 璅??蟡其誨??
     * - ?啗隞??嚗??詨?嚗??芸?瘛餃? .TW 敺韌
     * - 撌脫?敺韌?誨??靽??見
     *
     * @param symbol ???∠巨隞??
     * @return 璅????蟡其誨??
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }

        // 憒?撌脩???蝬湛?? . 蝚西?嚗??湔餈?
        if (symbol.contains(".")) {
            return symbol;
        }

        // 憒??舐??詨?嚗?∩誨??嚗?溶??.TW 敺韌
        // ?啗隞???澆?嚗? 雿嚗? 2330, 6770嚗? 5-6 雿嚗? 00878, 006208嚗?
        if (symbol.matches("\\d{4,6}")) {
            return symbol + ".TW";
        }

        // ?嗡??澆?嚗? AAPL, TSLA 蝑??∴?靽??見
        return symbol;
    }

    /**
     * 瘛餃???撠店獢?
     */
    private void addSymbol() {
        String symbol = JOptionPane.showInputDialog(
            this,
            I18n.get("watchlist.add.prompt"),
            I18n.get("watchlist.add.title"),
            JOptionPane.PLAIN_MESSAGE
        );

        if (symbol != null && !symbol.trim().isEmpty()) {
            symbol = symbol.trim().toUpperCase();

            // ?芸??箏?∩誨?溶??.TW 敺韌
            symbol = normalizeSymbol(symbol);

            // 瑼Ｘ?臬撌脣???
            for (WatchlistItem item : items) {
                if (item.symbol.equals(symbol)) {
                    JOptionPane.showMessageDialog(
                        this,
                        I18n.get("watchlist.add.duplicate"),
                        I18n.get("watchlist.add.title"),
                        JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }
            }

            // ?芸??亥岷銝剜??迂
            String chineseName = TaiwanStockNameService.getChineseName(symbol);

            // 瘛餃??啣?銵?
            items.add(new WatchlistItem(symbol, 0, 0, 0));
            tableModel.fireTableDataChanged();

            // 靽??啁楨摮?
            if (cache != null) {
                cache.addToWatchlist(symbol);
            }

            // 閫貊?矽
            if (onSymbolAdded != null) {
                onSymbolAdded.accept(symbol);
            }

            // 憿舐內瘛餃???閮嚗??思葉??蝔梧?
            String message = chineseName != null ?
                String.format("%s (%s) %s", symbol, chineseName, I18n.get("watchlist.add.success")) :
                String.format("%s %s", symbol, I18n.get("watchlist.add.success"));
            System.out.println(message);
        }
    }

    /**
     * ?芷?訾葉????
     */
    private void removeSelectedSymbol() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                I18n.get("watchlist.remove.noselection"),
                I18n.get("watchlist.remove.title"),
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        WatchlistItem item = items.get(selectedRow);

        int confirm = JOptionPane.showConfirmDialog(
            this,
            I18n.get("watchlist.remove.confirm") + " " + item.symbol + "?",
            I18n.get("watchlist.remove.title"),
            JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            items.remove(selectedRow);
            tableModel.fireTableDataChanged();

            // 敺楨摮??
            if (cache != null) {
                cache.removeFromWatchlist(item.symbol);
            }

            // 閫貊?矽
            if (onSymbolRemoved != null) {
                onSymbolRemoved.accept(item.symbol);
            }

            System.out.println("Removed " + item.symbol + " from watchlist");
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

    public void addSymbolProgrammatically(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return;

        symbol = symbol.trim().toUpperCase();

        // ?芸??箏?∩誨?溶??.TW 敺韌
        symbol = normalizeSymbol(symbol);

        // 瑼Ｘ?臬撌脣???
        for (WatchlistItem item : items) {
            if (item.symbol.equals(symbol)) {
                return;
            }
        }

        items.add(new WatchlistItem(symbol, 0, 0, 0));
        tableModel.fireTableDataChanged();

        if (cache != null) {
            cache.addToWatchlist(symbol);
        }
    }
    
    public void updateItem(String symbol, double last, double changePct, long volume) {
        for (WatchlistItem item : items) {
            if (item.symbol.equals(symbol)) {
                item.last = last;
                item.changePct = changePct;
                item.volume = volume;
                tableModel.fireTableDataChanged();
                return;
            }
        }
    }

    public void updateScanResult(MarketScanResult result) {
        if (result == null) {
            return;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            applyScanResult(result);
        } else {
            SwingUtilities.invokeLater(() -> applyScanResult(result));
        }
    }

    private void applyScanResult(MarketScanResult result) {
        for (WatchlistItem item : items) {
            if (item.symbol.equals(result.getSymbol())) {
                item.tradeMode = result.getTradeMode() != null ? result.getTradeMode().getDisplayName() : "";
                item.scanScore = result.getScore();
                DecisionResult decision = result.getDecisionResult();
                item.signal = decision != null ? decision.getAction().getDisplayName() : "";
                item.confidence = result.getConfidence();
                item.riskRewardRatio = result.getRiskRewardRatio() != null ? result.getRiskRewardRatio() : 0.0;
                item.rawSignalSummary = result.getRawSignalSummary();
                item.blockReason = result.getBlockReason();
                item.lastScanTime = result.getScannedAt() != null ? result.getScannedAt().format(SCAN_TIME_FMT) : "";
                tableModel.fireTableDataChanged();
                return;
            }
        }
    }
    
    public static class WatchlistItem {
        String symbol;
        String chineseName;  // 銝剜??迂
        double last;
        double changePct;
        long volume;
        String tradeMode;
        double scanScore;
        String signal;
        double confidence;
        double riskRewardRatio;
        String rawSignalSummary;
        String blockReason;
        String lastScanTime;

        public WatchlistItem(String symbol, double last, double changePct, long volume) {
            this.symbol = symbol;
            this.chineseName = TaiwanStockNameService.getChineseName(symbol);
            this.last = last;
            this.changePct = changePct;
            this.volume = volume;
            this.tradeMode = "";
            this.scanScore = 0.0;
            this.signal = "";
            this.confidence = 0.0;
            this.riskRewardRatio = 0.0;
            this.rawSignalSummary = "";
            this.blockReason = "";
            this.lastScanTime = "";
        }

        public WatchlistItem(String symbol, String chineseName, double last, double changePct, long volume) {
            this.symbol = symbol;
            this.chineseName = chineseName;
            this.last = last;
            this.changePct = changePct;
            this.volume = volume;
            this.tradeMode = "";
            this.scanScore = 0.0;
            this.signal = "";
            this.confidence = 0.0;
            this.riskRewardRatio = 0.0;
            this.rawSignalSummary = "";
            this.blockReason = "";
            this.lastScanTime = "";
        }
    }
    
    private static class WatchlistTableFormat implements TableFormat<WatchlistItem> {
        @Override
        public int getColumnCount() { return 13; }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("watchlist.symbol");
                case 1 -> I18n.get("watchlist.name");  // ?啣?嚗?蝔勗?
                case 2 -> I18n.get("watchlist.last");
                case 3 -> I18n.get("watchlist.change");
                case 4 -> I18n.get("watchlist.volume");
                case 5 -> "\u6a21\u5f0f";
                case 6 -> "\u5206\u6578";
                case 7 -> "\u8a0a\u865f";
                case 8 -> "\u4fe1\u5fc3";
                case 9 -> "R/R";
                case 10 -> "\u539f\u59cb\u7b56\u7565\u8a0a\u865f";
                case 11 -> "\u6700\u5f8c\u88ab\u64cb\u539f\u56e0";
                case 12 -> "\u6642\u9593";
                default -> "";
            };
        }

        @Override
        public Object getColumnValue(WatchlistItem item, int column) {
            return switch (column) {
                case 0 -> item.symbol;
                case 1 -> item.chineseName != null ? item.chineseName : "";  // ?啣?嚗葉??蝔?
                case 2 -> item.last;
                case 3 -> item.changePct;
                case 4 -> item.volume;
                case 5 -> item.tradeMode;
                case 6 -> item.scanScore;
                case 7 -> item.signal;
                case 8 -> item.confidence;
                case 9 -> item.riskRewardRatio;
                case 10 -> item.rawSignalSummary;
                case 11 -> item.blockReason;
                case 12 -> item.lastScanTime;
                default -> null;
            };
        }
    }
}

