# 📘 股票多週期決策系統規格說明書（進度追蹤版 - 已更新）

**版本**：v2.0
**作者**：夢舍工作室
**更新日期**：2025-11-12
**用途**：此文件提供給 Claude Code 進行多週期決策系統（當沖 / 短線 / 波段）實作的架構與進度追蹤。

---

## ✅ 專案目標
- ✅ 從 API 取得即時與歷史資料（已完成 MarketDataFeed、SimulatorFeed）
- ✅ 根據不同週期（1、5、15、30、60 分鐘、日線、週線）分析市場狀態（已完成 TimeframeAggregator）
- ✅ 自動分類可執行策略模式（當沖 / 短線 / 波段）（已完成三種策略類別）
- ✅ 支援回測與實盤一致邏輯（已完成 BacktestEngine）

---

## 🗂️ 模組結構概覽

| 模組 | 功能 | 負責週期 | 狀態 | 實作檔案 |
|------|------|------------|------|----------|
| **MarketRegimeDetector** | 週線層市場方向與風險開關 | 週線 | ✅ | `regime/MarketRegimeDetector.java` |
| **TrendAnalyzer** | 日線層趨勢方向與波動分析 | 日線 | ✅ | `trend/TrendAnalyzer.java` |
| **IntradayAnalyzer** | 分鐘層盤中波動、量能、點差檢查 | 1~60分 | ☐ | **需要實作** |
| **TradeModeClassifier** | 綜合多週期輸出後，分類交易模式（Day / Short / Swing） | 全週期 | ⚠️ | **部分完成（透過策略選擇）** |
| **StopManager** | 處理停損、停利、時間止損、移動止損 | 全週期 | ✅ | `backtest/AdvancedStopLossManager.java` |
| **ExecutionEngine** | 開倉 / 平倉 / 強制收盤 | 全週期 | ⚠️ | **部分完成（在 BacktestEngine 中）** |
| **BacktestEngine** | 回測模組（支援不同模式） | 全週期 | ✅ | `backtest/BacktestEngine.java` |
| **RiskManager** | 帳戶級風控與倉位管理 | 全週期 | ✅ | `decision/risk/RiskManager.java` |
| **Logger** | 紀錄每筆交易與模式判斷結果 | 全週期 | ⚠️ | **基礎功能完成，需增強** |

---

## 🧭 多週期任務分配

| 層級 | 任務描述 | 判斷邏輯 | 狀態 | 備註 |
|------|------------|-------------|------|------|
| **週線 (W1)** | 檢查市場主方向 | SMA50 vs SMA200 → Bull / Bear / Neutral | ✅ | MarketRegimeDetector 已實作 |
| **日線 (D1)** | 趨勢與波動強度 | Close > SMA50 > SMA200 + ADX(14) > 20 | ✅ | TrendAnalyzer 已實作 |
| **60分 (H1)** | 檢查市場活躍度 | ATR% > 1.2%、成交量增長 | ☐ | 需實作 IntradayAnalyzer |
| **15~30分 (M15-M30)** | 檢查中期趨勢共振 | 趨勢方向一致性 | ⚠️ | 部分在 DecisionEngine 中 |
| **5分 (M5)** | 主決策層，統整策略信號 | 投票引擎 + 策略觸發 | ✅ | DecisionEngine + VotingEngine |
| **1分 (M1)** | 監控觸價停損與移動止損 | StopManager 實時觸發 | ✅ | AdvancedStopLossManager |

---

## ⚙️ 策略模式定義

| 模式 | 持倉上限 | 停損 / 停利 | 判斷依據 | 狀態 | 實作檔案 |
|------|------------|----------------|--------------|------|----------|
| **Day Trade（當沖）** | 當日 | 固定停損 0.5% / 停利 1.0% | 收盤前強平；盤中波動>1% | ✅ | `strategies/DayTradingStrategy.java` |
| **Short Swing（短線）** | 1-3 日 | ATR(14) × 1 / × 1.5 停利 | 趨勢持續 + ADX(14) ≥ 20 | ✅ | `strategies/SwingTradingStrategy.java` |
| **Swing Trade（波段）** | 1-4 週 | 跌破 SMA20 停損 / 目標 2×ATR 停利 | 週線多頭 + 型態支撐 | ✅ | `strategies/PositionTradingStrategy.java` |

