package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.util.I18n;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

public class MenuBarFactory {
    
    public static JMenuBar createMenuBar(JFrame frame, Callbacks callbacks) {
        JMenuBar menuBar = new JMenuBar();
        
        // File Menu
        JMenu fileMenu = new JMenu(I18n.get("menu.file"));
        
        JMenuItem importCsvItem = new JMenuItem(I18n.get("menu.file.import.csv"));
        importCsvItem.addActionListener(e -> {
            if (callbacks.onImportCsv != null) {
                callbacks.onImportCsv.run();
            }
        });
        fileMenu.add(importCsvItem);
        
        JMenuItem exportCsvItem = new JMenuItem(I18n.get("menu.file.export.csv"));
        exportCsvItem.addActionListener(e -> {
            if (callbacks.onExportCsv != null) {
                callbacks.onExportCsv.run();
            }
        });
        fileMenu.add(exportCsvItem);
        
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem(I18n.get("menu.file.exit"));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        menuBar.add(fileMenu);
        
        // Tools Menu
        JMenu toolsMenu = new JMenu(I18n.get("menu.tools"));
        
        JMenuItem backtestItem = new JMenuItem(I18n.get("menu.tools.backtest"));
        backtestItem.addActionListener(e -> {
            if (callbacks.onBacktest != null) {
                callbacks.onBacktest.run();
            }
        });
        toolsMenu.add(backtestItem);
        
        menuBar.add(toolsMenu);
        
        // View Menu
        JMenu viewMenu = new JMenu("View");
        
        JMenu themeMenu = new JMenu("Theme");
        JMenuItem lightTheme = new JMenuItem("Light");
        JMenuItem darkTheme = new JMenuItem("Dark");
        
        lightTheme.addActionListener(e -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
                SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        darkTheme.addActionListener(e -> {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
                SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        themeMenu.add(lightTheme);
        themeMenu.add(darkTheme);
        viewMenu.add(themeMenu);
        
        // 指標設定
        viewMenu.addSeparator();
        JMenuItem indicatorSettings = new JMenuItem(I18n.get("menu.view.indicator.settings"));
        indicatorSettings.addActionListener(e -> {
            if (callbacks.onIndicatorSettings != null) {
                callbacks.onIndicatorSettings.run();
            }
        });
        viewMenu.add(indicatorSettings);
        
        menuBar.add(viewMenu);
        
        // Layout Menu
        JMenu layoutMenu = new JMenu("Layout");
        JMenuItem resetLayout = new JMenuItem("Reset Layout");
        JMenuItem saveLayout = new JMenuItem("Save Layout");
        resetLayout.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Reset Layout (Not implemented)"));
        saveLayout.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Save Layout (Not implemented)"));
        layoutMenu.add(resetLayout);
        layoutMenu.add(saveLayout);
        menuBar.add(layoutMenu);
        
        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> 
            JOptionPane.showMessageDialog(frame, 
                "DreamHouse Trading Workstation\nVersion 0.0.1\n\nAdvanced Trading Platform", 
                "About", 
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    public static class Callbacks {
        public Runnable onIndicatorSettings;
        public Runnable onImportCsv;
        public Runnable onExportCsv;
        public Runnable onBacktest;
    }
}

