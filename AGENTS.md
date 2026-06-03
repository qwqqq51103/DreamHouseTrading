# DreamHouseTrading AI Collaboration Guide

最後更新：2026-06-02

## 1. 專案概述

* 專案：`DreamHouseTrading`
* 類型：Java Swing 桌面交易分析平台
* 語言：Java 17
* 建構：Maven
* 邊界：分析、掃描、決策、回測、模擬執行，不做真實券商下單

---

## 2. 核心流程

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

任何新增功能都必須確認自己位於哪一層，並檢查是否需要串接上下游鏈路。

---

## 3. 開發原則

* 優先修改既有檔案。
* 只有在模組邊界真的需要時才新增檔案。
* 不新增 `*_progress.md`、`*_updated.md`、`*_plan.md` 這類臨時文件。
* 不在 UI 端重做交易語意、風控語意或訂單語意。
* `DecisionResult` 是唯一標準化決策輸出。
* Scanner 輸出應對齊 `MarketScanResult`。
* 新的執行邏輯優先使用 `ExecutionEngine.executeDecision(...)`。
* v1 維持 long-only，不要默默加入可執行放空路徑。
* 進場檢查必須經過 `RiskManager` 或等價核心風控服務。
* 本專案禁止「局部完成式修改」；任何新增功能都必須提供 UI / Config / Scanner / Decision / Risk / Execution / Backtest / Report / 文件的鏈路檢查結果，沒有鏈路證據不得宣稱完成。
* 不得為了提高 coverage 數字而新增低價值測試或 testing-only production API。
* 不得把測試通過誤報為 coverage 通過。

---

## 4. 文檔追蹤規則

每次功能或策略語意有實質更新時，必須同步更新文件：

* 使用者操作、下載、啟動、資料源邊界改變時，更新 `README.md`。
* 架構、策略流程、功能進度、限制或驗證結果改變時，更新 `PROJECT_DOCUMENTATION.md`。
* AI 協作規則、禁止事項、資料源邊界或開發流程改變時，更新 `AGENTS.md`。
* 主程式「關於 DreamHouseTrading」內容受影響時，必須同步更新。
* 主程式「當沖指標設定說明」內容受影響時，必須同步更新。

不得只修改程式而不更新進度文件，尤其是以下類型：

* 盤中資料源或 FinMind API 邊界變更。
* 自動監控交易語意變更。
* 當沖風控、停損停利、出場邏輯變更。
* 回測成交模型、成本模型、報告欄位變更。
* UI 新增主要面板、設定或使用流程變更。
* CSV / Report 欄位新增、刪除、改名或語意改變。
* Paper Trade Record 欄位新增、刪除、改名或語意改變。
* AI 協作流程、驗證命令、測試要求改變。

---

## 5. Git / Repo 原則

* GitHub 只保留一個正式 repo。
* `main` 是唯一長期主線。
* 新工作可在 `codex/*` 分支進行。
* 不要把交易日誌、個人本機資料或臨時輸出檔推回主線。
* 工作樹可能已有使用者改動；不要 revert 未確認的變更。
* 若發現工作樹已有 dirty 狀態，必須在回報中說明，不得擅自回退。
* 不得把 `logs/`、本機 `config/ui-layout.xml`、診斷 CSV、交易紀錄等本機輸出推回主線。

---

## 6. MarketDataCollector / FinMind 邊界

* 盤中雷達與盤中 K 線優先使用 `MarketDataCollectorFeed` 讀本機 `market_data` MySQL。
* DreamHouseTrading 盤中雷達不得 fallback 呼叫 FinMind 即時行情。
* FinMind 只保留低頻資料、盤後資料、新聞、分點與手動 API 查詢。
* `MarketDataCollectorFeed` 的 stale guard 是防止壞資料掃描，不是 UI 清空機制。
* `MarketDataCollectorFeed` stale warn 必須同步輸出診斷 CSV 到 `logs/market-data-warnings`，格式使用 UTF-8 with BOM，且不得提交實際日誌。
* 台股 `13:25~13:30` 是收盤集合競價，可能沒有連續 tick，不可視為 Collector 故障。
* `13:25~13:30` 仍要允許載入 09:00 起已收集的盤中 K 線。
* 台股分 K `TaiwanStockKBar` 是盤後資料，15:50 前不得當作盤中即時 K 線來源。
* 手動 `TaiwanStockKBar` SQL 補資料若支援日期範圍，必須先檢查股票日期的 SQL M1 分 K 根數、開收盤覆蓋與 D1 日線是否存在，只對不完整資料組合呼叫 FinMind API。
* 範圍分 K 匯入必須同步補 `candlesticks` 的 `D1`；D1 優先使用 FinMind `TaiwanStockPrice`，若日線資料為空才由當日 M1 分 K 聚合。
* Watchlist 成交量若來自 SQL tick，顯示最新 tick 原始 volume，不在 UI 端累加。
* Watchlist 台股漲跌幅應以指定日期前一交易日收盤價為基準；缺少昨收時才可明確 fallback，不要用圖表開盤價覆蓋。
* ChartDock live tick 成交量若是 SQL / snapshot 累積量，必須先轉差分量再更新目前 K 棒；不得把累積量逐 tick 疊加成暴量柱。

---

## 7. 當沖模擬交易語意

* 台股當沖模擬開單必須以一張為單位。
* `DAY_TRADE` 自動開倉數量固定 `1000` 股。
* 若現金不足買一張，走拒單流程，不拆零股、不自動降數量。
* 盤中自動監控與今日機會雷達必須強制為 `DAY_TRADE`。
* 不可讓分類器輸出的 `SWING_TRADE` 直接進入自動開倉路徑。
* `13:25` 後禁止所有自動監控 `OPEN_LONG`。
* `13:25` 後平掉所有 auto-managed 未平倉部位，不只依賴 `tradeMode == DAY_TRADE`。
* 自動監控開出的部位必須可辨識為 auto-managed。
* 自動交易記錄必須寫入 `logs/paper-trades`。
* 自動交易記錄必須包含 entry、exit、reason、mode、quantity、PnL、setup score、策略設定摘要、DecisionSource、auto-managed flag。
* 不得新增真實券商下單路徑。
* 不得在未明確要求的情況下加入可執行放空。

---

## 8. 當沖風控與策略規則

