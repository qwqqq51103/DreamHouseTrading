# UI 數據源實作待辦清單

> **創建日期**: 2025-11-12
> **狀態**: 需要實作
> **優先級**: 中

---

## 📊 當前狀態

### ExecutionStatusDock ✅ 已完成
- 已有 `setExecutionEngine()` 方法
- 已有 `updateDisplay()` 邏輯
- 可正常顯示執行狀態和訂單歷史

### MarketAnalysisDock ❌ 需要實作
- UI 框架已完成 (482 行)
- 缺少數據源接口
- 缺少更新邏輯

### ModeRecommendationDock ❌ 需要實作
- UI 框架已完成
- 缺少數據源接口
- 缺少更新邏輯

---

## 🔧 實作方案

### 1. MarketAnalysisDock 數據源

#### 需要添加的方法

```java
/**
 * 設定決策引擎
 */
public void setDecisionEngine(DecisionEngine engine) {
    this.decisionEngine = engine;
    updateDisplay();
}

/**
 * 更新市場分析顯示
 */
public void updateMarketAnalysis() {
    if (decisionEngine == null) {
        return;
    }

    SwingUtilities.invokeLater(() -> {
        // 1. 週線環境分析
        RegimeAnalysis regimeAnalysis = decisionEngine.getLastRegimeAnalysis();
        if (regimeAnalysis != null) {
            weeklyRegimeLabel.setText(regimeAnalysis.getRegime().getDisplayName());
            weeklyTrendLabel.setText(regimeAnalysis.getTrendDirection().name());
            weeklyADXLabel.setText(String.format("%.1f", regimeAnalysis.getAdx()));
            weeklyVolatilityLabel.setText(String.format("%.2f%%", regimeAnalysis.getVolatility() * 100));
            weeklyConfidenceBar.setValue((int)(regimeAnalysis.getConfidence() * 100));
        }

        // 2. 日線趨勢分析
        TrendAnalysis trendAnalysis = decisionEngine.getLastTrendAnalysis();
        if (trendAnalysis != null) {
            dailyTrendLabel.setText(trendAnalysis.getTrend().getDisplayName());
            dailyStrengthBar.setValue((int)(trendAnalysis.getStrength() * 100));
            dailyDirectionLabel.setText(trendAnalysis.getBias().name());
        }

        // 3. 分鐘層盤中分析
        // TODO: 需要從 DecisionEngine 獲取盤中分析數據

        // 4. 策略投票結果
        VotingResult votingResult = decisionEngine.getLastVotingResult();
        if (votingResult != null) {
            votingLongScoreLabel.setText(String.format("%.2f", votingResult.getLongScore()));
            votingShortScoreLabel.setText(String.format("%.2f", votingResult.getShortScore()));
            votingExitScoreLabel.setText(String.format("%.2f", votingResult.getExitScore()));
        }
    });
}
```

#### 需要添加的數據源接口到 DecisionEngine

```java
// 在 DecisionEngine.java 中添加

/**
 * 獲取最近的週線環境分析
 */
public RegimeAnalysis getLastRegimeAnalysis() {
    return lastRegimeAnalysis;
}

/**
 * 獲取最近的日線趨勢分析
 */
public TrendAnalysis getLastTrendAnalysis() {
    return lastTrendAnalysis;
}

/**
 * 獲取最近的投票結果
 */
public VotingResult getLastVotingResult() {
    return lastVotingResult;
}
```

#### 在 MainFrameWithDocking 中添加更新邏輯

```java
// 在回測監聽器中添加
private class BacktestProgressListener implements BacktestListener {
    @Override
    public void onBarProcessed(int barIndex, Bar bar) {
        // 更新市場分析面板
        if (currentStrategy instanceof MultiTimeframeDecisionStrategy) {
            MultiTimeframeDecisionStrategy mtStrategy =
                (MultiTimeframeDecisionStrategy) currentStrategy;
            DecisionEngine decisionEngine = mtStrategy.getDecisionEngine();

            if (decisionEngine != null) {
                marketAnalysisDock.setDecisionEngine(decisionEngine);
                marketAnalysisDock.updateMarketAnalysis();
            }
        }
    }
}
```

---

### 2. ModeRecommendationDock 數據源

#### 需要添加的方法

