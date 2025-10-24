package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.function.Consumer;

public class WatchlistPanel extends JPanel {
    private final EventList<WatchlistItem> items = new BasicEventList<>();
    private final EventTableModel<WatchlistItem> tableModel;
    private final JTable table;
    private Consumer<String> onSymbolDoubleClick;
    
    public WatchlistPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Watchlist"));
        
        // 初始化範例資料
        items.add(new WatchlistItem("AAPL", 180.50, 2.5, 1500000));
        items.add(new WatchlistItem("TSLA", 245.30, -1.2, 2300000));
        items.add(new WatchlistItem("MSFT", 380.20, 0.8, 980000));
        
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
        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    public void setOnSymbolDoubleClick(Consumer<String> callback) {
        this.onSymbolDoubleClick = callback;
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