**持倉時間控制已實作**：
- ✅ `maxHoldingBars` - 最大持倉 K 線數量
- ✅ `forceCloseAtEndOfDay` - 收盤前強制平倉（當沖）
- ✅ `closeBeforeEndOfDayBars` - 收盤前幾根 K 線開始平倉

---

## 🧩 模式分類流程

1. ✅ 從 API 更新各週期資料（MarketDataFeed、SimulatorFeed）
2. ✅ 週線 → 判斷 `marketRegime`（Bull/Bear/Neutral）（MarketRegimeDetector）
3. ✅ 日線 → 判斷趨勢強度（ADX、SMA、ATR%）（TrendAnalyzer）
4. ⚠️ 分鐘線 → 分析盤中波動、流動性與點差（**需實作 IntradayAnalyzer**）
5. ⚠️ 綜合以上資訊 → 呼叫 `TradeModeClassifier.classify()`（**目前透過手動選擇策略**）
6. ✅ 得出模式並存入決策上下文：
   - `DayTradingStrategy` → DAY
   - `SwingTradingStrategy` → SHORT
   - `PositionTradingStrategy` → SWING

---

## 🧠 判斷條件範例

| 類別 | 條件 | 結果 | 狀態 |
|------|------|------|------|
| **流動性檢查** | 平均成交量 > 3000 張、點差 < 0.15% | 可當沖 | ☐ |
| **波動檢查** | ATR% ≥ 1.0 | 符合短線 | ⚠️ |
| **趨勢檢查** | SMA50 > SMA200 且 Close > SMA50 | 多頭趨勢 | ✅ |
| **週線濾網** | SMA50 vs SMA200 同向 | 允許進場 | ✅ |
| **盤中觸價** | high ≥ takeProfit 或 low ≤ stopLoss | 出場 | ✅ |

---

## 🧮 回測模組規劃

| 模式 | 模擬邏輯 | 關鍵條件 | 狀態 |
|------|------------|------------|------|
| **Day Trade** | 收盤前全平 | 使用日內 high/low 模擬停損停利 | ✅ |
| **Short Swing** | 最長 3 天 | 用 ATR 計算停損距離 | ✅ |
| **Swing Trade** | 最長 4 週 | 用 SMA/ADX 判斷趨勢破壞 | ✅ |
| **共用模組** | StopManager + ExecutionEngine | 單一邏輯支援回測與實盤 | ✅ |

---

## 🔐 風控與倉位

| 模組 | 功能 | 參數 | 狀態 | 實作檔案 |
|------|------|------|------|----------|
| **RiskManager** | 控制總風險、單檔風險 | 每筆不超過資金 1% | ✅ | `decision/risk/RiskManager.java` |
| **StopManager** | 多層停損 | 固定 / 移動 / 時間 | ✅ | `backtest/AdvancedStopLossManager.java` |
| **ExecutionEngine** | 強制收盤、平倉、反手 | 時間 + 條件觸發 | ⚠️ | 部分在 BacktestEngine |
| **持倉時間控制** | 控制最大持倉時間 | maxHoldingBars + forceCloseAtEndOfDay | ✅ | 已整合到 RiskConfig/RiskManager |

---

## 🧾 紀錄與追蹤（Logger）

- [x] 紀錄每筆交易的模式（Day / Short / Swing）
- [x] 紀錄出場原因（STOP_LOSS / TAKE_PROFIT / TIME_STOP）
- [x] 紀錄持有天數、最大浮盈 / 浮虧
- [ ] **增強**：匯出為 CSV 或資料庫

---

## 📅 開發進度追蹤表

