# DreamHouseTrading

DreamHouseTrading 是一套 Java 17 / Swing 桌面交易分析平台，主要用於台股盤中監控、策略雷達、SQL 分 K 回測與模擬交易紀錄分析。系統邊界是分析、掃描、決策、回測與模擬執行，不做真實券商下單。

## 目前定位

- 盤中行情來源：`MarketDataCollectorFeed` 讀取本機 MySQL `market_data`。
- 低頻資料來源：FinMind 用於盤前股票池、盤後補資料、產業鏈、分點、新聞與手動 API 查詢。
- 備援資料來源：Yahoo 可保留為非盤中策略用途。
- 盤中雷達原則：DreamHouseTrading 不直接呼叫 FinMind 即時行情 API。
- 交易方向：v1 維持 long-only。
- 當沖語意：自動監控 / 今日機會雷達強制使用 `DAY_TRADE`，模擬開單固定 1 張 `1000` 股。

## 主要功能

- 多資料源管理：MarketDataCollectorFeed、FinMind、Yahoo。
- SQL 日內 K 線載入：支援指定日期與觀察清單批次回測。
- 今日機會雷達：輸出進場分數、阻擋原因、VWAP、ATR、量能延續、內部市場狀態等欄位。
- SQL 雷達開盤重播：非開盤時間可用指定日期 SQL K 線按時間逐步模擬盤中雷達掃描，更新機會雷達、主圖 K 線、執行狀態重播紀錄與已發生交易標記。
- 自動監控風控：早盤禁開倉、13:25 強制平倉、最大持倉、連敗與日損熔斷、停損冷卻。
- 三層停損停利：結構停損、盤勢感知停利、持倉中理由失效與移動停損管理。
- 回測報告：N+1 open 成交模型、手續費 / 當沖稅 / 滑價拆分、具體未進場原因、交易理由、技術指標、雷達加分明細與條件貢獻統計。
- 監控模板：內建 A 組穩健 `EMA8/34 + 跨日暖機`、B 組放寬 `EMA8/21`、C 組 `SMA8/21` 測試，可直接切換做 SQL 回測比較；使用者另存模板可在監控設定中刪除。
- 盤後股票池：依日線、量能、型態、當沖活躍度與分點籌碼產生隔日當沖候選。
- UI Dock：觀察清單、主圖、機會雷達、執行狀態、FinMind API、產業分布、交易紀錄分析。

## Windows 使用方式

### 下載方式

1. 若 GitHub Releases 已提供壓縮檔，下載最新 `DreamHouseTrading` release 並解壓縮。
2. 若尚未提供 release，直接下載原始碼：

```powershell
git clone https://github.com/qwqqq51103/DreamHouseTrading.git
cd DreamHouseTrading
```

### Windows 需求

- Windows 10 / 11
- JDK 17
- Maven 3.9+
- MySQL 8+
- MarketDataCollector 專案需先啟動並寫入 `market_data` 資料庫

### 啟動

```powershell
mvn -q -DskipTests compile
mvn exec:java
```

預設 SQL 連線：

```text
jdbc:mysql://localhost:3306/market_data?useSSL=false&serverTimezone=Asia/Taipei&characterEncoding=UTF-8
user=root
password=
```

## macOS 使用方式

### 下載方式

```bash
git clone https://github.com/qwqqq51103/DreamHouseTrading.git
cd DreamHouseTrading
```

### macOS 需求

- macOS 13+
- JDK 17
- Maven 3.9+
- MySQL 8+
- 可連到 MarketDataCollector 寫入的 `market_data` MySQL

可用 Homebrew 安裝必要工具：

```bash
brew install openjdk@17 maven mysql
```

若 shell 找不到 Java 17，請設定 `JAVA_HOME`：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### 啟動

```bash
mvn -q -DskipTests compile
mvn exec:java
```

macOS 上若 MarketDataCollector 跑在另一台 Windows 主機，請把 JDBC URL 改成該主機的 IP，並確認 MySQL 防火牆與帳號權限允許連線。

## 驗證命令

編譯：

```bash
mvn -q -DskipTests compile
```

測試：

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

注意：這台 Windows 主機上的一般 `mvn test` 可能被 JaCoCo coverage 檔權限卡住，因此測試請優先使用 `-Djacoco.skip=true`。

## 資料與 API 邊界

- 盤中股票 tick / K 線：只允許 MarketDataCollector 呼叫即時 API 並寫入 SQL。
- DreamHouseTrading 盤中雷達：只讀 SQL，不 fallback 打 FinMind 即時行情。
- FinMind `TaiwanStockKBar`：盤後分 K，15:50 前不得當作盤中即時 K 線。
- 工具列「載入範圍分K到SQL」：可用 `TaiwanStockKBar` 補指定日期範圍；每個股票日期會先檢查 SQL M1 分 K 根數與開收盤覆蓋，資料完整才略過 API。
- SQL 雷達回測若套用 A 組穩健模板，會從 SQL 載入前期 K 線暖機 EMA34，但只在指定交易日產生交易與績效；監控設定也可手動開關跨日暖機並調整暖機 K 線根數。
- A/B/C 三組都預設阻擋「均線單因子進場」：A 組要求突破後一根 K 確認，B 組用 EMA8/21 放寬反應速度，C 組用 SMA8/21 與 1.45 量能倍數測試均線類型差異。模板套用、自訂模板儲存與回測報告會保留這些設定。
- SQL 雷達回測會套用監控設定中的早盤禁開倉、尾盤禁止新倉、停損冷卻、日損 / 連敗熔斷、每日最多交易、開單間隔與同一根 5 分 K 只允許一筆，讓回測節奏更接近盤中自動監控；成交價仍採 N+1 open，盤中模擬則用當下最新價。
- SQL 雷達 replay 會保留監控設定的 K 棒數與跨日暖機資料，並以 rolling feed 控制每根 K 當下可見資料；不會把回測累積根數寫回監控 request，避免回測後段的 EMA / Volume Sustain 和盤中雷達不同。
- 工具列與工具選單的「SQL 雷達開盤重播」會讀取指定日期 SQL 資料，以模擬時間推進機會雷達、全市場重播成交紀錄與目前圖表商品的交易標記；此模式不呼叫 FinMind 即時 API，也不寫入正式 paper trade CSV。
- SQL 雷達 replay 每次啟動會輸出診斷 CSV 到 `logs/radar-replay`，記錄每個模擬時間點的掃描檔數、OPEN_LONG 數、阻擋數、已發生交易與前幾名候選，方便比對批次回測與盤中重播差異。
- SQL 雷達批次回測會在合併多檔結果時再次套用全局開單節奏與最大持倉限制，避免逐檔獨立回測高估同時可開倉筆數。
- FinMind `TaiwanStockIndustryChain`：低頻產業鏈資料，匯入 SQL 後快取使用。
- FinMind `TaiwanStockTradingDailyReport`：盤後分點資料，適合用於隔日股票池，不適合盤中即時開倉。

## 相關文件

- [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md)：目前架構、進度、驗證與待辦。
- [AGENTS.md](./AGENTS.md)：AI 協作規範與開發邊界。
