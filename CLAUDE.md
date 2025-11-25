# DreamHouseTrading 專案記憶與開發規範

> **用途**: 本文件為 AI 助手（Claude、Cursor 等）提供專案背景、開發規範和最佳實踐指南
> **最後更新**: 2025-11-25

---

## 📋 目錄

- [專案概述](#專案概述)
- [技術棧](#技術棧)
- [專案結構](#專案結構)
- [開發規範](#開發規範)
- [Git 工作流程](#git-工作流程)
- [代碼風格](#代碼風格)
- [測試要求](#測試要求)
- [文檔更新規則](#文檔更新規則)
- [常見任務](#常見任務)
- [重要注意事項](#重要注意事項)
- [疑難排解](#疑難排解)

---

## 專案概述

### 基本資訊

- **專案名稱**: DreamHouseTrading
- **類型**: 股票交易模擬與分析平台
- **語言**: Java 17
- **主要框架**: Java Swing + FlatLaf
- **建構工具**: Maven 3.8+
- **版本**: 0.0.1-SNAPSHOT

### 核心功能

1. **即時圖表系統**: K 線圖、技術指標、繪圖工具
2. **多數據源支援**: FinMind API（台股）、Yahoo Finance、Alpha Vantage 等
3. **資料庫整合**: 與 MarketDataCollector 整合，從 MySQL 載入歷史數據
4. **回測系統**: 多週期、多策略決策引擎
5. **UI 系統**: Modern Docking 框架，支援深色/淺色主題和中英文

### 專案目標

- 提供專業級股票圖表分析工具
- 支援台股即時數據與歷史數據整合
- 實現多策略回測引擎
- 保持代碼品質和可維護性

---

## 技術棧

### 核心技術

```
Java 17
├── UI 框架
│   ├── Java Swing (主框架)
│   ├── FlatLaf 3.6.1 (Look and Feel)
│   └── Modern Docking (停靠框架)
├── 圖表庫
│   ├── JFreeChart (圖表繪製)
│   └── ta4j (技術指標)
├── 數據源
│   ├── FinMind API (台股即時數據)
│   ├── Yahoo Finance
│   ├── Alpha Vantage
│   └── 自建 Simulator
├── 資料庫
│   ├── MySQL 8.0+ (歷史數據)
│   └── H2 (測試和快取)
└── 工具庫
    ├── SLF4J + Logback (日誌)
    ├── Jackson (JSON)
    └── Apache Commons
```

### 相關專案

- **MarketDataCollector**: 背景服務，持續收集市場數據到 MySQL
  - 位置: `C:\Users\chiat\Desktop\測試UI\MarketDataCollector`
  - JAR 位置: `lib/market-data-collector-1.0.0.jar`
  - 用途: 補充開盤後缺失的歷史數據

---

## 專案結構

### 目錄組織

```
DreamHouseTrading/
├── src/main/java/com/dreamhouse/trading/
│   ├── Main.java                          # 程式進入點
│   ├── core/                              # 核心邏輯
│   │   ├── MarketDataFeed.java           # 數據源介面
│   │   ├── FinMindFeed.java              # FinMind API 實現
│   │   ├── MarketDataLoader.java         # 資料庫載入器 (NEW!)
│   │   ├── DecisionEngine.java           # 決策引擎
│   │   └── cache/                        # 數據快取
│   ├── model/                            # 數據模型
│   │   ├── Tick.java                     # Tick 數據
│   │   ├── Bar.java                      # K 線數據
│   │   └── Quote.java                    # 報價數據
│   ├── strategy/                         # 交易策略
│   │   ├── base/                         # 基礎策略框架
│   │   ├── signal/                       # 信號策略
│   │   └── decision/                     # 決策策略
│   ├── ui/                               # UI 組件
│   │   ├── MainFrameWithDocking.java     # 主視窗
│   │   ├── ChartDock.java                # 圖表面板
│   │   └── ToolBarFactory.java           # 工具列
│   └── util/                             # 工具類
├── src/main/resources/
│   ├── messages_zh.properties            # 中文資源
│   ├── messages_en.properties            # 英文資源
│   └── logback.xml                       # 日誌配置
├── src/test/java/                        # 測試代碼
│   └── com/dreamhouse/trading/
│       └── core/
│           ├── TestDatabaseLoad.java     # 資料庫測試
│           └── DiagnoseDatabaseQuery.java # SQL 診斷
├── lib/                                  # 本地 JAR
│   └── market-data-collector-1.0.0.jar   # MarketDataCollector
├── pom.xml                               # Maven 配置
├── README.md                             # 專案說明
├── PROJECT_DOCUMENTATION.md              # 完整文檔
└── CLAUDE.md                             # 本文件
```

### 關鍵套件說明

| 套件 | 用途 | 重要類別 |
|------|------|---------|
| `core` | 核心邏輯、數據源管理 | MarketDataFeed, FinMindFeed, MarketDataLoader |
| `model` | 數據模型定義 | Tick, Bar, Quote, Position |
| `strategy` | 交易策略實現 | DecisionEngine, SignalStrategy |
| `ui` | 用戶界面組件 | MainFrame, ChartDock, WatchlistDock |
| `util` | 工具類和幫助函數 | TimeUtils, PriceUtils, I18nUtils |

---

## 開發規範

### 1. 代碼添加規則

#### 優先級原則

1. **永遠優先編輯現有文件**，避免創建新文件
2. 只在確實需要新功能模組時才創建新類別
3. 不要創建不必要的文檔文件（除非用戶明確要求）

#### 文件操作順序

```
1. 閱讀現有代碼 (使用 Read 工具)
2. 理解現有架構和設計模式
3. 編輯現有文件 (使用 Edit 工具)
4. 如果必須創建新文件，確認不會與現有代碼衝突
```

#### 避免的操作

❌ 不要創建重複功能的類別
❌ 不要創建 .md 文檔文件（除非用戶要求）
❌ 不要添加不必要的註解和 emoji
❌ 不要過度工程化（保持簡單）

### 2. 修改現有代碼的原則

#### 修改前檢查清單

- [ ] 已使用 `Read` 工具閱讀目標文件
- [ ] 理解現有代碼邏輯和設計意圖
- [ ] 確認修改不會破壞現有功能
- [ ] 保持代碼風格一致
- [ ] 避免引入新的依賴（除非必要）

#### 修改步驟

```java
// 1. 閱讀現有代碼
Read: src/main/java/com/dreamhouse/trading/core/FinMindFeed.java

// 2. 理解上下文
// - 這個類別的職責是什麼？
// - 使用了哪些設計模式？
// - 依賴哪些其他類別？

// 3. 進行最小化修改
Edit: 只修改需要改變的部分，保持其他代碼不變

// 4. 確保編譯通過
mvn compile -q
```

### 3. 新增功能的流程

#### Step 1: 評估需求

```
問題：用戶需要什麼功能？
現狀：專案中是否已有類似功能？
方案：
  - 選項 A：擴展現有類別（優先）
  - 選項 B：創建新類別（必要時）
```

#### Step 2: 設計決策

- 遵循現有架構模式
- 保持類別職責單一（Single Responsibility Principle）
- 使用依賴注入，避免硬編碼
- 考慮可測試性

#### Step 3: 實現

```java
// 好的例子：擴展現有類別
public class FinMindFeed implements MarketDataFeed {
    // 新增方法
    public void loadHistoricalDataFromDatabase(String symbol) {
        // 實現細節
    }
}

// 不好的例子：創建不必要的新類別
public class HistoricalDataLoader {  // ❌ 應該直接加到 FinMindFeed
    // ...
}
```

### 4. 錯誤處理原則

#### 記錄錯誤

```java
// ✅ 好的做法
try {
    loadData(symbol);
} catch (SQLException e) {
    logger.error("載入數據失敗: {}", symbol, e);
    throw new DataLoadException("無法載入 " + symbol, e);
}

// ❌ 避免
try {
    loadData(symbol);
} catch (Exception e) {  // 太寬泛
    e.printStackTrace();  // 不應使用
}
```

#### 優雅降級

```java
// 嘗試多種方法，提供 fallback
List<Tick> ticks = null;
try {
    ticks = loadFromDatabase(symbol);
} catch (Exception e) {
    logger.warn("資料庫載入失敗，嘗試 API: {}", e.getMessage());
    try {
        ticks = loadFromAPI(symbol);
    } catch (Exception e2) {
        logger.error("所有數據源都失敗", e2);
        return Collections.emptyList();
    }
}
```

---

## Git 工作流程

### Commit 訊息規範

遵循 Conventional Commits 規範：

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type 類型

| Type | 說明 | 範例 |
|------|------|------|
| `feat` | 新功能 | feat: 整合 MarketDataCollector 資料庫載入功能 |
| `fix` | 修復 Bug | fix: 修復切換商品時未載入資料庫數據 |
| `docs` | 文檔更新 | docs: 更新 README 添加資料庫整合說明 |
| `refactor` | 重構代碼 | refactor: 重構 DecisionEngine 投票邏輯 |
| `test` | 測試相關 | test: 添加 MarketDataLoader 單元測試 |
| `chore` | 建構/工具 | chore: 更新 Maven 依賴版本 |
| `style` | 代碼格式 | style: 統一代碼縮排格式 |
| `perf` | 效能優化 | perf: 優化資料庫查詢效能 |

#### Commit 訊息範例

```bash
feat: 整合 MarketDataCollector 資料庫載入功能與完整修復

## 新功能
- 與 MarketDataCollector 整合，從 MySQL 自動載入歷史數據
- 補充開盤後缺失數據（如 10:00 啟動程式，自動載入 9:00-10:00 的歷史數據）
- 切換商品時自動載入該商品的歷史數據
- 新增工具列按鈕「📊 載入歷史數據」支持手動載入

## 核心修復
1. 切換商品自動載入修復
   - subscribe() 方法新增背景執行緒載入資料庫數據

2. 股票代號格式自動匹配
   - 智能匹配：輸入 3706 自動查詢 3706.TW

3. VARCHAR 時間欄位查詢修復
   - 修復 SQL 字符串比較失敗問題

4. 時間戳解析修復
   - 實現三層降級解析策略

## 新增檔案
- MarketDataLoader.java
- TestDatabaseLoad.java
- DiagnoseDatabaseQuery.java

## 修改檔案
- FinMindFeed.java (subscribe, loadHistoricalDataFromDatabase)
- MainFrameWithDocking.java (手動載入按鈕)
- PROJECT_DOCUMENTATION.md (新增第 I 章)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Commit 前檢查清單

- [ ] 代碼編譯通過: `mvn compile -q`
- [ ] 測試通過: `mvn test -q` (pre-commit hook 會自動執行)
- [ ] 代碼格式正確
- [ ] 已更新相關文檔（如有需要）
- [ ] Commit 訊息符合規範

### Git 操作流程

```bash
# 1. 檢查狀態
git status

# 2. 添加修改的文件（選擇性添加，不要用 git add .）
git add src/main/java/com/dreamhouse/trading/core/FinMindFeed.java
git add src/main/java/com/dreamhouse/trading/core/MarketDataLoader.java
git add PROJECT_DOCUMENTATION.md

# 3. 創建 Commit（使用 HEREDOC 確保格式正確）
git commit -m "$(cat <<'EOF'
feat: 整合 MarketDataCollector 資料庫載入功能

## 新功能
- 與 MarketDataCollector 整合
- 自動載入歷史數據

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"

# 4. 推送到 GitHub
git push origin main
```

### Branch 策略

- **main**: 穩定版本，直接開發
- **feature/xxx**: 大型功能開發（如需要）
- **hotfix/xxx**: 緊急修復（如需要）

當前專案使用簡單的 main branch 策略，直接提交到 main。

---

## 代碼風格

### Java 代碼規範

#### 命名規範

```java
// 類別名稱：PascalCase
public class MarketDataLoader { }

// 方法名稱：camelCase
public void loadHistoricalData() { }

// 常量：UPPER_SNAKE_CASE
private static final int MAX_RETRIES = 3;

// 變數：camelCase
private String symbolName;
private List<Tick> tickData;

// 包名：lowercase
package com.dreamhouse.trading.core;
```

#### 代碼組織

```java
public class ExampleClass {
    // 1. 常量
    private static final Logger logger = LoggerFactory.getLogger(ExampleClass.class);
    private static final int DEFAULT_TIMEOUT = 5000;

    // 2. 成員變數
    private final String apiKey;
    private List<Tick> ticks;

    // 3. 建構子
    public ExampleClass(String apiKey) {
        this.apiKey = apiKey;
        this.ticks = new ArrayList<>();
    }

    // 4. 公開方法
    public void start() {
        // ...
    }

    // 5. 私有方法
    private void initializeConnection() {
        // ...
    }
}
```

#### 註解風格

```java
// ✅ 好的註解：解釋「為什麼」
// 修復：ts 欄位是 VARCHAR，需要轉換格式後才能比較
String sql = "SELECT * FROM ticks WHERE REPLACE(SUBSTRING(ts, 1, 19), 'T', ' ') >= ?";

// ❌ 不好的註解：重複代碼內容
// 設置 API key
this.apiKey = apiKey;

// ✅ JavaDoc（公開 API）
/**
 * 從資料庫載入指定股票今日開盤至今的歷史數據
 *
 * @param symbol 股票代號（如 "2330.TW"）
 * @return Tick 數據列表，按時間排序
 * @throws SQLException 資料庫連接失敗
 */
public List<Tick> loadTodayMarketOpenToNow(String symbol) throws SQLException {
    // ...
}
```

#### 日誌規範

```java
// ✅ 使用 SLF4J
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);

// 日誌等級使用
logger.error("嚴重錯誤: {}", message, exception);  // 錯誤
logger.warn("警告: {}", message);                  // 警告
logger.info("重要資訊: {}", message);              // 資訊
logger.debug("除錯資訊: {}", message);             // 除錯

// ✅ 參數化日誌（效能更好）
logger.info("載入 {} 的數據，共 {} 筆", symbol, count);

// ❌ 避免字串拼接
logger.info("載入 " + symbol + " 的數據，共 " + count + " 筆");
```

### 程式碼品質原則

#### SOLID 原則

1. **Single Responsibility**: 每個類別只負責一件事
2. **Open/Closed**: 對擴展開放，對修改封閉
3. **Liskov Substitution**: 子類別可以替換父類別
4. **Interface Segregation**: 介面應該小而專注
5. **Dependency Inversion**: 依賴抽象而非具體實現

#### 設計模式使用

專案中已使用的模式：
- **Strategy Pattern**: `MarketDataFeed` 介面和各種實現
- **Observer Pattern**: `MarketDataListener` 監聽器
- **Factory Pattern**: `ToolBarFactory` 創建 UI 組件
- **Builder Pattern**: 部分配置類使用 Builder
- **Singleton Pattern**: 某些管理類使用單例

---

## 測試要求

### 測試原則

1. **必須測試**: 核心邏輯、數據處理、API 整合
2. **可選測試**: UI 組件（手動測試為主）
3. **測試覆蓋率**: 目標 60%+ （核心模組 80%+）

### 測試結構

```
src/test/java/com/dreamhouse/trading/
├── core/
│   ├── DecisionEngineTest.java          # 決策引擎測試
│   ├── MarketDataCacheTest.java         # 快取測試
│   ├── TestDatabaseLoad.java            # 資料庫載入測試
│   └── DiagnoseDatabaseQuery.java       # SQL 診斷工具
├── strategy/
│   └── SignalStrategyTest.java          # 策略測試
└── util/
    └── TimeUtilsTest.java               # 工具類測試
```

### 測試命名

```java
// 格式：test + 方法名 + 預期結果
@Test
public void testLoadHistoricalData_Success() { }

@Test
public void testLoadHistoricalData_EmptyResult() { }

@Test
public void testLoadHistoricalData_DatabaseError() { }
```

### 執行測試

```bash
# 執行所有測試
mvn test

# 安靜模式（減少輸出）
mvn test -q

# 執行特定測試類
mvn test -Dtest=DecisionEngineTest

# 跳過測試（緊急情況）
mvn package -DskipTests
```

### Pre-commit Hook

專案配置了 Git pre-commit hook，會自動執行測試：

```bash
# .git/hooks/pre-commit
#!/bin/bash
echo "======================================"
echo "Git Pre-Commit Hook: Running Tests"
echo "======================================"
mvn test -q || exit 1
```

如果測試失敗，commit 會被阻止。

---

## 文檔更新規則

### 文檔層次結構

```
README.md                      # 專案概覽、快速開始（對外）
    ↓ 詳細內容連結
PROJECT_DOCUMENTATION.md       # 完整技術文檔（內部）
    ├── 章節 A: 功能說明
    ├── 章節 B: API 文檔
    ├── ...
    └── 章節 I: 資料庫整合與修復記錄
```

### 更新文檔的時機

#### 必須更新

- ✅ 新增主要功能（如資料庫整合）
- ✅ 修改公開 API
- ✅ 修改配置要求（如新增依賴）
- ✅ 重大 Bug 修復（值得記錄）

#### 不需要更新

- ❌ 內部重構（不影響外部使用）
- ❌ 代碼格式調整
- ❌ 小型 Bug 修復
- ❌ 測試代碼更新

### 文檔更新流程

```
1. 完成代碼修改
2. 確認需要更新哪些文檔
3. README.md: 更新功能清單、使用說明
4. PROJECT_DOCUMENTATION.md: 添加新章節或更新現有章節
5. 一起提交代碼和文檔更新
```

### 文檔撰寫規範

#### README.md 規範

- 簡潔明瞭，快速開始為主
- 使用表格、列表提高可讀性
- 添加 emoji 增加視覺效果（適度使用）
- 提供連結到詳細文檔

#### PROJECT_DOCUMENTATION.md 規範

- 詳細完整，技術細節優先
- 使用清晰的章節結構（## 和 ###）
- 包含代碼範例
- 記錄設計決策和權衡
- 提供疑難排解指南

#### 文檔範例

```markdown
## 新功能：資料庫整合

> **新功能** (2025-11-25): 與 MarketDataCollector 整合

### 功能說明

**問題場景**: 當在盤中啟動程式時，缺少開盤後的歷史數據。

**解決方案**:
1. 使用 MarketDataCollector 背景服務收集數據
2. DreamHouseTrading 自動從資料庫載入歷史數據

### 使用方式

\`\`\`bash
# 1. 啟動 MarketDataCollector
cd MarketDataCollector
./啟動真實API收集器.bat

# 2. 啟動 DreamHouseTrading
cd DreamHouseTrading
./啟動-完整編譯.bat
\`\`\`

### 技術細節

詳見 [PROJECT_DOCUMENTATION.md - 第 I 章](PROJECT_DOCUMENTATION.md#i-資料庫整合與修復記錄)
```

---

## 常見任務

### 編譯和運行

```bash
# 清理並編譯
mvn clean compile

# 安靜編譯（推薦）
mvn clean compile -q

# 打包（會執行測試）
mvn package

# 打包（跳過測試）
mvn package -DskipTests

# 執行程式
mvn exec:java

# 或使用批次檔（Windows）
啟動-完整編譯.bat  # 清理、編譯、運行
啟動.bat           # 直接運行（已編譯）
```

### 依賴管理

```bash
# 查看依賴樹
mvn dependency:tree

# 下載依賴
mvn dependency:resolve

# 更新依賴
mvn versions:display-dependency-updates

# 清理本地倉庫快取
mvn dependency:purge-local-repository
```

### 資料庫相關

```bash
# 測試資料庫載入
測試資料庫載入.bat

# 診斷 SQL 查詢
診斷資料庫查詢.bat

# 快速診斷特定股票
快速診斷-6770.bat

# 測試 FinMind API Token
測試FinMind-Token.bat
```

### 清理項目

```bash
# Maven 清理
mvn clean

# 清理日誌文件
del /Q logs\*.log

# 清理臨時文件
del /Q nul
del /Q *.tmp
```

---

## 重要注意事項

### ⚠️ 絕對不要做的事情

1. **❌ 不要直接修改 JAR 文件**
   - `lib/market-data-collector-1.0.0.jar` 來自 MarketDataCollector 專案
   - 需要修改時，去 MarketDataCollector 專案修改並重新編譯

2. **❌ 不要提交敏感資訊**
   - API Keys, Tokens
   - 資料庫密碼
   - 私人配置文件

3. **❌ 不要跳過測試**
   - 除非緊急情況
   - Pre-commit hook 確保測試通過

4. **❌ 不要創建巨大的 Commit**
   - 一次 Commit 應該只做一件事
   - 如果修改太多，拆分成多個 Commit

5. **❌ 不要破壞向後兼容性**
   - 修改公開 API 時要謹慎
   - 考慮deprecation策略

### ✅ 最佳實踐

1. **✅ 在修改前先閱讀代碼**
   ```java
   // 使用 Read 工具先理解現有代碼
   Read: src/main/java/com/dreamhouse/trading/core/FinMindFeed.java
   ```

2. **✅ 優先使用 Edit 而非 Write**
   ```java
   // 好：編輯現有文件
   Edit: FinMindFeed.java

   // 避免：創建新文件（除非必要）
   Write: NewFeatureLoader.java  // 可能不需要
   ```

3. **✅ 保持 Commit 訊息清晰**
   ```bash
   # 好
   feat: 新增資料庫自動載入功能

   # 不好
   Update code
   ```

4. **✅ 編寫可測試的代碼**
   ```java
   // 好：可測試
   public List<Tick> loadData(String symbol) {
       return database.query(symbol);
   }

   // 不好：難以測試
   public void loadAndDisplay(String symbol) {
       List<Tick> ticks = database.query(symbol);
       ui.show(ticks);  // UI 耦合
   }
   ```

5. **✅ 使用日誌而非 System.out**
   ```java
   // 好
   logger.info("載入 {} 完成", symbol);

   // 避免
   System.out.println("載入完成");
   ```

### 🔧 關鍵配置文件

#### pom.xml

- **不要隨意更新依賴版本**（可能引入不兼容）
- 新增依賴時確保 license 相容
- system scope 的依賴需要特別注意路徑

```xml
<!-- 本地 JAR 依賴 -->
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/market-data-collector-1.0.0.jar</systemPath>
</dependency>
```

#### logback.xml

- 控制日誌輸出級別
- 生產環境使用 INFO，開發使用 DEBUG
- 日誌文件會自動輪轉

```xml
<!-- 調整特定類別的日誌級別 -->
<logger name="com.dreamhouse.trading.core" level="DEBUG" />
```

#### datasource.properties

- **不要提交到 Git**（已在 .gitignore）
- 包含 API Keys 和資料庫密碼
- 本地開發使用

```properties
finmind.apitoken=YOUR_TOKEN_HERE
datasource.type=FINMIND
```

---

## 疑難排解

### 常見問題

#### 1. 編譯失敗

**症狀**: `mvn compile` 報錯

**檢查清單**:
- [ ] JDK 版本是否為 17+
- [ ] Maven 版本是否為 3.8+
- [ ] 是否有語法錯誤
- [ ] 依賴是否正確下載

**解決方案**:
```bash
# 清理並重新編譯
mvn clean compile

# 強制更新依賴
mvn clean compile -U
```

#### 2. 資料庫連接失敗

**症狀**: MarketDataLoader 初始化失敗

**檢查清單**:
- [ ] MySQL 服務是否啟動
- [ ] 資料庫 `market_data` 是否存在
- [ ] 連接資訊是否正確

**解決方案**:
```bash
# Windows: 檢查 MySQL 服務
services.msc → 找到 MySQL80 → 啟動

# 測試資料庫連接
測試資料庫載入.bat
```

#### 3. JAR 文件未找到

**症狀**: `NoClassDefFoundError: com/market/collector/query/MarketDataQueryHelper`

**原因**: `lib/market-data-collector-1.0.0.jar` 不存在或版本不對

**解決方案**:
```bash
# 重新編譯 MarketDataCollector
cd C:\Users\chiat\Desktop\測試UI\MarketDataCollector
mvn clean package -DskipTests

# 複製 JAR 到 DreamHouseTrading
copy target\market-data-collector-1.0.0.jar ..\DreamHouseTrading\lib\
```

#### 4. FinMind API 錯誤

**症狀**: `HTTP 400: Token is illegal`

**原因**: Token 無效或格式錯誤

**解決方案**:
```bash
# 測試 Token
測試FinMind-Token.bat

# 檢查配置文件
檢查: datasource.properties 中的 finmind.apitoken
```

#### 5. Git Push 被拒絕

**症狀**: `pre-commit hook failed`

**原因**: 測試未通過

**解決方案**:
```bash
# 手動執行測試查看錯誤
mvn test

# 修復測試錯誤後再提交
git commit ...
```

### 診斷工具

專案提供以下診斷工具：

| 工具 | 用途 | 命令 |
|------|------|------|
| 測試資料庫載入.bat | 測試資料庫連接和載入 | 雙擊執行 |
| 診斷資料庫查詢.bat | 診斷 SQL 查詢問題 | 雙擊執行 |
| 快速診斷-6770.bat | 快速診斷特定股票 | 雙擊執行 |
| 測試FinMind-Token.bat | 測試 FinMind Token | 雙擊執行 |
| 簡易診斷步驟.txt | 分步診斷指南 | 文字閱讀 |

---

## 最後更新記錄

### 2025-11-25

1. **新增資料庫整合功能**
   - 與 MarketDataCollector 整合
   - 自動載入歷史數據
   - 智能股票代號匹配

2. **修復多個問題**
   - 切換商品自動載入修復
   - VARCHAR 時間欄位查詢修復
   - 時間戳解析修復

3. **文檔更新**
   - PROJECT_DOCUMENTATION.md 新增第 I 章
   - README.md 新增資料庫整合說明
   - 創建 CLAUDE.md 專案記憶文件

---

## 📞 需要幫助？

如果遇到問題：

1. 查閱本文件的「疑難排解」章節
2. 查閱 `PROJECT_DOCUMENTATION.md` 的相關章節
3. 查看 Git 提交歷史了解最近的修改
4. 運行診斷工具定位問題

---

**文件版本**: 1.0
**最後更新**: 2025-11-25
**維護者**: DreamHouse Trading Team
