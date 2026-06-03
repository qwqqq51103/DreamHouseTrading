package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.model.Trade;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

public class TimeSalesDock extends JPanel {
    private static final int MAX_TRADES = 100;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private final EventList<TradeRow> trades = new BasicEventList<>();
    private final EventTableModel<TradeRow> tableModel;
    private final JTable table;
    
    public TimeSalesDock() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Time & Sales"));
        
        tableModel = new EventTableModel<>(trades, new TimeSalesTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        
        // 自訂渲染器
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private final DecimalFormat priceFmt = new DecimalFormat("0.00");
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < trades.size()) {
                    TradeRow tradeRow = trades.get(modelRow);
                    if (column == 3) { // Side 欄位
                        setForeground(tradeRow.side.equals("ASK") ? 
                            new Color(237, 28, 36) : new Color(34, 177, 76));
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
    
    public void addTrade(Trade trade) {
        SwingUtilities.invokeLater(() -> {
            String timeStr = trade.getTimestamp().format(TIME_FMT);
            String side = trade.getSide().name();
            
            // 新交易加在最上方
            trades.add(0, new TradeRow(timeStr, trade.getPrice(), trade.getQuantity(), side));
            
            // 維持最大筆數
            while (trades.size() > MAX_TRADES) {
                trades.remove(trades.size() - 1);
            }
        });
    }
    
    private static class TradeRow {
        String time;
        double price;
        long quantity;
        String side;
        
        TradeRow(String time, double price, long quantity, String side) {
            this.time = time;
            this.price = price;
            this.quantity = quantity;
            this.side = side;
        }
    }
    
    private static class TimeSalesTableFormat implements TableFormat<TradeRow> {
        @Override
        public int getColumnCount() { return 4; }
        
        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("timesales.time");
                case 1 -> I18n.get("timesales.price");
                case 2 -> I18n.get("timesales.quantity");
                case 3 -> I18n.get("timesales.side");
                default -> "";
            };
        }
        
        @Override
        public Object getColumnValue(TradeRow row, int column) {
            return switch (column) {
                case 0 -> row.time;
                case 1 -> row.price;
                case 2 -> row.quantity;
                case 3 -> row.side;
                default -> null;
            };
        }
    }
}

