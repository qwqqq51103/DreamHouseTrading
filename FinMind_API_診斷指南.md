# FinMind API 問題診斷與解決指南

## 🔍 當前問題

### 錯誤訊息
```
HTTP 400: {"msg":"Token is illegal.","status":400}
```

## 🛠️ 解決方案

### 1. 檢查 Token 是否正確

#### ✅ 驗證 Token 格式
您的 Token:
```
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJkYXRlIjoiMjAyNS0xMS0xNiAwMDo1MTozNiIsInVzZXJfaWQiOiJUYXJhIiwiaXAiOiIxMTQuMzYuMTY3LjIzNiIsImV4cCI6MTc2MzgzMDI5Nn0.QabBRWzf5sS8-gMb8HI9PDcUxr_QdXRpzPlCJbJNIuU
```

這是一個 JWT (JSON Web Token)，格式看起來正確。

#### 🔍 檢查 Token 是否過期
根據 Token 內容解碼：
- **過期時間 (exp)**: 1763830296
- **轉換為日期**: 2025-11-16（如果今天是 2025-11-21，Token 應該還有效）

#### ⚠️ 可能的問題

1. **Token 已被撤銷**
   - 解決：到 https://finmindtrade.com/ 重新生成 Token

2. **Token 權限不足**
   - `TaiwanStockKBar` API 需要 **Sponsor 會員**
   - 免費會員只能使用 `TaiwanStockPrice`（日線數據）

3. **API 端點改變**
   - FinMind 可能更新了 API 端點或認證方式

### 2. 檢查會員等級

#### Sponsor 會員功能對照表

| API | 免費會員 | Sponsor 會員 |
|-----|---------|--------------|
| TaiwanStockPrice (日線) | ✅ | ✅ |
| TaiwanStockKBar (分K) | ❌ | ✅ |
| taiwan_stock_tick_snapshot (即時快照) | ❌ | ✅ |
| TaiwanStockPriceTick (逐筆) | ❌ | ✅ |

**如果您看到 "Token is illegal" 錯誤：**
- 可能是您的帳號不是 Sponsor 會員
- 或 Token 確實有問題

### 3. 測試 Token

程式現在會在啟動時自動測試 Token 是否有效，查看日誌輸出：

```
[FinMind-TokenTest] INFO ... - 測試 Token 有效性...
[FinMind-TokenTest] INFO ... - ✓ Token 驗證成功！
```

或

```
[FinMind-TokenTest] WARN ... - ✗ Token 驗證失敗: Token is illegal
```

### 4. 手動測試 API

您可以使用以下命令手動測試：

#### 測試免費 API（TaiwanStockPrice）
```bash
curl "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockPrice&data_id=2330&start_date=2025-11-01&end_date=2025-11-01&token=YOUR_TOKEN_HERE"
```

#### 測試 Sponsor API（TaiwanStockKBar）
```bash
curl "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockKBar&data_id=2330&start_date=2025-11-21&token=YOUR_TOKEN_HERE"
```

替換 `YOUR_TOKEN_HERE` 為您的實際 Token。

### 5. 即時快照 API 問題

#### taiwan_stock_tick_snapshot

這個 API 可能：
1. 需要 Sponsor 會員
2. 使用不同的認證方式（Bearer token in header）
3. 已被廢棄或更名

**目前程式的處理方式：**
```java
// 使用 Bearer token 認證
requestBuilder.header("Authorization", "Bearer " + apiToken);
```

**如果此 API 不可用，程式會：**
- 自動降級到歷史數據 API
- 從資料庫載入數據（如果 MarketDataCollector 正在運行）

## 📋 建議操作步驟

### 步驟 1：重新取得 Token

1. 前往 https://finmindtrade.com/
2. 登入您的帳號
3. 前往「API Token」頁面
4. 重新生成 Token
5. 更新 `datasource.properties` 檔案：
   ```properties
   finmind.apitoken=新的Token
   ```

### 步驟 2：確認會員等級

1. 登入 https://finmindtrade.com/
2. 檢查您的會員方案
3. 如需使用分K線數據，需要升級為 Sponsor 會員

### 步驟 3：測試更新後的程式

1. 啟動程式
2. 查看日誌中的 Token 測試結果
3. 如果成功，會看到：
   ```
   ✓ Token 驗證成功！
   ```

### 步驟 4：暫時使用資料庫數據

如果 FinMind API 仍有問題，您可以：

1. **啟動 MarketDataCollector**
   ```batch
   cd C:\Users\chiat\Desktop\測試UI\MarketDataCollector
   雙擊：啟動真實API收集器.bat
   ```

2. **使用資料庫數據**
   - 程式會自動從資料庫載入歷史數據
   - 點擊「📊 載入歷史數據」按鈕手動載入

## 🔧 程式改進

我已經做了以下改進：

### 1. 移除 Token URL 編碼
```java
// 修改前
url += "&token=" + URLEncoder.encode(apiToken, StandardCharsets.UTF_8);

// 修改後
url += "&token=" + apiToken;
```

### 2. 添加詳細的錯誤診斷
程式現在會提供更詳細的錯誤訊息和解決建議。

### 3. 自動測試 Token
啟動時會自動測試 Token 是否有效。

### 4. 優雅降級
如果 API 失敗，會自動：
- 嘗試從資料庫載入數據
- 使用模擬數據（最後手段）

## 📞 聯絡 FinMind

如果問題持續：

1. **官網**: https://finmindtrade.com/
2. **文檔**: https://finmind.github.io/
3. **Email**: support@finmindtrade.com

## 🎯 快速檢查清單

- [ ] Token 格式正確（JWT 格式）
- [ ] Token 未過期
- [ ] 帳號為 Sponsor 會員（如需分K線）
- [ ] 股票代碼正確（如 `2330` 而非 `2330.TW`）
- [ ] 今日為交易日
- [ ] 網路連線正常
- [ ] datasource.properties 檔案已更新

---

**最新更新**: 2025-11-21
**程式版本**: DreamHouseTrading 0.0.1-SNAPSHOT