* 最大同時持倉數必須在自動開倉路徑實際生效。
* 當沖建議最大同時持倉先限制在 3 到 5 檔，每檔固定一張。
* 09:00~09:15 預設只收資料不自動開倉，時間需可由 UI 調整。
* 同一股票停損後預設冷卻 60 分鐘。
* 同一股票平倉後預設冷卻 30 分鐘。
* 13:05 後預設禁止新倉，13:25 強制平倉。
* `setup_score` 不能視為勝率保證。
* 當沖自動監控的開單資格需同時通過多頭進場門檻、雷達最低進場分數與硬阻擋條件；若要改成排序用途，必須同步更新 scanner、UI、報告與文件。
* RSI 超賣不能單獨作為開多理由。
* A/B/C 監控模板目前為當沖測試基準：

  * A 組穩健：`EMA8/34 + 跨日暖機 + 突破後一根 K 確認`
  * B 組放寬：`EMA8/21`
  * C 組：`SMA8/21 + 量能倍數 1.45`
* A/B/C 三組都應阻擋均線單因子進場，且關閉內部市場狀態過濾，避免模板比較混入不同市場閘門。
* 監控設定若新增會改變 scanner 行為的策略旗標，必須接到模板套用、自訂模板儲存與設定摘要，避免 UI 重建 config 時遺失核心規則。
* `SignalRSI = SHORT` 應阻擋自動監控 `OPEN_LONG`。
* Close <= VWAP、VWAP slope <= 0、Volume Sustain 不成立、追高超過 ATR 限制，應作為硬阻擋或明確阻擋原因。
* 三層停損停利應在 scanner / execution / backtest 語意一致：

  * 初始停損以 ATR、VWAP、近期低點等結構決定。
  * 停利依 TREND_UP / RANGE / WEAK 調整 R 倍數。
  * 持倉中若 VWAP_BREAK、VOLUME_FAIL、內部市場轉弱或相對強度失效，應能提前出場。

---

## 9. 當沖策略週期分工

* 日線：盤前股票池，不做盤中精準進出場。
* 5 分 K：主交易層，用於 VWAP、EMA、Volume Sustain、ATR、突破結構。
* 1 分 K：執行確認層，用於更細進場價格與提前出場。
* 回測報告必須記錄實際週期，M1 / M5 不可混在一起比較。
* 若 M1 / M5 結果要比較，報告必須明確標示 timeframe 與資料來源。
* 回測、Replay、Auto Monitor 不得因 timeframe 不同而改變核心風控語意。

---

## 10. 市場與產業資料規則

* FinMind 無法作為穩定盤中即時大盤 / 產業資料源。
* 盤中策略不得依賴非即時的 FinMind 大盤或產業資料做硬條件。
* 目前當沖用觀察清單內部市場狀態替代即時大盤 / 產業：

  * 平均漲跌幅。
  * 站上 VWAP 比例。
  * VWAP slope > 0 比例。
  * Volume Sustain 通過比例。
  * 強勢排名與創高 / 創低家數。
* 內部市場 ALLOW / BLOCK 門檻屬於監控設定，包含 VWAP 通過比例、平均漲跌、Volume Sustain 比例與創低多於創高檔數。
* 在未接可靠即時 TAIEX / 產業 SQL 前，UI 與報告應使用「內部基準 / 觀察清單群體」描述，不要誤寫成即時大盤 / 即時族群。
* 產業鏈使用 FinMind `TaiwanStockIndustryChain` 低頻匯入 SQL 快取。
* 分點使用 FinMind `TaiwanStockTradingDailyReport` 盤後匯入，主要用於隔日股票池。

---

## 11. UI / 圖表效能規則

* 使用 `MarketDataCollectorFeed` 切換股票時，K 線圖應批次載入 `Bar`。
* 不要把每根 bar 拆成多筆 tick 推給 UI。
* 圖表載入速度與 K 棒寬度是兩件事；恢復較寬 K 棒應調整 renderer，不要回退到逐 tick 載入。
* 盤中日內 K 線預設載入指定日期 `09:00~13:30`。
* 表格 UI resize 時要避免欄位遮蔽文字。
* 監控設定只保留當沖有效欄位；短線 / 波段欄位停用並標示目前自動監控僅支援當沖。
* 監控設定的使用者自訂模板可刪除；內建 A/B/C 測試模板應保留，不要讓刪除動作破壞固定比較基準。
* 停靠面板佈局屬於本機使用者狀態，儲存在專案根目錄的 `config/ui-layout.xml`；不得提交該檔案。
* 重設佈局應刪除本機儲存檔並還原預設 Docking 版面。
* 佈局還原需在主視窗開啟後延遲執行。
* 若完整 Docking XML 還原失敗，仍需依儲存檔內的 dockable ID 強制套用可見面板清單，避免未儲存面板在重開後回到預設顯示。
* 讀寫診斷寫入 `logs/ui-layout/ui_layout.log`。
* UI 可以顯示阻擋原因，但不得成為核心交易語意來源。
* UI 可以保留鏡射狀態，但不得取代 `RiskManager`、`ExecutionEngine`、`DecisionResult` 等核心狀態。

### RealtimeBarBuilder 規則

- SQL / snapshot 累積 volume 必須轉成 delta volume 後再更新 K 棒。
- 不得把累積量逐 tick 疊加成暴量柱。
- volume 倒退、歸零或來源切換時，不得產生負成交量。
- 台股 M5 日內完整 bar start 應為 09:00 到 13:25，共 54 根。
- 13:30 tick 不得額外產生 13:30 M5 bar。

---

## 12. 回測與報告規則

