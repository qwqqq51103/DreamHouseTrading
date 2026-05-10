# DreamHouseTrading

Java 17 Swing 股票交易模擬與分析平台，聚焦台股資料整合、圖表分析、策略決策與回測驗證。

## 專案定位

- 桌面應用：Java Swing + FlatLaf
- 圖表與技術指標：JFreeChart + ta4j
- 資料來源：FinMind、模擬資料源，並可串接 MarketDataCollector / MySQL
- 核心用途：觀察市場、分析訊號、產生交易建議、執行回測

## 目前保留的核心文檔

- `README.md`: 專案概覽、啟動方式、維護原則
- `PROJECT_DOCUMENTATION.md`: 技術細節、進度狀態、測試與修復記錄
- `AGENTS.md`: AI 助手作業規範與 skill 工作流

其餘歷史說明、進度副本、一次性指南已整併或刪除，避免文檔分散。

## 目前功能重點

- 即時與歷史 K 線圖表，支援多時間週期
- 技術指標與繪圖工具
- FinMind 台股資料整合
- MarketDataCollector / MySQL 歷史資料補載
- DecisionEngine 決策流程與風控
- MarketScannerService 與今日機會雷達
- 回測、策略驗證與測試覆蓋率報告

## 最近整理結果

- GitHub 以 `main` 為唯一長期主線
- 文檔收斂為三份核心檔案
- 多份重複進度文檔已移除
- 本地 agent 匯出與 issue 輔助資料夾已排除出版本控制

## 環境需求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+（若使用歷史資料整合）
- FinMind Token（若使用真實台股資料）

## 快速開始

### 1. 編譯

```bash
mvn -q -DskipTests compile
```

### 2. 測試

```bash
mvn -q test
```

### 3. 啟動

```bash
mvn exec:java
```

或使用專案內批次檔：

- `啟動-完整編譯.bat`
- `啟動-直接運行.bat`
- `啟動.bat`

## 真實資料設定

建立本機 `datasource.properties`：

```properties
finmind.apitoken=YOUR_TOKEN_HERE
datasource.type=FINMIND
```

此檔案已列入 `.gitignore`，不得提交。

## 專案結構

```text
src/main/java/com/dreamhouse/trading/
  core/        核心資料流、決策、掃描、風控
  strategy/    策略與訊號邏輯
  ui/          Swing 視窗、Dock、面板
  util/        共用工具

src/test/java/com/dreamhouse/trading/
  各核心模組測試
```

## 開發規則

- 優先修改現有類別，不先新增新檔
- 純 Java / Swing 任務通常不需要額外 skill
- 文件只維護三份核心檔案
- GitHub 只保留一個正式 repo 與一條長期主線 `main`
- 臨時分支合併後刪除，不保留平行主線

## 參考

詳細技術狀態、測試策略、修復紀錄與目前進度，請看 `PROJECT_DOCUMENTATION.md`。
