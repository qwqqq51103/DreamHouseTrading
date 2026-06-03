package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.DataSourceManager;
import com.dreamhouse.trading.core.DataSourceManager.DataSourceType;
import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.finmind.FinMindApiUsage;
import com.dreamhouse.trading.core.finmind.FinMindClient;
import com.dreamhouse.trading.util.I18n;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Data source configuration dialog.
 */
public class DataSourceConfigDialog extends JDialog {

    private final DataSourceManager dataSourceManager;
    private DataSourceType selectedType;

    private JComboBox<DataSourceType> dataSourceCombo;
    private JTextArea descArea;
    private JTextField apiKeyField;
    private JPanel apiKeyPanel;
    private JTextField jdbcUrlField;
    private JTextField jdbcUserField;
    private JPasswordField jdbcPasswordField;
    private JPanel collectorPanel;
    private JButton testButton;
    private JButton confirmButton;
    private JButton cancelButton;

    private boolean confirmed = false;

    public DataSourceConfigDialog(Frame parent, DataSourceManager dataSourceManager) {
        super(parent, I18n.get("dialog.datasource.title"), true);
        this.dataSourceManager = dataSourceManager;
        this.selectedType = dataSourceManager.getCurrentType();

        initComponents();
        layoutComponents();
        setupListeners();
        updateConfigPanelVisibility();

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initComponents() {
        dataSourceCombo = new JComboBox<>(DataSourceType.values());
        dataSourceCombo.setSelectedItem(selectedType);
        dataSourceCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataSourceType type) {
                    setText(type.getDisplayNameZh());
                }
                return this;
            }
        });

        descArea = new JTextArea(getDataSourceDescription(selectedType), 4, 46);
        descArea.setEditable(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setBackground(UIManager.getColor("Panel.background"));

        apiKeyField = new JTextField(dataSourceManager.getFinMindApiToken(), 34);
        apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        apiKeyPanel.add(new JLabel(I18n.get("dialog.datasource.apikey") + ":"));
        apiKeyPanel.add(apiKeyField);

        jdbcUrlField = new JTextField(dataSourceManager.getMarketCollectorJdbcUrl(), 38);
        jdbcUserField = new JTextField(dataSourceManager.getMarketCollectorUser(), 18);
        jdbcPasswordField = new JPasswordField(dataSourceManager.getMarketCollectorPassword(), 18);
        collectorPanel = createCollectorPanel();

        testButton = new JButton(I18n.get("dialog.datasource.test"));
        confirmButton = new JButton(I18n.get("dialog.confirm"));
        cancelButton = new JButton(I18n.get("dialog.cancel"));
    }

    private JPanel createCollectorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("MarketDataCollector MySQL"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("JDBC URL:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(jdbcUrlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("User:"), gbc);
        gbc.gridx = 1;
        panel.add(jdbcUserField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        panel.add(jdbcPasswordField, gbc);

        return panel;
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel(I18n.get("dialog.datasource.type") + ":"));
        typePanel.add(dataSourceCombo);
        mainPanel.add(typePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.datasource.description")));
        descPanel.add(new JScrollPane(descArea), BorderLayout.CENTER);
        mainPanel.add(descPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        mainPanel.add(apiKeyPanel);
        mainPanel.add(collectorPanel);

        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.add(testButton);
        mainPanel.add(testPanel);

        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        dataSourceCombo.addActionListener(e -> {
            selectedType = (DataSourceType) dataSourceCombo.getSelectedItem();
            updateConfigPanelVisibility();
            descArea.setText(getDataSourceDescription(selectedType));
        });

        testButton.addActionListener(e -> testConnection());
        confirmButton.addActionListener(e -> confirmSelection());
        cancelButton.addActionListener(e -> dispose());
        apiKeyField.addActionListener(e -> confirmSelection());
    }

    private void updateConfigPanelVisibility() {
        boolean finMind = selectedType == DataSourceType.FINMIND;
        boolean collector = selectedType == DataSourceType.MARKET_COLLECTOR;
        apiKeyPanel.setVisible(finMind);
        collectorPanel.setVisible(collector);
        testButton.setVisible(finMind || collector);

        if (finMind) {
            apiKeyField.setText(dataSourceManager.getFinMindApiToken());
        } else if (collector) {
            jdbcUrlField.setText(dataSourceManager.getMarketCollectorJdbcUrl());
            jdbcUserField.setText(dataSourceManager.getMarketCollectorUser());
            jdbcPasswordField.setText(dataSourceManager.getMarketCollectorPassword());
        }
        pack();
    }

    private String getDataSourceDescription(DataSourceType type) {
        return switch (type) {
            case MARKET_COLLECTOR ->
                    "從本機 market_data MySQL 讀取 MarketDataCollector 寫入的 ticks/candlesticks，盤中雷達不呼叫 FinMind API。";
            case FINMIND ->
                    "FinMind 僅保留低頻資料用途，例如新聞、分點券商、盤後資料校正與 API usage；盤中即時行情不從此處取得。";
            case YAHOO_FINANCE ->
                    "Yahoo Finance 備用資料源，適合非台股盤中雷達或低頻檢視，不需要 API token。";
        };
    }

    private void testConnection() {
        JDialog progressDialog = createProgressDialog();
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String detail = "";

            @Override
            protected Boolean doInBackground() throws Exception {
                if (selectedType == DataSourceType.MARKET_COLLECTOR) {
                    try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                            jdbcUrlField.getText().trim(),
                            jdbcUserField.getText().trim(),
                            new String(jdbcPasswordField.getPassword()))) {
                        boolean available = repository.isAvailable();
                        detail = available ? "MarketDataCollector DB 可連線" : "MarketDataCollector DB 無法連線";
                        return available;
                    }
                }
                if (selectedType == DataSourceType.FINMIND) {
                    String apiKey = apiKeyField.getText().trim();
                    if (apiKey.isBlank()) {
                        detail = I18n.get("dialog.datasource.test.invalid");
                        return false;
                    }
                    FinMindClient client = new FinMindClient(apiKey);
                    boolean valid = client.testToken();
                    if (valid) {
                        FinMindApiUsage usage = client.fetchApiUsage();
                        detail = "API 已呼叫/呼叫次數: "
                                + FinMindClient.getTotalRequestCount()
                                + " / "
                                + usage.displayText();
                    }
                    return valid;
                }
                return true;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    boolean success = get();
                    JOptionPane.showMessageDialog(DataSourceConfigDialog.this,
                            success ? I18n.get("dialog.datasource.test.success") + "\n" + detail : detail,
                            I18n.get("dialog.datasource.test"),
                            success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DataSourceConfigDialog.this,
                            I18n.get("dialog.datasource.test.error") + ": " + e.getMessage(),
                            I18n.get("dialog.datasource.test"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private JDialog createProgressDialog() {
        JDialog progressDialog = new JDialog(this, I18n.get("dialog.datasource.test"), true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.add(new JLabel(I18n.get("dialog.datasource.test.testing")));
        progressDialog.add(contentPanel, BorderLayout.CENTER);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);
        return progressDialog;
    }

    private void confirmSelection() {
        if (selectedType == DataSourceType.FINMIND) {
            String apiKey = apiKeyField.getText().trim();
            if (apiKey.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        I18n.get("dialog.datasource.apikey.required"),
                        I18n.get("dialog.datasource.title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            dataSourceManager.setFinMindApiToken(apiKey);
        } else if (selectedType == DataSourceType.MARKET_COLLECTOR) {
            dataSourceManager.setMarketCollectorConfig(
                    jdbcUrlField.getText(),
                    jdbcUserField.getText(),
                    new String(jdbcPasswordField.getPassword()));
        }

        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public DataSourceType getSelectedType() {
        return selectedType;
    }

    public static boolean showDialog(Frame parent, DataSourceManager dataSourceManager) {
        DataSourceConfigDialog dialog = new DataSourceConfigDialog(parent, dataSourceManager);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}