* SQL 雷達回測必須避免 Lookahead Bias。
* 訊號於第 N 根 K 收完成立後，第 N+1 根 open 或下一筆 tick 才能成交。
* 同一根 K 同時碰停利與停損時，保守先算停損。
* 成本模型要拆分 grossProfit、commission、tax、slippageCost、netProfit。
* 批次回測不得只輸出泛用 `No entry signal`；要保留 EMA、RSI、量能、投票或硬阻擋等可統計未進場原因。
* 回測進場前若停利目標扣除手續費、當沖稅與滑價後沒有正淨利，必須以「成本後停利空間不足」硬阻擋。
* 慢速 EMA 的跨日暖機資料只可供指定交易日指標初始化，不可把暖機日交易混入指定日期績效。
* SQL 單檔回測、批次 / 日期範圍回測與 SQL 雷達開盤重播若支援跨日暖機，輸出必須記錄暖機診斷欄位：是否啟用、要求根數、實際載入根數、第一根與最後一根暖機時間。
* 盤中自動監控不得使用 `selectedQueryDate` 當作實盤掃描日期；實盤 scanner 必須固定使用今日 SQL 資料，UI 歷史載入日期只影響歷史查詢 / 回測 / 重播。
* 盤中自動監控若啟用跨日暖機，scanner 可讀前期 K 線初始化指標；內部市場 context 不應用 session-only bars 覆蓋 scanner 的暖機資料。
* SQL 雷達 replay 與盤中自動監控必須共用早盤禁開倉、尾盤禁止新倉、停損冷卻、日損 / 連敗熔斷、每日最多交易、開單間隔、同一根 5 分 K 只允許一筆等節奏規則；時間判斷以訊號成立時間為準，不要用 K 棒起始時間誤擋。
* SQL 雷達 replay 不得在回放迴圈中污染原始 `ScanRequest`；監控設定的 K 棒數必須固定保留，當下可見資料應由 rolling feed 限制，scanner 必須從 replay feed 取 K 線，不得用 internal market context bars 覆蓋 scanner bars，避免 EMA / Volume Sustain / VWAP 和盤中監控不同。
* SQL 雷達 replay 若監控設定啟用跨日暖機，scanner 可讀暖機 K 線做指標初始化，但圖表與指定日期績效不可混入暖機日交易。
* SQL 雷達 replay 與盤中自動監控掃描必須在 `ScanRequest.asOfTime` 帶入實際掃描時間。
* 若 replay 有 SQL ticks，必須用 replay time 以前的 ticks 重新聚合當下 partial K 棒，不可直接使用盤後完整 M5 bar，避免 replay 在 09:30:20 看見 09:30~09:34:59 的未來資料。
* 若只能使用 candlesticks，才退回只使用已收完 K 線。
* SQL 雷達開盤重播是 UI 驗證模式，只能讀本機 SQL 並使用模擬時間逐步更新雷達 / 圖表 / 執行狀態重播紀錄；不得呼叫 FinMind 即時 API，不得寫入正式 paper trade CSV。
* SQL 雷達開盤重播的診斷 CSV 應寫入 `logs/radar-replay`，可記錄每步掃描、OPEN_LONG、阻擋與交易事件；這是除錯輸出，不等同正式交易紀錄。
* SQL 雷達開盤重播摘要中的 `trade_events` 必須由逐步 scanner 結果即時計算，不得混用預先批次 N+1 open 交易清單。
* 重播成交需套用最大持倉、每日最多交易、開單間隔、同一根 5 分 K 限制與 replay 時間風控。
* 逐檔候選與阻擋仍以 `_symbols.csv` 明細為準。
* SQL 雷達開盤重播若用來比對實盤自動監控，必須支援掃描間隔與掃描秒偏移；可從同日 `orders_yyyyMMdd.csv` 的 auto-monitor 事件推估排程偏移。
* 重播成交 / 出場價格應優先使用 SQL ticks 中模擬時間以前的最新價，不可只用 M5 bar close，否則與盤中即時平倉會系統性不一致。
* SQL 雷達開盤重播的 `TICKS_AGGREGATED` 模式必須依 replay time 對 full-day ticks 做切片聚合，讓 scanner 輸入接近盤中 MarketDataCollector 當下可見資料；不得把 full-day ticks 先聚成完整 K 棒後再用 K 棒起始時間判斷可見性。
* SQL 雷達開盤重播的診斷 CSV 必須使用 UTF-8 with BOM；若舊檔沒有 BOM，寫入前要先補 BOM，避免 Excel 直接開啟時中文阻擋原因亂碼。
* SQL 雷達批次回測不能只把每檔獨立 replay 結果全部相加；合併時仍要套用跨股票的全局開單節奏與最大持倉限制，否則會和盤中自動監控開單數嚴重不一致。
* 台股當沖證交稅必須納入模擬交易與回測。
* 報告需記錄阻擋原因、進場理由、出場理由、主要技術指標與雷達加分明細。
* 雷達加分明細至少要保留策略名稱、訊號方向、信心度、權重、LONG 分數貢獻與原因，並同步輸出到模擬交易 CSV 與 SQL 雷達回測匯出。
* SQL 雷達回測匯出必須保留加分條件貢獻統計與硬阻擋後續統計，用後續最大漲幅、最大回撤、收盤報酬驗證條件有效性。
* 任何預期用 Excel 開啟的 CSV 報告必須用 UTF-8 with BOM 寫出；Java 原始碼內的中文欄位名稱必須保持真正 Unicode 中文，不得提交 mojibake 字串。
* 若 Windows 終端顯示 CSV 或 Java 中文為亂碼，不可直接判定檔案壞掉；應用實際 Excel 開檔、UTF-8 編碼讀取或 codepoint 檢查確認。

---

## 13. 主要檔案

* `README.md`
* `PROJECT_DOCUMENTATION.md`
* `AGENTS.md`
* `pom.xml`
* `src/main/java/com/dreamhouse/trading/core/decision`
* `src/main/java/com/dreamhouse/trading/core/execution`
* `src/main/java/com/dreamhouse/trading/core/backtest`
* `src/main/java/com/dreamhouse/trading/core/scanner`
* `src/main/java/com/dreamhouse/trading/core/stockpool`
* `src/main/java/com/dreamhouse/trading/core/monitor`
* `src/main/java/com/dreamhouse/trading/core/logging`
* `src/main/java/com/dreamhouse/trading/ui`
* `src/test/java`

---

## 14. 驗證命令

本專案每次修改後至少必須執行編譯。若修改核心交易邏輯、Scanner、Risk、Execution、Backtest、Replay、CSV、設定模板或 UI 主要流程，必須執行測試與覆蓋率檢查。

### 14.1 快速編譯

```bash
mvn -q -DskipTests compile
```

用途：

* 確認 Java 語法、import、method signature、class reference 正常。
* 不代表功能正確。
* 不代表測試通過。
* 不代表功能已接到完整鏈路。

### 14.2 快速測試

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

用途：

* 在本機 JaCoCo 可能有權限問題時，先確認 unit tests 可通過。
* 這是最低測試，不可取代覆蓋率檢查。

### 14.3 覆蓋率測試

若環境允許，必須執行：

