# 階段 2 實作摘要：CSV 數據管理

## 📅 日期：2025-10-24

## ✅ 已完成功能

### 1. CSV 數據管理核心類別

**新增檔案：**
- `com.dreamhouse.trading.core.csv.CsvDataManager.java`

**功能說明：**
- **CSV 匯入**：從檔案讀取 K 線數據
  - 支援多種日期時間格式（`yyyy-MM-dd HH:mm:ss`、`yyyy/MM/dd HH:mm:ss` 等）
  - 可選擇是否包含標題行
  - 自動按時間排序
  - 跳過空行和註解行（`#` 開頭）

- **CSV 匯出**：將 K 線數據儲存為 CSV
  - 標準格式：`Timestamp,Open,High,Low,Close,Volume`
  - 可選擇是否包含標題行
  - 數值精度：價格保留 4 位小數，成交量整數

- **格式驗證**：檢查 CSV 檔案格式
  - 欄位數量驗證（必須 6 欄）
  - 數值格式驗證
  - 數據合理性驗證：
    - High >= max(Open, Close)
    - Low <= min(Open, Close)
    - High >= Low
    - 所有價格 > 0
    - 成交量 >= 0
  - 時間順序檢查（警告）

**支援的 CSV 格式：**
```csv
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
...
```

### 2. CSV 匯入對話框

**新增檔案：**
- `com.dreamhouse.trading.ui.dialog.CsvImportDialog.java`

**功能說明：**

#### 檔案選擇區
- 檔案瀏覽按鈕
- 檔案路徑顯示
- 「包含標題行」選項

#### 預覽區
- 顯示檔案資訊（檔名、大小）
- 顯示前 10 行內容
- 等寬字體便於閱讀

#### 驗證區
- 點擊「驗證格式」按鈕執行驗證
- 顯示驗證結果：
  - 有效行數
  - 錯誤列表（含行號）
  - 警告列表（如時間順序問題）
- 使用 `SwingWorker` 非阻塞驗證

#### 匯入功能
- 驗證通過後才能匯入
- 顯示確認對話框（警告會覆蓋現有數據）
- 進度條顯示匯入狀態
- 使用 `SwingWorker` 非阻塞匯入
- 匯入成功後顯示結果

### 3. 選單整合

**修改檔案：**
- `MainFrameWithDocking.java`
- `messages_zh.properties`
- `messages_en.properties`

**選單結構：**
```
檔案 (File)
├── 匯入 CSV... (Import CSV...)
├── 匯出 CSV... (Export CSV...)
├──────────────
└── 離開 (Exit)
```

**快捷鍵（規劃）：**
- `Ctrl+I`：匯入 CSV
- `Ctrl+E`：匯出 CSV

### 4. 國際化支援

**新增翻譯鍵：**

| 鍵                                      | 中文                                 | English                                    |
|-----------------------------------------|--------------------------------------|--------------------------------------------|
| `menu.file.import.csv`                  | 匯入 CSV...                          | Import CSV...                              |
| `menu.file.export.csv`                  | 匯出 CSV...                          | Export CSV...                              |
| `dialog.csv.import.title`               | CSV 匯入                             | CSV Import                                 |
| `dialog.csv.import.browse`              | 瀏覽...                              | Browse...                                  |
| `dialog.csv.import.has.header`          | 包含標題行                           | Contains Header Row                        |
| `dialog.csv.import.validate`            | 驗證格式                             | Validate Format                            |
| `dialog.csv.import.import`              | 匯入                                 | Import                                     |
| `dialog.csv.import.validation.success`  | 驗證成功！找到 %d 條有效資料。       | Validation successful! Found %d valid rows.|
| `dialog.csv.import.confirm.message`     | 確定要匯入這些資料嗎？現有資料將被取代。| Are you sure you want to import this data? Current data will be replaced. |
| `dialog.csv.import.success`             | 匯入成功！共 %d 條 K 線資料。        | Import successful! %d bars imported.       |

## 🎯 使用方法

### CSV 匯入流程

1. **開啟匯入對話框**
   - 選單：檔案 → 匯入 CSV...

2. **選擇檔案**
   - 點擊「瀏覽...」按鈕
   - 選擇 `.csv` 檔案

3. **預覽數據**
   - 自動顯示檔案資訊與前 10 行

4. **驗證格式**
   - 勾選「包含標題行」（如果有）
   - 點擊「驗證格式」按鈕
   - 查看驗證結果

5. **執行匯入**
   - 驗證通過後，「匯入」按鈕啟用
   - 點擊「匯入」
   - 確認對話框
   - 等待匯入完成
   - 查看匯入結果

### CSV 格式範例

**標準格式（含標題行）：**
```csv
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
2025-10-24 09:32:00,151.20,151.80,151.00,151.60,20000
```

**無標題行：**
```csv
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
2025-10-24 09:32:00,151.20,151.80,151.00,151.60,20000
```

