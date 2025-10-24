# StatusBar 訊息功能使用指南

## 🎯 功能說明

`StatusBar` 現在支援在右側顯示**通用訊息**，用於提示用戶當前狀態或操作結果。

---

## 📍 顯示位置

StatusBar 的完整佈局（從左到右）：

```
[●模擬] | [商品: AAPL] [最新: 150.25] [週期: 1m] | [FPS: 60] | [這裡顯示訊息]
                                                               ↑
                                                          訊息顯示區域
```

**視覺特徵：**
- 位置：狀態列最右側
- 顏色：灰色文字 (`RGB(100, 100, 100)`)
- 分隔線：前面有一條垂直分隔線 `|`

---

## 🚀 啟動時的訊息流程

### 階段 1：初始化
```
就緒
```
- 應用程式啟動時的預設訊息

### 階段 2：載入數據（0-3秒）
```
正在載入歷史資料...
```
- 開始生成 50 條歷史 K 線時顯示

### 階段 3：載入完成（3秒後）
```
歷史資料載入完成 (50條 K線)
```
- 歷史數據生成完畢後顯示

---

## 💡 實際應用場景

### 1. CSV 匯入成功
```java
statusBar.setText("已載入 30 條歷史 K 線");
```

顯示效果：
```
... | [FPS: 60] | 已載入 30 條歷史 K 線
```

### 2. 指標切換
```java
statusBar.setText("已切換到 MACD 指標");
```

### 3. 時間框切換
```java
statusBar.setText("已切換到 5 分鐘週期");
```

### 4. 錯誤提示
```java
statusBar.setText("載入失敗，請檢查數據格式");
```

### 5. 清除訊息
```java
statusBar.clearMessage();
```

顯示效果：
```
... | [FPS: 60] | 
```

---

## 🧪 測試方法

### 方法 1：啟動觀察

1. 啟動應用程式
   ```bash
   mvn clean compile exec:java
   ```

2. 觀察狀態列右側：
   - **0秒**：`就緒`
   - **立即**：`正在載入歷史資料...`
   - **3秒後**：`歷史資料載入完成 (50條 K線)`

### 方法 2：CSV 匯入測試

1. 選單：檔案 → 匯入 CSV...
2. 選擇 `sample_data.csv`
3. 勾選「包含標題行」
4. 點擊「驗證格式」
5. 點擊「匯入」
6. **觀察狀態列右側**：應顯示 `已載入 30 條歷史 K 線`

### 方法 3：手動測試

在代碼中添加測試：

```java
// MainFrameWithDocking 構造函數中
Timer testTimer = new Timer(5000, e -> {
    statusBar.setText("這是一條測試訊息");
});
testTimer.setRepeats(false);
testTimer.start();
```

5 秒後狀態列會顯示測試訊息。

---

## 📊 訊息顯示時機表

| 事件 | 訊息 | 持續時間 |
|------|------|----------|
| 應用程式啟動 | `就緒` | 初始 |
| 開始載入數據 | `正在載入歷史資料...` | 0-3秒 |
| 數據載入完成 | `歷史資料載入完成 (50條 K線)` | 3秒後持續顯示 |
| CSV 匯入成功 | `已載入 XX 條歷史 K 線` | 手動清除前持續 |
| 錯誤發生 | 自訂錯誤訊息 | 手動清除前持續 |

---

## 🎨 訊息樣式

### 預設樣式
- **字體**：系統預設
- **顏色**：灰色 `RGB(100, 100, 100)`
- **大小**：與其他狀態列文字相同
- **對齊**：左對齊

### 自訂樣式（未來擴展）

可以為不同類型的訊息設定不同顏色：

```java
// 成功訊息（綠色）
messageLabel.setForeground(new Color(34, 177, 76));
statusBar.setText("操作成功");

// 警告訊息（橙色）
messageLabel.setForeground(new Color(255, 165, 0));
statusBar.setText("注意：數據不完整");

// 錯誤訊息（紅色）
messageLabel.setForeground(Color.RED);
statusBar.setText("錯誤：載入失敗");

// 恢復預設（灰色）
messageLabel.setForeground(new Color(100, 100, 100));
statusBar.setText("就緒");
```

