# 🚀 進階功能實作計劃

**創建日期**: 2025-10-24  
**狀態**: 進行中

---

## 📊 總覽

本文檔詳細說明 **指標自訂介面** 和 **歷史回測系統** 的完整實作計劃。

### 進度追蹤

| 功能模組 | 狀態 | 完成度 | 預估工時 |
|---------|------|--------|----------|
| 2.5 指標自訂介面 | 🟡 進行中 | 50% | 8-10 小時 |
| 3.1 CSV 數據管理 | ⏳ 待開始 | 0% | 10-12 小時 |
| 3.2 回測引擎 | ⏳ 待開始 | 0% | 15-20 小時 |
| 3.3 內建策略 | ⏳ 待開始 | 0% | 10-15 小時 |
| 3.4 績效統計 | ⏳ 待開始 | 0% | 10-12 小時 |

---

## 📝 2.5 指標自訂介面

### ✅ 已完成

#### 1. `IndicatorSettingsDialog.java`
**位置**: `src/main/java/com/dreamhouse/trading/ui/dialog/`

**功能**:
- ✅ GUI 對話框設計
- ✅ 所有指標參數的 Spinner 控制項
- ✅ 顏色選擇器 (JColorChooser)
- ✅ 確認/取消/重設按鈕
- ✅ 國際化支持 (中英文)

**支持的指標參數**:
| 指標 | 參數 | 默認值 | 範圍 |
|------|------|--------|------|
| SMA | period | 20 | 1-200 |
| EMA | period | 20 | 1-200 |
| RSI | period | 14 | 2-50 |
| MACD | fast/slow/signal | 12/26/9 | 2-50/5-100/2-50 |
| BOLL | period/multiplier | 20/2.0 | 1-100/0.5-5.0 |
| KD | K/D period | 9/3 | 2-50/2-20 |
| ADX | period | 14 | 2-50 |
| CCI | period | 14 | 2-50 |
| Williams %R | period | 14 | 2-50 |

**顏色自訂**:
- ✅ SMA 顏色選擇
- ✅ EMA 顏色選擇
- ✅ RSI 顏色選擇

---

### ⏳ 待完成

#### 2. 整合到主窗口
**需要修改的文件**:
- `MainFrameWithDocking.java`
- `MenuBarFactory.java`

**任務**:
```java
// 在選單中新增「指標設定」項目
menu.view.indicator.settings=指標設定

// 在 MainFrameWithDocking 中新增方法
private void openIndicatorSettings() {
    IndicatorSettingsDialog dialog = new IndicatorSettingsDialog(this);
    dialog.setVisible(true);
    
    if (dialog.isConfirmed()) {
        // 更新 ChartDock 的指標參數
        chartDock.setIndicatorParameters(
            dialog.getSmaPeriod(),
            dialog.getEmaPeriod(),
            dialog.getRsiPeriod(),
            // ... 等等
        );
        
        // 更新指標顏色
        chartDock.setIndicatorColors(
            dialog.getSmaColor(),
            dialog.getEmaColor(),
            dialog.getRsiColor()
        );
        
        // 重新計算指標
        chartDock.recalculateIndicators();
    }
}
```

#### 3. 修改 `ChartDock.java`
**新增方法**:
```java
public class ChartDock {
    // 指標參數變數
    private int smaPeriod = 20;
    private int emaPeriod = 20;
    private int rsiPeriod = 14;
    // ...
    
    // Setters
    public void setIndicatorParameters(...) { }
    public void setIndicatorColors(...) { }
    public void recalculateIndicators() { }
    
    // 修改現有的 updateXXX 方法使用可變參數
    private void updateSMA() {
        List<Double> smaValues = indicatorService.getSMA(smaPeriod); // 使用變數
    }
}
```

#### 4. 參數持久化
**文件**: `indicator_settings.json`

```json
{
  "overlay": {
    "sma": {"period": 20, "color": "#007BFF"},
    "ema": {"period": 20, "color": "#FF8C00"},
    "boll": {"period": 20, "multiplier": 2.0}
  },
  "sub": {
    "rsi": {"period": 14, "color": "#FFA500"},
    "macd": {"fast": 12, "slow": 26, "signal": 9},
    "kd": {"K": 9, "D": 3},
    "adx": {"period": 14},
    "cci": {"period": 14},
    "wr": {"period": 14}
  }
}
```

