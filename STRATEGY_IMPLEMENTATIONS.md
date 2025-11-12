# 交易策略實際運作邏輯說明

> **文檔創建日期**: 2025-11-12
> **文檔目的**: 記錄系統中各個交易策略的實際運作邏輯與參數配置

---

## 📊 策略架構總覽

```
BaseStrategy (回測策略基類)
    │
    ├─► MultiTimeframeDecisionStrategy (多週期決策策略)
    │       ├─► DecisionEngine (決策引擎)
    │       └─► DecisionBaseStrategy[] (子策略群組)
    │               └─► SignalRSIStrategy (RSI 信號策略)
    │
    ├─► DayTradingStrategy (當沖交易策略)
    │       └─► 繼承 MultiTimeframeDecisionStrategy
    │
    ├─► SwingTradingStrategy (短線交易策略)
    │       └─► 繼承 MultiTimeframeDecisionStrategy
    │
    ├─► PositionTradingStrategy (波段交易策略)
    │       └─► 繼承 MultiTimeframeDecisionStrategy
    │
    └─► 傳統策略 (獨立運作)
            ├─► RSIStrategy (RSI 策略)
            ├─► SimpleMovingAverageStrategy (移動平均線策略)
            ├─► MACDStrategy (MACD 策略)
            └─► BollingerBandsStrategy (布林通道策略)
```

---

## 🔍 策略詳細說明

### 1. MultiTimeframeDecisionStrategy（多週期決策策略）

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/decision/strategies/MultiTimeframeDecisionStrategy.java`

#### 核心概念

這是一個**策略框架**，不直接產生交易信號，而是：
1. 整合多個子策略（如 SignalRSIStrategy）
2. 收集所有子策略的信號
3. 通過 DecisionEngine 進行多層級決策
4. 執行最終的交易動作

#### 運作流程

```
每根 K 線觸發時：
1. 讓所有子策略更新信號 (onBar)
2. 收集所有策略的信號
3. 傳給 DecisionEngine 進行決策：
   a. 無持倉 → 檢查進場條件（週線環境、日線趨勢、策略投票、風險檢查）
   b. 有持倉 → 檢查出場條件（風險違規、停損停利、策略投票）
4. 根據決策結果執行交易：
   - OPEN_LONG → buyWithStops()
   - CLOSE_POSITION → sellWithReason()
   - HOLD/NO_ACTION → 不動作
```

#### 預設配置

- **主要週期**: M5 (5 分鐘)
- **週線環境檢測**: 啟用
- **日線趨勢分析**: 啟用
- **進場閾值**: 0.6 (做多/做空)
- **出場閾值**: 0.5
- **日損限制**: 3%
- **最大倉位**: 30%

#### 實際交易邏輯

**進場條件** (需同時滿足):
1. 週線環境適合交易 (ADX > 20, 波動率合理)
2. 日線趨勢明確
3. 策略投票分數 >= 進場閾值 (0.6)
4. 風險檢查通過 (未超過日損限制、倉位限制)

**出場條件** (滿足任一):
1. 風險違規 (超過日損限制)
2. 觸發停損或停利
3. 策略出場投票分數 >= 出場閾值 (0.5)

---

### 2. SignalRSIStrategy（RSI 信號策略）

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/decision/strategies/SignalRSIStrategy.java`

#### 核心概念

這是一個**信號生成器**，不執行交易，只產生信號供 DecisionEngine 投票使用。

#### 信號生成邏輯

```java
if (RSI >= 70) {
    信號: SHORT (看空)
    信心度: 計算公式 = (RSI - 70) / (100 - 70)
    理由: "RSI超買"
}
else if (RSI <= 30) {
    信號: LONG (看多)
    信心度: 計算公式 = (30 - RSI) / 30
    理由: "RSI超賣"
}
else if (RSI > 50) {
    信號: HOLD
    信心度: 0.3
    理由: "RSI偏強"
}
else if (RSI < 50) {
    信號: HOLD
    信心度: 0.3
    理由: "RSI偏弱"
}
else {
    信號: NO_TRADE
    信心度: 0.0
    理由: "RSI中性"
}
```

#### 預設參數

- **RSI 週期**: 14
- **超買閾值**: 70
- **超賣閾值**: 30
- **權重**: 1.0

---

