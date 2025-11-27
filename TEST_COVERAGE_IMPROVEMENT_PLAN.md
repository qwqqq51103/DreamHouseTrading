# 測試覆蓋率提升計畫

**目標**: 將測試覆蓋率從當前水平提升至 60%+
**預估時間**: 8-16 小時
**狀態**: 規劃中
**日期**: 2025-11-27

---

## 執行摘要

本計畫旨在系統性地提升 DreamHouseTrading 專案的測試覆蓋率至 60% 以上，確保核心功能的穩定性和可維護性。

**當前狀況**:
- ✅ JaCoCo 代碼覆蓋率工具已配置（pom.xml）
- ✅ 已有 103+ 測試案例
- ⚠️ 當前覆蓋率: 需要進一步確認（pom.xml 設定最低 30%）
- 🎯 目標覆蓋率: 60%+

**策略**:
1. **階段 1**: 確認當前覆蓋率並識別優先級
2. **階段 2**: 為核心模組添加測試（目標 80%+）
3. **階段 3**: 為次要模組添加測試（目標 40%+）
4. **階段 4**: 驗證總體覆蓋率達標

---

## 階段 1: 當前覆蓋率分析

### 1.1 生成覆蓋率報告

**命令**:
```bash
mvn clean test jacoco:report
```

**報告位置**:
- HTML 報告: `target/site/jacoco/index.html`
- XML 報告: `target/site/jacoco/jacoco.xml`
- CSV 報告: `target/site/jacoco/jacoco.csv`

### 1.2 識別低覆蓋率模組

**優先處理區域**（推測）:
1. **核心邏輯**（目標 80%+）:
   - `com.dreamhouse.trading.core.DecisionEngine`
   - `com.dreamhouse.trading.core.decision.*`
   - `com.dreamhouse.trading.core.backtest.*`
   - `com.dreamhouse.trading.core.MarketDataLoader`

2. **數據源**（目標 60%+）:
   - `com.dreamhouse.trading.core.FinMindFeed`
   - `com.dreamhouse.trading.core.IEXCloudFeed`
   - `com.dreamhouse.trading.core.PolygonFeed`
   - `com.dreamhouse.trading.core.AlphaVantageFeed`

3. **策略系統**（目標 70%+）:
   - `com.dreamhouse.trading.core.decision.strategies.*`
   - `com.dreamhouse.trading.core.decision.voting.*`
   - `com.dreamhouse.trading.core.decision.risk.*`

4. **模型類別**（目標 90%+）:
   - `com.dreamhouse.trading.core.model.*`
   - `com.dreamhouse.trading.core.Timeframe`

5. **工具類**（目標 80%+）:
   - `com.dreamhouse.trading.util.*`

**可忽略區域**:
- UI 組件（`com.dreamhouse.trading.ui.*`）- 手動測試為主
- Main 類和啟動類
- 範例代碼（`com.dreamhouse.trading.examples.*`）

### 1.3 分析覆蓋率缺口

**執行步驟**:
1. 開啟 `target/site/jacoco/index.html` 查看總體覆蓋率
2. 按套件（Package）查看覆蓋率分布
3. 識別覆蓋率 < 50% 的類別
4. 列出未覆蓋的關鍵方法

**記錄格式**:
```
套件：com.dreamhouse.trading.core
覆蓋率：45%
未覆蓋類別：
- DecisionEngine: 38% (缺少多週期整合測試)
- MarketDataLoader: 25% (缺少錯誤處理測試)
...
```

---

## 階段 2: 核心模組測試（目標 80%+）

### 2.1 DecisionEngine 測試強化

**當前狀況**: 已有基本測試（`DecisionEngineTest.java`）

**需要添加的測試**:

1. **多週期數據聚合測試**
```java
@Test
public void testMultiTimeframeAggregation() {
    // 測試 5分鐘 -> 15分鐘 -> 1小時 的聚合
    // 驗證數據正確性和K線數量
}
```

2. **極端市場條件測試**
```java
@Test
public void testExtremeBearMarket() {
    // 測試連續下跌20%的情況
    // 驗證風控是否正確觸發
}

@Test
public void testHighVolatilityMarket() {
    // 測試高波動市場（日內波動 >10%）
    // 驗證策略行為
}
```

3. **錯誤恢復測試**
```java
@Test
public void testRecoveryFromInvalidData() {
    // 測試接收到無效數據時的處理
    // 驗證系統不會崩潰
}
```

### 2.2 MarketDataLoader 測試

**當前狀況**: 有基本測試（`TestDatabaseLoad.java`）

**需要添加的測試**:

1. **資料庫連接失敗測試**
```java
@Test
public void testDatabaseConnectionFailure() {
    // 使用錯誤的連接字串
    // 驗證優雅降級到 API
}
```

2. **SQL 注入防護測試**
```java
@Test
public void testSQLInjectionPrevention() {
    // 測試惡意的股票代號輸入
    // 驗證參數化查詢正確工作
}
```

