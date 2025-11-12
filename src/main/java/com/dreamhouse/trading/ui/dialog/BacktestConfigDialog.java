package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.backtest.*;
import com.dreamhouse.trading.core.backtest.strategies.*;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.strategies.*;
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
        strategyComboBox.addItem(new StrategyItem("MultiTimeframeDecisionStrategy", "⭐ 多週期決策策略 (新)"));
        strategyComboBox.addItem(new StrategyItem("DayTradingStrategy", "🔸 當沖交易策略"));
        strategyComboBox.addItem(new StrategyItem("SwingTradingStrategy", "🔹 短線交易策略"));
        strategyComboBox.addItem(new StrategyItem("PositionTradingStrategy", "🔺 波段交易策略"));
        strategyComboBox.addItem(new StrategyItem("MultiStyleStrategyManager", "🎯 多風格策略組合"));
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
                    case "MultiTimeframeDecisionStrategy":
                        addStringParameter("configType", "配置類型", "default",
                                        new String[]{"default", "conservative", "aggressive"});
                        addDoubleParameter("maxDailyLoss", "每日最大虧損(%)", 5.0, 1.0, 10.0);
                        addDoubleParameter("maxPositionSize", "最大倉位(%)", 50.0, 10.0, 100.0);
                        addDoubleParameter("longEntryThreshold", "做多進場閾值", 0.3, 0.0, 1.0);
                        addDoubleParameter("exitThreshold", "出場閾值", 0.6, 0.0, 1.0);
                        addIntegerParameter("rsiPeriod", "RSI週期", 14, 5, 30);
                        addDoubleParameter("rsiOversold", "RSI超賣閾值", 40.0, 20.0, 50.0);
                        addDoubleParameter("rsiOverbought", "RSI超買閾值", 60.0, 50.0, 80.0);
                        addBooleanParameter("verboseLogging", "詳細日誌", true);
                        addBooleanParameter("disableFilters", "關閉過濾器(測試)", false);
                        break;

                    case "DayTradingStrategy":
                        // 時間週期配置
                        addStringParameter("mainTimeframe", "主週期", "M5",
                                        new String[]{"M1", "M5", "M15"});
                        addStringParameter("riskTimeframe", "風控週期", "M1",
                                        new String[]{"M1", "M5"});

                        // RSI 參數
                        addIntegerParameter("rsiPeriod", "RSI週期", 5, 3, 14);
                        addDoubleParameter("rsiOversold", "RSI超賣閾值", 40.0, 20.0, 50.0);
                        addDoubleParameter("rsiOverbought", "RSI超買閾值", 60.0, 50.0, 80.0);

                        // 風險管理參數
                        addDoubleParameter("maxDailyLoss", "每日最大虧損(%)", 1.0, 0.5, 5.0);
                        addDoubleParameter("maxPositionSize", "最大倉位(%)", 20.0, 10.0, 50.0);

                        // 進出場閾值
                        addDoubleParameter("entryThreshold", "進場閾值", 0.3, 0.1, 0.7);
                        addDoubleParameter("exitThreshold", "出場閾值", 0.3, 0.1, 0.7);

                        // 持倉時間限制
                        addIntegerParameter("maxHoldingBars", "最大持倉K線數", 78, 30, 200);
                        addBooleanParameter("forceCloseEOD", "收盤強制平倉", true);

                        // 過濾器開關
                        addBooleanParameter("enableRegimeFilter", "啟用週線環境過濾", false);
                        addBooleanParameter("enableTrendFilter", "啟用日線趨勢過濾", false);
                        break;

                    case "SwingTradingStrategy":
                        // 時間週期配置
                        addStringParameter("mainTimeframe", "主週期", "M15",
                                        new String[]{"M5", "M15", "M30", "H1"});
                        addStringParameter("riskTimeframe", "風控週期", "M5",
                                        new String[]{"M1", "M5", "M15"});

                        // RSI 參數
                        addIntegerParameter("rsiPeriod", "RSI週期", 14, 7, 21);
                        addDoubleParameter("rsiOversold", "RSI超賣閾值", 30.0, 20.0, 40.0);
                        addDoubleParameter("rsiOverbought", "RSI超買閾值", 70.0, 60.0, 80.0);

                        // 風險管理參數
                        addDoubleParameter("maxDailyLoss", "每日最大虧損(%)", 3.0, 1.0, 10.0);
                        addDoubleParameter("maxPositionSize", "最大倉位(%)", 30.0, 10.0, 70.0);

                        // 進出場閾值
                        addDoubleParameter("entryThreshold", "進場閾值", 0.5, 0.3, 0.8);
                        addDoubleParameter("exitThreshold", "出場閾值", 0.5, 0.3, 0.8);

                        // 持倉時間限制
                        addIntegerParameter("maxHoldingBars", "最大持倉K線數", 288, 100, 500);
                        addBooleanParameter("forceCloseEOD", "收盤強制平倉", false);

                        // 過濾器開關
                        addBooleanParameter("enableRegimeFilter", "啟用週線環境過濾", false);
                        addBooleanParameter("enableTrendFilter", "啟用日線趨勢過濾", true);
                        break;

                    case "PositionTradingStrategy":
                        // 時間週期配置
                        addStringParameter("mainTimeframe", "主週期", "H1",
                                        new String[]{"M30", "H1", "D1"});
                        addStringParameter("riskTimeframe", "風控週期", "M15",
                                        new String[]{"M5", "M15", "M30"});

                        // RSI 參數
                        addIntegerParameter("rsiPeriod", "RSI週期", 21, 14, 30);
                        addDoubleParameter("rsiOversold", "RSI超賣閾值", 25.0, 15.0, 35.0);
                        addDoubleParameter("rsiOverbought", "RSI超買閾值", 75.0, 65.0, 85.0);

                        // 風險管理參數
                        addDoubleParameter("maxDailyLoss", "每日最大虧損(%)", 5.0, 2.0, 10.0);
                        addDoubleParameter("maxPositionSize", "最大倉位(%)", 50.0, 20.0, 100.0);

                        // 進出場閾值
                        addDoubleParameter("entryThreshold", "進場閾值", 0.7, 0.5, 0.9);
                        addDoubleParameter("exitThreshold", "出場閾值", 0.4, 0.2, 0.7);

                        // 持倉時間限制
                        addIntegerParameter("maxHoldingBars", "最大持倉K線數", 672, 300, 1000);
                        addBooleanParameter("forceCloseEOD", "收盤強制平倉", false);

                        // 過濾器開關
                        addBooleanParameter("enableRegimeFilter", "啟用週線環境過濾", true);
                        addBooleanParameter("enableTrendFilter", "啟用日線趨勢過濾", true);
                        break;

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
            // 特殊處理多週期決策策略
            if (selectedStrategy instanceof MultiTimeframeDecisionStrategy) {
                applyMultiTimeframeConfig((MultiTimeframeDecisionStrategy) selectedStrategy);
            } else {
                // 一般策略使用 StrategyConfig
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
    }

    /**
     * 應用多週期決策策略配置
     */
    private void applyMultiTimeframeConfig(MultiTimeframeDecisionStrategy strategy) {
        DecisionConfig config = strategy.getDecisionConfig();

        // 獲取用戶設定的值
        String configType = getStringValue("configType", "default");
        double maxDailyLoss = getDoubleValue("maxDailyLoss", 5.0) / 100.0;
        double maxPositionSize = getDoubleValue("maxPositionSize", 50.0) / 100.0;
        double longEntryThreshold = getDoubleValue("longEntryThreshold", 0.3);
        double exitThreshold = getDoubleValue("exitThreshold", 0.6);
        int rsiPeriod = getIntValue("rsiPeriod", 14);
        double rsiOversold = getDoubleValue("rsiOversold", 40.0);
        double rsiOverbought = getDoubleValue("rsiOverbought", 60.0);
        boolean verboseLogging = getBooleanValue("verboseLogging", true);
        boolean disableFilters = getBooleanValue("disableFilters", false);

        // 應用風險配置
        config.getRiskConfig().setMaxDailyLossPercent(maxDailyLoss);
        config.getRiskConfig().setMaxPositionSizePercent(maxPositionSize);

        // 應用投票配置
        config.getVotingConfig().setLongEntryThreshold(longEntryThreshold);
        config.getVotingConfig().setShortEntryThreshold(longEntryThreshold);
        config.getVotingConfig().setExitThreshold(exitThreshold);

        // 應用日誌配置
        config.setVerboseLogging(verboseLogging);

        // 關閉過濾器（測試模式）
        if (disableFilters) {
            config.setRegimeDetectionEnabled(false);
            config.setTrendAnalysisEnabled(false);
            System.out.println("[配置] 已關閉週線/日線過濾器（測試模式）");
        }

        // 配置 RSI 策略
        if (!strategy.getStrategies().isEmpty()) {
            SignalRSIStrategy rsiStrategy = (SignalRSIStrategy) strategy.getStrategies().get(0);
            rsiStrategy.setRsiPeriod(rsiPeriod);
            rsiStrategy.setOversoldThreshold(rsiOversold);
            rsiStrategy.setOverboughtThreshold(rsiOverbought);
            System.out.println("[配置] RSI策略: 週期=" + rsiPeriod +
                    ", 超賣=" + rsiOversold + ", 超買=" + rsiOverbought);
        }

        System.out.println("[配置] 多週期決策策略配置完成:");
        System.out.println("  - 進場閾值: " + longEntryThreshold);
        System.out.println("  - 出場閾值: " + exitThreshold);
        System.out.println("  - 每日最大虧損: " + (maxDailyLoss * 100) + "%");
        System.out.println("  - 最大倉位: " + (maxPositionSize * 100) + "%");
    }

    // 輔助方法獲取參數值
    private String getStringValue(String key, String defaultValue) {
        JComponent component = parameterComponents.get(key);
        if (component instanceof JComboBox) {
            Object value = ((JComboBox<?>) component).getSelectedItem();
            return value != null ? value.toString() : defaultValue;
        } else if (component instanceof JTextField) {
            return ((JTextField) component).getText();
        }
        return defaultValue;
    }

    private double getDoubleValue(String key, double defaultValue) {
        JComponent component = parameterComponents.get(key);
        if (component instanceof JSpinner) {
            return ((Number) ((JSpinner) component).getValue()).doubleValue();
        }
        return defaultValue;
    }

    private int getIntValue(String key, int defaultValue) {
        JComponent component = parameterComponents.get(key);
        if (component instanceof JSpinner) {
            return ((Number) ((JSpinner) component).getValue()).intValue();
        }
        return defaultValue;
    }

    private boolean getBooleanValue(String key, boolean defaultValue) {
        JComponent component = parameterComponents.get(key);
        if (component instanceof JCheckBox) {
            return ((JCheckBox) component).isSelected();
        }
        return defaultValue;
    }
    
    /**
     * 創建策略實例
     */
    private Strategy createStrategy(String className) {
        try {
            switch (className) {
                case "MultiTimeframeDecisionStrategy":
                    return createMultiTimeframeStrategy();
                case "DayTradingStrategy":
                    return createDayTradingStrategy();
                case "SwingTradingStrategy":
                    return createSwingTradingStrategy();
                case "PositionTradingStrategy":
                    return createPositionTradingStrategy();
                case "MultiStyleStrategyManager":
                    return MultiStyleStrategyManager.createBalancedManager();
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

    /**
     * 創建多週期決策策略
     */
    private MultiTimeframeDecisionStrategy createMultiTimeframeStrategy() {
        // 使用預設配置
        DecisionConfig config = DecisionConfig.createDefault();

        // 設定主迴圈時間週期為日線（D1）
        // 注意：如果您的數據是其他週期（如 M5, M15, H1 等），請修改此處
        config.setMainLoopTimeframe(com.dreamhouse.trading.core.Timeframe.D1);

        MultiTimeframeDecisionStrategy strategy = new MultiTimeframeDecisionStrategy(config);

        // 添加 RSI 子策略
        SignalRSIStrategy rsiStrategy = new SignalRSIStrategy();
        rsiStrategy.setWeight(1.0);
        strategy.addStrategy(rsiStrategy);

        return strategy;
    }

    /**
     * 創建當沖交易策略（使用 UI 參數）
     */
    private DayTradingStrategy createDayTradingStrategy() {
        // 創建配置
        DecisionConfig config = DayTradingStrategy.createDayTradingConfig();

        // 從 UI 讀取參數並應用
        applyDecisionStrategyConfig(config, "DayTradingStrategy");

        // 創建策略
        DayTradingStrategy strategy = new DayTradingStrategy(config);

        // 應用 RSI 參數
        applyRSIParameters(strategy);

        return strategy;
    }

    /**
     * 創建短線交易策略（使用 UI 參數）
     */
    private SwingTradingStrategy createSwingTradingStrategy() {
        // 創建配置
        DecisionConfig config = SwingTradingStrategy.createSwingTradingConfig();

        // 從 UI 讀取參數並應用
        applyDecisionStrategyConfig(config, "SwingTradingStrategy");

        // 創建策略
        SwingTradingStrategy strategy = new SwingTradingStrategy(config);

        // 應用 RSI 參數
        applyRSIParameters(strategy);

        return strategy;
    }

    /**
     * 創建波段交易策略（使用 UI 參數）
     */
    private PositionTradingStrategy createPositionTradingStrategy() {
        // 創建配置
        DecisionConfig config = PositionTradingStrategy.createPositionTradingConfig();

        // 從 UI 讀取參數並應用
        applyDecisionStrategyConfig(config, "PositionTradingStrategy");

        // 創建策略
        PositionTradingStrategy strategy = new PositionTradingStrategy(config);

        // 應用 RSI 參數
        applyRSIParameters(strategy);

        return strategy;
    }

    /**
     * 應用決策策略配置（通用方法）
     */
    private void applyDecisionStrategyConfig(DecisionConfig config, String strategyName) {
        // 時間週期配置
        String mainTimeframeStr = getStringValue("mainTimeframe", "M5");
        String riskTimeframeStr = getStringValue("riskTimeframe", "M1");
        config.setMainLoopTimeframe(parseTimeframe(mainTimeframeStr));
        config.setRiskMonitorTimeframe(parseTimeframe(riskTimeframeStr));

        // 風險管理參數
        double maxDailyLoss = getDoubleValue("maxDailyLoss", 3.0) / 100.0;
        double maxPositionSize = getDoubleValue("maxPositionSize", 30.0) / 100.0;
        config.getRiskConfig().setMaxDailyLossPercent(maxDailyLoss);
        config.getRiskConfig().setMaxPositionSizePercent(maxPositionSize);

        // 進出場閾值
        double entryThreshold = getDoubleValue("entryThreshold", 0.5);
        double exitThreshold = getDoubleValue("exitThreshold", 0.5);
        config.getVotingConfig().setLongEntryThreshold(entryThreshold);
        config.getVotingConfig().setShortEntryThreshold(entryThreshold);
        config.getVotingConfig().setExitThreshold(exitThreshold);

        // 持倉時間限制
        int maxHoldingBars = getIntValue("maxHoldingBars", 288);
        boolean forceCloseEOD = getBooleanValue("forceCloseEOD", false);
        config.getRiskConfig().setMaxHoldingBars(maxHoldingBars);
        config.getRiskConfig().setForceCloseAtEndOfDay(forceCloseEOD);

        // 過濾器開關
        boolean enableRegimeFilter = getBooleanValue("enableRegimeFilter", false);
        boolean enableTrendFilter = getBooleanValue("enableTrendFilter", true);
        config.setRegimeDetectionEnabled(enableRegimeFilter);
        config.setTrendAnalysisEnabled(enableTrendFilter);

        System.out.println(String.format("[配置] %s 配置完成:", strategyName));
        System.out.println("  - 主週期: " + mainTimeframeStr);
        System.out.println("  - 風控週期: " + riskTimeframeStr);
        System.out.println("  - 進場閾值: " + entryThreshold);
        System.out.println("  - 出場閾值: " + exitThreshold);
        System.out.println("  - 每日最大虧損: " + (maxDailyLoss * 100) + "%");
        System.out.println("  - 最大倉位: " + (maxPositionSize * 100) + "%");
        System.out.println("  - 最大持倉K線數: " + maxHoldingBars);
        System.out.println("  - 週線環境過濾: " + (enableRegimeFilter ? "啟用" : "關閉"));
        System.out.println("  - 日線趨勢過濾: " + (enableTrendFilter ? "啟用" : "關閉"));
    }

    /**
     * 應用 RSI 參數到策略
     */
    private void applyRSIParameters(MultiTimeframeDecisionStrategy strategy) {
        int rsiPeriod = getIntValue("rsiPeriod", 14);
        double rsiOversold = getDoubleValue("rsiOversold", 30.0);
        double rsiOverbought = getDoubleValue("rsiOverbought", 70.0);

        // 配置第一個策略（RSI 策略）
        if (!strategy.getStrategies().isEmpty()) {
            DecisionBaseStrategy firstStrategy = strategy.getStrategies().get(0);
            if (firstStrategy instanceof SignalRSIStrategy) {
                SignalRSIStrategy rsiStrategy = (SignalRSIStrategy) firstStrategy;
                rsiStrategy.setRsiPeriod(rsiPeriod);
                rsiStrategy.setOversoldThreshold(rsiOversold);
                rsiStrategy.setOverboughtThreshold(rsiOverbought);
                System.out.println("[配置] RSI策略: 週期=" + rsiPeriod +
                        ", 超賣=" + rsiOversold + ", 超買=" + rsiOverbought);
            }
        }
    }

    /**
     * 解析時間週期字串
     */
    private com.dreamhouse.trading.core.Timeframe parseTimeframe(String timeframeStr) {
        switch (timeframeStr) {
            case "M1": return com.dreamhouse.trading.core.Timeframe.M1;
            case "M5": return com.dreamhouse.trading.core.Timeframe.M5;
            case "M15": return com.dreamhouse.trading.core.Timeframe.M15;
            case "M30": return com.dreamhouse.trading.core.Timeframe.M30;
            case "H1": return com.dreamhouse.trading.core.Timeframe.H1;
            case "D1": return com.dreamhouse.trading.core.Timeframe.D1;
            case "W1": return com.dreamhouse.trading.core.Timeframe.W1;
            default: return com.dreamhouse.trading.core.Timeframe.M5;
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