```bash
cmd /c "mvn -q test"
```

或：

```bash
cmd /c "mvn -q clean test jacoco:report"
```

若因本機 JaCoCo 權限問題無法執行，必須明確回報：

```text
coverage 未執行原因：本機 JaCoCo coverage 檔案權限問題。
已改用：mvn -q -Djacoco.skip=true test
風險：只能確認測試通過，無法確認覆蓋率。
```

不得把 `-Djacoco.skip=true` 的測試結果宣稱為 coverage 通過。

### 14.4 覆蓋率最低要求

核心交易邏輯新增或修改時，必須優先補以下測試：

* Scanner 條件成立案例。
* Scanner 條件阻擋案例。
* `DecisionResult` 輸出案例。
* `RiskManager` 拒單 / 硬阻擋案例。
* `ExecutionEngine.executeDecision(...)` 接收到正確決策案例。
* Replay / Backtest 與 Auto Monitor 語意一致案例。
* CSV / Report 欄位輸出案例。
* Paper Trade Record 欄位輸出案例。

覆蓋率目標：

* 核心交易語意 class：建議 line coverage 不低於 70%。
* 新增核心 class：建議 line coverage 不低於 80%。
* UI class 不強制覆蓋率，但 UI 對 Config / Template / Core 的串接必須有測試或手動驗證紀錄。
* 只修改文字、文件、註解時，可不要求 coverage。

### 14.5 測試不足時的處理方式

若時間不足或現有架構不易測試，不可假裝測試完整。必須在回報中列出：

```text
測試不足區域：
- ...

目前已驗證：
- ...

仍需補測：
- ...

可能風險：
- ...
```

### 14.6 新功能測試命名規則

新增測試應優先使用可讀性高的命名：

```text
shouldOpenLongWhenAllDayTradeConditionsPass
shouldBlockOpenLongWhenRsiIsShort
shouldBlockOpenLongAfter1325
shouldUseNextBarOpenToAvoidLookaheadBias
shouldExportBlockReasonToReplayCsv
```

測試名稱必須能看出：

* 測什麼條件。
* 預期通過或阻擋。
* 是否涉及交易語意、時間語意、資料源語意或報告輸出。

---

## 15. 大型專案防漏實作規則

本專案屬於大型 Java Swing 交易分析平台。AI 或 Codex 在新增、修改、重構任何功能時，不得只完成單一 class 或單一方法，必須完成「功能鏈路」檢查。

### 15.1 功能鏈路定義

每個新增功能都必須確認是否需要串接以下鏈路：

1. `UI / 使用者操作入口`
2. `設定物件 / Template / Config`
3. `Scanner / Signal Generation`
4. `DecisionResult / Decision & Risk`
5. `RiskManager / 硬阻擋 / 熔斷 / 冷卻`
6. `ExecutionEngine.executeDecision(...)`
7. `Position / Order / Trade record`
8. `Backtest / Replay`
9. `CSV / Report Export`
10. `README.md / PROJECT_DOCUMENTATION.md / AGENTS.md`
11. 主程式「關於 DreamHouseTrading」
12. 主程式「當沖指標設定說明」

若某一層不需要修改，必須在回報中明確寫出：

```text
未修改原因：本功能不影響 XXX，因為 XXX。
```

不得只因為沒有編譯錯誤就視為功能完成。

### 15.2 新功能最低完成定義

新增功能只有在同時符合以下條件時，才可視為完成：

* UI 有入口，或明確說明此功能為純核心功能，暫無 UI。
* Config / Template 可保存並可重建，不會因 UI 重開或模板套用而遺失。
* Scanner / Decision / Risk / Execution 的交易語意一致。
* Replay / Backtest 與盤中自動監控使用同一套核心規則。
* Report / CSV 有輸出足夠診斷欄位。
* Paper Trade Record 可追溯交易來源、模式、數量、原因與損益。
* 測試至少覆蓋核心成功案例與至少一個阻擋案例。
* 編譯與測試命令已執行。
* 若改變使用方式、策略語意、資料源、報告欄位，文件已同步更新。

### 15.3 防止「寫了但沒用到」規則

AI 每次新增 class、method、config 欄位、enum、service 時，必須檢查是否真的被主流程使用。

必須執行或人工檢查：

```bash
grep -R "新增的類別或方法名稱" src/main/java
grep -R "新增的設定欄位名稱" src/main/java
grep -R "新增的 enum 名稱" src/main/java
```

回報中必須列出：

```text
新增項目：
- XXX

實際呼叫位置：
- src/main/java/.../AAA.java
- src/main/java/.../BBB.java

若沒有呼叫位置，原因：
- XXX 是預留 API / 測試輔助 / DTO / framework 自動載入。
```

禁止新增「沒有被呼叫、沒有測試、沒有文件說明」的核心交易邏輯。

### 15.4 禁止 testing-only production API

不得為了讓測試通過，而在 `src/main/java` 新增只被 `src/test/java` 呼叫的核心交易 API。

若 production API 目前只有測試呼叫，必須符合以下其中一項：

1. 它是明確的 DTO / getter / framework 需求。
2. 它已列入正式接入計畫，並在回報中說明接入位置。
3. 它是為了解耦巨大 class 的過渡 seam，且有後續移除或正式化計畫。

否則不得宣稱功能鏈路完成。

### 15.5 修改既有功能時的反向追蹤

修改既有功能前，必須先追蹤目前呼叫鏈：

```text
入口：
UI / Scheduler / Replay / Backtest / Manual Action

往下流向：
UI -> Config -> Scanner -> DecisionResult -> RiskManager -> ExecutionEngine -> Report
```

修改後必須再次確認：

* 原入口仍可正常使用。
* 新邏輯沒有繞過 `RiskManager`。
* 新邏輯沒有繞過 `DecisionResult`。
* 新邏輯沒有在 UI 端重寫交易語意。
* 新邏輯沒有造成 replay / backtest / auto-monitor 語意分裂。
* 新增欄位不會在模板套用或設定儲存後遺失。
* 舊有 CSV 欄位仍保留，若有新增欄位需文件化。
* 新增測試不是只測未接正式流程的 helper。

### 15.6 AI 修改回報格式

每次修改後，AI 必須用以下格式回報：

