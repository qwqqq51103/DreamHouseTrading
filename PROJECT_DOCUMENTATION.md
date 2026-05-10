# DreamHouseTrading 專案文檔

## 1. 文檔目的

本文件是 DreamHouseTrading 的唯一技術總文檔，負責整合：

- 當前功能與架構
- 開發與測試流程
- 重要修復與設計決策
- 專案目前進度與下一步

除 `README.md` 與 `AGENTS.md` 外，不再維護平行的進度文檔、覆蓋率指南、流程副本或更新版說明書。

## 2. 專案現況

### 2.1 基本資訊

- 專案名稱：DreamHouseTrading
- 類型：Java Swing 股票交易模擬與分析平台
- Java 版本：17
- 建構工具：Maven
- 主要 UI：Swing + FlatLaf + Modern Docking
- 圖表與分析：JFreeChart + ta4j

### 2.2 目前定位

此專案目前是桌面型交易分析工作站，不是 Web 專案。主要工作集中於：

- 市場資料接入與標準化
- 圖表展示與技術分析
- 多策略訊號與風控決策
- 候選標的掃描與雷達呈現
- 回測與測試驗證

## 3. 核心模組

### 3.1 資料層

- `MarketDataFeed`: 資料來源介面
- `FinMindFeed`: 台股資料來源與歷史 / 即時更新
- `SimulatorFeed`: 本地模擬資料
- `MarketDataLoader`: 從資料庫載入歷史資料
- `MarketDataNormalizer`: K 線排序、去重與裁切

### 3.2 決策與掃描

- `DecisionEngine`: 交易決策主流程
- `RiskManager`: 倉位、風險限制與進場檢查
- `MarketScannerService`: 觀察清單掃描入口
- `MarketScanResult`: 候選股掃描輸出模型
- `TradeModeClassifier`: 交易模式分類

### 3.3 策略

目前已接入的策略邏輯包含：

- 開盤區間突破
- 放量突破
- VWAP 回踩
- ATR 波動過濾
- 收盤前強制平倉
- 均線多頭排列
- 平台突破
- 高週期轉強
- 量縮整理後放量突破

### 3.4 UI

- `MainFrameWithDocking`: 主視窗
- `WatchlistPanel`: 觀察清單與訊號欄位
- `OpportunityRadarDock`: 今日機會雷達
- `NewsDock`: 個股新聞顯示
- `MarketAnalysisDock`: 盤中分析結果顯示

## 4. 最近已完成的關鍵整理

### 4.1 文檔收斂

本次已將原本分散的文檔體系收斂為三份核心文件：

- `README.md`
- `PROJECT_DOCUMENTATION.md`
- `AGENTS.md`

下列類型文件不再獨立維護：

- `*_progress.md`
- `*_UPDATED.md`
- `*_GUIDE.md`
- `*_PLAN.md`
- 單次整合報告
- 一次性診斷說明

若內容仍有價值，應直接寫入本文件對應章節。

### 4.2 GitHub 管理規則

- 正式倉庫只保留 `qwqqq51103/DreamHouseTrading`
- `main` 是唯一長期主線
- `feature/*`、`issue-*`、`codex-*`、`hotfix/*` 都是臨時分支
- 臨時分支合併完成後必須刪除遠端分支

### 4.3 本地版本控制清理

以下內容已列入 `.gitignore`，不應再上傳 GitHub：

- `.codex/`
- `.github-issues/`
- `agency-agents-zh/`
- `DreamHouseTrading_優化完成與使用說明.txt`
- `datasource.properties`

## 5. 目前功能進度

### 5.1 已穩定可用

- FinMind 與模擬資料源
- 歷史 K 線查詢與標準化
- 市場掃描與今日機會雷達
- 決策引擎與基礎風控
- Swing 主畫面與多 Dock 面板
- 歷史資料庫補載
- Maven 測試流程
- JaCoCo 覆蓋率報告

### 5.2 最近已完成的技術優化

#### 資料流與排程

- `MarketDataFeed.fetchHistoricalBars(...)` 已視為核心能力
- `FinMindFeed` 與 `SimulatorFeed` 已支援同步歷史 K 線查詢
- `MarketDataNormalizer` 統一處理排序、去重、barCount 裁切與未完成 K 線
- `FinMindFeed` 的生命週期控制已補強，降低快速停止 / 重啟時的排程例外風險