```java
/**
 * 設定決策引擎
 */
public void setDecisionEngine(DecisionEngine engine) {
    this.decisionEngine = engine;
    updateRecommendation();
}

/**
 * 更新模式建議
 */
public void updateRecommendation() {
    if (decisionEngine == null) {
        return;
    }

    SwingUtilities.invokeLater(() -> {
        // 1. 判斷當前市場環境
        RegimeAnalysis regime = decisionEngine.getLastRegimeAnalysis();
        TrendAnalysis trend = decisionEngine.getLastTrendAnalysis();

        String recommendedMode = "未知";
        String reason = "";
        Color modeColor = Color.GRAY;

        if (regime != null && trend != null) {
            // 根據市場環境推薦交易模式
            if (regime.getRegime() == MarketRegime.TRENDING &&
                trend.getStrength() > 0.7) {
                // 強趨勢 → 建議波段交易
                recommendedMode = "波段交易";
                reason = "市場處於強趨勢，適合波段操作";
                modeColor = new Color(0, 200, 0);
            } else if (regime.getVolatility() > 0.02 &&
                       trend.getStrength() > 0.5) {
                // 中等波動 + 中等趨勢 → 建議短線交易
                recommendedMode = "短線交易";
                reason = "市場波動適中，趨勢明確，適合短線";
                modeColor = new Color(255, 215, 0);
            } else if (regime.getVolatility() > 0.01) {
                // 高波動 → 建議當沖
                recommendedMode = "當沖交易";
                reason = "市場波動較大，建議當日沖銷";
                modeColor = new Color(255, 140, 0);
            } else {
                // 低波動/盤整 → 建議觀望
                recommendedMode = "觀望";
                reason = "市場盤整或波動過低，建議觀望";
                modeColor = Color.GRAY;
            }
        }

        // 更新 UI
        recommendedModeLabel.setText(recommendedMode);
        recommendedModeLabel.setForeground(modeColor);
        reasonTextArea.setText(reason);

        // 更新各模式適合度
        updateModeSuitability(regime, trend);
    });
}

/**
 * 更新各交易模式的適合度
 */
private void updateModeSuitability(RegimeAnalysis regime, TrendAnalysis trend) {
    if (regime == null || trend == null) {
        return;
    }

    // 計算各模式適合度 (0-100)
    int dayTradingSuit = calculateDayTradingSuitability(regime, trend);
    int swingTradingSuit = calculateSwingTradingSuitability(regime, trend);
    int positionTradingSuit = calculatePositionTradingSuitability(regime, trend);

    dayTradingSuitBar.setValue(dayTradingSuit);
    swingTradingSuitBar.setValue(swingTradingSuit);
    positionTradingSuitBar.setValue(positionTradingSuit);
}

private int calculateDayTradingSuitability(RegimeAnalysis regime, TrendAnalysis trend) {
    int score = 50; // 基礎分數

    // 高波動加分
    if (regime.getVolatility() > 0.02) score += 30;
    else if (regime.getVolatility() > 0.01) score += 15;

    // ADX 適中加分
    if (regime.getAdx() > 20 && regime.getAdx() < 40) score += 20;

    return Math.min(100, score);
}

// 類似實作 calculateSwingTradingSuitability 和 calculatePositionTradingSuitability
```

---

### 3. 整合到回測系統

#### 在 BacktestEngine 中添加監聽器接口

```java
public interface BacktestListener {
    void onBacktestStarted();
    void onBarProcessed(int barIndex, Bar bar); // 新增
    void onProgressUpdate(double progress);
    void onTradeExecuted(String symbol, TradeType type, int quantity, double price);
    void onBacktestCompleted(BacktestResult result);
}
```

#### 在回測循環中調用

```java
// 在 BacktestEngine.run() 中
for (int i = startIndex; i < barSeries.getBarCount(); i++) {
    currentBarIndex = i;
    Bar bar = barSeries.getBar(i);

    // 執行策略邏輯
    strategy.onBar(i, bar);

    // 通知監聽器 K 線已處理 (新增)
    notifyBarProcessed(i, bar);

    // 更新進度
    double progress = (double) (i - startIndex + 1) / (barSeries.getBarCount() - startIndex);
    notifyProgressUpdate(progress);
}
```

---

## 📝 實作步驟

### 階段 1: 添加 DecisionEngine 的 Getter 方法
- [ ] `getLastRegimeAnalysis()`
- [ ] `getLastTrendAnalysis()`
- [ ] `getLastVotingResult()`

### 階段 2: 實作 MarketAnalysisDock 數據源
- [ ] 添加 `setDecisionEngine()` 方法
- [ ] 添加 `updateMarketAnalysis()` 方法
- [ ] 測試數據顯示

### 階段 3: 實作 ModeRecommendationDock 數據源
- [ ] 添加 `setDecisionEngine()` 方法
- [ ] 添加 `updateRecommendation()` 方法
- [ ] 實作模式推薦邏輯
- [ ] 測試模式建議

### 階段 4: 整合到主框架
- [ ] 擴展 BacktestListener 接口
- [ ] 在 BacktestEngine 中添加 `onBarProcessed` 回調
- [ ] 在 MainFrameWithDocking 中監聽回測事件
- [ ] 更新 UI 面板

### 階段 5: 測試與驗證
- [ ] 回測時檢查市場分析面板更新
- [ ] 回測時檢查模式建議更新
- [ ] 回測時檢查執行狀態更新
- [ ] 驗證數據準確性

---

## ⚠️ 注意事項

1. **線程安全**: 所有 UI 更新必須在 EDT (Event Dispatch Thread) 中執行，使用 `SwingUtilities.invokeLater()`

2. **性能考慮**: 不要在每根 K 線處理時都更新 UI，可以每 10 根或每秒更新一次

3. **空值檢查**: DecisionEngine 可能返回 null，必須做好空值檢查

4. **回測結束清理**: 回測結束後清理監聽器，避免內存洩漏

---

## 🚀 預期效果

完成後，用戶在回測時可以實時看到：
- ✅ 週線環境分析 (趨勢、波動率、ADX)
- ✅ 日線趨勢分析 (趨勢強度、方向)
- ✅ 策略投票得分 (做多、做空、出場分數)
- ✅ 模式建議 (當沖、短線、波段適合度)
- ✅ 執行狀態 (訂單歷史、成功率)

---

**文檔版本**: 1.0
**創建時間**: 2025-11-12
**估計工時**: 4-6 小時