3. **時區處理測試**
```java
@Test
public void testTimezoneHandling() {
    // 測試不同時區的數據載入
    // 驗證時間轉換正確
}
```

### 2.3 策略系統測試

**重點類別**:
- `MultiTimeframeDecisionStrategy`
- `DayTradingStrategy`
- `SwingTradingStrategy`
- `PositionTradingStrategy`
- `MultiStyleStrategyManager`

**測試案例模板**:
```java
@Test
public void testDayTradingStrategy_BullMarket() {
    DayTradingStrategy strategy = new DayTradingStrategy();
    BarSeries bullMarket = TestDataGenerator.generateBullMarket(100);

    // 執行回測
    BacktestResult result = runBacktest(strategy, bullMarket);

    // 驗證：
    // 1. 有執行交易
    assertThat(result.getTradeCount()).isGreaterThan(0);
    // 2. 勝率 > 50%
    assertThat(result.getWinRate()).isGreaterThan(0.5);
    // 3. 日內全部平倉
    assertThat(result.getOpenPositions()).isEmpty();
}
```

**需要添加的測試**:
1. 每個策略的基本功能測試（多頭、空頭、震盪）
2. 資金分配測試（MultiStyleStrategyManager）
3. 策略切換測試
4. 參數敏感度測試

---

## 階段 3: 次要模組測試（目標 40%+）

### 3.1 數據源測試

**重點**:
- 網路錯誤處理
- API 限流處理
- 數據格式驗證

**範例測試**:
```java
@Test
public void testFinMindFeed_RateLimiting() {
    FinMindFeed feed = new FinMindFeed("test-token");

    // 快速連續請求 100 次
    for (int i = 0; i < 100; i++) {
        feed.fetchQuote("2330.TW");
    }

    // 驗證不會因為超過限流而崩潰
    // 驗證有適當的重試機制
}
```

### 3.2 投票系統測試

**重點類別**:
- `VotingEngine`
- `VotingResult`
- `VotingConfig`

**需要添加的測試**:
```java
@Test
public void testVotingEngine_MinimumVotersNotMet() {
    // 配置需要至少 3 個策略投票
    // 但只有 2 個策略
    // 驗證返回 HOLD
}

@Test
public void testVotingEngine_WeightedVoting() {
    // 策略 A: 權重 1.0, 投票 LONG
    // 策略 B: 權重 0.5, 投票 SHORT
    // 驗證加權計算正確
}
```

### 3.3 風險管理測試

**重點類別**:
- `RiskManager`
- `RiskConfig`
- `PositionSizer`

**需要添加的測試**:
```java
@Test
public void testRiskManager_DailyLossLimit() {
    RiskManager rm = new RiskManager(riskConfig);

    // 模擬當日虧損達到 3%
    rm.recordLoss(3000); // 假設初始資金 100,000

    // 驗證拒絕新交易
    assertThat(rm.canOpenNewPosition()).isFalse();
}

@Test
public void testPositionSizer_MaxPositionSize() {
    PositionSizer sizer = new PositionSizer(config);

    // 測試最大倉位限制
    int quantity = sizer.calculateQuantity(
        100000, // 資金
        150.0,  // 價格
        0.02    // 2% 風險
    );

    // 驗證不超過最大倉位
    double positionValue = quantity * 150.0;
    assertThat(positionValue / 100000).isLessThan(0.3); // 假設最大 30%
}
```

---

## 階段 4: 整合測試與驗證

### 4.1 端對端測試

**測試場景**:
1. **完整回測流程**
```java
@Test
public void testEndToEndBacktest() {
    // 1. 載入歷史數據（資料庫 + API）
    // 2. 初始化多風格策略管理器
    // 3. 執行完整回測（100 天）
    // 4. 驗證結果合理性
    // 5. 生成報告
}
```

2. **數據源切換測試**
```java
@Test
public void testDataSourceFallback() {
    // 1. 主數據源（FinMind）失敗
    // 2. 自動切換到備用數據源（Yahoo）
    // 3. 驗證數據連續性
}
```

### 4.2 效能測試

```java
@Test
public void testDecisionEnginePerformance() {
    // 測試處理 10,000 根 K 線的時間
    // 要求 < 5 秒

    long startTime = System.currentTimeMillis();

    engine.processBars(largeDataset);

    long duration = System.currentTimeMillis() - startTime;
    assertThat(duration).isLessThan(5000);
}
```

### 4.3 覆蓋率驗證

**執行步驟**:
```bash
# 1. 執行所有測試並生成報告
mvn clean test jacoco:report

# 2. 檢查覆蓋率
# - 打開 target/site/jacoco/index.html
# - 檢查 Overall Coverage

# 3. 驗證目標達成
# - 整體覆蓋率 >= 60%
# - 核心模組覆蓋率 >= 80%
# - 無遺漏的關鍵路徑
```

