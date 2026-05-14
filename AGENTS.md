# DreamHouseTrading AI Collaboration Guide

最後更新：2026-05-13

## 1. 專案概述

- 專案：`DreamHouseTrading`
- 類型：Java Swing 桌面交易分析平台
- 語言：Java 17
- 建構：Maven
- 邊界：分析 / 掃描 / 決策 / 回測 / 模擬執行，不做真實券商下單

## 2. 目前核心流程

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

## 3. 關鍵規則

### 3.1 編碼與變更原則

- 優先修改既有檔案
- 只有在模型或模組邊界真的需要時才新增檔案
- 避免建立新的進度文件或臨時說明文件
- 改動後要先確保編譯，再看是否需要更新文檔

### 3.2 Git / repo 原則

- GitHub 只保留一個正式 repo
- `main` 是唯一長期主線
- 新工作可在 `codex/*` 分支進行，但不要把多份平行文檔再推回主線

### 3.3 決策與執行語意

- `DecisionResult` 是唯一標準化決策輸出
- 新的執行邏輯應優先使用 `ExecutionEngine.executeDecision(...)`
- Scanner 樣式輸出應對齊 `MarketScanResult`
- 不要在 UI 端重做交易語意、風控語意或訂單語意

### 3.4 風控與產品限制

- v1 維持 `long-only`
- 不要默默加入放空可執行路徑
- 進場檢查必須經過 `RiskManager`
- `RiskManager` 目前至少應負責：
  - account risk
  - position sizing
  - minimum risk/reward
  - volatility range

### 3.5 MarketDataCollector / FinMind 邊界

- 盤中雷達與盤中 K 線優先使用 `MarketDataCollectorFeed` 讀本機 `market_data` MySQL。
- DreamHouseTrading 盤中雷達不得 fallback 呼叫 FinMind 即時行情；FinMind 只保留低頻資料、盤後資料、新聞、分點與手動 API 查詢。
- `MarketDataCollectorFeed` 的 stale guard 是防止壞資料掃描，不是 UI 清空機制。UI 載入歷史 K 線時，不應因單一 symbol stale 直接無資料。
- 台股 `13:25~13:30` 是收盤集合競價，可能沒有連續 tick；這段時間 collector latest tick 超過 stale threshold 是正常現象，不可視為 Collector 故障。
- `13:25~13:30` 仍要允許載入 09:00 起已收集的盤中 K 線，且不得因此改打 FinMind。
- 台股分 K `TaiwanStockKBar` 是盤後資料，不可用來補開盤中的即時分 K；15:50 前不得把它當作盤中即時 K 線來源。
- Watchlist 的成交量若來自 MarketDataCollector SQL tick，顯示最新 tick 原始 volume，不要在 UI 端累加歷史載入產生的量。

### 3.6 當沖模擬交易語意

- 台股當沖模擬開單必須以一張為單位；`DAY_TRADE` 自動開倉數量固定 `1000` 股。
- 若現金不足以買一張，應走既有拒單流程，不拆零股、不自動降數量。
- 盤中自動監控與今日機會雷達執行語意必須強制為 `DAY_TRADE`，不可讓分類器輸出的 `SWING_TRADE` 直接進入自動開倉路徑。
- `13:25` 後禁止所有自動監控 `OPEN_LONG`，並平掉所有自動監控產生的未平倉部位，不只依賴 `tradeMode == DAY_TRADE`。
- 當沖平倉應以最新可用本地行情價格執行；若缺少價格，不得靜默略過，必須在 UI/記錄中留下原因。
- 自動監控開出的部位需要可辨識為 auto-managed，13:25 強制平倉、停損冷卻與最大持倉限制都以此語意為準。
- 自動交易記錄必須寫入 `logs/paper-trades`，包含 entry、exit、reason、mode、quantity、PnL、setup score，方便盤後分析；盤後 UI 應能匯入 `completed_trades_*.csv`、`orders_*.csv`、`trade_setups_*.csv` 並串接完整生命週期。

### 3.7 盤中風控與策略優化規則

