# DreamHouseTrading Project Documentation

## 1. 專案定位

DreamHouseTrading 是 Swing 桌面交易分析平台，主線目標是：

- 市場資料分析
- 交易訊號與決策
- 風控檢查
- 模擬執行
- 回測與績效報表

目前產品邊界：

- 保留桌面架構
- 保留掃描 / 決策 / 回測能力
- 不做真實券商下單
- 第一階段維持 `long-only`

## 2. 主要模組

### 2.1 Market Data

- `MarketDataFeed`
- `FinMindFeed`
- `MarketDataLoader`
- `MarketDataCache`

### 2.2 Decision & Risk

- `DecisionEngine`
- `RiskManager`
- `MarketRegimeDetector`
- `TrendAnalyzer`
- `VotingEngine`
- `TradeModeClassifier`

### 2.3 Execution Simulation

- `ExecutionEngine`
- `ExecutionResult`
- `Order`
- `OrderSide`
- `OrderStatus`
- `OrderType`

### 2.4 Backtest & Reporting

- `BacktestEngine`
- `BacktestResult`
- `Portfolio`
- `Position`
- `PortfolioSnapshot`
- `TradeRecord`

### 2.5 UI

- `MainFrameWithDocking`
- `WatchlistPanel`
- `MarketAnalysisDock`
- `ModeRecommendationDock`
- `BacktestResultDialog`

## 3. 2026-05-11 完整化實作

### 3.1 核心流程定稿

專案內部流程已明確收斂成：

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

### 3.2 單一決策契約

`DecisionResult` 現在是策略層到執行層的唯一標準化決策物件。

欄位語意：

- `symbol`
- `tradeMode`
- `action`
- `orderType`
- `orderSide`
- `confidence`
- `reason`
- `suggestedStopLoss`
- `suggestedTakeProfit`
- `suggestedQuantity`
- `riskRewardRatio`

### 3.3 掃描輸出契約

`MarketScanResult` 由 `DecisionResult` 派生，讓掃描結果與決策結果共用同一套語意，不再各自長不同欄位定義。

### 3.4 執行契約

`ExecutionEngine` 新增：

- `executeDecision(DecisionResult decision, double price)`
- `executeOrder(Order order)`

這讓 UI 或其他呼叫端不需要再自己重建下單意圖。

### 3.5 風控規則

`RiskManager` 現在除了原有帳戶級檢查外，也會做進場層級檢查：

- 最低風險報酬比
- 波動率下限
- 波動率上限
- 既有資金與部位限制

### 3.6 金融語意限制

第一階段限制如下：

- `long-only`
- `OPEN_SHORT` 決策會被拒絕
- 模擬執行仍可保留 `OPEN_SHORT` 型別語意，但產品不允許落地

## 4. 目前已落地的核心模型

### 4.1 DecisionResult

用途：

- 標準化進出場建議
- 承接風控與投票結果
- 轉成 `MarketScanResult`
- 提供執行引擎消費

### 4.2 Order / ExecutionResult

用途：

- 標準化模擬訂單
- 區分 `BUY / SELL / SHORT / COVER`
- 區分 `NEW / ACCEPTED / FILLED / CANCELLED / REJECTED`

### 4.3 Portfolio / PortfolioSnapshot

用途：

- 追蹤現金、部位、總資產
- 累積已實現損益
- 建立快照供回測與報表使用

## 5. 驗證矩陣

### 5.1 已驗證情境

- `DecisionResult` 可正確轉成 `MarketScanResult`
- 風險報酬比不足時，`RiskManager` 會拒絕進場
- 波動太低時，`RiskManager` 會拒絕進場
- 波動太高時，`RiskManager` 會拒絕進場
- `ExecutionEngine` 可直接執行標準化做多決策
- `ExecutionEngine` 會拒絕標準化放空決策
- `ExecutionEngine` 可依決策物件平掉既有持倉

### 5.2 現有整合測試仍涵蓋

- `DecisionSystemIntegrationTest`
- `PortfolioTest`
- `PositionTest`
- 既有 cache / feed / data source 相關測試

## 6. 實際驗證結果

已執行：

```bash
mvn -q -DskipTests compile
cmd /c "mvn -q -Djacoco.skip=true test"
```

結果：

- 編譯通過
- 測試通過

殘留風險：

- 預設 `mvn test` 仍會因 JaCoCo coverage 檔案權限問題失敗
- 部分 feed 測試在背景 thread 仍會輸出 scheduler noise，但 Maven 可成功結束

## 7. 後續建議

下一階段若要再往「更完整金融體系」推進，建議順序：

1. 把 `TradeRecord` 補齊 entry/exit reason 與 hit-state 語意
2. 讓 `ModeRecommendationDock` 完全吃 `DecisionEngine` 既有分類結果，不再在 UI 端重算
3. 補 scanner 與 UI candidate table 的一致欄位模型
4. 補 `Feed -> Strategy -> Decision -> Execution -> Report` 的更完整整合測試
