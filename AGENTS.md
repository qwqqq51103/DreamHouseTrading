# DreamHouseTrading AI Collaboration Guide

最後更新：2026-05-25

## 1. 專案概述

- 專案：`DreamHouseTrading`
- 類型：Java Swing 桌面交易分析平台
- 語言：Java 17
- 建構：Maven
- 邊界：分析、掃描、決策、回測、模擬執行，不做真實券商下單

## 2. 核心流程

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

## 3. 開發原則

- 優先修改既有檔案。
- 只有在模組邊界真的需要時才新增檔案。
- 不新增 `*_progress.md`、`*_updated.md`、`*_plan.md` 這類臨時文件。
- 不在 UI 端重做交易語意、風控語意或訂單語意。
- `DecisionResult` 是唯一標準化決策輸出。
- Scanner 輸出應對齊 `MarketScanResult`。
- 新的執行邏輯優先使用 `ExecutionEngine.executeDecision(...)`。
- v1 維持 long-only，不要默默加入可執行放空路徑。
- 進場檢查必須經過 `RiskManager` 或等價核心風控服務。

## 4. 文檔追蹤規則

每次功能或策略語意有實質更新時，必須同步更新文件：

- 使用者操作、下載、啟動、資料源邊界改變時，更新 `README.md`。
- 架構、策略流程、功能進度、限制或驗證結果改變時，更新 `PROJECT_DOCUMENTATION.md`。
- AI 協作規則、禁止事項、資料源邊界或開發流程改變時，更新 `AGENTS.md`。

不得只修改程式而不更新進度文件，尤其是以下類型：

- 盤中資料源或 FinMind API 邊界變更。
- 自動監控交易語意變更。
- 當沖風控、停損停利、出場邏輯變更。
- 回測成交模型、成本模型、報告欄位變更。
- UI 新增主要面板、設定或使用流程變更。

## 5. Git / repo 原則

- GitHub 只保留一個正式 repo。
- `main` 是唯一長期主線。
- 新工作可在 `codex/*` 分支進行。
- 不要把交易日誌、個人本機資料或臨時輸出檔推回主線。
- 工作樹可能已有使用者改動；不要 revert 未確認的變更。

## 6. MarketDataCollector / FinMind 邊界

- 盤中雷達與盤中 K 線優先使用 `MarketDataCollectorFeed` 讀本機 `market_data` MySQL。
- DreamHouseTrading 盤中雷達不得 fallback 呼叫 FinMind 即時行情。
- FinMind 只保留低頻資料、盤後資料、新聞、分點與手動 API 查詢。
- `MarketDataCollectorFeed` 的 stale guard 是防止壞資料掃描，不是 UI 清空機制。
- `MarketDataCollectorFeed` stale warn 必須同步輸出診斷 CSV 到 `logs/market-data-warnings`，格式使用 UTF-8 with BOM，且不得提交實際日誌。
- 台股 `13:25~13:30` 是收盤集合競價，可能沒有連續 tick，不可視為 Collector 故障。
- `13:25~13:30` 仍要允許載入 09:00 起已收集的盤中 K 線。
- 台股分 K `TaiwanStockKBar` 是盤後資料，15:50 前不得當作盤中即時 K 線來源。
- 手動 `TaiwanStockKBar` SQL 補資料若支援日期範圍，必須先檢查股票日期的 SQL M1 分 K 根數、開收盤覆蓋與 D1 日線是否存在，只對不完整資料組合呼叫 FinMind API。
- 範圍分 K 匯入必須同步補 `candlesticks` 的 `D1`；D1 優先使用 FinMind `TaiwanStockPrice`，若日線資料為空才由當日 M1 分 K 聚合。
- Watchlist 成交量若來自 SQL tick，顯示最新 tick 原始 volume，不在 UI 端累加。
- Watchlist 台股漲跌幅應以指定日期前一交易日收盤價為基準；缺少昨收時才可明確 fallback，不要用圖表開盤價覆蓋。
- ChartDock live tick 成交量若是 SQL / snapshot 累積量，必須先轉差分量再更新目前 K 棒；不得把累積量逐 tick 疊加成暴量柱。

## 7. 當沖模擬交易語意

- 台股當沖模擬開單必須以一張為單位。
- `DAY_TRADE` 自動開倉數量固定 `1000` 股。
- 若現金不足買一張，走拒單流程，不拆零股、不自動降數量。
- 盤中自動監控與今日機會雷達必須強制為 `DAY_TRADE`。
- 不可讓分類器輸出的 `SWING_TRADE` 直接進入自動開倉路徑。
- `13:25` 後禁止所有自動監控 `OPEN_LONG`。
- `13:25` 後平掉所有 auto-managed 未平倉部位，不只依賴 `tradeMode == DAY_TRADE`。
- 自動監控開出的部位必須可辨識為 auto-managed。
- 自動交易記錄必須寫入 `logs/paper-trades`，包含 entry、exit、reason、mode、quantity、PnL、setup score 與策略設定摘要。

