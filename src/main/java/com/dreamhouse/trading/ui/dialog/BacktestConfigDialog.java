package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.backtest.*;
import com.dreamhouse.trading.core.backtest.strategies.*;
import com.dreamhouse.trading.util.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * 回測配置對話框
 * 用於設定回測參數和策略配置
 */
public class BacktestConfigDialog extends JDialog {
    
    private boolean confirmed = false;
    
    // 基本配置
    private JSpinner initialCapitalSpinner;
    private JSpinner commissionSpinner;
    private JSpinner slippageSpinner;
    
    // 策略選擇
    private JComboBox<StrategyItem> strategyComboBox;
    private JPanel strategyConfigPanel;
    private Map<String, JComponent> parameterComponents;
    
    // 當前選中的策略
    private Strategy selectedStrategy;
    
    /**
     * 構造函數
     */
    public BacktestConfigDialog(JFrame parent) {
        super(parent, "回測配置", true);
        this.parameterComponents = new HashMap<>();
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(parent);
    }
    
    /**
     * 初始化組件
     */
    private void initializeComponents() {
        // 基本配置
        initialCapitalSpinner = new JSpinner(new SpinnerNumberModel(100000.0, 1000.0, 10000000.0, 1000.0));
        commissionSpinner = new JSpinner(new SpinnerNumberModel(0.001, 0.0, 0.01, 0.0001));
        slippageSpinner = new JSpinner(new SpinnerNumberModel(0.0005, 0.0, 0.01, 0.0001));
        
        // 策略選擇
        strategyComboBox = new JComboBox<>();
        strategyComboBox.addItem(new StrategyItem("SimpleMovingAverageStrategy", "雙移動平均線策略"));
        strategyComboBox.addItem(new StrategyItem("RSIStrategy", "RSI 策略"));
        strategyComboBox.addItem(new StrategyItem("MACDStrategy", "MACD 策略"));
        strategyComboBox.addItem(new StrategyItem("BollingerBandsStrategy", "布林通道策略"));
        
        // 策略配置面板
        strategyConfigPanel = new JPanel(new MigLayout("fillx", "[right][fill]", ""));
        strategyConfigPanel.setBorder(BorderFactory.createTitledBorder("策略參數"));
        
        // 默認選擇第一個策略
        if (strategyComboBox.getItemCount() > 0) {
            strategyComboBox.setSelectedIndex(0);
            updateStrategyConfig();
        }
    }
    
    /**
     * 佈局組件
     */
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // 主面板
        JPanel mainPanel = new JPanel(new MigLayout("fillx", "[right][fill]", ""));
        
        // 基本配置
        JPanel basicConfigPanel = new JPanel(new MigLayout("fillx", "[right][fill]", ""));
        basicConfigPanel.setBorder(BorderFactory.createTitledBorder("基本配置"));
        
        basicConfigPanel.add(new JLabel("初始資金:"), "");
        basicConfigPanel.add(initialCapitalSpinner, "wrap");
        
        basicConfigPanel.add(new JLabel("手續費率:"), "");
        basicConfigPanel.add(commissionSpinner, "wrap");
        
        basicConfigPanel.add(new JLabel("滑點率:"), "");
        basicConfigPanel.add(slippageSpinner, "wrap");
        
        // 策略選擇
        JPanel strategySelectionPanel = new JPanel(new MigLayout("fillx", "[right][fill]", ""));
        strategySelectionPanel.setBorder(BorderFactory.createTitledBorder("策略選擇"));
        
        strategySelectionPanel.add(new JLabel("交易策略:"), "");
        strategySelectionPanel.add(strategyComboBox, "wrap");
        
        // 組合面板
        mainPanel.add(basicConfigPanel, "wrap, growx");
        mainPanel.add(strategySelectionPanel, "wrap, growx");
        mainPanel.add(strategyConfigPanel, "wrap, growx");
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton okButton = new JButton("確定");
        JButton cancelButton = new JButton("取消");
        
        okButton.addActionListener(e -> {
            confirmed = true;
            applyConfiguration();
            dispose();
        });
        
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 設定事件處理器
     */
    private void setupEventHandlers() {
        strategyComboBox.addActionListener(e -> updateStrategyConfig());
    }
    
