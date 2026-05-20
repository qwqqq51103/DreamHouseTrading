# DreamHouseTrading Project Documentation

最後更新：2026-05-20

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
- 雷達掃描與 SQL 回測。

限制：

- Collector 沒有啟動或資料過期時，雷達應跳過，不得改打 FinMind 即時 API。
- 台股 `13:25~13:30` 集合競價期間 tick 可能停止更新，這不是 Collector 故障。

### 3.2 FinMind

狀態：低頻與手動查詢資料源。

允許用途：

- 盤前 / 盤後補資料。
- `TaiwanStockKBar` 盤後分 K。
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

目前策略方向：

- 日線只做盤前股票池。
- 5 分 K 是主交易層。
- 1 分 K 是執行確認層。
- 盤中缺少可靠即時大盤與產業資料時，使用觀察清單內部市場狀態替代。

## 5. 回測與報告進度

已完成或已接入：

- SQL 雷達批次回測。
- 指定日期與日期範圍回測。
- 同一天資料仍按單日交易規則計算。
- N 根訊號成立後，N+1 open 成交。
- 同一根 K 同時碰停損與停利時，採保守停損優先。
- 回測報告拆分 grossProfit、commission、tax、slippageCost、netProfit。
- 交易理由、阻擋原因、技術指標與週期資訊寫入報告。
- 圖表支援回測開平倉標記。

仍需持續驗證：

- 被阻擋訊號的後續最大漲幅、最大回撤與收盤報酬統計。
- 各硬阻擋條件與加分條件的貢獻分析。
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
2. 補條件貢獻分析與阻擋後續表現統計。
3. 持續校準 B 組收斂版與三個當沖模板。
4. 改善分點資料匯入診斷，讓資料缺失、API 失敗、SQL 已有資料三種狀態更清楚。
5. 等回測可信度與風控穩定後，再評估 ML 或更複雜模型。
