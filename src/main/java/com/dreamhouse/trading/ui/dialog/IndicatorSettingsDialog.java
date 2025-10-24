package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.IndicatorConfig;
import com.dreamhouse.trading.ui.dock.ChartDock;
import com.dreamhouse.trading.util.I18n;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * 指標參數設定對話框
 * 允許用戶自訂指標的週期、顏色等參數
 */
public class IndicatorSettingsDialog extends JDialog {
    
    private boolean confirmed = false;
    private ChartDock chartDock;
    
    // 指標參數
    private JSpinner smaPeriodSpinner;
    private JSpinner emaPeriodSpinner;
    private JSpinner rsiPeriodSpinner;
    private JSpinner macdFastSpinner;
    private JSpinner macdSlowSpinner;
    private JSpinner macdSignalSpinner;
    private JSpinner bollPeriodSpinner;
    private JSpinner bollMultiplierSpinner;
    private JSpinner kdKPeriodSpinner;
    private JSpinner kdDPeriodSpinner;
    private JSpinner adxPeriodSpinner;
    private JSpinner cciPeriodSpinner;
    private JSpinner wrPeriodSpinner;
    
    // 顏色選擇
    private JButton smaColorButton;
    private JButton emaColorButton;
    private JButton rsiColorButton;
    
    private Color smaColor = new Color(0, 123, 255);
    private Color emaColor = new Color(255, 140, 0);
    private Color rsiColor = new Color(255, 165, 0);
    
