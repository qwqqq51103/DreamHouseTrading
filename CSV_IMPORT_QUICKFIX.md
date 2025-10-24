# CSV 匯入快速修復指南

## ❌ 錯誤訊息

```
Valid rows: 30
Errors (1):
  - Invalid datetime format: Unable to parse date/time: Timestamp
```

---

## 🔍 問題原因

CSV 檔案的**第一行是標題**（`Timestamp,Open,High,Low,Close,Volume`），但驗證時**沒有勾選「包含標題行」**，導致系統將 "Timestamp" 當作日期時間來解析。

---

## ✅ 解決方法

### 方法 1：勾選「包含標題行」（推薦）

1. 開啟 CSV 匯入對話框
2. 選擇 `sample_data.csv`
3. **✅ 勾選「包含標題行」**
4. 點擊「驗證格式」
5. 點擊「匯入」

**預期結果：**
```
Valid rows: 30
No errors
```

---

### 方法 2：移除 CSV 標題行

如果 CSV 沒有標題行，請確保第一行就是數據：

**錯誤格式：**
```csv
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
```

**正確格式（無標題）：**
```csv
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
```

然後**不要勾選**「包含標題行」。

---

## 📊 完整測試流程

### 使用 `sample_data.csv`

1. **開啟對話框**
   ```
   檔案 → 匯入 CSV...
   ```

2. **選擇檔案**
   - 點擊「瀏覽...」
   - 選擇 `sample_data.csv`
   - **預覽區應顯示前 10 行**

3. **設定選項**
   - ✅ **勾選「包含標題行」** ← 關鍵步驟！

4. **驗證格式**
   - 點擊「驗證格式」
   - **預期結果：**
     ```
     Valid rows: 30
     Errors (0)
     Warnings (0)
     ```

5. **執行匯入**
   - 點擊「匯入」
   - 確認對話框點擊「是」
   - 等待進度條完成

6. **驗證結果**
   - 圖表應顯示 30 條 K 線
   - 狀態列顯示：`已載入 30 條歷史 K 線`
   - Console 輸出：`CSV 匯入成功: 30 條 K 線`

---

## 🎯 驗證結果對照表

### ✅ 正確配置

| 檔案狀態 | 勾選「包含標題行」 | 驗證結果 |
|----------|-------------------|----------|
| 有標題行 | ✅ 是 | ✅ Valid rows: 30 |
| 無標題行 | ❌ 否 | ✅ Valid rows: 30 |

### ❌ 錯誤配置

| 檔案狀態 | 勾選「包含標題行」 | 驗證結果 |
|----------|-------------------|----------|
| 有標題行 | ❌ 否 | ❌ Error: Invalid datetime format: Timestamp |
| 無標題行 | ✅ 是 | ❌ 缺少一行數據 |

---

## 📝 `sample_data.csv` 檔案內容

**第一行（標題）：**
```
Timestamp,Open,High,Low,Close,Volume
```

**第二行開始（數據）：**
```
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
...
```

**總行數：** 31 行（1 行標題 + 30 行數據）

---

## 🐛 常見錯誤與解決

### 錯誤 1：Invalid datetime format: Timestamp

**原因：** 有標題行但未勾選  
**解決：** ✅ 勾選「包含標題行」

---

### 錯誤 2：Expected 6 columns, found X

**原因：** CSV 格式不正確  
**解決：** 確保每行都有 6 個欄位，用逗號分隔

---

### 錯誤 3：High (X) cannot be less than Low (Y)

**原因：** 數據邏輯錯誤  
**解決：** 修正 CSV 中的 OHLC 數據，確保：
- High >= max(Open, Close)
- Low <= min(Open, Close)
- High >= Low

---

### 錯誤 4：Prices must be positive

**原因：** 價格為負數或零  
**解決：** 確保所有價格 > 0

---

## 🔧 編譯錯誤修復

如果遇到編譯錯誤：
```
cannot find symbol: variable MAX_BARS
```

**已修復！** 現在可以重新編譯：

```bash
cd C:\Users\chiat\Desktop\測試UI\DreamHouseTrading
mvn clean compile exec:java
```

---

## ✅ 完整測試步驟

```bash
# 1. 編譯並啟動
mvn clean compile exec:java

# 2. 等待歷史數據載入（2-3秒）

# 3. CSV 匯入測試
檔案 → 匯入 CSV...
選擇 sample_data.csv
✅ 勾選「包含標題行」
驗證格式
匯入

# 4. 驗證結果
- 圖表顯示 30 條 K 線
- 狀態列顯示成功訊息
```

---

**修復完成！現在可以正常匯入 CSV 了。** 🎉

