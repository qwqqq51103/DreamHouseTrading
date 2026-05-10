package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionEngine;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBar;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * 信號監控服務
 * 定期掃描觀察列表中的商品，偵測交易信號
 */
public class SignalMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(SignalMonitorService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 監控配置
    private final SignalMonitorConfig config;

    // 數據源
    private final MarketDataFeed dataFeed;

    // 決策引擎
    private final DecisionEngine decisionEngine;

    // 市場掃描服務
    private final MarketScannerService scannerService;
    private final DecisionConfig decisionConfig;

    // 監控的商品列表
    private final Set<String> monitoredSymbols = new ConcurrentHashMap<String, Boolean>().keySet(true);
    private final Map<String, MarketDataListener> monitorDataListeners = new ConcurrentHashMap<>();

    // 定時器
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> monitorTask;

    // 信號回調（symbol, signal）
    private BiConsumer<String, DecisionResult> onSignalDetected;

    // 狀態回調（message）
    private java.util.function.Consumer<String> onStatusUpdate;

    // 掃描結果回調（供 UI 顯示候選分數）
    private java.util.function.Consumer<MarketScanResult> onScanResult;
    private java.util.function.Consumer<List<MarketScanResult>> onScanResults;

    // 運行狀態
    private volatile boolean running = false;

    // 上次檢測時間記錄（避免重複提醒）
    private final Map<String, LocalDateTime> lastSignalTime = new ConcurrentHashMap<>();

    // 子策略列表（用於產生信號）
    private final List<com.dreamhouse.trading.core.decision.signal.IStrategySignal> strategySignals;

    /**
     * 建構子（使用預設策略配置）
     */
    public SignalMonitorService(MarketDataFeed dataFeed, SignalMonitorConfig config) {
        this(dataFeed, config, null);
    }

    /**
     * 建構子（自訂策略配置）
     * @param dataFeed 數據源
     * @param config 監控配置
     * @param decisionConfig 決策配置（如果為 null，使用多週期決策策略）
     */
    public SignalMonitorService(MarketDataFeed dataFeed, SignalMonitorConfig config, DecisionConfig decisionConfig) {
        this.dataFeed = dataFeed;
        this.config = config;
        this.strategySignals = new java.util.ArrayList<>();

        // 創建模擬組合（僅用於決策引擎，不實際交易）
        Portfolio mockPortfolio = new Portfolio(100000.0);  // 10萬初始資金

        // 使用預設配置（與回測預設選項一致）
        if (decisionConfig == null) {
            decisionConfig = DecisionConfig.createDefault();
        }

        // 創建 DecisionEngine
        this.decisionConfig = decisionConfig;
        this.decisionEngine = new DecisionEngine(decisionConfig, mockPortfolio);
        this.scannerService = new MarketScannerService(dataFeed);

        // 添加 RSI 子策略（與回測系統一致）
        com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy rsiStrategy =
            new com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy();
        rsiStrategy.setWeight(1.0);
        strategySignals.add(rsiStrategy);

        logger.info("信號監控服務已創建，使用多週期決策策略（包含 RSI 子策略）");
    }

    /**
     * 開始監控
     */
    public synchronized void start(List<String> symbols) {
        if (running) {
            logger.warn("監控服務已在運行中");
            return;
        }

        monitoredSymbols.clear();
        monitoredSymbols.addAll(symbols);
        subscribeMonitoredSymbols();

        executor = Executors.newScheduledThreadPool(1);

        monitorTask = executor.scheduleAtFixedRate(
            this::scanSymbols,
            0,  // 立即開始
            config.getScanIntervalSeconds(),
            TimeUnit.SECONDS
        );

        running = true;

        String message = String.format("✅ 監控已啟動：%d 檔商品，每 %d 秒掃描",
            monitoredSymbols.size(),
            config.getScanIntervalSeconds());

        logger.info(message);
        notifyStatus(message);
    }

    /**
     * 停止監控
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        if (monitorTask != null) {
            monitorTask.cancel(false);
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        running = false;
        unsubscribeMonitoredSymbols();
        monitoredSymbols.clear();
        lastSignalTime.clear();

        String message = "⏹ 監控已停止";
        logger.info(message);
        notifyStatus(message);
    }

    /**
     * 掃描所有監控的商品
     */
    private void scanSymbols() {
        if (monitoredSymbols.isEmpty()) {
            logger.debug("觀察列表為空，跳過掃描");
            return;
        }

        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        logger.info("⏰ [{}] 開始掃描 {} 檔商品...", timestamp, monitoredSymbols.size());

        int signalCount = 0;
        List<MarketScanResult> scanResults = new ArrayList<>();

        for (String symbol : monitoredSymbols) {
            try {
                MarketScanResult scanResult = analyzeSymbol(symbol);
                if (isBatchScanMode()) {
                    if (scanResult != null) {
                        scanResults.add(scanResult);
                    }
                    continue;
                }
                notifyScanResult(scanResult);

                DecisionResult result = scanResult != null ? scanResult.getDecisionResult() : null;

                if (scanResult != null && scanResult.hasTradeSignal()) {
                    // 檢查是否為新信號（避免重複提醒）
                    if (isNewSignal(symbol)) {
                        signalCount++;
                        lastSignalTime.put(symbol, LocalDateTime.now());

                        logger.info("🔔 發現信號：{} - {}", symbol, result.getAction());
                        notifySignal(symbol, result);
                    }
                }
            } catch (Exception e) {
                logger.error("分析 {} 時發生錯誤: {}", symbol, e.getMessage(), e);
            }
        }

        String statusMsg = String.format("✅ [%s] 掃描完成：%d 檔商品，發現 %d 個信號",
            timestamp, monitoredSymbols.size(), signalCount);

        if (isBatchScanMode()) {
            notifyScanResults(scanResults);
            for (MarketScanResult scanResult : scanResults) {
                DecisionResult result = scanResult.getDecisionResult();
                if (scanResult.hasTradeSignal() && isNewSignal(scanResult.getSymbol())) {
                    signalCount++;
                    lastSignalTime.put(scanResult.getSymbol(), LocalDateTime.now());

                    logger.info("Signal detected: {} - {}", scanResult.getSymbol(), result.getAction());
                    notifySignal(scanResult.getSymbol(), result);
                }
            }
            statusMsg = String.format("[%s] Batch scan completed: %d symbols, %d signals",
                timestamp, monitoredSymbols.size(), signalCount);
        }

        logger.info(statusMsg);
        notifyStatus(statusMsg);
    }

    /**
     * 分析單一商品
     */
    private MarketScanResult analyzeSymbol(String symbol) {
        logger.debug("分析商品: {}", symbol);

        try {
            MarketScannerService.ScanRequest request = MarketScannerService.ScanRequest.createDefault()
                .timeframe(config.getTimeframe())
                .barCount(config.getBarCount())
                .tradeMode(config.getTradeMode())
                .decisionConfig(decisionConfig);
            MarketScanResult scanResult = scannerService.scan(symbol, request);
            DecisionResult result = scanResult.getDecisionResult();

            if (result != null && result.shouldTrade()) {
                logger.info("商品 {} 決策: {} - {}", symbol, result.getAction(), result.getReason());
            }

            return scanResult;

        } catch (Exception e) {
            logger.error("分析商品 {} 時發生錯誤: {}", symbol, e.getMessage(), e);
            return MarketScanResult.builder(symbol)
                .tradeMode(config.getTradeMode())
                .decisionResult(new DecisionResult.Builder()
                    .action(DecisionResult.Action.NO_ACTION)
                    .source(DecisionResult.Source.TECHNICAL)
                    .reason("掃描失敗：" + e.getMessage())
                    .confidence(0.0)
                    .build())
                .score(0.0)
                .build();
        }
    }

    /**
     * 將自定義 Bar 列表轉換為 ta4j BarSeries
     */
    private void subscribeMonitoredSymbols() {
        for (String symbol : monitoredSymbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            monitorDataListeners.computeIfAbsent(symbol, key -> {
                MarketDataListener listener = new MarketDataListener() {
                };
                try {
                    dataFeed.subscribe(key, listener);
                } catch (Exception e) {
                    logger.warn("Unable to subscribe monitored symbol {} for realtime simulation: {}", key, e.getMessage());
                }
                return listener;
            });
        }
    }

    private void unsubscribeMonitoredSymbols() {
        for (Map.Entry<String, MarketDataListener> entry : monitorDataListeners.entrySet()) {
            try {
                dataFeed.unsubscribe(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.debug("Unable to unsubscribe monitored symbol {}: {}", entry.getKey(), e.getMessage());
            }
        }
        monitorDataListeners.clear();
    }

    private BarSeries convertToTa4jBarSeries(String symbol, List<Bar> bars) {
        BaseBarSeries series = new BaseBarSeries(symbol);

        ZonedDateTime lastAddedTime = null;

        for (Bar bar : bars) {
            try {
                org.ta4j.core.Bar ta4jBar = convertToTa4jBar(bar);

                // 檢查時間是否遞增（避免重複時間戳）
                ZonedDateTime currentTime = ta4jBar.getEndTime();
                if (lastAddedTime != null && !currentTime.isAfter(lastAddedTime)) {
                    logger.debug("跳過時間重複或倒退的 K 線: {} (上一根: {})",
                        currentTime, lastAddedTime);
                    continue;
                }

                series.addBar(ta4jBar);
                lastAddedTime = currentTime;
            } catch (Exception e) {
                logger.debug("跳過無效 K 線: {}", e.getMessage());
            }
        }

        return series;
    }

    /**
     * 將自定義 Bar 轉換為 ta4j Bar
     */
    private org.ta4j.core.Bar convertToTa4jBar(Bar bar) {
        // 獲取週期時長（根據配置）
        Duration duration = Duration.ofMinutes(config.getTimeframe().getMinutes());

        // ⭐ 計算結束時間（ta4j 的 BaseBar 構造函數需要 endTime，不是開始時間）
        // 例如：09:00 的 M5 K 線，結束時間是 09:05
        ZonedDateTime endTime = bar.getTimestamp()
            .plus(duration)
            .atZone(ZoneId.systemDefault());

        // 創建 ta4j BaseBar
        return new BaseBar(
            duration,
            endTime,  // 使用結束時間
            BigDecimal.valueOf(bar.getOpen()),
            BigDecimal.valueOf(bar.getHigh()),
            BigDecimal.valueOf(bar.getLow()),
            BigDecimal.valueOf(bar.getClose()),
            BigDecimal.valueOf(bar.getVolume())
        );
    }

    /**
     * 檢查K線是否已完成
     *
     * @param bar K線數據
     * @param timeframe 時間週期
     * @return true 如果K線已完成，false 如果K線還在進行中
     */
    private boolean isBarComplete(Bar bar, Timeframe timeframe) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate barDate = bar.getTimestamp().toLocalDate();

        // ⭐ 如果K線日期不是今天，視為歷史數據，總是完成
        // 這允許使用歷史數據測試監控系統
        if (!barDate.equals(today)) {
            logger.debug("歷史K線（{}），視為已完成", bar.getTimestamp());
            return true;
        }

        // 今日K線：檢查是否完成
        LocalDateTime barEnd = bar.getTimestamp().plus(timeframe.getMinutes(), ChronoUnit.MINUTES);
        boolean isComplete = !now.isBefore(barEnd);

        if (!isComplete) {
            long secondsRemaining = ChronoUnit.SECONDS.between(now, barEnd);
            logger.debug("K線未完成，剩餘 {} 秒 (K線時間: {}, 結束時間: {}, 當前時間: {})",
                secondsRemaining, bar.getTimestamp(), barEnd, now);
        }

        return isComplete;
    }

    /**
     * 檢查是否為新信號（避免重複提醒）
     */
    private boolean isNewSignal(String symbol) {
        LocalDateTime lastTime = lastSignalTime.get(symbol);
        if (lastTime == null) {
            return true;
        }

        // 如果距離上次信號超過設定的最小間隔，視為新信號
        long minutesSinceLastSignal = java.time.Duration.between(lastTime, LocalDateTime.now()).toMinutes();
        return minutesSinceLastSignal >= config.getMinSignalIntervalMinutes();
    }

    /**
     * 通知信號
     */
    private void notifySignal(String symbol, DecisionResult result) {
        if (onSignalDetected != null) {
            try {
                onSignalDetected.accept(symbol, result);
            } catch (Exception e) {
                logger.error("通知信號時發生錯誤", e);
            }
        }
    }

    /**
     * 通知狀態更新
     */
    private void notifyStatus(String message) {
        if (onStatusUpdate != null) {
            try {
                onStatusUpdate.accept(message);
            } catch (Exception e) {
                logger.error("通知狀態時發生錯誤", e);
            }
        }
    }

    private void notifyScanResult(MarketScanResult result) {
        if (onScanResult != null && result != null) {
            try {
                onScanResult.accept(result);
            } catch (Exception e) {
                logger.error("通知掃描結果時發生錯誤", e);
            }
        }
    }

    /**
     * 設置信號回調
     */
    private void notifyScanResults(List<MarketScanResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        if (onScanResults != null) {
            try {
                onScanResults.accept(List.copyOf(results));
                return;
            } catch (Exception e) {
                logger.error("Failed to notify batch scan results", e);
            }
        }
        for (MarketScanResult result : results) {
            notifyScanResult(result);
        }
    }

    private boolean isBatchScanMode() {
        return true;
    }

    public void setOnSignalDetected(BiConsumer<String, DecisionResult> callback) {
        this.onSignalDetected = callback;
    }

    /**
     * 設置狀態回調
     */
    public void setOnStatusUpdate(java.util.function.Consumer<String> callback) {
        this.onStatusUpdate = callback;
    }

    /**
     * 設置掃描結果回調
     */
    public void setOnScanResult(java.util.function.Consumer<MarketScanResult> callback) {
        this.onScanResult = callback;
    }

    public void setOnScanResults(java.util.function.Consumer<List<MarketScanResult>> callback) {
        this.onScanResults = callback;
    }

    /**
     * 是否正在運行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 獲取監控的商品數量
     */
    public int getMonitoredSymbolCount() {
        return monitoredSymbols.size();
    }

    /**
     * 獲取配置
     */
    public SignalMonitorConfig getConfig() {
        return config;
    }
}
