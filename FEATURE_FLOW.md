# DreamHouseTrading 功能鏈路

最後更新：2026-06-03

本文件定義 Codex 在修改 DreamHouseTrading 前必須檢查的功能鏈路。程式能編譯不代表功能完成。任何交易功能都必須追蹤到會影響使用者行為、scanner 輸出、風控閘門、模擬執行、replay、報告與文件的相關層級。

## 1. 目的

DreamHouseTrading 是 Java 17 Swing 交易分析平台，支援分析、掃描、決策、回測、重播與 paper-trade 型態的模擬執行。專案不得新增真實券商下單路徑。

本專案主要風險是局部完成式修改：只改一個 class，卻讓 UI、config、scanner、risk、replay、CSV 或文件彼此不一致。本文件就是避免這類問題的檢查清單。

## 2. 標準功能鏈路

每次新增功能或修改行為時，請檢查以下鏈路：

1. UI / 使用者操作入口
2. Config / template / settings persistence
3. Scanner / signal generation
4. `MarketScanResult`
5. `DecisionResult`
6. `RiskManager` / risk gate
7. `ExecutionEngine.executeDecision(...)`
8. Position / order / trade record
9. Backtest / replay
10. CSV / report export
11. Paper trade record
12. `README.md` / `PROJECT_DOCUMENTATION.md` / `AGENTS.md`
13. 主程式「About DreamHouseTrading」文字
14. 主程式「day-trading indicator settings」文字

如果某一層不適用，實作回報必須說明原因。

## 3. 常見功能類型

Scanner 條件變更：
- 檢查 `RadarStrategyConfig`、`MarketScannerService`、`MarketScanResult`、replay `_symbols.csv`、backtest statistics、UI 可見原因與文件。

Risk 或 execution 變更：
- 檢查 `DecisionResult`、`RiskManager`、`ExecutionEngine`、paper trade CSV、auto-managed flags、replay / backtest 語意一致性，以及 allow / block 兩種測試。

資料源變更：
- 檢查 `MarketDataCollectorFeed`、`MarketDataCollectorRepository`、`RealtimeBarBuilder`、FinMind 邊界、stale diagnostics、UTF-8 BOM CSV 輸出、replay source modes 與文件。

Report 或 CSV schema 變更：
- 檢查 exporters、paper trade record、backtest report dialogs、Excel BOM 行為、文件，以及既有 CSV readers 的相容性。

UI 設定變更：
- 檢查載入、儲存、內建 A/B/C templates、自訂 template round-trip、setting summary、scanner request construction、replay request construction 與測試。

## 4. 最低完成規則

功能只有在符合以下條件時，才可視為完成：

- UI 有入口，或回報清楚說明這是純核心功能。
- Config / template state 可儲存，且重建後不會遺失行為。
- Scanner、decision、risk、execution 語意一致。
- Replay、backtest、auto monitor 使用同一套核心規則。
- CSV / report exports 包含足夠診斷欄位。
- Paper trade records 保留 `DecisionSource`、auto-managed flag、mode、quantity、PnL、entry reason、exit reason。
- 交易語意改變時，測試至少覆蓋一個通過案例與一個阻擋 / 失敗案例。
- 已執行 compile 與必要測試。
- 使用者行為、資料邊界、策略語意或報告欄位改變時，文件已同步更新。

## 5. Codex 修改前回報

修改前 Codex 應回報：

```text
功能鏈路風險：
- 目標：
- 既有入口：
- 預計修改檔案：
- 新增 class / method / config / enum：
- 受影響層級：
- 資料源影響：
- 交易語意影響：
- CSV / report 影響：
- Backtest / replay 影響：
- Paper trade 影響：
- 預計測試：
- 不可修改檔案：
```

## 6. Codex 完成後回報

修改後 Codex 應回報：

```text
功能鏈路檢查：
- UI：
- Config / Template：
- Scanner：
- MarketScanResult：
- DecisionResult：
- RiskManager：
- ExecutionEngine：
- Backtest / Replay：
- CSV / Report：
- Paper Trade Record：
- 文件：

防漏檢查：
- 新增 class / method 呼叫位置：
- 新增 config 儲存 / 載入：
- 新增 UI 欄位是否套用到核心 config：
- 新增策略條件是否出現在原因 / 報告：
- 是否新增 testing-only production API：
- 是否使用 reason 字串作為機器邏輯：

驗證：
- compile：
- unit test：
- coverage：
- manual verification：
```

## 7. 驗收標準

以下情況不得接受變更：

- 修改核心交易語意但沒有測試。
- 繞過 `RiskManager`、`DecisionResult` 或 `ExecutionEngine.executeDecision(...)`。
- 新增可執行放空行為。
- 修改 replay / backtest 但未處理 lookahead bias。
- 修改 CSV / report 欄位但缺少文件與 export tests。
- 提交 `logs/`、`data/`、`config/ui-layout.xml` 或 paper trade CSV 等本機執行輸出。
- 只執行 `-Djacoco.skip=true` 卻宣稱 coverage 通過。
