package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.*;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.DepthLevel;
import com.dreamhouse.trading.core.model.NewsItem;
import com.dreamhouse.trading.core.model.Tick;
import com.dreamhouse.trading.ui.chart.DrawingManager;
import com.dreamhouse.trading.ui.dialog.CsvExportDialog;
import com.dreamhouse.trading.ui.dialog.CsvImportDialog;
import com.dreamhouse.trading.ui.dialog.IndicatorSettingsDialog;
import com.dreamhouse.trading.ui.dialog.BacktestConfigDialog;
import com.dreamhouse.trading.ui.dialog.BacktestProgressDialog;
import com.dreamhouse.trading.ui.dialog.BacktestResultDialog;
import com.dreamhouse.trading.ui.dialog.DataSourceConfigDialog;
import com.dreamhouse.trading.core.backtest.*;
import com.dreamhouse.trading.core.execution.ExecutionEngine;
import com.dreamhouse.trading.core.execution.ExecutionMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.monitor.SignalMonitorService;
import com.dreamhouse.trading.core.monitor.SignalMonitorConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.ui.dock.*;
import com.dreamhouse.trading.util.I18n;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.app.RootDockingPanel;
import io.github.andrewauclair.moderndocking.DockingRegion;
import io.github.andrewauclair.moderndocking.Dockable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainFrameWithDocking extends JFrame {
    private final DataSourceManager dataSourceManager;
    private MarketDataFeed dataFeed;
    private final StatusBar statusBar;
    private final RootDockingPanel dockingPanel;

    private ChartDock chartDock;
    private WatchlistPanel watchlistPanel;
    private OrderBookDock orderBookDock;
    private TimeSalesDock timeSalesDock;
    private NewsDock newsDock;
    private OpportunityRadarDock opportunityRadarDock;

    // 新增的分析與執行面板
    private MarketAnalysisDock marketAnalysisDock;
    private ModeRecommendationDock modeRecommendationDock;
    private ExecutionStatusDock executionStatusDock;

    // 執行引擎與投資組合
    private Portfolio portfolio;
    private ExecutionEngine executionEngine;

    // 自動交易監控服務
    private SignalMonitorService signalMonitor;
    private volatile boolean autoTradingEnabled = false;
    private DecisionConfig monitorDecisionConfig;
    private final Map<String, AutoExitRule> autoExitRules = new ConcurrentHashMap<>();

    private String currentSymbol = "";
    private Timeframe currentTimeframe = Timeframe.M1;
    private int customBarCount = 100;  // 用戶自定義的K線數量，預設100根
    private double lastPrice = 0;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();

    // 保存當前的市場數據監聽器引用，用於取消訂閱
    private MarketDataListener currentMarketDataListener;
    private final Map<String, MarketDataListener> watchlistMarketDataListeners = new ConcurrentHashMap<>();
    private final Map<String, Double> watchlistLastPrices = new ConcurrentHashMap<>();

    public MainFrameWithDocking() {
        setTitle(I18n.get("app.title"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

        // 添加窗口關閉監聽器，確保資源正確釋放
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        // 建立資料源管理器
        dataSourceManager = new DataSourceManager();
        dataFeed = dataSourceManager.getCurrentDataSource();
        
        // 建立 MenuBar
        setJMenuBar(createMenuBar());
        
        // 建立 ToolBar
        JToolBar toolBar = createToolBar();
        
        // 建立 StatusBar
        statusBar = new StatusBar();
        statusBar.setSymbol(currentSymbol);
        statusBar.setTimeframe(currentTimeframe.getLabel());
        statusBar.setConnectionStatus(true);
        
        // 初始化 Modern Docking（必須先初始化再創建 RootDockingPanel）
        Docking.initialize(this);
        dockingPanel = new RootDockingPanel(this);

        // 初始化執行引擎
        initializeExecutionEngine();

        // 建立所有 Dock 面板
        createDockPanels();
        initializeCurrentSymbolFromWatchlist();
        
        // 組裝視窗
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(dockingPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        
        // 訂閱市場資料
        subscribeMarketData();
        subscribeWatchlistMarketData();
        
        // 啟動資料源
        dataFeed.start();
        
        // 顯示啟動訊息
        statusBar.setText(I18n.get("status.loading.data"));
        
        // 延遲更新狀態訊息（等待歷史數據載入完成）
        Timer welcomeTimer = new Timer(3000, e -> {
            statusBar.setText(I18n.get("status.data.loaded"));
        });
        welcomeTimer.setRepeats(false);
        welcomeTimer.start();
        
        // FPS 計算
        Timer fpsTimer = new Timer(1000, e -> {
            long now = System.currentTimeMillis();
            double elapsed = (now - lastFpsTime) / 1000.0;
            int fps = (int) (frameCount / elapsed);
            statusBar.setFPS(fps);
            frameCount = 0;
            lastFpsTime = now;
        });
        fpsTimer.start();

        Timer autoExitTimer = new Timer(1000, e -> checkAllAutoExits());
        autoExitTimer.start();
        
        // 快捷鍵設定
        setupKeyBindings();
    }
    
    private void createDockPanels() {
        // 主圖（包含 K線、成交量、技術指標）
        chartDock = new ChartDock();
        
        // 設置交易標記點擊監聽器
        chartDock.setTradeSelectionListener(trade -> {
            // 當用戶點擊交易標記時，顯示詳細信息
            showTradeDetail(trade);
        });
        
        DockableWrapper chartWrapper = new DockableWrapper("chart", I18n.get("dock.chart"), chartDock);
        Docking.registerDockable(chartWrapper);
        Docking.dock(chartWrapper, this);
        
        // 觀察清單
        watchlistPanel = new WatchlistPanel();
        watchlistPanel.setOnSymbolDoubleClick(this::changeSymbol);
        watchlistPanel.setOnSymbolAdded(symbol -> {
            subscribeWatchlistSymbol(symbol);
            restartSignalMonitorIfRunning();
        });
        watchlistPanel.setOnSymbolRemoved(symbol -> {
            unsubscribeWatchlistSymbol(symbol);
            restartSignalMonitorIfRunning();
        });
        DockableWrapper watchlistWrapper = new DockableWrapper("watchlist", I18n.get("dock.watchlist"), watchlistPanel);
        Docking.registerDockable(watchlistWrapper);
        Docking.dock(watchlistWrapper, chartWrapper, DockingRegion.WEST);

        // 今日機會雷達
        opportunityRadarDock = new OpportunityRadarDock();
        opportunityRadarDock.setOnSymbolSelected(this::changeSymbol);
        DockableWrapper radarWrapper = new DockableWrapper("opportunityRadar", "今日機會雷達", opportunityRadarDock);
        Docking.registerDockable(radarWrapper);
        Docking.dock(radarWrapper, watchlistWrapper, DockingRegion.SOUTH);
        
        // 五檔掛單
        orderBookDock = new OrderBookDock();
        DockableWrapper orderBookWrapper = new DockableWrapper("orderbook", I18n.get("dock.orderbook"), orderBookDock);
        Docking.registerDockable(orderBookWrapper);
        Docking.dock(orderBookWrapper, chartWrapper, DockingRegion.SOUTH);
        
        // 逐筆成交
        timeSalesDock = new TimeSalesDock();
        DockableWrapper timeSalesWrapper = new DockableWrapper("timesales", I18n.get("dock.timesales"), timeSalesDock);
        Docking.registerDockable(timeSalesWrapper);
        Docking.dock(timeSalesWrapper, orderBookWrapper, DockingRegion.EAST);
        
        // 市場消息
        newsDock = new NewsDock();
        DockableWrapper newsWrapper = new DockableWrapper("news", I18n.get("dock.news"), newsDock);
        Docking.registerDockable(newsWrapper);
        Docking.dock(newsWrapper, timeSalesWrapper, DockingRegion.EAST);

        // 設置 ChartDock 的引用，讓它可以轉發數據
        chartDock.setTimeSalesDock(timeSalesDock);
        chartDock.setNewsDock(newsDock);
        chartDock.setWatchlistPanel(watchlistPanel);

        // 市場分析面板
        marketAnalysisDock = new MarketAnalysisDock();
        DockableWrapper marketAnalysisWrapper = new DockableWrapper(
            "marketAnalysis", "市場分析", marketAnalysisDock);
        Docking.registerDockable(marketAnalysisWrapper);
        Docking.dock(marketAnalysisWrapper, watchlistWrapper, DockingRegion.SOUTH);

        // 模式建議面板
        modeRecommendationDock = new ModeRecommendationDock();
        DockableWrapper modeRecommendationWrapper = new DockableWrapper(
            "modeRecommendation", "模式建議", modeRecommendationDock);
        Docking.registerDockable(modeRecommendationWrapper);
        Docking.dock(modeRecommendationWrapper, marketAnalysisWrapper, DockingRegion.SOUTH);

        // 執行狀態面板
        executionStatusDock = new ExecutionStatusDock();
        executionStatusDock.setExecutionEngine(executionEngine);  // 連接執行引擎
        DockableWrapper executionStatusWrapper = new DockableWrapper(
            "executionStatus", "執行狀態", executionStatusDock);
        Docking.registerDockable(executionStatusWrapper);
        Docking.dock(executionStatusWrapper, modeRecommendationWrapper, DockingRegion.SOUTH);
    }

    private void initializeCurrentSymbolFromWatchlist() {
        if (currentSymbol != null && !currentSymbol.isBlank()) {
            return;
        }
        if (watchlistPanel == null || watchlistPanel.getSymbols().isEmpty()) {
            return;
        }

        currentSymbol = watchlistPanel.getSymbols().get(0);
        if (chartDock != null) {
            chartDock.setCurrentSymbol(currentSymbol);
        }
        if (newsDock != null) {
            newsDock.setCurrentSymbol(currentSymbol);
        }
        statusBar.setSymbol(currentSymbol);
    }

    /**
     * 初始化執行引擎
     */
    private void initializeExecutionEngine() {
        // 創建投資組合（初始資金 1,000,000）
        portfolio = new Portfolio(1_000_000.0);

        // 創建執行引擎（預設為模擬交易模式，手續費率 0.1425%）
        executionEngine = new ExecutionEngine(
            ExecutionMode.PAPER_TRADING,
            portfolio,
            0.001425  // 台股手續費約 0.1425%
        );

        System.out.println("ExecutionEngine initialized: " + executionEngine.getStatistics());

        // 初始化自動交易監控服務
        initializeSignalMonitor();
    }

    /**
     * 初始化信號監控服務
     */
    private void initializeSignalMonitor() {
        // 創建監控配置（預設：每 1 秒掃描，1 分鐘週期，100 根 K 線
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(1);
        config.setTimeframe(Timeframe.M1);
        config.setBarCount(100);
        config.setMinSignalIntervalMinutes(5);  // 同一商品信號間隔至少 5 分鐘

        // 創建監控服務（預設使用當沖策略，與回測系統一致）
        if (monitorDecisionConfig == null) {
            monitorDecisionConfig = createTestMonitorConfig();
        }
        signalMonitor = new SignalMonitorService(dataFeed, config, monitorDecisionConfig);

        // 設置信號檢測回調
        signalMonitor.setOnSignalDetected((symbol, signal) -> {
            handleTradingSignal(symbol, signal);
        });

        signalMonitor.setOnScanResults(this::updateScanResultsOnUi);

        // 設置狀態更新回調
        signalMonitor.setOnStatusUpdate(message -> {
            SwingUtilities.invokeLater(() -> {
                statusBar.setText(message);
                System.out.println("[監控] " + message);
            });
        });

        System.out.println("SignalMonitorService initialized");
    }

    private void updateScanResultOnUi(MarketScanResult result) {
        if (result == null) {
            return;
        }
        updateScanResultsOnUi(List.of(result));
    }

    private void updateScanResultsOnUi(List<MarketScanResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        Runnable updateTask = () -> {
            for (MarketScanResult result : results) {
                if (watchlistPanel != null) {
                    watchlistPanel.updateScanResult(result);
                }
                if (marketAnalysisDock != null) {
                    marketAnalysisDock.updateScanResult(result);
                }
            }
            if (opportunityRadarDock != null) {
                opportunityRadarDock.updateScanResults(results);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            updateTask.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(updateTask);
        } catch (Exception e) {
            SwingUtilities.invokeLater(updateTask);
            System.err.println("更新掃描 UI 失敗，已改用非同步更新：" + e.getMessage());
        }
    }

    private DecisionConfig createTestMonitorConfig() {
        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.setShortSellingEnabled(true);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.15);
        decisionConfig.getVotingConfig().setShortEntryThreshold(0.15);
        decisionConfig.getVotingConfig().setExitThreshold(0.15);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);
        decisionConfig.getRiskConfig().setMaxConcurrentPositions(50);
        decisionConfig.getRiskConfig().setMaxPositionSizePercent(0.95);
        decisionConfig.getRiskConfig().setMinCashReservePercent(0.0);
        return decisionConfig;
    }

    private DecisionConfig createBalancedMonitorConfig() {
        DecisionConfig decisionConfig = DecisionConfig.createDefault();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(true);
        decisionConfig.setShortSellingEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.35);
        decisionConfig.getVotingConfig().setShortEntryThreshold(0.45);
        decisionConfig.getVotingConfig().setExitThreshold(0.35);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);
        decisionConfig.getRiskConfig().setMaxConcurrentPositions(10);
        decisionConfig.getRiskConfig().setMaxPositionSizePercent(0.35);
        decisionConfig.getRiskConfig().setMinCashReservePercent(0.05);
        return decisionConfig;
    }

    /**
     * 處理交易信號
     */
    private void handleTradingSignal(String symbol, DecisionResult signal) {
        if (!autoTradingEnabled) {
            System.out.println("[信號] " + symbol + " - " + signal.getAction() + " (自動交易未啟用，忽略)");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                DecisionResult.Action action = signal.getAction();

                // 獲取當前價格（從最新 Bar 數據）
                double price = getCurrentPrice(symbol);
                if (price <= 0) {
                    System.out.println("無法獲取 " + symbol + " 的當前價格，跳過交易");
                    return;
                }
                if (executionStatusDock != null) {
                    executionStatusDock.updateMarketPrice(symbol, price);
                }

                String message = String.format("🔔 信號：%s - %s @ %.2f (置信度: %.0f%%)",
                    symbol, action, price, signal.getConfidence() * 100);

                System.out.println(message);
                statusBar.setText(message);

                // 根據信號執行交易
                ExecutionResult result = null;
                switch (action) {
                    case OPEN_LONG:
                        // 計算買入數量（使用可用資金的 20%）
                        double availableCash = portfolio.getCash();
                        double investAmount = availableCash * 0.2;  // 單次最多使用 20% 資金
                        int affordableShares = (int) (investAmount / price);
                        int quantity = affordableShares >= 1000
                            ? (affordableShares / 1000) * 1000
                            : affordableShares;  // 模擬盤允許零股，避免高價股測試時永遠無法開倉

                        if (quantity > 0) {
                            Double stopLoss = normalizeLongStopLoss(price, signal.getSuggestedStopLoss());
                            Double takeProfit = normalizeLongTakeProfit(price, signal.getSuggestedTakeProfit());
                            result = executionEngine.openPosition(symbol, quantity, price, stopLoss, takeProfit);
                            if (result.isSuccess()) {
                                autoExitRules.put(symbol, new AutoExitRule(stopLoss, takeProfit));
                                System.out.println("開倉成功：" + symbol + " x " + quantity + " @ " + price);
                            } else {
                                System.out.println("開倉失敗：" + result.getMessage());
                            }
                        } else {
                            System.out.println("資金不足，無法開倉");
                        }
                        break;

                    case CLOSE_POSITION:
                        // 平倉現有持倉
                        Position position = portfolio.getPosition(symbol);
                        if (position != null && position.getQuantity() > 0) {
                            int closeQuantity = position.getQuantity();
                            result = executionEngine.closePosition(symbol, closeQuantity, price);
                            if (result.isSuccess()) {
                                autoExitRules.remove(symbol);
                                System.out.println("平倉成功：" + symbol + " x " + closeQuantity + " @ " + price +
                                    " PnL=" + String.format("%.2f", result.getRealizedPnL()));
                            } else {
                                System.out.println("平倉失敗：" + result.getMessage());
                            }
                        } else {
                            System.out.println("無持倉，無法平倉");
                        }
                        break;

                    case OPEN_SHORT:
                        Position shortSignalPosition = portfolio.getPosition(symbol);
                        if (shortSignalPosition != null && shortSignalPosition.getQuantity() > 0) {
                            int closeQuantity = shortSignalPosition.getQuantity();
                            result = executionEngine.closePosition(symbol, closeQuantity, price);
                            if (result.isSuccess()) {
                                autoExitRules.remove(symbol);
                                System.out.println("SHORT 風險訊號觸發平倉：" + symbol + " x " + closeQuantity +
                                    " @ " + price + " PnL=" + String.format("%.2f", result.getRealizedPnL()));
                            } else {
                                System.out.println("SHORT 風險訊號平倉失敗：" + result.getMessage());
                            }
                        } else {
                            System.out.println("SHORT 風險訊號：" + symbol + " 無持倉，不開空單");
                        }
                        break;

                    default:
                        // HOLD 或 NO_ACTION，不執行操作
                        break;
                }

                // 更新 ExecutionStatusDock 顯示
                if (executionStatusDock != null && result != null) {
                    executionStatusDock.setExecutionEngine(executionEngine);
                }

            } catch (Exception e) {
                System.err.println("處理交易信號時發生錯誤: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * 獲取當前價格
     */
    private double getCurrentPrice(String symbol) {
        try {
            // 嘗試從 dataFeed 獲取最新的 K 線數據
            List<Bar> bars = dataFeed.fetchHistoricalBars(symbol, Timeframe.M1, 1);
            if (bars != null && !bars.isEmpty()) {
                return bars.get(bars.size() - 1).getClose();
            }
        } catch (Exception e) {
            System.err.println("獲取 " + symbol + " 價格失敗: " + e.getMessage());
        }
        return 0.0;
    }

    private Double normalizeLongStopLoss(double entryPrice, Double suggestedStopLoss) {
        if (suggestedStopLoss != null && suggestedStopLoss > 0.0 && suggestedStopLoss < entryPrice) {
            return suggestedStopLoss;
        }
        return entryPrice * 0.98;
    }

    private Double normalizeLongTakeProfit(double entryPrice, Double suggestedTakeProfit) {
        if (suggestedTakeProfit != null && suggestedTakeProfit > entryPrice) {
            return suggestedTakeProfit;
        }
        return entryPrice * 1.04;
    }

    private void checkAutoExit(String symbol, double price) {
        if (!autoTradingEnabled || symbol == null || price <= 0.0) {
            return;
        }

        AutoExitRule rule = autoExitRules.get(symbol);
        Position position = portfolio != null ? portfolio.getPosition(symbol) : null;
        if (rule == null || position == null || position.getQuantity() <= 0) {
            return;
        }

        String reason = null;
        if (rule.stopLoss != null && price <= rule.stopLoss) {
            reason = "停損";
        } else if (rule.takeProfit != null && price >= rule.takeProfit) {
            reason = "停利";
        }

        if (reason == null) {
            return;
        }

        int closeQuantity = position.getQuantity();
        ExecutionResult result = executionEngine.closePosition(symbol, closeQuantity, price);
        if (result.isSuccess()) {
            autoExitRules.remove(symbol);
            System.out.println(reason + "自動平倉：" + symbol + " x " + closeQuantity + " @ " + price
                + " PnL=" + String.format("%.2f", result.getRealizedPnL()));
            statusBar.setText(reason + "自動平倉：" + symbol + " @ " + String.format("%.2f", price));
            if (executionStatusDock != null) {
                executionStatusDock.setExecutionEngine(executionEngine);
            }
        } else {
            System.out.println(reason + "自動平倉失敗：" + result.getMessage());
        }
    }

    private void checkAllAutoExits() {
        if (!autoTradingEnabled || autoExitRules.isEmpty()) {
            return;
        }

        for (String symbol : new ArrayList<>(autoExitRules.keySet())) {
            double price = watchlistLastPrices.getOrDefault(symbol, 0.0);
            if (price <= 0.0) {
                price = getCurrentPrice(symbol);
            }
            if (price > 0.0) {
                if (executionStatusDock != null) {
                    executionStatusDock.updateMarketPrice(symbol, price);
                }
                checkAutoExit(symbol, price);
            }
        }
    }

    private static class AutoExitRule {
        private final Double stopLoss;
        private final Double takeProfit;

        private AutoExitRule(Double stopLoss, Double takeProfit) {
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
        }
    }

    private void showMonitorSettingsDialog() {
        if (monitorDecisionConfig == null) {
            monitorDecisionConfig = createTestMonitorConfig();
        }

        JDialog dialog = new JDialog(this, "監控門檻設定", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> templateBox = new JComboBox<>(new String[]{"目前設定", "測試低門檻", "平衡觀察"});
        JSpinner longThreshold = percentSpinner(monitorDecisionConfig.getVotingConfig().getLongEntryThreshold());
        JSpinner shortThreshold = percentSpinner(monitorDecisionConfig.getVotingConfig().getShortEntryThreshold());
        JSpinner exitThreshold = percentSpinner(monitorDecisionConfig.getVotingConfig().getExitThreshold());
        JSpinner minStrategies = new JSpinner(new SpinnerNumberModel(
            monitorDecisionConfig.getVotingConfig().getMinVotingStrategies(), 1, 10, 1));
        JCheckBox riskEnabled = new JCheckBox("啟用風險控管", monitorDecisionConfig.isRiskManagementEnabled());
        JCheckBox shortEnabled = new JCheckBox("允許空方訊號", monitorDecisionConfig.isShortSellingEnabled());

        templateBox.addActionListener(e -> {
            DecisionConfig selected = null;
            if (templateBox.getSelectedIndex() == 1) {
                selected = createTestMonitorConfig();
            } else if (templateBox.getSelectedIndex() == 2) {
                selected = createBalancedMonitorConfig();
            }
            if (selected != null) {
                longThreshold.setValue(selected.getVotingConfig().getLongEntryThreshold());
                shortThreshold.setValue(selected.getVotingConfig().getShortEntryThreshold());
                exitThreshold.setValue(selected.getVotingConfig().getExitThreshold());
                minStrategies.setValue(selected.getVotingConfig().getMinVotingStrategies());
                riskEnabled.setSelected(selected.isRiskManagementEnabled());
                shortEnabled.setSelected(selected.isShortSellingEnabled());
            }
        });

        addSettingsRow(panel, gbc, 0, "預設模板", templateBox);
        addSettingsRow(panel, gbc, 1, "做多門檻", longThreshold);
        addSettingsRow(panel, gbc, 2, "做空門檻", shortThreshold);
        addSettingsRow(panel, gbc, 3, "出場門檻", exitThreshold);
        addSettingsRow(panel, gbc, 4, "最少策略數", minStrategies);
        addSettingsRow(panel, gbc, 5, "", riskEnabled);
        addSettingsRow(panel, gbc, 6, "", shortEnabled);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("取消");
        JButton applyButton = new JButton("套用");
        cancelButton.addActionListener(e -> dialog.dispose());
        applyButton.addActionListener(e -> {
            DecisionConfig updatedConfig = switch (templateBox.getSelectedIndex()) {
                case 1 -> createTestMonitorConfig();
                case 2 -> createBalancedMonitorConfig();
                default -> monitorDecisionConfig;
            };
            updatedConfig.getVotingConfig().setLongEntryThreshold(((Number) longThreshold.getValue()).doubleValue());
            updatedConfig.getVotingConfig().setShortEntryThreshold(((Number) shortThreshold.getValue()).doubleValue());
            updatedConfig.getVotingConfig().setExitThreshold(((Number) exitThreshold.getValue()).doubleValue());
            updatedConfig.getVotingConfig().setMinVotingStrategies(((Number) minStrategies.getValue()).intValue());
            updatedConfig.setRiskManagementEnabled(riskEnabled.isSelected());
            updatedConfig.setShortSellingEnabled(shortEnabled.isSelected());
            monitorDecisionConfig = updatedConfig;

            boolean restart = autoTradingEnabled && signalMonitor != null;
            List<String> symbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();
            if (restart) {
                signalMonitor.stop();
                initializeSignalMonitor();
                if (!symbols.isEmpty()) {
                    signalMonitor.start(symbols);
                    autoTradingEnabled = true;
                }
            }
            statusBar.setText("監控門檻已更新");
            dialog.dispose();
        });
        buttons.add(cancelButton);
        buttons.add(applyButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JSpinner percentSpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 1.0, 0.05));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.00");
        spinner.setEditor(editor);
        return spinner;
    }

    private void addSettingsRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.65;
        panel.add(component, gbc);
    }

    /**
     * 啟動自動交易監控
     */
    private void startAutoTrading() {
        if (autoTradingEnabled) {
            JOptionPane.showMessageDialog(this,
                "自動交易監控已在運行中",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 獲取觀察列表中的商品
        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "觀察列表為空，請先添加要監控的商品",
                "無法啟動",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("確定要啟動自動交易監控嗎？\n\n" +
                "監控商品：%d 檔\n" +
                "執行模式：%s\n" +
                "初始資金：%.0f\n" +
                "可用資金：%.0f\n\n" +
                "請確保已充分測試策略",
                symbols.size(),
                executionEngine.getMode().getDisplayName(),
                portfolio.getInitialCash(),
                portfolio.getCash()),
            "啟動自動交易",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            signalMonitor.start(symbols);
            autoTradingEnabled = true;
            statusBar.setText("自動交易監控已啟動");
            System.out.println("自動交易監控已啟動，監控 " + symbols.size() + " 檔商品");
        }
    }

    /**
     * 停止自動交易監控
     */
    private void stopAutoTrading() {
        if (!autoTradingEnabled) {
            JOptionPane.showMessageDialog(this,
                "自動交易監控未運行",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "確定要停止自動交易監控嗎？",
            "停止監控",
            JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            signalMonitor.stop();
            autoTradingEnabled = false;
            statusBar.setText("自動交易監控已停止");
            System.out.println("自動交易監控已停止");
        }
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File Menu
        JMenu fileMenu = new JMenu(I18n.get("menu.file"));
        
        JMenuItem importCsvItem = new JMenuItem(I18n.get("menu.file.import.csv"));
        importCsvItem.addActionListener(e -> importCsvData());
        
        JMenuItem exportCsvItem = new JMenuItem(I18n.get("menu.file.export.csv"));
        exportCsvItem.addActionListener(e -> exportCsvData());
        
        fileMenu.add(importCsvItem);
        fileMenu.add(exportCsvItem);
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem(I18n.get("menu.file.exit"));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        
        // View Menu
        JMenu viewMenu = new JMenu(I18n.get("menu.view"));
        
        JMenu themeMenu = new JMenu(I18n.get("menu.view.theme"));
        JMenuItem lightTheme = new JMenuItem(I18n.get("menu.view.theme.light"));
        JMenuItem darkTheme = new JMenuItem(I18n.get("menu.view.theme.dark"));
        
        lightTheme.addActionListener(e -> setTheme(true));
        darkTheme.addActionListener(e -> setTheme(false));
        
        themeMenu.add(lightTheme);
        themeMenu.add(darkTheme);
        viewMenu.add(themeMenu);
        
        // Language Menu
        JMenu languageMenu = new JMenu(I18n.get("menu.view.language"));
        JMenuItem enItem = new JMenuItem(I18n.get("menu.view.language.en"));
        JMenuItem zhItem = new JMenuItem(I18n.get("menu.view.language.zh"));
        
        enItem.addActionListener(e -> changeLanguage(Locale.ENGLISH));
        zhItem.addActionListener(e -> changeLanguage(Locale.TRADITIONAL_CHINESE));
        
        languageMenu.add(enItem);
        languageMenu.add(zhItem);
        viewMenu.add(languageMenu);
        
        // 指標設定
        viewMenu.addSeparator();
        JMenuItem indicatorSettings = new JMenuItem(I18n.get("menu.view.indicator.settings"));
        indicatorSettings.addActionListener(e -> openIndicatorSettings());
        viewMenu.add(indicatorSettings);
        
        menuBar.add(viewMenu);

        // Data Source Menu
        JMenu dataMenu = new JMenu(I18n.get("menu.data"));

        JMenuItem dataSourceConfig = new JMenuItem(I18n.get("menu.data.config"));
        dataSourceConfig.addActionListener(e -> openDataSourceConfig());
        dataMenu.add(dataSourceConfig);

        dataMenu.addSeparator();

        // Quick switch submenu
        JMenu switchMenu = new JMenu(I18n.get("menu.data.switch"));

        JMenuItem switchToSimulator = new JMenuItem(I18n.get("menu.data.switch.simulator"));
        switchToSimulator.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.SIMULATOR));
        switchMenu.add(switchToSimulator);

        JMenuItem switchToYahoo = new JMenuItem(I18n.get("menu.data.switch.yahoo"));
        switchToYahoo.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.YAHOO_FINANCE));
        switchMenu.add(switchToYahoo);

        JMenuItem switchToAlphaVantage = new JMenuItem(I18n.get("menu.data.switch.alphavantage"));
        switchToAlphaVantage.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.ALPHA_VANTAGE));
        switchMenu.add(switchToAlphaVantage);

        JMenuItem switchToFinnhub = new JMenuItem(I18n.get("menu.data.switch.finnhub"));
        switchToFinnhub.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.FINNHUB));
        switchMenu.add(switchToFinnhub);

        JMenuItem switchToIEXCloud = new JMenuItem(I18n.get("menu.data.switch.iexcloud"));
        switchToIEXCloud.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.IEX_CLOUD));
        switchMenu.add(switchToIEXCloud);

        JMenuItem switchToPolygon = new JMenuItem(I18n.get("menu.data.switch.polygon"));
        switchToPolygon.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.POLYGON));
        switchMenu.add(switchToPolygon);

        dataMenu.add(switchMenu);
        menuBar.add(dataMenu);

        // Tools Menu
        JMenu toolsMenu = new JMenu(I18n.get("menu.tools"));

        JMenuItem backtestItem = new JMenuItem(I18n.get("menu.tools.backtest"));
        backtestItem.addActionListener(e -> openBacktestDialog());
        toolsMenu.add(backtestItem);

        toolsMenu.addSeparator();

        // 自動交易監控菜單
        JMenuItem monitorSettingsItem = new JMenuItem("監控門檻設定");
        monitorSettingsItem.addActionListener(e -> showMonitorSettingsDialog());
        toolsMenu.add(monitorSettingsItem);

        JMenuItem startMonitorItem = new JMenuItem("啟動自動交易監控");
        startMonitorItem.addActionListener(e -> startAutoTrading());
        toolsMenu.add(startMonitorItem);

        JMenuItem stopMonitorItem = new JMenuItem("停止自動交易監控");
        stopMonitorItem.addActionListener(e -> stopAutoTrading());
        toolsMenu.add(stopMonitorItem);

        menuBar.add(toolsMenu);

        // Layout Menu
        JMenu layoutMenu = new JMenu(I18n.get("menu.layout"));
        JMenuItem resetLayout = new JMenuItem(I18n.get("menu.layout.reset"));
        JMenuItem saveLayout = new JMenuItem(I18n.get("menu.layout.save"));
        resetLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "Reset Layout (Not implemented)"));
        saveLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "Save Layout (Not implemented)"));
        layoutMenu.add(resetLayout);
        layoutMenu.add(saveLayout);
        menuBar.add(layoutMenu);
        
        // Help Menu
        JMenu helpMenu = new JMenu(I18n.get("menu.help"));
        JMenuItem aboutItem = new JMenuItem(I18n.get("menu.help.about"));
        aboutItem.addActionListener(e -> 
            JOptionPane.showMessageDialog(this, 
                I18n.get("about.message"), 
                I18n.get("about.title"), 
                JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    private JToolBar createToolBar() {
        // 創建簡化的工具列，避免 ToolBarCallbacks 載入問題
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        // Symbol 搜尋框
        toolBar.add(new JLabel(" 商品 "));
        JTextField symbolField = new JTextField("", 8);
        symbolField.addActionListener(e -> changeSymbol(symbolField.getText()));
        toolBar.add(symbolField);
        
        JButton addToWatchlist = new JButton("加入觀察");
        toolBar.add(addToWatchlist);
        toolBar.addSeparator();
        
        // Timeframe 選擇
        toolBar.add(new JLabel(" 週期 "));
        JComboBox<Timeframe> timeframeCombo = new JComboBox<>(Timeframe.values());
        timeframeCombo.setMaximumSize(new Dimension(80, 25));
        timeframeCombo.addActionListener(e -> changeTimeframe((Timeframe) timeframeCombo.getSelectedItem()));
        toolBar.add(timeframeCombo);
        toolBar.addSeparator();

        // K線數量輸入
        toolBar.add(new JLabel(" K線數量 "));
        SpinnerNumberModel barCountModel = new SpinnerNumberModel(customBarCount, 10, 1000, 10);
        JSpinner barCountSpinner = new JSpinner(barCountModel);
        barCountSpinner.setMaximumSize(new Dimension(80, 25));
        barCountSpinner.addChangeListener(e -> {
            customBarCount = (Integer) barCountSpinner.getValue();
            System.out.println("[MainFrame] K線數量設定為: " + customBarCount);
            // 重新載入數據
            reloadDataWithCustomBarCount();
        });
        toolBar.add(barCountSpinner);
        toolBar.addSeparator();

        // 日期選擇
        toolBar.add(new JLabel(" 日期 "));

        JButton todayBtn = new JButton("今日");
        todayBtn.setToolTipText("切換到今日數據");
        todayBtn.addActionListener(e -> changeDateQuery(java.time.LocalDate.now()));
        toolBar.add(todayBtn);

        JButton yesterdayBtn = new JButton("昨日");
        yesterdayBtn.setToolTipText("切換到昨日數據");
        yesterdayBtn.addActionListener(e -> changeDateQuery(java.time.LocalDate.now().minusDays(1)));
        toolBar.add(yesterdayBtn);

        JButton customDateBtn = new JButton("自訂日期...");
        customDateBtn.setToolTipText("選擇特定日期");
        customDateBtn.addActionListener(e -> showDatePickerDialog());
        toolBar.add(customDateBtn);

        // 載入歷史數據按鈕
        JButton loadHistoryBtn = new JButton("📊 載入歷史數據");
        loadHistoryBtn.setToolTipText("從資料庫載入今日開盤後的所有數據");
        loadHistoryBtn.addActionListener(e -> loadHistoricalDataFromDatabase());
        toolBar.add(loadHistoryBtn);

        toolBar.addSeparator();

        // 指標選擇
        toolBar.add(new JLabel(" 指標 "));
        String[] indicators = {"無", "SMA", "EMA", "RSI", "MACD", "BOLL", "KD", "ADX", "OBV", "CCI", "WR"};
        JComboBox<String> indicatorCombo = new JComboBox<>(indicators);
        indicatorCombo.setMaximumSize(new Dimension(120, 25));
        indicatorCombo.addActionListener(e -> changeIndicator((String) indicatorCombo.getSelectedItem()));
        toolBar.add(indicatorCombo);
        toolBar.addSeparator();
        
        // 繪圖工具
        JToggleButton crosshairBtn = new JToggleButton("✛ 十字線");
        crosshairBtn.setSelected(true);
        crosshairBtn.addActionListener(e -> chartDock.toggleCrosshair());
        toolBar.add(crosshairBtn);
        
        JToggleButton trendlineBtn = new JToggleButton("📈 趨勢線");
        trendlineBtn.addActionListener(e -> toggleTrendline());
        toolBar.add(trendlineBtn);
        
        JToggleButton hlineBtn = new JToggleButton("─ 水平線");
        hlineBtn.addActionListener(e -> toggleHorizontalLine());
        toolBar.add(hlineBtn);

        JToggleButton measureBtn = new JToggleButton("📏 測量工具");
        measureBtn.addActionListener(e -> toggleMeasure());
        toolBar.add(measureBtn);

        JToggleButton fibonacciBtn = new JToggleButton("📊 斐波那契");
        fibonacciBtn.addActionListener(e -> toggleFibonacci());
        toolBar.add(fibonacciBtn);

        toolBar.addSeparator();
        
        // Zoom 控制
        JButton zoomInBtn = new JButton("🔍+ 放大");
        zoomInBtn.addActionListener(e -> chartDock.zoomIn());
        toolBar.add(zoomInBtn);
        
        JButton zoomOutBtn = new JButton("🔍- 縮小");
        zoomOutBtn.addActionListener(e -> chartDock.zoomOut());
        toolBar.add(zoomOutBtn);
        
        JButton zoomResetBtn = new JButton("重置");
        zoomResetBtn.addActionListener(e -> chartDock.resetZoom());
        toolBar.add(zoomResetBtn);
        toolBar.addSeparator();
        
        // 數據模擬控制
        JToggleButton simulationBtn = new JToggleButton("⏸ 暫停模擬");
        simulationBtn.setSelected(true);  // 預設為暫停狀態
        simulationBtn.addActionListener(e -> toggleSimulation(simulationBtn));
        toolBar.add(simulationBtn);
        
        toolBar.addSeparator();
        
        // 交易標記篩選
        JButton filterBtn = new JButton("🔍 篩選標記");
        filterBtn.addActionListener(e -> chartDock.showTradeMarkerFilter());
        toolBar.add(filterBtn);
        
        return toolBar;
    }
    
    private void subscribeMarketData() {
        // 創建新的監聽器
        currentMarketDataListener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                lastPrice = tick.getPrice();
                statusBar.setLastPrice(lastPrice, 0);
                frameCount++;
                chartDock.onTick(tick);
            }

            @Override
            public void onDepthUpdate(List<DepthLevel> depth) {
                orderBookDock.updateDepth(depth);
            }

            @Override
            public void onTrade(com.dreamhouse.trading.core.model.Trade trade) {
                timeSalesDock.addTrade(trade);
            }

            @Override
            public void onNews(com.dreamhouse.trading.core.model.NewsItem news) {
                // ⭐ 轉發新聞到 ChartDock，由它轉發到 NewsDock
                chartDock.onNews(news);
            }
        };

        // 訂閱當前商品
        dataFeed.subscribe(currentSymbol, currentMarketDataListener);
    }

    private void subscribeWatchlistMarketData() {
        if (watchlistPanel == null) {
            return;
        }
        for (String symbol : watchlistPanel.getSymbols()) {
            subscribeWatchlistSymbol(symbol);
        }
    }

    private void subscribeWatchlistSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || dataFeed == null) {
            return;
        }

        watchlistMarketDataListeners.computeIfAbsent(symbol, key -> {
            MarketDataListener listener = new MarketDataListener() {
                @Override
                public void onTick(Tick tick) {
                    double previous = watchlistLastPrices.getOrDefault(key, tick.getPrice());
                    watchlistLastPrices.put(key, tick.getPrice());
                    double changePct = previous > 0 ? ((tick.getPrice() - previous) / previous) * 100.0 : 0.0;
                    if (executionStatusDock != null) {
                        executionStatusDock.updateMarketPrice(key, tick.getPrice());
                    }
                    checkAutoExit(key, tick.getPrice());
                    if (watchlistPanel != null) {
                        watchlistPanel.updateItem(key, tick.getPrice(), changePct, tick.getVolume());
                    }
                }

                @Override
                public void onNews(NewsItem news) {
                    if (newsDock != null) {
                        newsDock.addNews(news);
                    }
                }
            };
            dataFeed.subscribe(key, listener);
            if (dataFeed.isConnected()) {
                dataFeed.loadHistoricalData(key, currentTimeframe, Math.max(2, customBarCount));
            }
            return listener;
        });
    }

    private void unsubscribeWatchlistSymbol(String symbol) {
        if (symbol == null || dataFeed == null) {
            return;
        }
        MarketDataListener listener = watchlistMarketDataListeners.remove(symbol);
        watchlistLastPrices.remove(symbol);
        if (listener != null) {
            dataFeed.unsubscribe(symbol, listener);
        }
    }

    private void unsubscribeAllWatchlistSymbols() {
        if (dataFeed != null) {
            for (Map.Entry<String, MarketDataListener> entry : watchlistMarketDataListeners.entrySet()) {
                dataFeed.unsubscribe(entry.getKey(), entry.getValue());
            }
        }
        watchlistMarketDataListeners.clear();
        watchlistLastPrices.clear();
    }

    private void restartSignalMonitorIfRunning() {
        if (!autoTradingEnabled || signalMonitor == null || watchlistPanel == null) {
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        signalMonitor.stop();
        if (!symbols.isEmpty()) {
            signalMonitor.start(symbols);
        }
    }

    private void changeSymbol(String symbol) {
        System.out.println("[MainFrame] 切換商品: " + currentSymbol + " -> " + symbol);

        // 取消舊商品的訂閱
        if (currentMarketDataListener != null) {
            dataFeed.unsubscribe(currentSymbol, currentMarketDataListener);
            System.out.println("[MainFrame] 取消訂閱: " + currentSymbol);
        }

        // 清除圖表數據
        chartDock.clearAllData();

        // 更新當前商品
        currentSymbol = symbol;
        chartDock.setCurrentSymbol(symbol);  // ⭐ 設置圖表的當前商品（用於觀察清單更新）
        if (newsDock != null) {
            newsDock.setCurrentSymbol(symbol);
        }
        statusBar.setSymbol(symbol);
        statusBar.setText("正在載入 " + symbol + " " + currentTimeframe.getLabel() + " 歷史數據...");

        // 訂閱新商品
        subscribeMarketData();

        // 在背景執行緒中加載歷史數據，使用當前週期
        new Thread(() -> {
            try {
                // 確保subscribe完成後再加載歷史數據
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始加載 " + symbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 歷史數據");
                dataFeed.loadHistoricalData(symbol, currentTimeframe, customBarCount);
                System.out.println("[MainFrame] " + symbol + " 歷史數據載入完成");

                SwingUtilities.invokeLater(() -> {
                    statusBar.setText(symbol + " " + currentTimeframe.getLabel() + " 數據載入完成");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[MainFrame] 歷史數據載入被中斷");
            }
        }, "HistoricalDataLoader-" + symbol).start();

        System.out.println("[MainFrame] 商品切換完成: " + symbol);
    }

    private void changeTimeframe(Timeframe tf) {
        System.out.println("[MainFrame] 切換週期: " + currentTimeframe + " -> " + tf);

        currentTimeframe = tf;
        statusBar.setTimeframe(tf.getLabel());
        statusBar.setText("正在載入 " + currentSymbol + " " + tf.getLabel() + " 數據...");

        // 清除圖表數據
        chartDock.clearAllData();

        // 在背景執行緒中重新加載當前商品的歷史數據，使用新的週期
        new Thread(() -> {
            try {
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始重新加載 " + currentSymbol + " " + customBarCount + " 根 " + tf.getLabel() + " 歷史數據");
                dataFeed.loadHistoricalData(currentSymbol, tf, customBarCount);
                System.out.println("[MainFrame] " + currentSymbol + " " + tf.getLabel() + " 歷史數據重新載入完成");

                SwingUtilities.invokeLater(() -> {
                    statusBar.setText(currentSymbol + " " + tf.getLabel() + " 數據載入完成");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[MainFrame] 歷史數據重新載入被中斷");
            }
        }, "TimeframeChanger-" + tf).start();
    }
    
    /**
     * 當用戶修改K線數量時，重新載入數據
     */
    private void reloadDataWithCustomBarCount() {
        System.out.println("[MainFrame] 重新載入數據，使用自定義K線數量: " + customBarCount);

        // 清除圖表數據
        chartDock.clearAllData();

        statusBar.setText("正在載入 " + currentSymbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 數據...");

        // 在背景執行緒中重新加載數據
        new Thread(() -> {
            try {
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始加載 " + currentSymbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 歷史數據");
                dataFeed.loadHistoricalData(currentSymbol, currentTimeframe, customBarCount);
                System.out.println("[MainFrame] " + currentSymbol + " 歷史數據重新載入完成");

                SwingUtilities.invokeLater(() -> {
                    statusBar.setText(currentSymbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 數據載入完成");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[MainFrame] 歷史數據重新載入被中斷");
            }
        }, "CustomBarCountLoader").start();
    }

    /**
     * 變更查詢日期
     */
    private void changeDateQuery(java.time.LocalDate date) {
        System.out.println("[MainFrame] 切換查詢日期: " + date);

        // 如果當前數據源是 FinMindFeed，設置查詢日期
        if (dataFeed instanceof com.dreamhouse.trading.core.FinMindFeed) {
            ((com.dreamhouse.trading.core.FinMindFeed) dataFeed).setQueryDate(date);
            statusBar.setText("已切換至 " + date + " 的數據");
        } else {
            JOptionPane.showMessageDialog(this,
                "日期查詢功能僅支援 FinMind 數據源\n請先切換至 FinMind 數據源",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 顯示日期選擇對話框
     */
    private void showDatePickerDialog() {
        // 創建簡單的日期選擇對話框
        JDialog dialog = new JDialog(this, "選擇日期", true);
        dialog.setLayout(new java.awt.BorderLayout());

        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 5, 5, 5);
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;

        // 年
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("年："), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel yearModel = new SpinnerNumberModel(
            java.time.LocalDate.now().getYear(), 2000, 2100, 1);
        JSpinner yearSpinner = new JSpinner(yearModel);
        panel.add(yearSpinner, gbc);

        // 月
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("月："), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel monthModel = new SpinnerNumberModel(
            java.time.LocalDate.now().getMonthValue(), 1, 12, 1);
        JSpinner monthSpinner = new JSpinner(monthModel);
        panel.add(monthSpinner, gbc);

        // 日
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("日："), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel dayModel = new SpinnerNumberModel(
            java.time.LocalDate.now().getDayOfMonth(), 1, 31, 1);
        JSpinner daySpinner = new JSpinner(dayModel);
        panel.add(daySpinner, gbc);

        dialog.add(panel, java.awt.BorderLayout.CENTER);

        // 按鈕
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("確定");
        JButton cancelButton = new JButton("取消");

        okButton.addActionListener(e -> {
            int year = (Integer) yearSpinner.getValue();
            int month = (Integer) monthSpinner.getValue();
            int day = (Integer) daySpinner.getValue();

            try {
                java.time.LocalDate selectedDate = java.time.LocalDate.of(year, month, day);
                changeDateQuery(selectedDate);
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                    "無效的日期！",
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * 從資料庫載入歷史數據
     * 用於補充開盤後的缺失數據
     */
    private void loadHistoricalDataFromDatabase() {
        System.out.println("[MainFrame] 手動觸發載入資料庫歷史數據");

        // 檢查當前數據源
        if (!(dataFeed instanceof com.dreamhouse.trading.core.FinMindFeed)) {
            JOptionPane.showMessageDialog(this,
                "歷史數據載入功能僅支援 FinMind 數據源\n請先切換至 FinMind 數據源",
                "提示",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 顯示載入中提示
        statusBar.setText("正在從資料庫載入今日開盤至今的數據...");

        // 在背景執行載入任務
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private int loadedCount = 0;
            private String errorMessage = null;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // 創建 MarketDataLoader
                    com.dreamhouse.trading.core.MarketDataLoader loader =
                        new com.dreamhouse.trading.core.MarketDataLoader();
                    loader.initialize();

                    // 載入當前商品的歷史數據
                    java.util.List<com.dreamhouse.trading.core.model.Tick> ticks =
                        loader.loadTodayMarketOpenToNow(currentSymbol);

                    loadedCount = ticks.size();

                    if (loadedCount > 0) {
                        // 通知所有監聽器
                        for (com.dreamhouse.trading.core.model.Tick tick : ticks) {
                            chartDock.onTick(tick);
                        }
                    }

                    loader.close();
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    statusBar.setText("載入失敗：" + errorMessage);
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                        "從資料庫載入數據失敗！\n" +
                        "錯誤訊息：" + errorMessage + "\n\n" +
                        "請確認：\n" +
                        "1. MarketDataCollector 資料庫正在運行\n" +
                        "2. 資料庫中有今日 " + currentSymbol + " 的數據",
                        "載入失敗",
                        JOptionPane.ERROR_MESSAGE);
                } else if (loadedCount == 0) {
                    statusBar.setText("資料庫中沒有今日數據");
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                        "資料庫中沒有找到今日 " + currentSymbol + " 的數據！\n\n" +
                        "請確認 MarketDataCollector 已經運行並收集了數據。",
                        "無數據",
                        JOptionPane.WARNING_MESSAGE);
                } else {
                    statusBar.setText("已從資料庫載入 " + loadedCount + " 筆數據");
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                        "成功從資料庫載入 " + loadedCount + " 筆歷史數據！",
                        "載入成功",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void changeIndicator(String indicator) {
        // 根據工具列選擇的指標，控制 ChartDock 的疊線指標和副圖指標
        // 工具列使用的是英文字符串，直接匹配
        System.out.println("選擇指標: " + indicator);
        if ("無".equals(indicator) || "None".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("None");
        } else if ("SMA".equals(indicator)) {
            chartDock.setOverlayIndicator("SMA");
            chartDock.setSubIndicator("None");
        } else if ("EMA".equals(indicator)) {
            chartDock.setOverlayIndicator("EMA");
            chartDock.setSubIndicator("None");
        } else if ("RSI".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("RSI");
        } else if ("MACD".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("MACD");
        } else if ("BOLL".equals(indicator)) {
            chartDock.setOverlayIndicator("BOLL");
            chartDock.setSubIndicator("None");
        } else if ("KD".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("KD");
        } else if ("ADX".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("ADX");
        } else if ("OBV".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("OBV");
        } else if ("CCI".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("CCI");
        } else if ("WR".equals(indicator)) {
            chartDock.setOverlayIndicator("None");
            chartDock.setSubIndicator("WR");
        }
    }
    
    private void toggleTrendline() {
        // 切換趨勢線工具
        if (chartDock.getDrawingManager().getCurrentTool() == DrawingManager.DrawingTool.TREND_LINE) {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.NONE);
            System.out.println("Trendline tool disabled");
        } else {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.TREND_LINE);
            System.out.println("Trendline tool enabled");
        }
    }
    
    private void toggleHorizontalLine() {
        // 切換水平線工具
        if (chartDock.getDrawingManager().getCurrentTool() == DrawingManager.DrawingTool.HORIZONTAL_LINE) {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.NONE);
            System.out.println("Horizontal line tool disabled");
        } else {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.HORIZONTAL_LINE);
            System.out.println("Horizontal line tool enabled");
        }
    }

    private void toggleMeasure() {
        // 切換測量工具
        if (chartDock.getDrawingManager().getCurrentTool() == DrawingManager.DrawingTool.MEASURE) {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.NONE);
            System.out.println("Measure tool disabled");
        } else {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.MEASURE);
            System.out.println("Measure tool enabled");
        }
    }

    private void toggleFibonacci() {
        // 切換斐波那契回調工具
        if (chartDock.getDrawingManager().getCurrentTool() == DrawingManager.DrawingTool.FIBONACCI) {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.NONE);
            System.out.println("Fibonacci tool disabled");
        } else {
            chartDock.setDrawingTool(DrawingManager.DrawingTool.FIBONACCI);
            System.out.println("Fibonacci tool enabled");
        }
    }
    
    /**
     * 切換數據模擬的暫停/開始狀態
     */
    private void toggleSimulation(JToggleButton button) {
        if (button.isSelected()) {
            // 暫停模擬
            dataFeed.pause();
            button.setText("▶ 開始模擬");
            statusBar.setText("數據模擬已暫停 - 適合查看歷史數據");
            System.out.println("[MainFrame] 數據模擬已暫停");
        } else {
            // 恢復模擬
            dataFeed.resume();
            button.setText("⏸ 暫停模擬");
            statusBar.setText("數據模擬運行中");
            System.out.println("[MainFrame] 數據模擬已恢復");
        }
    }
    
    /**
     * 顯示交易詳情對話框
     */
    private void showTradeDetail(com.dreamhouse.trading.core.backtest.Trade trade) {
        JDialog dialog = new JDialog(this, "交易詳情", true);
        dialog.setLayout(new BorderLayout(10, 10));
        
        // 創建詳情面板
        JPanel detailPanel = new JPanel(new GridLayout(0, 2, 10, 5));
        detailPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // 添加交易信息
        addDetailRow(detailPanel, "交易類型:", trade.getType().getDisplayName());
        addDetailRow(detailPanel, "時間:", trade.getTimestamp().toString());
        addDetailRow(detailPanel, "商品代碼:", trade.getSymbol());
        addDetailRow(detailPanel, "價格:", String.format("%.2f", trade.getPrice()));
        addDetailRow(detailPanel, "數量:", String.valueOf(trade.getQuantity()));
        addDetailRow(detailPanel, "金額:", String.format("%.2f", trade.getTotalAmount()));
        addDetailRow(detailPanel, "手續費:", String.format("%.2f", trade.getCommissionAmount()));
        
        if (trade.getStopLoss() != null) {
            addDetailRow(detailPanel, "停損價格:", String.format("%.2f", trade.getStopLoss()));
        }
        if (trade.getTakeProfit() != null) {
            addDetailRow(detailPanel, "停利價格:", String.format("%.2f", trade.getTakeProfit()));
        }
        if (trade.getExitReason() != null) {
            addDetailRow(detailPanel, "出場原因:", trade.getExitReason());
        }
        
        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        
        dialog.add(detailPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    /**
     * 添加詳情行
     */
    private void addDetailRow(JPanel panel, String label, String value) {
        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelComp.getFont().deriveFont(Font.BOLD));
        panel.add(labelComp);
        
        JLabel valueComp = new JLabel(value);
        panel.add(valueComp);
    }
    
    private void setTheme(boolean light) {
        try {
            if (light) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } else {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
            
            // 刷新圖表主題
            if (chartDock != null) {
                chartDock.refreshTheme();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private void changeLanguage(Locale locale) {
        I18n.setLocale(locale);
        refreshUI();
    }
    
    private void refreshUI() {
        setTitle(I18n.get("app.title"));
        setJMenuBar(createMenuBar());
        SwingUtilities.updateComponentTreeUI(this);
    }
    
    private void openIndicatorSettings() {
        IndicatorSettingsDialog dialog = new IndicatorSettingsDialog(this, chartDock);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            System.out.println("指標設定已確認:");
            System.out.println("SMA Period: " + dialog.getSmaPeriod());
            System.out.println("EMA Period: " + dialog.getEmaPeriod());
            System.out.println("RSI Period: " + dialog.getRsiPeriod());
            System.out.println("MACD: " + dialog.getMacdFast() + "/" + dialog.getMacdSlow() + "/" + dialog.getMacdSignal());
            System.out.println("BOLL: " + dialog.getBollPeriod() + ", " + dialog.getBollMultiplier());
            System.out.println("KD: " + dialog.getKdKPeriod() + "/" + dialog.getKdDPeriod());
            
            // 應用 SMA 參數
            IndicatorConfig smaConfig = new IndicatorConfig();
            smaConfig.setPeriod(dialog.getSmaPeriod());
            smaConfig.setColor(dialog.getSmaColor());
            chartDock.setIndicatorParameters("SMA", smaConfig);
            
            // 應用 EMA 參數
            IndicatorConfig emaConfig = new IndicatorConfig();
            emaConfig.setPeriod(dialog.getEmaPeriod());
            emaConfig.setColor(dialog.getEmaColor());
            chartDock.setIndicatorParameters("EMA", emaConfig);
            
            // 應用 RSI 參數
            IndicatorConfig rsiConfig = new IndicatorConfig();
            rsiConfig.setPeriod(dialog.getRsiPeriod());
            rsiConfig.setColor(dialog.getRsiColor());
            chartDock.setIndicatorParameters("RSI", rsiConfig);
            
            // 應用 MACD 參數
            IndicatorConfig macdConfig = new IndicatorConfig();
            macdConfig.setFastPeriod(dialog.getMacdFast());
            macdConfig.setSlowPeriod(dialog.getMacdSlow());
            macdConfig.setSignalPeriod(dialog.getMacdSignal());
            chartDock.setIndicatorParameters("MACD", macdConfig);
            
            // 應用 BOLL 參數
            IndicatorConfig bollConfig = new IndicatorConfig();
            bollConfig.setPeriod(dialog.getBollPeriod());
            bollConfig.setMultiplier(dialog.getBollMultiplier());
            chartDock.setIndicatorParameters("BOLL", bollConfig);
            
            // 應用 KD 參數
            IndicatorConfig kdConfig = new IndicatorConfig();
            kdConfig.setPeriod(dialog.getKdKPeriod());
            kdConfig.setPeriod2(dialog.getKdDPeriod());
            chartDock.setIndicatorParameters("KD", kdConfig);
            
            // 應用 ADX 參數
            IndicatorConfig adxConfig = new IndicatorConfig();
            adxConfig.setPeriod(dialog.getAdxPeriod());
            chartDock.setIndicatorParameters("ADX", adxConfig);
            
            // 應用 CCI 參數
            IndicatorConfig cciConfig = new IndicatorConfig();
            cciConfig.setPeriod(dialog.getCciPeriod());
            chartDock.setIndicatorParameters("CCI", cciConfig);
            
            // 應用 WR 參數
            IndicatorConfig wrConfig = new IndicatorConfig();
            wrConfig.setPeriod(dialog.getWrPeriod());
            chartDock.setIndicatorParameters("WR", wrConfig);
            
            statusBar.setText(I18n.get("status.indicator.updated"));
            System.out.println("✓ 指標參數已應用到圖表");
        }
    }
    
    /**
     * 格式化時間間隔顯示
     */
    private String formatInterval(int minutes) {
        if (minutes >= 1440) {
            int days = minutes / 1440;
            return days == 1 ? "日線" : days + "日線";
        } else if (minutes >= 60) {
            int hours = minutes / 60;
            return hours == 1 ? "1小時" : hours + "小時";
        } else {
            return minutes + "分鐘";
        }
    }

    /**
     * 開啟回測對話框
     */
    private void openBacktestDialog() {
        // 1. 顯示配置對話框
        BacktestConfigDialog configDialog = new BacktestConfigDialog(this);
        configDialog.setVisible(true);
        
        if (!configDialog.isConfirmed()) {
            return;
        }
        
        // 2. 獲取當前圖表數據
        List<org.ta4j.core.Bar> ta4jBars = chartDock.getCurrentBars();
        if (ta4jBars == null || ta4jBars.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "沒有可用的歷史數據進行回測！\n請先載入 CSV 數據或等待實時數據累積。",
                "數據不足",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 3. 驗證數據週期是否匹配策略要求
        int dataIntervalMinutes = chartDock.getDetectedIntervalMinutes();
        int strategyRequiredMinutes = configDialog.getStrategyRequiredIntervalMinutes();
        String strategyName = configDialog.getStrategyDisplayName();

        if (strategyRequiredMinutes > 0 && dataIntervalMinutes > 0) {
            if (dataIntervalMinutes > strategyRequiredMinutes) {
                // 數據週期太大（例如：1小時數據，但策略要求5分鐘）
                String dataInterval = formatInterval(dataIntervalMinutes);
                String requiredInterval = formatInterval(strategyRequiredMinutes);

                JOptionPane.showMessageDialog(this,
                    String.format(
                        "⚠️ 數據週期不匹配！\n\n" +
                        "策略：%s\n" +
                        "要求週期：%s\n" +
                        "當前數據：%s\n\n" +
                        "❌ 無法使用 %s 的數據執行 %s 的策略！\n\n" +
                        "建議：\n" +
                        "1. 載入更細的時間週期數據（%s 或更小）\n" +
                        "2. 或選擇適合當前數據的策略",
                        strategyName,
                        requiredInterval,
                        dataInterval,
                        dataInterval,
                        requiredInterval,
                        requiredInterval
                    ),
                    "數據週期不匹配",
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            } else if (dataIntervalMinutes < strategyRequiredMinutes) {
                // 數據週期太小（例如：1分鐘數據，但策略要求1小時）
                // 這種情況可以聚合，給出警告但允許繼續
                String dataInterval = formatInterval(dataIntervalMinutes);
                String requiredInterval = formatInterval(strategyRequiredMinutes);

                int choice = JOptionPane.showConfirmDialog(this,
                    String.format(
                        "⚠️ 數據週期較小\n\n" +
                        "策略：%s\n" +
                        "要求週期：%s\n" +
                        "當前數據：%s\n\n" +
                        "系統會嘗試將 %s 數據聚合為 %s，但可能影響準確性。\n\n" +
                        "是否繼續？",
                        strategyName,
                        requiredInterval,
                        dataInterval,
                        dataInterval,
                        requiredInterval
                    ),
                    "數據週期警告",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );

                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
        }

        // 3. 轉換數據格式並應用日期過濾
        List<com.dreamhouse.trading.core.model.Bar> bars = new ArrayList<>();
        boolean dateFilterEnabled = configDialog.isDateFilterEnabled();
        java.time.LocalDate startDate = dateFilterEnabled ? configDialog.getStartDate() : null;
        java.time.LocalDate endDate = dateFilterEnabled ? configDialog.getEndDate() : null;

        System.out.println(String.format("日期過濾: %s, 開始: %s, 結束: %s",
            dateFilterEnabled ? "啟用" : "停用",
            startDate != null ? startDate.toString() : "無",
            endDate != null ? endDate.toString() : "無"));

        int totalBars = 0;
        int filteredBars = 0;

        for (org.ta4j.core.Bar ta4jBar : ta4jBars) {
            totalBars++;
            java.time.LocalDateTime barDateTime = ta4jBar.getBeginTime().toLocalDateTime();
            java.time.LocalDate barDate = barDateTime.toLocalDate();

            // 應用日期過濾
            if (dateFilterEnabled) {
                // 檢查是否在日期範圍內
                if (barDate.isBefore(startDate) || barDate.isAfter(endDate)) {
                    continue;  // 跳過不在範圍內的K線
                }
            }

            filteredBars++;
            com.dreamhouse.trading.core.model.Bar bar = new com.dreamhouse.trading.core.model.Bar(
                barDateTime,
                ta4jBar.getOpenPrice().doubleValue(),
                ta4jBar.getHighPrice().doubleValue(),
                ta4jBar.getLowPrice().doubleValue(),
                ta4jBar.getClosePrice().doubleValue(),
                ta4jBar.getVolume().longValue()
            );
            bars.add(bar);
        }

        System.out.println(String.format("數據過濾結果: 總共 %d 根K線，過濾後 %d 根K線",
            totalBars, filteredBars));

        // 檢查過濾後是否還有足夠的數據
        if (bars.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "過濾後沒有可用的K線數據！\n請調整日期範圍。",
                "數據不足",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 4. 創建回測引擎
        BacktestEngine engine = new BacktestEngine();
        engine.setInitialCapital(configDialog.getInitialCapital());
        engine.setCommission(configDialog.getCommission());
        engine.setSlippage(configDialog.getSlippage());
        engine.setData(bars);
        
        // 5. 添加策略
        Strategy strategy = configDialog.getSelectedStrategy();
        if (strategy != null) {
            engine.addStrategy(strategy);
        }

        // 5.5. 添加回測監聽器以更新 UI 面板
        engine.addListener(new BacktestListener() {
            private int updateCounter = 0;  // 計數器，避免過於頻繁更新

            @Override
            public void onBacktestStarted() {
                System.out.println("[UI] 回測開始");
            }

            @Override
            public void onProgressUpdate(double progress) {
                // 進度更新已由 BacktestProgressDialog 處理
            }

            @Override
            public void onBarProcessed(int barIndex, org.ta4j.core.Bar bar) {
                // 每 10 根 K 線更新一次 UI，避免過於頻繁
                updateCounter++;
                if (updateCounter % 10 != 0) {
                    return;
                }

                // 檢查策略是否為 MultiTimeframeDecisionStrategy
                if (strategy instanceof com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy) {
                    com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy mtStrategy =
                        (com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy) strategy;

                    com.dreamhouse.trading.core.decision.DecisionEngine decisionEngine = mtStrategy.getDecisionEngine();

                    if (decisionEngine != null) {
                        // 更新市場分析面板
                        marketAnalysisDock.updateFromDecisionEngine(decisionEngine);

                        // 更新模式建議面板
                        modeRecommendationDock.updateFromDecisionEngine(decisionEngine);
                    }
                }
            }

            @Override
            public void onTradeExecuted(String symbol, TradeType type, int quantity, double price) {
                System.out.println(String.format("[UI] 交易執行: %s %s %d @ %.2f",
                    symbol, type, quantity, price));
            }

            @Override
            public void onBacktestCompleted(BacktestResult result) {
                System.out.println("[UI] 回測完成，交易數: " + result.getTrades().size());

                // 最後更新一次 UI 面板
                if (strategy instanceof com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy) {
                    com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy mtStrategy =
                        (com.dreamhouse.trading.core.decision.strategies.MultiTimeframeDecisionStrategy) strategy;

                    com.dreamhouse.trading.core.decision.DecisionEngine decisionEngine = mtStrategy.getDecisionEngine();

                    if (decisionEngine != null) {
                        marketAnalysisDock.updateFromDecisionEngine(decisionEngine);
                        modeRecommendationDock.updateFromDecisionEngine(decisionEngine);
                    }
                }
            }
        });

        // 6. 創建 SwingWorker
        SwingWorker<BacktestResult, Void> worker = new SwingWorker<BacktestResult, Void>() {
            @Override
            protected BacktestResult doInBackground() throws Exception {
                try {
                    return engine.runBacktest();
                } catch (Exception e) {
                    System.out.println("回測執行異常: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }
            
            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        System.out.println("回測被取消");
                        return;
                    }
                    
                    BacktestResult result = get();
                    System.out.println("回測完成，結果: " + (result != null ? "成功" : "失敗"));
                    if (result != null) {
                        System.out.println("交易次數: " + result.getTrades().size());
                    }
                    
                    if (result != null) {
                        // 顯示結果對話框並在圖表上標記交易點
                        SwingUtilities.invokeLater(() -> {
                            try {
                                // 在圖表上顯示交易標記
                                chartDock.showTradeMarkers(result.getTrades());
                                
                                // 顯示回測結果對話框
                                BacktestResultDialog resultDialog = new BacktestResultDialog(
                                    MainFrameWithDocking.this, result, strategy.getName());
                                resultDialog.setVisible(true);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                                    "顯示回測結果時發生錯誤: " + ex.getMessage(),
                                    "錯誤", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    } else {
                        System.out.println("回測結果為空，可能是數據不足或策略沒有產生交易");
                    }
                } catch (java.util.concurrent.CancellationException e) {
                    System.out.println("回測被取消: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this, 
                        "回測執行失敗：" + e.getMessage(), 
                        "錯誤", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        // 7. 先啟動 SwingWorker，然後顯示進度對話框
        BacktestProgressDialog progressDialog = new BacktestProgressDialog(this, engine, worker);
        
        // 先執行 worker，再顯示對話框（模態對話框會阻塞）
        worker.execute();
        
        // 顯示模態對話框（這會阻塞直到對話框關閉）
        progressDialog.setVisible(true);
    }
    
    private void importCsvData() {
        CsvImportDialog dialog = new CsvImportDialog(this);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed() && dialog.getImportedBars() != null) {
            List<Bar> bars = dialog.getImportedBars();
            
            // 載入 CSV 數據到圖表
            chartDock.loadHistoricalData(bars);
            
            System.out.println("CSV 匯入成功: " + bars.size() + " 條 K 線");
            statusBar.setText(String.format("已載入 %d 條歷史 K 線", bars.size()));
        }
    }
    
    private void exportCsvData() {
        // 獲取圖表中的數據
        List<org.ta4j.core.Bar> ta4jBars = chartDock.getCurrentBars();
        
        if (ta4jBars == null || ta4jBars.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                I18n.get("dialog.csv.export.error.no.data"),
                I18n.get("dialog.error"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 開啟匯出對話框
        CsvExportDialog dialog = new CsvExportDialog(this, ta4jBars);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            System.out.println("CSV 匯出成功: " + ta4jBars.size() + " 條 K 線");
            statusBar.setText(String.format(I18n.get("status.data.exported"), ta4jBars.size()));
        }
    }
    
    /**
     * 開啟數據源配置對話框
     */
    private void openDataSourceConfig() {
        DataSourceConfigDialog dialog = new DataSourceConfigDialog(this, dataSourceManager);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            // 用戶確認了新配置，重新連接數據源
            reconnectDataSource(dialog.getSelectedType());
        }
    }

    /**
     * 快速切換數據源
     */
    private void switchDataSource(DataSourceManager.DataSourceType type) {
        // 檢查是否需要API密鑰
        if (dataSourceManager.requiresApiKey(type) && !dataSourceManager.hasValidApiKey(type)) {
            int choice = JOptionPane.showConfirmDialog(this,
                "此數據源需要 API 密鑰，是否現在配置？",
                "需要 API 密鑰",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                openDataSourceConfig();
            }
            return;
        }

        reconnectDataSource(type);
    }

    /**
     * 重新連接數據源
     */
    private void reconnectDataSource(DataSourceManager.DataSourceType type) {
        boolean restartMonitoring = autoTradingEnabled;
        List<String> monitorSymbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();

        if (signalMonitor != null && autoTradingEnabled) {
            signalMonitor.stop();
            autoTradingEnabled = false;
        }

        unsubscribeAllWatchlistSymbols();
        // 停止當前數據源
        if (dataFeed != null && dataFeed.isConnected()) {
            dataFeed.stop();
        }

        // 切換到新數據源
        dataFeed = dataSourceManager.switchDataSource(type);
        watchlistMarketDataListeners.clear();
        watchlistLastPrices.clear();

        // 重新訂閱市場數據
        subscribeMarketData();
        subscribeWatchlistMarketData();

        // 啟動新數據源
        dataFeed.start();

        // 更新狀態欄
        initializeSignalMonitor();
        if (restartMonitoring && !monitorSymbols.isEmpty()) {
            signalMonitor.start(monitorSymbols);
            autoTradingEnabled = true;
        }

        String dataSourceName = type.getDisplayNameZh();
        statusBar.setText("已切換到: " + dataSourceName);

        JOptionPane.showMessageDialog(this,
            "已成功切換到 " + dataSourceName,
            "數據源切換",
            JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 清理資源
     */
    private void cleanup() {
        System.out.println("正在清理資源...");

        // 停止自動交易監控
        if (signalMonitor != null && autoTradingEnabled) {
            signalMonitor.stop();
            System.out.println("已停止自動交易監控");
        }

        // 停止數據源
        if (dataFeed != null) {
            dataFeed.stop();
            System.out.println("已停止數據源");
        }

        System.out.println("資源清理完成");
    }

    private void setupKeyBindings() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // Ctrl+L: Toggle Light/Dark
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "toggleTheme");
        actionMap.put("toggleTheme", new AbstractAction() {
            boolean isDark = true;
            @Override
            public void actionPerformed(ActionEvent e) {
                setTheme(!isDark);
                isDark = !isDark;
            }
        });

        // Ctrl+=: Zoom In
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        actionMap.put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chartDock.zoomIn();
            }
        });

        // Ctrl+-: Zoom Out
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "zoomOut");
        actionMap.put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chartDock.zoomOut();
            }
        });

        // Ctrl+0: Zoom Reset
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), "zoomReset");
        actionMap.put("zoomReset", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chartDock.resetZoom();
            }
        });
    }
    
    // Dockable 包裝類
    private static class DockableWrapper extends JPanel implements Dockable {
        private final String persistentID;
        private final String title;
        
        public DockableWrapper(String id, String title, JComponent content) {
            this.persistentID = id;
            this.title = title;
            setLayout(new BorderLayout());
            add(content, BorderLayout.CENTER);
        }
        
        @Override
        public String getPersistentID() {
            return persistentID;
        }
        
        @Override
        public String getTabText() {
            return title;
        }
    }
}

