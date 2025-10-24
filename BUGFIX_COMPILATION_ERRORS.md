# Bug 修復：編譯錯誤

## 📅 日期：2025-10-25

## 🐛 問題描述

### 錯誤 1: MenuBarFactory 簽名不匹配

**錯誤訊息：**
```
com/dreamhouse/trading/ui/MainFrame.java:[38,35] 
method createMenuBar in class com.dreamhouse.trading.ui.MenuBarFactory cannot be applied to given types;
  required: javax.swing.JFrame,com.dreamhouse.trading.ui.MenuBarFactory.Callbacks
  found:    com.dreamhouse.trading.ui.MainFrame
  reason: actual and formal argument lists differ in length
```

**原因：**
- `MenuBarFactory.createMenuBar()` 的簽名被修改為需要兩個參數
- `MainFrame.java` 仍使用舊的單參數調用方式

### 錯誤 2: StatusBar 缺少 setText 方法

**錯誤訊息：**
```
com/dreamhouse/trading/ui/MainFrameWithDocking.java:[363,22] 
cannot find symbol
  symbol:   method setText(java.lang.String)
  location: variable statusBar of type com.dreamhouse.trading.ui.StatusBar
```

**原因：**
- `MainFrameWithDocking` 中調用了 `statusBar.setText()`
- `StatusBar` 類別沒有這個方法

---

## ✅ 解決方案

### 修復 1: 更新 MainFrame.java

**修改前：**
```java
setJMenuBar(MenuBarFactory.createMenuBar(this));
```

**修改後：**
```java
MenuBarFactory.Callbacks menuCallbacks = new MenuBarFactory.Callbacks();
// MainFrame 不需要指標設定回調（使用舊版 UI）
setJMenuBar(MenuBarFactory.createMenuBar(this, menuCallbacks));
```

**說明：**
- 創建空的 `Callbacks` 物件
- 傳遞給 `createMenuBar()` 方法
- 保持與新 API 兼容

---

### 修復 2: 擴展 StatusBar 功能

#### 2.1 添加訊息標籤

**修改：** `StatusBar.java`

```java
public class StatusBar extends JPanel {
    private final JLabel connectionLabel;
    private final JLabel priceLabel;
    private final JLabel symbolLabel;
    private final JLabel timeframeLabel;
    private final JLabel fpsLabel;
    private final JLabel messageLabel;  // 新增
    
    public StatusBar() {
        // ... 其他初始化 ...
        
        add(new JSeparator(SwingConstants.VERTICAL));
        
        messageLabel = new JLabel("");  // 新增
        messageLabel.setForeground(new Color(100, 100, 100));
        add(messageLabel);
    }
}
```

#### 2.2 添加公開方法

```java
/**
 * 設定通用狀態訊息
 * @param message 要顯示的訊息
 */
public void setText(String message) {
    messageLabel.setText(message);
}

/**
 * 清除狀態訊息
 */
public void clearMessage() {
    messageLabel.setText("");
}
```

---

## 📊 StatusBar 功能擴展

### 修改前

`StatusBar` 只支援特定類型的狀態顯示：
- 連線狀態
- 商品代號
- 最新價格
- 時間框
- FPS

### 修改後

新增通用訊息區域，支援：
- ✅ CSV 匯入成功訊息
- ✅ 自訂狀態訊息
- ✅ 臨時通知
- ✅ 錯誤提示

### 使用範例

```java
// CSV 匯入成功
statusBar.setText("已載入 30 條歷史 K 線");

// 自訂訊息
statusBar.setText("數據正在處理中...");

// 清除訊息
statusBar.clearMessage();
```

---

## 🎨 UI 效果

### StatusBar 佈局（修改後）

```
[●連線狀態] | [商品: AAPL] [最新價: 150.25] [1m] | [FPS: 60] | [已載入 30 條歷史 K 線]
```

- 灰色文字顯示通用訊息
- 不影響其他狀態顯示
- 可動態更新

---

## 🔍 修改檔案清單

1. **`MainFrame.java`** - 更新 MenuBar 創建方式
2. **`StatusBar.java`** - 添加訊息標籤與 setText() 方法

---

## ✅ 驗證結果

### 編譯狀態

✅ **所有錯誤已修復**

⚠️ 僅剩警告（不影響功能）：
- 未使用的 import
- GlazedLists 過時的 API
- 未使用的變數

### 功能驗證

✅ `MainFrame` 可正常編譯  
✅ `MainFrameWithDocking` 可正常編譯  
✅ `StatusBar.setText()` 可用  
✅ 與現有功能兼容  

---

## 💡 未來改進建議

### 1. 統一 MainFrame 與 MainFrameWithDocking

目前有兩個主窗口實作：
- `MainFrame` - 舊版，不支援 Modern Docking
- `MainFrameWithDocking` - 新版，支援面板拖曳

**建議：**
- 保留 `MainFrameWithDocking` 作為唯一入口
- 或將 `MainFrame` 標記為 deprecated

### 2. StatusBar 自動清除機制

```java
public void setText(String message, int durationMs) {
    messageLabel.setText(message);
    
    Timer timer = new Timer(durationMs, e -> clearMessage());
    timer.setRepeats(false);
    timer.start();
}
```

使用範例：
```java
statusBar.setText("CSV 匯入成功！", 3000);  // 3 秒後自動清除
```

### 3. StatusBar 訊息類型

支援不同類型的訊息顏色：

```java
public enum MessageType {
    INFO,    // 灰色
    SUCCESS, // 綠色
    WARNING, // 橙色
    ERROR    // 紅色
}

public void setText(String message, MessageType type) {
    messageLabel.setText(message);
    
    switch (type) {
        case INFO:    messageLabel.setForeground(Color.GRAY); break;
        case SUCCESS: messageLabel.setForeground(Color.GREEN); break;
        case WARNING: messageLabel.setForeground(Color.ORANGE); break;
        case ERROR:   messageLabel.setForeground(Color.RED); break;
    }
}
```

---

## 📝 測試清單

### 編譯測試

- [x] `MainFrame.java` 編譯通過
- [x] `MainFrameWithDocking.java` 編譯通過
- [x] `StatusBar.java` 編譯通過
- [x] 無編譯錯誤

### 功能測試

- [ ] CSV 匯入後狀態列顯示訊息
- [ ] 訊息格式正確
- [ ] 訊息顏色正常
- [ ] 不影響其他狀態顯示

---

## 🔗 相關文檔

- `BUGFIX_EDT_ISSUE.md` - EDT 執行緒錯誤修復
- `PHASE_2_CSV_MANAGEMENT.md` - CSV 管理功能詳情
- `USER_GUIDE_NEW_FEATURES.md` - 新功能使用指南

---

**修復日期：** 2025-10-25  
**影響範圍：** UI 框架、狀態列  
**優先級：** 🔴 高（阻礙編譯）  
**狀態：** ✅ 已修復

