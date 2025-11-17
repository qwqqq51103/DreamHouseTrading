package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.monitor.SignalMonitorConfig;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 當沖監控設定對話框
 */
public class DayTradingMonitorDialog extends JDialog {

    private final SignalMonitorConfig config;
    private boolean confirmed = false;

    // UI 元件
    private JSpinner scanIntervalSpinner;
    private JComboBox<Timeframe> timeframeCombo;
    private JSpinner minSignalIntervalSpinner;
    private JRadioButton defaultParamsRadio;
    private JRadioButton customParamsRadio;
    private JSpinner rsiPeriodSpinner;
    private JSpinner rsiOversoldSpinner;
    private JSpinner rsiOverboughtSpinner;

    /**
     * 建構子
     */
    public DayTradingMonitorDialog(Frame owner, SignalMonitorConfig config) {
        super(owner, I18n.get("monitor.dialog.title"), true);
        this.config = config;

        setLayout(new BorderLayout(10, 10));
        setSize(500, 500);
        setLocationRelativeTo(owner);

        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // 基本設定面板
        mainPanel.add(createBasicSettingsPanel());
        mainPanel.add(Box.createVerticalStrut(15));

        // 策略參數面板
        mainPanel.add(createStrategyParametersPanel());
        mainPanel.add(Box.createVerticalStrut(15));

        // 按鈕面板
        JPanel buttonPanel = createButtonPanel();

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // 初始化值
        loadConfig();
    }

    /**
     * 創建基本設定面板
     */
    private JPanel createBasicSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            I18n.get("monitor.basic.settings"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 掃描間隔
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(I18n.get("monitor.scan.interval") + ":"), gbc);

        gbc.gridx = 1;
        scanIntervalSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 60, 5));
        panel.add(scanIntervalSpinner, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel(I18n.get("monitor.seconds")), gbc);

        // 時間週期
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel(I18n.get("monitor.timeframe") + ":"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        timeframeCombo = new JComboBox<>(new Timeframe[]{
            Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30
        });
        panel.add(timeframeCombo, gbc);

        // 最小信號間隔
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(new JLabel(I18n.get("monitor.min.signal.interval") + ":"), gbc);

        gbc.gridx = 1;
        minSignalIntervalSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        panel.add(minSignalIntervalSpinner, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel(I18n.get("monitor.minutes")), gbc);

        return panel;
    }

    /**
     * 創建策略參數面板
     */
    private JPanel createStrategyParametersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            I18n.get("monitor.strategy.parameters"),
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 參數選擇
        ButtonGroup paramGroup = new ButtonGroup();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        defaultParamsRadio = new JRadioButton(I18n.get("monitor.default.params"), true);
        defaultParamsRadio.addActionListener(e -> toggleCustomParams());
        paramGroup.add(defaultParamsRadio);
        panel.add(defaultParamsRadio, gbc);

        gbc.gridy = 1;
        customParamsRadio = new JRadioButton(I18n.get("monitor.custom.params"));
        customParamsRadio.addActionListener(e -> toggleCustomParams());
        paramGroup.add(customParamsRadio);
        panel.add(customParamsRadio, gbc);

        // RSI 週期
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        JLabel rsiPeriodLabel = new JLabel("  " + I18n.get("monitor.rsi.period") + ":");
        panel.add(rsiPeriodLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        rsiPeriodSpinner = new JSpinner(new SpinnerNumberModel(14, 5, 50, 1));
        rsiPeriodSpinner.setEnabled(false);
        panel.add(rsiPeriodSpinner, gbc);

        // RSI 超賣
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        JLabel rsiOversoldLabel = new JLabel("  " + I18n.get("monitor.rsi.oversold") + ":");
        panel.add(rsiOversoldLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        rsiOversoldSpinner = new JSpinner(new SpinnerNumberModel(30.0, 10.0, 50.0, 5.0));
        rsiOversoldSpinner.setEnabled(false);
        panel.add(rsiOversoldSpinner, gbc);

        // RSI 超買
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        JLabel rsiOverboughtLabel = new JLabel("  " + I18n.get("monitor.rsi.overbought") + ":");
        panel.add(rsiOverboughtLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        rsiOverboughtSpinner = new JSpinner(new SpinnerNumberModel(70.0, 50.0, 90.0, 5.0));
        rsiOverboughtSpinner.setEnabled(false);
        panel.add(rsiOverboughtSpinner, gbc);

        return panel;
    }

    /**
     * 創建按鈕面板
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton okButton = new JButton(I18n.get("dialog.ok"));
        okButton.addActionListener(e -> onOk());

        JButton cancelButton = new JButton(I18n.get("dialog.cancel"));
        cancelButton.addActionListener(e -> onCancel());

        panel.add(okButton);
        panel.add(cancelButton);

        return panel;
    }

    /**
     * 切換自訂參數
     */
    private void toggleCustomParams() {
        boolean custom = customParamsRadio.isSelected();
        rsiPeriodSpinner.setEnabled(custom);
        rsiOversoldSpinner.setEnabled(custom);
        rsiOverboughtSpinner.setEnabled(custom);
    }

    /**
     * 載入配置
     */
    private void loadConfig() {
        scanIntervalSpinner.setValue(config.getScanIntervalSeconds());
        timeframeCombo.setSelectedItem(config.getTimeframe());
        minSignalIntervalSpinner.setValue(config.getMinSignalIntervalMinutes());

        if (config.getRsiPeriod() != null) {
            customParamsRadio.setSelected(true);
            rsiPeriodSpinner.setValue(config.getRsiPeriod());
            rsiOversoldSpinner.setValue(config.getRsiOversold() != null ? config.getRsiOversold() : 30.0);
            rsiOverboughtSpinner.setValue(config.getRsiOverbought() != null ? config.getRsiOverbought() : 70.0);
            toggleCustomParams();
        }
    }

    /**
     * 確認
     */
    private void onOk() {
        // 保存配置
        config.setScanIntervalSeconds((Integer) scanIntervalSpinner.getValue());
        config.setTimeframe((Timeframe) timeframeCombo.getSelectedItem());
        config.setMinSignalIntervalMinutes((Integer) minSignalIntervalSpinner.getValue());

        if (customParamsRadio.isSelected()) {
            config.setRsiPeriod((Integer) rsiPeriodSpinner.getValue());
            config.setRsiOversold((Double) rsiOversoldSpinner.getValue());
            config.setRsiOverbought((Double) rsiOverboughtSpinner.getValue());
        } else {
            config.setRsiPeriod(null);
            config.setRsiOversold(null);
            config.setRsiOverbought(null);
        }

        confirmed = true;
        dispose();
    }

    /**
     * 取消
     */
    private void onCancel() {
        confirmed = false;
        dispose();
    }

    /**
     * 是否確認
     */
    public boolean isConfirmed() {
        return confirmed;
    }
}
