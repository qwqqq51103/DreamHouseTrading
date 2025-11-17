package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.function.Consumer;

public class ToolBarFactory {
    
    public static class ToolBarCallbacks {
        public Consumer<String> onSymbolChange;
        public Consumer<Timeframe> onTimeframeChange;
        public Consumer<String> onIndicatorChange;
        public Consumer<LocalDate> onDateChange;  // 新增：日期變更回調
        public Runnable onZoomIn;
        public Runnable onZoomOut;
        public Runnable onZoomReset;
        public Runnable onCrosshairToggle;
        public Runnable onTrendlineToggle;
        public Runnable onHorizontalLineToggle;
        public Runnable onMeasureToolToggle;
        public Runnable onFibonacciToggle;
    }
    
    public static JToolBar createToolBar(ToolBarCallbacks callbacks) {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        // Symbol 搜尋框
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
        
        // Timeframe 選擇
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

        // 日期選擇
        toolBar.add(new JLabel(" " + I18n.get("toolbar.date") + " "));

        // 日期快捷按鈕
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

        // 自訂日期按鈕
        JButton customDateBtn = new JButton(I18n.get("toolbar.customdate") + "...");
        customDateBtn.setToolTipText(I18n.get("toolbar.customdate.tooltip"));
        customDateBtn.addActionListener(e -> {
            // 彈出日期選擇對話框
            showDatePickerDialog(toolBar, callbacks);
        });
        toolBar.add(customDateBtn);

        toolBar.addSeparator();
        
        // 指標選擇
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
        
        toolBar.addSeparator();
        
        // 繪圖工具
        JToggleButton crosshairBtn = new JToggleButton("✛ " + I18n.get("toolbar.crosshair"));
        crosshairBtn.setSelected(true);
        crosshairBtn.addActionListener(e -> {
            if (callbacks.onCrosshairToggle != null) {
                callbacks.onCrosshairToggle.run();
            }
        });
        toolBar.add(crosshairBtn);
        
        JToggleButton trendlineBtn = new JToggleButton("📈 " + I18n.get("toolbar.trendline"));
        trendlineBtn.addActionListener(e -> {
            if (callbacks.onTrendlineToggle != null) {
                callbacks.onTrendlineToggle.run();
            }
        });
        toolBar.add(trendlineBtn);
        
        JToggleButton hlineBtn = new JToggleButton("─ " + I18n.get("toolbar.hline"));
        hlineBtn.addActionListener(e -> {
            if (callbacks.onHorizontalLineToggle != null) {
                callbacks.onHorizontalLineToggle.run();
            }
        });
        toolBar.add(hlineBtn);

        JToggleButton measureBtn = new JToggleButton("📏 " + I18n.get("toolbar.measure"));
        measureBtn.addActionListener(e -> {
            if (callbacks.onMeasureToolToggle != null) {
                callbacks.onMeasureToolToggle.run();
            }
        });
        toolBar.add(measureBtn);

        JToggleButton fibonacciBtn = new JToggleButton("📊 " + I18n.get("toolbar.fibonacci"));
        fibonacciBtn.addActionListener(e -> {
            if (callbacks.onFibonacciToggle != null) {
                callbacks.onFibonacciToggle.run();
            }
        });
        toolBar.add(fibonacciBtn);

        toolBar.addSeparator();
        
        // Zoom 控制
        JButton zoomInBtn = new JButton("🔍+ " + I18n.get("toolbar.zoomin"));
        zoomInBtn.addActionListener(e -> {
            if (callbacks.onZoomIn != null) {
                callbacks.onZoomIn.run();
            }
        });
        toolBar.add(zoomInBtn);
        
        JButton zoomOutBtn = new JButton("🔍- " + I18n.get("toolbar.zoomout"));
        zoomOutBtn.addActionListener(e -> {
            if (callbacks.onZoomOut != null) {
                callbacks.onZoomOut.run();
            }
        });
        toolBar.add(zoomOutBtn);
        
        JButton zoomResetBtn = new JButton(I18n.get("toolbar.reset"));
        zoomResetBtn.addActionListener(e -> {
            if (callbacks.onZoomReset != null) {
                callbacks.onZoomReset.run();
            }
        });
        toolBar.add(zoomResetBtn);
        
        return toolBar;
    }

    /**
     * 顯示自訂日期選擇對話框
     */
    private static void showDatePickerDialog(Component parent, ToolBarCallbacks callbacks) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                I18n.get("dialog.selectdate.title"), true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 年份選擇
        panel.add(new JLabel(I18n.get("dialog.selectdate.year") + ":"));
        SpinnerNumberModel yearModel = new SpinnerNumberModel(
                LocalDate.now().getYear(), 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        panel.add(yearSpinner);

        // 月份選擇
        panel.add(new JLabel(I18n.get("dialog.selectdate.month") + ":"));
        SpinnerNumberModel monthModel = new SpinnerNumberModel(
                LocalDate.now().getMonthValue(), 1, 12, 1);
        JSpinner monthSpinner = new JSpinner(monthModel);
        panel.add(monthSpinner);

        // 日期選擇
        panel.add(new JLabel(I18n.get("dialog.selectdate.day") + ":"));
        SpinnerNumberModel dayModel = new SpinnerNumberModel(
                LocalDate.now().getDayOfMonth(), 1, 31, 1);
        JSpinner daySpinner = new JSpinner(dayModel);
        panel.add(daySpinner);

        dialog.add(panel, BorderLayout.CENTER);

        // 按鈕面板
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
                        I18n.get("dialog.selectdate.error") + ": " + ex.getMessage(),
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

