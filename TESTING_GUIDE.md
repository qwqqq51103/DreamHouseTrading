# DreamHouseTrading 測試指南

最後更新：2026-06-03

本指南定義 Codex 變更需要執行哪些指令與測試。compile 通過只代表語法檢查通過。unit tests 通過不代表 coverage 通過。coverage 只有在 JaCoCo 實際執行時才有效。

## 1. 指令

快速編譯：

```bash
mvn -q -DskipTests compile
```

不執行 JaCoCo 的快速單元測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

覆蓋率測試：

```bash
cmd /c "mvn -q test"
```

完整 clean coverage report：

```bash
cmd /c "mvn -q clean test jacoco:report"
```

規則：

- compile 通過不代表功能正確。
- `-Djacoco.skip=true` 只能確認 unit tests，不代表 coverage。
- 如果本機因檔案權限問題無法執行 JaCoCo，必須明確回報原因並列出殘餘風險。
- 不得只為提高 coverage 而新增低價值測試。
- 不得新增 testing-only production APIs。

## 2. 修改類型對應必跑測試

只改文件：
- 實務可行時執行 compile。
- 未改 production behavior 時，不要求 coverage。

只改 UI 文字：
- 執行 compile。
- 可見行為改變時，建議手動 UI 驗證。

UI settings：
- 執行 compile 與 unit tests。
- 行為改變時，測試 config load / save、template apply、custom template round-trip、scanner request construction。

Config / template：
- 執行 compile 與 unit tests。
- 測試內建 A/B/C 保護、自訂 save / load、setting summary、replay request、auto-monitor request。

Scanner：
- 可行時執行 compile、unit tests、coverage。
- 透過 `MarketScannerService.scan(...)` 測試 allowed 與 blocked cases，不要只測 getters。

Decision / risk：
- 可行時執行 compile、unit tests、coverage。
- 測試 allow path、reject path、reason、quantity、cash、cooldown、time window、circuit-break rules。

Execution：
- 可行時執行 compile、unit tests、coverage。
- 測試 `ExecutionEngine.executeDecision(...)`、`DecisionSource`、auto-managed flag、long-only behavior、order result、paper trade record。

Backtest / replay：
- 可行時執行 compile、unit tests、coverage。
- 測試 N+1 open、next tick execution、13:25 force close、cost model、global position limits、daily trade limits、same M5 bar rule。

CSV / report：
- 執行 compile 與 unit tests。
- 測試 UTF-8 with BOM、schema fields、gross / commission / tax / slippage / net、reasons、`DecisionSource`、auto-managed flag、score components。

MarketDataCollector / RealtimeBarBuilder：
- 可行時執行 compile、unit tests、coverage。
- 測試 source mode、incomplete ticks、no FinMind intraday fallback、volume delta conversion、台股 M5 54-bar behavior。

SQL repository / schema：
- 執行 compile 與 focused repository tests。
- 未經人工審查，不要做大範圍自動 schema 變更。

## 3. P0 / P1 / P2 測試優先序

P0：
- `RadarReplayBacktestService`
- `BacktestEngine`
- `RiskManager`
- `ExecutionEngine.executeDecision(...)`
- `ReportGenerator` / `BacktestResult` / `TradeStatisticsAnalyzer`
- `PaperTradeRecorder`
- `AutoMonitorExecutionGate`

P1：
- `MarketDataCollectorFeed`
- `RealtimeBarBuilder`
- `TimeframeAggregator`
- SQL ticks / candlesticks source modes

P2：
- `RadarStrategyConfig`
- `SignalMonitorConfig`
- `SignalMonitorTemplateManager`
- A/B/C built-in templates
- Custom template round-trip
- UI setting summaries

## 4. 交易語意測試

測試名稱建議以行為命名，例如：

```text
shouldOpenLongWhenAllDayTradeConditionsPass
shouldBlockOpenLongWhenRsiIsShort
shouldBlockOpenLongAfter1325
shouldUseNextBarOpenToAvoidLookaheadBias
shouldExportBlockReasonToReplayCsv
```

受影響時必須覆蓋：

- N+1 open 避免 lookahead bias。
- 同一根 K 棒同時碰到停利與停損時，停損優先。
- 13:25 阻擋新的 `OPEN_LONG`。
- 13:25 只平掉 auto-managed positions。
- manual positions 不應被 auto-managed cutoff logic 誤平。
- v1 維持 long-only。
- FinMind 不可作為盤中即時 fallback。
- replay with ticks 必須依 replay time 切片。
- `TICKS_AGGREGATED` 不得預先聚合未來 K 棒。
- 當沖數量維持 1000 股。
- 現金不足時拒單，不得降低數量。
- 停損冷卻與平倉後冷卻有效。
- 每日最多交易與最大同時持倉有效。
- 同一根 M5 K 只允許一筆進場。
- 預期給 Excel 開啟的 CSV 含 UTF-8 BOM。

## 5. 禁止優先補的低價值測試

不要優先補：

- 只測 getter / setter。
- 只測 constructor。
- 只測 enum values。
- 沒有行為意義的 null-only tests。
- 不驗證行為的 Swing constructor tests。
- 需要新增只給 tests 使用的 production APIs 的測試。

## 6. 測試回報格式

```text
測試覆蓋評估：
- 改變的行為：
- 必要測試：
- 已新增測試：
- 觸及 production classes：
- 已執行指令：
- 手動檢查：
- 未測區域：
- Coverage 狀態：
```