## 8. 當沖風控與策略規則

- 最大同時持倉數必須在自動開倉路徑實際生效。
- 當沖建議最大同時持倉先限制在 3 到 5 檔，每檔固定一張。
- 09:00~09:15 預設只收資料不自動開倉，時間需可由 UI 調整。
- 同一股票停損後預設冷卻 60 分鐘。
- 同一股票平倉後預設冷卻 30 分鐘。
- 13:05 後預設禁止新倉，13:25 強制平倉。
- `setup_score` 不能視為勝率保證。
- 當沖自動監控的開單資格需同時通過多頭進場門檻、雷達最低進場分數與硬阻擋條件；若要改成排序用途，必須同步更新 scanner、UI、報告與文件。
- RSI 超賣不能單獨作為開多理由。
- A/B/C 監控模板目前為當沖測試基準：A 組穩健 `EMA8/34 + 跨日暖機 + 突破後一根 K 確認`，B 組放寬 `EMA8/21`，C 組 `SMA8/21 + 量能倍數 1.45`；三組都應阻擋均線單因子進場且關閉內部市場狀態過濾，避免模板比較混入不同市場閘門。
- 監控設定若新增會改變 scanner 行為的策略旗標，必須接到模板套用、自訂模板儲存與設定摘要，避免 UI 重建 config 時遺失核心規則。
- `SignalRSI = SHORT` 應阻擋自動監控 `OPEN_LONG`。
- Close <= VWAP、VWAP slope <= 0、Volume Sustain 不成立、追高超過 ATR 限制，應作為硬阻擋或明確阻擋原因。
- 三層停損停利應在 scanner / execution / backtest 語意一致：
  - 初始停損以 ATR、VWAP、近期低點等結構決定。
  - 停利依 TREND_UP / RANGE / WEAK 調整 R 倍數。
  - 持倉中若 VWAP_BREAK、VOLUME_FAIL、內部市場轉弱或相對強度失效，應能提前出場。

## 9. 當沖策略週期分工

- 日線：盤前股票池，不做盤中精準進出場。
- 5 分 K：主交易層，用於 VWAP、EMA、Volume Sustain、ATR、突破結構。
- 1 分 K：執行確認層，用於更細進場價格與提前出場。
- 回測報告必須記錄實際週期，M1 / M5 不可混在一起比較。

## 10. 市場與產業資料規則

- FinMind 無法作為穩定盤中即時大盤 / 產業資料源。
- 盤中策略不得依賴非即時的 FinMind 大盤或產業資料做硬條件。
- 目前當沖用觀察清單內部市場狀態替代即時大盤 / 產業：
  - 平均漲跌幅。
  - 站上 VWAP 比例。
  - VWAP slope > 0 比例。
  - Volume Sustain 通過比例。
  - 強勢排名與創高 / 創低家數。
- 內部市場 ALLOW / BLOCK 門檻屬於監控設定，包含 VWAP 通過比例、平均漲跌、Volume Sustain 比例與創低多於創高檔數。
- 在未接可靠即時 TAIEX / 產業 SQL 前，UI 與報告應使用「內部基準 / 觀察清單群體」描述，不要誤寫成即時大盤 / 即時族群。
- 產業鏈使用 FinMind `TaiwanStockIndustryChain` 低頻匯入 SQL 快取。
- 分點使用 FinMind `TaiwanStockTradingDailyReport` 盤後匯入，主要用於隔日股票池。

## 11. UI / 圖表效能規則

- 使用 `MarketDataCollectorFeed` 切換股票時，K 線圖應批次載入 `Bar`。
- 不要把每根 bar 拆成多筆 tick 推給 UI。
- 圖表載入速度與 K 棒寬度是兩件事；恢復較寬 K 棒應調整 renderer，不要回退到逐 tick 載入。
- 盤中日內 K 線預設載入指定日期 `09:00~13:30`。
- 表格 UI resize 時要避免欄位遮蔽文字。
- 監控設定只保留當沖有效欄位；短線 / 波段欄位停用並標示目前自動監控僅支援當沖。
- 監控設定的使用者自訂模板可刪除；內建 A/B/C 測試模板應保留，不要讓刪除動作破壞固定比較基準。
- 停靠面板佈局屬於本機使用者狀態，儲存在專案根目錄的 `config/ui-layout.xml`；不得提交該檔案，重設佈局應刪除本機儲存檔並還原預設 Docking 版面。佈局還原需在主視窗開啟後延遲執行；若完整 Docking XML 還原失敗，仍需依儲存檔內的 dockable ID 強制套用可見面板清單，避免未儲存面板在重開後回到預設顯示。讀寫診斷寫入 `logs/ui-layout/ui_layout.log`。

