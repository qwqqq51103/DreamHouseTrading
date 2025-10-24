package com.dreamhouse.trading.util;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {
    private static Locale currentLocale = Locale.ENGLISH;
    private static ResourceBundle messages = ResourceBundle.getBundle("messages", currentLocale);
    
    public static String get(String key) {
        try {
            return messages.getString(key);
        } catch (Exception e) {
            return key;
        }
    }
    
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        messages = ResourceBundle.getBundle("messages", currentLocale);
    }
    
    public static Locale getCurrentLocale() {
        return currentLocale;
    }
    
    public static void setEnglish() {
        setLocale(Locale.ENGLISH);
    }
    
    public static void setChinese() {
        setLocale(Locale.TRADITIONAL_CHINESE);
    }
}

