# DreamHouseTrading Project Documentation

最後更新：2026-05-26

## 1. 專案目標

DreamHouseTrading 是台股交易分析與模擬平台，重點是把盤中 SQL 分 K、雷達策略、風控、回測、紙上交易紀錄串成可驗證的流程。

目前不做真實券商下單，所有交易相關功能都限制在模擬交易、回測與報告分析。

## 2. 系統流程

1. Market Data
2. Signal Generation
3. Decision & Risk
4. Execution Simulation
5. Backtest & Reporting

核心原則：

- `DecisionResult` 是標準決策輸出。
- `MarketScanResult` 是雷達掃描輸出。
- `ExecutionEngine` 負責把決策轉成模擬執行結果。
- UI 不重做交易語意，只展示核心服務輸出。

## 3. 資料源現況

### 3.1 MarketDataCollectorFeed

狀態：主要盤中資料源。

用途：

- 讀取 MySQL `market_data`。
- 載入今日或指定日期 `09:00~13:30` 分 K。
- 觀察清單顯示最新 SQL tick。
- 觀察清單個股漲跌幅優先以指定日期前一交易日 SQL 收盤價為基準；缺昨收時才退回當日首根 K 開盤價或目前價格。
- 主圖 live tick 若收到 SQL / snapshot 累積成交量，會先轉成差分量再更新目前 K 棒，避免圖表成交量柱被重複累加放大。
- RealtimeBarBuilder 對累積 volume 倒退、歸零或來源切換採 reset / ignore 邏輯，不產生負成交量。
- 台股 M5 日內完整 bar start 為 09:00 到 13:25，共 54 根；13:30 收盤 tick 不會額外建立 13:30 M5 bar。
- Collector 資料過期時會跳過該股票掃描，並以 UTF-8 BOM CSV 輸出到 `logs/market-data-warnings/collector_stale_warnings_yyyyMMdd.csv`，方便追查 symbol、latest tick、lag 與 threshold。
- 雷達掃描與 SQL 回測。

限制：

- Collector 沒有啟動或資料過期時，雷達應跳過，不得改打 FinMind 即時 API。
- 台股 `13:25~13:30` 集合競價期間 tick 可能停止更新，這不是 Collector 故障。

### 3.2 FinMind

狀態：低頻與手動查詢資料源。

允許用途：

- 盤前 / 盤後補資料。
- `TaiwanStockKBar` 盤後分 K。
- 工具列已可用日期範圍補 `TaiwanStockKBar` 到 SQL；匯入前檢查股票日期的 SQL M1 分 K 根數、開收盤覆蓋與 D1 日線是否存在，只對不完整資料組合呼叫 API。匯入會寫入 `candlesticks` 的 `M1/M5/M15/M30/H1/D1`；D1 優先使用 `TaiwanStockPrice`，無日線資料時由當日分 K 聚合。
- `TaiwanStockInfo` 股票名稱與產業 fallback。
- `TaiwanStockIndustryChain` 產業鏈匯入 SQL。
- `TaiwanStockTradingDailyReport` 分點資料。
- 新聞、籌碼、API usage 與手動 API 面板。

禁止用途：

- DreamHouseTrading 盤中雷達不得直接呼叫 FinMind 即時行情。
- 盤中不得用 `TaiwanStockKBar` 補即時分 K。

### 3.3 Yahoo

狀態：保留為輔助資料源，不作為目前當沖自動監控主資料源。

## 4. 當沖監控進度

已完成或已接入：

- 自動監控語意強制為 `DAY_TRADE`。
- 當沖模擬開單固定 `1000` 股。
- 13:25 後禁止自動開倉並強制平倉 auto-managed 部位。
- 早盤禁開倉預設 `09:00~09:15`，可由監控設定調整。
- 同股停損冷卻預設 `60` 分鐘，平倉後冷卻預設 `30` 分鐘。
- 最大同時持倉預設 `3`。
- 每日最多自動交易預設 `5`。
- 日損、連敗、停損次數熔斷。
- `SignalRSI = SHORT` 阻擋 `OPEN_LONG`。
- VWAP、VWAP slope、Volume Sustain、ATR 追高限制。
- RANGE 盤時間 / 動能失效出場。
- 三層停損停利：結構停損、盤勢感知停利、持倉中理由失效 / 移動停損。
- 內部市場狀態過濾已可在監控設定調整 ALLOW / BLOCK 門檻，例如 VWAP 通過比例、平均漲跌、Volume Sustain 比例與創低多於創高檔數。

