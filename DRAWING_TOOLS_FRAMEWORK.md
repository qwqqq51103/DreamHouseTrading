# ✅ 繪圖工具基礎架構完成

## 📐 架構概述

完整的繪圖工具基礎架構已經建立，為圖表上的各種繪圖功能提供了強大的基礎。

---

## 🏗️ 架構組件

### 1. **DrawingObject.java** - 繪圖對象基類

**位置**：`src/main/java/com/dreamhouse/trading/ui/chart/DrawingObject.java`

**功能**：
- ✅ 所有繪圖對象的抽象基類
- ✅ 定義基本屬性（ID、顏色、線條樣式、選擇狀態、可見性）
- ✅ 定義核心方法（繪製、命中測試、移動、克隆）
- ✅ 提供選擇框和控制點的繪製

**核心方法**：
```java
public abstract void draw(Graphics2D g2, Rectangle2D plotArea);
public abstract boolean hitTest(double x, double y, Rectangle2D plotArea);
public abstract Rectangle2D getBounds(Rectangle2D plotArea);
public abstract void move(double dx, double dy, Rectangle2D plotArea);
public abstract DrawingObject clone();
```

**屬性**：
- `id` - 唯一標識符（UUID）
- `color` - 線條顏色
- `stroke` - 線條樣式
- `selected` - 是否被選中
- `visible` - 是否可見

---

### 2. **TrendLine.java** - 趨勢線

**位置**：`src/main/java/com/dreamhouse/trading/ui/chart/TrendLine.java`

**功能**：
- ✅ 由兩個點定義的直線
- ✅ 支援端點拖曳調整
- ✅ 命中測試（點到線的距離計算）
- ✅ 選中時顯示端點控制點

**特點**：
- 螢幕座標系統（x1, y1, x2, y2）
- 支援延伸線條（extend 屬性，未來功能）
- 端點命中測試（10 像素容差）
- 線條命中測試（5 像素容差）

**使用範例**：
```java
TrendLine line = new TrendLine(100, 200, 300, 150);
line.setColor(Color.BLUE);
line.setStroke(new BasicStroke(2.0f));
```

---

### 3. **HorizontalLine.java** - 水平線

**位置**：`src/main/java/com/dreamhouse/trading/ui/chart/HorizontalLine.java`

**功能**：
- ✅ 跨越整個圖表寬度的水平線
- ✅ 標記價格水平、支撐位、阻力位
- ✅ 顯示價格標籤
- ✅ 支援三種線條樣式（實線、虛線、點線）

**線條樣式**：
```java
public enum LineStyle {
    SOLID,      // 實線
    DASHED,     // 虛線
    DOTTED      // 點線
}
```

**特點**：
- 只能上下移動（垂直方向）
- 自動顯示價格標籤（右側）
- 標籤背景半透明
- 5 像素命中容差

**使用範例**：
```java
HorizontalLine line = new HorizontalLine(150.50, "Support");
line.setColor(Color.RED);
line.setStyle(HorizontalLine.LineStyle.DASHED);
```

---

### 4. **DrawingManager.java** - 繪圖工具管理器

**位置**：`src/main/java/com/dreamhouse/trading/ui/chart/DrawingManager.java`

**功能**：
- ✅ 管理所有繪圖對象
- ✅ 處理繪製流程（開始、更新、完成）
- ✅ 對象選擇和取消選擇
- ✅ 對象移動和刪除
- ✅ 工具切換

**支援的工具**：
```java
public enum DrawingTool {
    NONE,           // 選擇工具
    TREND_LINE,     // 趨勢線
    HORIZONTAL_LINE,// 水平線
    VERTICAL_LINE,  // 垂直線（未來）
    RECTANGLE,      // 矩形（未來）
    FIBONACCI       // 斐波那契回調（未來）
}
```

**核心功能**：

