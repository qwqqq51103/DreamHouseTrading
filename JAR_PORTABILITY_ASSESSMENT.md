# MarketDataCollector JAR 依賴可移植性評估報告

**日期**: 2025-11-27
**評估對象**: `lib/market-data-collector-1.0.0.jar`
**目的**: 評估當前 JAR 依賴方式的可移植性問題並提供改善方案

---

## 執行摘要

**當前狀況**: MarketDataCollector JAR 使用 Maven `system` scope 依賴，指向本地檔案路徑。

**核心問題**:
- ❌ 不可移植：新開發者需要手動取得 JAR 檔案
- ❌ Maven 不建議：`system` scope 已被官方標記為不推薦使用
- ❌ CI/CD 困難：持續整合環境需要特殊配置
- ⚠️ 版本管理：JAR 更新需要手動替換檔案

**建議方案**: 安裝到本地 Maven 倉庫（短期）+ 發布到 GitHub Packages（長期）

---

## 1. 現況分析

### 1.1 依賴配置

**pom.xml 配置**:
```xml
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/market-data-collector-1.0.0.jar</systemPath>
</dependency>
```

**exec-maven-plugin 額外配置**:
```xml
<additionalClasspathElements>
    <additionalClasspathElement>
        ${project.basedir}/lib/market-data-collector-1.0.0.jar
    </additionalClasspathElement>
</additionalClasspathElements>
```

### 1.2 使用情況

**使用位置**: `src/main/java/com/dreamhouse/trading/core/MarketDataLoader.java`

**使用的類別**:
1. `com.market.collector.model.Candlestick` - K線數據模型
2. `com.market.collector.model.Quote` - 報價數據模型
3. `com.market.collector.query.MarketDataQueryHelper` - 資料庫查詢輔助類

**JAR 大小**: ~11 MB (可能是 fat JAR，包含所有依賴)

**測試使用**: 無（測試代碼未使用此 JAR）

### 1.3 JAR 內容結構

```
com/market/collector/
├── client/                    # API 客戶端
│   ├── FinMindClient
│   ├── FinMindEnhancedClient
│   ├── MarketApiClient
│   ├── SmartMarketApiClient
│   ├── SmartMarketApiClientV2
│   └── YahooFinanceClient
├── model/                     # 數據模型 ✓ 使用中
│   ├── Candlestick
│   └── Quote
├── query/                     # 查詢輔助 ✓ 使用中
│   └── MarketDataQueryHelper
├── repository/                # 資料庫倉庫
│   └── MarketDataRepository
└── [Main classes]             # 主程式類
```

**實際使用比例**: 僅使用 3 個類別（model 包和 query 包）

---

## 2. 可移植性問題分析

### 2.1 當前問題

#### 問題 1: 新開發者無法自動取得依賴

**症狀**:
- 執行 `mvn clean compile` 會失敗，錯誤訊息：
  ```
  [ERROR] Failed to execute goal on project DreamHouseTrading:
  Could not resolve dependencies for project com.dreamhouse:DreamHouseTrading:jar:0.0.1-SNAPSHOT:
  Cannot access file (C:\Users\...\lib\market-data-collector-1.0.0.jar) from system library
  ```

**影響**:
- 新開發者需要手動從 MarketDataCollector 專案編譯 JAR
- 或需要有人提供 JAR 檔案

#### 問題 2: CI/CD 環境配置困難

**症狀**:
- GitHub Actions / Jenkins 等 CI 環境需要特殊步驟處理 JAR
- 無法使用標準 Maven 流程

**解決方法（目前）**:
- 將 JAR 提交到 Git（增加倉庫大小 11MB）
- 或在 CI 腳本中額外步驟編譯 MarketDataCollector

#### 問題 3: Maven System Scope 已不推薦

**官方說明**:
> System scope is deprecated. Dependencies with system scope will not be included in the test classpath and may cause unexpected behavior.

