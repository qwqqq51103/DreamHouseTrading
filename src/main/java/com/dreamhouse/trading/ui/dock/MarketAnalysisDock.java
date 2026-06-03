package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.decision.intraday.IntradayAnalysis;
import com.dreamhouse.trading.core.decision.intraday.LiquidityLevel;
import com.dreamhouse.trading.core.decision.regime.MarketRegime;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendDirection;
import com.dreamhouse.trading.core.decision.trend.TrendStrength;
import com.dreamhouse.trading.core.scanner.MarketScanResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MarketAnalysisDock extends JPanel {
    private static final DateTimeFormatter SCAN_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter UPDATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JLabel regimeLabel;
    private JLabel regimeConfidenceLabel;
    private JProgressBar regimeConfidenceBar;

    private JLabel trendDirectionLabel;
    private JLabel trendStrengthLabel;
    private JLabel trendConfidenceLabel;
    private JProgressBar trendConfidenceBar;

    private JLabel liquidityLabel;
    private JLabel volatilityLabel;
    private JLabel activeTimeLabel;
    private JLabel actionLabel;

    private JTextArea scanDetailArea;
    private JLabel lastUpdateLabel;

    public MarketAnalysisDock() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(40, 40, 40));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(40, 40, 40));
        mainPanel.add(createWeeklyPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createDailyPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createIntradayPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createScanDetailPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        lastUpdateLabel = new JLabel("尚未更新");
        lastUpdateLabel.setForeground(Color.GRAY);
        lastUpdateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(lastUpdateLabel);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        updateRegimeAnalysis(null);
        updateTrendAnalysis(null);
        updateIntradayAnalysis(null);
    }

    private JPanel createWeeklyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("週級市場狀態"));

        GridBagConstraints gbc = createConstraints();
        addLabelRow(panel, gbc, 0, "市場狀態", valueLabel -> {
            regimeLabel = valueLabel;
            regimeLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        });
        addConfidenceRow(panel, gbc, 1, valueLabel -> regimeConfidenceLabel = valueLabel, progressBar -> regimeConfidenceBar = progressBar);
        return panel;
    }

    private JPanel createDailyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("日級趨勢"));

        GridBagConstraints gbc = createConstraints();
        addLabelRow(panel, gbc, 0, "方向", valueLabel -> {
            trendDirectionLabel = valueLabel;
            trendDirectionLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        });
        addLabelRow(panel, gbc, 1, "強度", valueLabel -> trendStrengthLabel = valueLabel);
        addConfidenceRow(panel, gbc, 2, valueLabel -> trendConfidenceLabel = valueLabel, progressBar -> trendConfidenceBar = progressBar);
        return panel;
    }

    private JPanel createIntradayPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("盤中掃描摘要"));

        GridBagConstraints gbc = createConstraints();
        addLabelRow(panel, gbc, 0, "流動性 / 分數", valueLabel -> {
            liquidityLabel = valueLabel;
            liquidityLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        });
        addLabelRow(panel, gbc, 1, "波動 / 信心", valueLabel -> volatilityLabel = valueLabel);
        addLabelRow(panel, gbc, 2, "最近掃描", valueLabel -> activeTimeLabel = valueLabel);
        addLabelRow(panel, gbc, 3, "模式 / 動作", valueLabel -> {
            actionLabel = valueLabel;
            actionLabel.setFont(new Font("Microsoft JhengHei", Font.BOLD, 13));
        });
        return panel;
    }

    private JPanel createScanDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(createBorder("雷達細節"));

        scanDetailArea = new JTextArea(5, 24);
        scanDetailArea.setEditable(false);
        scanDetailArea.setLineWrap(true);
        scanDetailArea.setWrapStyleWord(true);
        scanDetailArea.setForeground(Color.LIGHT_GRAY);
        scanDetailArea.setBackground(new Color(45, 45, 45));
        scanDetailArea.setText("訊號摘要：--\n阻擋原因：--\n決策理由：--");
        panel.add(new JScrollPane(scanDetailArea), BorderLayout.CENTER);
        return panel;
    }

    public void updateRegimeAnalysis(RegimeAnalysis analysis) {
        Runnable updateTask = () -> {
            if (analysis == null) {
                regimeLabel.setText("尚未分析");
                regimeLabel.setForeground(Color.GRAY);
                regimeConfidenceLabel.setText("--");
                regimeConfidenceBar.setValue(0);
            } else {
                MarketRegime regime = analysis.getMarketRegime();
                double confidence = analysis.getConfidence();
                regimeLabel.setText(getRegimeDisplayName(regime));
                regimeLabel.setForeground(getRegimeColor(regime));
                int confidencePercent = (int) (confidence * 100);
                regimeConfidenceLabel.setText(confidencePercent + "%");
                regimeConfidenceBar.setValue(confidencePercent);
                regimeConfidenceBar.setForeground(getConfidenceColor(confidence));
            }
            updateLastUpdateTime();
        };
        runOnEdt(updateTask);
    }

    public void updateTrendAnalysis(TrendAnalysis analysis) {
        Runnable updateTask = () -> {
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
                trendDirectionLabel.setText(getTrendDirectionDisplayName(direction));
                trendDirectionLabel.setForeground(getTrendDirectionColor(direction));
                trendStrengthLabel.setText(getTrendStrengthDisplayName(strength));
                int confidencePercent = (int) (confidence * 100);
                trendConfidenceLabel.setText(confidencePercent + "%");
                trendConfidenceBar.setValue(confidencePercent);
                trendConfidenceBar.setForeground(getConfidenceColor(confidence));
            }
            updateLastUpdateTime();
        };
        runOnEdt(updateTask);
    }

    public void updateIntradayAnalysis(IntradayAnalysis analysis) {
        Runnable updateTask = () -> {
            if (analysis == null) {
                liquidityLabel.setText("尚未分析");
                liquidityLabel.setForeground(Color.GRAY);
                volatilityLabel.setText("--");
                activeTimeLabel.setText("--");
                actionLabel.setText("--");
            } else {
                LiquidityLevel liquidity = analysis.getLiquidityLevel();
                double atrPercent = analysis.getAtrPercent();
                boolean isActiveTime = analysis.isActiveTime();
                boolean isDayTradeSuitable = analysis.isSuitableForDayTrade();

                liquidityLabel.setText(liquidity.getDisplayName());
                liquidityLabel.setForeground(getLiquidityColor(liquidity));
                volatilityLabel.setText(String.format("波動 %.2f%%", atrPercent));
                activeTimeLabel.setText(isActiveTime ? "交易時段中" : "非交易時段");
                activeTimeLabel.setForeground(isActiveTime ? Color.GREEN : Color.ORANGE);
                actionLabel.setText(isDayTradeSuitable ? "可執行當沖" : "暫不建議當沖");
                actionLabel.setForeground(isDayTradeSuitable ? Color.GREEN : Color.RED);
            }
            updateLastUpdateTime();
        };
        runOnEdt(updateTask);
    }

    public void updateScanResult(MarketScanResult result) {
        if (result == null) {
            return;
        }

        Runnable updateTask = () -> {
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
            actionLabel.setText(mode + " / " + action);
            actionLabel.setForeground(result.hasTradeSignal() ? Color.GREEN : Color.GRAY);

            scanDetailArea.setText(String.format(
                    "訊號摘要：%s%n阻擋原因：%s%n決策理由：%s",
                    emptyAsDash(result.getRawSignalSummary()),
                    emptyAsDash(result.getBlockReason()),
                    emptyAsDash(result.getReason())));
            updateLastUpdateTime();
        };
        runOnEdt(updateTask);
    }

    public void updateFromDecisionEngine(com.dreamhouse.trading.core.decision.DecisionEngine decisionEngine) {
        if (decisionEngine == null) {
            return;
        }
        updateRegimeAnalysis(decisionEngine.getLastRegimeAnalysis());
        updateTrendAnalysis(decisionEngine.getLastTrendAnalysis());
    }

    private void updateLastUpdateTime() {
        lastUpdateLabel.setText("最後更新：" + LocalDateTime.now().format(UPDATE_TIME_FMT));
    }

    private void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private TitledBorder createBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft JhengHei", Font.BOLD, 14),
                Color.WHITE);
    }

    private GridBagConstraints createConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }

    private interface LabelConsumer {
        void accept(JLabel label);
    }

    private interface ProgressConsumer {
        void accept(JProgressBar progressBar);
    }

    private void addLabelRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, LabelConsumer consumer) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel label = new JLabel(labelText + "：");
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JLabel valueLabel = new JLabel("--");
        valueLabel.setForeground(Color.WHITE);
        consumer.accept(valueLabel);
        panel.add(valueLabel, gbc);
    }

    private void addConfidenceRow(
            JPanel panel,
            GridBagConstraints gbc,
            int row,
            LabelConsumer labelConsumer,
            ProgressConsumer progressConsumer) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.3;
        JLabel label = new JLabel("信心：");
        label.setForeground(Color.LIGHT_GRAY);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        JPanel confidencePanel = new JPanel(new BorderLayout(5, 0));
        confidencePanel.setBackground(new Color(50, 50, 50));
        JLabel valueLabel = new JLabel("--");
        valueLabel.setForeground(Color.WHITE);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(150, 20));
        labelConsumer.accept(valueLabel);
        progressConsumer.accept(progressBar);
        confidencePanel.add(valueLabel, BorderLayout.WEST);
        confidencePanel.add(progressBar, BorderLayout.CENTER);
        panel.add(confidencePanel, gbc);
    }

    private String getRegimeDisplayName(MarketRegime regime) {
        return switch (regime) {
            case BULL -> "多頭";
            case BEAR -> "空頭";
            case NEUTRAL -> "盤整";
            default -> "未知";
        };
    }

    private Color getRegimeColor(MarketRegime regime) {
        return switch (regime) {
            case BULL -> new Color(0, 255, 0);
            case BEAR -> new Color(255, 0, 0);
            case NEUTRAL -> new Color(255, 215, 0);
            default -> Color.GRAY;
        };
    }

    private String getTrendDirectionDisplayName(TrendDirection direction) {
        return switch (direction) {
            case UP -> "上升";
            case DOWN -> "下降";
            case SIDEWAY -> "橫盤";
            default -> "未知";
        };
    }

    private Color getTrendDirectionColor(TrendDirection direction) {
        return switch (direction) {
            case UP -> new Color(0, 255, 0);
            case DOWN -> new Color(255, 0, 0);
            case SIDEWAY -> new Color(255, 215, 0);
            default -> Color.GRAY;
        };
    }

    private String getTrendStrengthDisplayName(TrendStrength strength) {
        return switch (strength) {
            case STRONG -> "強";
            case MEDIUM -> "中";
            case WEAK -> "弱";
            default -> "未知";
        };
    }

    private Color getLiquidityColor(LiquidityLevel liquidity) {
        return switch (liquidity) {
            case VERY_HIGH -> new Color(0, 255, 0);
            case HIGH -> new Color(144, 238, 144);
            case MEDIUM -> new Color(255, 215, 0);
            case LOW -> new Color(255, 165, 0);
            case VERY_LOW -> new Color(255, 0, 0);
            default -> Color.GRAY;
        };
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.7) {
            return new Color(0, 200, 0);
        }
        if (confidence >= 0.5) {
            return new Color(255, 215, 0);
        }
        return new Color(255, 100, 0);
    }

    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }
}
