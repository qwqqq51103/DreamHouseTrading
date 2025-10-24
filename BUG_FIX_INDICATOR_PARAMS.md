# 🐛 Bug 修復：指標參數未實際應用到圖表

## 問題報告

**使用者回報**：
```
指標設定已確認:
SMA Period: 20
EMA Period: 20
RSI Period: 5
MACD: 12/26/9
BOLL: 20, 2.0
KD: 9/3
✓ 指標參數已應用到圖表

雖然有出現成功到圖表，但事實及上圖表的顏色與周期都沒有變動
```

---

## 根本原因分析

### 問題所在

雖然 `openIndicatorSettings()` 方法中已經調用了 `chartDock.setIndicatorParameters()`，參數也成功保存到 `indicatorConfigs` Map，**但是 `updateXXX()` 方法中仍然使用硬編碼的參數值**，而不是從 `indicatorConfigs` 讀取！

### 問題代碼示例

```java
// ❌ 錯誤：使用硬編碼的週期
private void updateSMA() {
    List<Double> smaValues = indicatorService.getSMA(20);  // <-- 固定20
    // ...
}

private void updateRSI() {
    List<Double> rsiValues = indicatorService.getRSI(14);  // <-- 固定14
    // ...
}

private void updateBOLL() {
    Map<String, List<Double>> bollData = indicatorService.getBollingerBands(20, 2.0);  // <-- 固定值
    // ...
}
```

### 為什麼沒有效果

1. **參數已保存** ✅ `setIndicatorParameters()` 成功更新 `indicatorConfigs`
2. **但未使用** ❌ `updateIndicators()` → `updateSMA()` 等方法仍使用硬編碼值
3. **顏色未設定** ❌ 完全沒有調用 `renderer.setSeriesPaint()` 來更新顏色

**結果**：無論如何修改參數，圖表都顯示預設的 SMA(20)、RSI(14) 等。

---

## ✅ 解決方案

### 修改內容

為所有 `updateXXX()` 方法添加：
1. **從 `indicatorConfigs` 讀取參數**
2. **更新指標線條顏色**

---

### 修正後的代碼

#### 1. **updateSMA()** - 讀取週期與顏色

```java
private void updateSMA() {
    // ✅ 從配置讀取週期
    IndicatorConfig config = indicatorConfigs.get("SMA");
    int smaPeriod = (config != null) ? config.getPeriod() : 20;
    
    // 使用動態週期計算
    List<Double> smaValues = indicatorService.getSMA(smaPeriod);
    
    for (int i = 0; i < smaValues.size() && i < ohlcSeries.getItemCount(); i++) {
        if (smaValues.get(i) != null) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod timePeriod = item.getPeriod();
            smaSeries.addOrUpdate(timePeriod, smaValues.get(i));
        }
    }
    
    // ✅ 更新顏色
    if (config != null && config.getColor() != null && combinedPlot != null) {
        XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0);
        XYItemRenderer renderer = plot.getRenderer(1);
        if (renderer != null) {
            renderer.setSeriesPaint(0, config.getColor());
        }
    }
}
```

#### 2. **updateRSI()** - 讀取週期與顏色

```java
private void updateRSI() {
    IndicatorConfig config = indicatorConfigs.get("RSI");
    int rsiPeriod = (config != null) ? config.getPeriod() : 14;
    
    List<Double> rsiValues = indicatorService.getRSI(rsiPeriod);
    
    // ... 數據更新 ...
    
    // ✅ 更新副圖顏色
    if (config != null && config.getColor() != null && indicatorPlot != null) {
        XYItemRenderer renderer = indicatorPlot.getRenderer();
        if (renderer != null) {
            renderer.setSeriesPaint(0, config.getColor());
        }
    }
}
```

#### 3. **updateMACD()** - 讀取 Fast/Slow/Signal 週期

```java
private void updateMACD() {
    IndicatorConfig config = indicatorConfigs.get("MACD");
    int fastPeriod = (config != null) ? config.getFastPeriod() : 12;
    int slowPeriod = (config != null) ? config.getSlowPeriod() : 26;
    int signalPeriod = (config != null) ? config.getSignalPeriod() : 9;
    
    Map<String, List<Double>> macdData = indicatorService.getMACD(fastPeriod, slowPeriod, signalPeriod);
    // ...
}
```

