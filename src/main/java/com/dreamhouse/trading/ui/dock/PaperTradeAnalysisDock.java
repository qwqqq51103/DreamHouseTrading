package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.StockNameResolver;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports paper-trading CSV logs and shows the full setup -> order -> close lifecycle.
 */
public class PaperTradeAnalysisDock extends JPanel {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final JTextField directoryField = new JTextField(Paths.get("logs", "paper-trades").toString(), 38);
    private final JTextField dateField = new JTextField(LocalDate.now().format(FILE_DATE), 8);
    private final JTextArea summaryArea = new JTextArea();
    private final DefaultTableModel timelineModel = tableModel(
            "狀態", "部位ID", "代碼", "中文", "模式", "數量", "開倉時間", "開倉價", "平倉時間", "平倉價", "淨損益", "開倉原因", "平倉原因");
    private final DefaultTableModel completedModel = tableModel(
            "平倉時間", "部位ID", "代碼", "中文", "模式", "數量", "進場", "出場", "淨損益", "結果", "Setup分數", "進場原因", "出場原因");
    private final DefaultTableModel setupsModel = tableModel(
            "進場時間", "部位ID", "訂單ID", "代碼", "中文", "模式", "數量", "進場價", "停損", "停利", "分數", "信心", "原因");
    private final DefaultTableModel ordersModel = tableModel(
            "時間", "部位ID", "訂單ID", "代碼", "中文", "方向", "狀態", "數量", "委託價", "成交價", "模式", "原因", "訊息");
    private final DefaultTableModel unmatchedModel = tableModel(
            "類型", "部位ID", "訂單ID", "代碼", "中文", "時間", "數量", "價格", "原因");

    public PaperTradeAnalysisDock() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setBackground(new Color(40, 40, 40));