目前策略方向：

- 日線只做盤前股票池。
- 5 分 K 是主交易層。
- 1 分 K 是執行確認層。
- 盤中缺少可靠即時大盤與產業資料時，使用觀察清單內部市場狀態替代。
- 「弱勢盤強於大盤 / 強於族群」在目前即時當沖模式下表示強於內部基準 / 觀察清單群體；除非未來接上可靠即時指數與族群 SQL，否則不應解讀成即時 TAIEX / 產業資料。

## 5. 回測與報告進度

已完成或已接入：

- SQL 雷達批次回測。
- 指定日期與日期範圍回測。
- SQL 雷達開盤重播，可在非開盤時間用指定日期 SQL K 線按模擬時間逐步更新機會雷達、主圖 K 線、執行狀態重播紀錄與已發生交易標記。
- 同一天資料仍按單日交易規則計算。
- N 根訊號成立後，N+1 open 成交。
- 同一根 K 同時碰停損與停利時，採保守停損優先。
- 批次雷達回測已把泛用 `No entry signal` 拆成 EMA、RSI、量能突破或投票不足等具體未進場原因。
- 回測開倉前加入「成本後停利空間不足」硬阻擋；停利目標扣除買賣手續費、當沖稅與滑價後無正淨利時不建立部位。
- M5 慢速 EMA 可使用 SQL 跨日 K 線暖機，暖機資料只供指標計算，不納入指定日期交易績效。
- SQL 單檔回測、批次 / 日期範圍回測與開盤重播都會記錄跨日暖機診斷，包含是否啟用、要求根數、實際載入根數與暖機起訖時間，用來驗證暖機是否真的生效。
- 回測報告拆分 grossProfit、commission、tax、slippageCost、netProfit。
- 交易理由、阻擋原因、技術指標與週期資訊寫入報告。
- 雷達策略訊號會記錄每項信心度、權重、LONG 加分與原因，並輸出到模擬交易 CSV 與 SQL 雷達回測報告。
- Backtest result / statistics 需統計已平倉交易成本拆分、出場原因分布與未進場阻擋原因分布；勝率不得混入未平倉部位。
- Paper trade CSV 需寫入 `decision_context_source` 與 `auto_managed`，讓自動監控、replay 與 backtest 來源可追蹤。
- TradeRecord / LogExporter CSV 需保留 `decision_source`、`auto_managed`、`timeframe`、成本拆分、entry / exit / block reason、setup score、radar score components 與策略設定摘要，避免正式交易紀錄匯出時丟失核心語意。

### LogExporter CSV schema v2

- LogExporter CSV schema v2 使用 ASCII snake_case header，方便程式解析與跨工具匯入；此變更相對舊中文 header 屬於 schema breaking change。若外部 Excel 模板、匯入工具或分析腳本依賴舊欄位名稱，需同步調整欄位對應。
- LogExporter CSV 仍使用 UTF-8 with BOM；欄位順序由測試鎖定，後續改名、改順序或改語言需同步更新文件與測試。
- LogExporter schema v2 主要欄位包含：`trade_id`、`decision_source`、`auto_managed`、`trade_mode`、`timeframe`、`quantity`、`entry_price`、`exit_price`、`gross_profit`、`commission`、`tax`、`slippage_cost`、`net_profit`、`entry_reason`、`exit_reason`、`block_reason`、`radar_score_components`、`strategy_setting_summary`。
- Backtest 匯出 TradeRecord 時，`DecisionSource` 應為 `BACKTEST`。若 timeframe、成本拆分、entry / exit reason、strategy summary 可由 `BacktestResult` 或交易資料取得，必須寫入；若 block reason 或 radar score components 目前不可從交易配對取得，應保留空值並列為後續接入項目，不得填入誤導性預設值。
- 雷達最低進場分數是做多門檻通過後的第二道 OPEN_LONG 分數門檻；同配置回測時需同時記錄多頭進場門檻與雷達最低進場分數。
- SQL 雷達回測報告已輸出加分條件貢獻統計與硬阻擋後續統計，可比較條件通過 / 未通過後的最大漲幅、最大回撤與收盤報酬。
- SQL 雷達 CSV、條件統計 CSV 與 stale warn 診斷 CSV 統一用 UTF-8 with BOM 寫出，避免 Windows Excel 直接開啟時中文欄位變成亂碼。
- 圖表支援回測開平倉標記。

