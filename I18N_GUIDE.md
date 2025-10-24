# 國際化 (I18n) 使用指南

## 📖 概述

本專案支援多語言介面（目前支援繁體中文和英文），所有 UI 文字都應該使用 `I18n.get()` 方法來取得翻譯。

## 🌐 支援語言

- **繁體中文** (`Locale.TRADITIONAL_CHINESE`) - 預設語言
- **English** (`Locale.ENGLISH`)

## 📝 翻譯檔案位置

```
src/main/resources/
├── messages_zh.properties  (繁體中文)
└── messages_en.properties  (英文)
```

## 💻 程式碼中使用方式

### 1. 基本用法

```java
import com.dreamhouse.trading.util.I18n;

// 取得翻譯文字
String title = I18n.get("app.title");
String menuFile = I18n.get("menu.file");
```

### 2. 在 Swing 元件中使用

```java
// JLabel
JLabel label = new JLabel(I18n.get("toolbar.symbol"));

// JButton
JButton button = new JButton(I18n.get("watchlist.add"));

// JMenu
JMenu menu = new JMenu(I18n.get("menu.file"));

// JMenuItem
JMenuItem menuItem = new JMenuItem(I18n.get("menu.file.exit"));
```

### 3. 動態切換語言

```java
// 切換到英文
I18n.setLocale(Locale.ENGLISH);

// 切換到繁體中文
I18n.setLocale(Locale.TRADITIONAL_CHINESE);

// 切換後需要重新載入 UI
refreshUI();
```

## 🔑 現有翻譯鍵值

### 應用程式基本

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `app.title` | 夢想交易工作站 | DreamHouse Trading Workstation |

### 選單

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `menu.file` | 檔案 | File |
| `menu.file.exit` | 離開 | Exit |
| `menu.view` | 檢視 | View |
| `menu.view.theme` | 主題 | Theme |
| `menu.view.theme.light` | 明亮 | Light |
| `menu.view.theme.dark` | 深色 | Dark |
| `menu.view.language` | 語言 | Language |
| `menu.layout` | 布局 | Layout |
| `menu.layout.reset` | 重設布局 | Reset Layout |
| `menu.layout.save` | 儲存布局 | Save Layout |
| `menu.help` | 說明 | Help |
| `menu.help.about` | 關於 | About |

### 工具列

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `toolbar.symbol` | 商品: | Symbol: |
| `toolbar.addwatchlist` | 加入觀察清單 | Add to Watchlist |
| `toolbar.timeframe` | 週期: | Timeframe: |
| `toolbar.indicator` | 指標: | Indicator: |
| `toolbar.crosshair` | 十字線 | Crosshair |
| `toolbar.trendline` | 趨勢線 | Trendline |
| `toolbar.hline` | 水平線 | H-Line |
| `toolbar.zoomin` | 放大 | Zoom In |
| `toolbar.zoomout` | 縮小 | Zoom Out |
| `toolbar.reset` | 重設 | Reset |

### 狀態列

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `status.simulated` | 模擬 | SIMULATED |
| `status.disconnected` | 斷線 | DISCONNECTED |
| `status.symbol` | 商品: | Symbol: |
| `status.last` | 最新: | Last: |
| `status.tf` | 週期: | TF: |
| `status.fps` | FPS: | FPS: |

### 停靠面板

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `dock.watchlist` | 觀察清單 | Watchlist |
| `dock.orderbook` | 五檔掛單 | Order Book (5 Levels) |
| `dock.timesales` | 逐筆成交 | Time & Sales |
| `dock.news` | 市場消息 | Market News |
| `dock.chart` | 主圖 (K線 + 指標) | Chart (K Line + Indicators) |
| `dock.indicator` | 副圖 (RSI/MACD) | Indicators (RSI/MACD) |

### 觀察清單

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `watchlist.symbol` | 商品 | Symbol |
| `watchlist.last` | 最新價 | Last |
| `watchlist.change` | 漲跌% | Chg% |
| `watchlist.volume` | 成交量 | Vol |
| `watchlist.add` | 新增 | Add |
| `watchlist.remove` | 刪除 | Remove |

### 五檔掛單

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `orderbook.side` | 方向 | Side |
| `orderbook.price` | 價格 | Price |
| `orderbook.quantity` | 數量 | Quantity |
| `orderbook.level` | 檔位 | Level |
| `orderbook.bid` | 買 | BID |
| `orderbook.ask` | 賣 | ASK |

### 逐筆成交

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `timesales.time` | 時間 | Time |
| `timesales.price` | 價格 | Price |
| `timesales.quantity` | 量 | Qty |
| `timesales.side` | 方向 | Side |

### 市場消息

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `news.time` | 時間 | Time |
| `news.source` | 來源 | Source |
| `news.title` | 標題 | Title |

### 圖表

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `chart.title` | K線即時走勢 (含 SMA(20) & 成交量) | K Line (Realtime) with SMA(20) & Volume |
| `chart.time` | 時間 | Time |
| `chart.price` | 價格 | Price |
| `chart.volume` | 成交量 | Volume |

### 指標

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `indicator.none` | 無 | None |
| `indicator.sma` | SMA | SMA |
| `indicator.ema` | EMA | EMA |
| `indicator.rsi` | RSI | RSI |
| `indicator.macd` | MACD | MACD |
| `indicator.rsi.title` | RSI(14) | RSI(14) |
| `indicator.macd.title` | MACD(12,26,9) | MACD(12,26,9) |

### 時間週期

| 鍵值 | 中文 | 英文 |
|------|------|------|
| `timeframe.1m` | 1分 | 1 Min |
| `timeframe.5m` | 5分 | 5 Min |
| `timeframe.15m` | 15分 | 15 Min |
| `timeframe.30m` | 30分 | 30 Min |
| `timeframe.1h` | 1小時 | 1 Hour |
| `timeframe.1d` | 1日 | 1 Day |
| `timeframe.1w` | 1週 | 1 Week |

## ➕ 如何新增翻譯

### 步驟 1：在翻譯檔案中加入新的鍵值

**messages_zh.properties**
```properties
# 新增的鍵值
my.new.key=我的新翻譯
```

**messages_en.properties**
```properties
# New key
my.new.key=My New Translation
```

### 步驟 2：在程式碼中使用

```java
String text = I18n.get("my.new.key");
```

## ⚠️ 注意事項

1. **Unicode 編碼**：`messages_zh.properties` 中的中文需要使用 Unicode 編碼（`\uXXXX`）
   - IDE 通常會自動處理，或使用 `native2ascii` 工具轉換
   
2. **特殊字符轉義**：
   - 換行符：使用 `\n`
   - 冒號：使用 `\:`（如果在鍵值中）
   
3. **命名規範**：
   - 使用小寫字母和點號分隔：`category.subcategory.key`
   - 保持鍵值簡潔且有意義
   
4. **保持同步**：
   - 在 `messages_zh.properties` 和 `messages_en.properties` 中保持相同的鍵值
   - 確保兩個檔案的鍵值數量一致

## 🔄 語言切換流程

1. 使用者在選單中選擇語言
2. 呼叫 `I18n.setLocale(locale)`
3. 呼叫 `refreshUI()` 重新載入介面
4. 所有使用 `I18n.get()` 的文字都會自動更新

## 📚 參考資源

- Java ResourceBundle 官方文檔
- Properties 檔案格式規範
- Unicode 編碼表

---

**最後更新**：2025-10-24

