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
- 雷達最低進場分數：做多門檻通過後的第二道分數門檻，低於此值仍會阻擋 OPEN_LONG。
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
- MarketDataCollector 資料過期時會跳過該股票掃描，並把 stale warn 診斷輸出到 `logs/market-data-warnings/collector_stale_warnings_yyyyMMdd.csv`。
- FinMind `TaiwanStockKBar`：盤後分 K，15:50 前不得當作盤中即時 K 線。
- 工具列「載入範圍分K到SQL」：可用 `TaiwanStockKBar` 補指定日期範圍；每個股票日期會先檢查 SQL M1 分 K 根數、開收盤覆蓋與 D1 日線是否存在，資料完整才略過 API。匯入會寫入 `candlesticks` 的 `M1/M5/M15/M30/H1/D1`；D1 優先由 `TaiwanStockPrice` 寫入，若日線資料為空才由當日分 K 聚合。
- SQL 雷達回測若套用 A 組穩健模板，會從 SQL 載入前期 K 線暖機 EMA34，但只在指定交易日產生交易與績效；監控設定也可手動開關跨日暖機並調整暖機 K 線根數。
- 盤中自動監控也會共用監控設定的跨日暖機資料做 scanner 指標初始化；自動監控固定使用今日 SQL 資料，不受工具列「載入日期」影響。
- A/B/C 三組都預設阻擋「均線單因子進場」：A 組要求突破後一根 K 確認，B 組用 EMA8/21 放寬反應速度，C 組用 SMA8/21 與 1.45 量能倍數測試均線類型差異。模板套用、自訂模板儲存與回測報告會保留這些設定。
- SQL 雷達回測會套用監控設定中的早盤禁開倉、尾盤禁止新倉、停損冷卻、日損 / 連敗熔斷、每日最多交易、開單間隔與同一根 5 分 K 只允許一筆，讓回測節奏更接近盤中自動監控；成交價仍採 N+1 open，盤中模擬則用當下最新價。
- SQL 雷達 replay 會保留監控設定的 K 棒數與跨日暖機資料，並以 rolling feed 控制每根 K 當下可見資料；掃描時固定走 replay feed 取 K 線，不用 internal market context bars 覆蓋 scanner bars，避免與盤中自動監控不同。
- SQL 雷達 replay 掃描會帶入模擬時間；若有 SQL ticks，replay feed 會用模擬時間以前的 tick 即時聚合 partial K 棒，避免 09:30:20 看到 09:30~09:34:59 的未來資料。若沒有 ticks 而只能用 candlesticks，才只使用已收完的 K 線。
- 工具列與工具選單的「SQL 雷達開盤重播」會讀取指定日期 SQL 資料，以模擬時間推進機會雷達、全市場重播成交紀錄與目前圖表商品的交易標記；此模式不呼叫 FinMind 即時 API，也不寫入正式 paper trade CSV。
- SQL 雷達 replay 每次啟動會輸出 UTF-8 BOM 診斷 CSV 到 `logs/radar-replay`，記錄每個模擬時間點的掃描檔數、OPEN_LONG 數、阻擋數、已發生交易與前幾名候選，方便比對批次回測與盤中重播差異，也可直接用 Excel 開啟中文阻擋原因。
- SQL 雷達單檔、批次 / 日期範圍與開盤重播都會輸出跨日暖機診斷；報告與 `_symbols.csv` 可看到 `scanner_bar_source`、`scanner_bars`、`visible_session_bars`、`last_visible_session_bar_time`、`warmup_enabled`、`warmup_requested_bars`、`warmup_loaded_bars`、`warmup_first`、`warmup_last`，用來確認 replay 當下實際可見 K 線與暖機資料。
- 重播摘要 CSV 的 `trade_events` 由 SQL 開盤重播當下的 scanner 結果即時計算，會套用同一套最大持倉、每日最多交易、開單間隔與同一根 5 分 K 限制；若要查每檔當下為何開單或阻擋，請看 `_symbols.csv` 的逐檔 `action`、`block_reason` 與 `long_bonus`。
- SQL 雷達開盤重播可設定「掃描間隔（秒）」與「掃描秒偏移」；預設會從同日 `orders_yyyyMMdd.csv` 的 auto-monitor 紀錄推估實盤掃描秒偏移，例如實盤都在 `:20/:50` 開單時，重播會用 20 秒偏移。重播開平倉價格優先使用 SQL ticks 在模擬時間以前的最新價，讓 replay 更接近盤中自動監控。
- SQL 雷達開盤重播比對實盤時，請固定使用 `TICKS_AGGREGATED`；此模式會從 full-day ticks 依 replay time 重新切片聚合，不會直接使用盤後完整 M5 bar 作為當下 scanner 輸入。
- SQL 雷達批次回測會在合併多檔結果時再次套用全局開單節奏與最大持倉限制，避免逐檔獨立回測高估同時可開倉筆數。
- `佈局 > 儲存` 會把目前停靠面板版面寫入專案根目錄的 `config/ui-layout.xml`，下次啟動會在主視窗開啟後自動套用；若完整 XML 還原失敗，系統仍會依儲存檔內的面板 ID 強制只保留已儲存的可見視窗。`佈局 > 重設` 會還原預設版面並刪除該本機檔案。若沒有套用，診斷會寫入 `logs/ui-layout/ui_layout.log`。
- FinMind `TaiwanStockIndustryChain`：低頻產業鏈資料，匯入 SQL 後快取使用。
- FinMind `TaiwanStockTradingDailyReport`：盤後分點資料，適合用於隔日股票池，不適合盤中即時開倉。

## 相關文件

- [PROJECT_DOCUMENTATION.md](./PROJECT_DOCUMENTATION.md)：目前架構、進度、驗證與待辦。
- [AGENTS.md](./AGENTS.md)：AI 協作規範與開發邊界。

## 2026-05-27 SQL 雷達重播更新

- 工具列 / 工具選單的「SQL 雷達開盤重播」新增資料來源模式：
  - `TICKS_AGGREGATED`：固定使用 SQL ticks 聚合 K 線，建議用來比對盤中實盤雷達。
  - `CANDLES_ONLY`：固定使用 SQL candlesticks，適合檢查盤後補入分 K 後的結果。
  - `AUTO`：candlesticks 完整時優先使用 candlesticks，否則使用 ticks 聚合。
- 重播 CSV 會輸出 UTF-8 with BOM，並記錄策略名稱、完整設定摘要、資料來源模式、每檔實際載入來源、跨日暖機實際載入根數、OPEN_LONG 候選、阻擋候選與雷達加分明細。
- 若同一日期同一配置重播結果不同，先檢查 CSV 的 `source_mode` 與 `loaded_data_sources`；盤後補入 candlesticks 後，AUTO 模式可能與盤中 ticks 聚合模式不同。
- 重播會同時輸出 `radar_replay_*.csv` 摘要檔與 `radar_replay_*_symbols.csv` 每檔明細檔。若要檢查股票代碼、分數、阻擋理由與加分明細，請優先看 `_symbols.csv`，避免多檔資料擠在同一格造成 Excel 閱讀錯位。
- 若 `_symbols.csv` 的 `actual_source` 顯示 `TICKS_AGGREGATED_INCOMPLETE`，代表 SQL ticks 聚合出的 M5 根數不足，例如 Collector 09:01 才有第一筆 tick 而缺少 09:00 bucket；這會影響 EMA、VWAP、VolumeBreakout 與回測/重播結果。