        add(createControls(), BorderLayout.NORTH);
        add(createTabs(), BorderLayout.CENTER);
    }

    private JPanel createControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton browseButton = new JButton("選擇資料夾");
        JButton loadButton = new JButton("匯入");
        JButton todayButton = new JButton("今天");

        browseButton.addActionListener(e -> chooseDirectory());
        todayButton.addActionListener(e -> {
            dateField.setText(LocalDate.now().format(FILE_DATE));
            loadSelectedDate();
        });
        loadButton.addActionListener(e -> loadSelectedDate());

        panel.add(new JLabel("資料夾"));
        panel.add(directoryField);
        panel.add(browseButton);
        panel.add(new JLabel("日期"));
        panel.add(dateField);
        panel.add(loadButton);
        panel.add(todayButton);
        return panel;
    }

    private JTabbedPane createTabs() {
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 13));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("摘要", new JScrollPane(summaryArea));
        tabs.addTab("開平倉流程", table(timelineModel, 7, 13, 7, 7, 5, 4, 9, 6, 9, 6, 7, 17, 17));
        tabs.addTab("完成交易", table(completedModel, 11, 13, 7, 7, 5, 4, 6, 6, 7, 5, 7, 17, 17));
        tabs.addTab("進場 Setup", table(setupsModel, 11, 13, 13, 7, 7, 5, 4, 6, 6, 6, 5, 5, 18));
        tabs.addTab("訂單", table(ordersModel, 11, 13, 13, 7, 7, 5, 6, 4, 6, 6, 5, 15, 15));
        tabs.addTab("未配對", table(unmatchedModel, 8, 14, 14, 8, 8, 12, 5, 6, 25));
        return tabs;
    }

    private JScrollPane table(DefaultTableModel model, int... columnWeights) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Microsoft JhengHei", Font.BOLD, 12));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeColumnsToViewport(table, scrollPane, columnWeights);
            }
        });
        table.getModel().addTableModelListener(e -> resizeColumnsToViewport(table, scrollPane, columnWeights));
        resizeColumnsToViewport(table, scrollPane, columnWeights);
        return scrollPane;
    }

    private void resizeColumnsToViewport(JTable table, JScrollPane scrollPane, int... weights) {
        int columnCount = table.getColumnModel().getColumnCount();
        if (columnCount == 0 || weights == null || weights.length != columnCount) {
            return;
        }
        Component viewport = scrollPane.getViewport();
        int availableWidth = viewport != null && viewport.getWidth() > 0
                ? viewport.getWidth()
                : scrollPane.getWidth();
        if (availableWidth <= 0) {
            return;
        }

        int totalWeight = 0;
        for (int weight : weights) {
            totalWeight += Math.max(1, weight);
        }
        int usedWidth = 0;
        for (int i = 0; i < columnCount; i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            int width = i == columnCount - 1
                    ? Math.max(48, availableWidth - usedWidth)
                    : Math.max(48, (int) Math.round((availableWidth * Math.max(1, weights[i])) / (double) totalWeight));
            column.setPreferredWidth(width);
            column.setMinWidth(Math.min(48, width));
            usedWidth += width;
        }
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(directoryField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
            loadSelectedDate();
        }
    }

    private void loadSelectedDate() {
        Path directory = Paths.get(directoryField.getText().trim());
        String dateText = dateField.getText().trim();
        SwingWorker<ImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ImportResult doInBackground() throws Exception {
                return importLogs(directory, dateText);
            }

            @Override
            protected void done() {
                try {
                    render(get());
                } catch (Exception e) {
                    summaryArea.setText("匯入失敗：" + e.getMessage());
                    clearTables();
                }
            }
        };
        worker.execute();
    }

    ImportResult importLogs(Path directory, String dateText) throws IOException {
        String normalizedDate = normalizeDate(dateText);
        CsvTable orders = readCsv(directory.resolve("orders_" + normalizedDate + ".csv"));
        CsvTable completed = readCsv(directory.resolve("completed_trades_" + normalizedDate + ".csv"));
        CsvTable setups = readCsv(directory.resolve("trade_setups_" + normalizedDate + ".csv"));
        return new ImportResult(directory, normalizedDate, orders, completed, setups, buildJourneys(orders, completed, setups));
    }

    private String normalizeDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return LocalDate.now().format(FILE_DATE);
        }
        String digits = dateText.replace("-", "").replace("/", "").trim();
        if (digits.length() != 8 || !digits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("日期格式需為 yyyyMMdd 或 yyyy-MM-dd");
        }
        return digits;
    }

    private CsvTable readCsv(Path path) throws IOException {
        if (Files.notExists(path)) {
            return new CsvTable(path, List.of(), List.of());
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new CsvTable(path, List.of(), List.of());
            }
            headerLine = stripBom(headerLine);
            List<String> headers = parseCsvLine(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(row);
            }
            return new CsvTable(path, headers, rows);
        }
    }

    private List<TradeJourney> buildJourneys(CsvTable orders, CsvTable completed, CsvTable setups) {
        Map<String, TradeJourney> journeys = new LinkedHashMap<>();
        for (Map<String, String> setup : setups.rows()) {
            String id = tradeId(setup);
            journeys.computeIfAbsent(id, TradeJourney::new).setup = setup;
        }
        for (Map<String, String> order : orders.rows()) {
            String id = tradeId(order);
            TradeJourney journey = journeys.computeIfAbsent(id, TradeJourney::new);
            journey.orders.add(order);
            if ("BUY".equalsIgnoreCase(order.get("side"))) {
                journey.entryOrder = order;
            } else if ("SELL".equalsIgnoreCase(order.get("side"))) {
                journey.exitOrder = order;
            }
        }
        for (Map<String, String> trade : completed.rows()) {
            String id = tradeId(trade);
            journeys.computeIfAbsent(id, TradeJourney::new).completed = trade;
        }
        List<TradeJourney> result = new ArrayList<>(journeys.values());
        result.sort(Comparator.comparing(TradeJourney::sortTime).reversed());
        return result;
    }

    private void render(ImportResult result) {
        clearTables();
        renderSummary(result);
        for (TradeJourney journey : result.journeys()) {
            renderTimelineRow(journey);
            if (journey.completed == null || journey.entryOrder == null || journey.exitOrder == null) {
                renderUnmatchedRows(journey);
            }
        }
        for (Map<String, String> row : result.completed().rows()) {
            completedModel.addRow(new Object[]{
                    row.get("closed_time"), row.get("position_id"), row.get("symbol"),
                    StockNameResolver.resolveChineseName(row.get("symbol")), row.get("trade_mode"),
                    row.get("quantity"), row.get("entry_price"), row.get("exit_price"),
                    row.get("net_pnl"), row.get("outcome"), row.get("setup_score"),
                    row.get("entry_reason"), row.get("exit_reason")
            });
        }
        for (Map<String, String> row : result.setups().rows()) {
            setupsModel.addRow(new Object[]{
                    row.get("entry_time"), row.get("position_id"), row.get("order_id"), row.get("symbol"),
                    StockNameResolver.resolveChineseName(row.get("symbol")), row.get("trade_mode"),
                    row.get("quantity"), row.get("entry_price"), row.get("stop_loss"), row.get("take_profit"),
                    row.get("setup_score"), row.get("confidence"), row.get("reason")
            });
        }
        for (Map<String, String> row : result.orders().rows()) {
            ordersModel.addRow(new Object[]{
                    row.get("event_time"), row.get("position_id"), row.get("order_id"), row.get("symbol"),
                    StockNameResolver.resolveChineseName(row.get("symbol")), row.get("side"),
                    row.get("order_status"), row.get("quantity"), row.get("requested_price"),
                    row.get("executed_price"), row.get("trade_mode"), row.get("reason"), row.get("message")
            });
        }
    }

    private void renderSummary(ImportResult result) {
        int completedCount = result.completed().rows().size();
        long wins = result.completed().rows().stream()
                .filter(row -> "WIN".equalsIgnoreCase(row.get("outcome")) || "true".equalsIgnoreCase(row.get("is_win")))
                .count();
        double netPnL = result.completed().rows().stream().mapToDouble(row -> number(row.get("net_pnl"))).sum();
        long buys = result.orders().rows().stream().filter(row -> "BUY".equalsIgnoreCase(row.get("side"))).count();
        long sells = result.orders().rows().stream().filter(row -> "SELL".equalsIgnoreCase(row.get("side"))).count();
        long openJourneys = result.journeys().stream().filter(journey -> journey.exitOrder == null).count();
        double winRate = completedCount > 0 ? (double) wins / completedCount * 100.0 : 0.0;

        summaryArea.setText(String.format("""
                匯入來源：%s
                日期：%s

                訂單筆數：%d（買 %d / 賣 %d）
                完成交易：%d
                勝率：%.1f%%
                淨損益：%.2f
                推估未平倉/未配對流程：%d

                注意：
                - 若買單多於賣單，代表當天可能仍有未平倉或 CSV 不完整。
                - 若 trade_mode 不是 DAY_TRADE，代表自動監控當沖語意仍需檢查。
                - 未配對頁可用來確認缺少 setup、缺少平倉或被拒單的流程。
                """,
                result.directory(), result.dateText(), result.orders().rows().size(), buys, sells,
                completedCount, winRate, netPnL, openJourneys));
    }

    private void renderTimelineRow(TradeJourney journey) {
        Map<String, String> source = firstNonNull(journey.completed, journey.setup, journey.entryOrder, journey.exitOrder);
        String symbol = value(source, "symbol");
        timelineModel.addRow(new Object[]{
                journey.status(),
                journey.id,
                symbol,
                StockNameResolver.resolveChineseName(symbol),
                firstValue(journey.completed, journey.setup, journey.entryOrder, "trade_mode"),
                firstValue(journey.completed, journey.setup, journey.entryOrder, "quantity"),
                firstValue(journey.completed, journey.setup, journey.entryOrder, "entry_time", "event_time"),
                firstValue(journey.completed, journey.setup, journey.entryOrder, "entry_price", "executed_price"),
                firstValue(journey.completed, journey.exitOrder, "exit_time", "event_time"),
                firstValue(journey.completed, journey.exitOrder, "exit_price", "executed_price"),
                firstValue(journey.completed, journey.exitOrder, "net_pnl", "realized_pnl"),
                firstValue(journey.setup, journey.entryOrder, "reason"),
                firstValue(journey.completed, journey.exitOrder, "exit_reason", "reason")
        });
    }

    private void renderUnmatchedRows(TradeJourney journey) {
        if (journey.setup == null && journey.entryOrder != null) {
            addUnmatched("缺少 setup", journey.entryOrder);
        }
        if (journey.entryOrder == null && journey.setup != null) {
            addUnmatched("缺少開倉單", journey.setup);
        }
        if (journey.exitOrder == null && journey.entryOrder != null) {
            addUnmatched("缺少平倉單", journey.entryOrder);
        }
        for (Map<String, String> order : journey.orders) {
            if (!"FILLED".equalsIgnoreCase(order.get("order_status"))) {
                addUnmatched("未成交/拒單", order);
            }
        }
    }

    private void addUnmatched(String type, Map<String, String> row) {
        String symbol = value(row, "symbol");
        unmatchedModel.addRow(new Object[]{
                type, value(row, "position_id"), value(row, "order_id"), symbol,
                StockNameResolver.resolveChineseName(symbol), firstValue(row, "event_time", "entry_time"),
                value(row, "quantity"), firstValue(row, "executed_price", "entry_price", "requested_price"),
                value(row, "reason")
        });
    }

    private void clearTables() {
        timelineModel.setRowCount(0);
        completedModel.setRowCount(0);
        setupsModel.setRowCount(0);
        ordersModel.setRowCount(0);
        unmatchedModel.setRowCount(0);
    }

    private String tradeId(Map<String, String> row) {
        String positionId = value(row, "position_id");
        if (!positionId.isBlank()) {
            return positionId;
        }
        String orderId = value(row, "order_id");
        return orderId.isBlank() ? "UNKNOWN_" + System.identityHashCode(row) : orderId;
    }

    private static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static double number(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @SafeVarargs
    private static Map<String, String> firstNonNull(Map<String, String>... rows) {
        for (Map<String, String> row : rows) {
            if (row != null) {
                return row;
            }
        }
        return Map.of();
    }

    private static String firstValue(Map<String, String> row, String... keys) {
        if (row == null) {
            return "";
        }
        for (String key : keys) {
            String value = value(row, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstValue(Map<String, String> first, Map<String, String> second, String... keys) {
        String value = firstValue(first, keys);
        return !value.isBlank() ? value : firstValue(second, keys);
    }

    private static String firstValue(
            Map<String, String> first,
            Map<String, String> second,
            Map<String, String> third,
            String... keys) {
        String value = firstValue(first, keys);
        if (!value.isBlank()) {
            return value;
        }
        value = firstValue(second, keys);
        return !value.isBlank() ? value : firstValue(third, keys);
    }

    private static String value(Map<String, String> row, String key) {
        return row != null ? row.getOrDefault(key, "") : "";
    }

    record ImportResult(
            Path directory,
            String dateText,
            CsvTable orders,
            CsvTable completed,
            CsvTable setups,
            List<TradeJourney> journeys) {
    }

    record CsvTable(Path path, List<String> headers, List<Map<String, String>> rows) {
    }

    static class TradeJourney {
        private final String id;
        private Map<String, String> setup;
        private Map<String, String> entryOrder;
        private Map<String, String> exitOrder;
        private Map<String, String> completed;
        private final List<Map<String, String>> orders = new ArrayList<>();

        private TradeJourney(String id) {
            this.id = id;
        }

        String status() {
            if (completed != null) {
                return "已平倉";
            }
            if (entryOrder != null && exitOrder == null) {
                return "可能未平倉";
            }
            return "未配對";
        }

        private String sortTime() {
            return firstValue(completed, setup, entryOrder, "closed_time", "entry_time", "event_time");
        }
    }
}