## 12. 回測與報告規則

- SQL 雷達回測必須避免 Lookahead Bias。
- 訊號於第 N 根 K 收完成立後，第 N+1 根 open 或下一筆 tick 才能成交。
- 同一根 K 同時碰停利與停損時，保守先算停損。
- 成本模型要拆分 grossProfit、commission、tax、slippageCost、netProfit。
- 批次回測不得只輸出泛用 `No entry signal`；要保留 EMA、RSI、量能、投票或硬阻擋等可統計未進場原因。
- 回測進場前若停利目標扣除手續費、當沖稅與滑價後沒有正淨利，必須以「成本後停利空間不足」硬阻擋。
- 慢速 EMA 的跨日暖機資料只可供指定交易日指標初始化，不可把暖機日交易混入指定日期績效。
- SQL 單檔回測、批次 / 日期範圍回測與 SQL 雷達開盤重播若支援跨日暖機，輸出必須記錄暖機診斷欄位：是否啟用、要求根數、實際載入根數、第一根與最後一根暖機時間。
- 盤中自動監控不得使用 `selectedQueryDate` 當作實盤掃描日期；實盤 scanner 必須固定使用今日 SQL 資料，UI 歷史載入日期只影響歷史查詢 / 回測 / 重播。
- 盤中自動監控若啟用跨日暖機，scanner 可讀前期 K 線初始化指標；內部市場 context 不應用 session-only bars 覆蓋 scanner 的暖機資料。
- SQL 雷達 replay 與盤中自動監控必須共用早盤禁開倉、尾盤禁止新倉、停損冷卻、日損 / 連敗熔斷、每日最多交易、開單間隔、同一根 5 分 K 只允許一筆等節奏規則；時間判斷以訊號成立時間為準，不要用 K 棒起始時間誤擋。
- SQL 雷達 replay 不得在回放迴圈中污染原始 `ScanRequest`；監控設定的 K 棒數必須固定保留，當下可見資料應由 rolling feed 限制，scanner 必須從 replay feed 取 K 線，不得用 internal market context bars 覆蓋 scanner bars，避免 EMA / Volume Sustain / VWAP 和盤中監控不同。
- SQL 雷達 replay 若監控設定啟用跨日暖機，scanner 可讀暖機 K 線做指標初始化，但圖表與指定日期績效不可混入暖機日交易。
- SQL 雷達 replay 與盤中自動監控掃描必須在 `ScanRequest.asOfTime` 帶入實際掃描時間；若 replay 有 SQL ticks，必須用 replay time 以前的 ticks 重新聚合當下 partial K 棒，不可直接使用盤後完整 M5 bar，避免 replay 在 09:30:20 看見 09:30~09:34:59 的未來資料。若只能使用 candlesticks，才退回只使用已收完 K 線。
- SQL 雷達開盤重播是 UI 驗證模式，只能讀本機 SQL 並使用模擬時間逐步更新雷達 / 圖表 / 執行狀態重播紀錄；不得呼叫 FinMind 即時 API，不得寫入正式 paper trade CSV。
- SQL 雷達開盤重播的診斷 CSV 應寫入 `logs/radar-replay`，可記錄每步掃描、OPEN_LONG、阻擋與交易事件；這是除錯輸出，不等同正式交易紀錄。
- SQL 雷達開盤重播摘要中的 `trade_events` 必須由逐步 scanner 結果即時計算，不得混用預先批次 N+1 open 交易清單；重播成交需套用最大持倉、每日最多交易、開單間隔、同一根 5 分 K 限制與 replay 時間風控。逐檔候選與阻擋仍以 `_symbols.csv` 明細為準。
- SQL 雷達開盤重播若用來比對實盤自動監控，必須支援掃描間隔與掃描秒偏移；可從同日 `orders_yyyyMMdd.csv` 的 auto-monitor 事件推估排程偏移。重播成交 / 出場價格應優先使用 SQL ticks 中模擬時間以前的最新價，不可只用 M5 bar close，否則與盤中即時平倉會系統性不一致。
- SQL 雷達開盤重播的 `TICKS_AGGREGATED` 模式必須依 replay time 對 full-day ticks 做切片聚合，讓 scanner 輸入接近盤中 MarketDataCollector 當下可見資料；不得把 full-day ticks 先聚成完整 K 棒後再用 K 棒起始時間判斷可見性。
- SQL 雷達開盤重播的診斷 CSV 必須使用 UTF-8 with BOM；若舊檔沒有 BOM，寫入前要先補 BOM，避免 Excel 直接開啟時中文阻擋原因亂碼。
- SQL 雷達批次回測不能只把每檔獨立 replay 結果全部相加；合併時仍要套用跨股票的全局開單節奏與最大持倉限制，否則會和盤中自動監控開單數嚴重不一致。
- 台股當沖證交稅必須納入模擬交易與回測。
- 報告需記錄阻擋原因、進場理由、出場理由、主要技術指標與雷達加分明細。
- 雷達加分明細至少要保留策略名稱、訊號方向、信心度、權重、LONG 分數貢獻與原因，並同步輸出到模擬交易 CSV 與 SQL 雷達回測匯出。
- SQL 雷達回測匯出必須保留加分條件貢獻統計與硬阻擋後續統計，用後續最大漲幅、最大回撤、收盤報酬驗證條件有效性。
- 任何預期用 Excel 開啟的 CSV 報告必須用 UTF-8 with BOM 寫出；Java 原始碼內的中文欄位名稱必須保持真正 Unicode 中文，不得提交 mojibake 字串。
- 若 Windows 終端顯示 CSV 或 Java 中文為亂碼，不可直接判定檔案壞掉；應用實際 Excel 開檔、UTF-8 編碼讀取或 codepoint 檢查確認。

