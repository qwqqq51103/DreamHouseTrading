package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.cache.MarketDataCache;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;

public class WatchlistPanel extends JPanel {
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

        // 初始化緩存
        try {
            cache = new MarketDataCache();
            loadWatchlistFromCache();
        } catch (Exception e) {
            System.err.println("Failed to initialize cache: " + e.getMessage());
            // 初始化範例資料
//            items.add(new WatchlistItem("AAPL", 180.50, 2.5, 1500000));
//            items.add(new WatchlistItem("TSLA", 245.30, -1.2, 2300000));
//            items.add(new WatchlistItem("MSFT", 380.20, 0.8, 980000));
        }
        
        tableModel = new EventTableModel<>(items, new WatchlistTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        
        // 雙擊切換商品
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
        
        // 自訂渲染器（顏色）
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final DecimalFormat priceFmt = new DecimalFormat("0.00");
            private final DecimalFormat pctFmt = new DecimalFormat("0.00%");
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (column == 2 && value instanceof Double) { // Chg%
                    double chg = (Double) value;
                    setText(pctFmt.format(chg / 100.0));
                    setForeground(chg >= 0 ? new Color(34, 177, 76) : new Color(237, 28, 36));
                } else if (column == 1 && value instanceof Double) { // Last
                    setText(priceFmt.format((Double) value));
                }
                
                return c;
            }
        });
        
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 底部按鈕
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
     * 從緩存加載觀察清單
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
     * 添加商品對話框
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

            // 檢查是否已存在
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

            // 添加到列表
            items.add(new WatchlistItem(symbol, 0, 0, 0));
            tableModel.fireTableDataChanged();

            // 保存到緩存
            if (cache != null) {
                cache.addToWatchlist(symbol);
            }

            // 觸發回調
            if (onSymbolAdded != null) {
                onSymbolAdded.accept(symbol);
            }

            System.out.println("Added " + symbol + " to watchlist");
        }
    }

    /**
     * 刪除選中的商品
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

            // 從緩存刪除
            if (cache != null) {
                cache.removeFromWatchlist(item.symbol);
            }

            // 觸發回調
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

        // 檢查是否已存在
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
    
    public static class WatchlistItem {
        String symbol;
        double last;
        double changePct;
        long volume;
        
        public WatchlistItem(String symbol, double last, double changePct, long volume) {
            this.symbol = symbol;
            this.last = last;
            this.changePct = changePct;
            this.volume = volume;
        }
    }
    
    private static class WatchlistTableFormat implements TableFormat<WatchlistItem> {
        @Override
        public int getColumnCount() { return 4; }
        
        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("watchlist.symbol");
                case 1 -> I18n.get("watchlist.last");
                case 2 -> I18n.get("watchlist.change");
                case 3 -> I18n.get("watchlist.volume");
                default -> "";
            };
        }
        
        @Override
        public Object getColumnValue(WatchlistItem item, int column) {
            return switch (column) {
                case 0 -> item.symbol;
                case 1 -> item.last;
                case 2 -> item.changePct;
                case 3 -> item.volume;
                default -> null;
            };
        }
    }
}

