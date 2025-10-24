# 📊 DreamHouse Trading Workstation - 完整項目文檔

> **統一項目文檔** - 包含所有功能、實作進度、技術細節與開發歷程

---

## 📋 目錄

- [📊 項目概述](#-項目概述)
- [🎯 功能清單](#-功能清單)
- [📈 開發進度追蹤](#-開發進度追蹤)
- [🏗️ 技術架構](#️-技術架構)
- [🚀 快速開始](#-快速開始)
- [📖 使用指南](#-使用指南)
- [🔧 開發指南](#-開發指南)
- [📝 開發歷程](#-開發歷程)
- [🐛 問題解決](#-問題解決)
- [🔮 未來規劃](#-未來規劃)

---

## 📊 項目概述

### 🏠 DreamHouse Trading Workstation

基於 Java Swing 打造的現代化股票交易模擬平台，提供專業級的技術分析工具和即時市場數據模擬。

### ✨ 核心特色

- **📈 即時 K 線圖表**: 支援多時間週期，動態成交量背景
- **🎨 繪圖工具**: 趨勢線、水平線、互動編輯
- **📊 技術指標**: 10+ 種專業指標，參數可調
- **🌐 國際化**: 完整中英文支援
- **🪟 Modern Docking**: 可拖曳面板系統
- **📁 數據管理**: CSV 匯入/匯出功能
- **🎯 模擬交易**: 即時市場數據模擬

### 📊 項目統計

- **程式語言**: Java 17
- **檔案數量**: 72 個檔案
- **程式碼行數**: 17,356 行
- **開發時間**: 2024年10月 (持續開發中)
- **架構**: 分層架構 (UI/Core/Widgets)

---

## 🎯 功能清單

### ✅ 已完成功能

#### 📊 圖表系統
- [x] **K 線圖表**
  - [x] 即時更新 (每秒1次)
  - [x] 多時間週期 (1m/5m/15m/30m/1h/1d/1w)
  - [x] 成交量背景 (顏色跟隨漲跌)
  - [x] 滑鼠縮放/平移
  - [x] 十字線工具

#### 🎨 繪圖工具
- [x] **趨勢線**
  - [x] 滑鼠拖曳繪製
  - [x] 端點編輯
  - [x] 顏色自訂
  - [x] 右鍵菜單
- [x] **水平線**
  - [x] 價格水平標記
  - [x] 自動價格標籤
  - [x] 多種線條樣式
  - [x] 顏色自訂

#### 📈 技術指標
- [x] **疊加指標 (主圖)**
  - [x] SMA (簡單移動平均)
  - [x] EMA (指數移動平均)
  - [x] BOLL (布林通道)
- [x] **副圖指標**
  - [x] RSI (相對強弱指標)
  - [x] MACD (指數平滑異同平均)
  - [x] KD (隨機指標)
  - [x] ADX (趨向指標)
  - [x] OBV (能量潮)
  - [x] CCI (順勢指標)
  - [x] Williams %R (威廉指標)
- [x] **指標自訂**
  - [x] 參數調整介面
  - [x] 顏色選擇
  - [x] 即時應用

#### 💹 市場數據
- [x] **觀察清單**
  - [x] 多商品監控
  - [x] 即時價格更新
  - [x] 漲跌幅顯示
  - [x] 雙擊切換商品
- [x] **五檔掛單**
  - [x] 買賣盤口深度
  - [x] 顏色區分 (綠買/紅賣)
  - [x] 動態更新
- [x] **逐筆成交**
  - [x] 時間序列記錄
  - [x] 買賣方向標示
  - [x] 最新在上排序
- [x] **市場消息**
  - [x] 假資料展示
  - [x] 時間/來源/標題

#### 🎨 使用者介面
- [x] **Modern Docking**
  - [x] 可拖曳面板
  - [x] 停靠區域
  - [x] 佈局保存
- [x] **主題系統**
  - [x] 深色/淺色主題
  - [x] 圖表同步更新
  - [x] 一鍵切換
- [x] **國際化**
  - [x] 中文/英文切換
  - [x] 完整翻譯覆蓋
  - [x] 動態語言切換
- [x] **狀態列**
  - [x] 連線狀態顯示
  - [x] 商品資訊
  - [x] 更新頻率 (FPS)
  - [x] 通用訊息顯示

#### 📁 數據管理
- [x] **CSV 功能**
  - [x] 歷史數據匯入
  - [x] 格式驗證
  - [x] 預覽功能
  - [x] 數據匯出
  - [x] 自訂格式

#### 🔧 開發工具
- [x] **建構系統**
  - [x] Maven 配置
  - [x] 依賴管理
  - [x] 一鍵執行
- [x] **程式碼品質**
  - [x] 分層架構
  - [x] 介面分離
  - [x] 完整註解

### 🔄 進行中功能

#### 🎯 繪圖工具增強
- [ ] **測量工具**
  - [ ] 價格漲跌幅測量
  - [ ] 時間跨度測量
  - [ ] K線數量統計
- [ ] **進階功能**
  - [ ] 趨勢線突破提醒
  - [ ] 繪圖保存/載入
  - [ ] 複製/貼上

#### 📊 回測系統
- [ ] **回測引擎**
  - [ ] 策略框架
  - [ ] 訊號生成
  - [ ] 績效統計
- [ ] **內建策略**
  - [ ] 雙均線策略
  - [ ] RSI 策略
  - [ ] MACD 策略
  - [ ] 布林通道策略

### 📅 待開發功能

#### 📈 技術指標擴充
- [ ] SAR (拋物線轉向)
- [ ] VRSI (量相對強弱)
- [ ] 成交量 MA
- [ ] ATR (平均真實範圍)

#### ⏱️ 多時間框架
- [ ] 同步顯示
- [ ] 三聯圖模式
- [ ] 快速切換

#### 🌐 數據來源
- [ ] Yahoo Finance API
- [ ] 即時數據接入
- [ ] 數據庫儲存

---

## 📈 開發進度追蹤

### 📊 總體進度

| 模組 | 完成度 | 狀態 | 最後更新 |
|------|--------|------|----------|
| 圖表系統 | 95% | ✅ 完成 | 2024-10-24 |
| 繪圖工具 | 85% | 🟡 進行中 | 2024-10-25 |
| 技術指標 | 90% | ✅ 完成 | 2024-10-24 |
| 市場數據 | 100% | ✅ 完成 | 2024-10-23 |
| 使用者介面 | 100% | ✅ 完成 | 2024-10-24 |
| 數據管理 | 75% | 🟡 進行中 | 2024-10-25 |
| 回測系統 | 0% | 🔴 待開始 | - |

### 📅 開發時程表

#### 第一階段 (已完成) - 基礎功能
- ✅ **2024-10-20**: 項目初始化，基礎架構
- ✅ **2024-10-21**: K線圖表系統
- ✅ **2024-10-22**: 技術指標實作
- ✅ **2024-10-23**: Modern Docking 整合
- ✅ **2024-10-24**: 國際化與主題系統

#### 第二階段 (已完成) - 進階功能
- ✅ **2024-10-24**: 指標參數自訂
- ✅ **2024-10-25**: CSV 數據管理
- ✅ **2024-10-25**: 繪圖工具基礎架構
- ✅ **2024-10-25**: 趨勢線與水平線

#### 第三階段 (進行中) - 功能完善
- 🔄 **2024-10-25**: 繪圖工具增強
- 📅 **2024-10-26**: 回測系統框架
- 📅 **2024-10-27**: 策略實作

### 🎯 里程碑

| 里程碑 | 目標日期 | 狀態 | 完成日期 |
|--------|----------|------|----------|
| MVP 版本 | 2024-10-23 | ✅ | 2024-10-23 |
| 繪圖工具完成 | 2024-10-25 | ✅ | 2024-10-25 |
| 回測系統 | 2024-10-28 | 📅 | - |
| 1.0 正式版 | 2024-11-01 | 📅 | - |

---

## 🏗️ 技術架構

### 📦 技術棧

```
Frontend (UI)
├── Swing (核心 UI 框架)
├── FlatLaf 3.6.1 (現代化外觀)
├── MigLayout 11.3 (佈局管理)
└── Modern Docking 1.3.1 (可停靠面板)

Chart & Analysis
├── JFreeChart 1.5.4 (圖表繪製)
└── ta4j 0.15 (技術指標計算)

Data Management
├── GlazedLists 1.11.0 (動態表格)
└── Java Concurrent (執行緒安全)

Build Tools
├── Maven 3.8+ (建構管理)
└── JDK 17 (執行環境)
```

### 🗂️ 專案結構

```
DreamHouseTrading/
├── src/main/java/com/dreamhouse/trading/
│   ├── Main.java                           # 程式入口
│   ├── core/                               # 核心邏輯層
│   │   ├── MarketDataFeed.java             # 市場數據介面
│   │   ├── SimulatorFeed.java              # 數據模擬器
│   │   ├── IndicatorService.java           # 技術指標服務
│   │   ├── IndicatorConfig.java            # 指標配置
│   │   ├── Timeframe.java                  # 時間週期
│   │   ├── csv/
│   │   │   └── CsvDataManager.java         # CSV 數據管理
│   │   └── model/                          # 數據模型
│   │       ├── Bar.java                    # K線數據
│   │       ├── Tick.java                   # 即時報價
│   │       ├── Trade.java                  # 成交記錄
│   │       ├── DepthLevel.java             # 掛單深度
│   │       └── NewsItem.java               # 新聞項目
│   ├── ui/                                 # 使用者介面層
│   │   ├── MainFrameWithDocking.java       # 主視窗 (Modern Docking)
│   │   ├── MainFrame.java                  # 主視窗 (傳統)
│   │   ├── StatusBar.java                  # 狀態列
│   │   ├── MenuBarFactory.java             # 選單工廠
│   │   ├── ToolBarFactory.java             # 工具列工廠
│   │   ├── chart/                          # 繪圖工具
│   │   │   ├── DrawingManager.java         # 繪圖管理器
│   │   │   ├── DrawingObject.java          # 繪圖對象基類
│   │   │   ├── TrendLine.java              # 趨勢線
│   │   │   ├── HorizontalLine.java         # 水平線
│   │   │   └── ChartOverlay.java           # 圖表覆蓋層
│   │   ├── dialog/                         # 對話框
│   │   │   ├── IndicatorSettingsDialog.java # 指標設定
│   │   │   ├── CsvImportDialog.java        # CSV 匯入
│   │   │   └── CsvExportDialog.java        # CSV 匯出
│   │   └── dock/                           # 面板元件
│   │       ├── ChartDock.java              # 圖表面板
│   │       ├── WatchlistPanel.java         # 觀察清單
│   │       ├── OrderBookDock.java          # 掛單簿
│   │       ├── TimeSalesDock.java          # 成交明細
│   │       └── NewsDock.java               # 新聞面板
│   └── util/
│       └── I18n.java                       # 國際化工具
├── src/main/resources/
│   ├── messages_zh.properties              # 中文翻譯
│   └── messages_en.properties              # 英文翻譯
├── pom.xml                                 # Maven 配置
├── settings.xml                            # Maven 鏡像設定
├── sample_data.csv                         # 範例數據
├── sample_data_recent.csv                  # 最新範例數據
└── PROJECT_DOCUMENTATION.md               # 統一項目文檔
```

### 🔄 架構設計原則

#### 分層架構
- **UI 層**: 使用者介面，負責顯示和交互
- **Core 層**: 業務邏輯，數據處理和計算
- **Model 層**: 數據模型，定義數據結構

#### 設計模式
- **Factory Pattern**: MenuBarFactory, ToolBarFactory
- **Observer Pattern**: MarketDataListener
- **Strategy Pattern**: DrawingObject 繼承體系
- **Singleton Pattern**: I18n 國際化工具

#### 執行緒安全
- **EDT 原則**: 所有 UI 更新在事件分派執行緒
- **SwingUtilities**: 使用 invokeLater 確保執行緒安全
- **Concurrent**: 市場數據模擬使用 ScheduledExecutorService

---

## 🚀 快速開始

### 📋 前置需求

- **JDK 17** 或更高版本
- **Maven 3.8+**
- **NetBeans 17** (推薦) 或其他 Java IDE

### ⚡ 快速啟動

1. **克隆專案**
```bash
git clone <repository-url>
cd DreamHouseTrading
```

2. **設定 Maven** (首次使用)
```bash
# Windows
copy settings.xml %USERPROFILE%\.m2\settings.xml

# Linux/Mac
cp settings.xml ~/.m2/settings.xml
```

3. **編譯並執行**
```bash
mvn clean compile exec:java
```

### 🎮 基本操作

#### 圖表操作
- **縮放**: 滑鼠滾輪 或 `Ctrl + =/-`
- **平移**: 滑鼠拖曳
- **重置**: `Ctrl + 0`

#### 繪圖工具
1. 點擊工具列 **📈 趨勢線** 按鈕
2. 在圖表上拖曳繪製
3. 右鍵編輯顏色或刪除

#### 指標設定
1. `View → 指標設定`
2. 調整參數和顏色
3. 點擊確認應用

---

## 📖 使用指南

### 📊 圖表功能

#### K 線圖表
- **時間週期**: 工具列下拉選單切換
- **成交量**: 半透明背景，綠漲紅跌
- **十字線**: 預設開啟，顯示價格和時間

#### 技術指標使用

**疊加指標 (主圖)**
```
SMA(20)  - 簡單移動平均，金色線條
EMA(20)  - 指數移動平均，藍色線條
BOLL(20,2) - 布林通道，紫色上下軌
```

**副圖指標**
```
RSI(14)    - 相對強弱指標 (0-100)
MACD(12,26,9) - 快慢線與柱狀圖
KD(9,3)    - 隨機指標 %K 和 %D
ADX(14)    - 趨向指標，含 +DI/-DI
OBV        - 能量潮，成交量指標
CCI(14)    - 順勢指標
Williams %R(14) - 威廉指標
```

### 🎨 繪圖工具

#### 趨勢線繪製
1. 點擊 **📈 趨勢線** 按鈕
2. 在圖表上點擊起點
3. 拖曳到終點並釋放
4. 完成後可拖曳端點調整

#### 水平線繪製
1. 點擊 **─ 水平線** 按鈕
2. 在圖表上點擊價格位置
3. 自動生成跨越圖表的水平線
4. 右側顯示價格標籤

#### 編輯繪圖對象
- **選擇**: 切換回選擇工具，點擊對象
- **移動**: 拖曳選中的對象
- **顏色**: 右鍵選單 → 變更顏色
- **刪除**: 右鍵選單 → 刪除

### 📁 數據管理

#### CSV 匯入
1. `File → 匯入 CSV...`
2. 選擇 CSV 檔案
3. 設定是否包含標題行
4. 預覽並驗證格式
5. 點擊匯入

**支援格式**:
```csv
Timestamp,Open,High,Low,Close,Volume
2024-10-25 09:30:00,100.0,101.5,99.5,101.0,1000
```

#### CSV 匯出
1. `File → 匯出 CSV...`
2. 選擇匯出路徑
3. 設定日期格式
4. 選擇是否包含標題
5. 點擊匯出

### 🌐 國際化

#### 語言切換
- `View → Language → 中文/English`
- 即時切換，無需重啟

#### 主題切換
- `View → Theme → Light/Dark`
- 圖表顏色同步更新

---

## 🔧 開發指南

### 🛠️ 開發環境設定

#### NetBeans 設定
1. 開啟 NetBeans 17
2. `File → Open Project`
3. 選擇 DreamHouseTrading 資料夾
4. 等待 Maven 依賴下載完成

#### Maven 設定
```xml
<!-- 主要依賴 -->
<dependencies>
    <dependency>
        <groupId>com.formdev</groupId>
        <artifactId>flatlaf</artifactId>
        <version>3.6.1</version>
    </dependency>
    <dependency>
        <groupId>org.jfree</groupId>
        <artifactId>jfreechart</artifactId>
        <version>1.5.4</version>
    </dependency>
    <dependency>
        <groupId>org.ta4j</groupId>
        <artifactId>ta4j-core</artifactId>
        <version>0.15</version>
    </dependency>
</dependencies>
```

### 📝 程式碼規範

#### 命名規範
- **類別**: PascalCase (ChartDock)
- **方法**: camelCase (updateIndicators)
- **常數**: UPPER_SNAKE_CASE (MAX_BARS)
- **套件**: 小寫點分隔 (com.dreamhouse.trading)

#### 註解規範
```java
/**
 * 圖表面板類別
 * 負責顯示 K 線圖表和技術指標
 */
public class ChartDock extends JPanel {
    
    /**
     * 更新技術指標
     * @param indicatorName 指標名稱
     */
    public void updateIndicator(String indicatorName) {
        // 實作內容
    }
}
```

### 🔌 擴展指南

#### 新增技術指標
1. 在 `IndicatorService.java` 新增計算方法
2. 在 `ChartDock.java` 新增繪製邏輯
3. 更新 `ToolBarFactory.java` 下拉選單
4. 新增國際化字串

#### 新增繪圖工具
1. 繼承 `DrawingObject` 基類
2. 實作抽象方法 (draw, hitTest, move)
3. 在 `DrawingManager` 新增工具類型
4. 更新工具列按鈕

#### 新增面板
1. 繼承 `JPanel` 或實作 `Dockable`
2. 實作 `MarketDataListener` (如需要)
3. 在 `MainFrameWithDocking` 註冊面板
4. 新增選單項目

---

## 📝 開發歷程

### 🎯 第一階段：基礎架構 (2024-10-20 ~ 2024-10-23)

#### 2024-10-20: 項目初始化
- ✅ 建立 Maven 專案結構
- ✅ 配置基礎依賴 (Swing, FlatLaf)
- ✅ 實作 Main 入口點
- ✅ 建立分層架構 (ui/core/model)

**關鍵決策**:
- 選擇 FlatLaf 作為現代化 Look & Feel
- 採用 Maven 作為建構工具
- 確立分層架構設計

#### 2024-10-21: K線圖表系統
- ✅ 整合 JFreeChart 圖表庫
- ✅ 實作 OHLC 燭台圖表
- ✅ 新增成交量背景顯示
- ✅ 實作市場數據模擬器

**技術亮點**:
- CombinedDomainXYPlot 組合圖表
- 自訂 XYBarRenderer 實現成交量顏色
- ScheduledExecutorService 定時數據更新

#### 2024-10-22: 技術指標實作
- ✅ 整合 ta4j 技術分析庫
- ✅ 實作 SMA/EMA 疊加指標
- ✅ 實作 RSI/MACD 副圖指標
- ✅ 建立 IndicatorService 服務層

**實作細節**:
```java
// SMA 計算與繪製
SimpleMovingAverageIndicator sma = new SimpleMovingAverageIndicator(closePrices, period);
for (int i = 0; i < barCount; i++) {
    double value = sma.getValue(i).doubleValue();
    smaSeries.addOrUpdate(getTimePeriod(i), value);
}
```

#### 2024-10-23: Modern Docking 整合
- ✅ 整合 Modern Docking 1.3.1
- ✅ 實作可拖曳面板系統
- ✅ 建立觀察清單、掛單簿等面板
- ✅ 完成 MVP 版本

**挑戰與解決**:
- **問題**: Modern Docking 依賴下載失敗
- **解決**: 配置 settings.xml Maven 鏡像
- **問題**: 面板初始化順序
- **解決**: 調整 Docking.initialize() 時機

### 🎨 第二階段：進階功能 (2024-10-24 ~ 2024-10-25)

#### 2024-10-24: 國際化與主題
- ✅ 實作完整國際化系統
- ✅ 支援中文/英文動態切換
- ✅ 整合深色/淺色主題
- ✅ 圖表顏色主題同步

**國際化實作**:
```java
// I18n 工具類
public class I18n {
    private static ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.getDefault());
    
    public static String get(String key) {
        return bundle.getString(key);
    }
}
```

**主題切換邏輯**:
```java
// 主題更新
private void updateThemeColors() {
    if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
        plotBackgroundColor = new Color(30, 30, 30);
        gridLineColor = new Color(60, 60, 60);
    } else {
        plotBackgroundColor = Color.WHITE;
        gridLineColor = new Color(220, 220, 220);
    }
}
```

#### 2024-10-24: 指標參數自訂
- ✅ 建立 IndicatorSettingsDialog
- ✅ 實作參數調整介面
- ✅ 支援顏色自訂
- ✅ 即時參數應用

**參數配置系統**:
```java
public class IndicatorConfig {
    private int period = 20;
    private Color color = Color.BLUE;
    private double multiplier = 2.0;
    // getters and setters
}
```

#### 2024-10-25: CSV 數據管理
- ✅ 實作 CsvDataManager 工具類
- ✅ 建立 CSV 匯入對話框
- ✅ 支援格式驗證與預覽
- ✅ 實作 CSV 匯出功能

**CSV 處理邏輯**:
```java
public static List<Bar> importFromCsv(File file, boolean hasHeader) throws IOException {
    List<Bar> bars = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        String line;
        boolean headerSkipped = !hasHeader;
        
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }
            bars.add(parseCsvLine(line));
        }
    }
    return bars;
}
```

#### 2024-10-25: 繪圖工具系統
- ✅ 建立繪圖工具基礎架構
- ✅ 實作 DrawingManager 管理器
- ✅ 完成趨勢線繪製功能
- ✅ 完成水平線繪製功能
- ✅ 整合到 ChartDock

**繪圖架構設計**:
```java
// 繪圖對象基類
public abstract class DrawingObject {
    public abstract void draw(Graphics2D g2, Rectangle2D plotArea);
    public abstract boolean hitTest(double x, double y, Rectangle2D plotArea);
    public abstract void move(double dx, double dy, Rectangle2D plotArea);
}

// 趨勢線實作
public class TrendLine extends DrawingObject {
    private double x1, y1, x2, y2;
    // 實作繪製邏輯
}
```

### 🔧 技術挑戰與解決方案

#### 挑戰 1: Maven 依賴管理
**問題**: Modern Docking 依賴無法下載
**解決方案**: 
- 配置 settings.xml 使用國內鏡像
- 新增 Maven Central 備用源
- 清理本地快取重新下載

#### 挑戰 2: 執行緒安全
**問題**: UI 更新導致 EDT 違規
**解決方案**:
```java
// 確保 UI 更新在 EDT 執行
SwingUtilities.invokeLater(() -> {
    chartPanel.repaint();
});
```

#### 挑戰 3: JFreeChart 客製化
**問題**: 成交量柱狀圖顏色控制
**解決方案**:
```java
// 自訂 XYBarRenderer
XYBarRenderer volumeRenderer = new XYBarRenderer() {
    @Override
    public Paint getItemPaint(int row, int column) {
        // 根據 K 線漲跌決定顏色
        return isUpBar ? Color.GREEN : Color.RED;
    }
};
```

#### 挑戰 4: 座標轉換
**問題**: 滑鼠座標與圖表數據座標轉換
**解決方案**:
```java
// 螢幕座標轉換
Point2D point = chartPanel.translateScreenToJava2D(mousePoint);
Rectangle2D dataArea = chartPanel.getScreenDataArea();
double screenX = point.getX() - dataArea.getX();
double screenY = point.getY() - dataArea.getY();
```

### 📊 程式碼統計

| 類別 | 檔案數 | 程式碼行數 | 主要功能 |
|------|--------|------------|----------|
| Core | 8 | 2,500 | 數據處理、指標計算 |
| UI | 15 | 8,000 | 使用者介面 |
| Chart | 5 | 1,200 | 繪圖工具 |
| Dialog | 3 | 800 | 對話框 |
| Dock | 6 | 4,000 | 面板元件 |
| Util | 1 | 200 | 工具類 |
| Resources | 2 | 350 | 國際化 |
| **總計** | **40** | **17,050** | - |

---

## 🐛 問題解決

### 🔧 常見問題

#### Q1: 編譯失敗，找不到依賴套件
```bash
# 解決方法：清理並重新下載
mvn clean
mvn dependency:purge-local-repository
mvn compile
```

#### Q2: Modern Docking 無法下載
**症狀**: `Could not resolve dependencies for project`
**解決方案**:
1. 使用提供的 `settings.xml`
2. 清理 Maven 快取: `rm -rf ~/.m2/repository`
3. 重新編譯: `mvn clean compile`

#### Q3: 圖表沒有顯示數據
**可能原因**:
- 程式啟動時間不足 (需等待1秒)
- 模擬器未正確啟動

**檢查方法**:
- 狀態列顯示「● 模擬中」
- FPS > 0
- 觀察清單有價格更新

#### Q4: 主題切換後圖表沒有變色
**解決方案**:
- 確認使用 `MainFrameWithDocking`
- 檢查 `refreshTheme()` 方法調用
- 重新啟動程式

#### Q5: 繪圖工具無法使用
**檢查項目**:
- 工具按鈕是否正確切換
- 滑鼠事件是否正確綁定
- 圖表區域是否正確計算

### 🐛 已修復的 Bug

#### Bug #1: 編譯錯誤 - ta4j 類型轉換
**問題**: `BigDecimal cannot be converted to org.ta4j.core.num.Num`
**修復**: 使用 `barSeries.numOf(value)` 轉換
```java
// 修復前
BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(
    sma, BigDecimal.valueOf(multiplier)
);

// 修復後  
BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(
    sma, barSeries.numOf(multiplier)
);
```

#### Bug #2: EDT 違規錯誤
**問題**: `Cannot call invokeAndWait from the event dispatcher thread`
**修復**: 將歷史數據生成移到背景執行緒
```java
// 修復後
new Thread(() -> {
    generateHistoricalData();
}).start();
```

#### Bug #3: 指標參數不生效
**問題**: 指標設定對話框的參數沒有應用到圖表
**根本原因**: `updateXXX()` 方法使用硬編碼參數
**修復**: 從 `indicatorConfigs` 讀取參數
```java
// 修復前
SimpleMovingAverageIndicator sma = new SimpleMovingAverageIndicator(closePrices, 20);

// 修復後
IndicatorConfig config = indicatorConfigs.get("SMA");
SimpleMovingAverageIndicator sma = new SimpleMovingAverageIndicator(
    closePrices, config.getPeriod()
);
```

#### Bug #4: CSV 匯入驗證錯誤
**問題**: 包含註解行的 CSV 檔案驗證失敗
**修復**: 正確處理註解行和標題行
```java
// 修復邏輯
while ((line = reader.readLine()) != null) {
    if (line.startsWith("#") || line.trim().isEmpty()) continue;
    if (!headerSkipped) {
        headerSkipped = true;
        continue;
    }
    // 處理數據行
}
```

### 🔍 除錯技巧

#### 日誌輸出
```java
// 在關鍵位置添加日誌
System.out.println("Current tool: " + drawingManager.getCurrentTool());
System.out.println("Mouse clicked at: " + x + ", " + y);
```

#### 斷點除錯
- 在 NetBeans 中設定斷點
- 使用 Debug 模式執行 (`Ctrl + F5`)
- 檢查變數值和執行流程

#### 效能監控
```java
// 測量執行時間
long startTime = System.currentTimeMillis();
// 執行代碼
long endTime = System.currentTimeMillis();
System.out.println("Execution time: " + (endTime - startTime) + "ms");
```

---

## 🔮 未來規劃

### 📅 短期目標 (1-2 週)

#### 1. 繪圖工具完善
- [ ] **測量工具**
  - [ ] 價格漲跌幅測量
  - [ ] 時間跨度測量  
  - [ ] K線數量統計
- [ ] **進階功能**
  - [ ] 趨勢線突破提醒
  - [ ] 繪圖保存/載入
  - [ ] 複製/貼上功能

#### 2. 技術指標擴充
- [ ] **新增指標**
  - [ ] SAR (拋物線轉向)
  - [ ] VRSI (量相對強弱)
  - [ ] 成交量 MA
  - [ ] ATR (平均真實範圍)

#### 3. 回測系統基礎
- [ ] **框架建立**
  - [ ] 策略介面定義
  - [ ] 回測引擎核心
  - [ ] 績效統計模組
- [ ] **範例策略**
  - [ ] 雙均線交叉
  - [ ] RSI 超買超賣
  - [ ] MACD 金叉死叉

### 📈 中期目標 (1-2 個月)

#### 1. 多時間框架分析
- [ ] **同步顯示**
  - [ ] 分割視窗多週期
  - [ ] 時間軸同步捲動
  - [ ] 指標數值對齊

#### 2. 數據來源擴充
- [ ] **外部數據**
  - [ ] Yahoo Finance API
  - [ ] Alpha Vantage API
  - [ ] 本地數據庫儲存

#### 3. 使用體驗優化
- [ ] **圖表增強**
  - [ ] 背景網格密度調整
  - [ ] 字體大小設定
  - [ ] 圖表截圖功能
- [ ] **快捷操作**
  - [ ] 空白鍵暫停/恢復
  - [ ] 方向鍵逐根瀏覽
  - [ ] 快速跳轉功能

### 🚀 長期目標 (3-6 個月)

#### 1. 進階交易功能
- [ ] **模擬交易**
  - [ ] 下單介面
  - [ ] 部位管理
  - [ ] 風險控制
- [ ] **策略編輯器**
  - [ ] 視覺化策略編輯
  - [ ] 自訂指標公式
  - [ ] 策略回測報告

#### 2. 雲端與協作
- [ ] **數據同步**
  - [ ] 雲端儲存
  - [ ] 多裝置同步
  - [ ] 分享功能
- [ ] **社群功能**
  - [ ] 策略分享
  - [ ] 討論區
  - [ ] 排行榜

#### 3. AI 智能輔助
- [ ] **智能分析**
  - [ ] 型態識別
  - [ ] 趨勢預測
  - [ ] 風險評估
- [ ] **自動化**
  - [ ] 智能選股
  - [ ] 自動交易
  - [ ] 風險監控

### 🎯 版本規劃

#### v1.0 (目標: 2024-11-01)
- ✅ 基礎圖表系統
- ✅ 技術指標
- ✅ 繪圖工具
- ✅ 數據管理
- [ ] 回測系統

#### v1.1 (目標: 2024-11-15)
- [ ] 測量工具
- [ ] 更多技術指標
- [ ] 策略回測
- [ ] 效能優化

#### v1.2 (目標: 2024-12-01)
- [ ] 多時間框架
- [ ] 外部數據源
- [ ] 進階繪圖工具
- [ ] 使用體驗優化

#### v2.0 (目標: 2025-01-01)
- [ ] 模擬交易系統
- [ ] 策略編輯器
- [ ] 雲端同步
- [ ] 移動端支援

---

## 📊 項目總結

### 🎯 成就與亮點

#### 技術成就
- ✅ **完整的技術分析平台**: 整合 10+ 種專業技術指標
- ✅ **現代化 UI 設計**: Modern Docking + FlatLaf 主題系統
- ✅ **國際化支援**: 完整中英文切換
- ✅ **繪圖工具系統**: 可擴展的繪圖架構
- ✅ **數據管理**: CSV 匯入匯出與驗證

#### 架構優勢
- 🏗️ **分層架構**: 清晰的職責分離
- 🔌 **可擴展性**: 易於新增指標和工具
- 🧵 **執行緒安全**: 正確的 EDT 處理
- 📝 **程式碼品質**: 完整註解與文檔

#### 使用者體驗
- 🎨 **直觀操作**: 拖曳繪製，右鍵編輯
- ⚡ **即時回饋**: 參數調整即時生效
- 🌐 **多語言**: 動態語言切換
- 🎯 **專業功能**: 媲美商業軟體的功能

### 📈 學習收穫

#### 技術技能
- **Java Swing 進階**: 自訂元件與事件處理
- **JFreeChart 深度客製**: 圖表渲染與座標轉換
- **Maven 專案管理**: 依賴管理與建構優化
- **設計模式應用**: Factory, Observer, Strategy 模式

#### 軟體工程
- **架構設計**: 分層架構與模組化
- **程式碼組織**: 套件結構與命名規範
- **版本控制**: Git 工作流程
- **文檔撰寫**: 技術文檔與使用指南

### 🎓 經驗分享

#### 開發心得
1. **架構先行**: 良好的架構設計是成功的基礎
2. **漸進開發**: 從 MVP 開始，逐步完善功能
3. **使用者導向**: 重視使用體驗和操作直觀性
4. **文檔重要**: 完整的文檔有助於維護和擴展

#### 技術選型
1. **FlatLaf**: 現代化外觀，易於整合
2. **JFreeChart**: 功能強大，客製化靈活
3. **ta4j**: 專業的技術分析庫
4. **Modern Docking**: 提升專業感的面板系統

#### 挑戰與解決
1. **依賴管理**: 透過 Maven 鏡像解決下載問題
2. **執行緒安全**: 嚴格遵守 EDT 原則
3. **效能優化**: 合理的數據結構和更新策略
4. **使用者體驗**: 多次迭代優化操作流程

---

## 📞 聯絡與貢獻

### 🤝 貢獻指南

歡迎提交 Issue 和 Pull Request！

**開發流程**:
1. Fork 本專案
2. 創建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交變更 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 開啟 Pull Request

### 📧 聯絡方式

- 🐛 **Issues**: [GitHub Issues](請填寫)
- 💬 **討論**: [GitHub Discussions](請填寫)
- 📧 **Email**: (請填寫)

### 🙏 致謝

感謝以下開源專案：
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) - 美觀的 Look and Feel
- [JFreeChart](https://github.com/jfree/jfreechart) - 強大的圖表庫
- [ta4j](https://github.com/ta4j/ta4j) - 技術分析工具
- [Modern Docking](https://github.com/andrewauclair/ModernDocking) - 現代化停靠系統

---

<div align="center">

**⭐ 如果這個專案對您有幫助，請給一個 Star！⭐**

Made with ❤️ by DreamHouse Trading Team

**最後更新**: 2024-10-25  
**文檔版本**: v1.0  
**專案狀態**: 🟢 積極開發中

</div>
