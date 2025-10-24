# CSV 匯入修復完成

## ✅ 問題已修復

### 問題描述
CSV 檔案包含註解行（`#` 開頭），導致標題行跳過邏輯錯誤。

**`sample_data.csv` 結構：**
```
行 1: # Sample Stock Data - AAPL          ← 註解
行 2: # Format: Timestamp,Open,...        ← 註解
行 3: Timestamp,Open,High,Low,Close,Volume ← 標題行
行 4: 2025-10-24 09:30:00,150.25,...      ← 數據開始
...
行 33: 2025-10-24 09:59:00,...            ← 數據結束
```

### 舊邏輯問題
```java
// ❌ 錯誤：直接跳過第一行，不管是否為註解
if (hasHeader && (line = reader.readLine()) != null) {
    lineNumber++;  // 跳過行 1（註解）
}
// 接下來讀取行 2（註解）→ 跳過
// 然後讀取行 3（標題）→ 嘗試解析為數據 ❌
```

### 新邏輯
```java
// ✅ 正確：先跳過所有註解和空行，然後跳過第一個非註解行（標題）
while ((line = reader.readLine()) != null) {
    if (line.isEmpty() || line.startsWith("#")) {
        continue;  // 跳過行 1、2（註解）
    }
    
    if (hasHeader && !headerSkipped) {
        headerSkipped = true;
        continue;  // 跳過行 3（標題）
    }
    
    // 處理行 4-33（數據）✅
}
```

---

## 🧪 測試步驟

### 1. 編譯並啟動

```bash
cd C:\Users\chiat\Desktop\測試UI\DreamHouseTrading
mvn clean compile exec:java
```

### 2. CSV 匯入測試

#### 步驟 A：驗證格式

1. 選單：**檔案 → 匯入 CSV...**
2. 點擊「瀏覽...」
3. 選擇 `sample_data.csv`
4. **✅ 勾選「包含標題行」**
5. 點擊「驗證格式」

**預期結果：**
```
Valid rows: 30
Errors (0)
Warnings (0)
```

#### 步驟 B：執行匯入

6. 點擊「匯入」
7. 確認對話框點擊「是」
8. 等待進度條

**預期結果：**
- ✅ 對話框顯示：`匯入成功！共 30 條 K 線資料。`
- ✅ 圖表清空並顯示 30 條新 K 線
- ✅ 成交量圖更新
- ✅ 技術指標重新計算
- ✅ 狀態列顯示：`已載入 30 條歷史 K 線`

---

## 📊 驗證邏輯改進

### 改進 1: 正確處理註解行

**之前：**
- 註解行會影響標題行檢測
- 可能誤將標題行當作數據解析

**現在：**
- 先過濾所有註解和空行
- 在剩餘行中識別標題行
- 標題行之後的行才是數據

### 改進 2: 統一處理邏輯

**修改檔案：**
- `CsvDataManager.importFromCsv()` ✅
- `CsvDataManager.validateCsvFile()` ✅

**兩個方法使用相同邏輯：**
```java
boolean headerSkipped = false;

while ((line = reader.readLine()) != null) {
    // 1. 跳過註解和空行
    if (line.isEmpty() || line.startsWith("#")) {
        continue;
    }
    
    // 2. 跳過標題行（第一個非註解行）
    if (hasHeader && !headerSkipped) {
        headerSkipped = true;
        continue;
    }
    
    // 3. 處理數據行
    // ...
}
```

---

## 📝 支援的 CSV 格式

### 格式 1: 有註解 + 有標題（推薦）

```csv
# 這是註解
# 可以有多行註解
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
```

**設定：** ✅ 勾選「包含標題行」

---

### 格式 2: 無註解 + 有標題

```csv
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
```

**設定：** ✅ 勾選「包含標題行」

---

### 格式 3: 無註解 + 無標題

```csv
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
2025-10-24 09:32:00,151.20,151.80,151.00,151.60,20000
```

**設定：** ❌ 不勾選「包含標題行」

---

### 格式 4: 有註解 + 無標題

```csv
# 這是註解
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
2025-10-24 09:31:00,150.75,151.50,150.50,151.20,18000
```

**設定：** ❌ 不勾選「包含標題行」

---

## 🎯 測試案例

### 案例 1: sample_data.csv（有註解 + 有標題）

**檔案內容：**
```
# Sample Stock Data - AAPL
# Format: Timestamp,Open,High,Low,Close,Volume
Timestamp,Open,High,Low,Close,Volume
2025-10-24 09:30:00,150.25,151.00,149.50,150.75,15000
...（30 行數據）
```

**操作：** ✅ 勾選「包含標題行」  
**結果：** ✅ Valid rows: 30

---

### 案例 2: 測試不勾選

**操作：** ❌ 不勾選「包含標題行」  
**結果：** ❌ Error: Invalid datetime format: Timestamp

**原因：** 標題行 `Timestamp,...` 被當作數據解析

---

## ✅ 驗證清單

- [x] 修復 `importFromCsv()` 方法
- [x] 修復 `validateCsvFile()` 方法
- [x] 支援註解行（`#` 開頭）
- [x] 支援空行
- [x] 正確識別標題行
- [x] 統一匯入與驗證邏輯

---

## 🚀 現在可以正常使用

```bash
# 1. 編譯
mvn clean compile exec:java

# 2. CSV 匯入
檔案 → 匯入 CSV...
選擇 sample_data.csv
✅ 勾選「包含標題行」
驗證 → 應顯示 Valid rows: 30
匯入 → 圖表應顯示 30 條 K 線
```

---

**修復完成！現在無論勾選與否，只要設定正確，CSV 驗證都會成功。** 🎉

