# DreamHouse Trading 開發流程指南

本文檔說明如何將測試整合到日常開發流程中，確保代碼品質。

---

## 🔄 完整開發流程

```
1. 編寫代碼
    ↓
2. 撰寫測試
    ↓
3. 執行測試
    ↓
4. 查看覆蓋率
    ↓
5. Git 提交（自動執行測試）
    ↓
6. 推送到遠端
```

---

## 📋 日常開發步驟

### Step 1: 開發新功能

```java
// 1. 編寫業務代碼
public class NewFeature {
    public int calculate(int a, int b) {
        return a + b;
    }
}
```

### Step 2: 撰寫測試

```java
// 2. 撰寫單元測試
@Test
void testCalculate() {
    NewFeature feature = new NewFeature();
    assertThat(feature.calculate(2, 3)).isEqualTo(5);
}
```

### Step 3: 執行測試

```bash
# 方法 1: 使用批次文件
run-tests.bat

# 方法 2: 使用 Maven
mvn test

# 方法 3: 使用 IDE
右鍵點擊測試類 → Run Test
```

### Step 4: 查看覆蓋率

```bash
# 生成並查看覆蓋率報告
run-coverage.bat

# 或使用 Maven
mvn clean test
# 報告在 target/site/jacoco/index.html
```

### Step 5: 提交代碼

```bash
# Git Hook 會自動執行測試
git add .
git commit -m "feat: 新增計算功能"

# 如果測試失敗，提交會被阻止
# 修復測試後再次提交
```

---

## 🛠️ 工具配置

### 1. Git Hooks（自動化測試）

**安裝步驟：**

```bash
# 執行安裝腳本
setup-git-hooks.bat
```

**效果：**
- ✅ 每次 `git commit` 前自動執行測試
- ✅ 測試失敗時阻止提交
- ✅ 確保提交的代碼都經過測試

**跳過測試提交（不推薦）：**
```bash
git commit --no-verify -m "message"
```

### 2. JaCoCo（代碼覆蓋率）

**已配置在 pom.xml：**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
</plugin>
```

**使用方式：**
```bash
# 生成覆蓋率報告
run-coverage.bat

# 或
mvn clean test
start target\site\jacoco\index.html
```

### 3. IDE 整合

#### IntelliJ IDEA

**執行測試：**
- 右鍵測試類 → Run 'TestClass'
- 快捷鍵：Ctrl+Shift+F10

**查看覆蓋率：**
- 右鍵測試類 → Run 'TestClass' with Coverage
- 快捷鍵：Ctrl+Shift+F10（with Coverage）

**設置自動測試：**
1. File → Settings → Build, Execution, Deployment → Compiler
2. 勾選 "Build project automatically"

#### NetBeans

**執行測試：**
- 右鍵專案 → Test
- 快捷鍵：Alt+F6

**查看結果：**
- Test Results 面板會自動顯示

---

## 📊 代碼覆蓋率目標

| 模組類型 | 目標覆蓋率 | 說明 |
|---------|-----------|------|
| 核心業務邏輯 | 90%+ | Trade, Position, Portfolio 等 |
| 工具類 | 80%+ | AdvancedStopLossManager 等 |
| 策略類 | 70%+ | SMA, RSI, MACD 等策略 |
| UI 類 | 30%+ | Swing 組件（較難測試） |
| **整體目標** | **70%+** | 專案總體覆蓋率 |

**當前狀態：**
- ✅ 核心模型類：100%
- ⏳ 策略類：進行中
- ❌ UI 類：0%

---

## 🔍 代碼審查清單

### 提交前檢查

- [ ] 所有測試通過（綠燈）
- [ ] 新代碼有對應的測試
- [ ] 覆蓋率沒有下降
- [ ] 沒有破壞現有測試
- [ ] 代碼符合專案規範

### 測試品質檢查

- [ ] 測試名稱清晰描述測試內容
- [ ] 測試包含正常情況和異常情況
- [ ] 測試獨立且可重複執行
- [ ] 使用有意義的斷言訊息
- [ ] 避免測試之間的相互依賴

---

## 🚀 快速參考

### 常用命令

```bash
# 執行測試
run-tests.bat                  # Windows 批次文件
mvn test                       # Maven 命令