**問題**:
- Maven 未來版本可能移除 `system` scope
- 與某些 Maven 插件不相容
- 無法傳遞依賴（transitive dependencies）

#### 問題 4: 版本管理困難

**問題**:
- JAR 更新時需要手動替換檔案
- 無法使用 Maven 版本管理功能（如 SNAPSHOT、RELEASE）
- 難以追蹤 JAR 的實際版本和來源

### 2.2 風險評估

| 風險項目 | 嚴重性 | 發生機率 | 影響範圍 |
|---------|--------|---------|---------|
| 新開發者無法編譯專案 | 高 | 高 | 開發流程 |
| CI/CD 配置複雜化 | 中 | 高 | 持續整合 |
| Maven 未來版本不相容 | 中 | 中 | 長期維護 |
| JAR 版本追蹤困難 | 低 | 高 | 版本管理 |
| 倉庫大小增加 | 低 | 中 | Git 效能 |

---

## 3. 解決方案評估

### 方案 1: 安裝到本地 Maven 倉庫 ✅ 推薦（短期）

**說明**: 使用 `mvn install:install-file` 將 JAR 安裝到本地 `~/.m2/repository`

**操作步驟**:
```bash
# 在 DreamHouseTrading 專案根目錄執行
mvn install:install-file \
  -Dfile=lib/market-data-collector-1.0.0.jar \
  -DgroupId=com.market \
  -DartifactId=market-data-collector \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

**pom.xml 修改**:
```xml
<!-- 移除 system scope 和 systemPath -->
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
    <!-- 不需要 scope，預設為 compile -->
</dependency>
```

**優點**:
- ✅ 簡單快速，立即可用
- ✅ 符合 Maven 標準做法
- ✅ 不需要修改太多配置
- ✅ 移除 `system` scope

**缺點**:
- ❌ 每個開發者需要手動執行安裝命令
- ❌ CI 環境也需要執行安裝步驟
- ❌ 無法自動分發給團隊成員

**適用情境**:
- 個人開發或小團隊
- 短期過渡方案

---

### 方案 2: 發布到 GitHub Packages ✅ 推薦（長期）

**說明**: 使用 GitHub Packages 作為 Maven 倉庫，團隊成員可自動下載

**MarketDataCollector 專案配置**:

在 MarketDataCollector 的 `pom.xml` 添加:
```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/qwqqq51103/MarketDataCollector</url>
    </repository>
</distributionManagement>
```

**發布命令**:
```bash
cd MarketDataCollector
mvn deploy -DskipTests
```

**DreamHouseTrading 專案配置**:

在 `pom.xml` 添加倉庫:
```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/qwqqq51103/MarketDataCollector</url>
    </repository>
</repositories>
```

依賴配置（正常方式）:
```xml
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
</dependency>
```

**GitHub Token 配置** (`~/.m2/settings.xml`):
```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

**優點**:
- ✅ 完全符合 Maven 最佳實踐
- ✅ 自動分發給團隊成員
- ✅ CI/CD 環境無縫整合
- ✅ 版本管理清晰（支持 SNAPSHOT、RELEASE）
- ✅ 免費（GitHub 提供）

**缺點**:
- ⚠️ 需要配置 GitHub Token
- ⚠️ 需要在 MarketDataCollector 專案進行配置
- ⚠️ 公開倉庫或需要權限控制

**適用情境**:
- 團隊協作開發
- 需要 CI/CD 的專案
- 長期維護的專案

---

### 方案 3: 發布到 Maven Central

**說明**: 發布到 Maven 中央倉庫，全世界開發者可使用

**優點**:
- ✅ 最高的可移植性
- ✅ 無需額外配置倉庫

**缺點**:
- ❌ 流程複雜（需要註冊 Sonatype、PGP 簽名、域名驗證等）
- ❌ 發布後無法刪除
- ❌ 審核時間較長
- ❌ 對於私有/內部專案不適合

