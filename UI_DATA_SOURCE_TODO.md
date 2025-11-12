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

### 階段 1: 添加 DecisionEngine 的 Getter 方法 ✅
- [x] `getLastRegimeAnalysis()` - 已存在
- [x] `getLastTrendAnalysis()` - 已存在
- [x] `getLastVotingResult()` - 已存在

### 階段 2: 實作 MarketAnalysisDock 數據源 ⚠️
- [ ] 添加 `setDecisionEngine()` 方法
- [ ] 添加 `updateMarketAnalysis()` 方法
- [ ] 測試數據顯示

**狀態**: 待實作 - UI 組件結構與預期不同，需要重新分析實際 UI 組件

### 階段 3: 實作 ModeRecommendationDock 數據源 ⚠️
- [ ] 添加 `setDecisionEngine()` 方法
- [ ] 添加 `updateRecommendation()` 方法
- [ ] 實作模式推薦邏輯
- [ ] 測試模式建議

**狀態**: 待實作 - UI 組件結構與預期不同，需要重新分析實際 UI 組件

### 階段 4: 整合到主框架 ✅
- [x] 擴展 BacktestListener 接口 - 已添加 `onBarProcessed(int barIndex, Bar bar)` 方法
- [x] 在 BacktestEngine 中添加 `onBarProcessed` 回調 - 已在 `processBar()` 中添加通知邏輯
- [ ] 在 MainFrameWithDocking 中監聽回測事件
- [ ] 更新 UI 面板

**狀態**: 部分完成 - 基礎設施已就緒，待 UI 面板實作完成後連接

### 階段 5: 測試與驗證 ⏳
- [ ] 回測時檢查市場分析面板更新
- [ ] 回測時檢查模式建議更新
- [ ] 回測時檢查執行狀態更新
- [ ] 驗證數據準確性

**狀態**: 待實作

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

## 📊 實作總結

### 已完成項目 ✅

1. **DecisionEngine Getter 方法確認** (階段1)
   - 確認 `getLastRegimeAnalysis()` 已存在
   - 確認 `getLastTrendAnalysis()` 已存在
   - 確認 `getLastVotingResult()` 已存在
   - 位置: `src/main/java/com/dreamhouse/trading/core/decision/DecisionEngine.java:480-490`

2. **BacktestListener 接口擴展** (階段4)
   - 添加 `onBarProcessed(int barIndex, org.ta4j.core.Bar bar)` 方法
   - 使用 `default` 實作，避免破壞現有代碼
   - 位置: `src/main/java/com/dreamhouse/trading/core/backtest/BacktestListener.java:20-27`

3. **BacktestEngine 回調通知** (階段4)
   - 在 `processBar()` 方法中添加 `notifyBarProcessed()` 調用
   - 添加 `notifyBarProcessed()` 私有方法
   - 位置: `src/main/java/com/dreamhouse/trading/core/backtest/BacktestEngine.java:191, 343-347`

4. **編譯驗證**
   - 編譯成功，無錯誤
   - 基礎架構已就緒

### 待完成項目 ⏳

1. **MarketAnalysisDock 數據源** (階段2)
   - 原因: UI 組件結構與文檔預期不同
   - 需要: 仔細分析實際 UI 組件名稱和結構

2. **ModeRecommendationDock 數據源** (階段3)
   - 原因: UI 組件結構與文檔預期不同
   - 已存在 `updateRecommendation(ClassificationResult)` 方法
   - 需要: 了解如何從 DecisionEngine 數據生成 ClassificationResult

3. **MainFrameWithDocking 監聽器連接** (階段4)
   - 需要: 在回測時添加監聽器，調用正確的 UI 更新方法
   - 依賴: 階段2和階段3完成

4. **測試與驗證** (階段5)
   - 需要: 執行實際回測，檢查 UI 面板是否正確更新

### 技術難點

1. **UI 組件不匹配**: 文檔中假設的 UI 組件名稱（如 `recommendedModeLabel`, `reasonTextArea`）與實際代碼不符（實際是 `primaryModeLabel`, `reasoningArea`）

2. **API 不匹配**:
   - `RegimeAnalysis.getRegime()` 不存在，應該是 `getMarketRegime()`
   - `RegimeAnalysis.getAdx()` 不存在，而是 `getTrendStrength()`
   - `TrendAnalysis.getStrength()` 返回 `TrendStrength` 枚舉，不是 double
   - `TrendAnalysis.getBias()` 應該是 `getBiasSide()`

3. **ClassificationResult 構造**: ModeRecommendationDock 已有 `updateRecommendation(ClassificationResult)` 方法，需要了解如何正確構造 ClassificationResult 對象

### 下一步建議

1. 詳細閱讀 MarketAnalysisDock.java 和 ModeRecommendationDock.java，找出實際的 UI 組件名稱
2. 確認如何從 DecisionEngine 的數據構造 ClassificationResult
3. 實作正確的數據綁定方法
4. 在 MainFrameWithDocking 中添加監聽器調用
5. 執行回測測試

---

**文檔版本**: 1.1
**創建時間**: 2025-11-12
**更新時間**: 2025-11-12 17:54
**估計工時**: 4-6 小時 (剩餘: 2-3 小時)
