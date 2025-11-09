package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class ToolBarFactory {
    
    public static class ToolBarCallbacks {
        public Consumer<String> onSymbolChange;
        public Consumer<Timeframe> onTimeframeChange;
        public Consumer<String> onIndicatorChange;
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
        JTextField symbolField = new JTextField("AAPL", 8);
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
}

