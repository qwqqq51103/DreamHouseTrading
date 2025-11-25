# 🏠 DreamHouseTrading - 交易工作站

> 基於 Java Swing 打造的現代化股票交易模擬平台

![Java](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-3.8+-blue)
![FlatLaf](https://img.shields.io/badge/FlatLaf-3.6.1-green)
![License](https://img.shields.io/badge/License-Learning-yellow)

> 📋 **完整文檔**: [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md) 包含詳細的技術細節、開發歷程和進度追蹤

---

## ✨ 主要特色

### 📊 專業級圖表系統
- **即時 K 線圖**: 每秒更新，支援多種時間週期 (1分/5分/15分/30分/1小時/1日/1週)
- **成交量背景**: 半透明柱狀圖，顏色跟隨漲跌
- **技術指標**: SMA, EMA, RSI, MACD, BOLL, KD, ADX, OBV, CCI, Williams %R
- **繪圖工具**: 趨勢線、水平線、十字線
- **互動功能**: 縮放、平移、參數自訂、顏色調整

### 💹 完整市場數據
- **多數據源支援**: Yahoo Finance、Alpha Vantage、Finnhub、IEX Cloud、Polygon.io、**FinMind API (台股專用)**
- **台股即時數據**: 支援 FinMind API Sponsor 會員功能
  - 1分鐘K線（TaiwanStockKBar）
  - 逐筆成交（TaiwanStockPriceTick）
  - 市場消息（TaiwanStockNews）
  - 即時快照（taiwan_stock_tick_snapshot，10秒更新）
- **資料庫整合** ✨ NEW (2025-11-25)
  - 與 MarketDataCollector 整合，從 MySQL 載入歷史數據
  - 補充開盤後缺失數據（如 10:00 啟動程式，自動載入 9:00-10:00 的歷史數據）
  - 切換商品時自動載入該商品的歷史數據
  - 手動按鈕「📊 載入歷史數據」
  - 智能股票代號匹配（自動補全 `.TW` 後綴）
- **觀察清單**: 多商品監控，即時價格更新
- **五檔掛單**: 真實買賣盤口（最佳買賣價）
- **逐筆成交**: 時間序列成交記錄，真實 TickType
- **市場消息**: 即時新聞推播

### 🎨 現代化介面
- **Modern Docking**: 可拖曳、可停靠的面板系統
- **深色/淺色主題**: 一鍵切換，圖表同步更新
- **中英文介面**: 完整國際化支援
- **CSV 數據管理**: 匯入/匯出歷史數據
- **快捷鍵**: 高效操作體驗

---

## 🚀 快速開始

### 前置需求
- **JDK 17** 或更高版本
- **Maven 3.8+**
- **NetBeans 17** (推薦) 或其他 Java IDE
- **FinMind API Token** (選用，用於台股數據)
- **MySQL 8.0+** (選用，用於資料庫整合功能)

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

4. **設定數據源** (選用)

建立 `datasource.properties` 檔案並填入 API keys：
```properties
# FinMind API (台股數據)
finmind.apitoken=YOUR_FINMIND_TOKEN
datasource.type=FINMIND

# 其他數據源
alphavantage.apikey=YOUR_KEY
finnhub.apikey=YOUR_KEY
iexcloud.apikey=YOUR_KEY
polygon.apikey=YOUR_KEY
```

> 💡 **取得 FinMind Token**: 前往 [FinMind 官網](https://finmindtrade.com/) 註冊並獲取 API Token

5. **執行程式**
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

### 1️⃣ 多數據源支援

**支援的數據源**:
| 數據源 | 類型 | 地區 | 說明 |
|--------|------|------|------|
| **FinMind** | 真實 | 🇹🇼 台灣 | **推薦**：台股即時數據，支援 1分K、逐筆、新聞 |
| Yahoo Finance | 真實 | 🌎 全球 | 免費，延遲數據 |
| Alpha Vantage | 真實 | 🌎 全球 | 需 API Key |
| Finnhub | 真實 | 🌎 全球 | 需 API Key，台股需付費 |
| IEX Cloud | 真實 | 🇺🇸 美股 | 需 API Key |
| Polygon.io | 真實 | 🇺🇸 美股 | 需 API Key |
| Simulator | 模擬 | - | 本地隨機數據生成 |

**FinMind API 功能（台股專用）**:
- ⏱️ **即時更新**: 10秒快照（交易時段 09:00-13:30）
- 📊 **1分鐘K線**: 支援 Sponsor 會員 TaiwanStockKBar
- 📈 **時間週期聚合**: 客戶端聚合為 5min/15min/30min/60min/Daily/Weekly
- 💹 **逐筆成交**: 真實 TickType（1=買進、2=賣出）
- 📰 **市場消息**: 即時新聞推播
- 🔄 **自動控制**: 交易時段自動啟用，盤後自動停止

### 2️⃣ 即時行情（模擬模式）
- ⏱️ **更新頻率**: 每秒 1 次
- 📊 **數據儲存**: 最多 500 根 K 線
- 💰 **價格模型**: 隨機遊走
- 📈 **成交量**: 動態生成 (100-600)

### 3️⃣ 技術分析工具

**疊加指標 (主圖)**
| 指標 | 週期 | 顏色 | 說明 |
|------|------|------|------|
| SMA  | 20   | 🟡 金色 | 簡單移動平均 |
| EMA  | 20   | 🔵 天藍色 | 指數移動平均 |
| BOLL | 20,2 | 🟣 紫色 | 布林通道 |

**副圖指標**
| 指標 | 參數 | 顏色 | 說明 |
|------|------|------|------|
| RSI  | 14   | 🟠 橘色 | 相對強弱指標 (0-100) |
| MACD | 12,26,9 | 🔵🟠 | 指數平滑異同平均 |
| KD   | 9,3  | 🟢🔴 | 隨機指標 |
| ADX  | 14   | 🟤 棕色 | 趨向指標 |
| OBV  | -    | 🟦 藍色 | 能量潮 |
| CCI  | 14   | 🟨 黃色 | 順勢指標 |
| WR   | 14   | 🟪 紫色 | 威廉指標 |

### 4️⃣ 圖表互動

**縮放**
- 🔍+ 放大 (`Ctrl + =`)
- 🔍- 縮小 (`Ctrl + -`)
- ⟲ 重置 (`Ctrl + 0`)
- 🖱️ 滑鼠滾輪縮放

**繪圖工具**
- ✛ 十字線 (預設開啟)
- 📈 趨勢線 (已完成)
- ─ 水平線 (已完成)

### 5️⃣ 市場數據面板

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

## 🇹🇼 FinMind API 台股數據配置

### 📋 前置準備

1. **註冊 FinMind 帳號**
   - 前往 [FinMind 官網](https://finmindtrade.com/)
   - 註冊並登入帳號
   - 前往「API 管理」頁面取得 API Token

2. **選擇會員方案**
   - **Free 會員**: 僅日線歷史數據
   - **Backer 會員**: 日線 + 逐筆成交
   - **Sponsor 會員**: ⭐ **推薦** - 完整功能（1分K線、即時快照）

### ⚙️ 配置步驟

1. **建立配置檔案**

   在專案根目錄建立 `datasource.properties`：
   ```properties
   # FinMind API Token（必填）
   finmind.apitoken=YOUR_API_TOKEN_HERE

   # 設定預設數據源為 FinMind
   datasource.type=FINMIND
   ```

2. **啟動程式**

   程式會自動偵測 `datasource.properties` 並載入 FinMind 數據源。

3. **切換數據源**

   在程式中透過 UI 切換數據源：
   - 工具列 → 「數據源」下拉選單 → 選擇「FinMind」

### 📊 支援的台股功能

| API 端點 | 功能 | 會員要求 | 說明 |
|----------|------|---------|------|
| `TaiwanStockPrice` | 日線數據 | Free | 歷史日線 K 線 |
| `TaiwanStockPriceTick` | 逐筆成交 | Backer+ | 歷史 Tick 數據 |
| `TaiwanStockKBar` | 1分鐘K線 | **Sponsor** | 分鐘級 K 線（當日） |
| `taiwan_stock_tick_snapshot` | 即時快照 | **Sponsor** | 10秒更新（盤中） |
| `TaiwanStockNews` | 市場消息 | Free | 個股新聞 |

### 🕐 交易時段自動偵測

程式會自動判斷是否為台股交易時段：
- **交易時段**: 週一至週五 09:00-13:30
- **盤中**: 自動啟用即時更新（10秒一次）
- **盤後**: 自動停止更新，避免資源浪費

### 💡 使用範例

**觀察台積電 (2330)**:
1. 在觀察清單輸入 `2330.TW`
2. 選擇時間週期（1分/5分/15分/30分/60分/日/週）
3. 查看 K 線圖表、逐筆成交、市場消息

**注意事項**:
- 股票代碼需加上 `.TW` 或 `.TWO` 後綴
- 1分鐘K線僅限當日數據
- 五檔掛單僅顯示最佳買賣價（API 限制）

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

**核心技術棧**:
- **UI**: Java Swing + FlatLaf + Modern Docking
- **圖表**: JFreeChart + ta4j (技術指標)
- **建構**: Maven + JDK 17

> 📋 **詳細架構**: 請參考 [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md#技術架構)

---

## 📚 完整文檔

| 文檔 | 說明 |
|------|------|
| **[PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md)** | 📋 **統一項目文檔** - 包含所有功能、開發歷程、技術細節 |

> 💡 **重要**: 所有詳細的技術文檔、開發歷程、問題解決方案都已整合到 `PROJECT_DOCUMENTATION.md` 中，並包含完整的進度追蹤系統。

---

## 🔧 疑難排解

### 快速解決

**編譯失敗**:
```bash
mvn clean compile
```

**圖表無數據**: 等待1秒，檢查狀態列「● 模擬中」

**Modern Docking 問題**: 使用提供的 `settings.xml`

> 🐛 **完整問題解決**: 請參考 [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md#問題解決)

---

## 🗄️ 資料庫整合 (NEW!)

> **新功能** (2025-11-25): 與 MarketDataCollector 整合，自動補充開盤後缺失的歷史數據

### 功能說明

**問題場景**: 當您在盤中（如 10:00）啟動程式時，只能獲取「當前時刻」之後的即時數據，缺少從開盤（9:00）到啟動時刻的歷史數據。

**解決方案**:
1. 使用 MarketDataCollector 背景服務持續收集市場數據到 MySQL
2. DreamHouseTrading 啟動或切換商品時，自動從資料庫載入歷史數據
3. 支持手動按鈕觸發載入

### 設置步驟

1. **安裝並啟動 MySQL** - 確保 MySQL 服務正在運行
2. **啟動 MarketDataCollector** - 運行 `啟動真實API收集器.bat`，持續收集市場數據
3. **啟動 DreamHouseTrading** - 運行 `啟動-完整編譯.bat`

### 使用方式

**自動載入（推薦）**:
- 程式啟動時自動載入已訂閱商品的歷史數據（僅在交易時間內）
- 切換商品時自動載入該商品的歷史數據

**手動載入**: 點擊工具列的「📊 載入歷史數據」按鈕

### 特色功能

- ✅ **智能股票代號匹配**: 輸入 `3706` 自動匹配資料庫中的 `3706.TW`
- ✅ **自動載入**: 切換商品時在背景執行緒載入，不阻塞 UI
- ✅ **完整歷史**: 補充開盤（9:00）至今的所有 tick 數據
- ✅ **詳細日誌**: 顯示載入進度、數據筆數、時間範圍等

### 診斷工具

如果遇到問題，使用以下診斷工具：
- **測試資料庫載入.bat** - 測試資料庫連接和數據載入
- **診斷資料庫查詢.bat** - 診斷 SQL 查詢問題
- **簡易診斷步驟.txt** - 分步診斷指南

> 📋 **技術細節**: 詳細的實現說明和修復記錄，請參閱 [PROJECT_DOCUMENTATION.md - 第 I 章：資料庫整合與修復記錄](PROJECT_DOCUMENTATION.md)

---

## 🚀 未來規劃

### 📅 **近期目標**

#### ✅ **已完成** (2025-01-13)
- 📈 **K線圖表系統** - 即時更新、多時間週期
- 🎨 **繪圖工具** - 趨勢線、水平線
- 📊 **技術指標** - SMA/EMA/RSI/MACD/BOLL/KD/ADX/OBV/CCI/WR
- 🌐 **國際化** - 中英文切換
- 📁 **CSV 管理** - 匯入/匯出功能
- 🇹🇼 **FinMind API 整合** - 台股即時數據、1分K線、逐筆成交、市場消息
- 🔄 **多數據源支援** - 7種數據源（含模擬器）

#### 🔄 **進行中**
- 🎯 **測量工具** - 價格/時間測量
- 📊 **回測系統** - 策略框架
- 📈 **更多指標** - SAR/ATR/VRSI

#### 📅 **下一步**
- 🔗 **外部數據** - Yahoo Finance API
- ⏱️ **多時間框架** - 同步顯示
- 🤖 **智能功能** - 策略編輯器

> 📋 **詳細規劃**: 請參考 [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md#未來規劃)

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
