# DreamHouseTrading 模組責任分工

最後更新：2026-06-03

本文件說明各模組責任與 Codex 修改規則。這不是人員 ownership 對照表，而是語意責任邊界表。

| 模組 / package | 責任 | 主要風險 | Codex 修改範圍 | 必要檢查 |
|---|---|---|---|---|
| `src/main/java/com/dreamhouse/trading/ui` | Swing UI、docking panels、dialogs、使用者操作 | UI 可能意外成為交易語意來源 | 允許小範圍 UI 修改 | 不得重做風控 / 訂單語意。檢查 config、scanner request、文件與手動 UI 行為。 |
| `ui/MainFrameWithDocking.java` | 主程式流程與大量整合入口 | class 很大，容易混入 UI 與核心行為 | 只允許小範圍精準修改 | 優先使用核心服務。驗證功能鏈路，避免無關重構。 |
| `core/scanner` | 雷達掃描、訊號彙整、硬阻擋、分數組件 | 進場邏輯、阻擋原因、分數報告 | 允許，但需審查 | 透過 `MarketScannerService.scan(...)` 測試；語意改變時更新 CSV / report / docs。 |
| `core/decision` | 決策引擎、分類器、投票、策略訊號 | `DecisionResult` 一致性與 long-only 邊界 | 允許，但需審查 | 保留標準化決策輸出，避免可執行 short path。 |
| `core/decision/risk` | 風控設定、風控閘門、違規原因 | 現金、時間規則、冷卻、熔斷 | 嚴格審查 | 新增 allow / block 測試，並同步 auto-monitor、replay、backtest 語意。 |
| `core/execution` | Paper execution、orders、execution results | 模擬訂單可能偏離 decision / risk | 嚴格審查 | 使用 `ExecutionEngine.executeDecision(...)`；保留 paper-trade 欄位與 v1 long-only。 |
| `core/backtest` | 回測引擎、replay、reports、portfolio / trades | Lookahead、成交價、成本、全局限制 | 嚴格審查 | 測試 N+1 open、next tick、stop-loss-first、成本與 replay parity。 |
| `core/monitor` | Auto monitor config / service / templates / gates | 排程、template persistence、交易節奏、cutoff | 允許，但需審查 | 測試 A/B/C 保護、自訂 round-trip、`DecisionSource`、auto-managed flag、cadence rules。 |
| `core/logging` | Paper trade 與 CSV logging | CSV schema、Excel BOM、來源可追溯性 | 允許，但需審查 | 驗證 BOM、欄位、`DecisionSource`、auto-managed flag、mode、quantity、PnL、reasons。 |
| `core/finmind` | FinMind API client / importers / boundaries | API quota、dataset 權限、盤中誤用 | 允許，但需審查 | 不得新增盤中即時 fallback。使用 fake gateway tests。 |
| `core/cache` | 本機 H2 cache 與 watchlist persistence | 本機狀態與清理行為 | 允許 | 改變 persistent semantics 前需測試。 |
| `core/MarketDataCollectorFeed.java` | SQL 盤中 feed 與 session bar loading | source mode 漂移與 stale data | 嚴格審查 | 測試 `TICKS_AGGREGATED`、`CANDLES_ONLY`、`AUTO`、incomplete ticks，且不得有 FinMind fallback。 |
| `core/MarketDataCollectorRepository.java` | SQL 存取與 schema helper behavior | 隱性 migration 與 SQL 相容性 | 有限度允許 | 大範圍 schema 變更需人工審查。 |
| `core/RealtimeBarBuilder.java` | live tick 轉 K 棒聚合 | volume 暴增與額外 13:30 M5 bar | 嚴格審查 | 測試 cumulative volume 轉 delta 與台股 M5 54-bar 行為。 |
| `core/TimeframeAggregator.java` | timeframe aggregation | M1 / M5 / D1 / W1 一致性 | 允許，但需審查 | 測試 timeframe 邊界與 session rules。 |
| `src/test/java` | 回歸保護 | 低價值或不穩定測試 | 允許 | 優先行為測試。避免 testing-only production API。 |
| `README.md` | 使用者安裝與操作 | 使用者可見行為漂移 | 允許 | 使用方式、資料邊界或流程改變時更新。 |
| `PROJECT_DOCUMENTATION.md` | 架構、進度、限制 | 架構描述漂移 | 允許 | 策略、replay、report 或限制改變時更新。 |
| `AGENTS.md` | AI 協作規則 | 流程規則漂移 | 允許，但需謹慎 | 不要整份替換。只新增聚焦段落。 |

## 測試責任對照

- Scanner 行為：`MarketScannerServiceTest`、template / settings tests。
- Risk 行為：`RiskManagerTest`、auto-monitor gate tests。
- Execution 行為：`ExecutionEngineTest`。
- Replay / backtest 行為：`RadarReplayBacktestServiceTest`、`BacktestEngineTest`、report / statistics tests。
- 資料源行為：`MarketDataCollectorFeedTest`、`RealtimeBarBuilderTest`、FinMind importer / client tests。
- Logging / report 行為：`PaperTradeRecorderTest`、`LogExporterTest`、`BacktestReportExporterTest`。

## 文件責任對照

行為改變時，請更新：

- 使用者操作或資料源邊界：`README.md`。
- 架構、策略流程、限制、驗證：`PROJECT_DOCUMENTATION.md`。
- AI 協作規則：`AGENTS.md`。
- 功能鏈路規則：`FEATURE_FLOW.md`。
- 測試要求：`TESTING_GUIDE.md`。
- 審查規則：`CODE_REVIEW_CHECKLIST.md`。
- 高風險區域：`RISK_AREAS.md`。
