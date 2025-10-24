package com.dreamhouse.trading.core;

import java.awt.Color;

/**
 * 指標配置類別
 */
public class IndicatorConfig {
    private int period;
    private int period2;  // 用於 KD 的 D 週期
    private int fastPeriod;  // 用於 MACD
    private int slowPeriod;  // 用於 MACD
    private int signalPeriod;  // 用於 MACD
    private double multiplier;  // 用於布林通道
    private Color color;
    private Color color2;  // 用於第二條線（如 MACD Signal）
    private Color color3;  // 用於第三條線（如 MACD Histogram）
    
    public IndicatorConfig() {
        this.period = 20;
        this.period2 = 3;
        this.fastPeriod = 12;
        this.slowPeriod = 26;
        this.signalPeriod = 9;
        this.multiplier = 2.0;
        this.color = Color.BLUE;
        this.color2 = Color.RED;
        this.color3 = Color.GREEN;
    }
    
    // Getters and Setters
    public int getPeriod() {
        return period;
    }
    
    public void setPeriod(int period) {
        this.period = period;
    }
    
    public int getPeriod2() {
        return period2;
    }
    
    public void setPeriod2(int period2) {
        this.period2 = period2;
    }
    
    public int getFastPeriod() {
        return fastPeriod;
    }
    
    public void setFastPeriod(int fastPeriod) {
        this.fastPeriod = fastPeriod;
    }
    
    public int getSlowPeriod() {
        return slowPeriod;
    }
    
    public void setSlowPeriod(int slowPeriod) {
        this.slowPeriod = slowPeriod;
    }
    
    public int getSignalPeriod() {
        return signalPeriod;
    }
    
    public void setSignalPeriod(int signalPeriod) {
        this.signalPeriod = signalPeriod;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
    
    public Color getColor() {
        return color;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public Color getColor2() {
        return color2;
    }
    
    public void setColor2(Color color2) {
        this.color2 = color2;
    }
    
    public Color getColor3() {
        return color3;
    }
    
    public void setColor3(Color color3) {
        this.color3 = color3;
    }
}