**工具類**: `IndicatorSettingsManager.java`
```java
public class IndicatorSettingsManager {
    private static final String SETTINGS_FILE = "indicator_settings.json";
    
    public static void save(IndicatorSettings settings);
    public static IndicatorSettings load();
    public static void restoreDefaults();
}
```

---

## 📊 3. 歷史回測系統

### 架構設計

```
com.dreamhouse.trading.backtest/
├── data/
│   ├── DataManager.java          // 數據管理器
│   ├── CSVImporter.java           // CSV 匯入
│   ├── CSVExporter.java           // CSV 匯出
│   └── DataValidator.java         // 數據驗證
├── engine/
│   ├── BacktestEngine.java        // 回測引擎核心
│   ├── Strategy.java              // 策略介面
│   ├── Signal.java                // 訊號類型 (BUY/SELL/HOLD)
│   ├── Position.java              // 部位管理
│   ├── Order.java                 // 訂單
│   └── Trade.java                 // 成交記錄
├── strategy/
│   ├── BaseStrategy.java          // 策略基類
│   ├── MACrossoverStrategy.java   // 雙均線策略
│   ├── RSIStrategy.java           // RSI 策略
│   ├── MACDStrategy.java          // MACD 策略
│   └── BollingerStrategy.java     // 布林通道策略
├── performance/
│   ├── PerformanceCalculator.java // 績效計算
│   ├── PerformanceMetrics.java    // 績效指標
│   ├── EquityCurve.java           // 資金曲線
│   └── TradeStatistics.java       // 交易統計
└── ui/
    ├── BacktestPanel.java         // 回測面板
    ├── StrategyConfigDialog.java  // 策略配置對話框
    └── PerformanceReportDialog.java // 績效報告對話框
```

---

### 3.1 CSV 數據管理

#### DataManager.java
```java
public class DataManager {
    private List<Bar> historicalData;
    
    public void importFromCSV(File csvFile) throws IOException;
    public void exportToCSV(File csvFile, List<Bar> data) throws IOException;
    public boolean validate(List<Bar> data);
    public void fillMissingData(List<Bar> data);
    public List<Bar> getDataRange(LocalDateTime start, LocalDateTime end);
}
```

#### CSV 格式
```csv
Date,Time,Open,High,Low,Close,Volume
2025-01-01,09:00:00,100.00,101.50,99.50,101.00,1000000
2025-01-01,09:01:00,101.00,102.00,100.50,101.50,1200000
```

**驗證規則**:
- ✅ 日期格式正確
- ✅ OHLC 數值合理 (High >= Open/Close, Low <= Open/Close)
- ✅ 成交量 > 0
- ✅ 無重複時間戳
- ✅ 時間連續性

---

### 3.2 回測引擎

#### Strategy 介面
```java
public interface Strategy {
    Signal onBar(Bar bar, BarSeries series);
    void onTrade(Trade trade);
    String getName();
    Map<String, Object> getParameters();
}

public enum Signal {
    BUY,    // 買入訊號
    SELL,   // 賣出訊號
    HOLD    // 持有
}
```

#### BacktestEngine.java
```java
public class BacktestEngine {
    private Strategy strategy;
    private List<Bar> data;
    private double initialCapital;
    private double currentCapital;
    private List<Position> positions;
    private List<Trade> trades;
    
    public BacktestEngine(Strategy strategy, double initialCapital) {
        this.strategy = strategy;
        this.initialCapital = initialCapital;
        this.currentCapital = initialCapital;
    }
    
    public PerformanceMetrics run(List<Bar> data) {
        for (Bar bar : data) {
            Signal signal = strategy.onBar(bar, barSeries);
            
            switch (signal) {
                case BUY:
                    executeBuy(bar);
                    break;
                case SELL:
                    executeSell(bar);
                    break;
                case HOLD:
                    // 持有，不操作
                    break;
            }
        }
        
        return calculatePerformance();
    }
    
    private void executeBuy(Bar bar) { }
    private void executeSell(Bar bar) { }
    private PerformanceMetrics calculatePerformance() { }
}
```

---

### 3.3 內建策略

