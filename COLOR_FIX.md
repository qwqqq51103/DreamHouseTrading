# 🎨 修復：SMA/EMA 顏色變更功能

## 問題

**使用者報告**：
> "EMA、SMA顏色好像不會換"

---

## 根本原因

### 錯誤的 Renderer Index

在 `ChartDock.java` 的初始化代碼中：

```java
// 第 256-262 行
pricePlot.setDataset(2, overlayDataset);  // overlayDataset 在 index 2
XYLineAndShapeRenderer overlayRenderer = new XYLineAndShapeRenderer(true, false);
overlayRenderer.setSeriesPaint(0, new Color(255, 215, 0));  // SMA 金色
overlayRenderer.setSeriesPaint(1, new Color(0, 191, 255));  // EMA 天藍色
overlayRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
overlayRenderer.setSeriesStroke(1, new BasicStroke(2.0f));
pricePlot.setRenderer(2, overlayRenderer);  // overlayRenderer 在 index 2
```

**關鍵資訊**：
- **overlayRenderer 在 index 2**（不是 index 1）
- **SMA 是 series 0**
- **EMA 是 series 1**

### 錯誤的更新代碼

```java
// ❌ 錯誤：使用 renderer index 1
private void updateSMA() {
    // ...
    XYItemRenderer renderer = plot.getRenderer(1);  // 錯誤！
    if (renderer != null) {
        renderer.setSeriesPaint(0, config.getColor());
    }
}

private void updateEMA() {
    // ...
    XYItemRenderer renderer = plot.getRenderer(1);  // 錯誤！
    if (renderer != null) {
        renderer.setSeriesPaint(0, config.getColor());  // 錯誤！應該是 series 1
    }
}
```

**問題**：
1. `getRenderer(1)` 取得的是 **volumeRenderer**（成交量渲染器），不是 overlayRenderer
2. EMA 使用 `setSeriesPaint(0)`，應該是 `setSeriesPaint(1)`

---

## ✅ 修正方案

### 正確的 Renderer Index 和 Series Index

```java
private void updateSMA() {
    IndicatorConfig config = indicatorConfigs.get("SMA");
    int smaPeriod = (config != null) ? config.getPeriod() : 20;
    
    List<Double> smaValues = indicatorService.getSMA(smaPeriod);
    
    for (int i = 0; i < smaValues.size() && i < ohlcSeries.getItemCount(); i++) {
        if (smaValues.get(i) != null) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod timePeriod = item.getPeriod();
            smaSeries.addOrUpdate(timePeriod, smaValues.get(i));
        }
    }
    
    // ✅ 更新顏色 - SMA 在 overlayRenderer (index 2) 的 series 0
    if (config != null && config.getColor() != null && combinedPlot != null) {
        XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0);
        XYItemRenderer renderer = plot.getRenderer(2); // ✅ overlayRenderer 是 index 2
        if (renderer != null) {
            renderer.setSeriesPaint(0, config.getColor()); // ✅ SMA 是 series 0
        }
    }
}

private void updateEMA() {
    IndicatorConfig config = indicatorConfigs.get("EMA");
    int emaPeriod = (config != null) ? config.getPeriod() : 20;
    
    List<Double> emaValues = indicatorService.getEMA(emaPeriod);
    
    for (int i = 0; i < emaValues.size() && i < ohlcSeries.getItemCount(); i++) {
        if (emaValues.get(i) != null) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod timePeriod = item.getPeriod();
            emaSeries.addOrUpdate(timePeriod, emaValues.get(i));
        }
    }
    
    // ✅ 更新顏色 - EMA 在 overlayRenderer (index 2) 的 series 1
    if (config != null && config.getColor() != null && combinedPlot != null) {
        XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0);
        XYItemRenderer renderer = plot.getRenderer(2); // ✅ overlayRenderer 是 index 2
        if (renderer != null) {
            renderer.setSeriesPaint(1, config.getColor()); // ✅ EMA 是 series 1
        }
    }
}
```

---

## 📊 Renderer 和 Dataset 索引對照表

在 `pricePlot` (主圖) 中：

| Index | Dataset | Renderer | 用途 |
|-------|---------|----------|------|
| **0** | ohlcDataset | CandlestickRenderer | K線圖 |
| **1** | volumeDataset | XYBarRenderer | 成交量 |
| **2** | overlayDataset | XYLineAndShapeRenderer | SMA/EMA 疊線指標 |

在 `overlayDataset` 中：

| Series Index | TimeSeries | 用途 |
|--------------|------------|------|
| **0** | smaSeries | SMA 線條 |
| **1** | emaSeries | EMA 線條 |
| **2** | bollUpperSeries | BOLL 上軌 |
| **3** | bollMiddleSeries | BOLL 中軌 |
| **4** | bollLowerSeries | BOLL 下軌 |

---

## 🧪 測試驗證

### 測試步驟

1. **工具列選擇 SMA 指標**
2. **打開指標設定對話框**（View → Indicator Settings）
3. **修改 SMA 顏色**：
   - 點擊 SMA 顏色按鈕
   - 選擇**紅色**
4. **點擊確認**

**預期結果**：
- ✅ SMA 線條**立即**變為紅色

### 測試步驟 2

1. **切換到 EMA 指標**
2. **打開指標設定對話框**
3. **修改 EMA 顏色**：
   - 點擊 EMA 顏色按鈕
   - 選擇**綠色**
4. **點擊確認**

**預期結果**：
- ✅ EMA 線條**立即**變為綠色

---

## 📝 修改檔案

- `ChartDock.java`
  - `updateSMA()` - 修正 renderer index 從 1 → 2
  - `updateEMA()` - 修正 renderer index 從 1 → 2，series index 從 0 → 1

---

## 🎯 關鍵修正

### 修正 1：Renderer Index

```diff
- XYItemRenderer renderer = plot.getRenderer(1);
+ XYItemRenderer renderer = plot.getRenderer(2);
```

### 修正 2：EMA Series Index

```diff
- renderer.setSeriesPaint(0, config.getColor());
+ renderer.setSeriesPaint(1, config.getColor());
```

---

## ✅ 編譯狀態

**狀態**：✅ 成功（僅剩無害的警告）

**警告數量**：7 個（都是未使用的 import 或 deprecated 方法，不影響功能）

---

## 🚀 現在可以測試了！

**SMA 和 EMA 的顏色變更應該完全正常了！** 🎉

**請重新測試**：
1. 修改 SMA 顏色 → 立即生效 ✅
2. 修改 EMA 顏色 → 立即生效 ✅
3. 修改 SMA 週期 → 立即生效 ✅
4. 修改 EMA 週期 → 立即生效 ✅