**如果未達標**:
- 識別仍然低於目標的模組
- 回到階段 2 或 3 補充測試

---

## 測試最佳實踐

### 1. 命名規範

```java
// 格式: test + 方法名 + 情境 + 預期結果
@Test
public void testCalculatePosition_InsufficientFunds_ReturnsZero() { }

@Test
public void testVoting_AllLongSignals_ReturnsLong() { }
```

### 2. AAA 模式

```java
@Test
public void testExample() {
    // Arrange: 準備測試數據和環境
    BarSeries series = TestData.generateSeries(100);
    Strategy strategy = new DayTradingStrategy();

    // Act: 執行被測試的行為
    Decision decision = strategy.makeDecision(series, 99);

    // Assert: 驗證結果
    assertThat(decision.getAction()).isEqualTo(Action.LONG);
    assertThat(decision.getConfidence()).isGreaterThan(0.6);
}
```

### 3. 使用測試工具類

**建議創建**:
```java
public class TestDataGenerator {
    public static BarSeries generateBullMarket(int bars) { }
    public static BarSeries generateBearMarket(int bars) { }
    public static BarSeries generateSidewaysMarket(int bars) { }
    public static List<Tick> generateTicks(int count) { }
}
```

### 4. Mock 外部依賴

```java
@Test
public void testFinMindFeed_NetworkError() {
    // 使用 Mockito mock HTTP 客戶端
    HttpClient mockClient = mock(HttpClient.class);
    when(mockClient.get(anyString())).thenThrow(new IOException());

    FinMindFeed feed = new FinMindFeed(mockClient, "token");

    // 驗證優雅處理錯誤
    assertThatCode(() -> feed.fetchQuote("2330.TW"))
        .doesNotThrowAnyException();
}
```

---

## 實施時程規劃

### 第 1 天（2-3 小時）
- ✅ 生成並分析當前覆蓋率報告
- ✅ 識別優先級模組
- ✅ 創建 TestDataGenerator 工具類

### 第 2-3 天（6-8 小時）
- ✅ 完成核心模組測試（階段 2）
  - DecisionEngine: 2 小時
  - MarketDataLoader: 1 小時
  - 策略系統: 3-4 小時

### 第 4 天（3-4 小時）
- ✅ 完成次要模組測試（階段 3）
  - 數據源: 1 小時
  - 投票系統: 1 小時
  - 風險管理: 1-2 小時

### 第 5 天（2-3 小時）
- ✅ 整合測試（階段 4.1, 4.2）
- ✅ 覆蓋率驗證（階段 4.3）
- ✅ 補充不足的測試

**總計**: 13-18 小時（含緩衝時間）

---

## 預期成果

### 量化指標
- ✅ 整體測試覆蓋率 >= 60%
- ✅ 核心模組覆蓋率 >= 80%
- ✅ 測試案例數量: 200+ （當前 103+）
- ✅ 所有測試通過
- ✅ CI/CD 整合（pre-commit hook 自動執行）

### 質化指標
- ✅ 關鍵業務邏輯有完整測試覆蓋
- ✅ 錯誤處理路徑有測試
- ✅ 邊界條件有測試
- ✅ 回歸測試可防止已修復的 Bug 再次出現

---

## 風險與緩解措施

### 風險 1: 測試運行時間過長

**問題**: 測試案例增加後，運行時間可能 > 5 分鐘，影響開發效率

**緩解**:
- 使用 JUnit 5 的 `@Tag` 分類測試
- 區分快速測試（< 1s）和慢速測試（> 1s）
- Pre-commit hook 只運行快速測試
- CI 運行全部測試

### 風險 2: Mock 過度使用

**問題**: 過度使用 Mock 可能導致測試不真實

**緩解**:
- 優先使用真實依賴（如 H2 in-memory database）
- 只在必要時 Mock（外部 API、網路請求）
- 保留部分整合測試

### 風險 3: 測試維護成本

**問題**: 測試代碼可能變得難以維護

**緩解**:
- 使用測試工具類（TestDataGenerator）減少重複代碼
- 保持測試簡潔（每個測試一個斷言點）
- 定期重構測試代碼

---

## 附錄

### A. JaCoCo 配置檢查

**pom.xml 現有配置**:
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.30</minimum>  <!-- 當前: 30% -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**建議修改**:
```xml
<minimum>0.60</minimum>  <!-- 更新為 60% -->
```

### B. 測試工具依賴

**確認已包含**:
- ✅ JUnit 5 (jupiter)
- ✅ Mockito
- ✅ AssertJ

**建議添加**:
```xml
<!-- ArchUnit: 架構測試 -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.2.0</version>
    <scope>test</scope>
</dependency>

<!-- TestContainers: 如需測試實際資料庫 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### C. 參考資源

- [JaCoCo Maven Plugin Documentation](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Assertions](https://assertj.github.io/doc/)

---

**文件版本**: 1.0
**作者**: DreamHouse Trading Team
**最後更新**: 2025-11-27