仍需持續驗證：

- 以多日資料驗證被阻擋訊號與加分條件貢獻統計是否穩定。
- 三層停損停利在多日資料上的勝率、Profit Factor 與平均虧損變化。

## 6. 盤後股票池進度

已完成或已接入：

- 產生隔日當沖股票池 UI。
- 觀察清單可鎖定股票，避免被股票池覆蓋。
- 股票池依日線趨勢、量能、型態、當沖活躍度、風險扣分與分點籌碼評分。
- 分點資料讀取 `finmind_taiwan_stock_trading_daily_report`，並支援 raw JSON 格式聚合。
- 分點欄位改用每家分點買賣超聚合，顯示最大買超 / 最大賣超分點、張數、分點家數與筆數。

注意：

- FinMind 分點資料是盤後資料，適合隔日股票池，不適合盤中進場。
- 若指定日期資料尚未匯入 SQL，股票池需先匯入或明確顯示資料缺失。

## 7. UI 進度

已完成或已接入：

- 主圖 K 棒批次載入，避免逐 tick 推送造成卡頓。
- 表格支援欄位 resize 與排序。
- 觀察清單支援批量新增 / 刪除與中文名稱補齊。
- 產業分布支援大類篩選與加入觀察清單。
- FinMind API 面板支援資料集分類與寫入 SQL。
- 交易紀錄分析可匯入每日 CSV 並檢視開平倉流程。
- 監控設定收斂為當沖有效欄位，短線 / 波段欄位停用。
- 監控模板提供 A 組穩健 `EMA8/34 + 跨日暖機 + 突破後一根 K 確認`、B 組放寬 `EMA8/21`、C 組 `SMA8/21 + 量能倍數 1.45`；三組皆阻擋均線單因子進場並關閉內部市場狀態過濾，供同資料 A/B/C 比較。
- 監控設定可儲存使用者自訂模板，並新增「刪除模板」移除自訂模板；內建 A/B/C 測試模板不可刪除，避免測試基準被誤刪。
- A/B/C 三組都會把均線單因子多單改為硬阻擋；A 組額外要求突破後一根 K 確認，B 組用 EMA8/21 放寬反應速度，C 組用 SMA8/21 測試均線類型差異，scanner 會保留阻擋原因供批次回測比較。
- 監控設定已顯示「阻擋 EMA 單因子進場」，模板切換、套用與自訂模板儲存會保留此旗標，避免 C 組 UI 回測退回 EMA-only 進場。
- SQL 雷達回測配置摘要會直接列出 EMA 單因子進場是否允許，先用報告確認模板旗標再比較績效。
- 監控設定已開放 SQL 回測跨日暖機開關與暖機 K 線根數，模板套用、自訂模板儲存與回測報告共用同一份設定。
- 盤中自動監控建立 scanner 時會固定使用今日 SQL 資料，並在啟用跨日暖機時讀取前期 K 線初始化 EMA / Volume Sustain / VWAP；內部市場 context 仍只作市場狀態與相對強弱，不再覆蓋 scanner 的暖機 bars。
- SQL 雷達 replay 已改用訊號成立時間判斷早盤禁開倉，並套用每日最多交易、開單間隔與同一根 5 分 K 只允許一筆；scanner 固定從 replay feed 取當下可見 K 線與跨日暖機，不再用 internal market context bars 覆蓋掃描 K 線，降低與盤中自動監控開單不一致的問題。
- SQL 雷達 replay 與盤中自動監控的 scanner 會帶入實際掃描時間 `asOfTime`；若重播資料來源有 SQL ticks，replay feed 會用 replay time 以前的 ticks 重新聚合當下 partial K 棒，避免歷史重播在 09:30:20 直接使用 09:30~09:34:59 的完整 M5 K 線。若只能使用 candlesticks，才退回只使用已收完 K 線。
- SQL 雷達批次回測合併多檔結果時會以全局每日最多交易、開單間隔、同一根 5 分 K 限制與最大持倉數篩選完成交易，避免逐檔獨立 replay 高估交易筆數。
- SQL 雷達 replay 每次掃描會保留監控設定的 K 棒數，只用 rolling feed 限制當下可見資料；不得在回放迴圈中把 request 的 barCount 改成累積根數，避免 Volume Sustain、EMA、VWAP 與盤中監控不一致。
- 工具選單與工具列新增「SQL 雷達開盤重播」與停止重播；重播模式使用本機 SQL 資料與模擬時間，會在執行狀態顯示所有已發生的重播開平倉紀錄，圖表只標示目前商品的交易點，不回打 FinMind，不寫入正式紙上交易紀錄。
- SQL 雷達 replay 會在 `logs/radar-replay` 輸出 UTF-8 BOM 診斷 CSV，逐步記錄掃描檔數、OPEN_LONG 數、阻擋數、交易事件與 Top candidates，用於追查「批次回測有單但重播沒單」這類差異。
- SQL 雷達 replay 摘要中的交易事件已改為由逐步 scanner 結果即時計算，不再混用預先產生的批次 N+1 open 交易清單；重播成交會套用最大持倉、每日最多交易、開單間隔與同一根 5 分 K 限制，用於比對盤中自動監控的開單順序。
- SQL 雷達開盤重播新增掃描間隔與掃描秒偏移；預設會讀取同日正式 paper trade `orders_yyyyMMdd.csv` 的 auto-monitor 時間推估實盤排程秒偏移，並以 SQL ticks 的當下以前最新價作為 replay 開平倉價格來源，降低 M5 bar close 與盤中即時價格造成的差異。
- SQL 雷達開盤重播的 `TICKS_AGGREGATED` 模式已改為依 replay time 對 full-day ticks 做切片聚合；這是比對實盤自動監控的建議模式，避免盤後完整 K 棒造成 Lookahead Bias。
- 佈局選單已接上停靠面板版面儲存與重設：儲存會寫入專案根目錄的本機 `config/ui-layout.xml`，下次啟動會在主視窗開啟後延遲套用；若 Modern Docking 完整 XML 還原失敗，會解析儲存檔內的 dockable ID 並強制隱藏未儲存面板，確保重開後只顯示使用者儲存的視窗集合。重設會還原預設停靠版面並刪除本機儲存檔；佈局讀寫診斷會寫入 `logs/ui-layout/ui_layout.log`。

