package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.DataSourceManager;
import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.finmind.FinMindApiUsage;
import com.dreamhouse.trading.core.finmind.FinMindClient;
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.core.finmind.FinMindRequest;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.ui.UIAutoScaler;
import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FinMindApiDock extends JPanel {

    private final DataSourceManager dataSourceManager;
    private final Consumer<List<Bar>> chartBarsConsumer;
    private final Consumer<String> statusConsumer;
    private final JComboBox<FinMindDataset.Category> categoryCombo = new JComboBox<>(queryableCategories());
    private final JComboBox<FinMindDataset> datasetCombo = new JComboBox<>(
            datasetsForCategory((FinMindDataset.Category) categoryCombo.getSelectedItem()));
    private final JTextField dataIdField = new JTextField("2330", 8);
    private final JTextField startDateField = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField endDateField = new JTextField(LocalDate.now().toString(), 10);
    private final JLabel usageLabel = new JLabel("API 已呼叫 / 呼叫額度：0 / --");
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable resultTable = new JTable(tableModel);
    private final JTextArea rawArea = new JTextArea();
    private JsonNode lastQueryRoot;
    private FinMindDataset lastQueryDataset;
    private String lastQueryDataId;
    private LocalDate lastQueryStartDate;
    private LocalDate lastQueryEndDate;

    public FinMindApiDock(DataSourceManager dataSourceManager) {
        this(dataSourceManager, null, null);
    }

    public FinMindApiDock(DataSourceManager dataSourceManager,
                          Consumer<List<Bar>> chartBarsConsumer,
                          Consumer<String> statusConsumer) {
        this.dataSourceManager = dataSourceManager;
        this.chartBarsConsumer = chartBarsConsumer;
        this.statusConsumer = statusConsumer;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("FinMind API 查詢"));

        add(createToolbar(), BorderLayout.NORTH);

        rawArea.setEditable(false);
        rawArea.setLineWrap(false);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setFillsViewportHeight(true);
        resultTable.putClientProperty(UIAutoScaler.AUTO_RESIZE_MODE_PROPERTY, JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resultTable.setDefaultRenderer(Object.class, new TooltipCellRenderer());
        resultTable.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                scheduleResizeResultColumns();
            }
        });
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(resultTable),
                new JScrollPane(rawArea));
        splitPane.setResizeWeight(0.65);
        add(splitPane, BorderLayout.CENTER);

        usageLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JButton writeSqlButton = new JButton("寫入SQL");
        writeSqlButton.setToolTipText("將目前 FinMind 查詢結果寫入 MarketDataCollector SQL");
        writeSqlButton.addActionListener(e -> writeLastResultToSql());
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(usageLabel, BorderLayout.CENTER);
        footer.add(writeSqlButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        updateUsageLabel(null);
        UIAutoScaler.install(this);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new GridBagLayout());
        categoryCombo.addActionListener(e -> updateDatasetOptions());
        datasetCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null
                    ? ""
                    : value.displayNameZh() + " / " + value.apiName() + "（" + value.tierDisplayNameZh() + "）");
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });

        JButton queryButton = new JButton("查詢資料");
        JButton datalistButton = new JButton("資料欄位");
        JButton translationButton = new JButton("欄位翻譯");
        JButton usageButton = new JButton("用量");
        queryButton.setToolTipText("查詢資料");
        datalistButton.setToolTipText("資料欄位");
        translationButton.setToolTipText("欄位翻譯");
        usageButton.setToolTipText("API 用量");

        queryButton.addActionListener(e -> queryData());
        datalistButton.addActionListener(e -> queryDatalist());
        translationButton.addActionListener(e -> queryTranslation());
        usageButton.addActionListener(e -> refreshUsage());

        JLabel categoryLabel = new JLabel("分類");
        JLabel datasetLabel = new JLabel("資料集");
        JLabel dataIdLabel = new JLabel("股票代碼");
        JLabel startLabel = new JLabel("開始日期");
        JLabel endLabel = new JLabel("結束日期");
        Runnable rebuild = () -> rebuildToolbar(
                toolbar,
                categoryLabel,
                datasetLabel,
                dataIdLabel,
                startLabel,
                endLabel,
                queryButton,
                datalistButton,
                translationButton,
                usageButton);
        toolbar.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rebuild.run();
            }
        });
        rebuild.run();
        SwingUtilities.invokeLater(rebuild);
        return toolbar;
    }

    private void rebuildToolbar(JPanel toolbar,
                                JLabel categoryLabel,
                                JLabel datasetLabel,
                                JLabel dataIdLabel,
                                JLabel startLabel,
                                JLabel endLabel,
                                JButton queryButton,
                                JButton datalistButton,
                                JButton translationButton,
                                JButton usageButton) {
        int width = effectiveToolbarWidth(toolbar);
        boolean compact = width <= 0 || width < 1300;
        boolean veryCompact = width > 0 && width < 620;

        dataIdField.setColumns(veryCompact ? 5 : 7);
        startDateField.setColumns(veryCompact ? 8 : 10);
        endDateField.setColumns(veryCompact ? 8 : 10);
        categoryCombo.setMinimumSize(new Dimension(96, categoryCombo.getPreferredSize().height));
        categoryCombo.setPreferredSize(new Dimension(compact ? 160 : 140, categoryCombo.getPreferredSize().height));
        datasetCombo.setMinimumSize(new Dimension(120, datasetCombo.getPreferredSize().height));
        datasetCombo.setPreferredSize(new Dimension(compact ? 360 : 420, datasetCombo.getPreferredSize().height));

        queryButton.setText(compact ? "查詢" : "查詢資料");
        datalistButton.setText(compact ? "欄位" : "資料欄位");
        translationButton.setText(compact ? "翻譯" : "欄位翻譯");
        usageButton.setText(compact ? "用量" : "API 用量");

        toolbar.removeAll();
        if (compact) {
            addToolbarRow(toolbar, categoryLabel, 0, categoryCombo, 5);
            addToolbarRow(toolbar, datasetLabel, 1, datasetCombo, 5);
            addToolbarField(toolbar, 0, 2, dataIdLabel, dataIdField);
            addToolbarField(toolbar, 2, 2, startLabel, startDateField);
            addToolbarField(toolbar, 4, 2, endLabel, endDateField);
            JButton[] buttons = {queryButton, datalistButton, translationButton, usageButton};
            for (int i = 0; i < buttons.length; i++) {
                addToolbarButton(toolbar, buttons[i], i, 3);
            }
        } else {
            int x = 0;
            addToolbarLabel(toolbar, categoryLabel, x++, 0);
            addToolbarComponent(toolbar, categoryCombo, x++, 0, 0.0, 1);
            addToolbarLabel(toolbar, datasetLabel, x++, 0);
            addToolbarComponent(toolbar, datasetCombo, x++, 0, 1.0, 1);
            addToolbarLabel(toolbar, dataIdLabel, x++, 0);
            addToolbarComponent(toolbar, dataIdField, x++, 0, 0.0, 1);
            addToolbarLabel(toolbar, startLabel, x++, 0);
            addToolbarComponent(toolbar, startDateField, x++, 0, 0.0, 1);
            addToolbarLabel(toolbar, endLabel, x++, 0);
            addToolbarComponent(toolbar, endDateField, x++, 0, 0.0, 1);
            addToolbarButton(toolbar, queryButton, x++, 0);
            addToolbarButton(toolbar, datalistButton, x++, 0);
            addToolbarButton(toolbar, translationButton, x++, 0);
            addToolbarButton(toolbar, usageButton, x, 0);
        }
        toolbar.revalidate();
        toolbar.repaint();
    }

    private int effectiveToolbarWidth(JPanel toolbar) {
        int visibleWidth = toolbar.getVisibleRect().width;
        if (visibleWidth > 0) {
            return visibleWidth;
        }
        int parentWidth = toolbar.getParent() != null ? toolbar.getParent().getWidth() : 0;
        if (parentWidth > 0) {
            return parentWidth;
        }
        return toolbar.getWidth();
    }

    private void addToolbarRow(JPanel toolbar, JLabel label, int y, java.awt.Component component, int gridWidth) {
        addToolbarLabel(toolbar, label, 0, y);
        addToolbarComponent(toolbar, component, 1, y, 1.0, gridWidth);
    }

    private void addToolbarField(JPanel toolbar, int x, int y, JLabel label, java.awt.Component field) {
        addToolbarLabel(toolbar, label, x, y);
        addToolbarComponent(toolbar, field, x + 1, y, 1.0, 1);
    }

    private void addToolbarLabel(JPanel toolbar, JLabel label, int x, int y) {
        GridBagConstraints constraints = toolbarConstraints(x, y, 0.0, 1);
        constraints.fill = GridBagConstraints.NONE;
        toolbar.add(label, constraints);
    }

    private void addToolbarButton(JPanel toolbar, JButton button, int x, int y) {
        addToolbarComponent(toolbar, button, x, y, 0.25, 1);
    }

    private void addToolbarComponent(JPanel toolbar, java.awt.Component component, int x, int y, double weightX, int gridWidth) {
        GridBagConstraints constraints = toolbarConstraints(x, y, weightX, gridWidth);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        toolbar.add(component, constraints);
    }

    private GridBagConstraints toolbarConstraints(int x, int y, double weightX, int gridWidth) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = gridWidth;
        constraints.weightx = weightX;
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private void queryData() {
        FinMindDataset dataset = selectedDataset();
        LocalDate startDate = parseDate(startDateField.getText());
        LocalDate endDate = parseDate(endDateField.getText());
        LocalDate singleDayDate = singleDayQueryDate(dataset, startDate, endDate);
        lastQueryDataset = dataset;
        lastQueryDataId = dataset.isDataIdDataset() ? normalizedDataId() : null;
        lastQueryStartDate = dataset.isSingleDayQuery() ? singleDayDate : startDate;
        lastQueryEndDate = dataset.isSingleDayQuery() ? null : endDate;

        runApiTask(dataset, () -> newClient().queryData(FinMindRequest.dataset(dataset)
                .dataId(lastQueryDataId)
                .startDate(lastQueryStartDate)
                .endDate(lastQueryEndDate)
                .build()));
    }

    private void queryDatalist() {
        clearLastSqlWritableResult();
        runApiTask(null, () -> newClient().queryDatalist(selectedDataset()));
    }

    private void queryTranslation() {
        clearLastSqlWritableResult();
        runApiTask(null, () -> newClient().queryTranslation(selectedDataset()));
    }

    private void refreshUsage() {
        SwingWorker<FinMindApiUsage, Void> worker = new SwingWorker<>() {
            @Override
            protected FinMindApiUsage doInBackground() {
                return newClient().fetchApiUsage();
            }

            @Override
            protected void done() {
                try {
                    updateUsageLabel(get());
                } catch (Exception e) {
                    showError(e);
                }
            }
        };
        worker.execute();
    }

    private void runApiTask(FinMindDataset dataset, ApiTask task) {
        setBusy(true);
        SwingWorker<JsonNode, Void> worker = new SwingWorker<>() {
            @Override
            protected JsonNode doInBackground() {
                return task.run();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    JsonNode root = get();
                    if (dataset != null) {
                        lastQueryRoot = root;
                    }
                    renderJson(root, dataset);
                    updateUsageLabel(null);
                    maybeLoadKBarToChart(root, dataset);
                } catch (Exception e) {
                    showError(e);
                }
            }
        };
        worker.execute();
    }

    private void clearLastSqlWritableResult() {
        lastQueryRoot = null;
        lastQueryDataset = null;
        lastQueryDataId = null;
        lastQueryStartDate = null;
        lastQueryEndDate = null;
    }

    private void writeLastResultToSql() {
        if (lastQueryDataset == null || lastQueryRoot == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "請先查詢 FinMind API，再寫入 SQL。",
                    "FinMind 寫入 SQL",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JsonNode data = lastQueryRoot.has("data") ? lastQueryRoot.get("data") : lastQueryRoot;
        if (data == null || !data.isArray() || data.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "目前查詢結果沒有可寫入的資料列。",
                    "FinMind 寫入 SQL",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        setBusy(true);
        notifyStatus("正在將 FinMind " + lastQueryDataset.apiName() + " 寫入 SQL...");
        SwingWorker<MarketDataCollectorRepository.FinMindSqlWriteResult, Void> worker = new SwingWorker<>() {
            @Override
            protected MarketDataCollectorRepository.FinMindSqlWriteResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    return repository.writeFinMindDatasetRows(
                            lastQueryDataset,
                            lastQueryDataId,
                            lastQueryStartDate,
                            lastQueryEndDate,
                            data);
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    MarketDataCollectorRepository.FinMindSqlWriteResult result = get();
                    String message = "FinMind 寫入 SQL 完成："
                            + result.tableName()
                            + " 原始 " + result.cachedRows()
                            + " 筆，策略表 " + result.marketRows()
                            + " 筆";
                    notifyStatus(message);
                    JOptionPane.showMessageDialog(
                            FinMindApiDock.this,
                            message,
                            "FinMind 寫入 SQL",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    showError(e);
                }
            }
        };
        worker.execute();
    }

    private void renderJson(JsonNode root, FinMindDataset dataset) {
        JsonNode data = root != null && root.has("data") ? root.get("data") : root;
        String note = emptyDataNote(data, dataset);
        rawArea.setText(note.isBlank()
                ? (root != null ? root.toPrettyString() : "")
                : note + System.lineSeparator() + System.lineSeparator() + (root != null ? root.toPrettyString() : ""));
        if (data == null || !data.isArray() || data.isEmpty()) {
            tableModel.setDataVector(new Object[][]{{note.isBlank() ? "查無資料" : note}}, new Object[]{"結果"});
            scheduleResizeResultColumns();
            notifyStatus(note.isBlank() ? "FinMind 查詢完成，但沒有資料" : note);
            return;
        }
        if (dataset == FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT) {
            renderTradingDailyReportSummary(data);
            return;
        }

        Set<String> columns = new LinkedHashSet<>();
        for (JsonNode row : data) {
            row.fieldNames().forEachRemaining(columns::add);
        }
        List<String> columnList = new ArrayList<>(columns);
        Object[][] rows = new Object[data.size()][columnList.size()];
        for (int i = 0; i < data.size(); i++) {
            JsonNode row = data.get(i);
            for (int j = 0; j < columnList.size(); j++) {
                JsonNode cell = row.get(columnList.get(j));
                rows[i][j] = cell == null || cell.isNull() ? "" : cell.asText();
            }
        }
        tableModel.setDataVector(rows, columnList.toArray());
        scheduleResizeResultColumns();
        notifyStatus("FinMind 查詢完成，共 " + data.size() + " 筆資料");
    }

    private void renderTradingDailyReportSummary(JsonNode data) {
        Map<String, BrokerTradingSummary> summaries = new LinkedHashMap<>();
        int rawRows = 0;
        for (JsonNode row : data) {
            rawRows++;
            String brokerName = readText(row, "securities_trader");
            String brokerId = readText(row, "securities_trader_id");
            String stockId = readText(row, "stock_id");
            String date = readText(row, "date");
            String key = brokerId + "|" + brokerName + "|" + stockId + "|" + date;
            BrokerTradingSummary summary = summaries.computeIfAbsent(key,
                    ignored -> new BrokerTradingSummary(brokerName, brokerId, stockId, date));
            summary.add(readLong(row, "buy"), readLong(row, "sell"));
        }

        List<BrokerTradingSummary> sorted = new ArrayList<>(summaries.values());
        sorted.sort(Comparator
                .comparingLong((BrokerTradingSummary summary) -> Math.abs(summary.netBuy())).reversed()
                .thenComparing(summary -> summary.brokerId));

        Object[][] rows = new Object[sorted.size()][8];
        for (int i = 0; i < sorted.size(); i++) {
            BrokerTradingSummary summary = sorted.get(i);
            rows[i][0] = summary.brokerName;
            rows[i][1] = summary.brokerId;
            rows[i][2] = summary.stockId;
            rows[i][3] = summary.date;
            rows[i][4] = summary.buy;
            rows[i][5] = summary.sell;
            rows[i][6] = summary.netBuy();
            rows[i][7] = summary.detailRows;
        }
        tableModel.setDataVector(rows, new Object[]{
                "券商", "券商代號", "股票代碼", "日期", "買進合計", "賣出合計", "買賣超", "明細筆數"
        });
        scheduleResizeResultColumns();
        notifyStatus("分點資料已彙總為 " + sorted.size() + " 筆券商資料，原始明細 " + rawRows + " 筆");
    }

    private void maybeLoadKBarToChart(JsonNode root, FinMindDataset dataset) {
        if (dataset != FinMindDataset.TAIWAN_STOCK_K_BAR || chartBarsConsumer == null) {
            return;
        }
        List<Bar> bars = parseKBarBars(root != null ? root.path("data") : null);
        if (bars.isEmpty()) {
            return;
        }
        chartBarsConsumer.accept(bars);
        notifyStatus("已載入 FinMind 分 K 到圖表，共 " + bars.size() + " 根");
    }

    private List<Bar> parseKBarBars(JsonNode dataArray) {
        List<Bar> bars = new ArrayList<>();
        if (dataArray == null || !dataArray.isArray()) {
            return bars;
        }
        for (JsonNode row : dataArray) {
            LocalDate date = parseDate(row.path("date").asText(null));
            if (date == null) {
                continue;
            }
            LocalDateTime timestamp = parseMinuteTimestamp(date, row.path("minute").asText("09:00"));
            double open = readDouble(row, "open", "Open");
            double high = readDouble(row, "high", "max", "Max", "High");
            double low = readDouble(row, "low", "min", "Min", "Low");
            double close = readDouble(row, "close", "Close");
            long volume = Math.round(readDouble(row, "volume", "Trading_Volume", "Trading_Volume_K"));
            if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
                continue;
            }
            bars.add(new Bar(timestamp, open, high, low, close, volume));
        }
        bars.sort(Comparator.comparing(Bar::getTimestamp));
        return bars;
    }

    private LocalDateTime parseMinuteTimestamp(LocalDate date, String minuteText) {
        String safeMinute = minuteText != null && !minuteText.isBlank() ? minuteText.trim() : "09:00";
        try {
            if (safeMinute.length() == 5) {
                return LocalDateTime.parse(date + " " + safeMinute + ":00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (safeMinute.length() >= 8) {
                return LocalDateTime.parse(date + " " + safeMinute.substring(0, 8),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception ignored) {
            return date.atTime(9, 0);
        }
        return date.atTime(9, 0);
    }

    private double readDouble(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value != null && !value.isNull()) {
                return value.asDouble();
            }
        }
        return 0.0;
    }

    private long readLong(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value != null && !value.isNull()) {
                return value.asLong();
            }
        }
        return 0L;
    }

    private String readText(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String emptyDataNote(JsonNode data, FinMindDataset dataset) {
        if (data == null || !data.isArray() || !data.isEmpty()) {
            return "";
        }
        if (dataset == FinMindDataset.TAIWAN_STOCK_K_BAR) {
            return "FinMind 回傳 0 筆分 K。TaiwanStockKBar 只提供單日資料，且今天盤中分 K 通常需等盤後更新後才會有資料；盤中請使用 MarketDataCollector。";
        }
        return "FinMind 回傳 0 筆資料，請確認股票代碼、日期區間與會員權限。";
    }

    private void scheduleResizeResultColumns() {
        resizeResultColumns();
        SwingUtilities.invokeLater(() -> {
            resizeResultColumns();
            SwingUtilities.invokeLater(this::resizeResultColumns);
        });
    }

    private void resizeResultColumns() {
        if (resultTable.getColumnCount() == 0) {
            return;
        }
        Runnable task = () -> {
            int tableWidth = resultTable.getVisibleRect().width;
            if (tableWidth <= 0 && resultTable.getParent() != null && resultTable.getParent().getWidth() > 0) {
                tableWidth = resultTable.getParent().getWidth();
            }
            if (tableWidth <= 0) {
                tableWidth = resultTable.getWidth();
            }
            if (tableWidth <= 0) {
                tableWidth = getWidth();
            }
            if (tableWidth <= 0) {
                return;
            }

            int columnCount = resultTable.getColumnCount();
            int[] weights = new int[columnCount];
            int totalWeight = 0;
            for (int i = 0; i < columnCount; i++) {
                String header = String.valueOf(resultTable.getColumnName(i));
                int weight = columnWeight(header);
                weights[i] = weight;
                totalWeight += weight;
            }
            int usableWidth = Math.max(240, tableWidth - 8);
            for (int i = 0; i < columnCount; i++) {
                TableColumn column = resultTable.getColumnModel().getColumn(i);
                int width = Math.max(48, usableWidth * weights[i] / Math.max(1, totalWeight));
                column.setPreferredWidth(width);
                column.setWidth(width);
                column.setMinWidth(Math.min(44, width));
            }
            resultTable.doLayout();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private int columnWeight(String header) {
        String normalized = header == null ? "" : header.toLowerCase();
        if (normalized.contains("industry") || normalized.contains("category")
                || normalized.contains("name") || normalized.contains("名稱")
                || normalized.contains("券商")) {
            return 18;
        }
        if (normalized.contains("summary") || normalized.contains("reason")
                || normalized.contains("msg") || normalized.contains("note")) {
            return 20;
        }
        if (normalized.contains("date") || normalized.contains("time")
                || normalized.contains("日期") || normalized.contains("時間")) {
            return 12;
        }
        if (normalized.contains("stock_id") || normalized.contains("股票代碼")
                || normalized.contains("id") || normalized.contains("代號")
                || normalized.contains("type")) {
            return 8;
        }
        if (normalized.contains("price") || normalized.contains("buy") || normalized.contains("sell")
                || normalized.contains("volume") || normalized.contains("open")
                || normalized.contains("high") || normalized.contains("low")
                || normalized.contains("close") || normalized.contains("合計")
                || normalized.contains("買賣超")) {
            return 10;
        }
        return 11;
    }

    private static class TooltipCellRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            java.awt.Component component = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            setToolTipText(value == null ? null : value.toString());
            return component;
        }
    }

    private void showError(Exception e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        rawArea.setText(message != null ? message : e.toString());
        tableModel.setDataVector(new Object[][]{{rawArea.getText()}}, new Object[]{"錯誤"});
        scheduleResizeResultColumns();
        notifyStatus("FinMind 查詢失敗：" + rawArea.getText());
        updateUsageLabel(null);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        if (SwingUtilities.isEventDispatchThread()) {
            categoryCombo.setEnabled(!busy);
            datasetCombo.setEnabled(!busy);
        } else {
            SwingUtilities.invokeLater(() -> {
                categoryCombo.setEnabled(!busy);
                datasetCombo.setEnabled(!busy);
            });
        }
    }

    private void updateUsageLabel(FinMindApiUsage usage) {
        String remote = usage != null ? usage.displayText() : "--";
        usageLabel.setText("API 已呼叫 / 呼叫額度：" + FinMindClient.getTotalRequestCount() + " / " + remote);
    }

    private FinMindClient newClient() {
        return new FinMindClient(dataSourceManager.getFinMindApiToken());
    }

    private FinMindDataset selectedDataset() {
        return (FinMindDataset) datasetCombo.getSelectedItem();
    }

    private FinMindDataset.Category selectedCategory() {
        return (FinMindDataset.Category) categoryCombo.getSelectedItem();
    }

    private void updateDatasetOptions() {
        FinMindDataset selected = selectedDataset();
        FinMindDataset.Category category = selectedCategory();
        FinMindDataset[] datasets = datasetsForCategory(category);
        datasetCombo.setModel(new DefaultComboBoxModel<>(datasets));
        if (selected != null && selected.category() == category) {
            datasetCombo.setSelectedItem(selected);
        } else if (datasets.length > 0) {
            datasetCombo.setSelectedIndex(0);
        }
    }

    private String normalizedDataId() {
        String value = dataIdField.getText();
        if (value == null) {
            return null;
        }
        return value.trim().replace(".TW", "");
    }

    private LocalDate singleDayQueryDate(FinMindDataset dataset, LocalDate startDate, LocalDate endDate) {
        if (dataset == null || !dataset.isSingleDayQuery()) {
            return startDate;
        }
        if (startDate != null) {
            return startDate;
        }
        if (endDate != null) {
            return endDate;
        }
        return LocalDate.now();
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private void notifyStatus(String message) {
        if (statusConsumer != null && message != null && !message.isBlank()) {
            statusConsumer.accept(message);
        }
    }

    private static FinMindDataset[] queryableDatasets() {
        return java.util.Arrays.stream(FinMindDataset.values())
                .filter(dataset -> !dataset.apiName().toLowerCase().contains("snapshot"))
                .toArray(FinMindDataset[]::new);
    }

    private static FinMindDataset.Category[] queryableCategories() {
        return java.util.Arrays.stream(queryableDatasets())
                .map(FinMindDataset::category)
                .distinct()
                .toArray(FinMindDataset.Category[]::new);
    }

    private static FinMindDataset[] datasetsForCategory(FinMindDataset.Category category) {
        if (category == null) {
            return queryableDatasets();
        }
        return java.util.Arrays.stream(queryableDatasets())
                .filter(dataset -> dataset.category() == category)
                .toArray(FinMindDataset[]::new);
    }

    @FunctionalInterface
    private interface ApiTask {
        JsonNode run();
    }

    private static class BrokerTradingSummary {
        private final String brokerName;
        private final String brokerId;
        private final String stockId;
        private final String date;
        private long buy;
        private long sell;
        private int detailRows;

        private BrokerTradingSummary(String brokerName, String brokerId, String stockId, String date) {
            this.brokerName = brokerName;
            this.brokerId = brokerId;
            this.stockId = stockId;
            this.date = date;
        }

        private void add(long buy, long sell) {
            this.buy += buy;
            this.sell += sell;
            this.detailRows++;
        }

        private long netBuy() {
            return buy - sell;
        }
    }
}
