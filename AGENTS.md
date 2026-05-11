# DreamHouseTrading AI Collaboration Guide

最後更新：2026-05-11

## 1. 專案概述

- 專案：`DreamHouseTrading`
- 類型：Java Swing 桌面交易分析平台
- 語言：Java 17
- 建構：Maven
- 邊界：分析 / 掃描 / 決策 / 回測 / 模擬執行，不做真實券商下單

## 2. 目前核心流程

1. `Market Data`
2. `Signal Generation`
3. `Decision & Risk`
4. `Execution Simulation`
5. `Backtest & Reporting`

## 3. 關鍵規則

### 3.1 編碼與變更原則

- 優先修改既有檔案
- 只有在模型或模組邊界真的需要時才新增檔案
- 避免建立新的進度文件或臨時說明文件
- 改動後要先確保編譯，再看是否需要更新文檔

### 3.2 Git / repo 原則

- GitHub 只保留一個正式 repo
- `main` 是唯一長期主線
- 新工作可在 `codex/*` 分支進行，但不要把多份平行文檔再推回主線

### 3.3 決策與執行語意

- `DecisionResult` 是唯一標準化決策輸出
- 新的執行邏輯應優先使用 `ExecutionEngine.executeDecision(...)`
- Scanner 樣式輸出應對齊 `MarketScanResult`
- 不要在 UI 端重做交易語意、風控語意或訂單語意

### 3.4 風控與產品限制

- v1 維持 `long-only`
- 不要默默加入放空可執行路徑
- 進場檢查必須經過 `RiskManager`
- `RiskManager` 目前至少應負責：
  - account risk
  - position sizing
  - minimum risk/reward
  - volatility range

## 4. 主要檔案

- [README.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\README.md)
- [PROJECT_DOCUMENTATION.md](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\PROJECT_DOCUMENTATION.md)
- [pom.xml](C:\Users\chiat\Desktop\測試UI\DreamHouseTrading\pom.xml)

核心程式區：

- `src/main/java/com/dreamhouse/trading/core/decision`
- `src/main/java/com/dreamhouse/trading/core/execution`
- `src/main/java/com/dreamhouse/trading/core/backtest`
- `src/main/java/com/dreamhouse/trading/ui`

## 5. 驗證命令

編譯：

```bash
mvn -q -DskipTests compile
```

測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

注意：

- 這台機器上的預設 `mvn test` 會被 JaCoCo coverage 檔權限問題卡住
- 若只是確認功能，先用 `-Djacoco.skip=true`

## 6. 文檔規則

- `README.md`：對外簡介與啟動方式
- `PROJECT_DOCUMENTATION.md`：技術現況、模型、驗證證據
- `AGENTS.md`：AI 協作規範

不要再新增：

- `*_progress.md`
- `*_updated.md`
- `*_plan.md`
- 重複說明文件

## 7. 後續工作優先序

1. 補 `TradeRecord` 的完整交易生命週期語意
2. 收斂 UI 端的分類/建議邏輯到核心服務
3. 補更完整的 scanner -> decision -> execution -> report 整合驗證