```text
【本次修改目標】
- ...

【修改檔案】
- ...

【功能鏈路檢查】
- UI：已接 / 不需接，原因...
- Config / Template：已接 / 不需接，原因...
- Scanner：已接 / 不需接，原因...
- DecisionResult：已接 / 不需接，原因...
- RiskManager：已接 / 不需接，原因...
- ExecutionEngine：已接 / 不需接，原因...
- Backtest / Replay：已接 / 不需接，原因...
- CSV / Report：已接 / 不需接，原因...
- Paper Trade Record：已接 / 不需接，原因...
- 文件：已更新 / 不需更新，原因...

【防漏檢查】
- 新增 class / method 是否有呼叫位置：是 / 否
- 新增 config 是否會保存與載入：是 / 否 / 不適用
- 新增 UI 欄位是否會套用到核心設定：是 / 否 / 不適用
- 新增策略條件是否會出現在報告或阻擋原因：是 / 否 / 不適用
- 是否新增 testing-only production API：是 / 否
- 是否仍有 reason 字串被當作機器判斷：是 / 否

【測試與驗證】
- compile：通過 / 未執行，原因...
- unit test：通過 / 未執行，原因...
- coverage：通過 / 未執行，原因...
- 手動驗證：...

【風險】
- ...
```

禁止只回覆「已完成」、「已修正」、「測試通過」而沒有列出證據。

---

## 16. SQL 雷達重播資料來源規範

* SQL 雷達重播必須支援明確資料來源模式：`TICKS_AGGREGATED`、`CANDLES_ONLY`、`AUTO`。
* 比對盤中實盤雷達時，預設使用 `TICKS_AGGREGATED`，因為盤中雷達看到的是 MarketDataCollector ticks 聚合結果；盤後補入 `candlesticks` 後不可讓同一設定默默改用另一組 K 線而不記錄。
* SQL 雷達重播 CSV 必須至少記錄：重播日期、週期、資料來源模式、每檔實際載入來源、策略名稱、完整設定摘要、掃描檔數、OPEN_LONG、阻擋數、交易事件、Top candidates、OPEN_LONG 候選、阻擋候選與加分明細。
* SQL 雷達重播必須同時輸出摘要 CSV 與 `_symbols.csv` 每檔明細 CSV。
* 逐檔分析以 `_symbols.csv` 為準。
* `_symbols.csv` 必須包含 scanner bar source、當下可見 session bars 與 warmup 診斷欄位，避免把多檔股票塞進同一欄造成 Excel 閱讀錯位。
* 台股 M5 日內完整 bar start 應為 09:00 到 13:25，共 54 根；不可把 13:30 收盤時間多算成額外一根。
* ticks 聚合若少於完整根數，資料來源狀態必須標成 `TICKS_AGGREGATED_INCOMPLETE`，不可只寫 `TICKS_AGGREGATED` 讓使用者誤判資料完整。
* SQL 雷達重播 CSV 必須使用 UTF-8 with BOM，讓 Windows Excel 直接開啟不亂碼。
* 每次功能、策略語意、資料源邊界或報告欄位更新時，除了 `README.md`、`PROJECT_DOCUMENTATION.md`、`AGENTS.md`，也必須同步更新主程式「關於 DreamHouseTrading」與「當沖指標設定說明」內容。

---

## 17. 常見漏接點與強制檢查清單

以下是本專案最容易發生「新增功能但沒有真的生效」的地方。AI 每次修改相關功能時必須逐項檢查。

### 17.1 UI 設定漏接

新增監控設定、策略參數、風控參數時，必須檢查：

* UI 欄位是否存在。
* UI 欄位是否能載入既有設定。
* UI 欄位修改後是否寫回 config。
* 套用內建 A/B/C 模板後是否保留或正確覆蓋。
* 自訂模板儲存後是否能再次載入。
* 設定摘要是否有顯示該參數。
* Replay / Backtest 是否也使用同一個參數。

### 17.2 Scanner 條件漏接

新增 scanner 條件時，必須檢查：

* 條件是否真的參與 `MarketScanResult`。
* 通過原因是否有記錄。
* 阻擋原因是否有記錄。
* 分數貢獻是否有記錄。
* CSV 是否輸出該條件。
* Replay `_symbols.csv` 是否能看到該條件。
* Backtest 統計是否能統計該條件。
* UI 是否能看到該條件造成的結果。

### 17.3 RiskManager 漏接

新增任何會影響進場、出場、持倉、冷卻、熔斷、成本、數量的功能時，必須檢查：

* 是否經過 `RiskManager` 或等價核心風控服務。
* 是否沒有在 UI 端直接決定可不可下單。
* 是否沒有在 scanner 端直接繞過風控。
* 是否有拒單原因。
* 是否有測試拒單案例。
* 是否同步到 replay / backtest。
* 是否同步到 auto-monitor 正式流程。

### 17.4 ExecutionEngine 漏接

新增可執行交易語意時，必須檢查：

* 是否透過 `ExecutionEngine.executeDecision(...)`。
* 是否保留 `DecisionResult` 作為標準輸入。
* 是否記錄 entry、exit、reason、mode、quantity、PnL。
* 是否能辨識 auto-managed position。
* 是否有 paper trade CSV 紀錄。
* 是否不會默默加入 short execution path。

### 17.5 Replay / Backtest 漏接

新增盤中監控規則時，必須檢查 replay / backtest 是否同步：

* 早盤禁開倉。
* 尾盤禁止新倉。
* 13:25 強制平倉。
* 停損冷卻。
* 平倉冷卻。
* 每日最多交易。
* 最大同時持倉。
* 同一根 5 分 K 只允許一筆。
* 成本、稅、滑價。
* N+1 open / 下一筆 tick 成交，避免 Lookahead Bias。

### 17.6 CSV / Report 漏接

新增任何條件、分數、阻擋、出場、成本、資料源模式時，必須檢查 CSV / Report：

* 摘要 CSV 是否有欄位。
* `_symbols.csv` 是否有逐檔欄位。
* 中文欄位是否為 UTF-8 with BOM。
* Excel 開啟不亂碼。
* 阻擋原因不可只寫 `No entry signal`。
* 必須能看出為什麼沒進場、為什麼進場、為什麼出場。

### 17.7 Paper Trade Record 漏接

新增交易執行或平倉語意時，必須檢查：

