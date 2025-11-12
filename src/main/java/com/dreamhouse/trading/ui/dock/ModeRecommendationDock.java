package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.decision.classifier.ClassificationResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 模式建議面板
 * 顯示自動交易模式分類建議與互動按鈕
 */
public class ModeRecommendationDock extends JPanel {

    // 主要建議組件
    private JLabel primaryModeLabel;
    private JLabel primaryConfidenceLabel;
    private JProgressBar primaryConfidenceBar;

    // 次要建議組件
    private JLabel secondaryModeLabel;
    private JLabel secondaryConfidenceLabel;
    private JProgressBar secondaryConfidenceBar;

    // 理由說明區域
    private JTextArea reasoningArea;

    // 互動按鈕
    private JButton adoptButton;
    private JButton manualSelectButton;

    // 最後更新時間
    private JLabel lastUpdateLabel;

    // 當前分類結果
    private ClassificationResult currentResult;

    // 事件監聽器
    private List<ModeAdoptionListener> adoptionListeners = new ArrayList<>();

    /**
     * 模式採納監聽器
     */
    public interface ModeAdoptionListener {
        void onModeAdopted(TradeMode mode);
        void onManualSelectionRequested();
    }

    /**
     * 建構子
     */
    public ModeRecommendationDock() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(40, 40, 40));

        // 創建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(40, 40, 40));

        // 添加各個區塊
        mainPanel.add(createPrimaryRecommendationPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createSecondaryRecommendationPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createReasoningPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createActionButtonsPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        // 添加更新時間標籤
        lastUpdateLabel = new JLabel("尚未更新");
        lastUpdateLabel.setForeground(Color.GRAY);
        lastUpdateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(lastUpdateLabel);

        // 使用滾動面板包裝
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // 初始化顯示
        updateRecommendation(null);
    }

    /**
     * 創建主要建議面板
     */
    private JPanel createPrimaryRecommendationPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "🎯 主要建議模式",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 模式名稱
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("建議模式：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        primaryModeLabel = new JLabel("--");
        primaryModeLabel.setForeground(Color.WHITE);
        primaryModeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 16));
        panel.add(primaryModeLabel, gbc);

        // 信心度
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel label2 = new JLabel("信心度：");
        label2.setForeground(Color.LIGHT_GRAY);
        panel.add(label2, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel confidencePanel = new JPanel(new BorderLayout(5, 0));
        confidencePanel.setBackground(new Color(50, 50, 50));
        primaryConfidenceLabel = new JLabel("--");
        primaryConfidenceLabel.setForeground(Color.WHITE);
        primaryConfidenceLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 14));
        primaryConfidenceBar = new JProgressBar(0, 100);
        primaryConfidenceBar.setStringPainted(false);
        primaryConfidenceBar.setPreferredSize(new Dimension(150, 25));
        confidencePanel.add(primaryConfidenceLabel, BorderLayout.WEST);
        confidencePanel.add(primaryConfidenceBar, BorderLayout.CENTER);
        panel.add(confidencePanel, gbc);

        return panel;
    }

    /**
     * 創建次要建議面板
     */
    private JPanel createSecondaryRecommendationPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "💡 備選建議模式",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 模式名稱
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("備選模式：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        secondaryModeLabel = new JLabel("--");
        secondaryModeLabel.setForeground(Color.WHITE);
        secondaryModeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        panel.add(secondaryModeLabel, gbc);

        // 信心度
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel label2 = new JLabel("信心度：");
        label2.setForeground(Color.LIGHT_GRAY);
        panel.add(label2, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel confidencePanel = new JPanel(new BorderLayout(5, 0));
        confidencePanel.setBackground(new Color(50, 50, 50));
        secondaryConfidenceLabel = new JLabel("--");
        secondaryConfidenceLabel.setForeground(Color.WHITE);
        secondaryConfidenceBar = new JProgressBar(0, 100);
        secondaryConfidenceBar.setStringPainted(false);
        secondaryConfidenceBar.setPreferredSize(new Dimension(150, 20));
        confidencePanel.add(secondaryConfidenceLabel, BorderLayout.WEST);
        confidencePanel.add(secondaryConfidenceBar, BorderLayout.CENTER);
        panel.add(confidencePanel, gbc);

        return panel;
    }

    /**
     * 創建理由說明面板
     */
    private JPanel createReasoningPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "📋 決策理由",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        reasoningArea = new JTextArea(6, 30);
        reasoningArea.setEditable(false);
        reasoningArea.setLineWrap(true);
        reasoningArea.setWrapStyleWord(true);
        reasoningArea.setBackground(new Color(40, 40, 40));
        reasoningArea.setForeground(Color.LIGHT_GRAY);
        reasoningArea.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 12));
        reasoningArea.setText("尚未進行分析");

        JScrollPane scrollPane = new JScrollPane(reasoningArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 創建操作按鈕面板
     */
    private JPanel createActionButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "⚡ 操作",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        );
        panel.setBorder(border);

        // 採納建議按鈕
        adoptButton = new JButton("✓ 採納建議");
        adoptButton.setPreferredSize(new Dimension(140, 35));
        adoptButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        adoptButton.setBackground(new Color(0, 150, 0));
        adoptButton.setForeground(Color.WHITE);
        adoptButton.setFocusPainted(false);
        adoptButton.setBorder(BorderFactory.createLineBorder(new Color(0, 180, 0), 2));
        adoptButton.setEnabled(false);
        adoptButton.addActionListener(e -> onAdoptRecommendation());

        // 手動選擇按鈕
        manualSelectButton = new JButton("⚙ 手動選擇");
        manualSelectButton.setPreferredSize(new Dimension(140, 35));
        manualSelectButton.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        manualSelectButton.setBackground(new Color(70, 130, 180));
        manualSelectButton.setForeground(Color.WHITE);
        manualSelectButton.setFocusPainted(false);
        manualSelectButton.setBorder(BorderFactory.createLineBorder(new Color(100, 150, 200), 2));
        manualSelectButton.addActionListener(e -> onManualSelection());

        panel.add(adoptButton);
        panel.add(manualSelectButton);

        return panel;
    }

    /**
     * 更新建議顯示
     */
    public void updateRecommendation(ClassificationResult result) {
        this.currentResult = result;

        SwingUtilities.invokeLater(() -> {
            if (result == null) {
                // 清空顯示
                primaryModeLabel.setText("尚未分析");
                primaryModeLabel.setForeground(Color.GRAY);
                primaryConfidenceLabel.setText("--");
                primaryConfidenceBar.setValue(0);

                secondaryModeLabel.setText("--");
                secondaryConfidenceLabel.setText("--");
                secondaryConfidenceBar.setValue(0);

                reasoningArea.setText("尚未進行分析");
                adoptButton.setEnabled(false);
            } else {
                // 主要建議
                TradeMode primaryMode = result.getPrimaryMode();
                double primaryConfidence = result.getConfidence();

                primaryModeLabel.setText(getModeDisplayName(primaryMode));
                primaryModeLabel.setForeground(getModeColor(primaryMode));

                int primaryPercent = (int) (primaryConfidence * 100);
                primaryConfidenceLabel.setText(primaryPercent + "%");
                primaryConfidenceBar.setValue(primaryPercent);
                primaryConfidenceBar.setForeground(getConfidenceColor(primaryConfidence));

                // 次要建議（信心度為主要信心度的 70%）
                TradeMode secondaryMode = result.getSecondaryMode();
                double secondaryConfidence = result.hasSecondaryMode() ? primaryConfidence * 0.7 : 0.0;

                secondaryModeLabel.setText(getModeDisplayName(secondaryMode));
                secondaryModeLabel.setForeground(getModeColor(secondaryMode));

                int secondaryPercent = (int) (secondaryConfidence * 100);
                secondaryConfidenceLabel.setText(secondaryPercent + "%");
                secondaryConfidenceBar.setValue(secondaryPercent);
                secondaryConfidenceBar.setForeground(getConfidenceColor(secondaryConfidence));

                // 理由說明
                String reasoning = formatReasoning(result);
                reasoningArea.setText(reasoning);

                // 只有當主要建議不是 NO_TRADE 時才啟用採納按鈕
                adoptButton.setEnabled(primaryMode != TradeMode.NO_TRADE);
            }
            updateLastUpdateTime();
        });
    }

    /**
     * 格式化決策理由
     */
    private String formatReasoning(ClassificationResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("【分析摘要】\n");
        sb.append(result.getSummary()).append("\n\n");

        sb.append("【詳細理由】\n");
        List<String> reasons = result.getReasons();
        for (int i = 0; i < reasons.size(); i++) {
            sb.append((i + 1)).append(". ").append(reasons.get(i)).append("\n");
        }

        return sb.toString();
    }

    /**
     * 採納建議事件處理
     */
    private void onAdoptRecommendation() {
        if (currentResult == null) return;

        TradeMode primaryMode = currentResult.getPrimaryMode();
        if (primaryMode == TradeMode.NO_TRADE) {
            JOptionPane.showMessageDialog(this,
                    "當前建議為「不建議交易」，無法採納。",
                    "無法採納",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 通知監聽器
        for (ModeAdoptionListener listener : adoptionListeners) {
            listener.onModeAdopted(primaryMode);
        }

        // 顯示確認訊息
        JOptionPane.showMessageDialog(this,
                "已採納建議：" + getModeDisplayName(primaryMode),
                "採納成功",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 手動選擇事件處理
     */
    private void onManualSelection() {
        // 通知監聽器
        for (ModeAdoptionListener listener : adoptionListeners) {
            listener.onManualSelectionRequested();
        }
    }

    /**
     * 更新最後更新時間
     */
    private void updateLastUpdateTime() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        lastUpdateLabel.setText("最後更新：" + now.format(formatter));
    }

    /**
     * 添加模式採納監聽器
     */
    public void addModeAdoptionListener(ModeAdoptionListener listener) {
        adoptionListeners.add(listener);
    }

    /**
     * 移除模式採納監聽器
     */
    public void removeModeAdoptionListener(ModeAdoptionListener listener) {
        adoptionListeners.remove(listener);
    }

    /**
     * 獲取當前分類結果
     */
    public ClassificationResult getCurrentResult() {
        return currentResult;
    }

    // ==================== 輔助方法 ====================

    private String getModeDisplayName(TradeMode mode) {
        if (mode == null) return "--";
        switch (mode) {
            case DAY_TRADE: return "🔸 當沖交易";
            case SHORT_SWING: return "🔹 短線交易";
            case SWING_TRADE: return "🔺 波段交易";
            case NO_TRADE: return "⛔ 不建議交易";
            default: return "未知";
        }
    }

    private Color getModeColor(TradeMode mode) {
        if (mode == null) return Color.GRAY;
        switch (mode) {
            case DAY_TRADE: return new Color(255, 165, 0);  // 橘色
            case SHORT_SWING: return new Color(0, 191, 255);  // 天藍色
            case SWING_TRADE: return new Color(144, 238, 144);  // 淺綠色
            case NO_TRADE: return new Color(220, 20, 60);  // 深紅色
            default: return Color.GRAY;
        }
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.7) {
            return new Color(0, 200, 0);  // 綠色
        } else if (confidence >= 0.5) {
            return new Color(255, 215, 0);  // 黃色
        } else if (confidence >= 0.3) {
            return new Color(255, 165, 0);  // 橘色
        } else {
            return new Color(255, 100, 0);  // 深橘色
        }
    }

    // ==================== 數據源整合方法 ====================

    /**
     * 從 DecisionEngine 更新模式建議
     * @param decisionEngine 決策引擎實例
     */
    public void updateFromDecisionEngine(com.dreamhouse.trading.core.decision.DecisionEngine decisionEngine) {
        if (decisionEngine == null) {
            updateRecommendation(null);
            return;
        }

        // 獲取市場環境和趨勢分析
        com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime =
            decisionEngine.getLastRegimeAnalysis();
        com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend =
            decisionEngine.getLastTrendAnalysis();

        // 構造 ClassificationResult
        ClassificationResult result = buildClassificationFromAnalysis(regime, trend);

        // 更新顯示
        updateRecommendation(result);
    }

    /**
     * 根據市場環境和趨勢分析構造分類結果
     */
    private ClassificationResult buildClassificationFromAnalysis(
            com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime,
            com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend) {

        ClassificationResult.Builder builder = new ClassificationResult.Builder();

        if (regime == null || trend == null) {
            return builder
                .primaryMode(TradeMode.NO_TRADE)
                .confidence(0.0)
                .addReason("市場環境分析數據不足")
                .build();
        }

        // 判斷主要模式
        TradeMode primaryMode = determinePrimaryMode(regime, trend);
        TradeMode secondaryMode = determineSecondaryMode(regime, trend, primaryMode);
        double confidence = calculateConfidence(regime, trend, primaryMode);

        builder.primaryMode(primaryMode)
               .secondaryMode(secondaryMode)
               .confidence(confidence);

        // 添加理由
        addReasons(builder, regime, trend, primaryMode);

        return builder.build();
    }

    /**
     * 判斷主要交易模式
     */
    private TradeMode determinePrimaryMode(
            com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime,
            com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend) {

        double volatility = regime.getVolatility();
        double trendStrength = regime.getTrendStrength();  // ADX 值
        com.dreamhouse.trading.core.decision.trend.TrendStrength strength = trend.getStrength();

        // 強趨勢環境 -> 波段交易
        if (trendStrength >= 25.0 && strength == com.dreamhouse.trading.core.decision.trend.TrendStrength.STRONG) {
            return TradeMode.SWING_TRADE;
        }

        // 高波動 + 明確趨勢 -> 短線交易
        if (volatility > 0.015 && trendStrength > 20.0) {
            return TradeMode.SHORT_SWING;
        }

        // 高波動 -> 當沖交易
        if (volatility > 0.01) {
            return TradeMode.DAY_TRADE;
        }

        // 盤整或低波動 -> 不建議交易
        if (volatility < 0.005 || trendStrength < 15.0) {
            return TradeMode.NO_TRADE;
        }

        // 預設：短線交易
        return TradeMode.SHORT_SWING;
    }

    /**
     * 判斷次要交易模式
     */
    private TradeMode determineSecondaryMode(
            com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime,
            com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend,
            TradeMode primaryMode) {

        double volatility = regime.getVolatility();
        double trendStrength = regime.getTrendStrength();

        // 根據主要模式決定次要模式
        switch (primaryMode) {
            case SWING_TRADE:
                // 波段交易的次要選擇：短線
                if (volatility > 0.01) {
                    return TradeMode.SHORT_SWING;
                }
                break;

            case SHORT_SWING:
                // 短線交易的次要選擇：當沖或波段
                if (volatility > 0.02) {
                    return TradeMode.DAY_TRADE;
                } else if (trendStrength > 25.0) {
                    return TradeMode.SWING_TRADE;
                }
                break;

            case DAY_TRADE:
                // 當沖的次要選擇：短線
                if (trendStrength > 20.0) {
                    return TradeMode.SHORT_SWING;
                }
                break;

            case NO_TRADE:
                // 不建議交易沒有次要選擇
                return null;
        }

        return null;
    }

    /**
     * 計算信心度
     */
    private double calculateConfidence(
            com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime,
            com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend,
            TradeMode primaryMode) {

        double baseConfidence = 0.5;
        double regimeConfidence = regime.getConfidence();
        double trendConfidence = trend.getConfidence();

        // 綜合環境和趨勢的信心度
        double envConfidence = (regimeConfidence + trendConfidence) / 2.0;

        // 根據模式特性調整
        switch (primaryMode) {
            case SWING_TRADE:
                // 波段交易需要高信心度
                if (regime.getTrendStrength() >= 30.0) {
                    baseConfidence += 0.3;
                }
                break;

            case SHORT_SWING:
                // 短線交易中等信心度
                if (regime.getVolatility() > 0.015) {
                    baseConfidence += 0.2;
                }
                break;

            case DAY_TRADE:
                // 當沖交易需要高波動
                if (regime.getVolatility() > 0.02) {
                    baseConfidence += 0.3;
                }
                break;

            case NO_TRADE:
                // 不交易時信心度較高（避免錯誤交易）
                baseConfidence += 0.2;
                break;
        }

        // 結合環境信心度
        double finalConfidence = (baseConfidence + envConfidence) / 2.0;

        return Math.min(1.0, Math.max(0.0, finalConfidence));
    }

    /**
     * 添加決策理由
     */
    private void addReasons(
            ClassificationResult.Builder builder,
            com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regime,
            com.dreamhouse.trading.core.decision.trend.TrendAnalysis trend,
            TradeMode primaryMode) {

        double volatility = regime.getVolatility();
        double trendStrength = regime.getTrendStrength();
        com.dreamhouse.trading.core.decision.regime.MarketRegime marketRegime = regime.getMarketRegime();
        com.dreamhouse.trading.core.decision.trend.TrendDirection direction = trend.getDirection();

        builder.addReason(String.format("市場環境：%s", getRegimeDisplayText(marketRegime)));
        builder.addReason(String.format("趨勢方向：%s", getTrendDirectionText(direction)));
        builder.addReason(String.format("趨勢強度（ADX）：%.1f", trendStrength));
        builder.addReason(String.format("波動率：%.2f%%", volatility * 100));

        // 根據模式添加具體建議
        switch (primaryMode) {
            case SWING_TRADE:
                builder.addReason("建議：跟隨主趨勢，設定較寬停損，持倉數日至數週");
                break;
            case SHORT_SWING:
                builder.addReason("建議：關注日內波動，1-3天持倉，及時獲利了結");
                break;
            case DAY_TRADE:
                builder.addReason("建議：快進快出，嚴格止損，當日平倉");
                break;
            case NO_TRADE:
                builder.addReason("建議：等待更好的交易機會，避免盤整行情");
                break;
        }
    }

    private String getRegimeDisplayText(com.dreamhouse.trading.core.decision.regime.MarketRegime regime) {
        switch (regime) {
            case BULL: return "多頭市場";
            case BEAR: return "空頭市場";
            case NEUTRAL: return "中性市場";
            default: return "未知";
        }
    }

    private String getTrendDirectionText(com.dreamhouse.trading.core.decision.trend.TrendDirection direction) {
        switch (direction) {
            case UP: return "上升";
            case DOWN: return "下降";
            case SIDEWAY: return "盤整";
            default: return "未知";
        }
    }
}