#### A. 繪製流程
```java
// 1. 開始繪製（滑鼠按下）
manager.startDrawing(x, y, plotArea);

// 2. 更新繪製（滑鼠拖曳）
manager.updateDrawing(x, y, plotArea);

// 3. 完成繪製（滑鼠釋放）
manager.finishDrawing(x, y, plotArea);

// 4. 取消繪製（ESC 鍵）
manager.cancelDrawing();
```

#### B. 選擇和編輯
```java
// 選擇對象
manager.selectObjectAt(x, y, plotArea);

// 移動選中對象
manager.moveSelected(dx, dy, plotArea);

// 刪除選中對象
manager.deleteSelected();

// 取消所有選擇
manager.deselectAll();
```

#### C. 工具管理
```java
// 切換工具
manager.setCurrentTool(DrawingTool.TREND_LINE);

// 獲取當前工具
DrawingTool tool = manager.getCurrentTool();
```

---

### 5. **ChartOverlay.java** - 圖表覆蓋層

**位置**：`src/main/java/com/dreamhouse/trading/ui/chart/ChartOverlay.java`

**功能**：
- ✅ 在 JFreeChart 上繪製自訂對象
- ✅ 座標轉換工具方法

**整合方式**：
```java
// 創建 DrawingManager
DrawingManager drawingManager = new DrawingManager();

// 創建覆蓋層
ChartOverlay overlay = new ChartOverlay(drawingManager);

// 添加到圖表
XYPlot plot = chart.getXYPlot();
plot.addAnnotation(overlay);
```

**座標轉換**：
```java
// 螢幕座標 → 數據座標
double[] dataCoords = ChartOverlay.screenToData(
    screenX, screenY, dataArea, domainAxis, rangeAxis, plot
);

// 數據座標 → 螢幕座標
double[] screenCoords = ChartOverlay.dataToScreen(
    dataX, dataY, dataArea, domainAxis, rangeAxis, plot
);
```

---

## 🎨 設計模式

### 1. **策略模式（Strategy Pattern）**
- `DrawingObject` 作為抽象策略
- `TrendLine`, `HorizontalLine` 等作為具體策略
- `DrawingManager` 作為上下文

### 2. **命令模式（Command Pattern）**
- 每個繪圖操作（移動、刪除）可封裝為命令
- 未來可支援 Undo/Redo 功能

### 3. **工廠模式（Factory Pattern）**
- `DrawingManager` 根據當前工具創建對象
- 統一創建邏輯

---

## 🔄 使用流程

### 場景 1：繪製趨勢線

```java
// 1. 設定工具
drawingManager.setCurrentTool(DrawingTool.TREND_LINE);

// 2. 使用者操作
// 滑鼠按下
drawingManager.startDrawing(startX, startY, plotArea);

// 滑鼠拖曳
drawingManager.updateDrawing(currentX, currentY, plotArea);

// 滑鼠釋放
drawingManager.finishDrawing(endX, endY, plotArea);

// 3. 繪圖完成，自動添加到列表
```

### 場景 2：選擇並移動對象

```java
// 1. 切換到選擇工具
drawingManager.setCurrentTool(DrawingTool.NONE);

// 2. 點擊選擇
drawingManager.selectObjectAt(x, y, plotArea);

// 3. 拖曳移動
drawingManager.moveSelected(dx, dy, plotArea);
```

### 場景 3：刪除對象

```java
// 選擇對象
drawingManager.selectObjectAt(x, y, plotArea);

// 刪除（通常綁定到 Delete 鍵）
drawingManager.deleteSelected();
```

---

## 📊 類別關係圖

```
DrawingObject (抽象基類)
    ├── TrendLine
    ├── HorizontalLine
    ├── VerticalLine (未來)
    ├── Rectangle (未來)
    └── FibonacciRetracement (未來)

DrawingManager
    ├── 管理 List<DrawingObject>
    ├── 處理繪製邏輯
    └── 提供選擇/編輯功能

ChartOverlay (JFreeChart Annotation)
    └── 使用 DrawingManager 繪製所有對象
```

---

## 🎯 特性總覽

### ✅ 已實作

1. **繪圖對象基類**
   - 完整的抽象基類
   - 選擇框和控制點繪製
   - 克隆支援