仍需注意：

- 所有新增表格都要確認 resize 時欄位不遮蔽文字。
- UI 只負責呈現與設定，不要複製核心交易語意。

## 8. 已知限制

- FinMind 無法作為穩定盤中即時大盤 / 產業資料源。
- 當沖策略目前不依賴即時大盤與即時產業，改用觀察清單內部市場狀態。
- 產業鏈與分點資料是低頻資料，主要用於盤前選股與盤後分析。
- MarketDataCollector 必須在盤中持續寫入 SQL，否則雷達只能跳過資料不足股票。
- macOS 可執行 DreamHouseTrading，但若 Collector 跑在 Windows，需調整 MySQL 網路連線設定。

## 9. 驗證

編譯：

```bash
mvn -q -DskipTests compile
```

測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

本機測試備註：

- 這台 Windows 主機上一般 `mvn test` 可能被 JaCoCo coverage 檔權限卡住。
- 測試輸出可能出現既有背景 scheduler noise，只要 Maven exit code 為 0 即視為通過。

## 10. 後續優先序

1. 用多日 SQL 回測驗證三層停損停利是否降低平均虧損。
2. 用 A/B/C 模板比較 EMA8/34 跨日暖機穩健組、EMA8/21 放寬組與 SMA8/21 測試組的條件貢獻。
3. 持續校準具體未進場原因與被阻擋訊號後續表現統計。
4. 改善分點資料匯入診斷，讓資料缺失、API 失敗、SQL 已有資料三種狀態更清楚。
5. 等回測可信度與風控穩定後，再評估 ML 或更複雜模型。

## 2026-05-27 進度：SQL 雷達重播可固定資料來源

