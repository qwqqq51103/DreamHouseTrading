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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NewsDock extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final EventList<NewsRow> visibleNews = new BasicEventList<>();
    private final List<NewsItem> allNews = new ArrayList<>();
    private final EventTableModel<NewsRow> tableModel;
    private final JTable table;
    private final JLabel symbolLabel;
    private String currentSymbol = "";

    public NewsDock() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("個股新聞"));

        symbolLabel = new JLabel("全部新聞");
        symbolLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(symbolLabel, BorderLayout.NORTH);

        tableModel = new EventTableModel<>(visibleNews, new NewsTableFormat());
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    NewsRow row = getSelectedNewsRow();
                    if (row != null && row.url != null && !row.url.isBlank()) {
                        openNewsUrl(row.url);
                    }
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setCurrentSymbol(String symbol) {
        SwingUtilities.invokeLater(() -> {
            currentSymbol = symbol != null ? symbol.trim().toUpperCase() : "";
            symbolLabel.setText(currentSymbol.isBlank() ? "全部新聞" : currentSymbol + " 新聞");
            refreshVisibleNews();
        });
    }

    public void addNews(NewsItem item) {
        if (item == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            allNews.removeIf(existing -> isSameNews(existing, item));
            allNews.add(item);
            allNews.sort(Comparator.comparing(this::timestampOrMin).reversed());
            while (allNews.size() > 300) {
                allNews.remove(allNews.size() - 1);
            }
            refreshVisibleNews();
        });
    }

    private void refreshVisibleNews() {
        visibleNews.clear();
        allNews.stream()
            .filter(this::matchesCurrentSymbol)
            .limit(80)
            .map(this::toRow)
            .forEach(visibleNews::add);
    }

    private boolean matchesCurrentSymbol(NewsItem item) {
        if (currentSymbol == null || currentSymbol.isBlank()) {
            return true;
        }
        String symbol = item.getSymbol();
        return symbol != null && symbol.equalsIgnoreCase(currentSymbol);
    }

    private NewsRow toRow(NewsItem item) {
        LocalDateTime timestamp = item.getTimestamp();
        String timeText = timestamp != null ? timestamp.format(TIME_FMT) : "";
        return new NewsRow(
            item.getSymbol() != null ? item.getSymbol() : "",
            timeText,
            item.getSource(),
            item.getTitle(),
            item.getUrl()
        );
    }

    private LocalDateTime timestampOrMin(NewsItem item) {
        return item.getTimestamp() != null ? item.getTimestamp() : LocalDateTime.MIN;
    }

    private boolean isSameNews(NewsItem left, NewsItem right) {
        String leftUrl = left.getUrl();
        String rightUrl = right.getUrl();
        if (leftUrl != null && rightUrl != null && !leftUrl.isBlank() && !rightUrl.isBlank()) {
            return leftUrl.equals(rightUrl);
        }
        return same(left.getSymbol(), right.getSymbol())
            && same(left.getSource(), right.getSource())
            && same(left.getTitle(), right.getTitle());
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private NewsRow getSelectedNewsRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return modelRow >= 0 && modelRow < visibleNews.size() ? visibleNews.get(modelRow) : null;
    }

    private void openNewsUrl(String url) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "無法開啟 URL: " + url, "錯誤", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class NewsRow {
        String symbol;
        String time;
        String source;
        String title;
        String url;

        NewsRow(String symbol, String time, String source, String title, String url) {
            this.symbol = symbol;
            this.time = time;
            this.source = source;
            this.title = title;
            this.url = url;
        }
    }

    private static class NewsTableFormat implements TableFormat<NewsRow> {
        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "代號";
                case 1 -> I18n.get("news.time");
                case 2 -> I18n.get("news.source");
                case 3 -> I18n.get("news.title");
                default -> "";
            };
        }

        @Override
        public Object getColumnValue(NewsRow row, int column) {
            return switch (column) {
                case 0 -> row.symbol;
                case 1 -> row.time;
                case 2 -> row.source;
                case 3 -> row.title;
                default -> null;
            };
        }
    }
}
