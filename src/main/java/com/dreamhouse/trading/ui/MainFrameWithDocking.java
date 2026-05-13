package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.*;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.DepthLevel;
import com.dreamhouse.trading.core.model.NewsItem;
import com.dreamhouse.trading.core.model.Tick;
import com.dreamhouse.trading.core.monitor.SignalMonitorConfig;
import com.dreamhouse.trading.core.monitor.SignalMonitorService;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.execution.ExecutionEngine;
import com.dreamhouse.trading.core.execution.ExecutionMode;
import com.dreamhouse.trading.core.execution.ExecutionResult;
import com.dreamhouse.trading.core.finmind.FinMindClient;
import com.dreamhouse.trading.core.finmind.FinMindKBarSqlImporter;
import com.dreamhouse.trading.core.logging.PaperTradeRecorder;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import com.dreamhouse.trading.ui.chart.DrawingManager;
import com.dreamhouse.trading.ui.dialog.CsvExportDialog;
import com.dreamhouse.trading.ui.dialog.CsvImportDialog;
import com.dreamhouse.trading.ui.dialog.IndicatorSettingsDialog;
import com.dreamhouse.trading.ui.dialog.BacktestConfigDialog;
import com.dreamhouse.trading.ui.dialog.BacktestProgressDialog;
import com.dreamhouse.trading.ui.dialog.BacktestResultDialog;
import com.dreamhouse.trading.ui.dialog.DataSourceConfigDialog;
import com.dreamhouse.trading.core.backtest.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainFrameWithDocking extends JFrame {
    static final int DAY_TRADE_LOT_SIZE = 1000;
    static final LocalTime DAY_TRADE_FORCE_CLOSE_TIME = LocalTime.of(13, 25);
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

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
    private FinMindApiDock finMindApiDock;

    // 新增的分析與執行面板
    private MarketAnalysisDock marketAnalysisDock;
    private ModeRecommendationDock modeRecommendationDock;
    private ExecutionStatusDock executionStatusDock;
    private PaperTradeAnalysisDock paperTradeAnalysisDock;

    private String currentSymbol = "";
    private Timeframe currentTimeframe = Timeframe.M1;
    private LocalDate selectedQueryDate = LocalDate.now(TAIPEI_ZONE);
    private int customBarCount = 100;  // 用戶自定義的K線數量，預設100根
    private double lastPrice = 0;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();
    private final Map<String, MarketScanResult> latestScanResults = new HashMap<>();
    private final Map<String, Double> latestPrices = new HashMap<>();
    private final Map<String, Double> watchlistOpenPrices = new HashMap<>();
    private final Map<String, Long> watchlistVolumes = new HashMap<>();
    private final Map<String, Double> activeStopLosses = new HashMap<>();
    private final Map<String, Double> activeTakeProfits = new HashMap<>();
    private final Map<String, TradeMode> activeTradeModes = new HashMap<>();
    private final Map<String, List<Trade>> latestSqlRadarBacktestTrades = new HashMap<>();
    private final Set<String> autoManagedPositions = new java.util.HashSet<>();
    private final Map<String, LocalDateTime> stopLossCooldownUntil = new HashMap<>();
    private final Set<String> watchlistMarketSubscriptions = new java.util.HashSet<>();
    private final Set<String> pendingAutoEntries = new java.util.HashSet<>();
    private SignalMonitorService signalMonitor;
    private SignalMonitorConfig monitorConfig;
    private DecisionConfig monitorDecisionConfig;
    private Portfolio monitorPortfolio;
    private ExecutionEngine executionEngine;
    private final PaperTradeRecorder paperTradeRecorder = new PaperTradeRecorder();
    private boolean autoTradingEnabled = false;

    // 保存當前的市場數據監聽器引用，用於取消訂閱
    private MarketDataListener currentMarketDataListener;
    private MarketDataListener watchlistMarketDataListener;

    public MainFrameWithDocking() {
        setTitle(I18n.get("app.title"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);

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
        
        // 建立所有 Dock 面板
        createDockPanels();
        
        // 組裝視窗
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(dockingPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        UIAutoScaler.install(this);
        
        // 訂閱市場資料
        subscribeMarketData();
        
        // 啟動資料源
        dataFeed.start();
        subscribeWatchlistMarketData();
        scanWatchlistForOpportunitiesAsync();
        initializeSignalMonitor();
        autoStartMonitoringIfPossible();

        Timer radarBootstrapTimer = new Timer(1500, e -> scanWatchlistForOpportunitiesAsync());
        radarBootstrapTimer.setRepeats(false);
        radarBootstrapTimer.start();
        
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

        Timer forceCloseTimer = new Timer(10_000, e -> closeAutoManagedPositionsAtCutoff());
        forceCloseTimer.start();
        
        // 快捷鍵設定
        setupKeyBindings();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
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
            syncCollectorSymbolAdd(symbol);
            subscribeWatchlistSymbol(symbol);
            scanWatchlistForOpportunitiesAsync();
            restartMonitoringIfRunning();
        });
        watchlistPanel.setOnSymbolRemoved(symbol -> {
            syncCollectorSymbolRemove(symbol);
            unsubscribeWatchlistSymbol(symbol);
            latestScanResults.remove(symbol);
            latestPrices.remove(symbol);
            watchlistOpenPrices.remove(symbol);
            watchlistVolumes.remove(symbol);
            activeStopLosses.remove(symbol);
            activeTakeProfits.remove(symbol);
            activeTradeModes.remove(symbol);
            autoManagedPositions.remove(symbol);
            pendingAutoEntries.remove(symbol);
            stopLossCooldownUntil.remove(symbol);
            scanWatchlistForOpportunitiesAsync();
            restartMonitoringIfRunning();
        });
        DockableWrapper watchlistWrapper = new DockableWrapper("watchlist", I18n.get("dock.watchlist"), watchlistPanel);
        Docking.registerDockable(watchlistWrapper);
        Docking.dock(watchlistWrapper, chartWrapper, DockingRegion.WEST);

        opportunityRadarDock = new OpportunityRadarDock();
        opportunityRadarDock.setOnSymbolSelected(this::changeSymbol);
        DockableWrapper radarWrapper = new DockableWrapper("opportunityRadar", "今日機會雷達", opportunityRadarDock);
        Docking.registerDockable(radarWrapper);
        Docking.dock(radarWrapper, watchlistWrapper, DockingRegion.SOUTH);

        finMindApiDock = new FinMindApiDock(dataSourceManager, chartDock::loadHistoricalData, statusBar::setText);
        DockableWrapper finMindApiWrapper = new DockableWrapper("finMindApi", "FinMind API 查詢", finMindApiDock);
        Docking.registerDockable(finMindApiWrapper);
        Docking.dock(finMindApiWrapper, radarWrapper, DockingRegion.SOUTH);

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
        DockableWrapper executionStatusWrapper = new DockableWrapper(
            "executionStatus", "執行狀態", executionStatusDock);
        Docking.registerDockable(executionStatusWrapper);
        Docking.dock(executionStatusWrapper, modeRecommendationWrapper, DockingRegion.SOUTH);

        paperTradeAnalysisDock = new PaperTradeAnalysisDock();
        DockableWrapper paperTradeAnalysisWrapper = new DockableWrapper(
            "paperTradeAnalysis", "交易紀錄分析", paperTradeAnalysisDock);
        Docking.registerDockable(paperTradeAnalysisWrapper);
        Docking.dock(paperTradeAnalysisWrapper, executionStatusWrapper, DockingRegion.SOUTH);
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

        JMenu panelMenu = new JMenu("面板");
        addPanelMenuItems(panelMenu);
        viewMenu.add(panelMenu);
        
        menuBar.add(viewMenu);

        // Data Source Menu
        JMenu dataMenu = new JMenu(I18n.get("menu.data"));

        JMenuItem dataSourceConfig = new JMenuItem(I18n.get("menu.data.config"));
        dataSourceConfig.addActionListener(e -> openDataSourceConfig());
        dataMenu.add(dataSourceConfig);

        dataMenu.addSeparator();

        // Quick switch submenu
        JMenu switchMenu = new JMenu(I18n.get("menu.data.switch"));

        JMenuItem switchToMarketCollector = new JMenuItem("MarketDataCollector");
        switchToMarketCollector.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.MARKET_COLLECTOR));
        switchMenu.add(switchToMarketCollector);

        JMenuItem switchToFinMind = new JMenuItem(I18n.get("menu.data.switch.finmind"));
        switchToFinMind.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.FINMIND));
        switchMenu.add(switchToFinMind);

        JMenuItem switchToYahoo = new JMenuItem(I18n.get("menu.data.switch.yahoo"));
        switchToYahoo.addActionListener(e -> switchDataSource(DataSourceManager.DataSourceType.YAHOO_FINANCE));
        switchMenu.add(switchToYahoo);

        dataMenu.add(switchMenu);
        menuBar.add(dataMenu);

        // Tools Menu
        JMenu toolsMenu = new JMenu(I18n.get("menu.tools"));

        JMenuItem backtestItem = new JMenuItem(I18n.get("menu.tools.backtest"));
        backtestItem.addActionListener(e -> openBacktestDialog());
        toolsMenu.add(backtestItem);

        JMenuItem radarScanItem = new JMenuItem("批次掃描觀察清單");
        radarScanItem.addActionListener(e -> scanWatchlistForOpportunitiesAsync());
        toolsMenu.add(radarScanItem);

        JMenuItem sqlRadarBacktestItem = new JMenuItem("SQL 雷達回測（目前商品）");
        sqlRadarBacktestItem.addActionListener(e -> runSqlRadarBacktestForCurrentSymbol());
        toolsMenu.add(sqlRadarBacktestItem);

        JMenuItem batchSqlRadarBacktestItem = new JMenuItem("SQL 雷達批次回測（觀察清單）");
        batchSqlRadarBacktestItem.addActionListener(e -> runSqlRadarBacktestForWatchlist());
        toolsMenu.add(batchSqlRadarBacktestItem);

        JMenuItem monitorSettingsItem = new JMenuItem("監控門檻設定");
        monitorSettingsItem.addActionListener(e -> showMonitorSettingsDialog());
        toolsMenu.add(monitorSettingsItem);

        JMenuItem tradeAnalysisItem = new JMenuItem("交易紀錄分析");
        tradeAnalysisItem.addActionListener(e -> showDockablePanel("paperTradeAnalysis", "交易紀錄分析"));
        toolsMenu.add(tradeAnalysisItem);

        JMenuItem startMonitorItem = new JMenuItem("啟動自動偵測");
        startMonitorItem.addActionListener(e -> startAutoTrading(true));
        toolsMenu.add(startMonitorItem);

        JMenuItem stopMonitorItem = new JMenuItem("停止自動偵測");
        stopMonitorItem.addActionListener(e -> stopAutoTrading(true));
        toolsMenu.add(stopMonitorItem);

        menuBar.add(toolsMenu);

        // Layout Menu
        JMenu layoutMenu = new JMenu(I18n.get("menu.layout"));
        JMenuItem resetLayout = new JMenuItem(I18n.get("menu.layout.reset"));
        JMenuItem saveLayout = new JMenuItem(I18n.get("menu.layout.save"));
        resetLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "版面重設功能尚未實作"));
        saveLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "版面儲存功能尚未實作"));
        layoutMenu.add(resetLayout);
        layoutMenu.add(saveLayout);
        menuBar.add(layoutMenu);
        
        // Help Menu
        JMenu helpMenu = new JMenu(I18n.get("menu.help"));
        JMenuItem aboutItem = new JMenuItem(I18n.get("menu.help.about"));
        aboutItem.addActionListener(e -> showAboutDialog());
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
        JButton loadHistoryBtn = new JButton("載入日期分K到SQL");
        loadHistoryBtn.setToolTipText("使用 FinMind TaiwanStockKBar 批量下載目前日期的觀察清單分K，寫入 MarketDataCollector SQL");
        loadHistoryBtn.addActionListener(e -> importSelectedDateKBarToSql());
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

        JButton tradeAnalysisBtn = new JButton("📊 交易紀錄");
        tradeAnalysisBtn.setToolTipText("開啟 paper-trades CSV 匯入與交易流程分析");
        tradeAnalysisBtn.addActionListener(e -> showDockablePanel("paperTradeAnalysis", "交易紀錄分析"));
        toolBar.add(tradeAnalysisBtn);

        JButton sqlRadarBacktestBtn = new JButton("SQL 雷達回測");
        sqlRadarBacktestBtn.setToolTipText("使用目前商品今天 SQL K 線重跑雷達策略，並在圖表標記開倉與平倉");
        sqlRadarBacktestBtn.addActionListener(e -> runSqlRadarBacktestForCurrentSymbol());
        toolBar.add(sqlRadarBacktestBtn);

        JButton batchSqlRadarBacktestBtn = new JButton("批次雷達回測");
        batchSqlRadarBacktestBtn.setToolTipText("使用觀察清單全部股票的 SQL K 線批次重跑雷達策略");
        batchSqlRadarBacktestBtn.addActionListener(e -> runSqlRadarBacktestForWatchlist());
        toolBar.add(batchSqlRadarBacktestBtn);
        
        toolBar.addSeparator();

        JButton panelButton = new JButton("面板");
        panelButton.setToolTipText("呼叫或切換主要 UI 面板");
        panelButton.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            addPanelMenuItems(popup);
            popup.addSeparator();
            JMenuItem dataSourceItem = new JMenuItem("資料源設定");
            dataSourceItem.addActionListener(event -> openDataSourceConfig());
            popup.add(dataSourceItem);
            JMenuItem indicatorItem = new JMenuItem("指標設定");
            indicatorItem.addActionListener(event -> openIndicatorSettings());
            popup.add(indicatorItem);
            JMenuItem aboutItem = new JMenuItem("關於 DreamHouseTrading");
            aboutItem.addActionListener(event -> showAboutDialog());
            popup.add(aboutItem);
            popup.show(panelButton, 0, panelButton.getHeight());
        });
        toolBar.add(panelButton);

        return toolBar;
    }

    private void addPanelMenuItems(JMenu menu) {
        for (PanelEntry entry : panelEntries()) {
            JMenuItem item = new JMenuItem(entry.title());
            item.addActionListener(e -> showDockablePanel(entry.id(), entry.title()));
            menu.add(item);
        }
    }

    private void addPanelMenuItems(JPopupMenu popup) {
        for (PanelEntry entry : panelEntries()) {
            JMenuItem item = new JMenuItem(entry.title());
            item.addActionListener(e -> showDockablePanel(entry.id(), entry.title()));
            popup.add(item);
        }
    }

    private List<PanelEntry> panelEntries() {
        return List.of(
                new PanelEntry("chart", "主圖表"),
                new PanelEntry("watchlist", "觀察清單"),
                new PanelEntry("opportunityRadar", "今日機會雷達"),
                new PanelEntry("finMindApi", "FinMind API 查詢"),
                new PanelEntry("orderbook", "委託簿"),
                new PanelEntry("timesales", "逐筆成交"),
                new PanelEntry("news", "新聞"),
                new PanelEntry("marketAnalysis", "市場分析"),
                new PanelEntry("modeRecommendation", "模式建議"),
                new PanelEntry("executionStatus", "模擬交易狀態"),
                new PanelEntry("paperTradeAnalysis", "交易紀錄分析"));
    }

    private void showDockablePanel(String dockableId, String title) {
        try {
            Docking.display(dockableId);
            statusBar.setText("已呼叫 UI 面板：" + title);
        } catch (Exception ex) {
            statusBar.setText("無法呼叫 UI 面板：" + title + "，" + ex.getMessage());
        }
    }

    private void showAboutDialog() {
        JTextArea message = new JTextArea("""
                DreamHouseTrading

                Java Swing 台股交易分析與模擬平台
                版本：0.1.x

                目前定位：
                - 盤中行情建議使用 MarketDataCollectorFeed 讀取本機 MySQL。
                - FinMind 保留低頻資料查詢，例如分點、新聞、盤後 K 線與 API 用量。
                - Yahoo 保留為一般行情資料源。
                - 系統只做分析、掃描、決策、回測與模擬交易，不做真實券商下單。

                最近更新：
                - FinMind API 面板支援分 K 匯入圖表。
                - 分點查詢會彙總券商買進、賣出與買賣超。
                - 工具列新增面板呼叫入口。
                - 主視窗支援依尺寸自動縮放 UI 文字與表格。
                """);
        message.setEditable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(54);
        UIAutoScaler.apply(this);
        JOptionPane.showMessageDialog(this, message, "關於 DreamHouseTrading", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void subscribeMarketData() {
        // 創建新的監聽器
        currentMarketDataListener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                lastPrice = tick.getPrice();
                if (tick.getSymbol() == null
                        || !watchlistMarketSubscriptions.contains(tick.getSymbol().trim().toUpperCase(Locale.ROOT))) {
                    handleMarketPriceUpdate(tick);
                }
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
        if (usesUnifiedMarketUpdates()) {
            subscribeWatchlistMarketData();
        }
    }

    private boolean usesUnifiedMarketUpdates() {
        DataSourceManager.DataSourceType type = dataSourceManager.getCurrentType();
        return type == DataSourceManager.DataSourceType.MARKET_COLLECTOR
                || type == DataSourceManager.DataSourceType.FINMIND;
    }

    private boolean isFinMindDataSource() {
        return dataSourceManager.getCurrentType() == DataSourceManager.DataSourceType.FINMIND;
    }

    private void subscribeWatchlistMarketData() {
        if (watchlistPanel == null) {
            return;
        }
        if (isFinMindDataSource()) {
            statusBar.setText("FinMind 資料源不訂閱觀察清單自動更新，避免 API 用量增加；請切換 MarketDataCollector。");
            return;
        }
        ensureWatchlistMarketDataListener();
        for (String watchSymbol : watchlistPanel.getSymbols()) {
            subscribeWatchlistSymbol(watchSymbol);
        }
    }

    private void ensureWatchlistMarketDataListener() {
        if (watchlistMarketDataListener != null) {
            return;
        }
        watchlistMarketDataListener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                handleMarketPriceUpdate(tick);
            }
        };
    }

    private void syncCollectorSymbolAdd(String symbol) {
        if (MarketDataCollectorSymbolSync.addSymbol(symbol)) {
            statusBar.setText("已同步新增到 MarketDataCollector 監控清單：" + symbol);
        } else {
            statusBar.setText("觀察清單已新增，但 MarketDataCollector symbols.properties 未同步：" + symbol);
        }
    }

    private void syncCollectorSymbolRemove(String symbol) {
        if (MarketDataCollectorSymbolSync.removeSymbol(symbol)) {
            statusBar.setText("已同步移除 MarketDataCollector 監控清單：" + symbol);
        } else {
            statusBar.setText("觀察清單已移除；MarketDataCollector 監控清單未找到或未同步：" + symbol);
        }
    }

    private void subscribeWatchlistSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || dataFeed == null) {
            return;
        }
        ensureWatchlistMarketDataListener();
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (watchlistMarketSubscriptions.add(normalized)) {
            dataFeed.subscribe(normalized, watchlistMarketDataListener);
        }
    }

    private void unsubscribeWatchlistSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || dataFeed == null || watchlistMarketDataListener == null) {
            return;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (watchlistMarketSubscriptions.remove(normalized)) {
            dataFeed.unsubscribe(normalized, watchlistMarketDataListener);
        }
    }

    private void unsubscribeAllWatchlistMarketData() {
        if (dataFeed == null || watchlistMarketDataListener == null || watchlistMarketSubscriptions.isEmpty()) {
            return;
        }
        for (String symbol : new ArrayList<>(watchlistMarketSubscriptions)) {
            dataFeed.unsubscribe(symbol, watchlistMarketDataListener);
        }
        watchlistMarketSubscriptions.clear();
    }

    private void handleMarketPriceUpdate(Tick tick) {
        if (tick == null || tick.getSymbol() == null || tick.getSymbol().isBlank() || tick.getPrice() <= 0.0) {
            return;
        }
        String symbol = tick.getSymbol();
        double price = tick.getPrice();
        latestPrices.put(symbol, price);
        paperTradeRecorder.recordMarketPrice(symbol, price, tick.getTimestamp());

        double openPrice = watchlistOpenPrices.computeIfAbsent(symbol, ignored -> price);
        long volume = resolveWatchlistVolume(tick);
        watchlistVolumes.put(symbol, volume);
        double changePct = openPrice > 0.0 ? ((price - openPrice) / openPrice) * 100.0 : 0.0;

        SwingUtilities.invokeLater(() -> {
            if (watchlistPanel != null) {
                watchlistPanel.updateItem(symbol, price, changePct, volume);
            }
            if (executionStatusDock != null) {
                executionStatusDock.updateMarketPrice(symbol, price);
            }
            if (monitorPortfolio != null) {
                monitorPortfolio.updateMarketValue(price);
            }
            closeAutoManagedPositionsAtCutoff();
            closeAutoPositionIfStopTriggered(symbol, price);
        });
    }

    private void closeAutoManagedPositionsAtCutoff() {
        if (!autoTradingEnabled || executionEngine == null || monitorPortfolio == null
                || !isDayTradeForceCloseTime(LocalTime.now(TAIPEI_ZONE))) {
            return;
        }

        List<Position> positions = new ArrayList<>(monitorPortfolio.getPositions());
        for (Position position : positions) {
            String symbol = position.getSymbol();
            if (!autoManagedPositions.contains(symbol) || position.getQuantity() <= 0) {
                continue;
            }

            double closePrice = getCurrentPrice(symbol);
            if (closePrice <= 0.0) {
                statusBar.setText("13:25 當沖強制平倉失敗，缺少最新價格：" + symbol);
                continue;
            }

            String reason = "13:25 自動監控強制平倉";
            ExecutionResult result = executionEngine.closePosition(
                    symbol,
                    position.getQuantity(),
                    closePrice,
                    com.dreamhouse.trading.core.execution.OrderType.MARKET,
                    reason);
            paperTradeRecorder.record(result, null, latestScanResults.get(symbol), "day-trade-cutoff");
            activeStopLosses.remove(symbol);
            activeTakeProfits.remove(symbol);
            activeTradeModes.remove(symbol);
            autoManagedPositions.remove(symbol);
            pendingAutoEntries.remove(symbol);
            if (executionStatusDock != null) {
                executionStatusDock.setExecutionEngine(executionEngine);
                executionStatusDock.updateMarketPrice(symbol, closePrice);
            }
            statusBar.setText(result.isSuccess()
                    ? reason + "：" + symbol + " @ " + String.format("%.2f", closePrice)
                    : reason + "失敗：" + symbol + "，" + result.getMessage());
        }
    }

    private void closeAutoPositionIfStopTriggered(String symbol, double price) {
        if (!autoTradingEnabled || executionEngine == null || monitorPortfolio == null) {
            return;
        }
        Position position = monitorPortfolio.getPosition(symbol);
        if (position == null || position.getQuantity() <= 0) {
            activeStopLosses.remove(symbol);
            activeTakeProfits.remove(symbol);
            activeTradeModes.remove(symbol);
            autoManagedPositions.remove(symbol);
            pendingAutoEntries.remove(symbol);
            return;
        }

        Double stopLoss = activeStopLosses.get(symbol);
        Double takeProfit = activeTakeProfits.get(symbol);
        String reason = null;
        if (stopLoss != null && stopLoss > 0.0 && price <= stopLoss) {
            reason = String.format("停損觸發：最新價 %.2f <= 停損 %.2f", price, stopLoss);
        } else if (takeProfit != null && takeProfit > 0.0 && price >= takeProfit) {
            reason = String.format("停利觸發：最新價 %.2f >= 停利 %.2f", price, takeProfit);
        }

        if (reason == null) {
            return;
        }

        boolean stopLossTriggered = stopLoss != null && stopLoss > 0.0 && price <= stopLoss;
        ExecutionResult result = executionEngine.closePosition(
                symbol,
                position.getQuantity(),
                price,
                com.dreamhouse.trading.core.execution.OrderType.MARKET,
                reason);
        paperTradeRecorder.record(result, null, latestScanResults.get(symbol), "auto-stop");
        activeStopLosses.remove(symbol);
        activeTakeProfits.remove(symbol);
        activeTradeModes.remove(symbol);
        autoManagedPositions.remove(symbol);
        pendingAutoEntries.remove(symbol);
        if (result.isSuccess() && stopLossTriggered) {
            registerStopLossCooldown(symbol);
        }
        if (executionStatusDock != null) {
            executionStatusDock.setExecutionEngine(executionEngine);
            executionStatusDock.updateMarketPrice(symbol, price);
        }
        if (result.isSuccess()) {
            statusBar.setText(reason + "，已自動平倉 " + symbol);
        } else {
            statusBar.setText("自動平倉失敗 " + symbol + "：" + result.getMessage());
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
        MarketScanResult scanResult = latestScanResults.get(symbol);
        if (scanResult != null && marketAnalysisDock != null) {
            marketAnalysisDock.updateScanResult(scanResult);
        }
        chartDock.setCurrentSymbol(symbol);  // ⭐ 設置圖表的當前商品（用於觀察清單更新）
        statusBar.setSymbol(symbol);
        statusBar.setText("正在載入 " + symbol + " " + currentTimeframe.getLabel() + " 歷史數據...");

        // 訂閱新商品
        subscribeMarketData();

        if (isFinMindDataSource()) {
            statusBar.setText("FinMind 資料源不自動載入圖表 K 線，避免 API 用量增加；請使用 FinMind API 面板手動查詢。");
            return;
        }

        // 在背景執行緒中加載歷史數據，使用當前週期
        new Thread(() -> {
            try {
                // 確保subscribe完成後再加載歷史數據
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始加載 " + symbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 歷史數據");
                loadChartData(symbol, currentTimeframe, customBarCount);
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

        if (isFinMindDataSource()) {
            statusBar.setText("FinMind 資料源不自動載入圖表 K 線，避免 API 用量增加；請使用 FinMind API 面板手動查詢。");
            return;
        }

        // 在背景執行緒中重新加載當前商品的歷史數據，使用新的週期
        new Thread(() -> {
            try {
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始重新加載 " + currentSymbol + " " + customBarCount + " 根 " + tf.getLabel() + " 歷史數據");
                loadChartData(currentSymbol, tf, customBarCount);
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

        if (isFinMindDataSource()) {
            statusBar.setText("FinMind 資料源不自動載入圖表 K 線，避免 API 用量增加；請使用 FinMind API 面板手動查詢。");
            return;
        }

        // 在背景執行緒中重新加載數據
        new Thread(() -> {
            try {
                Thread.sleep(50);

                System.out.println("[MainFrame] 開始加載 " + currentSymbol + " " + customBarCount + " 根 " + currentTimeframe.getLabel() + " 歷史數據");
                loadChartData(currentSymbol, currentTimeframe, customBarCount);
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
    private void loadChartData(String symbol, Timeframe timeframe, int barCount) {
        if (dataFeed instanceof MarketDataCollectorFeed) {
            List<Bar> bars = ((MarketDataCollectorFeed) dataFeed)
                    .fetchHistoricalBars(symbol, timeframe, barCount, selectedQueryDate);
            SwingUtilities.invokeLater(() -> {
                if (symbol != null && symbol.equals(currentSymbol) && timeframe == currentTimeframe) {
                    chartDock.loadHistoricalData(bars);
                    showSqlRadarBacktestMarkersFor(symbol);
                    statusBar.setText(symbol + " " + timeframe.getLabel() + " 數據載入完成");
                }
            });
            return;
        }
        dataFeed.loadHistoricalData(symbol, timeframe, barCount);
    }

    private void changeDateQuery(java.time.LocalDate date) {
        selectedQueryDate = date != null ? date : LocalDate.now(TAIPEI_ZONE);
        System.out.println("[MainFrame] 切換查詢日期: " + date);

        // 如果當前數據源是 FinMindFeed，設置查詢日期
        if (dataFeed instanceof com.dreamhouse.trading.core.FinMindFeed) {
            ((com.dreamhouse.trading.core.FinMindFeed) dataFeed).setQueryDate(selectedQueryDate);
            statusBar.setText("已切換至 " + selectedQueryDate + " 的數據；可按「載入日期分K到SQL」批量匯入觀察清單");
        } else if (dataFeed instanceof MarketDataCollectorFeed) {
            statusBar.setText("已切換 SQL K 線查詢日期：" + selectedQueryDate);
            updateWatchlistForSelectedSqlDate();
            if (currentSymbol != null && !currentSymbol.isBlank()) {
                chartDock.clearAllData();
                new Thread(() -> loadChartData(currentSymbol, currentTimeframe, customBarCount),
                        "SqlDateChartLoader-" + selectedQueryDate).start();
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "日期查詢功能支援 FinMind 與 MarketDataCollector 數據源",
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

    private void importSelectedDateKBarToSql() {
        System.out.println("[MainFrame] 手動觸發 FinMind 分K匯入 SQL，日期: " + selectedQueryDate);

        if (!(dataFeed instanceof com.dreamhouse.trading.core.FinMindFeed)) {
            JOptionPane.showMessageDialog(this,
                    "日期分K匯入需要使用 FinMind API。\n請先切換至 FinMind 數據源，再按「載入日期分K到SQL」。",
                    "FinMind API 匯入",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> symbols = resolveImportSymbols();
        if (symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "觀察清單沒有股票，也沒有目前商品可匯入。",
                    "無可匯入股票",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "將使用 FinMind TaiwanStockKBar 匯入 " + selectedQueryDate + " 的分K。\n"
                        + "股票數：" + symbols.size() + "\n"
                        + "API 呼叫：約 " + symbols.size() + " 次（每檔股票一次）\n"
                        + "寫入：market_data.candlesticks 的 M1/M5/M15/M30/H1\n\n"
                        + "是否開始？",
                "批量匯入分K到SQL",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        statusBar.setText("正在從 FinMind 匯入 " + selectedQueryDate + " 分K到 SQL...");
        SwingWorker<FinMindKBarSqlImporter.ImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FinMindKBarSqlImporter.ImportResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    FinMindKBarSqlImporter importer = new FinMindKBarSqlImporter(
                            new FinMindClient(dataSourceManager.getFinMindApiToken()),
                            repository);
                    return importer.importSymbols(symbols, selectedQueryDate);
                }
            }

            @Override
            protected void done() {
                try {
                    FinMindKBarSqlImporter.ImportResult result = get();
                    showFinMindKBarImportResult(result);
                    loadImportedCurrentSymbolBars(result);
                } catch (Exception e) {
                    String message = rootCauseMessage(e);
                    statusBar.setText("FinMind 分K匯入失敗：" + message);
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                            "FinMind 分K匯入 SQL 失敗：\n" + message + "\n\n"
                                    + "請確認 FinMind sponsor 權限、API quota、MarketDataCollector MySQL 連線。",
                            "匯入失敗",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private List<String> resolveImportSymbols() {
        List<String> symbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();
        List<String> normalized = new ArrayList<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank() && !normalized.contains(symbol.trim())) {
                normalized.add(symbol.trim());
            }
        }
        if (normalized.isEmpty() && currentSymbol != null && !currentSymbol.isBlank()) {
            normalized.add(currentSymbol.trim());
        }
        return normalized;
    }

    private void showFinMindKBarImportResult(FinMindKBarSqlImporter.ImportResult result) {
        statusBar.setText("FinMind 分K匯入完成：" + result.successSymbols() + "/" + result.requestedSymbols()
                + " 檔成功，寫入 " + result.totalInsertedBars() + " 根K線");

        StringBuilder detail = new StringBuilder();
        detail.append("日期：").append(result.date()).append('\n');
        detail.append("成功：").append(result.successSymbols()).append(" / ").append(result.requestedSymbols()).append(" 檔\n");
        detail.append("寫入K線：").append(result.totalInsertedBars()).append(" 根\n\n");
        for (FinMindKBarSqlImporter.SymbolImportResult item : result.symbolResults()) {
            detail.append(item.success() ? "OK " : "失敗 ")
                    .append(item.symbol())
                    .append("：");
            if (item.success()) {
                detail.append(item.insertedByInterval()).append('\n');
            } else {
                detail.append(item.message()).append('\n');
            }
        }

        JTextArea area = new JTextArea(detail.toString(), 18, 64);
        area.setEditable(false);
        area.setLineWrap(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "FinMind 分K匯入結果", JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadImportedCurrentSymbolBars(FinMindKBarSqlImporter.ImportResult result) {
        if (currentSymbol == null || currentSymbol.isBlank()
                || result.symbolResults().stream().noneMatch(item -> currentSymbol.equals(item.symbol()) && item.success())) {
            return;
        }
        try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                dataSourceManager.getMarketCollectorJdbcUrl(),
                dataSourceManager.getMarketCollectorUser(),
                dataSourceManager.getMarketCollectorPassword())) {
            String interval = switch (currentTimeframe) {
                case M1 -> "M1";
                case M5 -> "M5";
                case M15 -> "M15";
                case M30 -> "M30";
                case H1 -> "H1";
                default -> null;
            };
            if (interval == null) {
                return;
            }
            List<Bar> bars = repository.findCandlesByTimeRange(
                    currentSymbol,
                    interval,
                    selectedQueryDate.atTime(9, 0),
                    selectedQueryDate.atTime(13, 30));
            if (!bars.isEmpty()) {
                chartDock.loadHistoricalData(bars);
            }
        } catch (Exception e) {
            statusBar.setText("分K已匯入，但載入圖表失敗：" + e.getMessage());
        }
    }

    private void updateWatchlistForSelectedSqlDate() {
        if (watchlistPanel == null) {
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        SwingWorker<Void, WatchlistSnapshot> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    for (String symbol : symbols) {
                        if (symbol == null || symbol.isBlank()) {
                            continue;
                        }
                        List<Bar> bars = sqlFeed.fetchHistoricalBars(symbol, Timeframe.M1, 1000, selectedQueryDate);
                        publish(toWatchlistSnapshot(symbol, bars));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<WatchlistSnapshot> chunks) {
                for (WatchlistSnapshot snapshot : chunks) {
                    watchlistPanel.updateItem(snapshot.symbol(), snapshot.last(), snapshot.changePct(), snapshot.volume());
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusBar.setText("觀察清單已切換為 SQL 日期：" + selectedQueryDate);
                } catch (Exception e) {
                    statusBar.setText("觀察清單日期更新失敗：" + rootCauseMessage(e));
                }
            }
        };
        worker.execute();
    }

    private WatchlistSnapshot toWatchlistSnapshot(String symbol, List<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return new WatchlistSnapshot(symbol, 0.0, 0.0, 0L);
        }
        Bar first = bars.get(0);
        Bar last = bars.get(bars.size() - 1);
        double changePct = first.getOpen() > 0.0
                ? ((last.getClose() - first.getOpen()) / first.getOpen()) * 100.0
                : 0.0;
        long volume = bars.stream().mapToLong(Bar::getVolume).sum();
        return new WatchlistSnapshot(symbol, last.getClose(), changePct, volume);
    }

    private String rootCauseMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }

    private void scanWatchlistForOpportunitiesAsync() {
        if (watchlistPanel == null || opportunityRadarDock == null) {
            return;
        }

        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            opportunityRadarDock.clearResults();
            statusBar.setText("觀察清單為空");
            return;
        }

        statusBar.setText("正在批次掃描觀察清單...");
        if (isFinMindDataSource()) {
            opportunityRadarDock.clearResults();
            statusBar.setText("FinMind 資料源不執行自動雷達掃描，避免 API 用量增加；請切換 MarketDataCollector。");
            return;
        }

        SwingWorker<List<MarketScanResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MarketScanResult> doInBackground() {
                MarketScannerService scannerService = new MarketScannerService(dataFeed);
                List<MarketScanResult> results = new ArrayList<>();
                for (String symbol : symbols) {
                    MarketScanResult bestResult = findBestRadarCandidate(scannerService, symbol);
                    if (bestResult != null) {
                        results.add(bestResult);
                    }
                }
                results.sort(Comparator.naturalOrder());
                return results;
            }

            @Override
            protected void done() {
                try {
                    List<MarketScanResult> results = get();
                    if (results.isEmpty()) {
                        latestScanResults.clear();
                        opportunityRadarDock.clearResults();
                        statusBar.setText("本輪掃描沒有候選標的");
                        return;
                    }
                    latestScanResults.clear();
                    for (MarketScanResult result : results) {
                        latestScanResults.put(result.getSymbol(), result);
                        if (watchlistPanel != null) {
                            watchlistPanel.updateScanResult(result);
                        }
                    }
                    opportunityRadarDock.updateScanResults(results);
                    if (marketAnalysisDock != null) {
                        MarketScanResult selected = latestScanResults.get(currentSymbol);
                        if (selected == null) {
                            selected = results.get(0);
                        }
                        marketAnalysisDock.updateScanResult(selected);
                    }
                    statusBar.setText("今日機會雷達已更新，共 " + results.size() + " 檔");
                } catch (Exception e) {
                    latestScanResults.clear();
                    opportunityRadarDock.clearResults();
                    statusBar.setText("批次掃描失敗：" + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private MarketScanResult findBestRadarCandidate(MarketScannerService scannerService, String symbol) {
        MarketScanResult best = null;
        for (TradeMode mode : List.of(TradeMode.DAY_TRADE)) {
            MarketScanResult candidate = scannerService.scan(symbol, createRadarScanRequest(mode));
            if (candidate == null) {
                continue;
            }
            if (best == null || compareRadarCandidates(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private int compareRadarCandidates(MarketScanResult left, MarketScanResult right) {
        int actionableCompare = Boolean.compare(left.hasTradeSignal(), right.hasTradeSignal());
        if (actionableCompare != 0) {
            return actionableCompare;
        }
        int scoreCompare = Double.compare(left.getScore(), right.getScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return Double.compare(left.getConfidence(), right.getConfidence());
    }

    private MarketScannerService.ScanRequest createRadarScanRequest(TradeMode mode) {
        return MarketScannerService.ScanRequest.createDefault()
            .tradeMode(mode)
            .timeframe(resolveRadarTimeframe(mode))
            .barCount(resolveRadarBarCount(mode))
            .decisionConfig(resolveRadarDecisionConfig(mode))
            .radarStrategyConfig(monitorConfig != null
                    ? monitorConfig.getRadarStrategyConfig()
                    : RadarStrategyConfig.createDefault());
    }

    private void runSqlRadarBacktestForCurrentSymbol() {
        String symbol = currentSymbol;
        if (symbol == null || symbol.isBlank()) {
            JOptionPane.showMessageDialog(this, "請先選擇要回測的股票。", "SQL 雷達回測", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Timeframe timeframe = resolveRadarTimeframe(TradeMode.DAY_TRADE);
        MarketScannerService.ScanRequest request = createRadarScanRequest(TradeMode.DAY_TRADE)
                .initialCapital(1_000_000.0);
        statusBar.setText("SQL 雷達回測執行中：" + selectedQueryDate + " " + symbol);

        SwingWorker<BacktestResult, Void> worker = new SwingWorker<>() {
            private List<Bar> replayBars = List.of();

            @Override
            protected BacktestResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    replayBars = sqlFeed.fetchHistoricalBars(symbol, timeframe, 1000, selectedQueryDate);
                }
                if (replayBars == null || replayBars.size() < 2) {
                    throw new IllegalStateException("SQL K 線資料不足，無法回測：" + symbol + " " + selectedQueryDate);
                }
                RadarReplayBacktestService service = new RadarReplayBacktestService(
                        1_000_000.0,
                        0.001425,
                        monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                                ? monitorConfig.getEarlyEntryBlockStart()
                                : LocalTime.of(0, 0),
                        monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                                ? monitorConfig.getEarlyEntryBlockEnd()
                                : LocalTime.of(0, 0),
                        monitorConfig != null
                                ? monitorConfig.getLatestAutoEntryTime()
                                : LocalTime.of(13, 10),
                        monitorConfig != null && monitorConfig.isStopLossCooldownEnabled()
                                ? monitorConfig.getStopLossCooldownMinutes()
                                : 0);
                return service.replay(symbol, replayBars, request);
            }

            @Override
            protected void done() {
                try {
                    BacktestResult result = get();
                    latestSqlRadarBacktestTrades.put(symbol, result.getTrades());
                    chartDock.loadHistoricalData(replayBars);
                    SwingUtilities.invokeLater(() -> showSqlRadarBacktestMarkersFor(symbol));
                    BacktestResultDialog dialog = new BacktestResultDialog(
                            MainFrameWithDocking.this,
                            result,
                            "SQL 雷達回測-" + symbol + "-" + selectedQueryDate,
                            buildDayTradeBacktestConfigSummary("目前商品", 1, timeframe));
                    dialog.setVisible(true);
                    statusBar.setText(String.format(
                            "SQL 雷達回測完成：%s，標記 %d 筆交易",
                            symbol,
                            result.getTrades().size()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusBar.setText("SQL 雷達回測失敗：" + cause.getMessage());
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "SQL 雷達回測失敗：\n" + cause.getMessage(),
                            "SQL 雷達回測",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void runSqlRadarBacktestForWatchlist() {
        if (watchlistPanel == null) {
            JOptionPane.showMessageDialog(this, "觀察清單尚未初始化。", "SQL 雷達批次回測", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "觀察清單沒有股票可回測。", "SQL 雷達批次回測", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Timeframe timeframe = resolveRadarTimeframe(TradeMode.DAY_TRADE);
        MarketScannerService.ScanRequest request = createRadarScanRequest(TradeMode.DAY_TRADE)
                .initialCapital(1_000_000.0);
        statusBar.setText("SQL 雷達批次回測執行中：" + selectedQueryDate + "，" + symbols.size() + " 檔");

        SwingWorker<BacktestResult, Void> worker = new SwingWorker<>() {
            private final Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
            private final List<String> skipped = new ArrayList<>();

            @Override
            protected BacktestResult doInBackground() throws Exception {
                RadarReplayBacktestService service = createRadarReplayService();
                List<BacktestResult> symbolResults = new ArrayList<>();
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    for (String symbol : symbols) {
                        if (symbol == null || symbol.isBlank()) {
                            continue;
                        }
                        try {
                            List<Bar> bars = sqlFeed.fetchHistoricalBars(symbol, timeframe, 1000, selectedQueryDate);
                            if (bars == null || bars.size() < 2) {
                                skipped.add(symbol + "：SQL K 線不足");
                                continue;
                            }
                            BacktestResult result = service.replay(symbol, bars, request);
                            symbolResults.add(result);
                            tradesBySymbol.put(symbol, result.getTrades());
                        } catch (Exception e) {
                            skipped.add(symbol + "：" + e.getMessage());
                        }
                    }
                }
                if (symbolResults.isEmpty()) {
                    throw new IllegalStateException("觀察清單沒有可回測的 SQL K 線資料：" + selectedQueryDate);
                }
                return combineBacktestResults(symbolResults);
            }

            @Override
            protected void done() {
                try {
                    BacktestResult combined = get();
                    latestSqlRadarBacktestTrades.clear();
                    latestSqlRadarBacktestTrades.putAll(tradesBySymbol);
                    showSqlRadarBacktestMarkersFor(currentSymbol);

                    BacktestResultDialog dialog = new BacktestResultDialog(
                            MainFrameWithDocking.this,
                            combined,
                            "SQL 雷達批次回測-" + selectedQueryDate,
                            buildDayTradeBacktestConfigSummary("觀察清單批次", tradesBySymbol.size(), timeframe));
                    dialog.setVisible(true);
                    String skipText = skipped.isEmpty() ? "" : "，略過 " + skipped.size() + " 檔";
                    statusBar.setText(String.format(
                            "SQL 雷達批次回測完成：%d 檔有結果，總交易 %d 筆%s",
                            tradesBySymbol.size(),
                            combined.getTrades().size(),
                            skipText));
                    if (!skipped.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                MainFrameWithDocking.this,
                                "以下股票略過：\n" + String.join("\n", skipped),
                                "SQL 雷達批次回測",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusBar.setText("SQL 雷達批次回測失敗：" + cause.getMessage());
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "SQL 雷達批次回測失敗：\n" + cause.getMessage(),
                            "SQL 雷達批次回測",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private RadarReplayBacktestService createRadarReplayService() {
        return new RadarReplayBacktestService(
                1_000_000.0,
                0.001425,
                monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                        ? monitorConfig.getEarlyEntryBlockStart()
                        : LocalTime.of(0, 0),
                monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                        ? monitorConfig.getEarlyEntryBlockEnd()
                        : LocalTime.of(0, 0),
                monitorConfig != null
                        ? monitorConfig.getLatestAutoEntryTime()
                        : LocalTime.of(13, 10),
                monitorConfig != null && monitorConfig.isStopLossCooldownEnabled()
                        ? monitorConfig.getStopLossCooldownMinutes()
                        : 0);
    }

    private String buildDayTradeBacktestConfigSummary(String scope, int symbolCount, Timeframe timeframe) {
        SignalMonitorConfig activeMonitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDefault();
        RadarStrategyConfig radar = activeMonitorConfig.getRadarStrategyConfig() != null
                ? activeMonitorConfig.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        StringBuilder sb = new StringBuilder();
        sb.append("回測範圍: ").append(scope).append('\n');
        sb.append("資料日期: ").append(selectedQueryDate).append('\n');
        sb.append("回測檔數: ").append(Math.max(1, symbolCount)).append('\n');
        sb.append("資料源: MarketDataCollector SQL\n");
        sb.append("回測週期: ").append(timeframe != null ? timeframe.getLabel() : radar.getDayTradeTimeframe().getLabel()).append('\n');
        sb.append("交易模式: DAY_TRADE（盤中雷達回測強制當沖）\n");
        sb.append("每筆下單: ").append(DAY_TRADE_LOT_SIZE).append(" 股（1 張）\n");
        sb.append("每檔初始資金: 1000000.00\n");
        sb.append("手續費率: 0.001425\n");
        sb.append("13:25 強制平倉: 啟用\n");
        sb.append("13:25 後禁止開倉: 啟用\n");
        sb.append("早盤禁開倉: ")
                .append(activeMonitorConfig.isEarlyEntryBlockEnabled() ? "啟用" : "停用");
        if (activeMonitorConfig.isEarlyEntryBlockEnabled()) {
            sb.append("（")
                    .append(activeMonitorConfig.getEarlyEntryBlockStart())
                    .append(" ~ ")
                    .append(activeMonitorConfig.getEarlyEntryBlockEnd())
                    .append("）");
        }
        sb.append('\n');
        sb.append("停損後冷卻: ")
                .append(activeMonitorConfig.isStopLossCooldownEnabled() ? "啟用 " + activeMonitorConfig.getStopLossCooldownMinutes() + " 分鐘" : "停用")
                .append('\n');
        sb.append("雷達掃描間隔: ").append(activeMonitorConfig.getScanIntervalSeconds()).append(" 秒\n");
        sb.append("同股訊號間隔: ").append(activeMonitorConfig.getMinSignalIntervalMinutes()).append(" 分鐘\n");
        sb.append("雷達日內K棒數: ").append(radar.getDayTradeBarCount()).append('\n');
        sb.append("RSI: ")
                .append(radar.isRsiEnabled() ? "啟用" : "停用")
                .append("，週期=").append(radar.getRsiPeriod())
                .append("，超賣=").append(String.format(Locale.US, "%.2f", radar.getRsiOversold()))
                .append("，超買=").append(String.format(Locale.US, "%.2f", radar.getRsiOverbought()))
                .append("，權重=").append(String.format(Locale.US, "%.2f", radar.getRsiWeight()))
                .append('\n');
        sb.append("均線: ")
                .append(radar.isMovingAverageEnabled() ? "啟用" : "停用")
                .append("，類型=").append(radar.getMovingAverageType())
                .append("，快線=").append(radar.getFastMovingAveragePeriod())
                .append("，慢線=").append(radar.getSlowMovingAveragePeriod())
                .append("，權重=").append(String.format(Locale.US, "%.2f", radar.getMovingAverageWeight()))
                .append('\n');
        sb.append("量能突破: ")
                .append(radar.isVolumeBreakoutEnabled() ? "啟用" : "停用")
                .append("，回看K棒=").append(radar.getBreakoutLookbackBars())
                .append("，量能倍數=").append(String.format(Locale.US, "%.2f", radar.getVolumeMultiplier()))
                .append("，權重=").append(String.format(Locale.US, "%.2f", radar.getVolumeBreakoutWeight()))
                .append('\n');
        sb.append("RSI接刀確認: ")
                .append(radar.isRequireRsiEntryConfirmation() ? "啟用（需EMA或放量反轉確認）" : "停用")
                .append('\n');
        sb.append("尾盤禁止新倉時間: ").append(activeMonitorConfig.getLatestAutoEntryTime()).append('\n');
        sb.append("雷達最低進場分數: ").append(String.format(Locale.US, "%.2f", radar.getMinimumEntryScore())).append('\n');
        sb.append("量能突破 RSI 超買衝突過濾: ")
                .append(radar.isBlockBreakoutOnRsiOverbought() ? "啟用" : "停用")
                .append('\n');
        sb.append("量能突破價格延續確認: ")
                .append(radar.isRequireBreakoutContinuation() ? "啟用" : "停用")
                .append('\n');
        sb.append("做多需站上 VWAP: ")
                .append(radar.isRequirePriceAboveVwapForLong() ? "啟用" : "停用")
                .append('\n');
        sb.append("突破後一根確認: ")
                .append(radar.isRequireBreakoutNextBarConfirmation() ? "啟用" : "停用")
                .append('\n');
        sb.append("追價限制（近低漲幅）: ")
                .append(radar.getMaxEntryRiseFromRecentLowPercent() > 0.0
                        ? String.format(Locale.US, "%.2f%%", radar.getMaxEntryRiseFromRecentLowPercent() * 100.0)
                        : "停用")
                .append('\n');
        return sb.toString();
    }

    private BacktestResult combineBacktestResults(List<BacktestResult> results) {
        LocalDateTime start = results.stream()
                .map(BacktestResult::getStartDate)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime end = results.stream()
                .map(BacktestResult::getEndDate)
                .max(LocalDateTime::compareTo)
                .orElse(start);
        double initialCapital = 1_000_000.0 * Math.max(1, results.size());
        BacktestResult combined = new BacktestResult(start, end, initialCapital);
        double finalValue = 0.0;
        for (BacktestResult result : results) {
            finalValue += result.getFinalValue();
            for (Trade trade : result.getTrades()) {
                combined.addTrade(trade);
            }
        }
        combined.addSnapshot(end, finalValue, finalValue, 0.0, 0);
        combined.calculate();
        return combined;
    }

    private void showSqlRadarBacktestMarkersFor(String symbol) {
        if (symbol == null || symbol.isBlank() || latestSqlRadarBacktestTrades.isEmpty()) {
            return;
        }
        List<Trade> trades = latestSqlRadarBacktestTrades.get(symbol);
        if (trades == null || trades.isEmpty()) {
            chartDock.clearTradeMarkers();
            return;
        }
        chartDock.showTradeMarkers(trades);
    }

    private Timeframe resolveRadarTimeframe(TradeMode mode) {
        if (monitorConfig != null && monitorConfig.getRadarStrategyConfig() != null) {
            return monitorConfig.getRadarStrategyConfig().resolveTimeframe(mode);
        }
        return switch (mode) {
            case DAY_TRADE -> Timeframe.M5;
            case SHORT_SWING -> Timeframe.M15;
            case SWING_TRADE -> Timeframe.H1;
            default -> currentTimeframe;
        };
    }

    private int resolveRadarBarCount(TradeMode mode) {
        if (monitorConfig != null && monitorConfig.getRadarStrategyConfig() != null) {
            return monitorConfig.getRadarStrategyConfig().resolveBarCount(mode);
        }
        return switch (mode) {
            case DAY_TRADE -> Math.max(customBarCount, 120);
            case SHORT_SWING -> Math.max(customBarCount, 160);
            case SWING_TRADE -> Math.max(customBarCount, 240);
            default -> Math.max(customBarCount, 100);
        };
    }

    private DecisionConfig resolveRadarDecisionConfig(TradeMode mode) {
        return switch (mode) {
            case DAY_TRADE -> monitorDecisionConfig != null ? monitorDecisionConfig : createAggressiveMonitorConfig();
            case SHORT_SWING -> createAggressiveMonitorConfig();
            case SWING_TRADE -> createBalancedMonitorConfig();
            default -> DecisionConfig.createDefault();
        };
    }

    private void initializeSignalMonitor() {
        monitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createBalancedTemplate();
        monitorDecisionConfig = monitorDecisionConfig != null
                ? monitorDecisionConfig
                : createBalancedMonitorConfig();
        monitorPortfolio = monitorPortfolio != null ? monitorPortfolio : new Portfolio(1_000_000.0);
        executionEngine = new ExecutionEngine(ExecutionMode.PAPER_TRADING, monitorPortfolio, 0.001425);
        if (executionStatusDock != null) {
            executionStatusDock.setExecutionEngine(executionEngine);
        }

        signalMonitor = new SignalMonitorService(dataFeed, monitorConfig, monitorDecisionConfig);
        signalMonitor.setOnScanResults(this::updateScanResultsOnUi);
        signalMonitor.setOnSignalDetected(this::handleTradingSignal);
        signalMonitor.setOnStatusUpdate(message -> SwingUtilities.invokeLater(() -> statusBar.setText(message)));
    }

    private void autoStartMonitoringIfPossible() {
        if (watchlistPanel == null) {
            return;
        }
        if (isFinMindDataSource()) {
            statusBar.setText("FinMind 資料源不自動啟動雷達監控，避免 API 用量增加。");
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        startAutoTrading(false);
    }

    private void restartMonitoringIfRunning() {
        if (!autoTradingEnabled || watchlistPanel == null) {
            return;
        }
        if (isFinMindDataSource()) {
            if (signalMonitor != null) {
                signalMonitor.stop();
            }
            autoTradingEnabled = false;
            statusBar.setText("FinMind 資料源已停止自動雷達監控，避免 API 用量增加。");
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        if (signalMonitor != null) {
            signalMonitor.stop();
        }
        initializeSignalMonitor();
        if (!symbols.isEmpty()) {
            autoTradingEnabled = true;
            signalMonitor.start(symbols);
        }
    }

    private void updateScanResultsOnUi(List<MarketScanResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        Runnable updateTask = () -> {
            latestScanResults.clear();
            for (MarketScanResult result : results) {
                latestScanResults.put(result.getSymbol(), result);
                if (watchlistPanel != null) {
                    watchlistPanel.updateScanResult(result);
                }
            }
            if (opportunityRadarDock != null) {
                opportunityRadarDock.updateScanResults(results);
            }
            if (marketAnalysisDock != null) {
                MarketScanResult selected = latestScanResults.get(currentSymbol);
                if (selected == null) {
                    selected = results.get(0);
                }
                marketAnalysisDock.updateScanResult(selected);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            updateTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(updateTask);
            } catch (Exception e) {
                SwingUtilities.invokeLater(updateTask);
                System.err.println("Failed to synchronously update scan UI: " + e.getMessage());
            }
        }
    }

    private DecisionConfig createAggressiveMonitorConfig() {
        DecisionConfig config = DecisionConfig.createAggressive();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.18);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.20);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.1);
        config.getRiskConfig().setMaxConcurrentPositions(12);
        config.getRiskConfig().setMaxPositionSizePercent(0.35);
        config.getRiskConfig().setMinCashReservePercent(0.05);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createSimulationTestDecisionConfig() {
        DecisionConfig config = DecisionConfig.createAggressive();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(false);
        config.getVotingConfig().setLongEntryThreshold(0.05);
        config.getVotingConfig().setShortEntryThreshold(0.99);
        config.getVotingConfig().setExitThreshold(0.05);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(0.5);
        config.getRiskConfig().setMinVolatilityPercent(0.0);
        config.getRiskConfig().setMaxVolatilityPercent(1.0);
        config.getRiskConfig().setMaxConcurrentPositions(20);
        config.getRiskConfig().setMaxPositionSizePercent(0.50);
        config.getRiskConfig().setMinCashReservePercent(0.0);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createBalancedMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.35);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.5);
        config.getRiskConfig().setMaxConcurrentPositions(6);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createBConvergenceMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.35);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.5);
        config.getRiskConfig().setMaxConcurrentPositions(10);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig copyDecisionConfig(DecisionConfig source) {
        DecisionConfig copy = DecisionConfig.createDefault();
        copy.setMainLoopTimeframe(source.getMainLoopTimeframe());
        copy.setRiskMonitorTimeframe(source.getRiskMonitorTimeframe());
        copy.setRegimeDetectionEnabled(source.isRegimeDetectionEnabled());
        copy.setTrendAnalysisEnabled(source.isTrendAnalysisEnabled());
        copy.setPatternDetectionEnabled(source.isPatternDetectionEnabled());
        copy.setVotingEnabled(source.isVotingEnabled());
        copy.setRiskManagementEnabled(source.isRiskManagementEnabled());
        copy.setLogLevel(source.getLogLevel());
        copy.setVerboseLogging(source.isVerboseLogging());
        copy.getVotingConfig().setLongEntryThreshold(source.getVotingConfig().getLongEntryThreshold());
        copy.getVotingConfig().setShortEntryThreshold(source.getVotingConfig().getShortEntryThreshold());
        copy.getVotingConfig().setExitThreshold(source.getVotingConfig().getExitThreshold());
        copy.getVotingConfig().setReverseThreshold(source.getVotingConfig().getReverseThreshold());
        copy.getVotingConfig().setReverseEnabled(source.getVotingConfig().isReverseEnabled());
        copy.getVotingConfig().setMinVotingStrategies(source.getVotingConfig().getMinVotingStrategies());
        copy.getVotingConfig().setRequireConsensus(source.getVotingConfig().isRequireConsensus());
        copy.getVotingConfig().setSignalValidityMs(source.getVotingConfig().getSignalValidityMs());
        copy.getRiskConfig().setRiskPercentPerTrade(source.getRiskConfig().getRiskPercentPerTrade());
        copy.getRiskConfig().setMaxDailyLossPercent(source.getRiskConfig().getMaxDailyLossPercent());
        copy.getRiskConfig().setMaxSymbolLossPercent(source.getRiskConfig().getMaxSymbolLossPercent());
        copy.getRiskConfig().setMaxConcurrentPositions(source.getRiskConfig().getMaxConcurrentPositions());
        copy.getRiskConfig().setMaxPositionSizePercent(source.getRiskConfig().getMaxPositionSizePercent());
        copy.getRiskConfig().setMinCashReservePercent(source.getRiskConfig().getMinCashReservePercent());
        copy.getRiskConfig().setDailyLossLimitEnabled(source.getRiskConfig().isDailyLossLimitEnabled());
        copy.getRiskConfig().setSymbolLossLimitEnabled(source.getRiskConfig().isSymbolLossLimitEnabled());
        copy.getRiskConfig().setAllowExitOnlyAfterDailyLimit(source.getRiskConfig().isAllowExitOnlyAfterDailyLimit());
        copy.getRiskConfig().setMaxHoldingBars(source.getRiskConfig().getMaxHoldingBars());
        copy.getRiskConfig().setForceCloseAtEndOfDay(source.getRiskConfig().isForceCloseAtEndOfDay());
        copy.getRiskConfig().setCloseBeforeEndOfDayBars(source.getRiskConfig().getCloseBeforeEndOfDayBars());
        copy.getRiskConfig().setMinRiskRewardRatio(source.getRiskConfig().getMinRiskRewardRatio());
        copy.getRiskConfig().setMinVolatilityPercent(source.getRiskConfig().getMinVolatilityPercent());
        copy.getRiskConfig().setMaxVolatilityPercent(source.getRiskConfig().getMaxVolatilityPercent());
        copy.getRiskConfig().setAllowShortSelling(source.getRiskConfig().isAllowShortSelling());
        return copy;
    }

    private void showMonitorSettingsDialog() {
        if (monitorConfig == null || monitorDecisionConfig == null) {
            initializeSignalMonitor();
        }
        RadarStrategyConfig radarConfig = monitorConfig.getRadarStrategyConfig() != null
                ? monitorConfig.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();

        JDialog dialog = new JDialog(this, "監控門檻設定", true);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> templateBox = new JComboBox<>(new String[]{"目前設定", "模擬測試模板", "積極模板", "平衡模板", "B組收斂版模板"});
        JSpinner scanInterval = new JSpinner(new SpinnerNumberModel(monitorConfig.getScanIntervalSeconds(), 3, 60, 1));
        JComboBox<Timeframe> timeframeBox = new JComboBox<>(new Timeframe[]{Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1});
        timeframeBox.setSelectedItem(monitorConfig.getTimeframe());
        JSpinner barCountSpinner = new JSpinner(new SpinnerNumberModel(monitorConfig.getBarCount(), 60, 500, 20));
        JSpinner signalInterval = new JSpinner(new SpinnerNumberModel(monitorConfig.getMinSignalIntervalMinutes(), 1, 60, 1));
        JSpinner longThreshold = percentSpinner(monitorDecisionConfig.getVotingConfig().getLongEntryThreshold());
        JSpinner exitThreshold = percentSpinner(monitorDecisionConfig.getVotingConfig().getExitThreshold());
        JSpinner minRiskReward = decimalSpinner(monitorDecisionConfig.getRiskConfig().getMinRiskRewardRatio(), 0.5, 5.0, 0.1);
        JSpinner minVolatility = percentSpinner(monitorDecisionConfig.getRiskConfig().getMinVolatilityPercent());
        JSpinner maxVolatility = percentSpinner(monitorDecisionConfig.getRiskConfig().getMaxVolatilityPercent());
        JSpinner minStrategies = new JSpinner(new SpinnerNumberModel(
                monitorDecisionConfig.getVotingConfig().getMinVotingStrategies(), 1, 5, 1));
        JSpinner maxPositions = new JSpinner(new SpinnerNumberModel(
                monitorDecisionConfig.getRiskConfig().getMaxConcurrentPositions(), 1, 20, 1));
        JCheckBox riskEnabled = new JCheckBox("啟用風控", monitorDecisionConfig.isRiskManagementEnabled());
        JCheckBox earlyBlockEnabled = new JCheckBox("啟用早盤禁開倉", monitorConfig.isEarlyEntryBlockEnabled());
        JSpinner earlyStartHour = new JSpinner(new SpinnerNumberModel(monitorConfig.getEarlyEntryBlockStart().getHour(), 0, 23, 1));
        JSpinner earlyStartMinute = new JSpinner(new SpinnerNumberModel(monitorConfig.getEarlyEntryBlockStart().getMinute(), 0, 59, 1));
        JSpinner earlyEndHour = new JSpinner(new SpinnerNumberModel(monitorConfig.getEarlyEntryBlockEnd().getHour(), 0, 23, 1));
        JSpinner earlyEndMinute = new JSpinner(new SpinnerNumberModel(monitorConfig.getEarlyEntryBlockEnd().getMinute(), 0, 59, 1));
        JCheckBox stopLossCooldownEnabled = new JCheckBox("啟用停損後冷卻", monitorConfig.isStopLossCooldownEnabled());
        JSpinner stopLossCooldownMinutes = new JSpinner(new SpinnerNumberModel(
                monitorConfig.getStopLossCooldownMinutes(), 1, 240, 5));
        JSpinner latestEntryHour = new JSpinner(new SpinnerNumberModel(
                monitorConfig.getLatestAutoEntryTime().getHour(), 0, 23, 1));
        JSpinner latestEntryMinute = new JSpinner(new SpinnerNumberModel(
                monitorConfig.getLatestAutoEntryTime().getMinute(), 0, 59, 1));
        JPanel earlyBlockPanel = createTimeRangePanel(
                earlyBlockEnabled, earlyStartHour, earlyStartMinute, earlyEndHour, earlyEndMinute);
        JPanel stopLossCooldownPanel = createCheckboxSpinnerPanel(
                stopLossCooldownEnabled, stopLossCooldownMinutes, "分鐘");
        JPanel latestEntryPanel = createTimePanel(latestEntryHour, latestEntryMinute);
        JComboBox<Timeframe> dayTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M1, Timeframe.M5, Timeframe.M15});
        dayTimeframe.setSelectedItem(radarConfig.getDayTradeTimeframe());
        JComboBox<Timeframe> shortTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1});
        shortTimeframe.setSelectedItem(radarConfig.getShortSwingTimeframe());
        JComboBox<Timeframe> swingTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M15, Timeframe.H1, Timeframe.D1});
        swingTimeframe.setSelectedItem(radarConfig.getSwingTradeTimeframe());
        JSpinner dayBars = new JSpinner(new SpinnerNumberModel(radarConfig.getDayTradeBarCount(), 60, 600, 20));
        JSpinner shortBars = new JSpinner(new SpinnerNumberModel(radarConfig.getShortSwingBarCount(), 80, 800, 20));
        JSpinner swingBars = new JSpinner(new SpinnerNumberModel(radarConfig.getSwingTradeBarCount(), 100, 1000, 20));

        JCheckBox rsiEnabled = new JCheckBox("啟用 RSI", radarConfig.isRsiEnabled());
        JSpinner rsiPeriod = new JSpinner(new SpinnerNumberModel(radarConfig.getRsiPeriod(), 2, 60, 1));
        JSpinner rsiOversold = decimalSpinner(radarConfig.getRsiOversold(), 1.0, 99.0, 1.0);
        JSpinner rsiOverbought = decimalSpinner(radarConfig.getRsiOverbought(), 1.0, 99.0, 1.0);
        JSpinner rsiWeight = percentSpinner(radarConfig.getRsiWeight());
        JCheckBox requireRsiEntryConfirmation = new JCheckBox("RSI 接刀需 EMA 或放量確認", radarConfig.isRequireRsiEntryConfirmation());

        JCheckBox maEnabled = new JCheckBox("啟用均線趨勢", radarConfig.isMovingAverageEnabled());
        JComboBox<RadarStrategyConfig.MovingAverageType> maType = new JComboBox<>(RadarStrategyConfig.MovingAverageType.values());
        maType.setSelectedItem(radarConfig.getMovingAverageType());
        JSpinner fastMa = new JSpinner(new SpinnerNumberModel(radarConfig.getFastMovingAveragePeriod(), 2, 120, 1));
        JSpinner slowMa = new JSpinner(new SpinnerNumberModel(radarConfig.getSlowMovingAveragePeriod(), 3, 240, 1));
        JSpinner maWeight = percentSpinner(radarConfig.getMovingAverageWeight());

        JCheckBox volumeEnabled = new JCheckBox("啟用放量突破", radarConfig.isVolumeBreakoutEnabled());
        JSpinner breakoutLookback = new JSpinner(new SpinnerNumberModel(radarConfig.getBreakoutLookbackBars(), 5, 200, 5));
        JSpinner volumeMultiplier = decimalSpinner(radarConfig.getVolumeMultiplier(), 1.0, 10.0, 0.1);
        JSpinner volumeWeight = percentSpinner(radarConfig.getVolumeBreakoutWeight());
        JSpinner minimumRadarEntryScore = percentSpinner(radarConfig.getMinimumEntryScore());
        JCheckBox blockBreakoutOnRsiOverbought = new JCheckBox("量能突破遇 RSI 超買時禁止追高", radarConfig.isBlockBreakoutOnRsiOverbought());
        JCheckBox requireBreakoutContinuation = new JCheckBox("量能突破需價格延續確認", radarConfig.isRequireBreakoutContinuation());
        JCheckBox requirePriceAboveVwap = new JCheckBox("做多需站上 VWAP", radarConfig.isRequirePriceAboveVwapForLong());
        JCheckBox requireBreakoutNextBarConfirmation = new JCheckBox("突破後一根 K 確認", radarConfig.isRequireBreakoutNextBarConfirmation());
        JSpinner maxEntryRiseFromRecentLow = percentSpinner(radarConfig.getMaxEntryRiseFromRecentLowPercent());

        templateBox.addActionListener(e -> {
            if (templateBox.getSelectedIndex() == 0) {
                return;
            }
            SignalMonitorConfig configTemplate = switch (templateBox.getSelectedIndex()) {
                case 1 -> SignalMonitorConfig.createSimulationTestTemplate();
                case 2 -> SignalMonitorConfig.createAggressiveTemplate();
                case 4 -> SignalMonitorConfig.createBConvergenceTemplate();
                default -> SignalMonitorConfig.createBalancedTemplate();
            };
            DecisionConfig decisionTemplate = switch (templateBox.getSelectedIndex()) {
                case 1 -> createSimulationTestDecisionConfig();
                case 2 -> createAggressiveMonitorConfig();
                case 4 -> createBConvergenceMonitorConfig();
                default -> createBalancedMonitorConfig();
            };
            scanInterval.setValue(configTemplate.getScanIntervalSeconds());
            timeframeBox.setSelectedItem(configTemplate.getTimeframe());
            barCountSpinner.setValue(configTemplate.getBarCount());
            signalInterval.setValue(configTemplate.getMinSignalIntervalMinutes());
            earlyBlockEnabled.setSelected(configTemplate.isEarlyEntryBlockEnabled());
            earlyStartHour.setValue(configTemplate.getEarlyEntryBlockStart().getHour());
            earlyStartMinute.setValue(configTemplate.getEarlyEntryBlockStart().getMinute());
            earlyEndHour.setValue(configTemplate.getEarlyEntryBlockEnd().getHour());
            earlyEndMinute.setValue(configTemplate.getEarlyEntryBlockEnd().getMinute());
            stopLossCooldownEnabled.setSelected(configTemplate.isStopLossCooldownEnabled());
            stopLossCooldownMinutes.setValue(configTemplate.getStopLossCooldownMinutes());
            latestEntryHour.setValue(configTemplate.getLatestAutoEntryTime().getHour());
            latestEntryMinute.setValue(configTemplate.getLatestAutoEntryTime().getMinute());
            RadarStrategyConfig radarTemplate = configTemplate.getRadarStrategyConfig();
            dayTimeframe.setSelectedItem(radarTemplate.getDayTradeTimeframe());
            shortTimeframe.setSelectedItem(radarTemplate.getShortSwingTimeframe());
            swingTimeframe.setSelectedItem(radarTemplate.getSwingTradeTimeframe());
            dayBars.setValue(radarTemplate.getDayTradeBarCount());
            shortBars.setValue(radarTemplate.getShortSwingBarCount());
            swingBars.setValue(radarTemplate.getSwingTradeBarCount());
            rsiEnabled.setSelected(radarTemplate.isRsiEnabled());
            rsiPeriod.setValue(radarTemplate.getRsiPeriod());
            rsiOversold.setValue(radarTemplate.getRsiOversold());
            rsiOverbought.setValue(radarTemplate.getRsiOverbought());
            rsiWeight.setValue(radarTemplate.getRsiWeight());
            requireRsiEntryConfirmation.setSelected(radarTemplate.isRequireRsiEntryConfirmation());
            maEnabled.setSelected(radarTemplate.isMovingAverageEnabled());
            maType.setSelectedItem(radarTemplate.getMovingAverageType());
            fastMa.setValue(radarTemplate.getFastMovingAveragePeriod());
            slowMa.setValue(radarTemplate.getSlowMovingAveragePeriod());
            maWeight.setValue(radarTemplate.getMovingAverageWeight());
            volumeEnabled.setSelected(radarTemplate.isVolumeBreakoutEnabled());
            breakoutLookback.setValue(radarTemplate.getBreakoutLookbackBars());
            volumeMultiplier.setValue(radarTemplate.getVolumeMultiplier());
            volumeWeight.setValue(radarTemplate.getVolumeBreakoutWeight());
            minimumRadarEntryScore.setValue(radarTemplate.getMinimumEntryScore());
            blockBreakoutOnRsiOverbought.setSelected(radarTemplate.isBlockBreakoutOnRsiOverbought());
            requireBreakoutContinuation.setSelected(radarTemplate.isRequireBreakoutContinuation());
            requirePriceAboveVwap.setSelected(radarTemplate.isRequirePriceAboveVwapForLong());
            requireBreakoutNextBarConfirmation.setSelected(radarTemplate.isRequireBreakoutNextBarConfirmation());
            maxEntryRiseFromRecentLow.setValue(radarTemplate.getMaxEntryRiseFromRecentLowPercent());
            longThreshold.setValue(decisionTemplate.getVotingConfig().getLongEntryThreshold());
            exitThreshold.setValue(decisionTemplate.getVotingConfig().getExitThreshold());
            minRiskReward.setValue(decisionTemplate.getRiskConfig().getMinRiskRewardRatio());
            minVolatility.setValue(decisionTemplate.getRiskConfig().getMinVolatilityPercent());
            maxVolatility.setValue(decisionTemplate.getRiskConfig().getMaxVolatilityPercent());
            minStrategies.setValue(decisionTemplate.getVotingConfig().getMinVotingStrategies());
            maxPositions.setValue(decisionTemplate.getRiskConfig().getMaxConcurrentPositions());
            riskEnabled.setSelected(decisionTemplate.isRiskManagementEnabled());
        });

        addSettingsRow(panel, gbc, 0, "預設模板", templateBox);
        addSettingsRow(panel, gbc, 1, "掃描間隔（秒）", scanInterval);
        addSettingsRow(panel, gbc, 2, "主掃描週期", timeframeBox);
        addSettingsRow(panel, gbc, 3, "K 線數量", barCountSpinner);
        addSettingsRow(panel, gbc, 4, "同股訊號冷卻（分）", signalInterval);
        addSettingsRow(panel, gbc, 5, "做多門檻", longThreshold);
        addSettingsRow(panel, gbc, 6, "出場門檻", exitThreshold);
        addSettingsRow(panel, gbc, 7, "最低風報比", minRiskReward);
        addSettingsRow(panel, gbc, 8, "最低波動", minVolatility);
        addSettingsRow(panel, gbc, 9, "最高波動", maxVolatility);
        addSettingsRow(panel, gbc, 10, "最少策略數", minStrategies);
        addSettingsRow(panel, gbc, 11, "最大同時持倉", maxPositions);
        addSettingsRow(panel, gbc, 12, "", riskEnabled);
        addSettingsRow(panel, gbc, 13, "早盤禁開倉", earlyBlockPanel);
        addSettingsRow(panel, gbc, 14, "停損冷卻", stopLossCooldownPanel);
        addSettingsRow(panel, gbc, 15, "當沖週期", dayTimeframe);
        addSettingsRow(panel, gbc, 16, "短線週期", shortTimeframe);
        addSettingsRow(panel, gbc, 17, "波段週期", swingTimeframe);
        addSettingsRow(panel, gbc, 18, "當沖 K 線數量", dayBars);
        addSettingsRow(panel, gbc, 19, "短線 K 線數量", shortBars);
        addSettingsRow(panel, gbc, 20, "波段 K 線數量", swingBars);
        addSettingsRow(panel, gbc, 21, "", rsiEnabled);
        addSettingsRow(panel, gbc, 22, "RSI 週期", rsiPeriod);
        addSettingsRow(panel, gbc, 23, "RSI 超賣", rsiOversold);
        addSettingsRow(panel, gbc, 24, "RSI 超買", rsiOverbought);
        addSettingsRow(panel, gbc, 25, "RSI 權重", rsiWeight);
        addSettingsRow(panel, gbc, 26, "", maEnabled);
        addSettingsRow(panel, gbc, 27, "均線類型", maType);
        addSettingsRow(panel, gbc, 28, "均線快線", fastMa);
        addSettingsRow(panel, gbc, 29, "均線慢線", slowMa);
        addSettingsRow(panel, gbc, 30, "均線權重", maWeight);
        addSettingsRow(panel, gbc, 31, "", volumeEnabled);
        addSettingsRow(panel, gbc, 32, "突破回看 K 數", breakoutLookback);
        addSettingsRow(panel, gbc, 33, "成交量倍率", volumeMultiplier);
        addSettingsRow(panel, gbc, 34, "放量權重", volumeWeight);

        addSettingsRow(panel, gbc, 35, "尾盤禁止新倉時間", latestEntryPanel);
        addSettingsRow(panel, gbc, 36, "雷達最低進場分數", minimumRadarEntryScore);
        addSettingsRow(panel, gbc, 37, "", blockBreakoutOnRsiOverbought);
        addSettingsRow(panel, gbc, 38, "", requireBreakoutContinuation);
        addSettingsRow(panel, gbc, 39, "", requirePriceAboveVwap);
        addSettingsRow(panel, gbc, 40, "", requireBreakoutNextBarConfirmation);
        addSettingsRow(panel, gbc, 41, "追價限制（近低漲幅）", maxEntryRiseFromRecentLow);

        addSettingsRow(panel, gbc, 42, "", requireRsiEntryConfirmation);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("取消");
        JButton applyButton = new JButton("套用");
        cancelButton.addActionListener(e -> dialog.dispose());
        applyButton.addActionListener(e -> {
            monitorConfig.setScanIntervalSeconds(((Number) scanInterval.getValue()).intValue());
            monitorConfig.setTimeframe((Timeframe) timeframeBox.getSelectedItem());
            monitorConfig.setBarCount(((Number) barCountSpinner.getValue()).intValue());
            monitorConfig.setMinSignalIntervalMinutes(((Number) signalInterval.getValue()).intValue());
            monitorConfig.setBatchScanMode(true);
            monitorConfig.setEarlyEntryBlockEnabled(earlyBlockEnabled.isSelected());
            monitorConfig.setEarlyEntryBlockStart(readTime(earlyStartHour, earlyStartMinute));
            monitorConfig.setEarlyEntryBlockEnd(readTime(earlyEndHour, earlyEndMinute));
            monitorConfig.setStopLossCooldownEnabled(stopLossCooldownEnabled.isSelected());
            monitorConfig.setStopLossCooldownMinutes(((Number) stopLossCooldownMinutes.getValue()).intValue());
            monitorConfig.setLatestAutoEntryTime(readTime(latestEntryHour, latestEntryMinute));
            RadarStrategyConfig updatedRadarConfig = radarConfig.copy();
            updatedRadarConfig.setDayTradeTimeframe((Timeframe) dayTimeframe.getSelectedItem());
            updatedRadarConfig.setShortSwingTimeframe((Timeframe) shortTimeframe.getSelectedItem());
            updatedRadarConfig.setSwingTradeTimeframe((Timeframe) swingTimeframe.getSelectedItem());
            updatedRadarConfig.setDayTradeBarCount(((Number) dayBars.getValue()).intValue());
            updatedRadarConfig.setShortSwingBarCount(((Number) shortBars.getValue()).intValue());
            updatedRadarConfig.setSwingTradeBarCount(((Number) swingBars.getValue()).intValue());
            updatedRadarConfig.setRsiEnabled(rsiEnabled.isSelected());
            updatedRadarConfig.setRsiPeriod(((Number) rsiPeriod.getValue()).intValue());
            updatedRadarConfig.setRsiOversold(((Number) rsiOversold.getValue()).doubleValue());
            updatedRadarConfig.setRsiOverbought(((Number) rsiOverbought.getValue()).doubleValue());
            updatedRadarConfig.setRsiWeight(((Number) rsiWeight.getValue()).doubleValue());
            updatedRadarConfig.setRequireRsiEntryConfirmation(requireRsiEntryConfirmation.isSelected());
            updatedRadarConfig.setMovingAverageEnabled(maEnabled.isSelected());
            updatedRadarConfig.setMovingAverageType((RadarStrategyConfig.MovingAverageType) maType.getSelectedItem());
            updatedRadarConfig.setFastMovingAveragePeriod(((Number) fastMa.getValue()).intValue());
            updatedRadarConfig.setSlowMovingAveragePeriod(((Number) slowMa.getValue()).intValue());
            updatedRadarConfig.setMovingAverageWeight(((Number) maWeight.getValue()).doubleValue());
            updatedRadarConfig.setVolumeBreakoutEnabled(volumeEnabled.isSelected());
            updatedRadarConfig.setBreakoutLookbackBars(((Number) breakoutLookback.getValue()).intValue());
            updatedRadarConfig.setVolumeMultiplier(((Number) volumeMultiplier.getValue()).doubleValue());
            updatedRadarConfig.setVolumeBreakoutWeight(((Number) volumeWeight.getValue()).doubleValue());
            updatedRadarConfig.setMinimumEntryScore(((Number) minimumRadarEntryScore.getValue()).doubleValue());
            updatedRadarConfig.setBlockBreakoutOnRsiOverbought(blockBreakoutOnRsiOverbought.isSelected());
            updatedRadarConfig.setRequireBreakoutContinuation(requireBreakoutContinuation.isSelected());
            updatedRadarConfig.setRequirePriceAboveVwapForLong(requirePriceAboveVwap.isSelected());
            updatedRadarConfig.setRequireBreakoutNextBarConfirmation(requireBreakoutNextBarConfirmation.isSelected());
            updatedRadarConfig.setMaxEntryRiseFromRecentLowPercent(((Number) maxEntryRiseFromRecentLow.getValue()).doubleValue());
            monitorConfig.setRadarStrategyConfig(updatedRadarConfig);

            DecisionConfig updatedDecisionConfig = copyDecisionConfig(monitorDecisionConfig);
            updatedDecisionConfig.getVotingConfig().setLongEntryThreshold(((Number) longThreshold.getValue()).doubleValue());
            updatedDecisionConfig.getVotingConfig().setExitThreshold(((Number) exitThreshold.getValue()).doubleValue());
            updatedDecisionConfig.getVotingConfig().setMinVotingStrategies(((Number) minStrategies.getValue()).intValue());
            updatedDecisionConfig.setRiskManagementEnabled(riskEnabled.isSelected());
            updatedDecisionConfig.getRiskConfig().setMaxConcurrentPositions(((Number) maxPositions.getValue()).intValue());
            updatedDecisionConfig.getRiskConfig().setMinRiskRewardRatio(((Number) minRiskReward.getValue()).doubleValue());
            updatedDecisionConfig.getRiskConfig().setMinVolatilityPercent(((Number) minVolatility.getValue()).doubleValue());
            updatedDecisionConfig.getRiskConfig().setMaxVolatilityPercent(((Number) maxVolatility.getValue()).doubleValue());
            updatedDecisionConfig.getRiskConfig().setAllowShortSelling(false);
            monitorDecisionConfig = updatedDecisionConfig;

            boolean shouldRestart = autoTradingEnabled;
            List<String> symbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();
            if (signalMonitor != null) {
                signalMonitor.stop();
            }
            initializeSignalMonitor();
            if (shouldRestart && !symbols.isEmpty()) {
                autoTradingEnabled = true;
                signalMonitor.start(symbols);
            }
            statusBar.setText("監控門檻已更新");
            dialog.dispose();
        });
        buttons.add(cancelButton);
        buttons.add(applyButton);

        JScrollPane settingsScrollPane = new JScrollPane(panel);
        settingsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        settingsScrollPane.setPreferredSize(new Dimension(620, 700));
        dialog.add(settingsScrollPane, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel createTimeRangePanel(
            JCheckBox enabled,
            JSpinner startHour,
            JSpinner startMinute,
            JSpinner endHour,
            JSpinner endMinute) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(enabled);
        panel.add(startHour);
        panel.add(new JLabel(":"));
        panel.add(startMinute);
        panel.add(new JLabel("~"));
        panel.add(endHour);
        panel.add(new JLabel(":"));
        panel.add(endMinute);
        return panel;
    }

    private JPanel createCheckboxSpinnerPanel(JCheckBox enabled, JSpinner spinner, String suffix) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(enabled);
        panel.add(spinner);
        panel.add(new JLabel(suffix));
        return panel;
    }

    private JPanel createTimePanel(JSpinner hour, JSpinner minute) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(hour);
        panel.add(new JLabel(":"));
        panel.add(minute);
        return panel;
    }

    private LocalTime readTime(JSpinner hourSpinner, JSpinner minuteSpinner) {
        int hour = ((Number) hourSpinner.getValue()).intValue();
        int minute = ((Number) minuteSpinner.getValue()).intValue();
        return LocalTime.of(Math.max(0, Math.min(23, hour)), Math.max(0, Math.min(59, minute)));
    }

    private JSpinner percentSpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 1.0, 0.05));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.00"));
        return spinner;
    }

    private JSpinner decimalSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.0"));
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

    private void startAutoTrading(boolean showDialog) {
        if (isFinMindDataSource()) {
            String message = "FinMind 資料源不啟動自動雷達監控，避免背景掃描消耗 API 額度。請切換 MarketDataCollector。";
            statusBar.setText(message);
            if (showDialog) {
                JOptionPane.showMessageDialog(this, message, "FinMind API 保護", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        if (autoTradingEnabled) {
            if (showDialog) {
                JOptionPane.showMessageDialog(this, "自動偵測已在執行中", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (watchlistPanel == null) {
            return;
        }
        List<String> symbols = watchlistPanel.getSymbols();
        if (symbols.isEmpty()) {
            if (showDialog) {
                JOptionPane.showMessageDialog(this, "觀察清單為空，無法啟動偵測", "無法啟動", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        initializeSignalMonitor();
        autoTradingEnabled = true;
        signalMonitor.start(symbols);
        System.out.println("Auto trading monitor started for " + symbols.size() + " symbols: " + symbols);
        statusBar.setText("自動偵測已啟動");
    }

    private void stopAutoTrading(boolean showDialog) {
        if (!autoTradingEnabled) {
            if (showDialog) {
                JOptionPane.showMessageDialog(this, "自動偵測未啟動", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        autoTradingEnabled = false;
        if (signalMonitor != null) {
            signalMonitor.stop();
        }
        statusBar.setText("自動偵測已停止");
    }

    private void handleTradingSignal(String symbol, DecisionResult signal) {
        if (!autoTradingEnabled || signal == null || executionEngine == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                DecisionResult autoSignal = normalizeAutoMonitorSignal(symbol, signal);
                double price = getCurrentPrice(symbol);
                if (price <= 0.0) {
                    statusBar.setText("無法取得 " + symbol + " 即時價格，略過本次委託");
                    return;
                }

                ExecutionResult result = null;
                switch (autoSignal.getAction()) {
                    case OPEN_LONG -> {
                        if (monitorPortfolio.getPosition(symbol) != null || pendingAutoEntries.contains(symbol)) {
                            return;
                        }
                        String blockReason = resolveAutoEntryBlockReason(symbol);
                        if (blockReason != null) {
                            statusBar.setText(blockReason);
                            return;
                        }
                        int quantity = determineOrderQuantity(price, autoSignal);
                        if (quantity <= 0) {
                            statusBar.setText("資金不足，無法開倉 " + symbol);
                            return;
                        }
                        pendingAutoEntries.add(symbol);
                        result = executionEngine.openPosition(
                                symbol,
                                quantity,
                                price,
                                autoSignal.getOrderType(),
                                autoSignal.getSuggestedStopLoss(),
                                autoSignal.getSuggestedTakeProfit(),
                                autoSignal.getReason());
                    }
                    case CLOSE_POSITION -> {
                        Position position = monitorPortfolio.getPosition(symbol);
                        if (position == null || position.getQuantity() <= 0) {
                            return;
                        }
                        result = executionEngine.closePosition(
                                symbol,
                                position.getQuantity(),
                                price,
                                autoSignal.getOrderType(),
                                autoSignal.getReason());
                        pendingAutoEntries.remove(symbol);
                        activeStopLosses.remove(symbol);
                        activeTakeProfits.remove(symbol);
                        activeTradeModes.remove(symbol);
                        autoManagedPositions.remove(symbol);
                    }
                    case OPEN_SHORT, HOLD, NO_ACTION -> {
                        return;
                    }
                }

                if (result != null) {
                    paperTradeRecorder.record(result, autoSignal, latestScanResults.get(symbol), "auto-monitor");
                    if (!result.isSuccess()) {
                        pendingAutoEntries.remove(symbol);
                    } else if (autoSignal.getAction() == DecisionResult.Action.OPEN_LONG) {
                        activeTradeModes.put(symbol, TradeMode.DAY_TRADE);
                        autoManagedPositions.add(symbol);
                        if (result.getStopLoss() != null && result.getStopLoss() > 0.0) {
                            activeStopLosses.put(symbol, result.getStopLoss());
                        }
                        if (result.getTakeProfit() != null && result.getTakeProfit() > 0.0) {
                            activeTakeProfits.put(symbol, result.getTakeProfit());
                        }
                    }
                    if (executionStatusDock != null) {
                        executionStatusDock.setExecutionEngine(executionEngine);
                    }
                    statusBar.setText(String.format("批次執行：%s %s @ %.2f",
                            symbol, autoSignal.getAction().getDisplayName(), price));
                }
            } catch (Exception e) {
                pendingAutoEntries.remove(symbol);
                statusBar.setText("自動偵測執行失敗：" + e.getMessage());
            }
        });
    }

    private DecisionResult normalizeAutoMonitorSignal(String symbol, DecisionResult signal) {
        if (signal == null) {
            return null;
        }
        if (signal.getAction() != DecisionResult.Action.OPEN_LONG
                && signal.getAction() != DecisionResult.Action.CLOSE_POSITION) {
            return signal;
        }
        return new DecisionResult.Builder()
                .action(signal.getAction())
                .source(signal.getSource())
                .symbol(symbol != null && !symbol.isBlank() ? symbol : signal.getSymbol())
                .tradeMode(TradeMode.DAY_TRADE)
                .reason(signal.getReason())
                .timestamp(signal.getTimestamp())
                .orderType(signal.getOrderType())
                .orderSide(signal.getOrderSide())
                .suggestedStopLoss(signal.getSuggestedStopLoss())
                .suggestedTakeProfit(signal.getSuggestedTakeProfit())
                .suggestedQuantity(DAY_TRADE_LOT_SIZE)
                .riskRewardRatio(signal.getRiskRewardRatio())
                .regimeAnalysis(signal.getRegimeAnalysis())
                .trendAnalysis(signal.getTrendAnalysis())
                .votingResult(signal.getVotingResult())
                .riskViolation(signal.getRiskViolation())
                .confidence(signal.getConfidence())
                .build();
    }

    private String resolveAutoEntryBlockReason(String symbol) {
        LocalTime now = LocalTime.now(TAIPEI_ZONE);
        if (isDayTradeForceCloseTime(now)) {
            closeAutoManagedPositionsAtCutoff();
            return "13:25 後自動監控不再新開倉：" + symbol;
        }
        if (isLateAutoEntryBlocked(now)) {
            return "尾盤禁止新開倉時間已到：" + symbol;
        }
        if (isEarlyEntryBlocked(now)) {
            return "早盤禁開倉時段，略過自動開倉：" + symbol;
        }
        LocalDateTime cooldownUntil = stopLossCooldownUntil.get(symbol);
        if (cooldownUntil != null) {
            if (LocalDateTime.now(TAIPEI_ZONE).isBefore(cooldownUntil)) {
                return "停損冷卻中，略過自動開倉：" + symbol + "，冷卻至 " + cooldownUntil.toLocalTime();
            }
            stopLossCooldownUntil.remove(symbol);
        }
        int maxPositions = monitorDecisionConfig != null
                ? monitorDecisionConfig.getRiskConfig().getMaxConcurrentPositions()
                : 1;
        if (monitorPortfolio != null && monitorPortfolio.getPositionCount() >= maxPositions) {
            return "自動監控持倉已達上限 " + maxPositions + " 檔，略過：" + symbol;
        }
        return null;
    }

    private boolean isEarlyEntryBlocked(LocalTime time) {
        if (monitorConfig == null || !monitorConfig.isEarlyEntryBlockEnabled() || time == null) {
            return false;
        }
        LocalTime start = monitorConfig.getEarlyEntryBlockStart();
        LocalTime end = monitorConfig.getEarlyEntryBlockEnd();
        if (start == null || end == null || !start.isBefore(end)) {
            return false;
        }
        return !time.isBefore(start) && time.isBefore(end);
    }

    private boolean isLateAutoEntryBlocked(LocalTime time) {
        if (time == null) {
            return false;
        }
        LocalTime latestEntryTime = monitorConfig != null
                ? monitorConfig.getLatestAutoEntryTime()
                : LocalTime.of(13, 10);
        return latestEntryTime != null && !time.isBefore(latestEntryTime);
    }

    private void registerStopLossCooldown(String symbol) {
        if (symbol == null || symbol.isBlank()
                || monitorConfig == null
                || !monitorConfig.isStopLossCooldownEnabled()) {
            return;
        }
        stopLossCooldownUntil.put(
                symbol,
                LocalDateTime.now(TAIPEI_ZONE).plusMinutes(monitorConfig.getStopLossCooldownMinutes()));
    }

    private int determineOrderQuantity(double price, DecisionResult signal) {
        if (price <= 0.0 || executionEngine == null) {
            return 0;
        }
        TradeMode tradeMode = signal != null ? signal.getTradeMode() : null;
        return calculateOrderQuantity(
                price,
                monitorPortfolio.getCash(),
                monitorDecisionConfig.getRiskConfig().getMaxPositionSizePercent(),
                monitorDecisionConfig.getRiskConfig().getMinCashReservePercent(),
                tradeMode);
    }

    static int calculateOrderQuantity(double price, double cash, double maxPositionSizePercent,
                                      double minCashReservePercent, TradeMode tradeMode) {
        if (price <= 0.0) {
            return 0;
        }
        if (tradeMode == TradeMode.DAY_TRADE) {
            return DAY_TRADE_LOT_SIZE;
        }
        if (cash <= 0.0) {
            return 0;
        }
        double usableCash = cash * maxPositionSizePercent;
        double reserveCash = cash * minCashReservePercent;
        double budget = Math.max(0.0, usableCash - reserveCash);
        return (int) Math.floor(budget / price);
    }

    static long resolveWatchlistVolume(Tick tick) {
        return tick != null ? Math.max(0L, tick.getVolume()) : 0L;
    }

    static boolean isDayTradeForceCloseTime(LocalTime time) {
        return time != null && !time.isBefore(DAY_TRADE_FORCE_CLOSE_TIME);
    }

    private double getCurrentPrice(String symbol) {
        Double cachedPrice = latestPrices.get(symbol);
        if (cachedPrice != null && cachedPrice > 0.0) {
            return cachedPrice;
        }
        if (symbol != null && symbol.equals(currentSymbol) && lastPrice > 0.0) {
            return lastPrice;
        }
        if (isFinMindDataSource()) {
            return 0.0;
        }
        try {
            List<Bar> bars = dataFeed.fetchHistoricalBars(symbol, Timeframe.M1, 2);
            if (bars != null && !bars.isEmpty()) {
                return bars.get(bars.size() - 1).getClose();
            }
        } catch (Exception e) {
            System.err.println("取得價格失敗: " + e.getMessage());
        }
        return 0.0;
    }

    private void cleanup() {
        stopAutoTrading(false);
        unsubscribeAllWatchlistMarketData();
        if (dataFeed != null) {
            dataFeed.stop();
        }
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
        unsubscribeAllWatchlistMarketData();
        // 停止當前數據源
        if (dataFeed != null && dataFeed.isConnected()) {
            dataFeed.stop();
        }

        // 切換到新數據源
        dataFeed = dataSourceManager.switchDataSource(type);

        // 重新訂閱市場數據
        subscribeMarketData();

        // 啟動新數據源
        dataFeed.start();
        subscribeWatchlistMarketData();
        scanWatchlistForOpportunitiesAsync();
        restartMonitoringIfRunning();

        // 更新狀態欄
        String dataSourceName = type.getDisplayNameZh();
        statusBar.setText("已切換到: " + dataSourceName);

        JOptionPane.showMessageDialog(this,
            "已成功切換到 " + dataSourceName,
            "數據源切換",
            JOptionPane.INFORMATION_MESSAGE);
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
    private record PanelEntry(String id, String title) {
    }

    private record WatchlistSnapshot(String symbol, double last, double changePct, long volume) {
    }

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