#### 1. 雙均線交叉策略
```java
public class MACrossoverStrategy implements Strategy {
    private int fastPeriod = 5;
    private int slowPeriod = 20;
    
    @Override
    public Signal onBar(Bar bar, BarSeries series) {
        if (series.getBarCount() < slowPeriod + 1) {
            return Signal.HOLD;
        }
        
        SMAIndicator fastSMA = new SMAIndicator(closePrice, fastPeriod);
        SMAIndicator slowSMA = new SMAIndicator(closePrice, slowPeriod);
        
        int lastIndex = series.getBarCount() - 1;
        int prevIndex = lastIndex - 1;
        
        double fastCurrent = fastSMA.getValue(lastIndex).doubleValue();
        double slowCurrent = slowSMA.getValue(lastIndex).doubleValue();
        double fastPrev = fastSMA.getValue(prevIndex).doubleValue();
        double slowPrev = slowSMA.getValue(prevIndex).doubleValue();
        
        // 金叉：快線向上穿越慢線
        if (fastPrev <= slowPrev && fastCurrent > slowCurrent) {
            return Signal.BUY;
        }
        
        // 死叉：快線向下穿越慢線
        if (fastPrev >= slowPrev && fastCurrent < slowCurrent) {
            return Signal.SELL;
        }
        
        return Signal.HOLD;
    }
}
```

#### 2. RSI 策略
```java
public class RSIStrategy implements Strategy {
    private int period = 14;
    private double oversoldLevel = 30;
    private double overboughtLevel = 70;
    
    @Override
    public Signal onBar(Bar bar, BarSeries series) {
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        double rsiValue = rsi.getValue(series.getBarCount() - 1).doubleValue();
        
        if (rsiValue < oversoldLevel) {
            return Signal.BUY;  // 超賣，買入
        }
        
        if (rsiValue > overboughtLevel) {
            return Signal.SELL; // 超買，賣出
        }
        
        return Signal.HOLD;
    }
}
```

#### 3. MACD 策略
```java
public class MACDStrategy implements Strategy {
    private int fastPeriod = 12;
    private int slowPeriod = 26;
    private int signalPeriod = 9;
    
    @Override
    public Signal onBar(Bar bar, BarSeries series) {
        MACDIndicator macd = new MACDIndicator(closePrice, fastPeriod, slowPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
        
        int lastIndex = series.getBarCount() - 1;
        int prevIndex = lastIndex - 1;
        
        double macdCurrent = macd.getValue(lastIndex).doubleValue();
        double signalCurrent = signal.getValue(lastIndex).doubleValue();
        double macdPrev = macd.getValue(prevIndex).doubleValue();
        double signalPrev = signal.getValue(prevIndex).doubleValue();
        
        // 金叉
        if (macdPrev <= signalPrev && macdCurrent > signalCurrent) {
            return Signal.BUY;
        }
        
        // 死叉
        if (macdPrev >= signalPrev && macdCurrent < signalCurrent) {
            return Signal.SELL;
        }
        
        return Signal.HOLD;
    }
}
```

#### 4. 布林通道策略
```java
public class BollingerStrategy implements Strategy {
    private int period = 20;
    private double multiplier = 2.0;
    
    @Override
    public Signal onBar(Bar bar, BarSeries series) {
        BollingerBandsMiddleIndicator middle = ...;
        BollingerBandsUpperIndicator upper = ...;
        BollingerBandsLowerIndicator lower = ...;
        
        double close = bar.getClosePrice().doubleValue();
        double upperBand = upper.getValue(series.getBarCount() - 1).doubleValue();
        double lowerBand = lower.getValue(series.getBarCount() - 1).doubleValue();
        
        // 突破下軌，買入
        if (close < lowerBand) {
            return Signal.BUY;
        }
        
        // 突破上軌，賣出
        if (close > upperBand) {
            return Signal.SELL;
        }
        
        return Signal.HOLD;
    }
}
```

---

### 3.4 績效統計

#### PerformanceMetrics.java
```java
public class PerformanceMetrics {
    // 基本指標
    private double totalReturn;          // 總收益率
    private double annualizedReturn;     // 年化報酬率
    private double maxDrawdown;          // 最大回撤
    private double sharpeRatio;          // 夏普比率
    private double sortinoRatio;         // 索提諾比率
    
    // 交易統計
    private int totalTrades;             // 總交易次數
    private int winningTrades;           // 獲利交易次數
    private int losingTrades;            // 虧損交易次數
    private double winRate;              // 勝率
    private double averageWin;           // 平均獲利
    private double averageLoss;          // 平均虧損
    private double profitFactor;         // 獲利因子 (總獲利/總虧損)
    
    // 時間統計
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Duration tradingDuration;
    private double avgHoldingPeriod;     // 平均持倉時間
    
    // 資金曲線
    private List<EquityPoint> equityCurve;
    
    // Getters and Setters...
}

public class EquityPoint {
    private LocalDateTime timestamp;
    private double equity;              // 資金淨值
    private double drawdown;            // 回撤比例
}
```

