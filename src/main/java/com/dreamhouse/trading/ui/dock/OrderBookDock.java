package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.model.DepthLevel;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;

public class OrderBookDock extends JPanel {
    private final EventList<DepthRow> rows = new BasicEventList<>();
    private final EventTableModel<DepthRow> tableModel;
    private final JTable table;
    
    public OrderBookDock() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Order Book (5 Levels)"));
        
        tableModel = new EventTableModel<>(rows, new OrderBookTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        
        // 自訂渲染器
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private final DecimalFormat priceFmt = new DecimalFormat("0.00");
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < rows.size()) {
                    DepthRow depthRow = rows.get(modelRow);
                    if (depthRow.side.equals("ASK")) {
                        setForeground(new Color(237, 28, 36));
                    } else {
                        setForeground(new Color(34, 177, 76));
                    }
                    
                    if (column == 1 && value instanceof Double) {
                        setText(priceFmt.format((Double) value));
                    }
                }
                
                return c;
            }
        };
        
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
    
    public void updateDepth(List<DepthLevel> depth) {
        SwingUtilities.invokeLater(() -> {
            rows.clear();
            
            // ASK (倒序，最高價在上)
            depth.stream()
                .filter(d -> d.getSide() == DepthLevel.Side.ASK)
                .sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice()))
                .forEach(d -> rows.add(new DepthRow("ASK", d.getPrice(), d.getQuantity(), d.getLevel())));
            
            // BID (順序，最高價在上)
            depth.stream()
                .filter(d -> d.getSide() == DepthLevel.Side.BID)
                .sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice()))
                .forEach(d -> rows.add(new DepthRow("BID", d.getPrice(), d.getQuantity(), d.getLevel())));
        });
    }
    
    private static class DepthRow {
        String side;
        double price;
        long quantity;
        int level;
        
        DepthRow(String side, double price, long quantity, int level) {
            this.side = side;
            this.price = price;
            this.quantity = quantity;
            this.level = level;
        }
    }
    
    private static class OrderBookTableFormat implements TableFormat<DepthRow> {
        @Override
        public int getColumnCount() { return 4; }
        
        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("orderbook.side");
                case 1 -> I18n.get("orderbook.price");
                case 2 -> I18n.get("orderbook.quantity");
                case 3 -> I18n.get("orderbook.level");
                default -> "";
            };
        }
        
        @Override
        public Object getColumnValue(DepthRow row, int column) {
            return switch (column) {
                case 0 -> row.side;
                case 1 -> row.price;
                case 2 -> row.quantity;
                case 3 -> row.level;
                default -> null;
            };
        }
    }
}