## 13. 主要檔案

- `README.md`
- `PROJECT_DOCUMENTATION.md`
- `pom.xml`
- `src/main/java/com/dreamhouse/trading/core/decision`
- `src/main/java/com/dreamhouse/trading/core/execution`
- `src/main/java/com/dreamhouse/trading/core/backtest`
- `src/main/java/com/dreamhouse/trading/core/scanner`
- `src/main/java/com/dreamhouse/trading/core/stockpool`
- `src/main/java/com/dreamhouse/trading/ui`

## 14. 驗證命令

編譯：

```bash
mvn -q -DskipTests compile
```

測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

注意：

- 這台機器上的預設 `mvn test` 可能被 JaCoCo coverage 檔權限問題卡住。
- 若只是確認功能，先用 `-Djacoco.skip=true`。

## 15. 後續優先序

1. 用多日 SQL 回測驗證三層停損停利。
2. 補條件貢獻分析與被阻擋訊號後續表現。
3. 持續校準 B 組收斂版與三個當沖模板。
4. 改善分點資料匯入與 SQL 診斷。
5. 等資料與回測都穩定後，再評估 ML。

## 16. 2026-05-27 更新規範

- SQL 雷達重播必須支援明確資料來源模式：`TICKS_AGGREGATED`、`CANDLES_ONLY`、`AUTO`。
- 比對盤中實盤雷達時，預設使用 `TICKS_AGGREGATED`，因為盤中雷達看到的是 MarketDataCollector ticks 聚合結果；盤後補入 `candlesticks` 後不可讓同一設定默默改用另一組 K 線而不記錄。
- SQL 雷達重播 CSV 必須至少記錄：重播日期、週期、資料來源模式、每檔實際載入來源、策略名稱、完整設定摘要、掃描檔數、OPEN_LONG、阻擋數、交易事件、Top candidates、OPEN_LONG 候選、阻擋候選與加分明細。
- SQL 雷達重播必須同時輸出摘要 CSV 與 `_symbols.csv` 每檔明細 CSV；逐檔分析以 `_symbols.csv` 為準，且 `_symbols.csv` 必須包含 scanner bar source、當下可見 session bars 與 warmup 診斷欄位，避免把多檔股票塞進同一欄造成 Excel 閱讀錯位。
- 台股 M5 日內完整 bar start 應為 09:00 到 13:25，共 54 根；不可把 13:30 收盤時間多算成額外一根。
- ticks 聚合若少於完整根數，資料來源狀態必須標成 `TICKS_AGGREGATED_INCOMPLETE`，不可只寫 `TICKS_AGGREGATED` 讓使用者誤判資料完整。
- SQL 雷達重播 CSV 必須使用 UTF-8 with BOM，讓 Windows Excel 直接開啟不亂碼。
- 每次功能、策略語意、資料源邊界或報告欄位更新時，除了 `README.md`、`PROJECT_DOCUMENTATION.md`、`AGENTS.md`，也必須同步更新主程式「關於 DreamHouseTrading」與「當沖指標設定說明」內容。
