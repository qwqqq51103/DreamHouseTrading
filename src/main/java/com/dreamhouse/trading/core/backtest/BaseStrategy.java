package com.dreamhouse.trading.core.backtest;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 策略基類
 * 提供策略的基本實現和通用功能
 */
public abstract class BaseStrategy implements Strategy {
    
    protected BacktestEngine engine;
    protected BarSeries barSeries;
    protected StrategyConfig config;
    protected String name;
    protected String description;
    
    /**
     * 構造函數
     */
    public BaseStrategy(String name, String description) {
        this.name = name;
        this.description = description;
        this.config = new StrategyConfig();
        initializeDefaultConfig();
    }
    
    /**
     * 初始化默認配置
     * 子類可以重寫此方法設定默認參數
     */
    protected void initializeDefaultConfig() {
        // 默認實現為空，子類可以重寫
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public void initialize(BarSeries barSeries) {
        this.barSeries = barSeries;
        onInitialize();
    }
    
    @Override
    public void cleanup() {
        onCleanup();
    }
    
    @Override
    public void setEngine(BacktestEngine engine) {
        this.engine = engine;
    }
    
    @Override
    public BacktestEngine getEngine() {
        return engine;
    }
    
    @Override
    public StrategyConfig getConfig() {
        return config;
    }
    
    @Override
    public void setConfig(StrategyConfig config) {
        this.config = config;
    }
    
    /**
     * 子類初始化邏輯
     */
    protected void onInitialize() {
        // 默認實現為空，子類可以重寫
    }
    
    /**
     * 子類清理邏輯
     */
    protected void onCleanup() {
        // 默認實現為空，子類可以重寫
    }
    
    /**
     * 執行買入操作
     */
    protected boolean buy(String symbol, int quantity) {
        return engine != null && engine.buy(symbol, quantity, OrderType.MARKET);
    }
    
    /**
     * 執行賣出操作
     */
    protected boolean sell(String symbol, int quantity) {
        return engine != null && engine.sell(symbol, quantity, OrderType.MARKET);
    }
    
    /**
     * 獲取當前價格
     */
    protected double getCurrentPrice() {
        if (barSeries != null && barSeries.getBarCount() > 0) {
            return barSeries.getLastBar().getClosePrice().doubleValue();
        }
        return 0.0;
    }
    
    /**
     * 獲取指定索引的K線
     */
    protected Bar getBar(int index) {
        if (barSeries != null && index >= 0 && index < barSeries.getBarCount()) {
            return barSeries.getBar(index);
        }
        return null;
    }
    
    /**
     * 獲取當前投資組合
     */
    protected Portfolio getPortfolio() {
        return engine != null ? engine.getPortfolio() : null;
    }
    
    /**
     * 檢查是否有足夠的現金
     */
    protected boolean hasEnoughCash(double amount) {
        Portfolio portfolio = getPortfolio();
        return portfolio != null && portfolio.hasEnoughCash(amount);
    }
    
    /**
     * 檢查是否有持倉
     */
    protected boolean hasPosition(String symbol) {
        Portfolio portfolio = getPortfolio();
        return portfolio != null && portfolio.hasPosition(symbol);
    }
    
    /**
     * 獲取持倉數量
     */
    protected int getPositionQuantity(String symbol) {
        Portfolio portfolio = getPortfolio();
        if (portfolio != null) {
            Position position = portfolio.getPosition(symbol);
            return position != null ? position.getQuantity() : 0;
        }
        return 0;
    }
    
    /**
     * 記錄日誌
     */
    protected void log(String message) {
        System.out.println("[" + name + "] " + message);
    }
    
    /**
     * 記錄調試信息
     */
    protected void debug(String message) {
        if (config.getBooleanParameter("debug", false)) {
            System.out.println("[DEBUG " + name + "] " + message);
        }
    }
}