### 3. DayTradingStrategy（當沖交易策略）

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/decision/strategies/DayTradingStrategy.java`

#### 核心概念

繼承自 MultiTimeframeDecisionStrategy，專門設計給當日沖銷使用。

#### 特點

- ✅ **快進快出**：低進場門檻 (0.3)，容易進出
- ✅ **當日平倉**：強制收盤前平倉
- ✅ **嚴格風控**：1% 日內止損
- ✅ **短週期指標**：使用 5 分鐘 K 線，RSI 週期僅 5
- ❌ **不看大趨勢**：關閉週線環境、日線趨勢檢測

#### 配置參數

| 參數 | 值 | 說明 |
|------|-----|------|
| 主要週期 | M5 (5分鐘) | 可調整為 M1 |
| 風控週期 | M1 (1分鐘) | 快速監控 |
| 週線環境檢測 | ❌ 關閉 | 當沖不看大趨勢 |
| 日線趨勢分析 | ❌ 關閉 | 當沖不看大趨勢 |
| 進場閾值 | 0.3 | 低門檻，容易進場 |
| 出場閾值 | 0.3 | 容易出場 |
| 日損限制 | 1% | 嚴格風控 |
| 最大倉位 | 20% | 單筆最多 20% |
| 強制當日平倉 | ✅ 是 | 收盤前平倉 |
| 最大持倉時間 | 78 根 K 線 | 約 6.5 小時 |

#### 子策略配置

**RSI 策略** (快速版):
- RSI 週期: 5 (非常短)
- 超賣閾值: 40 (較寬鬆，容易觸發)
- 超買閾值: 60 (較寬鬆，容易觸發)
- 權重: 1.0

#### 適用場景

- 盤中波動交易
- 快速獲利了結
- 不想留倉過夜的投資人
- 資金較小，追求高週轉率

---

### 4. SwingTradingStrategy（短線交易策略）

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/decision/strategies/SwingTradingStrategy.java`

#### 核心概念

繼承自 MultiTimeframeDecisionStrategy，適合持倉 1-5 天的短線波段交易。

#### 特點

- ✅ **跟隨日線趨勢**：啟用日線趨勢分析
- ✅ **中等風險報酬比**：3% 日損限制，目標 6% 獲利
- ✅ **允許留倉過夜**：不強制當日平倉
- ✅ **標準週期指標**：使用 15 分鐘 K 線，RSI 週期 14
- ❌ **不看週線環境**：關注短期機會

#### 配置參數

| 參數 | 值 | 說明 |
|------|-----|------|
| 主要週期 | M15 (15分鐘) | 短線波段週期 |
| 風控週期 | M5 (5分鐘) | 中等監控頻率 |
| 週線環境檢測 | ❌ 關閉 | 不看週線 |
| 日線趨勢分析 | ✅ 啟用 | 跟隨日線趨勢 |
| 進場閾值 | 0.5 | 中等門檻 |
| 出場閾值 | 0.5 | 平衡進出 |
| 日損限制 | 3% | 中等風控 |
| 最大倉位 | 30% | 單筆最多 30% |
| 強制當日平倉 | ❌ 否 | 允許留倉 |
| 最大持倉時間 | 288 根 K 線 | 約 3 天 |

#### 子策略配置

**RSI 策略** (標準版):
- RSI 週期: 14 (標準)
- 超賣閾值: 30 (標準)
- 超買閾值: 70 (標準)
- 權重: 1.0

#### 適用場景

- 日內波段交易
- 跟隨明確的短期趨勢
- 有時間盯盤但不想太頻繁交易
- 追求中等風險報酬比

---

### 5. PositionTradingStrategy（波段交易策略）

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/decision/strategies/PositionTradingStrategy.java`

#### 核心概念

繼承自 MultiTimeframeDecisionStrategy，適合持倉數週到數月的長線波段交易。

#### 特點 (預期設計)

- ✅ **看重大趨勢**：啟用週線環境、日線趨勢檢測
- ✅ **較寬鬆風控**：5% 日損限制，目標 15% 獲利
- ✅ **長期持倉**：最多持倉 30 天
- ✅ **長週期指標**：使用 1 小時 K 線，RSI 週期 21
- ✅ **高勝率為先**：進場閾值 0.7 (保守)

#### 配置參數 (預期)

| 參數 | 值 | 說明 |
|------|-----|------|
| 主要週期 | H1 (1小時) | 長線波段週期 |
| 風控週期 | M15 (15分鐘) | 較低監控頻率 |
| 週線環境檢測 | ✅ 啟用 | 看週線環境 |
| 日線趨勢分析 | ✅ 啟用 | 跟隨日線趨勢 |
| 進場閾值 | 0.7 | 高門檻，保守 |
| 出場閾值 | 0.4 | 不輕易出場 |
| 日損限制 | 5% | 較寬鬆風控 |
| 最大倉位 | 50% | 單筆最多 50% |
| 強制當日平倉 | ❌ 否 | 允許長期持倉 |
| 最大持倉時間 | 720 根 K 線 | 約 30 天 |

#### 子策略配置 (預期)

**RSI 策略** (慢速版):
- RSI 週期: 21 (較長)
- 超賣閾值: 30
- 超買閾值: 70
- 權重: 1.0

**MACD 策略** (可添加):
- 快線週期: 12
- 慢線週期: 26
- 信號線週期: 9

---

### 6. 傳統獨立策略

這些策略是獨立運作的，不依賴 DecisionEngine。

#### 6.1 RSIStrategy

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/backtest/strategies/RSIStrategy.java`

**交易邏輯**:
```
買入條件：RSI <= 30 (超賣)
賣出條件：
  - RSI >= 70 (超買)
  - 或 獲利 >= 3%
  - 或 虧損 >= -3%
```

