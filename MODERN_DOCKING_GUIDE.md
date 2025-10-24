# Modern Docking 使用指南

## 檢查清單

### 1. 檢查依賴是否下載成功

在 NetBeans 中：
1. 右鍵點擊專案 → Properties
2. 選擇 Libraries
3. 查看 Compile Classpath 中是否有 `modern-docking-0.11.3.jar`

或在命令列執行（需要配置 Maven）：
```bash
"C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" dependency:list | findstr modern-docking
```

### 2. 如果依賴未下載

在 NetBeans 中：
1. 右鍵點擊專案
2. Build → Clean and Build Project
3. 等待 Maven 自動下載所有依賴

### 3. 檢查 Import 語句

正確的 Modern Docking import：
```java
import modern.docking.Docking;
import modern.docking.DockingRegion;
import modern.docking.app.Dockable;
import modern.docking.app.RootDockingPanel;
```

### 4. 替代方案

如果 Modern Docking 持續有問題，建議使用：
- **MainFrameAdvanced.java**（已實作，使用 JSplitPane）
- 優點：不需額外依賴、穩定、可調整大小
- 缺點：無法拖曳面板到任意位置

## Modern Docking 版本說明

| 版本 | Artifact ID | 狀態 |
|------|-------------|------|
| 0.11.3 | modern-docking | ✅ 推薦（穩定版） |
| 0.12.0 | modern-docking-single-app | ❌ 可能不穩定 |

## 測試步驟

1. 確認 pom.xml 使用 `modern-docking` version `0.11.3`
2. 在 NetBeans 執行 Clean and Build
3. 檢查編譯錯誤是否消失
4. 如果仍有錯誤，使用 MainFrameAdvanced.java

