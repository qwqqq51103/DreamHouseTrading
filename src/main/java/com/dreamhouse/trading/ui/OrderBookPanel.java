package com.dreamhouse.trading.ui;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Random;

public class OrderBookPanel extends JPanel {
    private final EventList<OrderBookRow> rows = new BasicEventList<>();
    private final EventTableModel<OrderBookRow> tableModel;
    private final JTable table;
    private final Random random = new Random();
    private double midPrice = 100.0;

    public OrderBookPanel() {
        setLayout(new BorderLayout());

        // 初始化五檔 (5 Bid + 5 Ask)
        for (int i = 5; i >= 1; i--) {
            rows.add(new OrderBookRow("ASK", round(midPrice + i * 0.2), randomQty()));
        }
        for (int i = 0; i < 5; i++) {
            rows.add(new OrderBookRow("BID", round(midPrice - (i + 1) * 0.2), randomQty()));
        }

        tableModel = new EventTableModel<>(rows, new OrderBookTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);

        // 自訂 Renderer: ASK 紅、BID 綠
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private final Color bidColor = new Color(34, 177, 76);
            private final Color askColor = new Color(237, 28, 36);
            private final DecimalFormat priceFmt = new DecimalFormat("0.00");

            @Override
            protected void setValue(Object value) {
                if (value instanceof Double) {
                    super.setValue(priceFmt.format((Double) value));
                } else {
                    super.setValue(value);
                }
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                OrderBookRow r = rows.get(modelRow);
                c.setForeground("ASK".equals(r.side) ? askColor : bidColor);
                return c;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 模擬隨機更新
        Timer timer = new Timer(800, e -> randomUpdate());
        timer.start();
    }

    private void randomUpdate() {
        // 輕微漂移中價
        midPrice += (random.nextDouble() - 0.5) * 0.08;
        midPrice = Math.max(1.0, midPrice);

        // 隨機挑一列更新數量或價格
        if (rows.isEmpty()) return;
        int idx = random.nextInt(rows.size());
        OrderBookRow r = rows.get(idx);

        if (random.nextDouble() < 0.3) {
            // 偶爾調整價格，使五檔跟著中價移動
            double level = (idx < 5) ? (5 - idx) : (idx - 4); // ASK: 5..1, BID: 1..5
            double sign = (idx < 5) ? +1 : -1;
            r.price = round(midPrice + sign * level * 0.2 + (random.nextDouble() - 0.5) * 0.04);
        } else {
            // 一般更新數量
            int delta = random.nextInt(300) - 150;
            r.quantity = Math.max(0, r.quantity + delta);
            if (r.quantity == 0) r.quantity = randomQty();
        }

        // 通知表格有內容改變
        int row = idx;
        tableModel.fireTableRowsUpdated(row, row);
    }

    private int randomQty() {
        return 500 + random.nextInt(3000);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // 資料結構
    static class OrderBookRow {
        String side;   // "BID" or "ASK"
        double price;
        int quantity;

        OrderBookRow(String side, double price, int quantity) {
            this.side = side;
            this.price = price;
            this.quantity = quantity;
        }
    }

    // 表格欄位定義
    static class OrderBookTableFormat implements TableFormat<OrderBookRow> {
        @Override
        public int getColumnCount() { return 3; }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Side";
                case 1 -> "Price";
                case 2 -> "Quantity";
                default -> "";
            };
        }

        @Override
        public Object getColumnValue(OrderBookRow baseObject, int column) {
            return switch (column) {
                case 0 -> baseObject.side;
                case 1 -> baseObject.price;
                case 2 -> baseObject.quantity;
                default -> null;
            };
        }
    }
}