- 最大同時持倉數必須在自動開倉路徑實際生效，不能只停留在設定或策略評分中。
- 當沖建議最大同時持倉先限制在 3 到 5 檔，每檔固定一張，避免 40+ 檔分散持倉。
- 09:00~09:10 預設只收資料不自動開倉，避免開盤前幾分鐘資料不完整與價格跳動造成連續停損；時間需可由監控設定 UI 調整。
- 同一股票停損後預設冷卻 60 分鐘，冷卻期間不得由自動監控重進；冷卻開關與分鐘數需可由監控設定 UI 調整。
- `setup_score` 不能被視為勝率保證；若盤後紀錄顯示高分區間表現較差，必須重新檢查權重來源。
- RSI 超賣不能單獨作為開多理由；若要用 RSI 超賣進場，必須至少有 EMA 未明顯下彎或放量反轉其中一項確認。
- 任何策略優化都應先用 `completed_trades_*.csv`、`orders_*.csv`、`trade_setups_*.csv` 做盤後驗證，再調整實盤模擬參數。

### 3.8 UI / 圖表效能規則

- 使用 `MarketDataCollectorFeed` 切換股票時，K 線圖應批次載入 `Bar`，不要把每根 bar 拆成多筆 tick 推給 UI。
- 圖表載入速度與 K 棒寬度是兩件事；恢復較寬 K 棒應調整 renderer 寬度策略，不要回退到逐 tick 載入。
- 盤中日內 K 線預設載入今天 09:00~13:30 的資料；跨日需求應由明確設定控制。
- FinMind API 面板、觀察清單、雷達、執行狀態等表格 UI 在 resize 時要避免欄位遮蔽文字，欄位與字體縮放應保持一致。

### 3.9 當沖策略 v2 市場脈絡規則

- 自動監控開多前應先建立 `MarketContextSnapshot`，用本地 SQL 的 `TAIEX`、`TPEx` 與觀察清單 K 線判斷 `TREND_UP / RANGE / WEAK / DATA_MISSING`。
- 盤中即時大盤、個股、VWAP、量能與族群強度一律讀 `MarketDataCollectorFeed` / `market_data` SQL，不得由 DreamHouseTrading 直接呼叫 FinMind 即時 API。
- 弱勢盤不是完全禁止交易，但 `OPEN_LONG` 必須同時符合：站上自身 VWAP、VWAP 斜率向上、強於對應大盤至少 0.3%、強於所屬族群至少 0.2%。
- 震盪盤應提高門檻，至少要求 VWAP 結構與量能延續品質；趨勢偏多盤才允許標準 B 組條件。
- 族群映射使用 FinMind `TaiwanStockIndustryChain` 低頻匯入 SQL 快取，盤中 scanner 只讀快取，不在掃描時打 API。
- ATR 停損停利屬於核心 scanner / execution 語意，不應只在 UI 顯示；回測與雷達理由要保留 ATR、VWAP、Regime、Industry、Volume Sustain 的判斷資料。

## 4. 主要檔案

- [README.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\README.md)
- [PROJECT_DOCUMENTATION.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\PROJECT_DOCUMENTATION.md)
- [pom.xml](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\pom.xml)

核心程式區：

- `src/main/java/com/dreamhouse/trading/core/decision`
- `src/main/java/com/dreamhouse/trading/core/execution`
- `src/main/java/com/dreamhouse/trading/core/backtest`
- `src/main/java/com/dreamhouse/trading/ui`

## 5. 驗證命令

編譯：

```bash
mvn -q -DskipTests compile
```

測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

注意：

- 這台機器上的預設 `mvn test` 會被 JaCoCo coverage 檔權限問題卡住
- 若只是確認功能，先用 `-Djacoco.skip=true`

## 6. 文檔規則

- `README.md`：對外簡介與啟動方式
- `PROJECT_DOCUMENTATION.md`：技術現況、模型、驗證證據
- `AGENTS.md`：AI 協作規範

不要再新增：

- `*_progress.md`
- `*_updated.md`
- `*_plan.md`
- 重複說明文件

## 7. 後續工作優先序

1. 讓盤中自動監控的交易語意明確強制為當沖或明確禁止當沖，避免 `SWING_TRADE` 語意漂移。
2. 將 13:25 後禁止所有自動開倉、平掉所有自動監控持倉的規則落到核心執行路徑。
3. 把最大同時持倉、每股停損冷卻、09:10 前不開倉接入自動開倉流程。
4. 補 `TradeRecord` 的完整交易生命週期語意。
5. 收斂 UI 端的分類/建議邏輯到核心服務。
6. 補更完整的 scanner -> decision -> execution -> report 整合驗證。