* Paper Trade CSV 是否寫入。
* 是否記錄 `DecisionSource`。
* 是否記錄 auto-managed flag。
* 是否記錄 mode / quantity / PnL。
* 是否記錄 entry reason / exit reason。
* 是否記錄成本拆分。
* 是否記錄策略設定摘要。
* 是否使用 UTF-8 with BOM。

### 17.8 文件漏接

只要改變以下內容，必須同步更新文件：

* 使用者操作方式。
* 交易語意。
* 風控語意。
* 資料源邊界。
* 回測成交模型。
* 成本模型。
* CSV / Report 欄位。
* Paper Trade Record 欄位。
* UI 主要流程。
* AI 協作規則。

至少檢查：

* `README.md`
* `PROJECT_DOCUMENTATION.md`
* `AGENTS.md`
* 主程式「關於 DreamHouseTrading」
* 主程式「當沖指標設定說明」

---

## 18. 測試覆蓋率提升規則

本專案提升測試覆蓋率時，必須先保護交易語意，不得只為了提高 coverage 數字而補低價值測試。

### 18.1 測試優先順序

P0 優先補以下核心交易流程：

1. `RadarReplayBacktestService`

   * 避免 Lookahead Bias。
   * 訊號第 N 根 K 成立後，只能在第 N+1 根 open 或下一筆 tick 成交。
   * 同一根 K 同時碰停利與停損時，保守先算停損。
   * 13:25 強制平倉。
   * 成本後停利空間不足時不得進場。
   * 全局最大持倉與每日交易限制必須生效。

2. `BacktestEngine`

   * `runBacktest()`
   * `buy()`
   * `sell()`
   * `buyWithStops()`
   * `sellWithReason()`

3. `RiskManager`

   * 當沖固定一張。
   * 現金不足不得拆零股。
   * 早盤禁開倉。
   * 尾盤禁止新倉。
   * 停損冷卻。
   * 平倉冷卻。
   * 日損熔斷。
   * 連敗熔斷。
   * 成本後報酬不足阻擋。

4. `ExecutionEngine.executeDecision(...)`

   * 不得繞過 `DecisionResult`。
   * 不得繞過 `RiskManager`。
   * 現金不足必須拒單。
   * 自動監控部位必須標記為 auto-managed。
   * 交易紀錄必須能寫入 paper trade log。

5. `ReportGenerator / BacktestResult / TradeStatisticsAnalyzer`

   * 報告必須保留 grossProfit。
   * 報告必須保留 commission。
   * 報告必須保留 tax。
   * 報告必須保留 slippageCost。
   * 報告必須保留 netProfit。
   * 報告必須保留阻擋原因。
   * 報告必須保留進場理由。
   * 報告必須保留出場理由。
   * 報告必須保留雷達加分明細。

6. `PaperTradeRecorder`

   * 必須記錄 `DecisionSource`。
   * 必須記錄 auto-managed flag。
   * 必須記錄 mode / quantity / PnL。
   * 必須記錄 entry reason / exit reason。
   * 必須使用 UTF-8 with BOM。

P1 補以下資料源與聚合邏輯：

1. `MarketDataCollectorFeed.fetchSessionBarsWithSource(...)`

   * `TICKS_AGGREGATED`
   * `CANDLES_ONLY`
   * `AUTO`
   * `TICKS_AGGREGATED_INCOMPLETE`
   * ticks / candles fallback

2. `RealtimeBarBuilder`

   * snapshot 累積量必須轉差分。
   * 不得把累積量逐 tick 疊加成暴量柱。

3. `TimeframeAggregator`

   * M1 轉 M5。
   * D1 聚合。
   * W1 聚合。
   * 台股 M5 日內完整 bar start 應為 09:00 到 13:25，共 54 根。

P2 補以下設定與 UI 鏈路：

1. `RadarStrategyConfig`
2. `SignalMonitorConfig`
3. `MainFrameWithDocking` 監控模板儲存 / 載入
4. A/B/C 內建模板保護
5. 自訂模板 round-trip
6. 設定摘要是否完整顯示 scanner 行為旗標

P2 template / config round-trip 必須保留以下正式交易語意：

- A/B/C 內建模板是受保護測試基準，自訂模板儲存 / 刪除不得覆蓋或刪除內建模板。
- 模板套用、自訂模板 save/load、replay request 建立與 auto-monitor request 建立，必須共用核心 `SignalMonitorTemplateManager` / config copy 路徑；不得把正式模板序列化藏在 Swing-only private helper。
- `RadarStrategyConfig` round-trip 必須保留 VWAP、VWAP slope、Volume Sustain、ATR 追價、RSI SHORT / overbought 阻擋、market context、breakout confirmation、MA type / periods、timeframe、bar count、cross-day warmup、min score 與 quality threshold。
- `SignalMonitorConfig` round-trip 必須保留 scan cadence、early / late session rule、trade mode、`DecisionSource`、auto-managed flag 與 day-trade quantity。
- 設定摘要與 CSV / Report 必須輸出足以追溯 scanner 行為的旗標；測試不得只驗證 getter / setter，至少要有案例走 `MarketScannerService.scan(...)` 正式流程。

### 18.2 禁止的低價值測試

除非能驗證交易語意，否則不要優先補：

* getter / setter 測試
* constructor 不報錯測試
* enum values 測試
* Swing UI constructor 測試
* 單純檢查物件不為 null 的測試
* 沒有 assert 核心交易結果的 smoke test
* 只測為測試新增、但正式流程沒有使用的 production API

### 18.3 每個新增測試必須回報

每次新增測試後，AI 必須回報：

```text
【新增測試檔案】
- ...

【新增測試方法】
- ...

【驗證的交易語意】
- ...

【對應 production class】
- ...

【測試資料】
- ...

【主要斷言】
- ...

【是否接正式鏈路】
- 是 / 否，原因...

【本次沒有補到的風險】
- ...

【執行命令】
- mvn -q -DskipTests compile
- cmd /c "mvn -q -Djacoco.skip=true test"
- 若可行，cmd /c "mvn -q test"

【coverage 狀態】
- 若使用 -Djacoco.skip=true，只能宣稱 unit test 通過，不得宣稱 coverage 通過。
```

---

## 19. Auto Monitor 正式交易鏈路規則

自動監控 OPEN_LONG 不得直接呼叫 `ExecutionEngine.openPosition(...)`。

正式鏈路必須為：

