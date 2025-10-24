package com.dreamhouse.trading;

import com.dreamhouse.trading.ui.MainFrameWithDocking;
import com.dreamhouse.trading.util.I18n;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {
        // 設定預設語言（可改為 Locale.ENGLISH）
        I18n.setLocale(Locale.TRADITIONAL_CHINESE);
        
        // 設定 FlatDarkLaf 主題
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 啟動主視窗（使用 Modern Docking 版本）
        SwingUtilities.invokeLater(() -> {
            MainFrameWithDocking frame = new MainFrameWithDocking();
            frame.setVisible(true);
        });
    }
}
