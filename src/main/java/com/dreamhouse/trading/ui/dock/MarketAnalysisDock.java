package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.decision.regime.MarketRegime;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendDirection;
import com.dreamhouse.trading.core.decision.trend.TrendStrength;
import com.dreamhouse.trading.core.decision.intraday.IntradayAnalysis;
import com.dreamhouse.trading.core.decision.intraday.LiquidityLevel;
import com.dreamhouse.trading.core.scanner.MarketScanResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/**
 * 市場分析面板
 * 顯示多週期市場分析結果（週線、日線、分鐘線）
 */
public class MarketAnalysisDock extends JPanel {
    private static final DateTimeFormatter SCAN_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 週線分析組件
    private JLabel regimeLabel;
    private JLabel regimeConfidenceLabel;
    private JProgressBar regimeConfidenceBar;

    // 日線分析組件
    private JLabel trendDirectionLabel;
    private JLabel trendStrengthLabel;
    private JLabel trendConfidenceLabel;
    private JProgressBar trendConfidenceBar;

    // 分鐘線分析組件
    private JLabel liquidityLabel;
    private JLabel volatilityLabel;
    private JLabel activeTimeLabel;
    private JLabel dayTradeSuitableLabel;
    private JTextArea scanDetailArea;

    // 最後更新時間
    private JLabel lastUpdateLabel;