    public IndicatorSettingsDialog(Frame owner, ChartDock chartDock) {
        super(owner, I18n.get("dialog.indicator.settings.title"), true);
        this.chartDock = chartDock;
        initComponents();
        loadCurrentSettings();
        setLocationRelativeTo(owner);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 疊線指標設定
        mainPanel.add(createOverlayIndicatorPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 副圖指標設定
        mainPanel.add(createSubIndicatorPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        
        // 捲動面板
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
        
        // 按鈕面板
        add(createButtonPanel(), BorderLayout.SOUTH);
        
        pack();
        setMinimumSize(new Dimension(500, 600));
    }
    
    private JPanel createOverlayIndicatorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.indicator.overlay")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // SMA 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("SMA " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        smaPeriodSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
        panel.add(smaPeriodSpinner, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        smaColorButton = createColorButton(smaColor);
        panel.add(smaColorButton, gbc);
        row++;
        
        // EMA 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("EMA " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        emaPeriodSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
        panel.add(emaPeriodSpinner, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        emaColorButton = createColorButton(emaColor);
        panel.add(emaColorButton, gbc);
        row++;
        
        // BOLL 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("BOLL " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        bollPeriodSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        panel.add(bollPeriodSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("BOLL " + I18n.get("dialog.indicator.multiplier") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        bollMultiplierSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 5.0, 0.1));
        panel.add(bollMultiplierSpinner, gbc);
        
        return panel;
    }
    
    private JPanel createSubIndicatorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("dialog.indicator.sub")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // RSI 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("RSI " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        rsiPeriodSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        panel.add(rsiPeriodSpinner, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        rsiColorButton = createColorButton(rsiColor);
        panel.add(rsiColorButton, gbc);
        row++;
        
        // MACD 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("MACD Fast:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        macdFastSpinner = new JSpinner(new SpinnerNumberModel(12, 2, 50, 1));
        panel.add(macdFastSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("MACD Slow:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        macdSlowSpinner = new JSpinner(new SpinnerNumberModel(26, 5, 100, 1));
        panel.add(macdSlowSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("MACD Signal:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        macdSignalSpinner = new JSpinner(new SpinnerNumberModel(9, 2, 50, 1));
        panel.add(macdSignalSpinner, gbc);
        row++;
        
        // KD 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("KD K " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        kdKPeriodSpinner = new JSpinner(new SpinnerNumberModel(9, 2, 50, 1));
        panel.add(kdKPeriodSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("KD D " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        kdDPeriodSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 20, 1));
        panel.add(kdDPeriodSpinner, gbc);
        row++;
        
        // ADX 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("ADX " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        adxPeriodSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        panel.add(adxPeriodSpinner, gbc);
        row++;
        
        // CCI 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("CCI " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        cciPeriodSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        panel.add(cciPeriodSpinner, gbc);
        row++;
        
        // Williams %R 設定
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel("Williams %R " + I18n.get("dialog.indicator.period") + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        wrPeriodSpinner = new JSpinner(new SpinnerNumberModel(14, 2, 50, 1));
        panel.add(wrPeriodSpinner, gbc);
        
        return panel;
    }
    
    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton("    ");
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(50, 25));
        button.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, I18n.get("dialog.indicator.choose.color"), button.getBackground());
            if (newColor != null) {
                button.setBackground(newColor);
            }
        });
        return button;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JButton confirmButton = new JButton(I18n.get("dialog.confirm"));
        confirmButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        
        JButton cancelButton = new JButton(I18n.get("dialog.cancel"));
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        JButton resetButton = new JButton(I18n.get("dialog.reset"));
        resetButton.addActionListener(e -> resetToDefaults());
        
        panel.add(resetButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(confirmButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    private void loadCurrentSettings() {
        if (chartDock == null) {
            resetToDefaults();
            return;
        }
        
        // 載入 SMA 設定
        IndicatorConfig smaConfig = chartDock.getIndicatorConfig("SMA");
        if (smaConfig != null) {
            smaPeriodSpinner.setValue(smaConfig.getPeriod());
            smaColorButton.setBackground(smaConfig.getColor());
        }
        
        // 載入 EMA 設定
        IndicatorConfig emaConfig = chartDock.getIndicatorConfig("EMA");
        if (emaConfig != null) {
            emaPeriodSpinner.setValue(emaConfig.getPeriod());
            emaColorButton.setBackground(emaConfig.getColor());
        }
        
        // 載入 RSI 設定
        IndicatorConfig rsiConfig = chartDock.getIndicatorConfig("RSI");
        if (rsiConfig != null) {
            rsiPeriodSpinner.setValue(rsiConfig.getPeriod());
            rsiColorButton.setBackground(rsiConfig.getColor());
        }
        
        // 載入 MACD 設定
        IndicatorConfig macdConfig = chartDock.getIndicatorConfig("MACD");
        if (macdConfig != null) {
            macdFastSpinner.setValue(macdConfig.getFastPeriod());
            macdSlowSpinner.setValue(macdConfig.getSlowPeriod());
            macdSignalSpinner.setValue(macdConfig.getSignalPeriod());
        }
        
        // 載入 BOLL 設定
        IndicatorConfig bollConfig = chartDock.getIndicatorConfig("BOLL");
        if (bollConfig != null) {
            bollPeriodSpinner.setValue(bollConfig.getPeriod());
            bollMultiplierSpinner.setValue(bollConfig.getMultiplier());
        }
        
        // 載入 KD 設定
        IndicatorConfig kdConfig = chartDock.getIndicatorConfig("KD");
        if (kdConfig != null) {
            kdKPeriodSpinner.setValue(kdConfig.getPeriod());
            kdDPeriodSpinner.setValue(kdConfig.getPeriod2());
        }
        
        // 載入 ADX 設定
        IndicatorConfig adxConfig = chartDock.getIndicatorConfig("ADX");
        if (adxConfig != null) {
            adxPeriodSpinner.setValue(adxConfig.getPeriod());
        }
        
        // 載入 CCI 設定
        IndicatorConfig cciConfig = chartDock.getIndicatorConfig("CCI");
        if (cciConfig != null) {
            cciPeriodSpinner.setValue(cciConfig.getPeriod());
        }
        
        // 載入 WR 設定
        IndicatorConfig wrConfig = chartDock.getIndicatorConfig("WR");
        if (wrConfig != null) {
            wrPeriodSpinner.setValue(wrConfig.getPeriod());
        }
    }
    
    private void resetToDefaults() {
        smaPeriodSpinner.setValue(20);
        emaPeriodSpinner.setValue(20);
        rsiPeriodSpinner.setValue(14);
        macdFastSpinner.setValue(12);
        macdSlowSpinner.setValue(26);
        macdSignalSpinner.setValue(9);
        bollPeriodSpinner.setValue(20);
        bollMultiplierSpinner.setValue(2.0);
        kdKPeriodSpinner.setValue(9);
        kdDPeriodSpinner.setValue(3);
        adxPeriodSpinner.setValue(14);
        cciPeriodSpinner.setValue(14);
        wrPeriodSpinner.setValue(14);
        
        smaColorButton.setBackground(new Color(0, 123, 255));
        emaColorButton.setBackground(new Color(255, 140, 0));
        rsiColorButton.setBackground(new Color(255, 165, 0));
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    // Getters
    public int getSmaPeriod() { return (int) smaPeriodSpinner.getValue(); }
    public int getEmaPeriod() { return (int) emaPeriodSpinner.getValue(); }
    public int getRsiPeriod() { return (int) rsiPeriodSpinner.getValue(); }
    public int getMacdFast() { return (int) macdFastSpinner.getValue(); }
    public int getMacdSlow() { return (int) macdSlowSpinner.getValue(); }
    public int getMacdSignal() { return (int) macdSignalSpinner.getValue(); }
    public int getBollPeriod() { return (int) bollPeriodSpinner.getValue(); }
    public double getBollMultiplier() { return (double) bollMultiplierSpinner.getValue(); }
    public int getKdKPeriod() { return (int) kdKPeriodSpinner.getValue(); }
    public int getKdDPeriod() { return (int) kdDPeriodSpinner.getValue(); }
    public int getAdxPeriod() { return (int) adxPeriodSpinner.getValue(); }
    public int getCciPeriod() { return (int) cciPeriodSpinner.getValue(); }
    public int getWrPeriod() { return (int) wrPeriodSpinner.getValue(); }
    
    public Color getSmaColor() { return smaColorButton.getBackground(); }
    public Color getEmaColor() { return emaColorButton.getBackground(); }
    public Color getRsiColor() { return rsiColorButton.getBackground(); }
}