**適用情境**:
- 開源專案
- 提供給外部使用的函式庫

**建議**: ❌ 不建議（過於複雜，且 MarketDataCollector 可能不適合公開）

---

### 方案 4: 包含為 Maven 子模組

**說明**: 將 MarketDataCollector 源碼作為 DreamHouseTrading 的子模組

**結構**:
```
DreamHouseTrading/
├── pom.xml (parent)
├── app/
│   └── pom.xml (DreamHouseTrading application)
└── market-data-collector/
    └── pom.xml (MarketDataCollector module)
```

**優點**:
- ✅ 一次編譯全部模組
- ✅ 完全自給自足，無外部依賴
- ✅ 方便調試和修改

**缺點**:
- ❌ 增加專案複雜度
- ❌ 違反單一職責原則（MarketDataCollector 是獨立專案）
- ❌ 如果 MarketDataCollector 源碼未公開則無法實施

**適用情境**:
- MarketDataCollector 源碼可用
- 兩個專案緊密耦合

**建議**: ⚠️ 考慮（如果 MarketDataCollector 源碼可用且願意合併）

---

### 方案 5: 保持現狀（不推薦）

**說明**: 繼續使用 `system` scope

**優點**:
- ✅ 無需修改

**缺點**:
- ❌ 所有上述可移植性問題持續存在
- ❌ 技術債累積

**建議**: ❌ 不建議

---

## 4. 建議實施方案

### 🎯 推薦方案：兩階段實施

#### 階段 1: 立即實施（短期）- 方案 1
**時程**: 立即（1 小時內）

1. 安裝 JAR 到本地 Maven 倉庫
2. 修改 `pom.xml` 移除 `system` scope
3. 提供團隊成員安裝指南

**效益**:
- 解決 `system` scope 不推薦問題
- 改善本地開發體驗

#### 階段 2: 長期優化（1-2 週內）- 方案 2
**時程**: 1-2 週（需協調 MarketDataCollector 專案）

1. 在 MarketDataCollector 配置 GitHub Packages
2. 發布第一個版本到 GitHub Packages
3. DreamHouseTrading 切換到從 GitHub Packages 下載

**效益**:
- 完全解決可移植性問題
- CI/CD 無縫整合
- 團隊協作更順暢

---

## 5. 實施指南

### 5.1 階段 1 實施步驟

#### Step 1: 安裝 JAR 到本地倉庫

**Windows (cmd)**:
```batch
cd C:\Users\chiat\Desktop\測試UI\DreamHouseTrading

mvn install:install-file ^
  -Dfile=lib\market-data-collector-1.0.0.jar ^
  -DgroupId=com.market ^
  -DartifactId=market-data-collector ^
  -Dversion=1.0.0 ^
  -Dpackaging=jar
```

**驗證安裝**:
```batch
dir %USERPROFILE%\.m2\repository\com\market\market-data-collector\1.0.0
```

應該看到:
```
market-data-collector-1.0.0.jar
market-data-collector-1.0.0.pom
```

#### Step 2: 修改 pom.xml

**移除**:
```xml
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>  <!-- 移除這行 -->
    <systemPath>${project.basedir}/lib/market-data-collector-1.0.0.jar</systemPath>  <!-- 移除這行 -->
</dependency>
```

**改為**:
```xml
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
</dependency>
```

**同時移除 exec-maven-plugin 的額外配置**:
```xml
<!-- 移除這段 -->
<additionalClasspathElements>
    <additionalClasspathElement>
        ${project.basedir}/lib/market-data-collector-1.0.0.jar
    </additionalClasspathElement>
</additionalClasspathElements>
```

#### Step 3: 測試編譯

```batch
mvn clean compile
```

應該成功編譯，無任何錯誤。

#### Step 4: 測試運行

```batch
mvn clean test
```

所有測試應該通過。

#### Step 5: 更新文檔

