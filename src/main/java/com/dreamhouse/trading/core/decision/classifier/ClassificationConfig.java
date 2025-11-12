package com.dreamhouse.trading.core.decision.classifier;

/**
 * 交易模式分類配置
 * 定義各種交易模式的判斷閾值
 */
public class ClassificationConfig {

    // ========== 當沖交易（Day Trade）判斷標準 ==========

    /**
     * 當沖所需最低流動性等級（4 = HIGH）
     */
    private int dayTradeMinLiquidityLevel = 4;

    /**
     * 當沖所需最低波動度（ATR%）
     */
    private double dayTradeMinVolatility = 1.0;  // 1.0%

    /**
     * 當沖是否需要活躍時段
     */
    private boolean dayTradeRequireActiveTime = true;

    // ========== 短線交易（Short Swing）判斷標準 ==========

    /**
     * 短線所需最低流動性等級（3 = MEDIUM）
     */
    private int shortSwingMinLiquidityLevel = 3;

    /**
     * 短線所需最低波動度（ATR%）
     */
    private double shortSwingMinVolatility = 0.5;  // 0.5%

    /**
     * 短線所需最低趨勢信心度
     */
    private double shortSwingMinTrendConfidence = 0.5;

    // ========== 波段交易（Swing Trade）判斷標準 ==========

    /**
     * 波段所需最低流動性等級（2 = LOW）
     */
    private int swingTradeMinLiquidityLevel = 2;

    /**
     * 波段所需最低週線環境信心度
     */
    private double swingTradeMinRegimeConfidence = 0.5;

    /**
     * 波段所需最低趨勢強度
     */
    private double swingTradeMinTrendStrength = 0.6;

    /**
     * 波段是否需要週線多頭環境
     */
    private boolean swingTradeRequireBullRegime = true;

    // ========== 整體判斷參數 ==========

    /**
     * 最低信心度閾值（低於此值建議觀望）
     */
    private double minConfidenceThreshold = 0.3;

    /**
     * 是否啟用嚴格模式（所有條件都必須滿足）
     */
    private boolean strictMode = false;

    // ========== 預設配置工廠方法 ==========

    /**
     * 創建保守配置（嚴格標準）
     */
    public static ClassificationConfig createConservative() {
        ClassificationConfig config = new ClassificationConfig();
        config.setDayTradeMinLiquidityLevel(5);  // VERY_HIGH
        config.setDayTradeMinVolatility(1.2);
        config.setShortSwingMinTrendConfidence(0.6);
        config.setSwingTradeMinTrendStrength(0.7);
        config.setMinConfidenceThreshold(0.5);
        config.setStrictMode(true);
        return config;
    }

    /**
     * 創建激進配置（寬鬆標準）
     */
    public static ClassificationConfig createAggressive() {
        ClassificationConfig config = new ClassificationConfig();
        config.setDayTradeMinLiquidityLevel(3);  // MEDIUM
        config.setDayTradeMinVolatility(0.8);
        config.setShortSwingMinTrendConfidence(0.4);
        config.setSwingTradeMinTrendStrength(0.5);
        config.setMinConfidenceThreshold(0.2);
        config.setStrictMode(false);
        return config;
    }

    /**
     * 創建預設配置（平衡標準）
     */
    public static ClassificationConfig createDefault() {
        return new ClassificationConfig();
    }

    // ========== Getters and Setters ==========

    public int getDayTradeMinLiquidityLevel() {
        return dayTradeMinLiquidityLevel;
    }

    public void setDayTradeMinLiquidityLevel(int dayTradeMinLiquidityLevel) {
        this.dayTradeMinLiquidityLevel = dayTradeMinLiquidityLevel;
    }

    public double getDayTradeMinVolatility() {
        return dayTradeMinVolatility;
    }

    public void setDayTradeMinVolatility(double dayTradeMinVolatility) {
        this.dayTradeMinVolatility = dayTradeMinVolatility;
    }

    public boolean isDayTradeRequireActiveTime() {
        return dayTradeRequireActiveTime;
    }

    public void setDayTradeRequireActiveTime(boolean dayTradeRequireActiveTime) {
        this.dayTradeRequireActiveTime = dayTradeRequireActiveTime;
    }

    public int getShortSwingMinLiquidityLevel() {
        return shortSwingMinLiquidityLevel;
    }

    public void setShortSwingMinLiquidityLevel(int shortSwingMinLiquidityLevel) {
        this.shortSwingMinLiquidityLevel = shortSwingMinLiquidityLevel;
    }

    public double getShortSwingMinVolatility() {
        return shortSwingMinVolatility;
    }

    public void setShortSwingMinVolatility(double shortSwingMinVolatility) {
        this.shortSwingMinVolatility = shortSwingMinVolatility;
    }

    public double getShortSwingMinTrendConfidence() {
        return shortSwingMinTrendConfidence;
    }

    public void setShortSwingMinTrendConfidence(double shortSwingMinTrendConfidence) {
        this.shortSwingMinTrendConfidence = shortSwingMinTrendConfidence;
    }

    public int getSwingTradeMinLiquidityLevel() {
        return swingTradeMinLiquidityLevel;
    }

    public void setSwingTradeMinLiquidityLevel(int swingTradeMinLiquidityLevel) {
        this.swingTradeMinLiquidityLevel = swingTradeMinLiquidityLevel;
    }

    public double getSwingTradeMinRegimeConfidence() {
        return swingTradeMinRegimeConfidence;
    }

    public void setSwingTradeMinRegimeConfidence(double swingTradeMinRegimeConfidence) {
        this.swingTradeMinRegimeConfidence = swingTradeMinRegimeConfidence;
    }

    public double getSwingTradeMinTrendStrength() {
        return swingTradeMinTrendStrength;
    }

    public void setSwingTradeMinTrendStrength(double swingTradeMinTrendStrength) {
        this.swingTradeMinTrendStrength = swingTradeMinTrendStrength;
    }

    public boolean isSwingTradeRequireBullRegime() {
        return swingTradeRequireBullRegime;
    }

    public void setSwingTradeRequireBullRegime(boolean swingTradeRequireBullRegime) {
        this.swingTradeRequireBullRegime = swingTradeRequireBullRegime;
    }

    public double getMinConfidenceThreshold() {
        return minConfidenceThreshold;
    }

    public void setMinConfidenceThreshold(double minConfidenceThreshold) {
        this.minConfidenceThreshold = minConfidenceThreshold;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    @Override
    public String toString() {
        return String.format("ClassificationConfig[dayTrade(liq=%d,vol=%.1f%%), shortSwing(liq=%d,trend=%.1f), swing(regime=%.1f,trend=%.1f)]",
                dayTradeMinLiquidityLevel, dayTradeMinVolatility * 100,
                shortSwingMinLiquidityLevel, shortSwingMinTrendConfidence * 100,
                swingTradeMinRegimeConfidence * 100, swingTradeMinTrendStrength * 100);
    }
}
