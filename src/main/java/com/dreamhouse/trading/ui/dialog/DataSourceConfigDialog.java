package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.DataSourceManager;
import com.dreamhouse.trading.core.DataSourceManager.DataSourceType;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 數據源配置對話框
 * 允許用戶選擇數據源類型並配置API密鑰
 */
public class DataSourceConfigDialog extends JDialog {

    private final DataSourceManager dataSourceManager;
    private DataSourceType selectedType;

    private JComboBox<DataSourceType> dataSourceCombo;
    private JTextField apiKeyField;
    private JLabel apiKeyLabel;
    private JPanel apiKeyPanel;
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

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initComponents() {
        // 數據源選擇
        dataSourceCombo = new JComboBox<>(DataSourceType.values());
        dataSourceCombo.setSelectedItem(selectedType);
        dataSourceCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                         int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataSourceType) {
                    DataSourceType type = (DataSourceType) value;
                    setText(type.getDisplayNameZh());
                }
                return this;
            }
        });

        // API密鑰輸入
        apiKeyLabel = new JLabel(I18n.get("dialog.datasource.apikey") + ":");
        apiKeyField = new JTextField(30);
        apiKeyField.setText(dataSourceManager.getApiKey(selectedType));

        testButton = new JButton(I18n.get("dialog.datasource.test"));
        confirmButton = new JButton(I18n.get("dialog.confirm"));
        cancelButton = new JButton(I18n.get("dialog.cancel"));

        // API密鑰面板（初始隱藏）
        apiKeyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        apiKeyPanel.add(apiKeyLabel);
        apiKeyPanel.add(apiKeyField);
        apiKeyPanel.add(testButton);

        updateApiKeyPanelVisibility();
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 數據源選擇區域
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel(I18n.get("dialog.datasource.type") + ":"));
        typePanel.add(dataSourceCombo);
        mainPanel.add(typePanel);

        mainPanel.add(Box.createVerticalStrut(10));

        // 描述區域
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.datasource.description")));

        JTextArea descArea = new JTextArea(getDataSourceDescription(selectedType), 4, 40);
        descArea.setEditable(false);
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setBackground(UIManager.getColor("Panel.background"));
        descPanel.add(new JScrollPane(descArea), BorderLayout.CENTER);

        mainPanel.add(descPanel);

        mainPanel.add(Box.createVerticalStrut(10));

        // API密鑰面板
        mainPanel.add(apiKeyPanel);

        add(mainPanel, BorderLayout.CENTER);

        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        dataSourceCombo.addActionListener(e -> {
            selectedType = (DataSourceType) dataSourceCombo.getSelectedItem();
            updateApiKeyPanelVisibility();
            updateDescription();
        });

        testButton.addActionListener(e -> testConnection());
        confirmButton.addActionListener(e -> confirmSelection());
        cancelButton.addActionListener(e -> dispose());

        // 回車鍵確認
        apiKeyField.addActionListener(e -> confirmSelection());
    }

    private void updateApiKeyPanelVisibility() {
        boolean requiresKey = dataSourceManager.requiresApiKey(selectedType);
        apiKeyPanel.setVisible(requiresKey);
        testButton.setVisible(requiresKey);

        // 更新API密鑰欄位顯示當前選擇的數據源的密鑰
        if (requiresKey) {
            apiKeyField.setText(dataSourceManager.getApiKey(selectedType));
        }

        pack();
    }

    private void updateDescription() {
        // 找到描述區域並更新文字
        Container parent = apiKeyPanel.getParent();
        for (Component comp : parent.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                if (panel.getBorder() instanceof javax.swing.border.TitledBorder) {
                    for (Component innerComp : panel.getComponents()) {
                        if (innerComp instanceof JScrollPane) {
                            JScrollPane scrollPane = (JScrollPane) innerComp;
                            JViewport viewport = scrollPane.getViewport();
                            if (viewport.getView() instanceof JTextArea) {
                                JTextArea textArea = (JTextArea) viewport.getView();
                                textArea.setText(getDataSourceDescription(selectedType));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private String getDataSourceDescription(DataSourceType type) {
        switch (type) {
            case SIMULATOR:
                return I18n.get("dialog.datasource.desc.simulator");

            case YAHOO_FINANCE:
                return I18n.get("dialog.datasource.desc.yahoo");

            case ALPHA_VANTAGE:
                return I18n.get("dialog.datasource.desc.alphavantage");

            case FINNHUB:
                return I18n.get("dialog.datasource.desc.finnhub");

            case IEX_CLOUD:
                return I18n.get("dialog.datasource.desc.iexcloud");

            case POLYGON:
                return I18n.get("dialog.datasource.desc.polygon");

            default:
                return "";
        }
    }

    private void testConnection() {
        String apiKey = apiKeyField.getText().trim();

        if (apiKey.isEmpty() || apiKey.equals("demo")) {
            JOptionPane.showMessageDialog(this,
                I18n.get("dialog.datasource.test.invalid"),
                I18n.get("dialog.datasource.test"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 顯示測試中提示
        JDialog progressDialog = new JDialog(this, I18n.get("dialog.datasource.test"), true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.add(new JLabel(I18n.get("dialog.datasource.test.testing")));
        progressDialog.add(contentPanel, BorderLayout.CENTER);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

        // 在背景執行緒中測試
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                // 簡單的驗證：檢查API key格式
                // 實際測試需要發送真實請求
                return apiKey.length() >= 8 && !apiKey.equals("demo");
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(DataSourceConfigDialog.this,
                            I18n.get("dialog.datasource.test.success"),
                            I18n.get("dialog.datasource.test"),
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(DataSourceConfigDialog.this,
                            I18n.get("dialog.datasource.test.failed"),
                            I18n.get("dialog.datasource.test"),
                            JOptionPane.ERROR_MESSAGE);
                    }
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

    private void confirmSelection() {
        // 如果需要API密鑰但沒有提供
        if (dataSourceManager.requiresApiKey(selectedType)) {
            String apiKey = apiKeyField.getText().trim();
            if (apiKey.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    I18n.get("dialog.datasource.apikey.required"),
                    I18n.get("dialog.datasource.title"),
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 保存API密鑰
            dataSourceManager.setApiKey(selectedType, apiKey);
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

    /**
     * 顯示對話框並返回是否確認
     */
    public static boolean showDialog(Frame parent, DataSourceManager dataSourceManager) {
        DataSourceConfigDialog dialog = new DataSourceConfigDialog(parent, dataSourceManager);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}
