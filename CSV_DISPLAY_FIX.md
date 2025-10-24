# CSV 顯示問題修復指南

## 🔍 問題分析

### 問題現象
匯入 CSV 後，圖表顯示如下：
- 左側有少量 K 線擠在一起
- 中間大片空白區域
- 時間軸從早上延伸到深夜
- 排版難看，難以閱讀

### 根本原因

**`sample_data.csv` 的數據範圍：**
```
開始: 2025-10-24 09:30:00
結束: 2025-10-24 09:59:00
時長: 30 分鐘
```

**JFreeChart 的自動範圍：**
- JFreeChart 會自動調整時間軸範圍
- 對於少量數據，可能會顯示整天的時間範圍
- 導致 30 分鐘的數據分散在 24 小時的軸上

---

## ✅ 解決方案

### 方案 1: 使用新的測試數據（推薦）

我已經創建了 **`sample_data_recent.csv`**：
- **50 條數據**（更多數據）
- **時間範圍**：13:30 - 14:19（連續 50 分鐘）
- **價格範圍**：150 - 163（趨勢上升）
- **成交量**：15,000 - 48,000

**使用方法：**
1. 檔案 → 匯入 CSV...
2. 選擇 **`sample_data_recent.csv`**
3. ✅ 勾選「包含標題行」
4. 驗證並匯入

**預期效果：**
- K 線分佈均勻
- 時間軸緊湊（約 1 小時範圍）
- 排版美觀

---

### 方案 2: 手動調整視圖範圍

**使用滑鼠操作：**
1. **滾輪放大**：在圖表上滾動滑鼠滾輪
2. **拖曳平移**：按住左鍵拖曳圖表
3. **重置視圖**：工具列點擊「重置」按鈕

**使用快捷鍵：**
- `Ctrl + =`：放大
- `Ctrl + -`：縮小
- `Ctrl + 0`：重置

---

### 方案 3: 修改 ChartDock 的自動範圍邏輯

目前 `ChartDock.loadHistoricalData()` 使用：
```java
chartPanel.restoreAutoBounds();
```

這會恢復到**自動範圍**，可能導致顯示範圍過大。

**改進方案（未來實作）：**
```java
// 計算實際數據範圍
long firstTime = bars.get(0).getTimestamp().toEpochSecond(ZoneOffset.UTC);
long lastTime = bars.get(bars.size()-1).getTimestamp().toEpochSecond(ZoneOffset.UTC);

// 添加 10% 的邊距
long padding = (long)((lastTime - firstTime) * 0.1);

// 手動設定時間軸範圍
ValueAxis domainAxis = combinedPlot.getDomainAxis();
domainAxis.setRange(firstTime - padding, lastTime + padding);
```

---

## 📊 測試數據對比

### 舊數據：sample_data.csv
| 屬性 | 值 |
|------|-----|
| 數據量 | 30 條 |
| 時間範圍 | 09:30 - 09:59 |
| 時長 | 30 分鐘 |
| 價格範圍 | 150.25 - 158.20 |
| **問題** | **時間軸過長，K 線擠在左側** |

### 新數據：sample_data_recent.csv
| 屬性 | 值 |
|------|-----|
| 數據量 | 50 條 |
| 時間範圍 | 13:30 - 14:19 |
| 時長 | 50 分鐘 |
| 價格範圍 | 150.25 - 163.20 |
| **優勢** | **更多數據，分佈均勻** |

---

## 🎯 快速測試

### 步驟 1: 使用新測試檔案

```bash
cd C:\Users\chiat\Desktop\測試UI\DreamHouseTrading
mvn clean compile exec:java
```

### 步驟 2: 匯入新 CSV

1. 檔案 → 匯入 CSV...
2. 選擇 **`sample_data_recent.csv`**
3. ✅ 勾選「包含標題行」
4. 驗證（應顯示 50 條有效資料）
5. 匯入

### 步驟 3: 驗證結果

**預期效果：**
- ✅ K 線均勻分佈
- ✅ 時間軸緊湊（約 1 小時）
- ✅ 價格變化清晰可見
- ✅ 成交量分佈合理
- ✅ 技術指標正常顯示

---

## 🔧 進階：創建自己的測試數據

### Excel 快速生成

```excel
=NOW()                          # 起始時間
=A1+TIME(0,1,0)                 # 每分鐘遞增
=150+RAND()*10                  # 隨機價格
=15000+RAND()*30000             # 隨機成交量
```

### Python 腳本生成

```python
import pandas as pd
from datetime import datetime, timedelta

# 生成時間序列
start_time = datetime.now().replace(hour=13, minute=30, second=0)
times = [start_time + timedelta(minutes=i) for i in range(100)]

# 生成價格（隨機遊走）
price = 150
prices = []
for _ in times:
    price += (random.random() - 0.5) * 2
    prices.append(price)

# 生成 OHLC
data = {
    'Timestamp': times,
    'Open': prices,
    'High': [p + random.random() for p in prices],
    'Low': [p - random.random() for p in prices],
    'Close': [p + (random.random()-0.5) for p in prices],
    'Volume': [15000 + random.randint(0, 30000) for _ in times]
}

df = pd.DataFrame(data)
df.to_csv('custom_data.csv', index=False)
```

---

## 💡 最佳實踐

### CSV 數據建議

1. **數據量**：至少 50-100 條
2. **時間範圍**：連續且緊湊（1-2 小時）
3. **價格變化**：有明顯趨勢或波動
4. **成交量**：有變化，不要全部相同

### 不推薦

❌ 數據量太少（< 30 條）  
❌ 時間跨度太大（> 1 天）  
❌ 時間不連續（有大段空白）  
❌ 價格無變化（水平線）

### 推薦

✅ 50-200 條數據  
✅ 連續的 1-4 小時範圍  
✅ 有趨勢或波動的價格  
✅ 變化的成交量

---

## 🎨 視覺效果對比

### 使用 sample_data.csv（30 條）
```
圖表顯示:
[K線] [                                空白                                ]
09:30   10:00        12:00           14:00           16:00           18:00
```
**問題**：K 線擠在左側，右側全是空白

### 使用 sample_data_recent.csv（50 條）
```
圖表顯示:
[    K線均勻分佈在整個可見範圍    ]
13:30        13:45        14:00        14:15
```
**效果**：分佈均勻，易於閱讀

---

## 🚀 立即測試

**使用新數據：**
```
1. 匯入 sample_data_recent.csv
2. ✅ 勾選「包含標題行」
3. 驗證：Valid rows: 50
4. 匯入
5. 查看改善後的顯示效果
```

---

**新測試數據已準備好，應該可以解決排版問題！** 🎉

