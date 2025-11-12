package com.dreamhouse.trading.core.decision.intraday;

/**
 * 盤中分析配置
 * 定義流動性與波動度的判斷標準
 */
public class IntradayConfig {

    // ========== 流動性相關參數 ==========

    /**
     * 極高流動性成交量門檻（張數）
     */
    private int veryHighVolumeThreshold = 5000;

    /**
     * 高流動性成交量門檻（張數）
     */
    private int highVolumeThreshold = 3000;

    /**
     * 中等流動性成交量門檻（張數）
     */
    private int mediumVolumeThreshold = 1000;

    /**
     * 低流動性成交量門檻（張數）
     */
    private int lowVolumeThreshold = 500;

    /**
     * 極高流動性點差門檻（百分比）
     */
    private double veryHighSpreadThreshold = 0.05;  // 0.05%

    /**
     * 高流動性點差門檻（百分比）
     */
    private double highSpreadThreshold = 0.15;  // 0.15%

    /**
     * 中等流動性點差門檻（百分比）
     */
    private double mediumSpreadThreshold = 0.30;  // 0.30%

    /**
     * 低流動性點差門檻（百分比）
     */
    private double lowSpreadThreshold = 0.50;  // 0.50%

    // ========== 波動度相關參數 ==========

    /**
     * ATR 計算週期
     */
    private int atrPeriod = 14;

    /**
     * 高波動度 ATR% 門檻
     */
    private double highVolatilityThreshold = 1.5;  // 1.5%

    /**
     * 中等波動度 ATR% 門檻
     */
    private double mediumVolatilityThreshold = 1.0;  // 1.0%

    /**
     * 低波動度 ATR% 門檻
     */
    private double lowVolatilityThreshold = 0.5;  // 0.5%

    // ========== 時間過濾參數 ==========

    /**
     * 是否啟用時間過濾
     */
    private boolean enableTimeFilter = true;

    /**
     * 開盤後避開時間（分鐘）
     */
    private int avoidMinutesAfterOpen = 30;

    /**
     * 收盤前避開時間（分鐘）
     */
    private int avoidMinutesBeforeClose = 30;

    /**
     * 成交量計算窗口（K 線數量）
     */
    private int volumeWindow = 20;

    // ========== 預設配置工廠方法 ==========

    /**
     * 創建保守配置（嚴格標準）
     */
    public static IntradayConfig createConservative() {
        IntradayConfig config = new IntradayConfig();
        config.setHighVolumeThreshold(5000);
        config.setHighSpreadThreshold(0.10);
        config.setMediumVolatilityThreshold(1.2);
        return config;
    }

    /**
     * 創建激進配置（寬鬆標準）
     */
    public static IntradayConfig createAggressive() {
        IntradayConfig config = new IntradayConfig();
        config.setHighVolumeThreshold(2000);
        config.setHighSpreadThreshold(0.20);
        config.setMediumVolatilityThreshold(0.8);
        return config;
    }

    /**
     * 創建預設配置
     */
    public static IntradayConfig createDefault() {
        return new IntradayConfig();
    }

    // ========== Getters and Setters ==========

    public int getVeryHighVolumeThreshold() {
        return veryHighVolumeThreshold;
    }

    public void setVeryHighVolumeThreshold(int veryHighVolumeThreshold) {
        this.veryHighVolumeThreshold = veryHighVolumeThreshold;
    }

    public int getHighVolumeThreshold() {
        return highVolumeThreshold;
    }

    public void setHighVolumeThreshold(int highVolumeThreshold) {
        this.highVolumeThreshold = highVolumeThreshold;
    }

    public int getMediumVolumeThreshold() {
        return mediumVolumeThreshold;
    }

    public void setMediumVolumeThreshold(int mediumVolumeThreshold) {
        this.mediumVolumeThreshold = mediumVolumeThreshold;
    }

    public int getLowVolumeThreshold() {
        return lowVolumeThreshold;
    }

    public void setLowVolumeThreshold(int lowVolumeThreshold) {
        this.lowVolumeThreshold = lowVolumeThreshold;
    }

    public double getVeryHighSpreadThreshold() {
        return veryHighSpreadThreshold;
    }

    public void setVeryHighSpreadThreshold(double veryHighSpreadThreshold) {
        this.veryHighSpreadThreshold = veryHighSpreadThreshold;
    }

    public double getHighSpreadThreshold() {
        return highSpreadThreshold;
    }

    public void setHighSpreadThreshold(double highSpreadThreshold) {
        this.highSpreadThreshold = highSpreadThreshold;
    }

    public double getMediumSpreadThreshold() {
        return mediumSpreadThreshold;
    }

    public void setMediumSpreadThreshold(double mediumSpreadThreshold) {
        this.mediumSpreadThreshold = mediumSpreadThreshold;
    }

    public double getLowSpreadThreshold() {
        return lowSpreadThreshold;
    }

    public void setLowSpreadThreshold(double lowSpreadThreshold) {
        this.lowSpreadThreshold = lowSpreadThreshold;
    }

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = atrPeriod;
    }

    public double getHighVolatilityThreshold() {
        return highVolatilityThreshold;
    }

    public void setHighVolatilityThreshold(double highVolatilityThreshold) {
        this.highVolatilityThreshold = highVolatilityThreshold;
    }

    public double getMediumVolatilityThreshold() {
        return mediumVolatilityThreshold;
    }

    public void setMediumVolatilityThreshold(double mediumVolatilityThreshold) {
        this.mediumVolatilityThreshold = mediumVolatilityThreshold;
    }

    public double getLowVolatilityThreshold() {
        return lowVolatilityThreshold;
    }

    public void setLowVolatilityThreshold(double lowVolatilityThreshold) {
        this.lowVolatilityThreshold = lowVolatilityThreshold;
    }

    public boolean isEnableTimeFilter() {
        return enableTimeFilter;
    }

    public void setEnableTimeFilter(boolean enableTimeFilter) {
        this.enableTimeFilter = enableTimeFilter;
    }

    public int getAvoidMinutesAfterOpen() {
        return avoidMinutesAfterOpen;
    }

    public void setAvoidMinutesAfterOpen(int avoidMinutesAfterOpen) {
        this.avoidMinutesAfterOpen = avoidMinutesAfterOpen;
    }

    public int getAvoidMinutesBeforeClose() {
        return avoidMinutesBeforeClose;
    }

    public void setAvoidMinutesBeforeClose(int avoidMinutesBeforeClose) {
        this.avoidMinutesBeforeClose = avoidMinutesBeforeClose;
    }

    public int getVolumeWindow() {
        return volumeWindow;
    }

    public void setVolumeWindow(int volumeWindow) {
        this.volumeWindow = volumeWindow;
    }

    @Override
    public String toString() {
        return String.format("IntradayConfig[highVolume=%d, highSpread=%.2f%%, mediumVolatility=%.2f%%]",
                highVolumeThreshold, highSpreadThreshold * 100, mediumVolatilityThreshold * 100);
    }
}