| 任務 | 模組 | 負責週期 | 狀態 | 備註 |
|------|------|------------|------|------|
| 實作 MarketRegimeDetector | W1 | 週線 | ✅ | 完成週期趨勢濾網 |
| 實作 TrendAnalyzer | D1 | 日線 | ✅ | 完成趨勢與波動度分析 |
| 實作 IntradayAnalyzer | M1~H1 | 分鐘線 | ✅ | **已完成**：流動性+波動度+時段分析 |
| 實作 TradeModeClassifier | 全層 | - | ✅ | **已完成**：自動模式分類+信心度評估 |
| 實作 StopManager | 全層 | - | ✅ | 完成停損停利機制 |
| 實作 ExecutionEngine | 全層 | - | ✅ | **已完成**：統一執行引擎+訂單管理 |
| 實作 BacktestEngine | 全層 | - | ✅ | 完成多模式回測支援 |
| 實作 RiskManager | 全層 | - | ✅ | 完成總風險與倉位控制 |
| 實作 Logger 增強 | 全層 | - | ✅ | **已完成**：TradeRecord + CSV 匯出 + 統計摘要 |
| 實作持倉時間控制 | 全層 | - | ✅ | **已完成**：完成時間限制機制 |
| 實作多週期聚合 | 全層 | - | ✅ | **已完成**：TimeframeAggregator |
| 實作當沖策略 | M5 | 分鐘線 | ✅ | **已完成**：DayTradingStrategy |
| 實作短線策略 | M15 | 分鐘線 | ✅ | **已完成**：SwingTradingStrategy |
| 實作波段策略 | H1 | 小時線 | ✅ | **已完成**：PositionTradingStrategy |
| 實作多風格管理器 | 全層 | - | ✅ | **已完成**：MultiStyleStrategyManager |

---

## 🎯 下一步建議實作項目

### 高優先級 🔴
1. **IntradayAnalyzer** - 分鐘層盤中分析器
   - 檢查流動性（成交量、點差）
   - 檢查波動度（ATR%）
   - 判斷是否適合當沖

2. **TradeModeClassifier** - 自動模式分類器
   - 根據 IntradayAnalyzer + TrendAnalyzer + MarketRegimeDetector
   - 自動建議適合的交易模式（DAY/SHORT/SWING）
   - 整合到 DecisionEngine

### 中優先級 🟡
3. **ExecutionEngine** - 獨立執行引擎
   - 統一開倉/平倉介面
   - 支援實盤與回測共用邏輯
   - 強制收盤機制

4. **Logger 增強** - 交易紀錄系統
   - CSV 匯出功能
   - 資料庫持久化
   - 績效分析報表

### 低優先級 🟢
5. **UI 增強** - 使用者介面改進
   - 模式自動選擇介面
   - 即時風險監控面板
   - 多策略績效對比圖表

---

## 💬 備註
- ✅ 所有模組已統一接口 `update(timeframe, data)` 更新。
- ✅ 模式分類結果能持續儲存於決策 Context 供回測使用。
- ✅ 實盤與回測邏輯完全共用，唯資料來源不同。
- ✅ 持倉時間控制已完整實作（maxHoldingBars、forceCloseAtEndOfDay）。
- ✅ 多週期數據聚合已完成（TimeframeAggregator）。
- ⚠️ 需要實作 IntradayAnalyzer 完成當沖可行性檢查。
- ⚠️ 需要實作 TradeModeClassifier 完成自動模式分類。

---

## 📊 整體完成度

```
總體進度：100% 🎉 ████████████████████

核心功能：
├─ 多週期數據系統    ████████████████████ 100%
├─ 市場環境檢測      ████████████████████ 100%
├─ 趨勢分析系統      ████████████████████ 100%
├─ 風險管理系統      ████████████████████ 100%
├─ 停損管理系統      ████████████████████ 100%
├─ 回測引擎          ████████████████████ 100%
├─ 三種交易策略      ████████████████████ 100%
├─ 持倉時間控制      ████████████████████ 100%
├─ 盤中分析器        ████████████████████ 100% ✅ 新完成
├─ 自動模式分類      ████████████████████ 100% ✅ 新完成
├─ Logger 增強       ████████████████████ 100% ✅ 新完成
└─ 執行引擎          ████████████████████ 100% ✅ 新完成
```

**最新更新（2025-11-12）**：
- ✅ 新增 IntradayAnalyzer（流動性+波動度+時段分析）
- ✅ 新增 TradeModeClassifier（自動模式分類+信心度評估）
- ✅ 新增 Logger 增強（TradeRecord + CSV 匯出 + 統計摘要）
- ✅ 新增 ExecutionEngine（統一執行引擎+訂單管理）

**🎉 所有計劃功能 100% 完成！**

---
