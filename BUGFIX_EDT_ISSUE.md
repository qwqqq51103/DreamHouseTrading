# Bug 修復：Event Dispatch Thread (EDT) 錯誤

## 🐛 問題描述

**錯誤訊息：**
```
Exception in thread "AWT-EventQueue-0" java.lang.Error: 
Cannot call invokeAndWait from the event dispatcher thread
```

**發生位置：**
- `SimulatorFeed.generateHistoricalData()`
- 在 `MainFrameWithDocking` 構造函數中調用 `dataFeed.start()`

**根本原因：**
- `MainFrameWithDocking` 的構造函數在 EDT 中執行
- `start()` 方法直接調用 `generateHistoricalData()`
- `generateHistoricalData()` 使用 `SwingUtilities.invokeAndWait()`
- **規則：不能在 EDT 中調用 `invokeAndWait()`**

---

## ✅ 解決方案

### 修改檔案：`SimulatorFeed.java`

#### 1. 將歷史數據生成移到背景執行緒

**修改前：**
```java
@Override
public void start() {
    connected = true;
    generateHistoricalData();  // 在 EDT 中執行
    executor.scheduleAtFixedRate(...);
}
```

**修改後：**
```java
@Override
public void start() {
    connected = true;
    
    // 在背景執行緒中生成歷史數據，避免阻塞 EDT
    new Thread(() -> {
        generateHistoricalData();
        
        // 歷史數據生成完畢後，開始即時數據更新
        executor.scheduleAtFixedRate(this::generateMarketData, 0, 1000, TimeUnit.MILLISECONDS);
    }, "HistoricalDataGenerator").start();
}
```

#### 2. 改用 `invokeLater` 而非 `invokeAndWait`

**修改前：**
```java
SwingUtilities.invokeAndWait(() -> {
    for (MarketDataListener listener : symbolListeners) {
        listener.onTick(tick);
    }
});
```

**修改後：**
```java
SwingUtilities.invokeLater(() -> {
    for (MarketDataListener listener : symbolListeners) {
        listener.onTick(tick);
    }
});
```

---

## 🔍 技術細節

### Event Dispatch Thread (EDT) 規則

1. **所有 Swing UI 操作必須在 EDT 中執行**
2. **不能在 EDT 中調用 `invokeAndWait()`**
3. **長時間運行的任務應在背景執行緒執行**

### `invokeLater` vs `invokeAndWait`

| 方法 | 阻塞 | 使用場景 |
|------|------|----------|
| `invokeLater` | 否 | 異步更新 UI，不需要等待結果 |
| `invokeAndWait` | 是 | 同步更新 UI，需要等待結果（但不能在 EDT 中調用） |

### 修復後的執行流程

```
MainFrameWithDocking 構造函數（在 EDT 中）
    ↓
dataFeed.start()
    ↓
創建背景執行緒 "HistoricalDataGenerator"
    ↓
    ├─ EDT 繼續執行（UI 不阻塞）
    └─ 背景執行緒執行 generateHistoricalData()
        ↓
        生成 50 條 K 線（每個 tick 使用 invokeLater）
        ↓
        啟動定時任務（每秒更新即時數據）
```

---

## 🧪 驗證

### 測試步驟

1. 啟動應用程式：
   ```bash
   mvn clean compile exec:java
   ```

2. **預期行為：**
   - ✅ 應用程式正常啟動
   - ✅ UI 立即顯示（不凍結）
   - ✅ 約 2 秒後顯示 50 條歷史 K 線
   - ✅ 繼續每秒更新即時數據

3. **不應出現的錯誤：**
   - ❌ `Cannot call invokeAndWait from the event dispatcher thread`

---

## 📊 性能影響

### 修改前
- ❌ UI 凍結約 2 秒（等待歷史數據生成）
- ❌ 用戶體驗差

### 修改後
- ✅ UI 立即響應
- ✅ 歷史數據在背景載入
- ✅ 用戶體驗良好

---

## 💡 經驗教訓

### 1. 避免在構造函數中執行長時間任務

**不好的做法：**
```java
public MainFrame() {
    // ... UI 初始化 ...
    dataFeed.start();  // 可能阻塞 UI
}
```

**更好的做法：**
```java
public MainFrame() {
    // ... UI 初始化 ...
}

public void initialize() {
    dataFeed.start();  // 在構造完成後調用
}
```

### 2. 使用 `SwingWorker` 處理長時間任務

對於更複雜的背景任務，考慮使用 `SwingWorker`：

```java
SwingWorker<Void, Void> worker = new SwingWorker<>() {
    @Override
    protected Void doInBackground() {
        generateHistoricalData();
        return null;
    }
    
    @Override
    protected void done() {
        executor.scheduleAtFixedRate(...);
    }
};
worker.execute();
```

### 3. 檢查是否在 EDT 中

```java
if (SwingUtilities.isEventDispatchThread()) {
    // 在 EDT 中，不能調用 invokeAndWait
    SwingUtilities.invokeLater(() -> updateUI());
} else {
    // 不在 EDT 中，可以調用 invokeAndWait
    SwingUtilities.invokeAndWait(() -> updateUI());
}
```

---

## 🔗 相關資源

- [Java Swing Threading](https://docs.oracle.com/javase/tutorial/uiswing/concurrency/index.html)
- [SwingUtilities API](https://docs.oracle.com/javase/8/docs/api/javax/swing/SwingUtilities.html)
- [SwingWorker Tutorial](https://docs.oracle.com/javase/tutorial/uiswing/concurrency/worker.html)

---

## ✅ 修復狀態

- ✅ 問題已修復
- ✅ 代碼編譯通過
- ✅ 無 linter 錯誤
- 🧪 待用戶測試確認

---

**修復日期：** 2025-10-24  
**修復檔案：** `SimulatorFeed.java`  
**影響範圍：** 歷史 K 線生成功能  
**優先級：** 🔴 高（阻礙啟動）

