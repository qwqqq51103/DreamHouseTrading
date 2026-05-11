# DreamHouseTrading

DreamHouseTrading 是一個以 Java 17 與 Swing 建立的桌面交易分析平台，定位是「桌面分析 + 掃描 + 決策 + 回測 + 模擬執行」，目前不做真實券商下單。

## 目前主線能力

- 多資料源市場資料接入與歷史資料載入
- 技術分析、趨勢/環境判斷、投票式決策
- 回測與績效報表
- 模擬執行與投資組合狀態追蹤
- Watchlist / 分析 dock / 模式建議 dock 等桌面 UI

## 2026-05-11 完整化更新

- `DecisionResult` 現在是唯一標準化決策輸出
- `DecisionResult` 已包含：
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
- 新增 `MarketScanResult`，讓掃描結果可直接對齊決策語意
- 新增 `Order` / `OrderSide` / `OrderStatus`
- `ExecutionEngine` 新增 `executeDecision(DecisionResult, price)`
- `RiskManager` 新增進場層級檢查：
  - 最低風險報酬比
  - 波動區間上下限
- 專案第一階段明確維持 `long-only`

## 專案結構

```text
src/main/java/com/dreamhouse/trading/
  core/
    backtest/
    decision/
    execution/
    logging/
    monitor/
  ui/
  util/

src/test/java/com/dreamhouse/trading/
  core/
    backtest/
    cache/
    decision/
    execution/
```

## 核心流程

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

## 建置與驗證

編譯：

```bash
mvn -q -DskipTests compile
```

完整測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

說明：

- 這台 Windows 環境的預設 `mvn test` 會被 JaCoCo coverage 輸出檔權限問題卡住
- 跳過 JaCoCo agent 後，測試可正常通過

## 主文檔

- [PROJECT_DOCUMENTATION.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\PROJECT_DOCUMENTATION.md): 技術現況、核心模型、驗證證據
- [AGENTS.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\AGENTS.md): AI 協作規範與開發守則
