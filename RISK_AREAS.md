# DreamHouseTrading 高風險區域

最後更新：2026-06-03

本文件標示 Codex 不應大範圍自動修改的區域。這些模組包含交易語意、資料源邊界、報告 schema，或本機執行狀態。

## 1. 通用規則

- 不得新增真實券商執行。
- 不得新增可執行放空行為。
- 不得繞過 `DecisionResult`。
- 不得繞過 `RiskManager`。
- 不得繞過 `ExecutionEngine.executeDecision(...)`。
- 不得讓 UI state 成為核心交易事實來源。
- 不得使用 FinMind 作為盤中即時 fallback。
- 不得提交 `logs/`、`data/`、`config/ui-layout.xml` 或 paper trade CSV。
- 不得把 `-Djacoco.skip=true test` 回報成 coverage。

## 2. 高風險區域

| 區域 | 風險 | Codex 是否允許修改 | 必要限制 |
|---|---|---|---|
| `src/main/java/com/dreamhouse/trading/core/execution` | 訂單語意、paper execution、long-only 邊界 | 可以，但需嚴格審查 | 必須保留 `DecisionResult`、`RiskManager`、v1 long-only、paper record 欄位與測試。 |
| `src/main/java/com/dreamhouse/trading/core/decision/risk` | 現金、數量、時間閘門、冷卻、熔斷 | 可以，但需嚴格審查 | 必須測試 allow / reject cases，並同步 replay / backtest / auto-monitor 語意。 |
| `src/main/java/com/dreamhouse/trading/core/decision` | 決策輸出與策略投票 | 可以，但需審查 | 必須保留 `DecisionResult` 作為標準輸出，並避免可執行 short path。 |
| `src/main/java/com/dreamhouse/trading/core/scanner` | 進場訊號、硬阻擋、分數組件 | 可以，但需審查 | 必須更新 reasons、score components、CSV / report 可見性與 scanner tests。 |
| `src/main/java/com/dreamhouse/trading/core/backtest` | Lookahead、成交模型、成本、replay parity | 可以，但需嚴格審查 | 必須測試 N+1 open / next tick、stop-loss-first、成本與全局交易限制。 |
| `RadarReplayBacktestService` | replay 時間語意與執行一致性 | 可以，但需嚴格審查 | 必須避免 future bars，並保留全局 pacing / position limits。 |
| `BacktestEngine` | portfolio 與 fill behavior | 可以，但需審查 | 必須保留成本模型與交易配對語意。 |
| `MarketDataCollectorFeed` | 盤中資料源邊界與 source modes | 可以，但需嚴格審查 | 不得為盤中即時資料呼叫 FinMind fallback。必須輸出 source diagnostics。 |
| `MarketDataCollectorRepository` | SQL 讀寫與 schema creation | 有限度允許 | 大範圍 schema 變更需人工審查。避免隱性 migrations。 |
| `RealtimeBarBuilder` | tick 聚合與 volume 正確性 | 可以，但需嚴格審查 | 必須將 cumulative volume 轉為 delta，並避免產生額外 13:30 M5 bar。 |
| `TimeframeAggregator` | M1 / M5 / D1 / W1 aggregation | 可以，但需審查 | 必須保留台股 M5 09:00-13:25 共 54 根規則。 |
| `PaperTradeRecorder` | 正式 paper trade CSV | 可以，但需審查 | 必須保留 BOM、`DecisionSource`、auto-managed flag、reasons、mode、quantity、PnL。 |
| `LogExporter` / reports | CSV / report schema | 可以，但需審查 | Schema 變更需要測試與文件。 |
| `MainFrameWithDocking.java` | 大型 UI class，含多個 workflow entry points | 只允許小範圍修改 | 不得在 UI 重做核心交易語意。優先使用核心服務。 |
| Config / template classes | 策略設定持久化 | 可以，但需審查 | 必須保留 A/B/C built-ins 與 custom template round-trip。 |
| `logs/`、`data/`、`config/ui-layout.xml` | 本機執行輸出 | 不允許 | 永遠不得放入 PR。 |

## 3. 絕對禁止自動修改事項

- 新增真實券商下單。
- 新增實盤可執行放空。
- 讓 UI 成為 risk 或 execution 的事實來源。
- 用 UI checks 取代核心 risk checks。
- 用 ad hoc signal strings 取代 `DecisionResult`。
- 新增 FinMind 盤中即時 fallback。
- 在文件邊界前把 `TaiwanStockKBar` 當成盤中即時來源。
- 把本機執行輸出寫入版本控管變更。
- 新增只被 tests 使用的 production APIs。

## 4. 合併前必須審查

變更碰到以下區域時，必須由人工 / GPT 審查：

- `core/execution` 任何檔案。
- `core/decision/risk` 任何檔案。
- `MarketScannerService`。
- `RadarReplayBacktestService`。
- `BacktestEngine`。
- `MarketDataCollectorFeed`。
- `MarketDataCollectorRepository`。
- `RealtimeBarBuilder`。
- `PaperTradeRecorder`。
- `LogExporter`。
- `MainFrameWithDocking.java`。
- 任何 CSV / report schema。
- 任何會改變 scanner 行為的 config / template 欄位。

## 5. Dirty Worktree 治理

修改前：

- 執行 `git status --short --branch`。
- 回報既有 modified 與 untracked files。
- 不得 revert unrelated user changes。
- 不得把 unrelated dirty files 混入 patch。

PR 前：

- 確認沒有包含本機 `logs/`、`data/`、`config/ui-layout.xml`、diagnostic CSV 或 paper trade CSV。
- 如果已追蹤的本機輸出存在，必須回報，並要求人工決定是否移除或清理 history。

## 6. 高風險修改回報格式

```text
高風險修改：
- 區域：
- 原因：
- 預期行為：
- 受影響鏈路：
- 交易語意是否改變：
- Backtest / replay 是否改變：
- Auto-monitor 是否改變：
- CSV / report 是否改變：
- 資料源是否改變：
- 測試：
- 殘餘風險：
- 是否需要人工審查：
```