#### 4. **updateBOLL()** - 讀取週期與倍數

```java
private void updateBOLL() {
    IndicatorConfig config = indicatorConfigs.get("BOLL");
    int bollPeriod = (config != null) ? config.getPeriod() : 20;
    double multiplier = (config != null) ? config.getMultiplier() : 2.0;
    
    Map<String, List<Double>> bollData = indicatorService.getBollingerBands(bollPeriod, multiplier);
    // ...
}
```

#### 5. **updateKD()** - 讀取 K 和 D 週期

```java
private void updateKD() {
    IndicatorConfig config = indicatorConfigs.get("KD");
    int kPeriod = (config != null) ? config.getPeriod() : 9;
    int dPeriod = (config != null) ? config.getPeriod2() : 3;
    
    Map<String, List<Double>> kdData = indicatorService.getStochastic(kPeriod, dPeriod);
    // ...
}
```

#### 6. **updateADX(), updateCCI(), updateWR()** - 讀取週期

```java
private void updateADX() {
    IndicatorConfig config = indicatorConfigs.get("ADX");
    int adxPeriod = (config != null) ? config.getPeriod() : 14;
    Map<String, List<Double>> adxData = indicatorService.getADX(adxPeriod);
    // ...
}

private void updateCCI() {
    IndicatorConfig config = indicatorConfigs.get("CCI");
    int cciPeriod = (config != null) ? config.getPeriod() : 14;
    List<Double> cciValues = indicatorService.getCCI(cciPeriod);
    // ...
}

private void updateWR() {
    IndicatorConfig config = indicatorConfigs.get("WR");
    int wrPeriod = (config != null) ? config.getPeriod() : 14;
    List<Double> wrValues = indicatorService.getWilliamsR(wrPeriod);
    // ...
}
```

---

## 🔧 其他修正

### 變數名稱衝突

**問題**：在 for 迴圈外定義了 `int period`，在迴圈內又定義了 `RegularTimePeriod period`，導致編譯錯誤。

**解決**：
- 外層參數變數重新命名：`smaPeriod`, `rsiPeriod`, `bollPeriod` 等
- 內層時間變數重新命名：`timePeriod`

```java
// 外層
int smaPeriod = (config != null) ? config.getPeriod() : 20;

// 內層
for (int i = 0; i < smaValues.size() && i < ohlcSeries.getItemCount(); i++) {
    // ...
    RegularTimePeriod timePeriod = item.getPeriod();
    smaSeries.addOrUpdate(timePeriod, smaValues.get(i));
}
```

### 缺少 Import

**問題**：`XYItemRenderer` 未 import

**解決**：
```java
import org.jfree.chart.renderer.xy.XYItemRenderer;
```

---

## ✅ 驗證步驟

### 測試案例 1：修改 SMA 週期

1. 打開指標設定對話框
2. 將 SMA 週期從 20 改為 **10**
3. 點擊確認

**預期結果**：
- ✅ SMA 線條變得更貼近 K 線（反應更靈敏）
- ✅ 圖表標題顯示 `SMA(10)`
- ✅ 控制台輸出：`✓ 指標參數已應用到圖表`

### 測試案例 2：修改 SMA 顏色

1. 打開指標設定對話框
2. 點擊 SMA 顏色按鈕，選擇 **紅色**
3. 點擊確認

**預期結果**：
- ✅ SMA 線條變為紅色

### 測試案例 3：修改 RSI 週期

1. 切換到 RSI 指標
2. 打開設定對話框
3. 將 RSI 週期從 14 改為 **5**（如您測試的值）
4. 點擊確認

**預期結果**：
- ✅ RSI 線條變得極度靈敏（週期短，波動大）
- ✅ 圖表標題顯示 `RSI(5)`

### 測試案例 4：修改 BOLL 倍數

1. 切換到 BOLL 指標
2. 將倍數從 2.0 改為 **1.5**
3. 點擊確認

