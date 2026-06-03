# DreamHouseTrading 專案脈絡

最後更新：2026-06-03

DreamHouseTrading 是 Java 17 / Maven / Swing 桌面交易分析平台，用於分析、行情掃描、決策輔助、回測、重播與模擬執行。本專案不得執行真實券商下單。

## 1. 技術棧

- 語言：Java 17
- 建構：Maven
- UI：Swing、FlatLaf、JFreeChart、Modern Docking
- 測試：JUnit 5、Mockito、AssertJ、Surefire、JaCoCo
- 主要本機行情資料：MySQL `market_data`

## 2. 產品邊界

系統可以：

- 載入行情資料。
- 產生訊號。
- 建立標準化決策。
- 套用風控檢查。
- 模擬執行。
- 回測與重播。
- 匯出報告與 paper-trade records。

系統不得：

- 送出真實券商訂單。
- 未經明確未來 scope 核准，就新增可執行放空路徑。
- 讓 UI state 成為核心交易事實來源。
- 使用 FinMind 作為盤中即時 fallback source。

## 3. 核心流程

1. Market Data
2. Signal Generation
3. Decision & Risk
4. Execution Simulation
5. Backtest & Reporting

重要核心輸出與服務：

- `MarketScanResult`
- `DecisionResult`
- `RiskManager`
- `ExecutionEngine`
- `PaperTradeRecorder`

## 4. 資料源邊界

盤中雷達與盤中 K 棒：
- 優先使用 `MarketDataCollectorFeed` 讀取本機 SQL `market_data`。
- 不得 fallback 到 FinMind 即時報價。

FinMind：
- 低頻資料。
- 盤後資料。
- 新聞。
- 券商 / 分點資料。
- 手動 API 查詢流程。

Yahoo / 其他 feeds：
- 只在已文件化的 secondary 或 fallback market-data workflows 使用。

## 5. 交易規則

目前 v1 當沖自動化維持 long-only。

關鍵規則：

- Auto monitor 與今日機會雷達強制為 `DAY_TRADE`。
- 自動 `DAY_TRADE` 下單數量固定 1000 股。
- 現金不足時拒單；不得降成零股數量。
- 13:25 後禁止 auto-monitor `OPEN_LONG`。
- 13:25 起 auto-managed positions 強制平倉。
- Manual positions 不得被當成 auto-managed。
- RSI 超賣不能單獨作為開多理由。
- `SignalRSI = SHORT` 會阻擋 auto-monitor `OPEN_LONG`。
- Close <= VWAP、VWAP slope <= 0、Volume Sustain 失敗、ATR 追價過高，應作為硬阻擋候選或明確阻擋原因。

## 6. Backtest / Replay 規則

- 必須避免 lookahead bias。
- 第 N 根已完成 K 棒產生的訊號，只能在第 N+1 根 open 或下一筆可見 tick 成交。
- 同一根 K 棒同時碰到停利與停損時，保守先算停損。
- replay 若使用 ticks，必須只聚合 replay time 當下可見 ticks。
- candles-only 模式只能使用已收完 K 棒。
- 跨日暖機可初始化指標，但不得污染指定日期績效。
- backtest / replay 報告必須保留 timeframe 與 source diagnostics。

## 7. AI 協作模式

建議模式：GPT + Codex 半自動協作流程。

- GPT：需求拆解、規格、測試策略、code review。
- Codex：讀取 repo、修改 code / docs / tests、準備 diff 或 PR。
- 使用者：最終確認、合併與上線判斷。

本專案目前不適合高度自動合併，因為交易語意密度高，UI 與核心流程仍有交錯，且回歸風險需要人工審查。

## 8. 本機狀態

不得提交：

- `logs/`
- `data/`
- `config/ui-layout.xml`
- diagnostic CSV
- paper trade CSV
- `datasource.properties` 等本機 credentials

如果這些檔案已被追蹤，做 PR 決策前必須先回報。