```text
Scanner / Signal
→ DecisionResult
→ DecisionResult.source = AUTO_MONITOR
→ RiskManager.checkDayTradeOpenLong(...)
→ ExecutionEngine.executeDecision(...)
→ ExecutionResult
→ Position / PaperTrade / UI 狀態同步
```

### 19.1 禁止旁路

Auto Monitor 不得直接呼叫：

```java
executionEngine.openPosition(...)
```

除非是 `ExecutionEngine.executeDecision(...)` 內部封裝呼叫。

Auto Monitor 不得在 UI 端自行完成以下核心判斷：

* 現金不足買一張
* 13:25 後禁止開倉
* 停損冷卻
* 日損熔斷
* 最大持倉數
* 每日最多交易
* 開單間隔
* 同一根 5 分 K 只允許一筆

UI 只能顯示阻擋原因，不得成為核心風控來源。

### 19.2 Reason 不得作為機器判斷

`DecisionResult.reason`、`entry reason`、`exit reason text` 只可作為人類閱讀文字，不得作為程式判斷依據。

禁止使用：

```java
reason.contains("停損")
reason.contains("STOP")
reason.contains("auto")
reason.contains("monitor")
reason.contains("自動監控")
```

程式判斷必須使用 enum / metadata，例如：

```java
decision.getSource()
decision.getDecisionSource()
result.getOrderType()
```

可用的 metadata 包含：

* `DecisionSource`
* `ExitReason`
* `OrderType`
* `TradeMode`
* `autoManaged`
* `RiskViolation`

UI 可以顯示 reason，但不得以 reason 文字作為核心交易語意判斷。

### 19.3 DecisionSource 規則

目前決策來源至少包含：

```java
MANUAL
AUTO_MONITOR
RADAR_REPLAY
BACKTEST
SYSTEM
```

若新增決策來源，必須同步檢查：

* `DecisionResult`
* `ExecutionEngine`
* `PaperTradeRecorder`
* `Backtest / Replay`
* CSV / Report
* 測試案例
* 文件

### 19.3 RiskManager 規則

Auto Monitor OPEN_LONG 前置風控必須走 `RiskManager.checkDayTradeOpenLong(...)` 或等價核心風控服務。

至少包含：

* 現金不足買一張阻擋
* 13:25 後禁止 OPEN_LONG
* 停損冷卻
* 日損熔斷
* 最大持倉
* 每日交易限制
* 開單間隔

UI 可以負責顯示中文阻擋原因，但不得在 UI 端重新實作核心交易語意。

### 19.4 停損冷卻與日損熔斷

停損出場後，正式流程必須呼叫：

```java
RiskManager.registerStopLossCooldown(...)
```

平倉產生已實現損益後，正式流程必須呼叫：

```java
RiskManager.updateDailyPnL(...)
```

若 UI 暫時保留 `autoManagedPositions`、`stopLossCooldownUntil`、`autoMonitorDailyPnl` 作為顯示狀態，必須視為鏡射狀態，不得成為核心風控來源。

---

## 20. Report / CSV / Paper Trade 輸出規則

### 20.1 Backtest / Report 成本欄位

回測、報告、CSV、Paper Trade Record 必須保留成本拆分：

* `grossProfit`
* `commission`
* `tax`
* `slippageCost`
* `netProfit`

不得只輸出未扣成本損益。

### 20.2 Report / CSV 可追溯欄位

Report / CSV 必須能追溯：

* 為什麼進場
* 為什麼不進場
* 為什麼出場
* 使用哪個 trade mode
* 使用哪個 timeframe
* 使用哪個資料來源模式
* 使用哪些 radar score components

至少保留：

* `block reason`
* `entry reason`
* `exit reason`
* `trade mode`
* `timeframe`
* `bar source`
* `DecisionSource`
* `radar score components`

阻擋原因不得只輸出泛用 `No entry signal`。

### 20.3 Radar Score Components

雷達加分明細至少要保留：

* 策略名稱
* 訊號方向
* 信心度
* 權重
* LONG 分數貢獻
* 原因

並同步輸出到：

* SQL 雷達回測匯出
* Replay `_symbols.csv`
* Paper Trade CSV
* Report / Exporter

### 20.4 Paper Trade Record

自動交易紀錄必須寫入 `logs/paper-trades`，且至少保留：

* `DecisionSource`
* auto-managed flag
* symbol
* side / action
* trade mode
* timeframe
* quantity
* entry price
* exit price
* grossProfit
* commission
* tax
* slippageCost
* netProfit
* entry reason
* exit reason
* block reason，如有
* setup score
* radar score components
* strategy setting summary

禁止只記錄成交結果而沒有決策來源與原因。

### 20.5 CSV 編碼

任何預期用 Excel 開啟的 CSV 必須使用 UTF-8 with BOM。

Java 原始碼內的中文欄位名稱必須保持真正 Unicode 中文，不得提交 mojibake 字串。

若 Windows 終端顯示 CSV 或 Java 中文亂碼，不可直接判定檔案壞掉；應使用 Excel、UTF-8 reader 或 codepoint 檢查確認。

### 20.6 LogExporter CSV schema 規則

`LogExporter` 匯出的 CSV header 若改名、改順序、改語言，視為 schema breaking change。

目前 `LogExporter` 使用 CSV schema v2：ASCII snake_case header。

若改為或維持 ASCII snake_case header，必須：

- 明確標示 schema version 或 migration note。
- 更新 `README.md`、`PROJECT_DOCUMENTATION.md`、`AGENTS.md`。
- 補測試鎖定 header 欄位與順序。
- 補測試確認 UTF-8 BOM。
- 說明舊中文 header 是否仍支援。

不得在未說明相容性風險的情況下重寫 CSV header。

### 20.7 TradeRecord 正式鏈路規則

`TradeRecord` 新增語意欄位後，必須同步檢查正式 builder call site。

不得只在測試中建立完整 `TradeRecord`，而正式匯出流程仍輸出 null/default。

至少檢查：

- `BacktestResultDialog -> TradeRecord -> LogExporter`
- `PaperTradeRecorder`
- Report / Exporter
- Replay / Backtest 匯出流程

---

## 21. 已知技術債與過渡狀態

### 21.1 BacktestEngine N+1 open 過渡設計

目前 `BacktestEngine` 已用測試保護 N+1 open 成交語意。

若目前實作是透過 `getCurrentPrice()` 在第 N 根 signal 時回傳第 N+1 根 open，需視為過渡設計。