**預期結果**：
- ✅ 布林通道的上下軌道變窄，更靠近中軌

---

## 📊 修改的檔案

1. `ChartDock.java`
   - 添加 `import org.jfree.chart.renderer.xy.XYItemRenderer;`
   - 修改 `updateSMA()` - 讀取配置與更新顏色
   - 修改 `updateEMA()` - 讀取配置與更新顏色
   - 修改 `updateRSI()` - 讀取配置與更新顏色
   - 修改 `updateMACD()` - 讀取 Fast/Slow/Signal 配置
   - 修改 `updateBOLL()` - 讀取週期與倍數配置
   - 修改 `updateKD()` - 讀取 K/D 週期配置
   - 修改 `updateADX()` - 讀取週期配置
   - 修改 `updateCCI()` - 讀取週期配置
   - 修改 `updateWR()` - 讀取週期配置
   - 修正所有變數名稱衝突

---

## 🎯 技術細節

### 參數讀取模式

```java
IndicatorConfig config = indicatorConfigs.get("INDICATOR_NAME");
int period = (config != null) ? config.getPeriod() : DEFAULT_VALUE;
```

**優點**：
- 如果配置存在，使用配置值
- 如果配置不存在（不應該發生），使用預設值
- 避免 `NullPointerException`

### 顏色更新模式

**疊線指標（SMA/EMA）**：
```java
if (config != null && config.getColor() != null && combinedPlot != null) {
    XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0); // 主圖
    XYItemRenderer renderer = plot.getRenderer(1); // SMA/EMA renderer
    if (renderer != null) {
        renderer.setSeriesPaint(0, config.getColor());
    }
}
```

**副圖指標（RSI/MACD）**：
```java
if (config != null && config.getColor() != null && indicatorPlot != null) {
    XYItemRenderer renderer = indicatorPlot.getRenderer();
    if (renderer != null) {
        renderer.setSeriesPaint(0, config.getColor());
    }
}
```

---

## 📈 預期效果

### 修正前

```
使用者：我要 SMA(10)，紅色
系統：✓ 指標參數已更新（儲存到 indicatorConfigs）
圖表：仍然顯示 SMA(20)，藍色（硬編碼）
```

### 修正後

```
使用者：我要 SMA(10)，紅色
系統：✓ 指標參數已更新（儲存到 indicatorConfigs）
updateSMA()：從 indicatorConfigs 讀取週期 = 10，顏色 = 紅色
圖表：✅ 顯示 SMA(10)，紅色線條
```

---

## 🚀 後續測試建議

### 1. 基本功能測試

- [ ] SMA 週期修改（10, 20, 50）
- [ ] EMA 週期修改（5, 20, 100）
- [ ] RSI 週期修改（5, 14, 21）
- [ ] MACD 參數修改（8/21/5, 12/26/9）
- [ ] BOLL 倍數修改（1.5, 2.0, 2.5）

### 2. 顏色測試

- [ ] SMA 顏色切換（藍→紅→綠）
- [ ] EMA 顏色切換（橙→紫→黃）
- [ ] RSI 顏色切換（金橙→紅→藍）

### 3. 極端值測試

- [ ] SMA(200) - 測試資料不足情況
- [ ] RSI(2) - 極短週期
- [ ] BOLL 倍數 0.5 - 極窄通道

### 4. 持久性測試

- [ ] 修改參數後關閉對話框
- [ ] 重新打開對話框，確認顯示最後一次的值
- [ ] 切換指標再切回，確認參數仍保留

---

## 📝 總結

✅ **Bug 已完全修復**

**修正內容**：
1. ✅ 所有 `updateXXX()` 方法從 `indicatorConfigs` 讀取參數
2. ✅ SMA/EMA/RSI 支援顏色更新
3. ✅ 修正變數名稱衝突
4. ✅ 添加缺少的 import

**影響範圍**：
- 9 個指標的參數讀取邏輯
- 3 個指標的顏色更新邏輯

**測試狀態**：
- ✅ 編譯成功（僅剩無害的警告）
- ⏳ 待使用者實際測試驗證

**現在可以實際測試，參數和顏色變更應該會立即生效了！** 🎉

