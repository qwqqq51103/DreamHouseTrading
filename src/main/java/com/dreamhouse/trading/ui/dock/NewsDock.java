package com.dreamhouse.trading.ui.dock;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.swing.EventTableModel;
import com.dreamhouse.trading.core.model.NewsItem;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NewsDock extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    
    private final EventList<NewsRow> news = new BasicEventList<>();
    private final EventTableModel<NewsRow> tableModel;
    private final JTable table;
    
    public NewsDock() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Market News"));

        // 假資料（已禁用 - 使用真實 API 數據）
        // news.add(new NewsRow("10-23 14:30", "Bloomberg", "Fed signals rate cut pause", "https://example.com"));
        // news.add(new NewsRow("10-23 13:15", "Reuters", "Tech stocks rally on earnings", "https://example.com"));
        // news.add(new NewsRow("10-23 11:00", "CNBC", "Oil prices surge amid supply concerns", "https://example.com"));

        tableModel = new EventTableModel<>(news, new NewsTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        
        // 點擊開啟 URL
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < news.size()) {
                        NewsRow newsRow = news.get(row);
                        try {
                            Desktop.getDesktop().browse(new java.net.URI(newsRow.url));
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(NewsDock.this, 
                                "無法開啟 URL: " + newsRow.url, "錯誤", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });
        
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
    
    public void addNews(NewsItem item) {
        SwingUtilities.invokeLater(() -> {
            String timeStr = item.getTimestamp().format(TIME_FMT);
            news.add(0, new NewsRow(timeStr, item.getSource(), item.getTitle(), item.getUrl()));
            
            // 最多保留 50 則
            while (news.size() > 50) {
                news.remove(news.size() - 1);
            }
        });
    }
    
    private static class NewsRow {
        String time;
        String source;
        String title;
        String url;
        
        NewsRow(String time, String source, String title, String url) {
            this.time = time;
            this.source = source;
            this.title = title;
            this.url = url;
        }
    }
    
    private static class NewsTableFormat implements TableFormat<NewsRow> {
        @Override
        public int getColumnCount() { return 3; }
        
        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.get("news.time");
                case 1 -> I18n.get("news.source");
                case 2 -> I18n.get("news.title");
                default -> "";
            };
        }
        
        @Override
        public Object getColumnValue(NewsRow row, int column) {
            return switch (column) {
                case 0 -> row.time;
                case 1 -> row.source;
                case 2 -> row.title;
                default -> null;
            };
        }
    }
}

