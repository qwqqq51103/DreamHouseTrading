package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.DataSourceManager;
import com.dreamhouse.trading.core.finmind.FinMindApiUsage;
import com.dreamhouse.trading.core.finmind.FinMindClient;
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.core.finmind.FinMindRequest;
import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FinMindApiDock extends JPanel {

    private final DataSourceManager dataSourceManager;
    private final JComboBox<FinMindDataset> datasetCombo = new JComboBox<>(queryableDatasets());
    private final JTextField dataIdField = new JTextField("2330", 8);
    private final JTextField startDateField = new JTextField(LocalDate.now().minusDays(7).toString(), 10);
    private final JTextField endDateField = new JTextField(LocalDate.now().toString(), 10);
    private final JLabel usageLabel = new JLabel("API 已呼叫 / 呼叫額度：0 / --");
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable resultTable = new JTable(tableModel);
    private final JTextArea rawArea = new JTextArea();

    public FinMindApiDock(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("FinMind API 查詢"));

        add(createToolbar(), BorderLayout.NORTH);

        rawArea.setEditable(false);
        rawArea.setLineWrap(false);
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(resultTable),
                new JScrollPane(rawArea));
        splitPane.setResizeWeight(0.65);
        add(splitPane, BorderLayout.CENTER);

        usageLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(usageLabel, BorderLayout.SOUTH);
        updateUsageLabel(null);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
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
        JButton datalistButton = new JButton("資料清單");
        JButton translationButton = new JButton("欄位翻譯");
        JButton usageButton = new JButton("使用量");

        queryButton.addActionListener(e -> queryData());
        datalistButton.addActionListener(e -> queryDatalist());
        translationButton.addActionListener(e -> queryTranslation());
        usageButton.addActionListener(e -> refreshUsage());

        toolbar.add(new JLabel("資料集"));
        toolbar.add(datasetCombo);
        toolbar.add(new JLabel("股票代碼"));
        toolbar.add(dataIdField);
        toolbar.add(new JLabel("開始日期"));
        toolbar.add(startDateField);
        toolbar.add(new JLabel("結束日期"));
        toolbar.add(endDateField);
        toolbar.add(queryButton);
        toolbar.add(datalistButton);
        toolbar.add(translationButton);
        toolbar.add(usageButton);
        return toolbar;
    }

    private void queryData() {
        FinMindDataset dataset = selectedDataset();
        runApiTask(() -> newClient().queryData(FinMindRequest.dataset(dataset)
                .dataId(dataset.isDataIdDataset() ? dataIdField.getText().trim() : null)
                .startDate(parseDate(startDateField.getText()))
                .endDate(parseDate(endDateField.getText()))
                .build()));
    }

    private void queryDatalist() {
        runApiTask(() -> newClient().queryDatalist(selectedDataset()));
    }

    private void queryTranslation() {
        runApiTask(() -> newClient().queryTranslation(selectedDataset()));
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

    private void runApiTask(ApiTask task) {
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
                    renderJson(get());
                    updateUsageLabel(null);
                } catch (Exception e) {
                    showError(e);
                }
            }
        };
        worker.execute();
    }

    private void renderJson(JsonNode root) {
        JsonNode data = root != null && root.has("data") ? root.get("data") : root;
        rawArea.setText(root != null ? root.toPrettyString() : "");
        if (data == null || !data.isArray() || data.isEmpty()) {
            tableModel.setDataVector(new Object[0][0], new Object[]{"結果"});
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
    }

    private void showError(Exception e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        rawArea.setText(message != null ? message : e.toString());
        updateUsageLabel(null);
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        if (SwingUtilities.isEventDispatchThread()) {
            datasetCombo.setEnabled(!busy);
        } else {
            SwingUtilities.invokeLater(() -> datasetCombo.setEnabled(!busy));
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

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private static FinMindDataset[] queryableDatasets() {
        return java.util.Arrays.stream(FinMindDataset.values())
                .filter(dataset -> !dataset.apiName().toLowerCase().contains("snapshot"))
                .toArray(FinMindDataset[]::new);
    }

    @FunctionalInterface
    private interface ApiTask {
        JsonNode run();
    }
}