- SQL 雷達開盤重播新增資料來源模式：`TICKS_AGGREGATED`、`CANDLES_ONLY`、`AUTO`。
- `TICKS_AGGREGATED` 會固定從 SQL ticks 聚合日內 K 線，作為比對盤中實盤雷達的預設模式。
- `CANDLES_ONLY` 會固定讀 SQL candlesticks，用於檢查盤後補分 K 後的資料。
- `AUTO` 維持原本邏輯：candlesticks 完整時優先，否則 fallback 到 ticks 聚合；此模式可能因盤後補資料導致同一設定重播結果改變。
- 雷達重播 CSV 已擴充欄位，包含策略名稱、完整設定摘要、資料來源模式、每檔實際載入來源、OPEN_LONG 候選、阻擋候選、交易事件與雷達加分明細；輸出使用 UTF-8 with BOM。
- 雷達重播新增 `_symbols.csv` 每檔明細檔，讓每個 replay time / symbol 各自成列，並輸出 `scanner_bar_source`、`scanner_bars`、`visible_session_bars`、`last_visible_session_bar_time`、`warmup_enabled`、`warmup_requested_bars`、`warmup_loaded_bars`、`warmup_first`、`warmup_last`，避免摘要 CSV 單格過長造成 Excel 閱讀時股票代碼與欄位看起來錯位。
- M5 完整日內 K 線預期根數修正為 54 根（09:00 到 13:25 的 bar start），不再把 13:30 多算成額外一根而誤標 `CANDLES_INCOMPLETE`。
- ticks 聚合來源會標示 `TICKS_AGGREGATED_COMPLETE / TICKS_AGGREGATED_INCOMPLETE`；若 Collector 缺少 09:00 bucket，重播會清楚顯示 ticks M5 根數不足，避免誤以為同配置但資料完整一致。
- 主程式「關於 DreamHouseTrading」與「當沖指標設定說明」已補充資料來源模式與 CSV 診斷說明。

## P1-C Auto Monitor / Replay cadence convergence

## P2 Template / Config round-trip

- Template logic has a core `SignalMonitorTemplateManager` so A/B/C built-ins, custom template persistence, setting summaries, auto-monitor requests, and replay requests can share the same config copy / serialization path.
- Built-in A/B/C templates remain protected baselines. Custom template save/delete only affects user-defined templates.
- Round-trip tests now cover `RadarStrategyConfig`, `SignalMonitorConfig`, and `SignalMonitorTemplateManager`, including VWAP, VWAP slope, Volume Sustain, ATR chase limit, RSI blocking, market context, breakout confirmation, MA type / periods, timeframe, bar count, cross-day warmup, scan cadence, `DecisionSource`, auto-managed flag, and day-trade quantity.
- Scanner template tests run through `MarketScannerService.scan(...)` to verify template-applied flags affect the formal scanner path, not only object getters.

- Auto Monitor 與 SQL Radar Replay 的自動開倉節奏已收斂到 `AutoMonitorExecutionGate`：早盤 warmup、13:05 禁止新倉、13:25 force-close window、daily max trades、entry pacing、同一根 M5 一筆、max open positions、stop-loss cooldown 與 trading halted 狀態使用同一組 gate input。
- 時間判斷以 signal / asOfTime 為準，不以 K 棒 start time 取代訊號成立時間。
- 13:25 cutoff 由 `ExecutionEngine.forceCloseAutoManagedPositions(...)` 依 engine 的 auto-managed 狀態平倉；manual position 不會被這條規則誤關。
- Live replay exit cooldown 不再用 `reason.contains("STOP")` 判斷，改用 replay exit type metadata。
- `ExecutionResult` 現在保留 `DecisionSource`，`PaperTradeRecorder` 在缺少 `DecisionResult` 的 system / cutoff close 場景會 fallback 到 result 上的 source。
- `ExecutionEngine.reversePosition(...)` 在 v1 long-only 直接拒絕，避免反向倉位或可執行放空路徑。
- `ExecutionEngine.partialClose(...)` 會保留 partial close reason、realized PnL 與剩餘 quantity。
---

## AI Collaboration Documentation

The semi-automatic GPT + Codex workflow is documented in these root-level files:

- `PROJECT_CONTEXT.md`: project scope, data-source boundaries, trading rules, and collaboration mode.
- `FEATURE_FLOW.md`: required feature-chain inspection from UI through docs.
- `TESTING_GUIDE.md`: required validation commands and tests by change type.
- `CODE_REVIEW_CHECKLIST.md`: GPT / reviewer checklist for Codex diffs and PRs.
- `RISK_AREAS.md`: high-risk modules and automatic-merge blockers.
- `MODULE_OWNERSHIP.md`: package responsibilities, allowed Codex scope, and required checks.

These documents formalize C-mode semi-automatic collaboration: GPT prepares requirements and review, Codex reads the repo and produces scoped diffs, and the user keeps final merge and release authority.