後續較佳重構方向：

```text
第 N 根 K 收完產生 signal
→ 建立 pending order
→ 第 N+1 根 open 或下一筆可見 tick 成交
```

禁止在未確認語意前，把成交價改回第 N 根 close，避免重新引入 Lookahead Bias。

### 21.2 UI 鏡射狀態

若 UI 暫時保留以下狀態：

* `autoManagedPositions`
* `stopLossCooldownUntil`
* `autoMonitorDailyPnl`

這些只能視為 UI 顯示或相容用鏡射狀態。

核心來源應逐步收斂到：

* `ExecutionEngine`
* `RiskManager`
* `DecisionResult`
* `PaperTradeRecorder`

不得讓 UI 鏡射狀態重新成為核心交易語意來源。

### 21.3 MainFrameWithDocking 巨大類別

若 `MainFrameWithDocking` 內部邏輯過多，優先抽出小型 service，而不是繼續在 UI class 疊加交易語意。

可優先考慮：

* `AutoMonitorExecutionService`
* `MonitorTemplateStore`
* `AutoMonitorRiskAdapter`
* `PaperTradeViewModelMapper`

抽 service 時必須保留既有交易語意，並補對應測試。

---

## 22. 測試穩定性與 Flaky Risk 規則

單元測試不得依賴：

* 真實外部 API
* 真實 MySQL
* 真實今天日期
* 不可控背景 thread
* 未關閉的 scheduler
* 長時間 `Thread.sleep`

若測試使用 executor / scheduler，必須在測試結束後確實 shutdown。

若測試需要資料源，優先使用 fake feed、fixture、deterministic executor。

若測試屬於 integration test，必須明確隔離，不得混入預設 unit test 流程造成 flaky。

測試通過但輸出 background exception 時，不得視為完全乾淨通過，必須列入 P0.5 測試穩定性待辦。

已知需優先檢查的測試類型：

* `IEXCloudFeedTest`
* `PolygonFeedTest`
* `SignalMonitorServiceTest`
* `MarketDataCollectorFeedTest`
* `FinMindFeedTest`
* 任何使用 ExecutorService / Scheduler / Thread.sleep / CountDownLatch 的測試

不得為了讓輸出安靜而吞掉真正錯誤。

### 22.1 測試隔離建構子規則

若為了隔離外部 API、DB、背景 scheduler 而新增 package-private 測試建構子，必須符合：

- 不改變正式 public constructor 行為。
- 不暴露成 public API。
- 只能用於注入 fake feed、停用背景 scheduler 或避免真實外部連線。
- 必須由對應測試覆蓋。
- 回報中必須說明正式流程仍使用原 public constructor。

不得為了測試方便而改變正式資料源 lifecycle 或交易語意。

---

## 23. 後續優先序

1. P0 收斂審查：

   * 確認 P0 第一批與第二批測試不是只測旁路 API。
   * 確認新增 production method / field 有正式流程使用。
   * 確認 Paper Trade / Report 欄位真的由正式流程寫入。

2. P0.5 測試穩定性：

   * 清理 feed 背景 thread 例外。
   * 隔離 integration test。
   * 移除 unit test 對真實 API / DB / 今天日期的依賴。

3. P1 資料源與 K 線聚合：

   * `MarketDataCollectorFeed.fetchSessionBarsWithSource(...)`
   * `RealtimeBarBuilder`
   * `TimeframeAggregator`
   * `TICKS_AGGREGATED_INCOMPLETE`

4. P2 設定與 UI 鏈路：

   * `RadarStrategyConfig`
   * `SignalMonitorConfig`
   * A/B/C 模板保護
   * 自訂模板 round-trip
   * 設定摘要完整性

5. 用多日 SQL 回測驗證三層停損停利。

6. 補條件貢獻分析與被阻擋訊號後續表現。

7. 持續校準 B 組收斂版與三個當沖模板。

8. 改善分點資料匯入與 SQL 診斷。

9. 等資料與回測都穩定後，再評估 ML。

## 24. P1-C Auto Monitor / Replay 節奏一致性規則

Auto Monitor 與 SQL Radar Replay 的自動開倉節奏必須共用 `AutoMonitorExecutionGate` 或等價核心 gate，不得在 UI、replay、scanner 各自重寫不同版本。

- 早盤收資料不開倉。
- 13:05 後禁止新倉。
- 13:25 強制平倉。
- 同一根 5 分 K 只允許一筆自動開倉。
- 每日最多交易數必須生效。
- 最大同時持倉數必須生效。
- 停損冷卻、平倉冷卻、日損熔斷、連敗熔斷必須以同一套狀態輸入判斷。
- 時間判斷必須以 signal / asOfTime 為準，不得只用 K 棒起始時間。
- 13:25 cutoff 應平掉 auto-managed position，不得只依賴 `tradeMode == DAY_TRADE`。

Execution 語意：

- `ExecutionEngine.forceCloseAutoManagedPositions(...)` 只可平掉 engine 標記為 auto-managed 的部位，不得誤關 manual position。
- `ExecutionEngine.reversePosition(...)` 在 v1 long-only 應拒絕，不得形成可執行放空或反向倉位。
- `ExecutionResult` 必須保留 `DecisionSource`，paper trade 在缺少 `DecisionResult` 的 cutoff / system close 場景應從 `ExecutionResult` fallback 記錄來源。
- partial close 必須保留剩餘 quantity、realized PnL 與明確 close reason。
---

## AI 協作文件索引

半自動 GPT + Codex 流程請優先閱讀以下文件：

* `PROJECT_CONTEXT.md`：專案邊界、核心流程、資料源與 AI 協作模式。
* `FEATURE_FLOW.md`：UI / Config / Scanner / Decision / Risk / Execution / Backtest / Report / 文件完整鏈路。
* `TESTING_GUIDE.md`：修改類型對應的 compile、unit test、coverage 與核心交易語意測試要求。
* `CODE_REVIEW_CHECKLIST.md`：GPT 或人工 reviewer 審查 Codex diff / PR 的檢查清單。
* `RISK_AREAS.md`：Codex 不可自動大改或必須嚴格審查的高風險區域。
* `MODULE_OWNERSHIP.md`：各 package / module 的責任、風險、允許修改範圍與必測項目。

這些文件不取代本檔規則；若內容衝突，以本檔交易語意、資料源邊界與禁止事項為準。
