package com.dreamhouse.trading.core.backtest;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略配置類
 * 用於存儲策略的參數設定
 */
public class StrategyConfig {
    
    private final Map<String, Object> parameters;
    
    /**
     * 構造函數
     */
    public StrategyConfig() {
        this.parameters = new HashMap<>();
    }
    
    /**
     * 設定參數
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    /**
     * 獲取參數
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        Object value = parameters.get(key);
        if (value != null) {
            try {
                return (T) value;
            } catch (ClassCastException e) {
                System.err.println("Parameter type mismatch for key: " + key);
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 獲取整數參數
     */
    public int getIntParameter(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 獲取雙精度參數
     */
    public double getDoubleParameter(String key, double defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 獲取布林參數
     */
    public boolean getBooleanParameter(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * 獲取字串參數
     */
    public String getStringParameter(String key, String defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
    
    /**
     * 檢查是否包含參數
     */
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
    
    /**
     * 移除參數
     */
    public void removeParameter(String key) {
        parameters.remove(key);
    }
    
    /**
     * 清空所有參數
     */
    public void clear() {
        parameters.clear();
    }
    
    /**
     * 獲取所有參數鍵
     */
    public java.util.Set<String> getParameterKeys() {
        return parameters.keySet();
    }
    
    /**
     * 複製配置
     */
    public StrategyConfig copy() {
        StrategyConfig copy = new StrategyConfig();
        copy.parameters.putAll(this.parameters);
        return copy;
    }
    
    @Override
    public String toString() {
        return "StrategyConfig{parameters=" + parameters + "}";
    }
}