    /**
     * 建構子
     */
    public MarketAnalysisDock() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(40, 40, 40));

        // 創建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(40, 40, 40));

        // 添加三個分析區塊
        mainPanel.add(createWeeklyPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createDailyPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createIntradayPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createScanDetailPanel());
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
        updateRegimeAnalysis(null);
        updateTrendAnalysis(null);
        updateIntradayAnalysis(null);
    }

    /**
     * 創建週線分析面板
     */
    private JPanel createWeeklyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "📊 週線市場環境",
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

        // 市場趨勢
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("市場趨勢：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        regimeLabel = new JLabel("--");
        regimeLabel.setForeground(Color.WHITE);
        regimeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        panel.add(regimeLabel, gbc);

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
        regimeConfidenceLabel = new JLabel("--");
        regimeConfidenceLabel.setForeground(Color.WHITE);
        regimeConfidenceBar = new JProgressBar(0, 100);
        regimeConfidenceBar.setStringPainted(false);
        regimeConfidenceBar.setPreferredSize(new Dimension(150, 20));
        confidencePanel.add(regimeConfidenceLabel, BorderLayout.WEST);
        confidencePanel.add(regimeConfidenceBar, BorderLayout.CENTER);
        panel.add(confidencePanel, gbc);

        return panel;
    }

    /**
     * 創建日線分析面板
     */
    private JPanel createDailyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "📈 日線趨勢分析",
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

        // 趨勢方向
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("趨勢方向：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        trendDirectionLabel = new JLabel("--");
        trendDirectionLabel.setForeground(Color.WHITE);
        trendDirectionLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        panel.add(trendDirectionLabel, gbc);

        // 趨勢強度
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel label2 = new JLabel("趨勢強度：");
        label2.setForeground(Color.LIGHT_GRAY);
        panel.add(label2, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        trendStrengthLabel = new JLabel("--");
        trendStrengthLabel.setForeground(Color.WHITE);
        panel.add(trendStrengthLabel, gbc);

        // 信心度
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        JLabel label3 = new JLabel("信心度：");
        label3.setForeground(Color.LIGHT_GRAY);
        panel.add(label3, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel confidencePanel = new JPanel(new BorderLayout(5, 0));
        confidencePanel.setBackground(new Color(50, 50, 50));
        trendConfidenceLabel = new JLabel("--");
        trendConfidenceLabel.setForeground(Color.WHITE);
        trendConfidenceBar = new JProgressBar(0, 100);
        trendConfidenceBar.setStringPainted(false);
        trendConfidenceBar.setPreferredSize(new Dimension(150, 20));
        confidencePanel.add(trendConfidenceLabel, BorderLayout.WEST);
        confidencePanel.add(trendConfidenceBar, BorderLayout.CENTER);
        panel.add(confidencePanel, gbc);

        return panel;
    }

    /**
     * 創建分鐘線分析面板
     */
    private JPanel createIntradayPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "⚡ 盤中狀態分析",
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

        // 流動性等級
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        JLabel label1 = new JLabel("流動性：");
        label1.setForeground(Color.LIGHT_GRAY);
        panel.add(label1, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        liquidityLabel = new JLabel("--");
        liquidityLabel.setForeground(Color.WHITE);
        liquidityLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        panel.add(liquidityLabel, gbc);

        // 波動度
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        JLabel label2 = new JLabel("波動度：");
        label2.setForeground(Color.LIGHT_GRAY);
        panel.add(label2, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        volatilityLabel = new JLabel("--");
        volatilityLabel.setForeground(Color.WHITE);
        panel.add(volatilityLabel, gbc);

        // 活躍時段
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        JLabel label3 = new JLabel("活躍時段：");
        label3.setForeground(Color.LIGHT_GRAY);
        panel.add(label3, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        activeTimeLabel = new JLabel("--");
        activeTimeLabel.setForeground(Color.WHITE);
        panel.add(activeTimeLabel, gbc);

        // 當沖適合度
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.3;
        JLabel label4 = new JLabel("當沖適合度：");
        label4.setForeground(Color.LIGHT_GRAY);
        panel.add(label4, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        dayTradeSuitableLabel = new JLabel("--");
        dayTradeSuitableLabel.setForeground(Color.WHITE);
        dayTradeSuitableLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        panel.add(dayTradeSuitableLabel, gbc);

        return panel;
    }

    private JPanel createScanDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                "\u8a0a\u865f\u8a3a\u65b7",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE
        ));

        scanDetailArea = new JTextArea(5, 24);
        scanDetailArea.setEditable(false);
        scanDetailArea.setLineWrap(true);
        scanDetailArea.setWrapStyleWord(true);
        scanDetailArea.setForeground(Color.LIGHT_GRAY);
        scanDetailArea.setBackground(new Color(45, 45, 45));
        scanDetailArea.setText("\u539f\u59cb\u7b56\u7565\u8a0a\u865f: --\n\u6700\u5f8c\u88ab\u64cb\u539f\u56e0: --\n\u6700\u7d42\u6c7a\u7b56\u539f\u56e0: --");
        panel.add(new JScrollPane(scanDetailArea), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 更新週線分析顯示
     */
    public void updateRegimeAnalysis(RegimeAnalysis analysis) {
        SwingUtilities.invokeLater(() -> {
            if (analysis == null) {
                regimeLabel.setText("尚未分析");
                regimeLabel.setForeground(Color.GRAY);
                regimeConfidenceLabel.setText("--");
                regimeConfidenceBar.setValue(0);
            } else {
                MarketRegime regime = analysis.getMarketRegime();
                double confidence = analysis.getConfidence();

                // 設定市場趨勢
                String regimeText = getRegimeDisplayName(regime);
                regimeLabel.setText(regimeText);
                regimeLabel.setForeground(getRegimeColor(regime));

                // 設定信心度
                int confidencePercent = (int) (confidence * 100);
                regimeConfidenceLabel.setText(confidencePercent + "%");
                regimeConfidenceBar.setValue(confidencePercent);
                regimeConfidenceBar.setForeground(getConfidenceColor(confidence));
            }
            updateLastUpdateTime();
        });
    }

    /**
     * 更新日線分析顯示
     */
    public void updateTrendAnalysis(TrendAnalysis analysis) {
        SwingUtilities.invokeLater(() -> {
            if (analysis == null) {
                trendDirectionLabel.setText("尚未分析");
                trendDirectionLabel.setForeground(Color.GRAY);
                trendStrengthLabel.setText("--");
                trendConfidenceLabel.setText("--");
                trendConfidenceBar.setValue(0);
            } else {
                TrendDirection direction = analysis.getDirection();
                TrendStrength strength = analysis.getStrength();
                double confidence = analysis.getConfidence();

                // 設定趨勢方向
                String directionText = getTrendDirectionDisplayName(direction);
                trendDirectionLabel.setText(directionText);
                trendDirectionLabel.setForeground(getTrendDirectionColor(direction));

                // 設定趨勢強度
                trendStrengthLabel.setText(getTrendStrengthDisplayName(strength));

                // 設定信心度
                int confidencePercent = (int) (confidence * 100);
                trendConfidenceLabel.setText(confidencePercent + "%");
                trendConfidenceBar.setValue(confidencePercent);
                trendConfidenceBar.setForeground(getConfidenceColor(confidence));
            }
            updateLastUpdateTime();
        });
    }

    /**
     * 更新分鐘線分析顯示
     */
    public void updateIntradayAnalysis(IntradayAnalysis analysis) {
        Runnable updateTask = () -> {
            if (analysis == null) {
                liquidityLabel.setText("尚未分析");
                liquidityLabel.setForeground(Color.GRAY);
                volatilityLabel.setText("--");
                activeTimeLabel.setText("--");
                dayTradeSuitableLabel.setText("--");
            } else {
                LiquidityLevel liquidity = analysis.getLiquidityLevel();
                double atrPercent = analysis.getAtrPercent();
                boolean isActiveTime = analysis.isActiveTime();
                boolean isDayTradeSuitable = analysis.isSuitableForDayTrade();

                // 設定流動性
                liquidityLabel.setText(liquidity.getDisplayName());
                liquidityLabel.setForeground(getLiquidityColor(liquidity));

                // 設定波動度
                String volatilityText = String.format("%.2f%%", atrPercent);
                volatilityLabel.setText(volatilityText);

                // 設定活躍時段
                activeTimeLabel.setText(isActiveTime ? "✓ 是" : "✗ 否");
                activeTimeLabel.setForeground(isActiveTime ? Color.GREEN : Color.ORANGE);

                // 設定當沖適合度
                if (isDayTradeSuitable) {
                    dayTradeSuitableLabel.setText("✓ 適合");
                    dayTradeSuitableLabel.setForeground(Color.GREEN);
                } else {
                    dayTradeSuitableLabel.setText("✗ 不適合");
                    dayTradeSuitableLabel.setForeground(Color.RED);
                }
            }
            updateLastUpdateTime();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            updateTask.run();
        } else {
            SwingUtilities.invokeLater(updateTask);
        }
    }

    public void updateScanResult(MarketScanResult result) {
        if (result == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            liquidityLabel.setText(String.format("分數 %.0f%%", result.getScore() * 100));
            liquidityLabel.setForeground(result.getScore() >= 0.6 ? Color.GREEN : Color.ORANGE);

            volatilityLabel.setText(String.format("信心 %.0f%%", result.getConfidence() * 100));

            String time = result.getScannedAt() != null ? result.getScannedAt().format(SCAN_TIME_FMT) : "--";
            activeTimeLabel.setText(time);
            activeTimeLabel.setForeground(Color.LIGHT_GRAY);

            String mode = result.getTradeMode() != null ? result.getTradeMode().getDisplayName() : "--";
            String action = result.getDecisionResult() != null
                ? result.getDecisionResult().getAction().getDisplayName()
                : "--";
            dayTradeSuitableLabel.setText(mode + " / " + action);
            dayTradeSuitableLabel.setForeground(result.hasTradeSignal() ? Color.GREEN : Color.GRAY);
            scanDetailArea.setText(String.format(
                "\u539f\u59cb\u7b56\u7565\u8a0a\u865f: %s%n\u6700\u5f8c\u88ab\u64cb\u539f\u56e0: %s%n\u6700\u7d42\u6c7a\u7b56\u539f\u56e0: %s",
                result.getRawSignalSummary(),
                result.getBlockReason(),
                result.getReason()));

            updateLastUpdateTime();
        });
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

    // ==================== 輔助方法 ====================

    private String getRegimeDisplayName(MarketRegime regime) {
        switch (regime) {
            case BULL: return "🟢 多頭市場";
            case BEAR: return "🔴 空頭市場";
            case NEUTRAL: return "🟡 中性市場";
            default: return "未知";
        }
    }

    private Color getRegimeColor(MarketRegime regime) {
        switch (regime) {
            case BULL: return new Color(0, 255, 0);
            case BEAR: return new Color(255, 0, 0);
            case NEUTRAL: return new Color(255, 215, 0);
            default: return Color.GRAY;
        }
    }

    private String getTrendDirectionDisplayName(TrendDirection direction) {
        switch (direction) {
            case UP: return "⬆ 上升";
            case DOWN: return "⬇ 下降";
            case SIDEWAY: return "➡ 盤整";
            default: return "未知";
        }
    }

    private Color getTrendDirectionColor(TrendDirection direction) {
        switch (direction) {
            case UP: return new Color(0, 255, 0);
            case DOWN: return new Color(255, 0, 0);
            case SIDEWAY: return new Color(255, 215, 0);
            default: return Color.GRAY;
        }
    }

    private String getTrendStrengthDisplayName(TrendStrength strength) {
        switch (strength) {
            case STRONG: return "強勢";
            case MEDIUM: return "中等";
            case WEAK: return "弱勢";
            default: return "未知";
        }
    }

    private Color getLiquidityColor(LiquidityLevel liquidity) {
        switch (liquidity) {
            case VERY_HIGH: return new Color(0, 255, 0);
            case HIGH: return new Color(144, 238, 144);
            case MEDIUM: return new Color(255, 215, 0);
            case LOW: return new Color(255, 165, 0);
            case VERY_LOW: return new Color(255, 0, 0);
            default: return Color.GRAY;
        }
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.7) {
            return new Color(0, 200, 0);  // 綠色
        } else if (confidence >= 0.5) {
            return new Color(255, 215, 0);  // 黃色
        } else {
            return new Color(255, 100, 0);  // 橘色
        }
    }

    // ==================== 數據源整合方法 ====================

    /**
     * 從 DecisionEngine 更新所有市場分析數據
     * @param decisionEngine 決策引擎實例
     */
    public void updateFromDecisionEngine(com.dreamhouse.trading.core.decision.DecisionEngine decisionEngine) {
        if (decisionEngine == null) {
            return;
        }

        // 更新週線環境分析
        com.dreamhouse.trading.core.decision.regime.RegimeAnalysis regimeAnalysis =
            decisionEngine.getLastRegimeAnalysis();
        updateRegimeAnalysis(regimeAnalysis);

        // 更新日線趨勢分析
        com.dreamhouse.trading.core.decision.trend.TrendAnalysis trendAnalysis =
            decisionEngine.getLastTrendAnalysis();
        updateTrendAnalysis(trendAnalysis);

        // 更新分鐘線盤中分析（如果有）
        // IntradayAnalysis 目前從 DecisionEngine 沒有直接獲取方法，暫時跳過
        // 可以在未來添加 getLastIntradayAnalysis() 方法
    }
}
