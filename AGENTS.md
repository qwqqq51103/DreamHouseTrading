# DreamHouseTrading AI Collaboration Guide

最後更新：2026-05-21

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
- 台股 `13:25~13:30` 是收盤集合競價，可能沒有連續 tick，不可視為 Collector 故障。
- `13:25~13:30` 仍要允許載入 09:00 起已收集的盤中 K 線。
- 台股分 K `TaiwanStockKBar` 是盤後資料，15:50 前不得當作盤中即時 K 線來源。
- 手動 `TaiwanStockKBar` SQL 補資料若支援日期範圍，必須先檢查股票日期的 SQL M1 分 K 根數與開收盤覆蓋，只對不完整資料組合呼叫 FinMind API。
- Watchlist 成交量若來自 SQL tick，顯示最新 tick 原始 volume，不在 UI 端累加。
- Watchlist 台股漲跌幅應以指定日期前一交易日收盤價為基準；缺少昨收時才可明確 fallback，不要用圖表開盤價覆蓋。

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
- RSI 超賣不能單獨作為開多理由。
- C 組回測模板用於驗證「EMA 單因子不得單獨開多」；預設需再有 RSI 轉強，或前一根有效放量突破且下一根續收高確認，不要退回當根突破直接追價。
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

## 12. 回測與報告規則

- SQL 雷達回測必須避免 Lookahead Bias。
- 訊號於第 N 根 K 收完成立後，第 N+1 根 open 或下一筆 tick 才能成交。
- 同一根 K 同時碰停利與停損時，保守先算停損。
- 成本模型要拆分 grossProfit、commission、tax、slippageCost、netProfit。
- 批次回測不得只輸出泛用 `No entry signal`；要保留 EMA、RSI、量能、投票或硬阻擋等可統計未進場原因。
- 回測進場前若停利目標扣除手續費、當沖稅與滑價後沒有正淨利，必須以「成本後停利空間不足」硬阻擋。
- 慢速 EMA 的跨日暖機資料只可供指定交易日指標初始化，不可把暖機日交易混入指定日期績效。
- 台股當沖證交稅必須納入模擬交易與回測。
- 報告需記錄阻擋原因、進場理由、出場理由、主要技術指標與雷達加分明細。
- 雷達加分明細至少要保留策略名稱、訊號方向、信心度、權重、LONG 分數貢獻與原因，並同步輸出到模擬交易 CSV 與 SQL 雷達回測匯出。
- SQL 雷達回測匯出必須保留加分條件貢獻統計與硬阻擋後續統計，用後續最大漲幅、最大回撤、收盤報酬驗證條件有效性。

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