#### 決策與風控

- `DecisionEngine` 會優先採用策略建議的停損與停利
- 新增風控參數：
  - `shortSellingEnabled`
  - `minRiskRewardRatio`
  - `minEntryAtrPercent`
  - `maxEntryAtrPercent`
  - `atrFilterPeriod`
- 第一版維持台股做多與平倉流程，空方訊號目前只做風險提醒

#### 掃描與 UI

- `MarketScannerService` 成為 watchlist 掃描統一入口
- `WatchlistPanel` 顯示交易模式分數、信心度、風險報酬比與最後訊號時間
- `OpportunityRadarDock` 呈現今日候選標的
- 點選候選股可同步切換圖表、新聞與分析面板

#### 測試與可維護性

- JaCoCo 改為使用 ASCII-safe 路徑輸出
- `DataSourceManagerTest` 已避免直接操作根目錄的 `datasource.properties`
- 已補測核心測試：
  - `MarketDataNormalizerTest`
  - `MarketScannerServiceTest`
  - `DecisionEngineRiskFiltersTest`
  - `DayTradingSignalStrategiesTest`
  - `SwingSignalStrategiesTest`
  - `DecisionSystemIntegrationTest`

## 6. 目前未完成或後續議題

### 6.1 尚未落地的產品級能力

- 真實下單流程
- 融券放空完整支持
- 多策略績效對比 UI
- 更完整的回測結果展示
- 更深的交易日誌與匯出策略

### 6.2 後續建議

1. 補強 `DecisionEngine`、`RiskManager` 與掃描結果間的整體回歸測試
2. 針對 FinMind 交易時段切換與排程邊界行為增加測試
3. 若之後擴充 Web 模組，再另行引入瀏覽器驗證流程，不與 Swing 文檔混寫

## 7. 測試與驗證

### 7.1 常用命令

編譯：

```bash
mvn -q -DskipTests compile
```

執行全部測試：

```bash
mvn -q test
```

生成覆蓋率報告：

```bash
mvn clean test
```

### 7.2 驗證原則

- 修改核心邏輯時至少執行 `mvn -q test`
- 修改資料流、決策或策略時，優先補回歸測試
- 修改 UI 時至少手動檢查主要畫面是否可啟動

## 8. 文檔維護規則

### 8.1 README.md

用途：

- 對外展示專案
- 說明如何啟動
- 提供最少但必要的開發規則

不應放入：

- 細碎進度追蹤
- 長篇修復歷史
- 多版本設計草案

### 8.2 PROJECT_DOCUMENTATION.md

用途：

- 唯一技術總文檔
- 維護目前進度、修復記錄與架構現況

### 8.3 AGENTS.md

用途：

- AI 助手的規範、Git / 文檔治理規則、skill 工作流

## 9. 本次文檔清理決策

### 9.1 直接刪除

以下文件屬於重複、副本、一次性輸出或已過時說明，應刪除：

- `CODE_COVERAGE_GUIDE.md`
- `DEVELOPMENT_WORKFLOW.md`
- `SETUP_COMPLETE.md`
- `TEST_COVERAGE_IMPROVEMENT_PLAN.md`
- `FinMind_API_診斷指南.md`
- `JAR_PORTABILITY_ASSESSMENT.md`
- `STRATEGY_IMPLEMENTATIONS.md`
- `UI_DATA_SOURCE_TODO.md`
- `multi_timeframe_trade_spec_progress.md`
- `multi_timeframe_trade_spec_progress_UPDATED.md`
- `資料庫數據未導入診斷指南.md`
- `.claude/` 相關文檔
- `CLAUDE.md`

### 9.2 整併後保留

- 進度狀態改寫進本文件第 5 章
- 開發 / 測試流程改寫進本文件第 7 章
- GitHub 與文檔治理改寫進本文件第 4、8 章與 `AGENTS.md`

## 10. 更新記錄

- 2026-05-10：文檔體系收斂為 `README.md`、`PROJECT_DOCUMENTATION.md`、`AGENTS.md`
- 2026-05-10：新增 GitHub 單主線治理規則
- 2026-05-10：重寫專案進度描述，移除多份平行進度文檔
