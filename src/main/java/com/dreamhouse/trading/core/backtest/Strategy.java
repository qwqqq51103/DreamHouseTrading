package com.dreamhouse.trading.core.backtest;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 交易策略接口
 * 所有交易策略都必須實現此接口
 */
public interface Strategy {
    
    /**
     * 策略名稱
     */
    String getName();
    
    /**
     * 策略描述
     */
    String getDescription();
    
    /**
     * 初始化策略
     * 在回測開始前調用
     */
    void initialize(BarSeries barSeries);
    
    /**
     * 處理新的K線數據
     * 每根K線都會調用此方法
     * 
     * @param barIndex 當前K線索引
     * @param bar 當前K線數據
     */
    void onBar(int barIndex, Bar bar);
    
    /**
     * 策略清理
     * 在回測結束後調用
     */
    void cleanup();
    
    /**
     * 設定回測引擎
     */
    void setEngine(BacktestEngine engine);
    
    /**
     * 獲取回測引擎
     */
    BacktestEngine getEngine();
    
    /**
     * 策略參數配置
     */
    StrategyConfig getConfig();
    
    /**
     * 設定策略參數
     */
    void setConfig(StrategyConfig config);
}