    /**
     * 更新策略配置面板
     */
    private void updateStrategyConfig() {
        strategyConfigPanel.removeAll();
        parameterComponents.clear();
        
        StrategyItem selectedItem = (StrategyItem) strategyComboBox.getSelectedItem();
        if (selectedItem != null) {
            selectedStrategy = createStrategy(selectedItem.getClassName());
            if (selectedStrategy != null) {
                StrategyConfig config = selectedStrategy.getConfig();
                
                // 根據策略類型添加參數配置
                switch (selectedItem.getClassName()) {
                    case "SimpleMovingAverageStrategy":
                        addIntegerParameter("shortPeriod", "短期週期", config.getIntParameter("shortPeriod", 10), 1, 100);
                        addIntegerParameter("longPeriod", "長期週期", config.getIntParameter("longPeriod", 20), 1, 200);
                        addIntegerParameter("maxPosition", "最大倉位", config.getIntParameter("maxPosition", 1000), 1, 10000);
                        break;
                        
                    case "RSIStrategy":
                        addIntegerParameter("rsiPeriod", "RSI 週期", config.getIntParameter("rsiPeriod", 14), 2, 50);
                        addDoubleParameter("oversoldThreshold", "超賣閾值", config.getDoubleParameter("oversoldThreshold", 30.0), 10.0, 40.0);
                        addDoubleParameter("overboughtThreshold", "超買閾值", config.getDoubleParameter("overboughtThreshold", 70.0), 60.0, 90.0);
                        addIntegerParameter("maxPosition", "最大倉位", config.getIntParameter("maxPosition", 1000), 1, 10000);
                        break;
                        
                    case "MACDStrategy":
                        addIntegerParameter("fastPeriod", "快線週期", config.getIntParameter("fastPeriod", 12), 5, 30);
                        addIntegerParameter("slowPeriod", "慢線週期", config.getIntParameter("slowPeriod", 26), 15, 50);
                        addIntegerParameter("signalPeriod", "信號線週期", config.getIntParameter("signalPeriod", 9), 5, 20);
                        addIntegerParameter("maxPosition", "最大倉位", config.getIntParameter("maxPosition", 1000), 1, 10000);
                        break;
                        
                    case "BollingerBandsStrategy":
                        addIntegerParameter("period", "週期", config.getIntParameter("period", 20), 10, 50);
                        addDoubleParameter("multiplier", "標準差倍數", config.getDoubleParameter("multiplier", 2.0), 1.0, 3.0);
                        addStringParameter("strategy", "策略類型", config.getStringParameter("strategy", "reversion"), 
                                        new String[]{"reversion", "breakout"});
                        addIntegerParameter("maxPosition", "最大倉位", config.getIntParameter("maxPosition", 1000), 1, 10000);
                        break;
                }
                
                // 通用參數
                addStringParameter("symbol", "交易商品", config.getStringParameter("symbol", "STOCK"), null);
                addBooleanParameter("debug", "調試模式", config.getBooleanParameter("debug", false));
            }
        }
        
        strategyConfigPanel.revalidate();
        strategyConfigPanel.repaint();
    }
    
    /**
     * 添加整數參數
     */
    private void addIntegerParameter(String key, String label, int defaultValue, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultValue, min, max, 1));
        strategyConfigPanel.add(new JLabel(label + ":"), "");
        strategyConfigPanel.add(spinner, "wrap");
        parameterComponents.put(key, spinner);
    }
    
    /**
     * 添加雙精度參數
     */
    private void addDoubleParameter(String key, String label, double defaultValue, double min, double max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(defaultValue, min, max, 0.1));
        strategyConfigPanel.add(new JLabel(label + ":"), "");
        strategyConfigPanel.add(spinner, "wrap");
        parameterComponents.put(key, spinner);
    }
    
    /**
     * 添加字串參數
     */
    private void addStringParameter(String key, String label, String defaultValue, String[] options) {
        JComponent component;
        
        if (options != null) {
            JComboBox<String> comboBox = new JComboBox<>(options);
            comboBox.setSelectedItem(defaultValue);
            component = comboBox;
        } else {
            JTextField textField = new JTextField(defaultValue);
            component = textField;
        }
        
        strategyConfigPanel.add(new JLabel(label + ":"), "");
        strategyConfigPanel.add(component, "wrap");
        parameterComponents.put(key, component);
    }
    
    /**
     * 添加布林參數
     */
    private void addBooleanParameter(String key, String label, boolean defaultValue) {
        JCheckBox checkBox = new JCheckBox("", defaultValue);
        strategyConfigPanel.add(new JLabel(label + ":"), "");
        strategyConfigPanel.add(checkBox, "wrap");
        parameterComponents.put(key, checkBox);
    }
    
    /**
     * 應用配置
     */
    private void applyConfiguration() {
        if (selectedStrategy != null) {
            StrategyConfig config = selectedStrategy.getConfig();
            
            // 應用參數配置
            for (Map.Entry<String, JComponent> entry : parameterComponents.entrySet()) {
                String key = entry.getKey();
                JComponent component = entry.getValue();
                
                if (component instanceof JSpinner) {
                    Object value = ((JSpinner) component).getValue();
                    config.setParameter(key, value);
                } else if (component instanceof JTextField) {
                    String value = ((JTextField) component).getText();
                    config.setParameter(key, value);
                } else if (component instanceof JComboBox) {
                    Object value = ((JComboBox<?>) component).getSelectedItem();
                    config.setParameter(key, value);
                } else if (component instanceof JCheckBox) {
                    boolean value = ((JCheckBox) component).isSelected();
                    config.setParameter(key, value);
                }
            }
        }
    }
    
    /**
     * 創建策略實例
     */
    private Strategy createStrategy(String className) {
        try {
            switch (className) {
                case "SimpleMovingAverageStrategy":
                    return new SimpleMovingAverageStrategy();
                case "RSIStrategy":
                    return new RSIStrategy();
                case "MACDStrategy":
                    return new MACDStrategy();
                case "BollingerBandsStrategy":
                    return new BollingerBandsStrategy();
                default:
                    return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Getters
    public boolean isConfirmed() { return confirmed; }
    
    public double getInitialCapital() {
        return ((Number) initialCapitalSpinner.getValue()).doubleValue();
    }
    
    public double getCommission() {
        return ((Number) commissionSpinner.getValue()).doubleValue();
    }
    
    public double getSlippage() {
        return ((Number) slippageSpinner.getValue()).doubleValue();
    }
    
    public Strategy getSelectedStrategy() { return selectedStrategy; }
    
    /**
     * 策略項目類
     */
    private static class StrategyItem {
        private final String className;
        private final String displayName;
        
        public StrategyItem(String className, String displayName) {
            this.className = className;
            this.displayName = displayName;
        }
        
        public String getClassName() { return className; }
        public String getDisplayName() { return displayName; }
        
        @Override
        public String toString() { return displayName; }
    }
}