在 `README.md` 添加「開發環境設置」章節：

```markdown
## 開發環境設置

### 前置要求

1. JDK 17+
2. Maven 3.8+
3. MySQL 8.0+ （如需使用資料庫功能）

### 安裝 MarketDataCollector 依賴

本專案依賴 `market-data-collector-1.0.0.jar`，需要手動安裝到本地 Maven 倉庫：

\`\`\`batch
mvn install:install-file ^
  -Dfile=lib\market-data-collector-1.0.0.jar ^
  -DgroupId=com.market ^
  -DartifactId=market-data-collector ^
  -Dversion=1.0.0 ^
  -Dpackaging=jar
\`\`\`

### 編譯專案

\`\`\`batch
mvn clean compile
\`\`\`

### 運行測試

\`\`\`batch
mvn test
\`\`\`
```

### 5.2 階段 2 實施步驟（可選，需 MarketDataCollector 配合）

請參考附錄 A。

---

## 6. 風險與緩解措施

### 風險 1: JAR 版本不一致

**風險**: 團隊成員安裝的 JAR 版本可能不同

**緩解措施**:
- 在 README 明確指定 JAR 版本
- 提供驗證腳本檢查已安裝版本
- 使用 SHA-256 校驗確保檔案完整性

### 風險 2: CI/CD 環境配置失敗

**風險**: CI 環境忘記執行安裝步驟

**緩解措施**:
- 在 CI 腳本添加安裝步驟
- 使用 Docker 容器預先安裝依賴

### 風險 3: JAR 更新時同步問題

**風險**: MarketDataCollector 更新後，DreamHouseTrading 未同步

**緩解措施**:
- 建立 JAR 更新流程文檔
- 使用語義化版本號
- 階段 2 實施後自動解決

---

## 7. 結論

**當前狀況**: MarketDataCollector JAR 使用 `system` scope 依賴，存在明顯的可移植性問題。

**建議行動**:
1. ✅ **立即執行**: 安裝到本地 Maven 倉庫（方案 1）
2. ✅ **計劃實施**: 發布到 GitHub Packages（方案 2）

**預期效益**:
- 解決 `system` scope 不推薦問題
- 改善團隊協作體驗
- 簡化 CI/CD 流程
- 符合 Maven 最佳實踐

**下一步**: 執行「5.1 階段 1 實施步驟」

---

## 附錄

### 附錄 A: GitHub Packages 完整配置指南

**MarketDataCollector 專案配置**:

1. 修改 `pom.xml` 添加 distribution management:
```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/qwqqq51103/MarketDataCollector</url>
    </repository>
</distributionManagement>
```

2. 創建 GitHub Personal Access Token:
   - Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Generate new token (classic)
   - 勾選: `write:packages`, `read:packages`

3. 配置 Maven settings (`~/.m2/settings.xml`):
```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_TOKEN</password>
    </server>
</servers>
```

4. 發布:
```batch
cd MarketDataCollector
mvn clean deploy -DskipTests
```

**DreamHouseTrading 專案配置**:

1. 修改 `pom.xml` 添加倉庫:
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/qwqqq51103/MarketDataCollector</url>
    </repository>
</repositories>
```

2. 依賴正常使用:
```xml
<dependency>
    <groupId>com.market</groupId>
    <artifactId>market-data-collector</artifactId>
    <version>1.0.0</version>
</dependency>
```

3. 團隊成員也需要配置相同的 `~/.m2/settings.xml`

### 附錄 B: JAR 校驗

**生成 SHA-256 校驗碼**:
```batch
powershell Get-FileHash lib\market-data-collector-1.0.0.jar -Algorithm SHA256
```

**記錄校驗碼** (在 README 或文檔中):
```
SHA-256: [校驗碼]
```

團隊成員下載 JAR 後可驗證完整性。

---

**文件版本**: 1.0
**作者**: DreamHouse Trading Team
**最後更新**: 2025-11-27