# 查看覆蓋率
run-coverage.bat              # 生成並打開報告
mvn jacoco:report             # 只生成報告

# Git 操作
git add .                      # 添加所有變更
git commit -m "message"        # 提交（自動測試）
git commit --no-verify -m ""   # 跳過測試提交

# 安裝工具
setup-git-hooks.bat           # 安裝 Git Hooks
```

### 快捷腳本

| 腳本 | 功能 | 用途 |
|------|------|------|
| `run-tests.bat` | 執行所有測試 | 快速驗證代碼 |
| `run-coverage.bat` | 生成覆蓋率報告 | 查看測試覆蓋率 |
| `setup-git-hooks.bat` | 安裝 Git Hooks | 自動化測試流程 |

---

## 💡 最佳實踐

### ✅ 推薦做法

1. **先寫測試，再寫代碼（TDD）**
   ```java
   @Test void testNewFeature() { ... }  // 先寫測試
   public void newFeature() { ... }     // 再實作功能
   ```

2. **小步提交**
   - 每個功能完成後立即提交
   - 提交訊息清晰描述變更

3. **定期查看覆蓋率**
   - 每週至少查看一次覆蓋率報告
   - 優先為核心功能補充測試

4. **保持測試快速**
   - 單元測試應該在秒級完成
   - 慢測試考慮重構或移到集成測試

### ❌ 避免的做法

1. **不寫測試就提交**
   - Git Hook 會阻止，但不要依賴它
   - 養成先寫測試的習慣

2. **為了覆蓋率而測試**
   - 測試應該驗證行為，不只是執行代碼
   - 有意義的測試 > 高覆蓋率

3. **跳過失敗的測試**
   - 不要註解掉失敗的測試
   - 修復測試或修復代碼

4. **測試依賴外部資源**
   - 測試應該獨立運行
   - 使用 Mock 替代外部依賴

---

## 🔧 故障排除

### 問題 1: Git Hook 不執行

**症狀：** 提交時沒有自動執行測試

**解決方案：**
```bash
# 重新安裝 Git Hooks
setup-git-hooks.bat

# 檢查 hook 文件存在
dir .git\hooks\pre-commit
```

### 問題 2: 測試執行緩慢

**症狀：** 測試需要很長時間

**解決方案：**
```bash
# 只執行特定測試
mvn test -Dtest=TradeTest

# 跳過集成測試
mvn test -DskipITs
```

### 問題 3: 覆蓋率報告無法打開

**症狀：** `target/site/jacoco/index.html` 不存在

**解決方案：**
```bash
# 確保執行了測試
mvn clean test

# 手動生成報告
mvn jacoco:report
```

### 問題 4: Maven 找不到

**症狀：** `mvn: command not found`

**解決方案：**
```bash
# 使用完整路徑
"C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" test

# 或使用批次文件
run-tests.bat
```

---

## 📚 進階主題

### 持續整合（CI）

將來可以配置 GitHub Actions：

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: mvn test
      - uses: codecov/codecov-action@v3
```

### 性能測試

對於性能敏感的代碼，可以添加性能測試：

```java
@Test
void testPerformance() {
    long start = System.currentTimeMillis();
    // 執行操作
    long duration = System.currentTimeMillis() - start;
    assertThat(duration).isLessThan(1000); // 1 秒內完成
}
```

### 集成測試

對於需要多個組件協作的測試：

```java
@SpringBootTest  // 如果使用 Spring
class IntegrationTest {
    @Test
    void testEndToEnd() {
        // 完整流程測試
    }
}
```

---

## 🎯 下一步行動

1. ✅ 安裝 Git Hooks：`setup-git-hooks.bat`
2. ✅ 執行測試：`run-tests.bat`
3. ✅ 查看覆蓋率：`run-coverage.bat`
4. ⏳ 為低覆蓋率模組補充測試
5. ⏳ 達到 70% 整體覆蓋率目標

---

## 📞 需要幫助？

- 測試撰寫指南：`UNIT_TESTS_SUMMARY.md`
- 覆蓋率指南：`CODE_COVERAGE_GUIDE.md`
- 代碼改進案例：`CODE_IMPROVEMENTS.md`

---

**更新日期**: 2025-01-09
**工具版本**: JUnit 5.10.1, JaCoCo 0.8.11
**流程狀態**: ✅ 已配置完成