#### 6.2 SimpleMovingAverageStrategy

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/backtest/strategies/SimpleMovingAverageStrategy.java`

**交易邏輯**:
```
買入條件：
  - 短期 MA 上穿長期 MA (金叉)
  - 或 短期 MA 在長期 MA 上方且距離擴大 > 0.5%

賣出條件：
  - 短期 MA 下穿長期 MA (死叉)
  - 或 短期 MA 在長期 MA 下方且距離擴大 > 0.5%
```

#### 6.3 MACDStrategy

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/backtest/strategies/MACDStrategy.java`

**交易邏輯**:
```
買入條件：
  - MACD 線上穿信號線 (金叉)
  - 或 MACD 在信號線上方且柱狀圖擴大

賣出條件：
  - MACD 線下穿信號線 (死叉)
  - 或 獲利 >= 4.5% 且柱狀圖縮小
```

#### 6.4 BollingerBandsStrategy

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/backtest/strategies/BollingerBandsStrategy.java`

**交易邏輯** (均值回歸模式):
```
買入條件：
  - 價格觸及下軌 (position < 0.1)
  - 或 價格接近下軌 (position < 0.25)

賣出條件：
  - 價格觸及上軌 (position > 0.9)
  - 或 價格回歸中軌 (position > 0.5 且之前在下半部)
```

---

## 🔧 停損停利機制

### AdvancedStopLossManager

**檔案位置**: `src/main/java/com/dreamhouse/trading/core/backtest/AdvancedStopLossManager.java`

#### 支援的停損類型

1. **固定停損/停利**
   - 預設：5% 停損，10% 停利
   - 開倉時計算，持倉期間固定

2. **移動止損** (Trailing Stop)
   - 啟動條件：獲利 >= 5%
   - 移動幅度：最高價的 3% 下方
   - 只升不降，保護獲利

3. **時間止損**
   - 預設：最多持倉 72 小時
   - 超時強制平倉

4. **波動率調整停損**
   - 使用 ATR 指標計算
   - 停損 = 進場價 - (ATR × 2)
   - 停利 = 進場價 + (ATR × 4)

5. **技術指標停損**
   - 跌破 SMA(20) 停損
   - 適用於趨勢跟隨策略

#### 檢查優先順序

```
1. 固定停損 (如果移動止損未啟動)
2. 固定停利
3. 移動止損 (如果已啟動)
4. 時間止損
5. 技術指標停損
```

#### **重要修復** (2025-11-12)

在之前的版本中，`DecisionEngine.onPositionOpened` 只記錄停損價格，但沒有實際設定到 `AdvancedStopLossManager`，導致停損檢查被跳過。

**修復內容**:
1. 添加 `AdvancedStopLossManager.setPositionStop()` 方法
2. 在 `DecisionEngine.onPositionOpened()` 中調用此方法實際設定停損

---

## 📈 DecisionEngine 決策流程

### 無持倉時

```
1. 檢查風險違規 (日損限制)
   ↓ 通過
2. 週線環境檢測 (如果啟用)
   ↓ 適合交易
3. 日線趨勢分析 (如果啟用)
   ↓ 趨勢明確
4. 決定允許的交易方向 (週線 ∩ 日線)
   ↓
5. 多策略投票
   ↓ 進場分數 >= 閾值
6. 風險檢查 (倉位大小、資金充足)
   ↓ 通過
7. 產生開倉決策 (OPEN_LONG/OPEN_SHORT)
```

### 有持倉時

```
1. 檢查風險違規 (日損限制)
   ↓ 違規 → 強制平倉
2. 檢查停損停利
   ↓ 觸發 → 平倉
3. 策略出場投票
   ↓ 出場分數 >= 閾值 → 平倉
4. 繼續持有 (HOLD)
```

---

## 🎯 策略選擇指南

| 策略類型 | 適合對象 | 優點 | 缺點 |
|---------|---------|------|------|
| **當沖交易** | 日內交易者 | 風險可控、無隔夜風險 | 需頻繁盯盤、手續費高 |
| **短線交易** | 短波段交易者 | 平衡風險報酬、交易機會多 | 需定期盯盤 |
| **波段交易** | 長線投資者 | 交易頻率低、適合上班族 | 需承受較大回檔 |
| **傳統策略** | 回測驗證 | 邏輯簡單、易理解 | 無多週期過濾、較多假信號 |

---

## 🚀 未來優化方向

1. **完善 PositionTradingStrategy** 的實作
2. **添加 SignalMACDStrategy** 和 **SignalMAStrategy**
3. **實作多風格策略管理器** (MultiStyleStrategyManager)
4. **添加更多技術指標**：KD、BOLL、VWAP 等
5. **優化投票權重分配**：動態調整策略權重
6. **添加機器學習模型**：預測市場走勢
7. **實作組合優化**：多策略組合的最佳化

---

**文檔版本**: 1.0
**最後更新**: 2025-11-12
**維護者**: Claude Code
