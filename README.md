# 🏠 DreamHouseTrading - 交易工作站

> 基於 Java Swing 打造的現代化股票交易模擬平台

![Java](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-3.8+-blue)
![FlatLaf](https://img.shields.io/badge/FlatLaf-3.6.1-green)
![License](https://img.shields.io/badge/License-Learning-yellow)

---

## ✨ 主要特色

### 📊 專業級圖表系統
- **即時 K 線圖**: 每秒更新，支援多種時間週期 (1分/5分/15分/30分/1小時/1日/1週)
- **成交量背景**: 半透明柱狀圖，顏色跟隨漲跌
- **技術指標**: SMA, EMA, RSI, MACD
- **互動工具**: 十字線、縮放、平移

### 💹 完整市場數據
- **觀察清單**: 多商品監控，即時價格更新
- **五檔掛單**: 模擬買賣盤口深度
- **逐筆成交**: 時間序列成交記錄
- **市場消息**: 資訊推播 (預留)

### 🎨 現代化介面
- **Modern Docking**: 可拖曳、可停靠的面板系統
- **深色/淺色主題**: 一鍵切換，圖表同步更新
- **中英文介面**: 完整國際化支援
- **快捷鍵**: 高效操作體驗

---

## 🚀 快速開始

### 前置需求
- **JDK 17** 或更高版本
- **Maven 3.8+**
- **NetBeans 17** (推薦) 或其他 Java IDE

### 安裝步驟

1. **克隆專案**
```bash
git clone <repository-url>
cd DreamHouseTrading
```

2. **設定 Maven** (首次使用)
```bash
# 複製設定檔到 Maven 目錄
cp settings.xml ~/.m2/settings.xml  # Linux/Mac
copy settings.xml %USERPROFILE%\.m2\settings.xml  # Windows
```

3. **編譯專案**
```bash
mvn clean compile
```

4. **執行程式**
```bash
mvn exec:java
```

或在 NetBeans 中：
- 按 `Shift + F11` 清理建置
- 按 `F6` 執行

---

## 📸 功能預覽

### 主要畫面
```
┌─────────────────────────────────────────────────────────────────┐
│ File  View  Layout  Help                    🔍 AAPL [工具列]    │
├──────────┬─────────────────────────────────────┬────────────────┤
│ 觀察清單  │                                     │  逐筆成交      │
│ ─────── │        📈 K 線圖表 + 成交量          │  ─────────    │
│ AAPL    │           + SMA(20)                 │  15:30:42     │
│ TSLA    │                                     │  $180.50      │
│ MSFT    │                                     │  B 1000       │
├──────────┤─────────────────────────────────────├────────────────┤
│ 五檔掛單  │        📉 RSI(14) 副圖              │  市場消息      │
│ ─────── │                                     │  ─────────    │
│ Ask 5檔 │                                     │  新聞標題...   │
│ Bid 5檔 │                                     │                │
├──────────┴─────────────────────────────────────┴────────────────┤
│ ● 模擬中  商品: AAPL  最新: $180.50 (+2.5%)  週期: 1m  FPS: 60 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎯 核心功能

### 1️⃣ 即時行情模擬
- ⏱️ **更新頻率**: 每秒 1 次
- 📊 **數據儲存**: 最多 500 根 K 線
- 💰 **價格模型**: 隨機遊走
- 📈 **成交量**: 動態生成 (100-600)

### 2️⃣ 技術分析工具

**疊加指標 (主圖)**
| 指標 | 週期 | 顏色 | 說明 |
|------|------|------|------|
| SMA  | 20   | 🟡 金色 | 簡單移動平均 |
| EMA  | 20   | 🔵 天藍色 | 指數移動平均 |

**副圖指標**
| 指標 | 參數 | 顏色 | 說明 |
|------|------|------|------|
| RSI  | 14   | 🟠 橘色 | 相對強弱指標 (0-100) |
| MACD | 12,26,9 | 🔵🟠 | 指數平滑異同平均 |

### 3️⃣ 圖表互動

**縮放**
- 🔍+ 放大 (`Ctrl + =`)
- 🔍- 縮小 (`Ctrl + -`)
- ⟲ 重置 (`Ctrl + 0`)
- 🖱️ 滑鼠滾輪縮放

**工具**
- ✛ 十字線 (預設開啟)
- 📈 趨勢線 (預留)
- ─ 水平線 (預留)

### 4️⃣ 市場數據面板

**觀察清單**
- 多商品同時監控
- 即時價格更新
- 漲跌幅顏色標示
- 雙擊切換商品

**五檔掛單**
- 5 檔買單 (Bid)
- 5 檔賣單 (Ask)
- 每秒動態更新

**逐筆成交**
- 時間戳記
- 價格、數量
- 買賣方向標示

---

## ⚙️ 設定與自訂

### 可調整參數

**K 線儲存數量** (`ChartDock.java`)
```java
private static final int MAX_BARS = 500;  // 預設 500 根
```

**指標週期** (`IndicatorService.java`)
```java
indicatorService.getSMA(20);   // SMA 週期
indicatorService.getEMA(20);   // EMA 週期
indicatorService.getRSI(14);   // RSI 週期
indicatorService.getMACD(12, 26, 9);  // MACD 快線, 慢線, 訊號線
```

**更新頻率** (`SimulatorFeed.java`)
```java
executor.scheduleAtFixedRate(
    this::generateMarketData, 
    0, 1000,  // 毫秒，1000 = 1秒
    TimeUnit.MILLISECONDS
);
```

---

## ⌨️ 快捷鍵

| 功能 | Windows/Linux | Mac |
|------|---------------|-----|
| 放大 | `Ctrl + =` | `Cmd + =` |
| 縮小 | `Ctrl + -` | `Cmd + -` |
| 重置縮放 | `Ctrl + 0` | `Cmd + 0` |
| 重置佈局 | `Ctrl + L` | `Cmd + L` |

---

## 🎨 主題切換

**深色主題** (預設)
- 圖表背景：深灰 (#1E1E1E)
- 網格線：中灰 (#3C3C3C)

**淺色主題**
- 圖表背景：純白 (#FFFFFF)
- 網格線：淺灰 (#DCDCDC)

**切換方式**: `View → Theme → Light / Dark`

---

## 🌍 多語言支援

支援語言：
- 🇹🇼 **繁體中文** (預設)
- 🇺🇸 **English**

**切換方式**: `View → Language → 中文 / English`

**翻譯範圍**:
- ✅ 選單
- ✅ 工具列
- ✅ 狀態列
- ✅ 面板標題
- ✅ 圖表標籤
- ✅ 按鈕文字

---

## 📦 技術架構

### 技術棧
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

### 專案結構
```
DreamHouseTrading/
├── src/main/java/com/dreamhouse/trading/
│   ├── Main.java                    # 程式入口
│   ├── core/                        # 核心邏輯
│   │   ├── MarketDataFeed.java      # 行情介面
│   │   ├── SimulatorFeed.java       # 模擬器
│   │   ├── IndicatorService.java    # 指標服務
│   │   └── model/                   # 數據模型
│   ├── ui/                          # 使用者介面
│   │   ├── MainFrameWithDocking.java  # 主視窗
│   │   ├── StatusBar.java           # 狀態列
│   │   ├── MenuBarFactory.java      # 選單
│   │   ├── ToolBarFactory.java      # 工具列
│   │   └── dock/                    # 面板元件
│   │       ├── ChartDock.java       # 圖表
│   │       ├── WatchlistPanel.java  # 觀察清單
│   │       ├── OrderBookDock.java   # 掛單簿
│   │       ├── TimeSalesDock.java   # 成交明細
│   │       └── NewsDock.java        # 新聞
│   └── util/
│       └── I18n.java                # 國際化工具
├── src/main/resources/
│   ├── messages_zh.properties       # 中文翻譯
│   └── messages_en.properties       # 英文翻譯
├── pom.xml                          # Maven 設定
├── settings.xml                     # Maven 映射設定
├── FEATURES.md                      # 詳細功能清單
├── MAVEN_SETUP_GUIDE.md            # Maven 設定指南
├── MODERN_DOCKING_GUIDE.md         # Modern Docking 指南
└── I18N_GUIDE.md                   # 國際化指南
```

---

## 📚 相關文檔

| 文檔 | 說明 |
|------|------|
| **[FEATURES.md](FEATURES.md)** | 📋 完整功能清單與技術細節 |
| **[MAVEN_SETUP_GUIDE.md](MAVEN_SETUP_GUIDE.md)** | 🔧 Maven 環境設定教學 |
| **[MODERN_DOCKING_GUIDE.md](MODERN_DOCKING_GUIDE.md)** | 🪟 Modern Docking 使用說明 |
| **[I18N_GUIDE.md](I18N_GUIDE.md)** | 🌍 多語言翻譯對照表 |

---

## 🔧 疑難排解

### 常見問題

**Q1: 編譯失敗，找不到依賴套件**
```bash
# 解決方法：清理並重新下載
mvn clean
mvn dependency:purge-local-repository
mvn compile
```

**Q2: 圖表沒有顯示數據**
- 確認程式已啟動超過 1 秒（第一根 K 線需要時間）
- 檢查狀態列是否顯示「● 模擬中」
- 查看 FPS 是否大於 0

**Q3: Modern Docking 無法下載**
- 使用提供的 `settings.xml` 設定 Maven 映射
- 參考 [MAVEN_SETUP_GUIDE.md](MAVEN_SETUP_GUIDE.md)

**Q4: 主題切換後圖表沒有變色**
- 確認使用的是 `MainFrameWithDocking` 而非 `MainFrameAdvanced`
- 重新啟動程式

---

## 🚀 未來規劃

### 短期目標 (1-2 個月)

#### 1. 完善繪圖工具 🎨
- [ ] **趨勢線繪製**
  - 滑鼠拖曳繪製趨勢線
  - 支援線條編輯（移動、調整角度）
  - 線條樣式設定（顏色、粗細、虛線）
  - 趨勢線突破提醒
  - 保存與載入繪圖
  
- [ ] **水平線繪製**
  - 快速標記支撐/壓力位
  - 價格標籤顯示
  - 批量管理水平線
  - 顏色分類（支撐綠、壓力紅）
  
- [ ] **測量工具**
  - 價格漲跌幅測量
  - 時間跨度測量
  - K線數量統計

#### 2. 擴充技術指標 📊
- [ ] **趨勢類指標**
  - BOLL (布林通道) - 20, 2σ
  - MACD 加入零軸線
  - DMI/ADX (趨向指標)
  - SAR (拋物線轉向)
  
- [ ] **震盪類指標**
  - KD (隨機指標) - 9, 3, 3
  - CCI (順勢指標) - 14
  - Williams %R - 14
  
- [ ] **成交量指標**
  - OBV (能量潮)
  - VRSI (量相對強弱)
  - 成交量 MA

- [ ] **指標自訂**
  - 週期參數調整介面
  - 指標顏色自訂
  - 多指標疊加（最多3個）

#### 3. 多時間框架分析 ⏱️
- [ ] **同步顯示**
  - 分割視窗顯示多週期
  - 1分 + 5分 + 15分 三聯圖
  - 時間軸同步捲動
  
- [ ] **快速切換**
  - Tab 鍵快速切換週期
  - 週期比較模式
  - 主/次週期標示
  
- [ ] **數據對齊**
  - 高低週期K線對齊
  - 指標數值同步
  - 游標同步定位

#### 4. 歷史數據與回測 📈
- [ ] **數據管理**
  - CSV 檔案匯入/匯出
  - 歷史數據下載（Yahoo Finance API）
  - 數據庫儲存（SQLite）
  - 數據修復與補齊
  
- [ ] **回測引擎**
  - 簡單策略回測框架
  - 買賣訊號標記
  - 績效統計（勝率、最大回撤）
  - 資金曲線圖
  
- [ ] **策略範例**
  - 雙均線交叉策略
  - RSI 超買超賣策略
  - MACD 金叉死叉策略
  - 布林通道突破策略

#### 5. 使用體驗優化 ✨
- [ ] **圖表增強**
  - 背景網格密度調整
  - 字體大小設定
  - K線寬度自適應
  - 圖表截圖功能
  
- [ ] **數據顯示**
  - Tooltip 資訊增強（顯示所有指標數值）
  - K線資訊懸浮視窗
  - 即時漲跌排行榜
  
- [ ] **快捷操作**
  - 空白鍵暫停/恢復更新
  - 方向鍵逐根K線瀏覽
  - Home/End 跳至首尾
  - PageUp/PageDown 翻頁

### 中期目標
- [ ] 接入真實行情 API
- [ ] 策略編輯器與回測引擎
- [ ] 下單交易介面
- [ ] 部位管理系統

### 長期目標
- [ ] 資料庫持久化
- [ ] 雲端同步
- [ ] 移動端應用
- [ ] AI 智能輔助

---

## 📄 授權資訊

本專案為**學習與展示用途**。

使用的開源套件授權：
- **FlatLaf**: Apache License 2.0
- **JFreeChart**: LGPL
- **ta4j**: MIT License
- **Modern Docking**: MIT License
- **GlazedLists**: MPL / LGPL

---

## 🤝 貢獻指南

歡迎提交 Issue 和 Pull Request！

**開發流程**:
1. Fork 本專案
2. 創建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交變更 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 開啟 Pull Request

---

## 📞 聯絡方式

- 📧 Email: (請填寫)
- 💬 Discord: (請填寫)
- 🐛 Issues: [GitHub Issues](請填寫)

---

## 🙏 致謝

感謝以下開源專案：
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) - 美觀的 Look and Feel
- [JFreeChart](https://github.com/jfree/jfreechart) - 強大的圖表庫
- [ta4j](https://github.com/ta4j/ta4j) - 技術分析工具
- [Modern Docking](https://github.com/andrewauclair/ModernDocking) - 現代化停靠系統

---

<div align="center">

**⭐ 如果這個專案對您有幫助，請給一個 Star！⭐**

Made with ❤️ by DreamHouse Trading Team

</div>