---

## 🔧 API 參考

### StatusBar.setText(String message)

設定狀態列訊息。

**參數：**
- `message` - 要顯示的訊息文字

**範例：**
```java
statusBar.setText("CSV 匯入成功！");
```

### StatusBar.clearMessage()

清除狀態列訊息（設為空字串）。

**範例：**
```java
statusBar.clearMessage();
```

---

## 💻 完整程式碼範例

### 範例 1：CSV 匯入後顯示訊息

```java
private void importCsvData() {
    CsvImportDialog dialog = new CsvImportDialog(this);
    dialog.setVisible(true);
    
    if (dialog.isConfirmed() && dialog.getImportedBars() != null) {
        List<Bar> bars = dialog.getImportedBars();
        
        // TODO: 載入到圖表
        // chartDock.loadHistoricalData(bars);
        
        // 顯示成功訊息
        statusBar.setText(String.format("已載入 %d 條歷史 K 線", bars.size()));
    }
}
```

### 範例 2：定時清除訊息

```java
statusBar.setText("CSV 匯入成功！");

// 3 秒後自動清除
Timer clearTimer = new Timer(3000, e -> {
    statusBar.clearMessage();
});
clearTimer.setRepeats(false);
clearTimer.start();
```

### 範例 3：長時間操作的進度提示

```java
statusBar.setText("正在處理數據...");

SwingWorker<Void, Void> worker = new SwingWorker<>() {
    @Override
    protected Void doInBackground() throws Exception {
        // 長時間操作
        processData();
        return null;
    }
    
    @Override
    protected void done() {
        statusBar.setText("處理完成！");
    }
};
worker.execute();
```

---

## 🐛 故障排除

### 問題 1：看不到訊息

**可能原因：**
- 訊息為空字串
- 訊息顏色與背景色相同
- StatusBar 未正確初始化

**解決方法：**
```java
// 確認訊息不為空
String message = "測試訊息";
if (message != null && !message.isEmpty()) {
    statusBar.setText(message);
}

// 檢查顏色設定
messageLabel.setForeground(new Color(100, 100, 100));
```

### 問題 2：訊息被截斷

**原因：** StatusBar 使用 `FlowLayout`，空間不足時會截斷

**解決方法：**
- 使用簡短的訊息
- 或改用 `BorderLayout` 為訊息區域預留空間

### 問題 3：訊息沒有即時更新

**原因：** 未在 EDT 中更新

**解決方法：**
```java
SwingUtilities.invokeLater(() -> {
    statusBar.setText("新訊息");
});
```

---

## 🌐 國際化支援

訊息支援中英文切換：

### 中文
```properties
status.ready=就緒
status.loading.data=正在載入歷史資料...
status.data.loaded=歷史資料載入完成 (50條 K線)
```

### English
```properties
status.ready=Ready
status.loading.data=Loading historical data...
status.data.loaded=Historical data loaded (50 bars)
```

**使用方式：**
```java
statusBar.setText(I18n.get("status.ready"));
```

---

## 📝 開發建議

### 1. 使用語義化的訊息
❌ 不好：`statusBar.setText("OK");`  
✅ 好：`statusBar.setText("操作完成");`

### 2. 提供具體資訊
❌ 不好：`statusBar.setText("成功");`  
✅ 好：`statusBar.setText("已載入 30 條歷史 K 線");`

### 3. 定義常用訊息為常數
```java
public class StatusMessages {
    public static final String LOADING = "正在載入...";
    public static final String SUCCESS = "操作成功";
    public static final String ERROR = "操作失敗";
}
```

### 4. 使用 I18n 支援多語言
```java
statusBar.setText(I18n.get("status.loading"));
```

---

**最後更新：** 2025-10-25  
**相關檔案：** `StatusBar.java`, `MainFrameWithDocking.java`

