# DreamHouseTrading 程式碼審查清單

最後更新：2026-06-03

當 GPT 或人工審查者檢查 Codex 產生的 diff 或 PR 時，請使用這份清單。審查重點應放在缺陷、行為回歸、缺少測試，以及交易語意不清楚的地方。

## 1. 審查輸入

審查前請先確認：

- Codex 已回報目前 dirty worktree 狀態。
- Codex 已列出修改檔案與預期範圍。
- Codex 已列出功能鏈路影響。
- Codex 已列出測試與覆蓋率狀態。
- diff 不包含本機執行輸出。

## 2. 功能鏈路

檢查本次變更是否影響：

- UI。
- Config / template。
- Scanner。
- `MarketScanResult`。
- `DecisionResult`。
- `RiskManager`。
- `ExecutionEngine`。
- Backtest / replay。
- CSV / report。
- Paper trade record。
- 文件。

如果任何受影響層級缺漏，應要求修改。

## 3. 交易語意

請檢查：

- `DecisionResult` 仍是標準決策輸出。
- 進場檢查仍經過 `RiskManager` 或等價核心風控服務。
- 可執行決策使用 `ExecutionEngine.executeDecision(...)`。
- UI 沒有重做交易、風控或訂單語意。
- 沒有新增可執行放空路徑。
- v1 仍維持 long-only。
- `DAY_TRADE` 數量仍是一張 / 1000 股。
- 現金不足時走拒單，而不是自動降低數量。
- `13:25` cutoff 仍阻擋新的自動 `OPEN_LONG`。
- `13:25` 強制平倉只套用在 auto-managed positions，不應誤套用人工部位。
- 停損冷卻、平倉後冷卻、日損熔斷、連敗熔斷、每日最多交易、最大同時持倉、同一根 M5 K 只允許一筆等規則仍有效。
- 機器決策沒有依賴脆弱的 `reason.contains(...)` 文字判斷。

## 4. Lookahead Bias

請檢查：

- 第 N 根已完成 K 棒產生的訊號，只能在第 N+1 根 open 或下一筆可見 tick 成交。
- replay 在 09:30:20 這類時間點，不能看到完整的 09:30-09:34:59 M5 K 棒。
- tick 聚合必須依 replay time 切片。
- candles-only 模式只能使用已收完 K 棒。
- 跨日暖機只能初始化指標，不可混入交易績效。
- 批次回測不可只把各股票獨立結果相加，而忽略全局持倉與交易節奏限制。

## 5. 資料源邊界

請檢查：

- 盤中雷達使用本機 SQL / `MarketDataCollectorFeed`。
- 沒有新增 FinMind 盤中即時 fallback。
- `TaiwanStockKBar` 沒有在文件邊界前被當成盤中即時 K 棒來源。
- stale guard 用於防止壞資料掃描，不是 UI 清空機制。
- `TICKS_AGGREGATED`、`CANDLES_ONLY`、`AUTO` 行為明確且可輸出報告。
- tick 聚合不完整時，會回報 incomplete source status。
- SQL / snapshot 累積 volume 在更新 live bars 前已轉為 delta。

## 6. CSV / Report

請檢查：

- 預期給 Excel 開啟的 CSV 使用 UTF-8 with BOM。
- header / schema 變更已文件化。
- 既有 schema 欄位沒有被靜默移除。
- 報告保留 grossProfit、commission、tax、slippageCost、netProfit。
- 報告保留 entry reason、exit reason、block reason、radar score components。
- paper records 保留 `DecisionSource`、auto-managed flag、trade mode、timeframe、quantity、PnL、strategy summary。
- 被阻擋候選不會全部簡化成泛用 `No entry signal`。

## 7. 測試

請檢查：

- 測試同時覆蓋允許與阻擋行為。
- 測試盡量走正式流程，而不是只測 getter。
- scanner 測試呼叫 `MarketScannerService.scan(...)`。
- replay / backtest 測試能證明 lookahead protection。
- execution 測試能證明有使用 `DecisionResult` 與 risk path。
- CSV 測試在相關情境驗證欄位與 BOM。
- Codex 沒有新增 testing-only production API。
- `-Djacoco.skip=true test` 沒有被回報成 coverage 通過。

## 8. 禁止自動合併條件

以下情況不得自動合併：

- execution、risk、scanner 或 backtest 有變更，但缺少聚焦測試。
- 交易語意有變更，但文件未更新。
- CSV / report 有變更，但缺少 schema 測試與文件。
- coverage 失敗，或跳過 coverage 卻未說明原因。
- 出現新的可執行放空行為。
- 出現 FinMind 盤中 fallback。
- PR 包含本機 `logs/`、`data/`、`config/ui-layout.xml` 或 paper trade CSV。
- unrelated dirty files 被混入 PR。
- reason 文字被拿來當機器狀態邏輯。
- replay / backtest lookahead 行為未測試。

## 9. GPT Review 輸出格式

```text
審查結果：
- 通過 / 不通過 / 要求修改：

阻擋問題：
- ...

非阻擋問題：
- ...

缺少的驗證：
- ...

風險評估：
- ...

建議下一步：
- ...
```
