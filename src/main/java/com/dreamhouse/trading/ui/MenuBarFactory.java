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
        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        
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
    }
}