**支援註解：**
```csv
# 這是註解行，會被跳過
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000

# 空行也會被跳過
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
```

## 🏗️ 技術架構

### CSV 數據管理器（CsvDataManager）

```
CsvDataManager
├── importFromCsv()           // 匯入 CSV
├── exportToCsv()             // 匯出 CSV
├── validateCsvFile()         // 驗證格式
├── parseCsvLine()            // 解析單行
├── parseDateTime()           // 解析日期時間（多格式）
└── validateBar()             // 驗證 K 線數據
```

### CSV 匯入對話框（CsvImportDialog）

```
CsvImportDialog
├── browseFile()              // 選擇檔案
├── showPreview()             // 顯示預覽
├── validateFile()            // 驗證格式（SwingWorker）
├── performImport()           // 執行匯入（SwingWorker）
└── isConfirmed()             // 取得確認狀態
```

### 資料流程

```
CSV 檔案
    ↓
CsvDataManager.validateCsvFile()
    ↓
驗證結果（errors, warnings, validRows）
    ↓
CsvImportDialog 顯示結果
    ↓
用戶點擊「匯入」
    ↓
CsvDataManager.importFromCsv()
    ↓
List<Bar> bars
    ↓
MainFrameWithDocking.importCsvData()
    ↓
[TODO] chartDock.loadHistoricalData(bars)
    ↓
圖表更新
```

## 🔍 驗證規則

### 格式驗證

1. **欄位數量**：必須正好 6 欄
2. **日期時間格式**：嘗試多種格式，至少一種成功
3. **數值格式**：Open/High/Low/Close/Volume 必須為有效數字
4. **數值類型**：
   - 價格：浮點數（Double）
   - 成交量：整數（Long）

### 數據合理性驗證

1. **價格正值**：所有價格必須 > 0
2. **High >= Low**：最高價不能低於最低價
3. **High >= max(Open, Close)**：最高價必須 >= 開盤/收盤最大值
4. **Low <= min(Open, Close)**：最低價必須 <= 開盤/收盤最小值
5. **成交量非負**：Volume >= 0

### 時間順序檢查（警告）

- 如果時間戳早於前一筆，發出警告
- 匯入時會自動排序，不影響匯入

## 📊 錯誤處理

### CsvFormatException

自訂例外類別，包含：
- 錯誤訊息
- 行號

範例：
```
Line 15: Invalid datetime format: Unable to parse date/time: 2025-13-40 25:61:99
Line 23: High (150.00) cannot be less than Low (151.00)
Line 45: Expected 6 columns, found 5
```

### SwingWorker 錯誤處理

- 驗證/匯入使用 `SwingWorker` 在背景執行
- 錯誤捕獲並顯示在對話框中
- 不會凍結 UI

## 🚀 未來改進

### 待整合功能

1. **匯入後載入到圖表**
   ```java
   // MainFrameWithDocking.importCsvData()
   chartDock.loadHistoricalData(bars);
   ```

2. **CSV 匯出對話框**
   - 選擇匯出範圍（全部/可見/自訂）
   - 選擇輸出格式
   - 添加統計資訊

3. **CSV 格式模板**
   - 提供範例 CSV 下載
   - 多種格式支援（Yahoo Finance, TradingView, etc.）

### 進階功能

1. **批次匯入**
   - 一次匯入多個檔案
   - 合併數據

2. **自動檢測分隔符**
   - 支援逗號、Tab、分號

3. **資料清理**
   - 去除重複數據
   - 填補缺失數據

4. **錯誤修復建議**
   - 智能提示常見錯誤
   - 一鍵修復選項

## 📝 已知限制

1. **匯出功能未實作**：目前只有佔位函數
2. **大檔案效能**：超過 10,000 行可能較慢（未優化）
3. **檔案編碼**：假設為系統預設編碼（UTF-8/Big5）

## ✅ 編譯狀態

- ✅ CsvDataManager 編譯通過
- ✅ CsvImportDialog 編譯通過
- ✅ 選單整合完成
- ✅ 國際化資源完整

## 📋 測試清單

### 功能測試

- [ ] 匯入含標題行的 CSV
- [ ] 匯入無標題行的 CSV
- [ ] 匯入包含註解與空行的 CSV
- [ ] 驗證欄位數量錯誤
- [ ] 驗證日期格式錯誤
- [ ] 驗證數值格式錯誤
- [ ] 驗證數據不合理（High < Low）
- [ ] 驗證時間順序警告
- [ ] 取消匯入操作
- [ ] 中英文切換後選單正確

### 整合測試

- [ ] 匯入後圖表更新（待實作）
- [ ] 多次匯入覆蓋舊數據
- [ ] 匯入後技術指標重新計算

---

**下一階段預告：回測引擎**
- 策略框架設計
- 訊號生成機制
- 內建策略實作

