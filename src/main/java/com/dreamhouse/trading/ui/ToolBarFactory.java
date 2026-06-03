package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.function.Consumer;

public class ToolBarFactory {

    public static class ToolBarCallbacks {
        public Consumer<String> onSymbolChange;
        public Consumer<Timeframe> onTimeframeChange;
        public Consumer<String> onIndicatorChange;
        public Consumer<LocalDate> onDateChange;
        public Runnable onLoadHistoricalData;
    }

    public static JToolBar createToolBar(ToolBarCallbacks callbacks) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(new JLabel(" " + I18n.get("toolbar.symbol") + " "));
        JTextField symbolField = new JTextField("", 8);
        symbolField.addActionListener(e -> {
            if (callbacks.onSymbolChange != null) {
                callbacks.onSymbolChange.accept(symbolField.getText());
            }
        });
        toolBar.add(symbolField);

        JButton addToWatchlist = new JButton(I18n.get("toolbar.addwatchlist"));
        addToWatchlist.setToolTipText(I18n.get("toolbar.addwatchlist"));
        toolBar.add(addToWatchlist);

        toolBar.addSeparator();

        toolBar.add(new JLabel(" " + I18n.get("toolbar.timeframe") + " "));
        JComboBox<Timeframe> timeframeCombo = new JComboBox<>(Timeframe.values());
        timeframeCombo.setMaximumSize(new Dimension(80, 25));
        timeframeCombo.addActionListener(e -> {
            if (callbacks.onTimeframeChange != null) {
                callbacks.onTimeframeChange.accept((Timeframe) timeframeCombo.getSelectedItem());
            }
        });
        toolBar.add(timeframeCombo);

        toolBar.addSeparator();

        toolBar.add(new JLabel(" " + I18n.get("toolbar.date") + " "));

        JButton todayBtn = new JButton(I18n.get("toolbar.today"));
        todayBtn.setToolTipText(I18n.get("toolbar.today.tooltip"));
        todayBtn.addActionListener(e -> {
            if (callbacks.onDateChange != null) {
                callbacks.onDateChange.accept(LocalDate.now());
            }
        });
        toolBar.add(todayBtn);

        JButton yesterdayBtn = new JButton(I18n.get("toolbar.yesterday"));
        yesterdayBtn.setToolTipText(I18n.get("toolbar.yesterday.tooltip"));
        yesterdayBtn.addActionListener(e -> {
            if (callbacks.onDateChange != null) {
                callbacks.onDateChange.accept(LocalDate.now().minusDays(1));
            }
        });
        toolBar.add(yesterdayBtn);

        JButton lastWeekBtn = new JButton(I18n.get("toolbar.lastweek"));
        lastWeekBtn.setToolTipText(I18n.get("toolbar.lastweek.tooltip"));
        lastWeekBtn.addActionListener(e -> {
            if (callbacks.onDateChange != null) {
                callbacks.onDateChange.accept(LocalDate.now().minusWeeks(1));
            }
        });
        toolBar.add(lastWeekBtn);

        JButton customDateBtn = new JButton(I18n.get("toolbar.customdate") + "...");
        customDateBtn.setToolTipText(I18n.get("toolbar.customdate.tooltip"));
        customDateBtn.addActionListener(e -> showDatePickerDialog(toolBar, callbacks));
        toolBar.add(customDateBtn);

        JButton loadHistoryBtn = new JButton(I18n.get("toolbar.loadhistory"));
        loadHistoryBtn.setToolTipText(I18n.get("toolbar.loadhistory.tooltip"));
        loadHistoryBtn.addActionListener(e -> {
            if (callbacks.onLoadHistoricalData != null) {
                callbacks.onLoadHistoricalData.run();
            }
        });
        toolBar.add(loadHistoryBtn);

        toolBar.addSeparator();

        toolBar.add(new JLabel(" " + I18n.get("toolbar.indicator") + " "));
        String[] indicators = {
                I18n.get("indicator.none"),
                I18n.get("indicator.sma"),
                I18n.get("indicator.ema"),
                I18n.get("indicator.rsi"),
                I18n.get("indicator.macd"),
                I18n.get("indicator.boll"),
                I18n.get("indicator.kd"),
                I18n.get("indicator.adx"),
                I18n.get("indicator.obv"),
                I18n.get("indicator.cci"),
                I18n.get("indicator.wr")
        };
        JComboBox<String> indicatorCombo = new JComboBox<>(indicators);
        indicatorCombo.setMaximumSize(new Dimension(120, 25));
        indicatorCombo.addActionListener(e -> {
            if (callbacks.onIndicatorChange != null) {
                callbacks.onIndicatorChange.accept((String) indicatorCombo.getSelectedItem());
            }
        });
        toolBar.add(indicatorCombo);

        return toolBar;
    }

    private static void showDatePickerDialog(Component parent, ToolBarCallbacks callbacks) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                I18n.get("dialog.selectdate.title"), true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(new JLabel(I18n.get("dialog.selectdate.year") + ":"));
        SpinnerNumberModel yearModel = new SpinnerNumberModel(
                LocalDate.now().getYear(), 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        panel.add(yearSpinner);

        panel.add(new JLabel(I18n.get("dialog.selectdate.month") + ":"));
        SpinnerNumberModel monthModel = new SpinnerNumberModel(
                LocalDate.now().getMonthValue(), 1, 12, 1);
        JSpinner monthSpinner = new JSpinner(monthModel);
        panel.add(monthSpinner);

        panel.add(new JLabel(I18n.get("dialog.selectdate.day") + ":"));
        SpinnerNumberModel dayModel = new SpinnerNumberModel(
                LocalDate.now().getDayOfMonth(), 1, 31, 1);
        JSpinner daySpinner = new JSpinner(dayModel);
        panel.add(daySpinner);

        dialog.add(panel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton(I18n.get("dialog.ok"));
        JButton cancelButton = new JButton(I18n.get("dialog.cancel"));

        okButton.addActionListener(e -> {
            try {
                int year = (Integer) yearSpinner.getValue();
                int month = (Integer) monthSpinner.getValue();
                int day = (Integer) daySpinner.getValue();

                LocalDate selectedDate = LocalDate.of(year, month, day);

                if (callbacks.onDateChange != null) {
                    callbacks.onDateChange.accept(selectedDate);
                }

                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        I18n.get("dialog.selectdate.invalid"),
                        I18n.get("dialog.error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