2. **趨勢線**
   - 兩點定義
   - 端點拖曳
   - 命中測試

3. **水平線**
   - 價格水平標記
   - 多種線條樣式
   - 價格標籤

4. **繪圖管理器**
   - 完整的繪製流程
   - 對象選擇和編輯
   - 工具切換

5. **圖表覆蓋層**
   - JFreeChart 整合
   - 座標轉換

### 🚀 未來擴展

1. **更多繪圖工具**
   - [ ] 垂直線
   - [ ] 矩形/橢圓
   - [ ] 斐波那契回調
   - [ ] 文字標籤
   - [ ] 箭頭

2. **進階功能**
   - [ ] Undo/Redo（撤銷/重做）
   - [ ] 複製/貼上
   - [ ] 鎖定對象（防止移動）
   - [ ] 圖層管理
   - [ ] 對齊工具

3. **持久化**
   - [ ] 儲存繪圖到檔案
   - [ ] 載入繪圖從檔案
   - [ ] 匯出為圖片

4. **樣式管理**
   - [ ] 預設樣式設定
   - [ ] 樣式模板
   - [ ] 顏色主題

---

## 🔧 整合到 ChartDock

### 下一步驟

為了將繪圖工具整合到 `ChartDock.java`，需要：

1. **添加 DrawingManager 實例**
```java
private DrawingManager drawingManager;
```

2. **初始化覆蓋層**
```java
drawingManager = new DrawingManager();
ChartOverlay overlay = new ChartOverlay(drawingManager);
mainPlot.addAnnotation(overlay);
```

3. **添加滑鼠監聽器**
```java
chartPanel.addChartMouseListener(new ChartMouseListener() {
    @Override
    public void chartMouseClicked(ChartMouseEvent event) {
        // 處理點擊事件
    }
    
    @Override
    public void chartMouseMoved(ChartMouseEvent event) {
        // 處理滑鼠移動
    }
});
```

4. **工具列整合**
- 添加繪圖工具按鈕
- 綁定工具切換事件

---

## 📝 程式碼範例

### 完整使用範例

```java
// 創建管理器
DrawingManager manager = new DrawingManager();

// 創建覆蓋層並添加到圖表
ChartOverlay overlay = new ChartOverlay(manager);
plot.addAnnotation(overlay);

// 繪製趨勢線
manager.setCurrentTool(DrawingTool.TREND_LINE);
manager.startDrawing(100, 200, plotArea);
manager.updateDrawing(200, 150, plotArea);
manager.finishDrawing(300, 100, plotArea);

// 繪製水平線
manager.setCurrentTool(DrawingTool.HORIZONTAL_LINE);
manager.startDrawing(0, 180, plotArea);
manager.finishDrawing(0, 180, plotArea);  // 水平線只需要 Y 座標

// 切換到選擇工具
manager.setCurrentTool(DrawingTool.NONE);

// 選擇對象
manager.selectObjectAt(150, 175, plotArea);

// 刪除選中對象
manager.deleteSelected();

// 獲取所有趨勢線
List<TrendLine> trendLines = manager.getObjectsOfType(TrendLine.class);

// 清除所有繪圖
manager.clearAll();
```

---

## ✅ 完成度：100%

**狀態**：✅ **基礎架構完成**

**編譯狀態**：✅ 無錯誤（僅1個未使用 import 警告）

**已實作組件**：
- ✅ DrawingObject（抽象基類）
- ✅ TrendLine（趨勢線）
- ✅ HorizontalLine（水平線）
- ✅ DrawingManager（管理器）
- ✅ ChartOverlay（覆蓋層）

**架構特點**：
- ✅ 可擴展性強（易於添加新工具）
- ✅ 職責分離（各類別功能單一）
- ✅ 完整的選擇和編輯功能
- ✅ JFreeChart 無縫整合

**下一步**：
- 整合到 ChartDock
- 添加滑鼠事件處理
- 創建工具列按鈕

**繪圖工具基礎架構已就緒！** 🎉