#### 計算公式

**總收益率**:
```
Total Return = (Final Capital - Initial Capital) / Initial Capital * 100%
```

**年化報酬率**:
```
Annualized Return = (1 + Total Return)^(365/Days) - 1
```

**最大回撤 (MDD)**:
```
MDD = max((Peak - Valley) / Peak) * 100%
```

**夏普比率**:
```
Sharpe Ratio = (Average Return - Risk Free Rate) / Standard Deviation
```

**勝率**:
```
Win Rate = Winning Trades / Total Trades * 100%
```

**獲利因子**:
```
Profit Factor = Total Profit / |Total Loss|
```

---

### UI 設計

#### BacktestPanel.java
```
┌────────────────────────────────────────────────────┐
│ 回測設定                                            │
├────────────────────────────────────────────────────┤
│ 數據來源: [選擇 CSV 文件]  [匯入]                   │
│ 數據範圍: [2025-01-01] ~ [2025-12-31]              │
│ 初始資金: [100000]                                  │
│ 交易成本: [0.1%]                                    │
│                                                     │
│ 策略選擇: [雙均線策略 ▼]  [設定參數]                │
│                                                     │
│ [開始回測]  [停止]  [匯出報告]                      │
├────────────────────────────────────────────────────┤
│ 回測進度: ███████████░░░░░░░░░ 60%                │
├────────────────────────────────────────────────────┤
│ 績效摘要                                            │
│ 總收益率: +25.5%    │ 最大回撤: -8.2%               │
│ 勝率: 65%           │ 交易次數: 45                  │
│ 獲利因子: 2.1       │ 夏普比率: 1.8                 │
├────────────────────────────────────────────────────┤
│ [資金曲線圖]                                        │
│  ┌────────────────────────────────────────┐       │
│  │  120k ─                          ╱─    │       │
│  │  110k ─              ╱─────╱────╱      │       │
│  │  100k ─────╱────╱───╱                  │       │
│  │   90k ─                                 │       │
│  └────────────────────────────────────────┘       │
│    Jan  Feb  Mar  Apr  May  Jun  Jul  Aug        │
├────────────────────────────────────────────────────┤
│ 交易明細                                            │
│ ┌──────┬──────┬──────┬──────┬──────┬──────┐     │
│ │ 時間  │ 類型  │ 價格  │ 數量  │ 損益  │ 累計  │     │
│ ├──────┼──────┼──────┼──────┼──────┼──────┤     │
│ │ 01-05│ BUY  │ 100.5│ 100  │ -    │ -    │     │
│ │ 01-10│ SELL │ 105.0│ 100  │ +450│+450  │     │
│ │ ...  │ ...  │ ... │ ...  │ ... │ ... │     │
│ └──────┴──────┴──────┴──────┴──────┴──────┘     │
└────────────────────────────────────────────────────┘
```

---

## 📋 實作優先順序

### 第一階段 (當前)
1. ✅ 完成指標設定對話框 GUI
2. ⏳ 整合到主窗口
3. ⏳ 參數持久化

### 第二階段
4. CSV 數據管理
5. 數據驗證與清理

### 第三階段
6. 回測引擎核心
7. 策略介面定義
8. 部位管理

### 第四階段
9. 實現 4 個內建策略
10. 策略參數配置

### 第五階段
11. 績效計算
12. 資金曲線圖
13. 績效報告

### 第六階段
14. 回測 UI 面板
15. 整合測試
16. 文檔完善

---

## 🎯 驗收標準

### 指標自訂介面
- [x] 對話框可正常開啟
- [ ] 參數修改生效
- [ ] 顏色修改生效
- [ ] 參數持久化
- [ ] 重設功能正常

### 歷史回測系統
- [ ] 可匯入標準 CSV 格式
- [ ] 數據驗證正確
- [ ] 4 個策略運行正確
- [ ] 績效計算準確
- [ ] 資金曲線圖正確顯示
- [ ] 可匯出回測報告

---

## 📝 開發日誌

**2025-10-24**:
- ✅ 創建 `IndicatorSettingsDialog.java`
- ✅ 添加國際化支持
- ✅ 設計完整的回測系統架構

**待繼續**:
- 整合指標設定對話框到主窗口
- 開始實作 CSV 數據管理
- 實現回測引擎核心

---

*本文檔持續更新中...*

