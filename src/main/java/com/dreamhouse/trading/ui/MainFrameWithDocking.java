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
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.core.finmind.FinMindIndustryChainImporter;
import com.dreamhouse.trading.core.finmind.FinMindKBarSqlImporter;
import com.dreamhouse.trading.core.finmind.FinMindRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.dreamhouse.trading.core.logging.PaperTradeRecorder;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.IndustryStrength;
import com.dreamhouse.trading.core.scanner.InternalMarketContextService;
import com.dreamhouse.trading.core.scanner.MarketContextService;
import com.dreamhouse.trading.core.scanner.MarketContextSnapshot;
import com.dreamhouse.trading.core.scanner.MarketRegime;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;
import com.dreamhouse.trading.core.scanner.WeakMarketLongPolicy;
import com.dreamhouse.trading.core.stockpool.AfterHoursStockPoolService;
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
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.Set;

public class MainFrameWithDocking extends JFrame {
    static final int DAY_TRADE_LOT_SIZE = 1000;
    static final LocalTime DAY_TRADE_FORCE_CLOSE_TIME = LocalTime.of(13, 25);
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final Path MONITOR_TEMPLATE_STORE = Path.of("config", "monitor_templates.properties");
    private record MonitorTemplate(
            String name,
            String description,
            SignalMonitorConfig monitorConfig,
            DecisionConfig decisionConfig,
            boolean userDefined) {
    }

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
    private MarketStatusDock marketStatusDock;
    private IndustryDistributionDock industryDistributionDock;

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
    private final Map<String, LocalDateTime> autoEntryTimes = new HashMap<>();
    private final Map<String, Double> autoEntryPrices = new HashMap<>();
    private final Map<String, Double> autoEntryRiskAmounts = new HashMap<>();
    private final Set<String> autoRangeEntries = new java.util.HashSet<>();
    private final Map<String, List<Trade>> latestSqlRadarBacktestTrades = new HashMap<>();
    private SqlRadarOpeningReplaySession activeSqlRadarReplaySession;
    private final Set<String> autoManagedPositions = new java.util.HashSet<>();
    private final Map<String, LocalDateTime> stopLossCooldownUntil = new HashMap<>();
    private final Set<String> watchlistMarketSubscriptions = new java.util.HashSet<>();
    private final Set<String> pendingAutoEntries = new java.util.HashSet<>();
    private LocalDate autoMonitorRiskDate = LocalDate.now(TAIPEI_ZONE);
    private double autoMonitorDailyPnl = 0.0;
    private int autoMonitorStopLossCount = 0;
    private int autoMonitorConsecutiveLosses = 0;
    private boolean autoMonitorTradingHalted = false;
    private int autoMonitorEntryCount = 0;
    private LocalDateTime autoMonitorLastEntryTime;
    private final Set<String> autoMonitorEntryBuckets = new java.util.HashSet<>();
    private SignalMonitorService signalMonitor;
    private SignalMonitorConfig monitorConfig;
    private DecisionConfig monitorDecisionConfig;
    private String monitorStrategyName = "當沖標準趨勢版";
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
        refreshMarketAndIndustryDocksFromSqlV2();
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

        marketStatusDock = new MarketStatusDock();
        DockableWrapper marketStatusWrapper = new DockableWrapper("marketStatus", "大盤狀態", marketStatusDock);
        Docking.registerDockable(marketStatusWrapper);
        Docking.dock(marketStatusWrapper, radarWrapper, DockingRegion.EAST);

        industryDistributionDock = new IndustryDistributionDock();
        industryDistributionDock.setOnAddIndustrySymbols(this::addIndustrySymbolsToWatchlist);
        industryDistributionDock.setOnRemoveIndustrySymbols(this::removeIndustrySymbolsFromWatchlist);
        industryDistributionDock.setOnAddSymbols(this::addSymbolsToWatchlist);
        DockableWrapper industryDistributionWrapper = new DockableWrapper(
                "industryDistribution", "產業分布", industryDistributionDock);
        Docking.registerDockable(industryDistributionWrapper);
        Docking.dock(industryDistributionWrapper, marketStatusWrapper, DockingRegion.SOUTH);

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

        JMenuItem sqlRadarReplayItem = new JMenuItem("SQL 雷達開盤重播");
        sqlRadarReplayItem.addActionListener(e -> startSqlRadarOpeningReplay());
        toolsMenu.add(sqlRadarReplayItem);

        JMenuItem stopSqlRadarReplayItem = new JMenuItem("停止 SQL 雷達重播");
        stopSqlRadarReplayItem.addActionListener(e -> stopSqlRadarOpeningReplay());
        toolsMenu.add(stopSqlRadarReplayItem);

        JMenuItem importIndustryItem = new JMenuItem("匯入 FinMind 產業到 SQL");
        importIndustryItem.addActionListener(e -> importFinMindIndustryToSql());
        toolsMenu.add(importIndustryItem);

        JMenuItem nextDayStockPoolItem = new JMenuItem("產生隔日當沖股票池");
        nextDayStockPoolItem.addActionListener(e -> generateAfterHoursStockPool());
        toolsMenu.add(nextDayStockPoolItem);

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
        JMenuItem dayTradeGuideItem = new JMenuItem("當沖指標設定說明");
        dayTradeGuideItem.addActionListener(e -> showDayTradeIndicatorGuideDialog());
        helpMenu.add(dayTradeGuideItem);

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
        JButton loadHistoryBtn = new JButton("載入範圍分K到SQL");
        loadHistoryBtn.setToolTipText("批量補齊指定日期範圍的觀察清單分K；SQL M1 分K時段覆蓋完整的股票日期不再呼叫 FinMind API");
        loadHistoryBtn.addActionListener(e -> importDateRangeKBarToSql());
        toolBar.add(loadHistoryBtn);

        JButton taiexBtn = new JButton("TAIEX");
        taiexBtn.setToolTipText("從 SQL 載入加權指數到主圖並更新大盤分析");
        taiexBtn.addActionListener(e -> loadMarketIndexToChart("TAIEX"));
        toolBar.add(taiexBtn);

        JButton tpexBtn = new JButton("TPEx");
        tpexBtn.setToolTipText("從 SQL 載入櫃買指數到主圖並更新大盤分析");
        tpexBtn.addActionListener(e -> loadMarketIndexToChart("TPEx"));
        toolBar.add(tpexBtn);

        toolBar.addSeparator();

        // 指標選擇
        toolBar.add(new JLabel(" 指標 "));
        String[] indicators = {"無", "SMA", "EMA", "RSI", "MACD", "BOLL", "KD", "ADX", "OBV", "CCI", "WR"};
        JComboBox<String> indicatorCombo = new JComboBox<>(indicators);
        indicatorCombo.setMaximumSize(new Dimension(120, 25));
        indicatorCombo.addActionListener(e -> changeIndicator((String) indicatorCombo.getSelectedItem()));
        toolBar.add(indicatorCombo);
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

        JButton sqlRadarReplayBtn = new JButton("雷達開盤重播");
        sqlRadarReplayBtn.setToolTipText("用 SQL 指定日期資料模擬開盤雷達逐步掃描");
        sqlRadarReplayBtn.addActionListener(e -> startSqlRadarOpeningReplay());
        toolBar.add(sqlRadarReplayBtn);

        JButton stopSqlRadarReplayBtn = new JButton("停止重播");
        stopSqlRadarReplayBtn.setToolTipText("停止目前 SQL 雷達開盤重播");
        stopSqlRadarReplayBtn.addActionListener(e -> stopSqlRadarOpeningReplay());
        toolBar.add(stopSqlRadarReplayBtn);
        
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
            JMenuItem dayTradeGuideItem = new JMenuItem("當沖指標設定說明");
            dayTradeGuideItem.addActionListener(event -> showDayTradeIndicatorGuideDialog());
            popup.add(dayTradeGuideItem);
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
                new PanelEntry("marketStatus", "大盤狀態"),
                new PanelEntry("industryDistribution", "產業分布"),
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

                2026-05-29 replay fix:
                - SQL 雷達開盤重播在 ticks 模式會依 replay time 即時聚合 partial K 棒，避免使用盤後完整 M5 K 的未來資料。
                - 比對實盤自動監控時，請使用 TICKS_AGGREGATED 並確認掃描間隔 / 秒偏移與實盤一致。

                SQL 雷達重播更新：
                - 可選資料來源模式：固定 ticks 聚合、固定 candlesticks、或 AUTO。
                - 比對盤中實盤雷達時建議使用固定 ticks 聚合，避免盤後補入 candlesticks 後重播結果改變。
                - 雷達重播 CSV 會記錄策略名稱、完整設定摘要、資料來源模式、實際載入來源、開單/阻擋原因與加分明細。

                Java Swing 台股交易分析與模擬平台
                版本：0.2.x

                目前定位：
                - 盤中行情與雷達掃描以 MarketDataCollectorFeed 讀取本機 market_data MySQL。
                - FinMind 保留低頻資料、盤後資料、手動查詢與 SQL 匯入，不作為 DreamHouseTrading 盤中即時行情來源。
                - Yahoo 保留為一般行情資料源。
                - 系統只做分析、掃描、決策、回測與模擬交易，不做真實券商下單。

                最近更新：
                - 大盤狀態加入 TAIEX / TPEx，支援指定日期 SQL 資料載入與市場狀態判斷。
                - 產業分布改用 SQL 產業全體股票計算，不再只依觀察清單估算。
                - 產業選股支援加入整個族群、刪除整個族群，並同步 MarketDataCollector symbols.properties。
                - 觀察清單支援批量新增與批量刪除。
                - 當沖策略支援 VWAP、Volume Sustain、Market Regime、族群相對強弱與 ATR 風控紀錄。
                - 交易紀錄與 SQL 雷達回測可輸出含開平倉理由、損益與技術指標的報告。
                """);
        message.setEditable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(54);
        UIAutoScaler.apply(this);
        JOptionPane.showMessageDialog(this, message, "關於 DreamHouseTrading", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDayTradeIndicatorGuideDialog() {
        JTextArea message = new JTextArea("""
                2026-05-29 replay fix:
                - TICKS_AGGREGATED 重播會用 replay time 以前的 SQL ticks 聚合 partial K 棒，避免 09:30:20 看到 09:30~09:34:59 的未來完整 K。
                - 如果只用 candlesticks，重播只能使用已收完 K 線，不能精準模擬盤中 partial K。

                SQL 雷達重播與資料來源：
                - 固定 ticks 聚合：最接近盤中實盤雷達，適合檢查為何實盤有/沒有開單。
                - 固定 candlesticks：適合檢查盤後補入的分 K 資料。
                - AUTO：candlesticks 完整時優先使用 candlesticks，否則用 ticks 聚合；同一天盤後補資料前後可能跑出不同結果。
                - 匯出的 CSV 會記錄資料來源、策略設定、加分明細、OPEN_LONG、阻擋原因與交易事件。

                當沖指標設定詳細說明

                一、資料來源
                - 盤中雷達、K 線、VWAP、成交量、大盤與族群強度都應優先讀取本機 SQL。
                - DreamHouseTrading 不直接呼叫 FinMind 即時行情；FinMind 主要用於盤後 K 線、產業鏈、分點、新聞與手動匯入。
                - 指定日期回測與圖表載入會依目前選取日期讀 SQL，不限定今天。

                二、固定交易語意
                - 自動監控 / 今日機會雷達開多一律視為 DAY_TRADE。
                - 當沖數量固定 1000 股；資金不足買一張會拒單，不拆零股。
                - 13:25 後禁止自動監控新開倉，並平掉 auto-managed 未平倉部位。
                - 09:00~09:10 預設只收資料不開倉，可在監控設定調整。
                - 停損後同股票預設冷卻 60 分鐘，可在監控設定調整。

                三、進場濾網
                - SignalRSI = SHORT 時禁止自動監控 OPEN_LONG。
                - RSI 超賣不得單獨開多，至少需要 EMA 未明顯下彎或放量反轉確認。
                - B 模板會檢查 VWAP 結構、量能延續、最大持倉、冷卻與收盤時間。
                - 弱勢盤仍可開多，但必須同時強於自身 VWAP、強於內部基準、強於觀察清單群體。

                四、市場與族群
                - TAIEX / TPEx 用於 Market Regime：TREND_UP、RANGE、WEAK、DATA_MISSING。
                - TWSE 股票對比 TAIEX；TPEx 股票對比 TPEx。
                - 弱勢盤放行門檻：個股日內表現至少強於內部基準 0.3%，強於觀察清單群體 0.2%。
                - 族群強度使用 FinMind TaiwanStockIndustryChain 匯入 SQL 後的產業全體股票計算。

                五、風控與報告
                - ATR 用於動態停損、停利、追高限制與 RR 評估。
                - 最大同時持倉數在自動開倉前檢查，避免過度分散。
                - 回測報告會記錄 RSI、EMA、VWAP、VWAP slope、Volume Sustain、Regime、Industry、ATR 與開平倉理由。

                靜態設定：張數、時間窗、停損冷卻分鐘、最大持倉、弱勢盤門檻與模板權重。
                動態判斷：市場狀態、VWAP / slope、量能延續、ATR、族群強度、個股相對大盤與族群強弱。
                """);
        message.setEditable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(72);
        message.setRows(28);
        JScrollPane scrollPane = new JScrollPane(message);
        UIAutoScaler.apply(this);
        JOptionPane.showMessageDialog(this, scrollPane, "當沖指標設定說明", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void subscribeMarketData() {
        // 創建新的監聽器
        currentMarketDataListener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                if (tick.getSymbol() == null
                        || !watchlistMarketSubscriptions.contains(tick.getSymbol().trim().toUpperCase(Locale.ROOT))) {
                    handleMarketPriceUpdate(tick);
                }
                if (isTickForCurrentChart(tick)) {
                    lastPrice = tick.getPrice();
                    statusBar.setLastPrice(lastPrice, 0);
                    frameCount++;
                    chartDock.onTick(tick);
                }
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

        LocalDate sessionDate = tick.getTimestamp() != null
                ? tick.getTimestamp().toLocalDate()
                : LocalDate.now(TAIPEI_ZONE);
        double referencePrice = watchlistOpenPrices.computeIfAbsent(
                symbol,
                ignored -> resolveWatchlistReferencePrice(symbol, sessionDate, price));
        long volume = resolveWatchlistVolume(tick);
        watchlistVolumes.put(symbol, volume);
        double changePct = referencePrice > 0.0 ? ((price - referencePrice) / referencePrice) * 100.0 : 0.0;

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
            closeRangePositionIfFailureTriggered(symbol, price);
        });
    }

    private double resolveWatchlistReferencePrice(String symbol, LocalDate sessionDate, double fallbackPrice) {
        if (symbol == null || symbol.isBlank() || sessionDate == null) {
            return fallbackPrice;
        }
        try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                dataSourceManager.getMarketCollectorJdbcUrl(),
                dataSourceManager.getMarketCollectorUser(),
                dataSourceManager.getMarketCollectorPassword())) {
            OptionalDouble previousClose = repository.findPreviousClosePrice(symbol, sessionDate);
            if (previousClose.isPresent() && previousClose.getAsDouble() > 0.0) {
                return previousClose.getAsDouble();
            }
        } catch (Exception e) {
            System.err.println("[MainFrame] 無法讀取 " + symbol + " 昨收，改用目前價格作為觀察清單基準: " + e.getMessage());
        }
        return fallbackPrice;
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
            paperTradeRecorder.record(result, null, latestScanResults.get(symbol), "day-trade-cutoff", monitorStrategyName, buildMonitorStrategyDetails());
            clearAutoPositionState(symbol);
            if (executionStatusDock != null) {
                executionStatusDock.setExecutionEngine(executionEngine);
                executionStatusDock.updateMarketPrice(symbol, closePrice);
            }
            statusBar.setText(result.isSuccess()
                    ? reason + "：" + symbol + " @ " + String.format("%.2f", closePrice)
                    : reason + "失敗：" + symbol + "，" + result.getMessage());
        }
    }

    private boolean isTickForCurrentChart(Tick tick) {
        if (tick == null) {
            return false;
        }
        String tickSymbol = tick.getSymbol();
        if (tickSymbol == null || tickSymbol.isBlank()) {
            return true;
        }
        if (currentSymbol == null || currentSymbol.isBlank()) {
            return false;
        }
        return StockNameResolver.normalize(currentSymbol).equals(StockNameResolver.normalize(tickSymbol));
    }

    private void closeAutoPositionIfStopTriggered(String symbol, double price) {
        if (!autoTradingEnabled || executionEngine == null || monitorPortfolio == null) {
            return;
        }
        Position position = monitorPortfolio.getPosition(symbol);
        if (position == null || position.getQuantity() <= 0) {
            clearAutoPositionState(symbol);
            return;
        }

        if (closeAutoPositionIfReasonInvalidated(symbol, price, position)) {
            return;
        }
        updateAutoTrailingStop(symbol, price);

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
        paperTradeRecorder.record(result, null, latestScanResults.get(symbol), "auto-stop", monitorStrategyName, buildMonitorStrategyDetails());
        clearAutoPositionState(symbol);
        if (result.isSuccess() && stopLossTriggered) {
            registerStopLossCooldown(symbol);
        }
        if (result.isSuccess()) {
            registerPostExitCooldown(symbol);
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

    private void closeRangePositionIfFailureTriggered(String symbol, double price) {
        if (!autoTradingEnabled || executionEngine == null || monitorPortfolio == null || monitorConfig == null
                || !monitorConfig.isRangeFailureExitEnabled() || symbol == null || !autoRangeEntries.contains(symbol)) {
            return;
        }
        Position position = monitorPortfolio.getPosition(symbol);
        if (position == null || position.getQuantity() <= 0) {
            clearAutoPositionState(symbol);
            return;
        }
        LocalDateTime entryTime = autoEntryTimes.getOrDefault(symbol, position.getOpenTime());
        long holdingMinutes = Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        MarketScanResult scanResult = latestScanResults.get(symbol);
        String reason = null;

        if (monitorConfig.isRangeFailureVolumeSustainExitEnabled()
                && holdingMinutes >= 5
                && scanResult != null
                && Boolean.FALSE.equals(scanResult.getVolumeSustain())) {
            reason = "RANGE 盤動能失效出場：Volume Sustain 轉弱";
        }

        Double entryPrice = autoEntryPrices.get(symbol);
        Double riskAmount = autoEntryRiskAmounts.get(symbol);
        if (reason == null
                && holdingMinutes >= monitorConfig.getRangeFailureExitMinutes()
                && entryPrice != null
                && riskAmount != null
                && riskAmount > 0.0) {
            double targetPrice = entryPrice + riskAmount * monitorConfig.getRangeFailureMinR();
            if (price < targetPrice) {
                reason = String.format(Locale.US,
                        "RANGE 盤時間失效出場：持倉 %d 分鐘仍未達 %.2fR（目標 %.2f，現價 %.2f）",
                        holdingMinutes,
                        monitorConfig.getRangeFailureMinR(),
                        targetPrice,
                        price);
            }
        }

        if (reason == null) {
            return;
        }

        ExecutionResult result = executionEngine.closePosition(
                symbol,
                position.getQuantity(),
                price,
                com.dreamhouse.trading.core.execution.OrderType.MARKET,
                reason);
        paperTradeRecorder.record(result, null, scanResult, "range-failure-exit", monitorStrategyName, buildMonitorStrategyDetails());
        clearAutoPositionState(symbol);
        if (result.isSuccess()) {
            registerPostExitCooldown(symbol);
            updateAutoMonitorRiskAfterClose(symbol, result, null);
            statusBar.setText(reason + "，已自動平倉 " + symbol);
        } else {
            statusBar.setText("RANGE 盤失效平倉失敗 " + symbol + "：" + result.getMessage());
        }
        if (executionStatusDock != null) {
            executionStatusDock.setExecutionEngine(executionEngine);
            executionStatusDock.updateMarketPrice(symbol, price);
        }
    }

    private void updateAutoTrailingStop(String symbol, double price) {
        Double entryPrice = autoEntryPrices.get(symbol);
        Double riskAmount = autoEntryRiskAmounts.get(symbol);
        if (entryPrice == null || riskAmount == null || riskAmount <= 0.0 || price < entryPrice + riskAmount) {
            return;
        }
        double breakEvenStop = entryPrice + riskAmount * 0.05;
        Double currentStop = activeStopLosses.get(symbol);
        if (currentStop == null || currentStop < breakEvenStop) {
            activeStopLosses.put(symbol, breakEvenStop);
            statusBar.setText(String.format(Locale.US,
                    "三層風控移動停損：%s 已達 1R，停損上移至 %.2f",
                    symbol,
                    breakEvenStop));
        }
    }

    private boolean closeAutoPositionIfReasonInvalidated(String symbol, double price, Position position) {
        MarketScanResult scanResult = latestScanResults.get(symbol);
        if (scanResult == null) {
            return false;
        }
        LocalDateTime entryTime = autoEntryTimes.getOrDefault(symbol, position.getOpenTime());
        long holdingMinutes = Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        Double entryPrice = autoEntryPrices.get(symbol);
        Double riskAmount = autoEntryRiskAmounts.get(symbol);
        String reason = null;

        if (holdingMinutes >= 3
                && scanResult.getVwap() != null
                && scanResult.getVwap() > 0.0
                && price < scanResult.getVwap()) {
            reason = String.format(Locale.US,
                    "EXIT_VWAP_BREAK：最新價 %.2f 跌破 VWAP %.2f",
                    price,
                    scanResult.getVwap());
        }
        if (reason == null
                && holdingMinutes >= 5
                && Boolean.FALSE.equals(scanResult.getVolumeSustain())) {
            double requiredProgress = entryPrice != null && riskAmount != null && riskAmount > 0.0
                    ? entryPrice + riskAmount * 0.50
                    : Double.POSITIVE_INFINITY;
            if (price < requiredProgress) {
                reason = String.format(Locale.US,
                        "EXIT_VOLUME_FAIL：量能延續失效，最新價 %.2f 未達 0.5R",
                        price);
            }
        }
        if (reason == null) {
            return false;
        }

        ExecutionResult result = executionEngine.closePosition(
                symbol,
                position.getQuantity(),
                price,
                com.dreamhouse.trading.core.execution.OrderType.MARKET,
                reason);
        paperTradeRecorder.record(result, null, scanResult, "reason-invalidated-exit", monitorStrategyName, buildMonitorStrategyDetails());
        clearAutoPositionState(symbol);
        if (result.isSuccess()) {
            registerPostExitCooldown(symbol);
            updateAutoMonitorRiskAfterClose(symbol, result, null);
            statusBar.setText(reason + "，已自動平倉 " + symbol);
        } else {
            statusBar.setText("理由失效平倉失敗 " + symbol + "：" + result.getMessage());
        }
        if (executionStatusDock != null) {
            executionStatusDock.setExecutionEngine(executionEngine);
            executionStatusDock.updateMarketPrice(symbol, price);
        }
        return result.isSuccess();
    }

    private void clearAutoPositionState(String symbol) {
        activeStopLosses.remove(symbol);
        activeTakeProfits.remove(symbol);
        activeTradeModes.remove(symbol);
        autoManagedPositions.remove(symbol);
        pendingAutoEntries.remove(symbol);
        autoEntryTimes.remove(symbol);
        autoEntryPrices.remove(symbol);
        autoEntryRiskAmounts.remove(symbol);
        autoRangeEntries.remove(symbol);
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

    private void loadMarketIndexToChart(String indexSymbol) {
        if (!(dataFeed instanceof MarketDataCollectorFeed)) {
            statusBar.setText("大盤圖表目前只從 MarketDataCollector SQL 讀取，請先切換資料源。");
            return;
        }
        String previousSymbol = currentSymbol;
        currentSymbol = indexSymbol;
        chartDock.clearAllData();
        statusBar.setText("正在從 SQL 載入 " + indexSymbol + " 大盤資料...");
        new Thread(() -> {
            loadChartData(indexSymbol, currentTimeframe, customBarCount);
            refreshMarketAndIndustryDocksFromSqlV2();
            System.out.println("[MainFrame] 大盤圖表切換: " + previousSymbol + " -> " + indexSymbol);
        }, "MarketIndexChartLoader").start();
    }

    private void changeDateQuery(java.time.LocalDate date) {
        selectedQueryDate = date != null ? date : LocalDate.now(TAIPEI_ZONE);
        watchlistOpenPrices.clear();
        System.out.println("[MainFrame] 切換查詢日期: " + date);

        // 如果當前數據源是 FinMindFeed，設置查詢日期
        if (dataFeed instanceof com.dreamhouse.trading.core.FinMindFeed) {
            ((com.dreamhouse.trading.core.FinMindFeed) dataFeed).setQueryDate(selectedQueryDate);
            statusBar.setText("已切換至 " + selectedQueryDate + " 的數據；可按「載入日期分K到SQL」批量匯入觀察清單");
        } else if (dataFeed instanceof MarketDataCollectorFeed) {
            statusBar.setText("已切換 SQL K 線查詢日期：" + selectedQueryDate);
            updateWatchlistForSelectedSqlDate();
            refreshMarketAndIndustryDocksFromSqlV2();
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

    private void importDateRangeKBarToSql() {
        List<String> symbols = resolveImportSymbols();
        if (symbols.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "觀察清單沒有股票，也沒有目前商品可匯入。",
                    "無可匯入股票",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate[] range = promptKBarImportDateRange();
        if (range == null) {
            return;
        }
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];
        long calendarDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        System.out.println("[MainFrame] 手動觸發 FinMind 分K補齊 SQL，日期範圍: " + startDate + " ~ " + endDate);

        int confirm = JOptionPane.showConfirmDialog(this,
                "將補齊 " + startDate + " ~ " + endDate + " 的 FinMind TaiwanStockKBar 分K。\n"
                        + "股票數：" + symbols.size() + "\n"
                        + "日期數：" + calendarDays + "\n"
                        + "API 上限：約 " + (calendarDays * symbols.size()) + " 次；SQL M1 分K完整者略過\n"
                        + "週六、週日會直接略過；休市日若 SQL 無資料仍可能收到 FinMind 0 筆結果\n"
                        + "寫入：market_data.candlesticks 的 M1/M5/M15/M30/H1\n\n"
                        + "是否開始？",
                "批量補齊分K到SQL",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        statusBar.setText("正在補齊 " + startDate + " ~ " + endDate + " 分K到 SQL...");
        SwingWorker<FinMindKBarSqlImporter.RangeImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FinMindKBarSqlImporter.RangeImportResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    FinMindKBarSqlImporter importer = new FinMindKBarSqlImporter(
                            new FinMindClient(dataSourceManager.getFinMindApiToken()),
                            repository);
                    return importer.importMissingSymbols(symbols, startDate, endDate);
                }
            }

            @Override
            protected void done() {
                try {
                    FinMindKBarSqlImporter.RangeImportResult result = get();
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

    private LocalDate[] promptKBarImportDateRange() {
        JTextField startField = new JTextField(selectedQueryDate.toString(), 12);
        JTextField endField = new JTextField(selectedQueryDate.toString(), 12);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("開始日期"), gbc);
        gbc.gridx = 1;
        panel.add(startField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("結束日期"), gbc);
        gbc.gridx = 1;
        panel.add(endField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(new JLabel("格式：yyyy-MM-dd；SQL M1 分K根數與開收盤覆蓋完整時不打 API。"), gbc);

        while (true) {
            int option = JOptionPane.showConfirmDialog(
                    this,
                    panel,
                    "載入範圍分K到SQL",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                return null;
            }
            try {
                LocalDate start = LocalDate.parse(startField.getText().trim());
                LocalDate end = LocalDate.parse(endField.getText().trim());
                if (end.isBefore(start)) {
                    JOptionPane.showMessageDialog(this, "結束日期不可早於開始日期。", "載入範圍分K到SQL", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                return new LocalDate[]{start, end};
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(this, "日期格式錯誤，請使用 yyyy-MM-dd。", "載入範圍分K到SQL", JOptionPane.WARNING_MESSAGE);
            }
        }
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

    private void showFinMindKBarImportResult(FinMindKBarSqlImporter.RangeImportResult result) {
        statusBar.setText("FinMind 分K補齊完成：API " + result.apiRequests()
                + " 次，匯入 " + result.successImports() + " 組股票日期，略過 SQL 完整 " + result.skippedExisting() + " 組");

        StringBuilder detail = new StringBuilder();
        detail.append("日期範圍：").append(result.startDate()).append(" ~ ").append(result.endDate()).append('\n');
        detail.append("掃描日期：").append(result.daysScanned()).append(" 天\n");
        detail.append("股票數：").append(result.requestedSymbols()).append(" 檔\n");
        detail.append("實際 API 呼叫：").append(result.apiRequests()).append(" 次\n");
        detail.append("成功匯入：").append(result.successImports()).append(" 組股票日期\n");
        detail.append("SQL 分K完整略過：").append(result.skippedExisting()).append(" 組股票日期\n");
        detail.append("寫入K線：").append(result.totalInsertedBars()).append(" 根\n\n");
        for (FinMindKBarSqlImporter.DateImportResult dateResult : result.dateResults()) {
            detail.append("[").append(dateResult.date()).append("] ");
            if (dateResult.marketClosed()) {
                detail.append("週末略過 ").append(dateResult.marketClosedSkippedSymbols()).append(" 檔\n");
                continue;
            }
            detail.append('\n');
            for (FinMindKBarSqlImporter.SymbolImportResult item : dateResult.symbolResults()) {
                if (item.skippedExisting()) {
                    detail.append("略過 ");
                } else {
                    detail.append(item.success() ? "OK " : "失敗 ");
                }
                detail.append(item.symbol()).append("：");
                if (item.success()) {
                    detail.append(item.insertedByInterval());
                } else {
                    detail.append(item.message());
                }
                detail.append('\n');
            }
        }

        JTextArea area = new JTextArea(detail.toString(), 22, 78);
        area.setEditable(false);
        area.setLineWrap(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "FinMind 分K補齊結果", JOptionPane.INFORMATION_MESSAGE);
    }

    private void importFinMindIndustryToSql() {
        statusBar.setText("正在匯入 FinMind 台股基本資料與產業鏈到 SQL...");
        SwingWorker<FinMindIndustryImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected FinMindIndustryImportResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    FinMindClient client = new FinMindClient(dataSourceManager.getFinMindApiToken());
                    JsonNode stockInfoRoot = client.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_INFO)
                            .build());
                    JsonNode stockInfoRows = stockInfoRoot != null ? stockInfoRoot.path("data") : null;
                    MarketDataCollectorRepository.FinMindSqlWriteResult stockInfoWrite =
                            repository.writeFinMindDatasetRows(
                                    FinMindDataset.TAIWAN_STOCK_INFO,
                                    null,
                                    null,
                                    null,
                                    stockInfoRows);

                    FinMindIndustryChainImporter importer = new FinMindIndustryChainImporter(
                            client,
                            repository);
                    FinMindIndustryChainImporter.ImportResult industryResult = importer.importIndustryChain();
                    List<IndustryStrength> industryRows = repository.findIndustrySummaries().stream()
                            .map(summary -> new IndustryStrength(
                                    summary.industry(),
                                    0.0,
                                    0.0,
                                    summary.symbolCount(),
                                    0,
                                    0.0))
                            .toList();
                    List<MarketDataCollectorRepository.IndustryStockInfo> stockRows =
                            repository.findIndustryStockRows();
                    return new FinMindIndustryImportResult(stockInfoWrite, industryResult, industryRows, stockRows);
                }
            }

            @Override
            protected void done() {
                try {
                    FinMindIndustryImportResult result = get();
                    statusBar.setText("FinMind 產業匯入完成：台股基本資料 raw "
                            + result.stockInfoWrite().rawRows()
                            + " 筆，產業鏈解析 " + result.industryResult().parsedRows()
                            + " 筆，寫入 industry_chain " + result.industryResult().insertedRows() + " 筆");
                    if (industryDistributionDock != null) {
                        industryDistributionDock.updateIndustryData(
                                result.industryRows(),
                                result.stockRows(),
                                Set.copyOf(watchlistPanel != null ? watchlistPanel.getSymbols() : List.of()));
                        showDockablePanel("industryDistribution", "產業分布");
                    }
                    refreshMarketAndIndustryDocksFromSqlV2();
                    scanWatchlistForOpportunitiesAsync();
                    showFinMindIndustryImportResult(result);
                } catch (Exception e) {
                    String message = rootCauseMessage(e);
                    statusBar.setText("FinMind 產業匯入失敗：" + message);
                    JOptionPane.showMessageDialog(MainFrameWithDocking.this,
                            "FinMind 產業匯入失敗：\n" + message + "\n\n"
                                    + "此功能會先查 TaiwanStockInfo，再查 TaiwanStockIndustryChain。"
                                    + "\n請確認 API token、會員權限與 MarketDataCollector MySQL 連線。",
                            "FinMind 產業匯入",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showFinMindIndustryImportResult(FinMindIndustryImportResult result) {
        String message = "FinMind 產業匯入完成\n\n"
                + "1. TaiwanStockInfo 已寫入 raw SQL\n"
                + "   table: " + result.stockInfoWrite().tableName() + "\n"
                + "   API rows: " + result.stockInfoWrite().rawRows() + "\n"
                + "   SQL rows: " + result.stockInfoWrite().cachedRows() + "\n\n"
                + "2. TaiwanStockIndustryChain 已寫入 industry_chain\n"
                + "   parsed rows: " + result.industryResult().parsedRows() + "\n"
                + "   inserted rows: " + result.industryResult().insertedRows() + "\n\n"
                + "產業分布 UI 已刷新。";
        JOptionPane.showMessageDialog(
                this,
                message,
                "FinMind 產業匯入",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private record FinMindIndustryImportResult(
            MarketDataCollectorRepository.FinMindSqlWriteResult stockInfoWrite,
            FinMindIndustryChainImporter.ImportResult industryResult,
            List<IndustryStrength> industryRows,
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows) {
    }

    private void generateAfterHoursStockPool() {
        if (watchlistPanel == null) {
            JOptionPane.showMessageDialog(this, "觀察清單尚未初始化。", "隔日當沖股票池", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> sourceSymbols = watchlistPanel.getSymbols();
        if (sourceSymbols == null || sourceSymbols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "請先放入候選股票到觀察清單，再產生隔日股票池。", "隔日當沖股票池", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField dateField = new JTextField(selectedQueryDate.toString(), 12);
        JSpinner topNSpinner = new JSpinner(new SpinnerNumberModel(Math.min(30, Math.max(1, sourceSymbols.size())), 1, 100, 1));
        JCheckBox includeBrokerChips = new JCheckBox("加入分點籌碼評分（Sponsor API）", true);
        JCheckBox updateWatchlist = new JCheckBox("產生後更新觀察清單與 MarketDataCollector symbols.properties", true);
        JCheckBox replaceWatchlist = new JCheckBox("用入選股票取代目前觀察清單", false);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("盤後資料日期"), gbc);
        gbc.gridx = 1;
        panel.add(dateField, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        panel.add(new JLabel("入選檔數"), gbc);
        gbc.gridx = 1;
        panel.add(topNSpinner, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(includeBrokerChips, gbc);
        gbc.gridy++;
        panel.add(updateWatchlist, gbc);
        gbc.gridy++;
        panel.add(replaceWatchlist, gbc);
        gbc.gridy++;
        panel.add(new JLabel("候選來源：目前觀察清單 " + sourceSymbols.size() + " 檔"), gbc);

        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "產生隔日當沖股票池",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }

        LocalDate poolDate;
        try {
            poolDate = LocalDate.parse(dateField.getText().trim());
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, "日期格式錯誤，請使用 yyyy-MM-dd。", "隔日當沖股票池", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int topN = ((Number) topNSpinner.getValue()).intValue();
        boolean brokerEnabled = includeBrokerChips.isSelected();
        boolean shouldUpdateWatchlist = updateWatchlist.isSelected();
        boolean shouldReplaceWatchlist = replaceWatchlist.isSelected();

        statusBar.setText("隔日當沖股票池產生中：" + poolDate + "，候選 " + sourceSymbols.size() + " 檔");
        SwingWorker<AfterHoursStockPoolService.StockPoolResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AfterHoursStockPoolService.StockPoolResult doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    AfterHoursStockPoolService service = new AfterHoursStockPoolService(
                            new FinMindClient(dataSourceManager.getFinMindApiToken()),
                            repository);
                    return service.buildPool(sourceSymbols, poolDate, topN, brokerEnabled);
                }
            }

            @Override
            protected void done() {
                try {
                    AfterHoursStockPoolService.StockPoolResult result = get();
                    List<String> selectedSymbols = result.selectedCandidates().stream()
                            .map(AfterHoursStockPoolService.StockPoolCandidate::symbol)
                            .toList();
                    if (shouldUpdateWatchlist && !selectedSymbols.isEmpty()) {
                        if (shouldReplaceWatchlist) {
                            Set<String> locked = watchlistPanel.getLockedSymbols();
                            List<String> toRemove = watchlistPanel.getSymbols().stream()
                                    .filter(symbol -> !selectedSymbols.contains(symbol))
                                    .filter(symbol -> !locked.contains(StockNameResolver.normalize(symbol)))
                                    .toList();
                            removeSymbolsFromWatchlistInternal(toRemove);
                        }
                        addSymbolsToWatchlistInternal(selectedSymbols);
                    }
                    showAfterHoursStockPoolResult(result, shouldUpdateWatchlist, shouldReplaceWatchlist);
                    statusBar.setText("隔日當沖股票池完成：" + result.selectedCandidates().size()
                            + " / " + result.allCandidates().size() + " 檔入選");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusBar.setText("隔日當沖股票池失敗：" + cause.getMessage());
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "隔日當沖股票池失敗：\n" + cause.getMessage(),
                            "隔日當沖股票池",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showAfterHoursStockPoolResult(
            AfterHoursStockPoolService.StockPoolResult result,
            boolean updatedWatchlist,
            boolean replacedWatchlist) {
        String[] columns = {
                "排名", "代碼", "中文", "總分", "趨勢", "量能", "型態", "當沖", "分點", "風險扣分",
                "分點買賣超(張)", "集中度", "風險旗標", "理由"
        };
        columns = new String[] {
                "排名", "代碼", "中文", "總分", "趨勢", "量能", "型態", "當沖", "分點", "風險扣分",
                "分點筆數", "分點家數", "最大買超分點", "最大買超(張)", "最大賣超分點", "最大賣超(張)",
                "集中度", "風險旗標", "理由"
        };
        Object[][] rows = new Object[result.allCandidates().size()][columns.length];
        for (int i = 0; i < result.allCandidates().size(); i++) {
            AfterHoursStockPoolService.StockPoolCandidate candidate = result.allCandidates().get(i);
            rows[i][0] = i + 1;
            rows[i][1] = candidate.symbol();
            rows[i][2] = candidate.chineseName();
            rows[i][3] = candidate.totalScore();
            rows[i][4] = candidate.trendScore();
            rows[i][5] = candidate.volumeScore();
            rows[i][6] = candidate.patternScore();
            rows[i][7] = candidate.dayTradingScore();
            rows[i][8] = candidate.brokerChipScore();
            rows[i][9] = candidate.riskPenalty();
            rows[i][10] = candidate.brokerRowCount();
            rows[i][11] = candidate.brokerBranchCount();
            rows[i][12] = candidate.brokerTopBuyer();
            rows[i][13] = candidate.brokerNetBuyLots();
            rows[i][14] = candidate.brokerTopSeller();
            rows[i][15] = candidate.brokerTopSellerNetSellLots();
            rows[i][16] = candidate.brokerConcentration();
            rows[i][17] = candidate.brokerRiskFlag();
            rows[i][18] = candidate.reason();
        }
        JTable table = new JTable(new DefaultTableModel(rows, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0, 10, 11 -> Integer.class;
                    case 3, 4, 5, 6, 7, 8, 9, 13, 15, 16 -> Double.class;
                    default -> String.class;
                };
            }
        });
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(12).setPreferredWidth(160);
        table.getColumnModel().getColumn(14).setPreferredWidth(160);
        table.getColumnModel().getColumn(18).setPreferredWidth(760);

        StringBuilder summary = new StringBuilder();
        summary.append(result.brokerDataSummary()).append('\n');
        summary.append("資料日期：").append(result.date()).append('\n');
        summary.append("候選成功：").append(result.allCandidates().size()).append(" 檔\n");
        summary.append("入選：").append(result.selectedCandidates().size()).append(" 檔\n");
        summary.append("觀察清單：").append(updatedWatchlist ? (replacedWatchlist ? "已取代" : "已加入入選股票") : "未更新").append('\n');
        if (!result.skipped().isEmpty()) {
            summary.append("略過：").append(result.skipped().size()).append(" 檔\n");
        }

        JTextArea summaryArea = new JTextArea(summary.toString());
        summaryArea.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(summaryArea, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(1180, 620));

        JOptionPane.showMessageDialog(
                this,
                panel,
                "隔日當沖股票池結果",
                JOptionPane.INFORMATION_MESSAGE);
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

    private void loadImportedCurrentSymbolBars(FinMindKBarSqlImporter.RangeImportResult result) {
        if (currentSymbol == null || currentSymbol.isBlank()
                || selectedQueryDate.isBefore(result.startDate()) || selectedQueryDate.isAfter(result.endDate())) {
            return;
        }
        boolean selectedDateAvailable = result.dateResults().stream()
                .filter(dateResult -> selectedQueryDate.equals(dateResult.date()) && !dateResult.marketClosed())
                .flatMap(dateResult -> dateResult.symbolResults().stream())
                .anyMatch(item -> currentSymbol.equals(item.symbol()) && (item.success() || item.skippedExisting()));
        if (!selectedDateAvailable) {
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
                updateWatchlistForSelectedSqlDate();
            }
        } catch (Exception e) {
            statusBar.setText("分K已補齊，但載入圖表失敗：" + e.getMessage());
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
                        OptionalDouble previousClose = repository.findPreviousClosePrice(symbol, selectedQueryDate);
                        publish(toWatchlistSnapshot(symbol, bars, previousClose));
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

    private WatchlistSnapshot toWatchlistSnapshot(String symbol, List<Bar> bars, OptionalDouble previousClose) {
        if (bars == null || bars.isEmpty()) {
            return new WatchlistSnapshot(symbol, 0.0, 0.0, 0L);
        }
        Bar first = bars.get(0);
        Bar last = bars.get(bars.size() - 1);
        double referencePrice = previousClose != null && previousClose.isPresent() && previousClose.getAsDouble() > 0.0
                ? previousClose.getAsDouble()
                : first.getOpen();
        double changePct = referencePrice > 0.0
                ? ((last.getClose() - referencePrice) / referencePrice) * 100.0
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
            private MarketContextSnapshot marketContext;

            @Override
            protected List<MarketScanResult> doInBackground() {
                MarketScannerService scannerService = new MarketScannerService(dataFeed);
                marketContext = buildInternalMarketContext(symbols, TradeMode.DAY_TRADE);
                List<MarketScanResult> results = new ArrayList<>();
                for (String symbol : symbols) {
                    MarketScanResult bestResult = findBestRadarCandidate(scannerService, symbol, marketContext);
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
                    updateMarketContextDocks(marketContext);
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

    private MarketScanResult findBestRadarCandidate(
            MarketScannerService scannerService,
            String symbol,
            MarketContextSnapshot marketContext) {
        MarketScanResult best = null;
        for (TradeMode mode : List.of(TradeMode.DAY_TRADE)) {
            MarketScanResult candidate = scannerService.scan(symbol, createRadarScanRequest(mode).marketContext(marketContext));
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
                    : RadarStrategyConfig.createDefault())
            .asOfTime(LocalDateTime.now());
    }

    private MarketContextSnapshot buildMarketContextWithIndustryUniverse(List<String> symbols, TradeMode mode) {
        if (symbols == null || symbols.isEmpty()) {
            return MarketContextSnapshot.empty("觀察清單沒有商品");
        }
        Timeframe timeframe = resolveRadarTimeframe(mode);
        int barCount = resolveRadarBarCount(mode);
        IndustryUniverseData universeData = loadIndustryUniverseData(selectedQueryDate);
        MarketContextService contextService = new MarketContextService(
                dataFeed,
                symbol -> universeData.industryBySymbol().getOrDefault(normalizeSymbolForIndustry(symbol), "未分類"));
        return contextService.build(
                symbols,
                timeframe,
                barCount,
                selectedQueryDate,
                universeData.availableIndustryBySymbol(),
                universeData.totalCountsByIndustry());
    }

    private MarketContextSnapshot buildMarketContext(List<String> symbols, TradeMode mode) {
        if (symbols == null || symbols.isEmpty()) {
            return MarketContextSnapshot.empty("觀察清單沒有商品");
        }
        Timeframe timeframe = resolveRadarTimeframe(mode);
        int barCount = resolveRadarBarCount(mode);
        Map<String, MarketDataCollectorRepository.IndustryInfo> industryInfos = loadIndustryInfo(symbols);
        MarketContextService contextService = new MarketContextService(dataFeed, symbol -> {
            MarketDataCollectorRepository.IndustryInfo info = industryInfos.get(symbol);
            if (info == null) {
                String stockId = normalizeStockId(symbol);
                info = industryInfos.get(stockId);
            }
            return info != null ? info.displayIndustry() : "未分類";
        });
        return contextService.build(symbols, timeframe, barCount, selectedQueryDate);
    }

    private MarketContextSnapshot buildInternalMarketContext(List<String> symbols, TradeMode mode) {
        if (symbols == null || symbols.isEmpty()) {
            return MarketContextSnapshot.empty("觀察清單沒有股票");
        }
        Timeframe timeframe = resolveRadarTimeframe(mode);
        int barCount = resolveRadarBarCount(mode);
        InternalMarketContextService contextService = new InternalMarketContextService(dataFeed);
        RadarStrategyConfig radarConfig = monitorConfig != null
                ? monitorConfig.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        return contextService.build(symbols, timeframe, barCount, selectedQueryDate, radarConfig);
    }

    private Map<String, MarketDataCollectorRepository.IndustryInfo> loadIndustryInfo(List<String> symbols) {
        try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                dataSourceManager.getMarketCollectorJdbcUrl(),
                dataSourceManager.getMarketCollectorUser(),
                dataSourceManager.getMarketCollectorPassword())) {
            return repository.findIndustryInfoForSymbols(symbols);
        } catch (Exception e) {
            System.err.println("Failed to load industry info: " + e.getMessage());
            return Map.of();
        }
    }

    private IndustryUniverseData loadIndustryUniverseData(LocalDate date) {
        try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                dataSourceManager.getMarketCollectorJdbcUrl(),
                dataSourceManager.getMarketCollectorUser(),
                dataSourceManager.getMarketCollectorPassword())) {
            return buildIndustryUniverseData(
                    repository.findIndustryStockRows(),
                    repository.findSymbolsWithSessionData(date != null ? date : LocalDate.now(TAIPEI_ZONE)));
        } catch (Exception e) {
            System.err.println("Failed to load industry universe: " + e.getMessage());
            return new IndustryUniverseData(List.of(), Map.of(), Map.of(), Map.of(), List.of());
        }
    }

    private IndustryUniverseData buildIndustryUniverseData(
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows,
            Set<String> availableSymbols) {
        List<MarketDataCollectorRepository.IndustryStockInfo> safeRows =
                stockRows != null ? stockRows : List.of();
        Set<String> safeAvailable = availableSymbols != null ? availableSymbols : Set.of();
        Map<String, String> industryBySymbol = new LinkedHashMap<>();
        Map<String, String> availableIndustryBySymbol = new LinkedHashMap<>();
        Map<String, Integer> totalCounts = new LinkedHashMap<>();

        for (MarketDataCollectorRepository.IndustryStockInfo row : safeRows) {
            String symbol = normalizeSymbolForIndustry(row.symbol());
            if (symbol.isBlank()) {
                continue;
            }
            String industry = row.industryGroup() != null && !row.industryGroup().isBlank()
                    ? row.industryGroup().trim()
                    : "未分類";
            industryBySymbol.put(symbol, industry);
            totalCounts.merge(industry, 1, Integer::sum);
            if (safeAvailable.contains(symbol)) {
                availableIndustryBySymbol.put(symbol, industry);
            }
        }

        List<IndustryStrength> summaryRows = totalCounts.entrySet().stream()
                .map(entry -> new IndustryStrength(
                        entry.getKey(),
                        0.0,
                        0.0,
                        0,
                        0,
                        0.0,
                        entry.getValue(),
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        "SQL產業成分股"))
                .toList();
        return new IndustryUniverseData(
                safeRows,
                industryBySymbol,
                availableIndustryBySymbol,
                totalCounts,
                summaryRows);
    }

    private void refreshMarketAndIndustryDocksFromSqlV2() {
        if (marketStatusDock == null && industryDistributionDock == null) {
            return;
        }
        List<String> symbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();
        SwingWorker<MarketContextSnapshot, Void> worker = new SwingWorker<>() {
            private IndustryUniverseData universeData =
                    new IndustryUniverseData(List.of(), Map.of(), Map.of(), Map.of(), List.of());

            @Override
            protected MarketContextSnapshot doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    universeData = buildIndustryUniverseData(
                            repository.findIndustryStockRows(),
                            repository.findSymbolsWithSessionData(
                                    selectedQueryDate != null ? selectedQueryDate : LocalDate.now(TAIPEI_ZONE)));
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    MarketContextService contextService = new MarketContextService(
                            sqlFeed,
                            symbol -> universeData.industryBySymbol().getOrDefault(normalizeSymbolForIndustry(symbol), "未分類"));
                    return contextService.build(
                            symbols,
                            resolveRadarTimeframe(TradeMode.DAY_TRADE),
                            resolveRadarBarCount(TradeMode.DAY_TRADE),
                            selectedQueryDate,
                            universeData.availableIndustryBySymbol(),
                            universeData.totalCountsByIndustry());
                }
            }

            @Override
            protected void done() {
                try {
                    MarketContextSnapshot snapshot = get();
                    if (marketStatusDock != null) {
                        marketStatusDock.updateSnapshot(snapshot);
                    }
                    if (industryDistributionDock != null) {
                        List<IndustryStrength> industryRows = universeData.summaryRows();
                        if (snapshot != null && snapshot.industries() != null && !snapshot.industries().isEmpty()) {
                            industryRows = snapshot.industries().values().stream()
                                    .sorted(Comparator.comparingDouble(IndustryStrength::score).reversed())
                                    .toList();
                        }
                        industryDistributionDock.updateIndustryData(
                                industryRows,
                                universeData.stockRows(),
                                Set.copyOf(watchlistPanel != null ? watchlistPanel.getSymbols() : List.of()));
                    }
                } catch (Exception e) {
                    statusBar.setText("大盤/產業資料載入失敗：" + rootCauseMessage(e));
                    if (industryDistributionDock != null) {
                        industryDistributionDock.updateIndustryData(List.of(), List.of(), Set.of());
                    }
                }
            }
        };
        worker.execute();
    }

    private String normalizeSymbolForIndustry(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record IndustryUniverseData(
            List<MarketDataCollectorRepository.IndustryStockInfo> stockRows,
            Map<String, String> industryBySymbol,
            Map<String, String> availableIndustryBySymbol,
            Map<String, Integer> totalCountsByIndustry,
            List<IndustryStrength> summaryRows) {
    }

    private void refreshMarketAndIndustryDocksFromSql() {
        if (marketStatusDock == null && industryDistributionDock == null) {
            return;
        }
        List<String> symbols = watchlistPanel != null ? watchlistPanel.getSymbols() : List.of();
        SwingWorker<MarketContextSnapshot, Void> worker = new SwingWorker<>() {
            private List<IndustryStrength> sqlIndustryRows = List.of();
            private List<MarketDataCollectorRepository.IndustryStockInfo> sqlIndustryStockRows = List.of();

            @Override
            protected MarketContextSnapshot doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    sqlIndustryRows = repository.findIndustrySummaries().stream()
                            .map(summary -> new IndustryStrength(
                                    summary.industry(),
                                    0.0,
                                    0.0,
                                    summary.symbolCount(),
                                    0,
                                    0.0))
                            .toList();
                    sqlIndustryStockRows = repository.findIndustryStockRows();
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    Map<String, MarketDataCollectorRepository.IndustryInfo> industryInfos =
                            repository.findIndustryInfoForSymbols(symbols);
                    MarketContextService contextService = new MarketContextService(sqlFeed, symbol -> {
                        MarketDataCollectorRepository.IndustryInfo info = industryInfos.get(symbol);
                        if (info == null) {
                            info = industryInfos.get(normalizeStockId(symbol));
                        }
                        return info != null ? info.displayIndustry() : "未知產業";
                    });
                    return contextService.build(symbols, resolveRadarTimeframe(TradeMode.DAY_TRADE), resolveRadarBarCount(TradeMode.DAY_TRADE), selectedQueryDate);
                }
            }

            @Override
            protected void done() {
                try {
                    MarketContextSnapshot snapshot = get();
                    if (marketStatusDock != null) {
                        marketStatusDock.updateSnapshot(snapshot);
                    }
                    if (industryDistributionDock != null) {
                        List<IndustryStrength> industryRows = sqlIndustryRows;
                        if (snapshot != null && snapshot.industries() != null && !snapshot.industries().isEmpty()) {
                            industryRows = snapshot.industries().values().stream()
                                    .filter(IndustryStrength::hasData)
                                    .sorted(Comparator.comparingDouble(IndustryStrength::score).reversed())
                                    .toList();
                        }
                        industryDistributionDock.updateIndustryData(
                                industryRows,
                                sqlIndustryStockRows,
                                Set.copyOf(watchlistPanel != null ? watchlistPanel.getSymbols() : List.of()));
                    }
                } catch (Exception e) {
                    statusBar.setText("大盤/產業資料載入失敗：" + rootCauseMessage(e));
                    if (industryDistributionDock != null) {
                        industryDistributionDock.updateIndustryData(List.of(), List.of(), Set.of());
                    }
                }
            }
        };
        worker.execute();
    }

    private void addIndustrySymbolsToWatchlist(String industry) {
        if (industry == null || industry.isBlank() || watchlistPanel == null) {
            return;
        }
        statusBar.setText("正在載入產業族群股票：" + industry);
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    return repository.findSymbolsByIndustry(industry);
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> symbols = get();
                    if (symbols == null || symbols.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                MainFrameWithDocking.this,
                                "SQL industry_chain 找不到族群股票：" + industry + "\n請先匯入 FinMind TaiwanStockIndustryChain。",
                                "產業選股",
                                JOptionPane.INFORMATION_MESSAGE);
                        statusBar.setText("產業族群沒有可加入股票：" + industry);
                        return;
                    }

                    int added = 0;
                    int synced = 0;
                    AddSymbolsResult result = addSymbolsToWatchlistInternal(symbols);
                    added = result.added();
                    synced = result.synced();
                    statusBar.setText("產業族群已加入觀察清單：" + industry
                            + "，新增 " + added + " 檔，同步 Collector " + synced + " 檔");
                } catch (Exception e) {
                    String message = rootCauseMessage(e);
                    statusBar.setText("產業族群加入失敗：" + message);
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "產業族群加入觀察清單失敗：\n" + message,
                            "產業選股",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void removeIndustrySymbolsFromWatchlist(String industry) {
        if (industry == null || industry.isBlank() || watchlistPanel == null) {
            return;
        }
        statusBar.setText("正在刪除產業族群股票：" + industry);
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    return repository.findSymbolsByIndustry(industry);
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> symbols = get();
                    if (symbols == null || symbols.isEmpty()) {
                        statusBar.setText("產業族群沒有可刪除股票：" + industry);
                        return;
                    }
                    RemoveSymbolsResult result = removeSymbolsFromWatchlistInternal(symbols);
                    statusBar.setText("產業族群已刪除：" + industry
                            + "，觀察清單移除 " + result.removed()
                            + " 檔，同步 Collector " + result.synced() + " 檔");
                } catch (Exception e) {
                    String message = rootCauseMessage(e);
                    statusBar.setText("產業族群刪除失敗：" + message);
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "產業族群刪除失敗：\n" + message,
                            "刪除族群",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void addSymbolsToWatchlist(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        AddSymbolsResult result = addSymbolsToWatchlistInternal(symbols);
        statusBar.setText("已加入產業選股清單：新增 " + result.added()
                + " 檔，同步 Collector " + result.synced() + " 檔");
    }

    private AddSymbolsResult addSymbolsToWatchlistInternal(List<String> symbols) {
        if (watchlistPanel == null || symbols == null || symbols.isEmpty()) {
            return new AddSymbolsResult(0, 0);
        }
        int added = 0;
        int synced = 0;
        for (String symbol : symbols.stream().filter(s -> s != null && !s.isBlank()).distinct().toList()) {
            boolean newlyAdded = watchlistPanel.addSymbolProgrammatically(symbol);
            boolean syncOk = MarketDataCollectorSymbolSync.addSymbol(symbol);
            if (newlyAdded) {
                added++;
                subscribeWatchlistSymbol(symbol);
            }
            if (syncOk) {
                synced++;
            }
        }
        StockNameResolver.loadMarketDataCollectorNames();
        refreshMarketAndIndustryDocksFromSqlV2();
        scanWatchlistForOpportunitiesAsync();
        restartMonitoringIfRunning();
        return new AddSymbolsResult(added, synced);
    }

    private record AddSymbolsResult(int added, int synced) {
    }

    private RemoveSymbolsResult removeSymbolsFromWatchlistInternal(List<String> symbols) {
        if (watchlistPanel == null || symbols == null || symbols.isEmpty()) {
            return new RemoveSymbolsResult(0, 0);
        }
        List<String> removed = watchlistPanel.removeSymbolsProgrammatically(symbols, false);
        int synced = 0;
        for (String symbol : removed) {
            if (MarketDataCollectorSymbolSync.removeSymbol(symbol)) {
                synced++;
            }
        }
        for (String symbol : removed) {
            cleanupRemovedWatchlistSymbol(symbol);
        }
        StockNameResolver.loadMarketDataCollectorNames();
        refreshMarketAndIndustryDocksFromSqlV2();
        scanWatchlistForOpportunitiesAsync();
        restartMonitoringIfRunning();
        return new RemoveSymbolsResult(removed.size(), synced);
    }

    private void cleanupRemovedWatchlistSymbol(String symbol) {
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
    }

    private record RemoveSymbolsResult(int removed, int synced) {
    }

    private void updateMarketContextDocks(MarketContextSnapshot snapshot) {
        if (marketStatusDock != null) {
            marketStatusDock.updateSnapshot(snapshot);
        }
        if (industryDistributionDock != null
                && snapshot != null
                && snapshot.industries() != null
                && !snapshot.industries().isEmpty()) {
            industryDistributionDock.updateSnapshot(snapshot);
        }
    }

    private String normalizeStockId(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        int dot = normalized.indexOf('.');
        return dot >= 0 ? normalized.substring(0, dot) : normalized;
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
                List<Bar> warmupBars = List.of();
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    replayBars = sqlFeed.fetchHistoricalBars(symbol, timeframe, 1000, selectedQueryDate);
                    warmupBars = fetchSqlRadarBacktestWarmupBars(sqlFeed, symbol, timeframe, selectedQueryDate, request);
                }
                if (replayBars == null || replayBars.size() < 2) {
                    throw new IllegalStateException("SQL K 線資料不足，無法回測：" + symbol + " " + selectedQueryDate);
                }
                RadarReplayBacktestService service = createRadarReplayService();
                return service.replay(symbol, warmupBars, replayBars, request);
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
        LocalDate[] range = promptSqlRadarBacktestDateRange();
        if (range == null) {
            return;
        }
        LocalDate startDate = range[0];
        LocalDate endDate = range[1];
        String dateLabel = startDate.equals(endDate)
                ? startDate.toString()
                : startDate + " ~ " + endDate;

        Timeframe timeframe = resolveRadarTimeframe(TradeMode.DAY_TRADE);
        MarketScannerService.ScanRequest request = createRadarScanRequest(TradeMode.DAY_TRADE)
                .initialCapital(1_000_000.0);
        statusBar.setText("SQL 雷達批次回測執行中：" + dateLabel + "，" + symbols.size() + " 檔");

        SwingWorker<BacktestResult, Void> worker = new SwingWorker<>() {
            private final Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
            private final List<String> skipped = new ArrayList<>();
            private int testedSymbolDays = 0;

            @Override
            protected BacktestResult doInBackground() throws Exception {
                RadarReplayBacktestService service = createRadarReplayService();
                List<BacktestResult> daySymbolResults = new ArrayList<>();
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                        for (String symbol : symbols) {
                            if (symbol == null || symbol.isBlank()) {
                                continue;
                            }
                            try {
                                List<Bar> bars = sqlFeed.fetchHistoricalBars(symbol, timeframe, 1000, date);
                                if (bars == null || bars.size() < 2) {
                                    skipped.add(date + " " + symbol + "：SQL K 線不足");
                                    continue;
                                }
                                List<Bar> warmupBars = fetchSqlRadarBacktestWarmupBars(sqlFeed, symbol, timeframe, date, request);
                                BacktestResult result = service.replay(symbol, warmupBars, bars, request);
                                daySymbolResults.add(result);
                                testedSymbolDays++;
                                tradesBySymbol.computeIfAbsent(symbol, ignored -> new ArrayList<>())
                                        .addAll(result.getTrades());
                            } catch (Exception e) {
                                skipped.add(date + " " + symbol + "：" + e.getMessage());
                            }
                        }
                    }
                }
                if (daySymbolResults.isEmpty()) {
                    throw new IllegalStateException("觀察清單沒有可回測的 SQL K 線資料：" + dateLabel);
                }
                return combineBacktestResults(daySymbolResults);
            }

            @Override
            protected void done() {
                try {
                    BacktestResult combined = get();
                    latestSqlRadarBacktestTrades.clear();
                    latestSqlRadarBacktestTrades.putAll(groupTradesBySymbol(combined.getTrades()));
                    showSqlRadarBacktestMarkersFor(currentSymbol);

                    BacktestResultDialog dialog = new BacktestResultDialog(
                            MainFrameWithDocking.this,
                            combined,
                            "SQL 雷達批次回測-" + dateLabel,
                            buildDayTradeBacktestConfigSummary("觀察清單批次", testedSymbolDays, timeframe, dateLabel));
                    dialog.setVisible(true);
                    String skipText = skipped.isEmpty() ? "" : "，略過 " + skipped.size() + " 筆股票日";
                    statusBar.setText(String.format(
                            "SQL 雷達批次回測完成：%s，%d 筆股票日有結果，總交易 %d 筆%s",
                            dateLabel,
                            testedSymbolDays,
                            combined.getTrades().size(),
                            skipText));
                    if (!skipped.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                MainFrameWithDocking.this,
                                "以下股票日略過：\n" + String.join("\n", skipped),
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

    private void startSqlRadarOpeningReplay() {
        SqlRadarReplayConfig config = promptSqlRadarReplayConfig();
        if (config == null) {
            return;
        }
        if (activeSqlRadarReplaySession != null) {
            activeSqlRadarReplaySession.stop();
            activeSqlRadarReplaySession = null;
        }
        statusBar.setText("SQL 雷達開盤重播準備中：" + config.date() + "，" + config.symbols().size() + " 檔");

        SwingWorker<SqlRadarOpeningReplaySession, Void> worker = new SwingWorker<>() {
            @Override
            protected SqlRadarOpeningReplaySession doInBackground() throws Exception {
                Timeframe timeframe = resolveRadarTimeframe(TradeMode.DAY_TRADE);
                MarketScannerService.ScanRequest request = createRadarScanRequest(TradeMode.DAY_TRADE)
                        .initialCapital(1_000_000.0);
                Map<String, List<Bar>> sessionBarsBySymbol = new LinkedHashMap<>();
                Map<String, List<Bar>> warmupBarsBySymbol = new LinkedHashMap<>();
                Map<String, List<Tick>> replayTicksBySymbol = new LinkedHashMap<>();
                Map<String, MarketDataCollectorFeed.SessionBarLoadResult> sourceBySymbol = new LinkedHashMap<>();
                try (MarketDataCollectorRepository repository = new MarketDataCollectorRepository(
                        dataSourceManager.getMarketCollectorJdbcUrl(),
                        dataSourceManager.getMarketCollectorUser(),
                        dataSourceManager.getMarketCollectorPassword())) {
                    MarketDataCollectorFeed sqlFeed = new MarketDataCollectorFeed(repository, java.time.Duration.ofDays(1));
                    for (String symbol : config.symbols()) {
                        MarketDataCollectorFeed.SessionBarLoadResult loadResult = sqlFeed.fetchSessionBarsWithSource(
                                symbol,
                                timeframe,
                                config.date(),
                                config.sourceMode());
                        replayTicksBySymbol.put(symbol, repository.findSessionTicks(symbol, config.date()));
                        List<Bar> bars = loadResult.bars();
                        if (bars == null || bars.size() < 2) {
                            sourceBySymbol.put(symbol, loadResult);
                            continue;
                        }
                        List<Bar> warmupBars = fetchSqlRadarBacktestWarmupBars(sqlFeed, symbol, timeframe, config.date(), request);
                        sessionBarsBySymbol.put(symbol, bars);
                        warmupBarsBySymbol.put(symbol, warmupBars);
                        sourceBySymbol.put(symbol, loadResult);
                    }
                }
                if (sessionBarsBySymbol.isEmpty()) {
                    throw new IllegalStateException("指定日期沒有可重播的 SQL K 線資料：" + config.date());
                }
                return new SqlRadarOpeningReplaySession(
                        config,
                        timeframe,
                        request,
                        sessionBarsBySymbol,
                        warmupBarsBySymbol,
                        replayTicksBySymbol,
                        sourceBySymbol);
            }

            @Override
            protected void done() {
                try {
                    activeSqlRadarReplaySession = get();
                    activeSqlRadarReplaySession.start();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusBar.setText("SQL 雷達開盤重播失敗：" + cause.getMessage());
                    JOptionPane.showMessageDialog(
                            MainFrameWithDocking.this,
                            "SQL 雷達開盤重播失敗：\n" + cause.getMessage(),
                            "SQL 雷達開盤重播",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void stopSqlRadarOpeningReplay() {
        if (activeSqlRadarReplaySession != null) {
            activeSqlRadarReplaySession.stop();
            activeSqlRadarReplaySession = null;
            statusBar.setText("SQL 雷達開盤重播已停止");
        }
    }

    private SqlRadarReplayConfig promptSqlRadarReplayConfig() {
        JComboBox<String> scope = new JComboBox<>(new String[]{"觀察清單", "目前商品"});
        JTextField dateField = new JTextField(selectedQueryDate.toString(), 12);
        JTextField startField = new JTextField("09:00", 8);
        JTextField endField = new JTextField("13:30", 8);
        int defaultScanInterval = monitorConfig != null ? monitorConfig.getScanIntervalSeconds() : 30;
        int defaultScanOffset = inferReplayScanOffsetSeconds(selectedQueryDate, defaultScanInterval);
        JSpinner scanInterval = new JSpinner(new SpinnerNumberModel(defaultScanInterval, 3, 60, 1));
        JSpinner scanOffset = new JSpinner(new SpinnerNumberModel(defaultScanOffset, 0, 59, 1));
        JComboBox<MarketDataCollectorFeed.SessionBarSourceMode> sourceMode = new JComboBox<>(
                MarketDataCollectorFeed.SessionBarSourceMode.values());
        sourceMode.setSelectedItem(MarketDataCollectorFeed.SessionBarSourceMode.TICKS_AGGREGATED);
        sourceMode.setToolTipText("比對盤中實盤雷達請固定使用 ticks 聚合；AUTO 會在 SQL candlesticks 完整時改用 candlesticks。");
        JComboBox<String> speed = new JComboBox<>(new String[]{"慢速（1000ms/步）", "正常（500ms/步）", "快速（200ms/步）", "極快（50ms/步）"});
        speed.setSelectedIndex(1);

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        panel.add(new JLabel("重播範圍"));
        panel.add(scope);
        panel.add(new JLabel("資料日期"));
        panel.add(dateField);
        panel.add(new JLabel("開始時間"));
        panel.add(startField);
        panel.add(new JLabel("結束時間"));
        panel.add(endField);
        panel.add(new JLabel("掃描間隔（秒）"));
        panel.add(scanInterval);
        panel.add(new JLabel("掃描秒偏移"));
        panel.add(scanOffset);
        panel.add(new JLabel("資料來源模式"));
        panel.add(sourceMode);
        panel.add(new JLabel("播放速度"));
        panel.add(speed);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "SQL 雷達開盤重播",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            LocalTime start = parseReplayTime(startField.getText().trim());
            LocalTime end = parseReplayTime(endField.getText().trim());
            if (!end.isAfter(start)) {
                JOptionPane.showMessageDialog(this, "結束時間必須晚於開始時間。", "SQL 雷達開盤重播", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            List<String> symbols = resolveSqlRadarReplaySymbols(String.valueOf(scope.getSelectedItem()));
            if (symbols.isEmpty()) {
                JOptionPane.showMessageDialog(this, "沒有可重播的股票。", "SQL 雷達開盤重播", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            int delayMs = switch (speed.getSelectedIndex()) {
                case 0 -> 1000;
                case 2 -> 200;
                case 3 -> 50;
                default -> 500;
            };
            selectedQueryDate = date;
            int selectedScanInterval = Math.max(3, ((Number) scanInterval.getValue()).intValue());
            int selectedScanOffset = Math.floorMod(((Number) scanOffset.getValue()).intValue(), selectedScanInterval);
            MarketDataCollectorFeed.SessionBarSourceMode selectedMode =
                    (MarketDataCollectorFeed.SessionBarSourceMode) sourceMode.getSelectedItem();
            return new SqlRadarReplayConfig(date, start, end, delayMs, selectedScanInterval, selectedScanOffset, symbols, selectedMode);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "日期或時間格式錯誤，日期請用 yyyy-MM-dd，時間請用 HH:mm。", "SQL 雷達開盤重播", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private int inferReplayScanOffsetSeconds(LocalDate date, int scanIntervalSeconds) {
        if (date == null) {
            return 0;
        }
        int interval = Math.max(1, scanIntervalSeconds);
        Path ordersPath = Path.of(
                "logs",
                "paper-trades",
                "orders_" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv");
        if (!Files.exists(ordersPath)) {
            return 0;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (String line : Files.readAllLines(ordersPath, StandardCharsets.UTF_8)) {
                if (line == null || line.startsWith("event_time,") || !line.contains(",auto-monitor,")) {
                    continue;
                }
                String[] columns = line.split(",", 4);
                if (columns.length == 0 || columns[0].isBlank()) {
                    continue;
                }
                LocalDateTime eventTime = LocalDateTime.parse(columns[0].trim(), formatter);
                return Math.floorMod(eventTime.getSecond(), interval);
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private List<String> resolveSqlRadarReplaySymbols(String scope) {
        java.util.LinkedHashSet<String> symbols = new java.util.LinkedHashSet<>();
        if ("目前商品".equals(scope)) {
            if (currentSymbol != null && !currentSymbol.isBlank()) {
                symbols.add(currentSymbol);
            }
        } else if (watchlistPanel != null) {
            for (String symbol : watchlistPanel.getSymbols()) {
                if (symbol != null && !symbol.isBlank()) {
                    symbols.add(symbol);
                }
            }
        }
        return new ArrayList<>(symbols);
    }

    private LocalTime parseReplayTime(String value) {
        String safe = value != null ? value.trim() : "";
        if (safe.length() == 5) {
            return LocalTime.parse(safe);
        }
        return LocalTime.parse(safe.substring(0, Math.min(8, safe.length())));
    }

    private record SqlRadarReplayConfig(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            int delayMs,
            int scanIntervalSeconds,
            int scanOffsetSeconds,
            List<String> symbols,
            MarketDataCollectorFeed.SessionBarSourceMode sourceMode) {
        private SqlRadarReplayConfig {
            scanIntervalSeconds = Math.max(3, scanIntervalSeconds);
            scanOffsetSeconds = Math.floorMod(scanOffsetSeconds, scanIntervalSeconds);
            sourceMode = sourceMode != null
                    ? sourceMode
                    : MarketDataCollectorFeed.SessionBarSourceMode.TICKS_AGGREGATED;
        }
    }

    private record SqlRadarReplayStep(
            LocalDateTime replayTime,
            List<MarketScanResult> results,
            MarketContextSnapshot context,
            List<Bar> chartBars,
            List<Trade> markerTrades,
            List<Trade> replayTrades,
            int openLongSignals,
            int blockedSignals) {
    }

    private class SqlRadarOpeningReplaySession {
        private final SqlRadarReplayConfig config;
        private final Timeframe timeframe;
        private final MarketScannerService.ScanRequest request;
        private final ReplayBarFeed replayFeed;
        private final Map<String, List<Bar>> warmupBarsBySymbol;
        private final Map<String, List<Tick>> replayTicksBySymbol;
        private final Map<String, MarketDataCollectorFeed.SessionBarLoadResult> sourceBySymbol;
        private final Path replayLogPath;
        private final Path replayDetailLogPath;
        private final javax.swing.Timer timer;
        private LocalDateTime cursor;
        private boolean running;
        private boolean stepInProgress;
        private final Map<String, ReplayOpenPosition> liveReplayOpenPositions = new HashMap<>();
        private final Map<String, LocalDateTime> liveReplayLastSignalTime = new HashMap<>();
        private final Map<String, LocalDateTime> liveReplayStopLossCooldownUntil = new HashMap<>();
        private final Set<String> liveReplayEntryBuckets = new HashSet<>();
        private final List<Trade> liveReplayTrades = new ArrayList<>();
        private LocalDateTime liveReplayLastEntryTime;
        private int liveReplayEntryCount;
        private double liveReplayRealizedPnl;
        private int liveReplayStopLossCount;
        private int liveReplayConsecutiveLosses;
        private boolean liveReplayTradingHalted;

        SqlRadarOpeningReplaySession(
                SqlRadarReplayConfig config,
                Timeframe timeframe,
                MarketScannerService.ScanRequest request,
                Map<String, List<Bar>> sessionBarsBySymbol,
                Map<String, List<Bar>> warmupBarsBySymbol,
                Map<String, List<Tick>> replayTicksBySymbol,
                Map<String, MarketDataCollectorFeed.SessionBarLoadResult> sourceBySymbol) {
            this.config = config;
            this.timeframe = timeframe != null ? timeframe : Timeframe.M5;
            this.request = request != null ? request : MarketScannerService.ScanRequest.createDefault();
            this.replayFeed = new ReplayBarFeed(sessionBarsBySymbol, warmupBarsBySymbol, replayTicksBySymbol, this.timeframe);
            this.warmupBarsBySymbol = normalizeReplayBars(warmupBarsBySymbol);
            this.replayTicksBySymbol = normalizeReplayTicks(replayTicksBySymbol);
            this.sourceBySymbol = sourceBySymbol != null ? new LinkedHashMap<>(sourceBySymbol) : Map.of();
            this.replayLogPath = createReplayLogPath(config);
            this.replayDetailLogPath = createReplayDetailLogPath(this.replayLogPath);
            this.cursor = config.date().atTime(config.startTime()).plusSeconds(config.scanOffsetSeconds());
            this.timer = new javax.swing.Timer(Math.max(50, config.delayMs()), event -> runStep());
            this.timer.setRepeats(false);
        }

        void start() {
            running = true;
            latestSqlRadarBacktestTrades.clear();
            opportunityRadarDock.clearResults();
            initializeReplayLog();
            showDockablePanel("executionStatus", "執行狀態");
            if (executionStatusDock != null) {
                executionStatusDock.showReplayTrades(List.of());
            }
            statusBar.setText("SQL 雷達開盤重播開始：" + config.date() + " " + config.startTime() + " ~ " + config.endTime()
                    + "；日誌 " + replayLogPath);
            runStep();
        }

        void stop() {
            running = false;
            timer.stop();
            stepInProgress = false;
        }

        private void runStep() {
            if (!running || stepInProgress) {
                return;
            }
            if (cursor.toLocalTime().isAfter(config.endTime())) {
                stop();
                statusBar.setText("SQL 雷達開盤重播完成：" + config.date() + "，" + config.symbols().size() + " 檔");
                return;
            }
            stepInProgress = true;
            LocalDateTime replayTime = cursor;
            SwingWorker<SqlRadarReplayStep, Void> worker = new SwingWorker<>() {
                @Override
                protected SqlRadarReplayStep doInBackground() {
                    replayFeed.setReplayTime(replayTime);
                    MarketContextService contextService = new MarketContextService(replayFeed, symbol -> "重播觀察清單");
                    MarketContextSnapshot context = contextService.build(
                            config.symbols(),
                            timeframe,
                            request.getBarCount());
                    MarketScannerService scanner = new MarketScannerService(replayFeed);
                    List<MarketScanResult> results = new ArrayList<>();
                    for (String symbol : config.symbols()) {
                        MarketScanResult result = scanner.scan(symbol, request.copy()
                                .marketContext(context)
                                .useMarketContextBars(false)
                                .asOfTime(replayTime));
                        if (result != null) {
                            results.add(result.withScannedAt(replayTime));
                        }
                    }
                    results.sort(Comparator.naturalOrder());
                    List<Trade> replayTrades = advanceLiveEquivalentReplay(replayTime, results);
                    String chartSymbol = resolveReplayChartSymbol();
                    List<Bar> chartBars = replayFeed.visibleSessionBars(chartSymbol);
                    List<Trade> markerTrades = replayTrades.stream()
                            .filter(trade -> chartSymbol.equals(trade.getSymbol()))
                            .filter(trade -> !trade.getTimestamp().isAfter(replayTime))
                            .toList();
                    int openLongSignals = (int) results.stream()
                            .filter(result -> result.getDecisionResult() != null
                                    && result.getDecisionResult().getAction() == DecisionResult.Action.OPEN_LONG)
                            .count();
                    int blockedSignals = (int) results.stream()
                            .filter(result -> result.getBlockReason() != null && !result.getBlockReason().isBlank())
                            .count();
                    appendReplayLog(replayTime, results, replayTrades, openLongSignals, blockedSignals);
                    return new SqlRadarReplayStep(
                            replayTime,
                            results,
                            context,
                            chartBars,
                            markerTrades,
                            replayTrades,
                            openLongSignals,
                            blockedSignals);
                }

                @Override
                protected void done() {
                    try {
                        SqlRadarReplayStep step = get();
                        applyReplayStep(step);
                        cursor = cursor.plusSeconds(Math.max(3, config.scanIntervalSeconds()));
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        stop();
                        statusBar.setText("SQL 雷達開盤重播中止：" + cause.getMessage());
                    } finally {
                        stepInProgress = false;
                        if (running) {
                            timer.setInitialDelay(Math.max(50, config.delayMs()));
                            timer.restart();
                        }
                    }
                }
            };
            worker.execute();
        }

        private String resolveReplayChartSymbol() {
            if (currentSymbol != null && replayFeed.hasSymbol(currentSymbol)) {
                return currentSymbol;
            }
            return config.symbols().isEmpty() ? "" : config.symbols().get(0);
        }

        private void applyReplayStep(SqlRadarReplayStep step) {
            if (step.results() != null) {
                opportunityRadarDock.updateScanResults(step.results());
                    latestScanResults.clear();
                    for (MarketScanResult result : step.results()) {
                        latestScanResults.put(result.getSymbol(), result);
                    }
                    latestSqlRadarBacktestTrades.clear();
                    latestSqlRadarBacktestTrades.putAll(groupTradesBySymbol(step.replayTrades()));
                }
            updateMarketContextDocks(step.context());
            if (step.chartBars() != null && !step.chartBars().isEmpty()) {
                chartDock.loadHistoricalData(step.chartBars());
                chartDock.showTradeMarkers(step.markerTrades());
            }
            if (executionStatusDock != null) {
                executionStatusDock.showReplayTrades(step.replayTrades());
            }
            statusBar.setText(String.format(
                    "SQL 雷達開盤重播 %s | %s | 掃描 %d 檔 | OPEN_LONG %d | 阻擋 %d | 重播交易 %d 筆 | 日誌 %s",
                    config.date(),
                    step.replayTime().toLocalTime(),
                    step.results() != null ? step.results().size() : 0,
                    step.openLongSignals(),
                    step.blockedSignals(),
                    step.replayTrades() != null ? step.replayTrades().size() : 0,
                    replayLogPath));
        }

        private List<Trade> advanceLiveEquivalentReplay(LocalDateTime replayTime, List<MarketScanResult> results) {
            Map<String, MarketScanResult> resultBySymbol = (results != null ? results : List.<MarketScanResult>of()).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MarketScanResult::getSymbol,
                            result -> result,
                            (left, right) -> left,
                            LinkedHashMap::new));
            closeLiveReplayPositions(replayTime, resultBySymbol);
            for (MarketScanResult result : results != null ? results : List.<MarketScanResult>of()) {
                if (result == null || !result.hasTradeSignal()) {
                    continue;
                }
                String symbol = result.getSymbol();
                if (!isNewLiveReplaySignal(symbol, replayTime)) {
                    continue;
                }
                liveReplayLastSignalTime.put(symbol, replayTime);
                String blockReason = resolveLiveReplayEntryBlockReason(symbol, replayTime);
                if (blockReason != null) {
                    continue;
                }
                double price = latestReplayPrice(symbol, replayTime);
                if (price <= 0.0) {
                    continue;
                }
                Trade buy = createLiveReplayBuy(
                        replayTime,
                        symbol,
                        DAY_TRADE_LOT_SIZE,
                        price,
                        result.getSuggestedStopLoss(),
                        result.getSuggestedTakeProfit(),
                        firstNonBlank(result.getReason(), result.getLongBonusSummary()));
                liveReplayTrades.add(buy);
                liveReplayOpenPositions.put(symbol, new ReplayOpenPosition(
                        symbol,
                        DAY_TRADE_LOT_SIZE,
                        price,
                        result.getSuggestedStopLoss(),
                        result.getSuggestedTakeProfit(),
                        buy));
                liveReplayEntryCount++;
                liveReplayLastEntryTime = replayTime;
                liveReplayEntryBuckets.add(fiveMinuteBacktestBucket(replayTime));
            }
            return liveReplayTrades.stream()
                    .filter(trade -> !trade.getTimestamp().isAfter(replayTime))
                    .sorted(Comparator.comparing(Trade::getTimestamp)
                            .thenComparing(Trade::getSymbol)
                            .thenComparing(trade -> trade.getType().name()))
                    .toList();
        }

        private void closeLiveReplayPositions(LocalDateTime replayTime, Map<String, MarketScanResult> resultBySymbol) {
            if (liveReplayOpenPositions.isEmpty()) {
                return;
            }
            List<String> symbols = new ArrayList<>(liveReplayOpenPositions.keySet());
            for (String symbol : symbols) {
                ReplayOpenPosition open = liveReplayOpenPositions.get(symbol);
                ReplayExit exit = resolveLiveReplayExit(open, replayTime, resultBySymbol != null ? resultBySymbol.get(symbol) : null);
                if (exit == null) {
                    continue;
                }
                Trade sell = createLiveReplaySell(
                        replayTime,
                        symbol,
                        open.quantity(),
                        exit.price(),
                        open.stopLoss(),
                        open.takeProfit(),
                        exit.reason());
                liveReplayTrades.add(sell);
                liveReplayOpenPositions.remove(symbol);
                double pnl = sell.getNetProceeds() - open.entryTrade().getTotalCost();
                liveReplayRealizedPnl += pnl;
                if (pnl < 0.0) {
                    liveReplayConsecutiveLosses++;
                } else if (pnl > 0.0) {
                    liveReplayConsecutiveLosses = 0;
                }
                if (exit.reason().toUpperCase(Locale.ROOT).contains("STOP")) {
                    liveReplayStopLossCount++;
                    if (monitorConfig != null && monitorConfig.isStopLossCooldownEnabled()) {
                        registerLiveReplayCooldown(symbol, replayTime, monitorConfig.getStopLossCooldownMinutes());
                    }
                }
                if (monitorConfig != null && monitorConfig.getPostExitCooldownMinutes() > 0) {
                    registerLiveReplayCooldown(symbol, replayTime, monitorConfig.getPostExitCooldownMinutes());
                }
                if (isLiveReplayTradingHalted()) {
                    liveReplayTradingHalted = true;
                }
            }
        }

        private void registerLiveReplayCooldown(String symbol, LocalDateTime replayTime, int minutes) {
            if (symbol == null || symbol.isBlank() || replayTime == null || minutes <= 0) {
                return;
            }
            LocalDateTime until = replayTime.plusMinutes(minutes);
            LocalDateTime existing = liveReplayStopLossCooldownUntil.get(symbol);
            if (existing == null || until.isAfter(existing)) {
                liveReplayStopLossCooldownUntil.put(symbol, until);
            }
        }

        private ReplayExit resolveLiveReplayExit(ReplayOpenPosition open, LocalDateTime replayTime, MarketScanResult scanResult) {
            if (open == null) {
                return null;
            }
            Bar latest = latestReplayBar(open.symbol(), replayTime);
            if (latest == null) {
                return null;
            }
            long holdingMinutes = replayTime != null && open.entryTrade().getTimestamp() != null
                    ? Duration.between(open.entryTrade().getTimestamp(), replayTime).toMinutes()
                    : 0;
            double price = latest.getClose();
            if (holdingMinutes >= 3
                    && scanResult != null
                    && scanResult.getVwap() != null
                    && scanResult.getVwap() > 0.0
                    && price < scanResult.getVwap()) {
                return new ReplayExit(price, "EXIT_VWAP_BREAK");
            }
            double risk = open.stopLoss() != null ? Math.max(0.0, open.entryPrice() - open.stopLoss()) : 0.0;
            if (holdingMinutes >= 5
                    && scanResult != null
                    && Boolean.FALSE.equals(scanResult.getVolumeSustain())
                    && risk > 0.0
                    && price < open.entryPrice() + risk * 0.50) {
                return new ReplayExit(price, "EXIT_VOLUME_FAIL");
            }
            if (open.stopLoss() != null && latest.getLow() <= open.stopLoss()) {
                return new ReplayExit(open.stopLoss(), "EXIT_STOP_LOSS");
            }
            if (open.takeProfit() != null && latest.getHigh() >= open.takeProfit()) {
                return new ReplayExit(open.takeProfit(), "EXIT_TAKE_PROFIT");
            }
            if (replayTime != null && !replayTime.toLocalTime().isBefore(DAY_TRADE_FORCE_CLOSE_TIME)) {
                return new ReplayExit(latest.getClose(), "EXIT_TIME_FORCE");
            }
            return null;
        }

        private String resolveLiveReplayEntryBlockReason(String symbol, LocalDateTime replayTime) {
            if (liveReplayTradingHalted) {
                return "live-equivalent replay trading halted";
            }
            if (symbol == null || symbol.isBlank() || liveReplayOpenPositions.containsKey(symbol)) {
                return "position already open";
            }
            LocalTime time = replayTime != null ? replayTime.toLocalTime() : LocalTime.now(TAIPEI_ZONE);
            if (!time.isBefore(DAY_TRADE_FORCE_CLOSE_TIME)) {
                return "force close window";
            }
            if (monitorConfig != null && monitorConfig.getLatestAutoEntryTime() != null
                    && !time.isBefore(monitorConfig.getLatestAutoEntryTime())) {
                return "latest entry time reached";
            }
            if (monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()) {
                LocalTime start = monitorConfig.getEarlyEntryBlockStart();
                LocalTime end = monitorConfig.getEarlyEntryBlockEnd();
                if (start != null && end != null && start.isBefore(end)
                        && !time.isBefore(start) && time.isBefore(end)) {
                    return "early entry block";
                }
            }
            LocalDateTime cooldownUntil = liveReplayStopLossCooldownUntil.get(symbol);
            if (cooldownUntil != null) {
                if (replayTime != null && replayTime.isBefore(cooldownUntil)) {
                    return "stop loss cooldown";
                }
                liveReplayStopLossCooldownUntil.remove(symbol);
            }
            if (monitorConfig != null && liveReplayEntryCount >= monitorConfig.getDailyMaxAutoTrades()) {
                return "daily max auto trades reached";
            }
            if (monitorConfig != null && monitorConfig.getEntryPacingMinutes() > 0 && liveReplayLastEntryTime != null
                    && replayTime != null
                    && replayTime.isBefore(liveReplayLastEntryTime.plusMinutes(monitorConfig.getEntryPacingMinutes()))) {
                return "entry pacing";
            }
            if (monitorConfig != null && monitorConfig.isOneEntryPerFiveMinuteBar()
                    && replayTime != null
                    && liveReplayEntryBuckets.contains(fiveMinuteBacktestBucket(replayTime))) {
                return "one entry per M5";
            }
            int maxPositions = monitorDecisionConfig != null
                    ? Math.max(1, monitorDecisionConfig.getRiskConfig().getMaxConcurrentPositions())
                    : 1;
            if (liveReplayOpenPositions.size() >= maxPositions) {
                return "max positions reached";
            }
            return null;
        }

        private boolean isNewLiveReplaySignal(String symbol, LocalDateTime replayTime) {
            LocalDateTime last = liveReplayLastSignalTime.get(symbol);
            if (last == null || replayTime == null) {
                return true;
            }
            int interval = monitorConfig != null ? Math.max(0, monitorConfig.getMinSignalIntervalMinutes()) : 0;
            return Duration.between(last, replayTime).toMinutes() >= interval;
        }

        private boolean isLiveReplayTradingHalted() {
            if (monitorConfig == null || !monitorConfig.isDisableTradingAfterLossLimit()) {
                return false;
            }
            return liveReplayRealizedPnl <= monitorConfig.getDailyMaxLoss()
                    || (monitorConfig.getDailyMaxStopLossCount() > 0
                    && liveReplayStopLossCount >= monitorConfig.getDailyMaxStopLossCount())
                    || (monitorConfig.getConsecutiveLossLimit() > 0
                    && liveReplayConsecutiveLosses >= monitorConfig.getConsecutiveLossLimit());
        }

        private double latestReplayPrice(String symbol, LocalDateTime replayTime) {
            Tick tick = latestReplayTick(symbol, replayTime);
            if (tick != null && tick.getPrice() > 0.0) {
                return tick.getPrice();
            }
            Bar latest = latestReplayBar(symbol, replayTime);
            return latest != null ? latest.getClose() : 0.0;
        }

        private Tick latestReplayTick(String symbol, LocalDateTime replayTime) {
            if (symbol == null || replayTime == null) {
                return null;
            }
            List<Tick> ticks = replayTicksBySymbol.get(normalizeReplaySymbol(symbol));
            if ((ticks == null || ticks.isEmpty()) && symbol.contains(".")) {
                ticks = replayTicksBySymbol.get(normalizeReplaySymbol(symbol.substring(0, symbol.indexOf('.'))));
            }
            if ((ticks == null || ticks.isEmpty()) && !symbol.contains(".")) {
                ticks = replayTicksBySymbol.get(normalizeReplaySymbol(symbol + ".TW"));
            }
            if (ticks == null || ticks.isEmpty()) {
                return null;
            }
            Tick latest = null;
            for (Tick tick : ticks) {
                if (tick == null || tick.getTimestamp() == null || tick.getTimestamp().isAfter(replayTime)) {
                    break;
                }
                latest = tick;
            }
            return latest;
        }

        private Bar latestReplayBar(String symbol, LocalDateTime replayTime) {
            List<Bar> bars = scannerBarsForLog(symbol, replayTime);
            return bars.isEmpty() ? null : bars.get(bars.size() - 1);
        }

        private Trade createLiveReplayBuy(
                LocalDateTime timestamp,
                String symbol,
                int quantity,
                double price,
                Double stopLoss,
                Double takeProfit,
                String reason) {
            double slippageCost = price * quantity * 0.0005;
            return new Trade(timestamp, symbol, TradeType.BUY, quantity, price, 0.001425,
                    0.0, slippageCost, stopLoss, takeProfit, reason);
        }

        private Trade createLiveReplaySell(
                LocalDateTime timestamp,
                String symbol,
                int quantity,
                double price,
                Double stopLoss,
                Double takeProfit,
                String reason) {
            double slippageCost = price * quantity * 0.0005;
            return new Trade(timestamp, symbol, TradeType.SELL, quantity, price, 0.001425,
                    0.0015, slippageCost, stopLoss, takeProfit, reason);
        }

        private record ReplayOpenPosition(
                String symbol,
                int quantity,
                double entryPrice,
                Double stopLoss,
                Double takeProfit,
                Trade entryTrade) {
        }

        private record ReplayExit(double price, String reason) {
        }

        private Map<String, List<Bar>> normalizeReplayBars(Map<String, List<Bar>> source) {
            Map<String, List<Bar>> normalized = new LinkedHashMap<>();
            if (source == null) {
                return normalized;
            }
            for (Map.Entry<String, List<Bar>> entry : source.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<Bar> bars = entry.getValue() != null
                        ? entry.getValue().stream()
                                .filter(bar -> bar != null && bar.getTimestamp() != null)
                                .sorted(Comparator.comparing(Bar::getTimestamp))
                                .toList()
                        : List.of();
                normalized.put(normalizeReplaySymbol(entry.getKey()), bars);
            }
            return normalized;
        }

        private Map<String, List<Tick>> normalizeReplayTicks(Map<String, List<Tick>> source) {
            Map<String, List<Tick>> normalized = new LinkedHashMap<>();
            if (source == null) {
                return normalized;
            }
            for (Map.Entry<String, List<Tick>> entry : source.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<Tick> ticks = entry.getValue() != null
                        ? entry.getValue().stream()
                                .filter(tick -> tick != null && tick.getTimestamp() != null)
                                .sorted(Comparator.comparing(Tick::getTimestamp))
                                .toList()
                        : List.of();
                normalized.put(normalizeReplaySymbol(entry.getKey()), ticks);
            }
            return normalized;
        }

        private String normalizeReplaySymbol(String symbol) {
            return symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        }

        private Path createReplayLogPath(SqlRadarReplayConfig config) {
            String timestamp = LocalDateTime.now(TAIPEI_ZONE).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            return Path.of("logs", "radar-replay", "radar_replay_" + config.date() + "_" + timestamp + ".csv");
        }

        private Path createReplayDetailLogPath(Path summaryPath) {
            String fileName = summaryPath.getFileName().toString();
            String detailName = fileName.endsWith(".csv")
                    ? fileName.substring(0, fileName.length() - 4) + "_symbols.csv"
                    : fileName + "_symbols.csv";
            return summaryPath.resolveSibling(detailName);
        }

        private void initializeReplayLog() {
            try {
                Files.createDirectories(replayLogPath.getParent());
                String header = "\ufeffreplay_time,replay_date,timeframe,source_mode,replay_scan_interval_sec,replay_scan_offset_sec,loaded_data_sources,strategy_name,strategy_details,"
                        + "scanned_symbols,open_long_signals,blocked_signals,replay_trades,trade_events,top_candidates,"
                        + "open_long_candidates,blocked_candidates,score_components,warmup_summary\n";
                Files.writeString(replayLogPath, header, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                String detailHeader = "\ufeffreplay_time,replay_date,timeframe,source_mode,replay_scan_interval_sec,replay_scan_offset_sec,symbol,actual_source,bars,candles,ticks,expected_bars,"
                        + "scanner_bar_source,scanner_bars,visible_session_bars,last_visible_session_bar_time,"
                        + "warmup_enabled,warmup_requested_bars,warmup_loaded_bars,warmup_first,warmup_last,"
                        + "strategy_name,score,confidence,action,block_reason,reason,market_decision,trade_mode,"
                        + "vwap,vwap_slope,volume_sustain,atr_stop_loss,atr_take_profit,long_bonus,score_components\n";
                Files.writeString(replayDetailLogPath, detailHeader, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                statusBar.setText("SQL 雷達重播日誌建立失敗：" + e.getMessage());
            }
        }

        private void appendReplayLog(
                LocalDateTime replayTime,
                List<MarketScanResult> results,
                List<Trade> replayTrades,
                int openLongSignals,
                int blockedSignals) {
            try {
                List<Trade> currentTrades = replayTrades.stream()
                        .filter(trade -> trade.getTimestamp().equals(replayTime))
                        .toList();
                String tradeEvents = currentTrades.stream()
                        .map(trade -> trade.getTimestamp() + " " + trade.getSymbol() + " " + trade.getType()
                                + " " + trade.getQuantity() + "@" + String.format(Locale.US, "%.2f", trade.getPrice()))
                        .collect(java.util.stream.Collectors.joining(" | "));
                String topCandidates = results.stream()
                        .sorted(Comparator.naturalOrder())
                        .limit(5)
                        .map(result -> result.getSymbol()
                                + ":" + String.format(Locale.US, "%.3f", result.getScore())
                                + ":" + (result.getDecisionResult() != null ? result.getDecisionResult().getAction().name() : "NO_DECISION")
                                + ":" + firstNonBlank(result.getBlockReason(), result.getReason()))
                        .collect(java.util.stream.Collectors.joining(" | "));
                String openLongCandidates = results.stream()
                        .filter(result -> result.getDecisionResult() != null
                                && result.getDecisionResult().getAction() == DecisionResult.Action.OPEN_LONG)
                        .map(result -> result.getSymbol()
                                + ":score=" + String.format(Locale.US, "%.3f", result.getScore())
                                + ":confidence=" + String.format(Locale.US, "%.3f", result.getConfidence())
                                + ":reason=" + result.getReason()
                                + ":bonus=" + result.getLongBonusSummary())
                        .collect(java.util.stream.Collectors.joining(" | "));
                String blockedCandidates = results.stream()
                        .filter(result -> result.getBlockReason() != null && !result.getBlockReason().isBlank())
                        .map(result -> result.getSymbol()
                                + ":score=" + String.format(Locale.US, "%.3f", result.getScore())
                                + ":block=" + result.getBlockReason())
                        .collect(java.util.stream.Collectors.joining(" | "));
                String scoreComponents = results.stream()
                        .sorted(Comparator.naturalOrder())
                        .limit(10)
                        .map(result -> result.getSymbol() + ":" + result.getScoreComponentSummary())
                        .collect(java.util.stream.Collectors.joining(" | "));
                String line = csv(replayTime.toString()) + ","
                        + csv(config.date().toString()) + ","
                        + csv(timeframe.name()) + ","
                        + csv(config.sourceMode().name()) + ","
                        + config.scanIntervalSeconds() + ","
                        + config.scanOffsetSeconds() + ","
                        + csv(dataSourceSummary()) + ","
                        + csv(monitorStrategyName) + ","
                        + csv(buildMonitorStrategyDetails()) + ","
                        + results.size() + ","
                        + openLongSignals + ","
                        + blockedSignals + ","
                        + replayTrades.size() + ","
                        + csv(tradeEvents) + ","
                        + csv(topCandidates) + ","
                        + csv(openLongCandidates) + ","
                        + csv(blockedCandidates) + ","
                        + csv(scoreComponents) + ","
                        + csv(warmupSummary()) + "\n";
                Files.writeString(replayLogPath, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                appendReplayDetailLog(replayTime, results);
            } catch (IOException e) {
                statusBar.setText("SQL 雷達重播日誌寫入失敗：" + e.getMessage());
            }
        }

        private String dataSourceSummary() {
            if (sourceBySymbol.isEmpty()) {
                return "";
            }
            Map<String, Long> counts = sourceBySymbol.values().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            MarketDataCollectorFeed.SessionBarLoadResult::actualSource,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.counting()));
            String examples = sourceBySymbol.entrySet().stream()
                    .limit(8)
                    .map(entry -> entry.getValue().toSummary(entry.getKey()))
                    .collect(java.util.stream.Collectors.joining(" | "));
            return counts.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("; "))
                    + (examples.isBlank() ? "" : "; examples=" + examples);
        }

        private String warmupSummary() {
            int requested = requestedWarmupBars();
            boolean enabled = isWarmupEnabled();
            long loadedSymbols = warmupBarsBySymbol.values().stream()
                    .filter(bars -> bars != null && !bars.isEmpty())
                    .count();
            int minLoaded = warmupBarsBySymbol.values().stream()
                    .mapToInt(bars -> bars != null ? bars.size() : 0)
                    .min()
                    .orElse(0);
            int maxLoaded = warmupBarsBySymbol.values().stream()
                    .mapToInt(bars -> bars != null ? bars.size() : 0)
                    .max()
                    .orElse(0);
            String examples = warmupBarsBySymbol.entrySet().stream()
                    .limit(8)
                    .map(entry -> entry.getKey() + "=" + (entry.getValue() != null ? entry.getValue().size() : 0))
                    .collect(java.util.stream.Collectors.joining(" | "));
            return "enabled=" + enabled
                    + "; requested=" + requested
                    + "; loaded_symbols=" + loadedSymbols + "/" + warmupBarsBySymbol.size()
                    + "; loaded_range=" + minLoaded + "-" + maxLoaded
                    + (examples.isBlank() ? "" : "; examples=" + examples);
        }

        private void appendReplayDetailLog(LocalDateTime replayTime, List<MarketScanResult> results) throws IOException {
            StringBuilder detail = new StringBuilder();
            for (MarketScanResult result : results) {
                MarketDataCollectorFeed.SessionBarLoadResult source = sourceFor(result.getSymbol());
                List<Bar> warmupBars = warmupFor(result.getSymbol());
                List<Bar> visibleSessionBars = replayFeed.visibleSessionBars(result.getSymbol());
                List<Bar> scannerBars = scannerBarsForLog(result.getSymbol(), replayTime);
                DecisionResult decision = result.getDecisionResult();
                detail.append(csv(replayTime.toString())).append(',')
                        .append(csv(config.date().toString())).append(',')
                        .append(csv(timeframe.name())).append(',')
                        .append(csv(config.sourceMode().name())).append(',')
                        .append(config.scanIntervalSeconds()).append(',')
                        .append(config.scanOffsetSeconds()).append(',')
                        .append(csv(result.getSymbol())).append(',')
                        .append(csv(source != null ? source.actualSource() : "")).append(',')
                        .append(source != null ? source.bars().size() : 0).append(',')
                        .append(source != null ? source.candleCount() : 0).append(',')
                        .append(source != null ? source.tickCount() : 0).append(',')
                        .append(source != null ? source.expectedBars() : 0).append(',')
                        .append(csv("REPLAY_FEED")).append(',')
                        .append(scannerBars.size()).append(',')
                        .append(visibleSessionBars.size()).append(',')
                        .append(csv(visibleSessionBars.isEmpty() ? "" : visibleSessionBars.get(visibleSessionBars.size() - 1).getTimestamp().toString())).append(',')
                        .append(isWarmupEnabled()).append(',')
                        .append(requestedWarmupBars()).append(',')
                        .append(warmupBars.size()).append(',')
                        .append(csv(warmupBars.isEmpty() ? "" : warmupBars.get(0).getTimestamp().toString())).append(',')
                        .append(csv(warmupBars.isEmpty() ? "" : warmupBars.get(warmupBars.size() - 1).getTimestamp().toString())).append(',')
                        .append(csv(monitorStrategyName)).append(',')
                        .append(String.format(Locale.US, "%.3f", result.getScore())).append(',')
                        .append(String.format(Locale.US, "%.3f", result.getConfidence())).append(',')
                        .append(csv(decision != null ? decision.getAction().name() : "NO_DECISION")).append(',')
                        .append(csv(result.getBlockReason())).append(',')
                        .append(csv(result.getReason())).append(',')
                        .append(csv(result.getMarketDecision() != null ? result.getMarketDecision().name() : "")).append(',')
                        .append(csv(result.getTradeMode() != null ? result.getTradeMode().name() : "")).append(',')
                        .append(csv(formatNullable(result.getVwap()))).append(',')
                        .append(csv(formatNullable(result.getVwapSlopePercent()))).append(',')
                        .append(csv(result.getVolumeSustain() != null ? result.getVolumeSustain().toString() : "")).append(',')
                        .append(csv(formatNullable(result.getAtrStopLoss()))).append(',')
                        .append(csv(formatNullable(result.getAtrTakeProfit()))).append(',')
                        .append(csv(result.getLongBonusSummary())).append(',')
                        .append(csv(result.getScoreComponentSummary())).append('\n');
            }
            Files.writeString(replayDetailLogPath, detail.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        private List<Bar> scannerBarsForLog(String symbol, LocalDateTime replayTime) {
            List<Bar> bars = replayFeed.fetchHistoricalBars(symbol, timeframe, request.getBarCount()).stream()
                    .filter(bar -> bar != null && bar.getTimestamp() != null)
                    .sorted(Comparator.comparing(Bar::getTimestamp))
                    .toList();
            return bars;
        }

        private MarketDataCollectorFeed.SessionBarLoadResult sourceFor(String symbol) {
            if (symbol == null) {
                return null;
            }
            MarketDataCollectorFeed.SessionBarLoadResult source = sourceBySymbol.get(symbol);
            if (source != null) {
                return source;
            }
            String normalized = symbol.trim().toUpperCase(Locale.ROOT);
            source = sourceBySymbol.get(normalized);
            if (source != null) {
                return source;
            }
            for (Map.Entry<String, MarketDataCollectorFeed.SessionBarLoadResult> entry : sourceBySymbol.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.trim().equalsIgnoreCase(normalized)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private List<Bar> warmupFor(String symbol) {
            if (symbol == null) {
                return List.of();
            }
            List<Bar> bars = warmupBarsBySymbol.get(symbol);
            if (bars != null) {
                return bars;
            }
            String normalized = normalizeReplaySymbol(symbol);
            bars = warmupBarsBySymbol.get(normalized);
            return bars != null ? bars : List.of();
        }

        private boolean isWarmupEnabled() {
            RadarStrategyConfig radar = request != null ? request.getRadarStrategyConfig() : null;
            return radar != null && radar.isBacktestCrossDayWarmupEnabled();
        }

        private int requestedWarmupBars() {
            RadarStrategyConfig radar = request != null ? request.getRadarStrategyConfig() : null;
            if (radar == null || !radar.isBacktestCrossDayWarmupEnabled()) {
                return 0;
            }
            int minimumForSlowAverage = Math.max(0, radar.getSlowMovingAveragePeriod() * 3);
            return Math.max(radar.getBacktestWarmupBarCount(), minimumForSlowAverage);
        }

        private String formatNullable(Double value) {
            return value != null && Double.isFinite(value) ? String.format(Locale.US, "%.4f", value) : "";
        }

        private String csv(String value) {
            String safe = value != null ? value : "";
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }

        private String firstNonBlank(String first, String second) {
            return first != null && !first.isBlank() ? first : (second != null ? second : "");
        }
    }

    private static class ReplayBarFeed implements MarketDataFeed {
        private final Map<String, List<Bar>> sessionBarsBySymbol;
        private final Map<String, List<Bar>> warmupBarsBySymbol;
        private final Map<String, List<Tick>> sessionTicksBySymbol;
        private final Timeframe defaultTimeframe;
        private LocalDateTime replayTime = LocalDateTime.now();

        ReplayBarFeed(
                Map<String, List<Bar>> sessionBarsBySymbol,
                Map<String, List<Bar>> warmupBarsBySymbol,
                Map<String, List<Tick>> sessionTicksBySymbol,
                Timeframe defaultTimeframe) {
            this.sessionBarsBySymbol = normalizeBarMap(sessionBarsBySymbol);
            this.warmupBarsBySymbol = normalizeBarMap(warmupBarsBySymbol);
            this.sessionTicksBySymbol = normalizeTickMap(sessionTicksBySymbol);
            this.defaultTimeframe = defaultTimeframe != null ? defaultTimeframe : Timeframe.M5;
        }

        void setReplayTime(LocalDateTime replayTime) {
            this.replayTime = replayTime != null ? replayTime : LocalDateTime.now();
        }

        boolean hasSymbol(String symbol) {
            String key = normalizeSymbolKey(symbol);
            return sessionBarsBySymbol.containsKey(key) || warmupBarsBySymbol.containsKey(key);
        }

        List<Bar> visibleSessionBars(String symbol) {
            return visibleSessionBars(symbol, defaultTimeframe);
        }

        List<Bar> visibleSessionBars(String symbol, Timeframe timeframe) {
            String key = normalizeSymbolKey(symbol);
            List<Tick> ticks = sessionTicksBySymbol.getOrDefault(key, List.of());
            if (!ticks.isEmpty()) {
                return aggregateVisibleTicks(ticks, timeframe != null ? timeframe : defaultTimeframe);
            }
            int minutes = Math.max(1, timeframe != null ? timeframe.getMinutes() : defaultTimeframe.getMinutes());
            return sessionBarsBySymbol.getOrDefault(key, List.of()).stream()
                    .filter(bar -> !bar.getTimestamp().plusMinutes(minutes).isAfter(replayTime))
                    .toList();
        }

        @Override
        public void subscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void unsubscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            List<Bar> visibleSession = visibleSessionBars(symbol, timeframe);
            List<Bar> visible = new ArrayList<>();
            visible.addAll(warmupBarsBySymbol.getOrDefault(normalizeSymbolKey(symbol), List.of()));
            visible.addAll(visibleSession);
            int safeCount = Math.max(1, barCount);
            if (visible.size() > safeCount) {
                return new ArrayList<>(visible.subList(visible.size() - safeCount, visible.size()));
            }
            return visible;
        }

        private List<Bar> aggregateVisibleTicks(List<Tick> ticks, Timeframe timeframe) {
            if (ticks == null || ticks.isEmpty() || timeframe == null) {
                return List.of();
            }
            Map<LocalDateTime, MutableReplayBar> grouped = new LinkedHashMap<>();
            long previousVolume = -1L;
            for (Tick tick : ticks) {
                if (tick == null || tick.getTimestamp() == null || tick.getTimestamp().isAfter(replayTime)) {
                    break;
                }
                LocalDateTime bucketTime = floorToTimeframe(tick.getTimestamp(), timeframe);
                MutableReplayBar bar = grouped.computeIfAbsent(bucketTime, ignored -> new MutableReplayBar(bucketTime, tick.getPrice()));
                long volumeContribution = resolveVolumeContribution(previousVolume, tick.getVolume());
                bar.add(tick.getPrice(), volumeContribution);
                previousVolume = tick.getVolume();
            }
            return grouped.values().stream()
                    .map(MutableReplayBar::toBar)
                    .toList();
        }

        private static LocalDateTime floorToTimeframe(LocalDateTime timestamp, Timeframe timeframe) {
            LocalDateTime minute = timestamp.withSecond(0).withNano(0);
            int frameMinutes = Math.max(1, timeframe.getMinutes());
            int minuteOfDay = minute.getHour() * 60 + minute.getMinute();
            int flooredMinuteOfDay = minuteOfDay - (minuteOfDay % frameMinutes);
            return minute.toLocalDate().atStartOfDay().plusMinutes(flooredMinuteOfDay);
        }

        private static long resolveVolumeContribution(long previousVolume, long currentVolume) {
            if (currentVolume <= 0) {
                return 0L;
            }
            if (previousVolume < 0) {
                return currentVolume;
            }
            if (currentVolume >= previousVolume) {
                return currentVolume - previousVolume;
            }
            return currentVolume;
        }

        private static Map<String, List<Bar>> normalizeBarMap(Map<String, List<Bar>> source) {
            Map<String, List<Bar>> normalized = new LinkedHashMap<>();
            if (source == null) {
                return normalized;
            }
            for (Map.Entry<String, List<Bar>> entry : source.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<Bar> bars = entry.getValue() != null
                        ? entry.getValue().stream()
                                .filter(bar -> bar != null && bar.getTimestamp() != null)
                                .sorted(Comparator.comparing(Bar::getTimestamp))
                                .toList()
                        : List.of();
                normalized.put(normalizeSymbolKey(entry.getKey()), bars);
            }
            return normalized;
        }

        private static Map<String, List<Tick>> normalizeTickMap(Map<String, List<Tick>> source) {
            Map<String, List<Tick>> normalized = new LinkedHashMap<>();
            if (source == null) {
                return normalized;
            }
            for (Map.Entry<String, List<Tick>> entry : source.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<Tick> ticks = entry.getValue() != null
                        ? entry.getValue().stream()
                                .filter(tick -> tick != null && tick.getTimestamp() != null)
                                .sorted(Comparator.comparing(Tick::getTimestamp))
                                .toList()
                        : List.of();
                normalized.put(normalizeSymbolKey(entry.getKey()), ticks);
            }
            return normalized;
        }

        private static String normalizeSymbolKey(String symbol) {
            return symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
        }

        private static class MutableReplayBar {
            private final LocalDateTime timestamp;
            private final double open;
            private double high;
            private double low;
            private double close;
            private long volume;

            private MutableReplayBar(LocalDateTime timestamp, double firstPrice) {
                this.timestamp = timestamp;
                this.open = firstPrice;
                this.high = firstPrice;
                this.low = firstPrice;
                this.close = firstPrice;
            }

            private void add(double price, long volumeContribution) {
                high = Math.max(high, price);
                low = Math.min(low, price);
                close = price;
                volume += Math.max(0L, volumeContribution);
            }

            private Bar toBar() {
                return new Bar(timestamp, open, high, low, close, volume);
            }
        }
    }

    private LocalDate[] promptSqlRadarBacktestDateRange() {
        JTextField startField = new JTextField(selectedQueryDate.toString(), 12);
        JTextField endField = new JTextField(selectedQueryDate.toString(), 12);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("開始日期"), gbc);
        gbc.gridx = 1;
        panel.add(startField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("結束日期"), gbc);
        gbc.gridx = 1;
        panel.add(endField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(new JLabel("格式：yyyy-MM-dd；同一天請輸入相同起訖日期。"), gbc);

        while (true) {
            int option = JOptionPane.showConfirmDialog(
                    this,
                    panel,
                    "SQL 雷達批次回測日期範圍",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                return null;
            }
            try {
                LocalDate start = LocalDate.parse(startField.getText().trim());
                LocalDate end = LocalDate.parse(endField.getText().trim());
                if (end.isBefore(start)) {
                    JOptionPane.showMessageDialog(this, "結束日期不可早於開始日期。", "SQL 雷達批次回測", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                return new LocalDate[]{start, end};
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(this, "日期格式錯誤，請使用 yyyy-MM-dd。", "SQL 雷達批次回測", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private List<Bar> fetchSqlRadarBacktestWarmupBars(
            MarketDataCollectorFeed sqlFeed,
            String symbol,
            Timeframe timeframe,
            LocalDate date,
            MarketScannerService.ScanRequest request) {
        RadarStrategyConfig radar = request != null && request.getRadarStrategyConfig() != null
                ? request.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        if (!radar.isBacktestCrossDayWarmupEnabled()) {
            return List.of();
        }
        int minimumForSlowAverage = Math.max(0, radar.getSlowMovingAveragePeriod() * 3);
        int warmupCount = Math.max(radar.getBacktestWarmupBarCount(), minimumForSlowAverage);
        return sqlFeed.fetchWarmupBarsBeforeSession(symbol, timeframe, date, warmupCount);
    }

    private RadarReplayBacktestService createRadarReplayService() {
        return new RadarReplayBacktestService(
                1_000_000.0,
                0.001425,
                0.0015,
                0.0005,
                monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                        ? monitorConfig.getEarlyEntryBlockStart()
                        : LocalTime.of(0, 0),
                monitorConfig != null && monitorConfig.isEarlyEntryBlockEnabled()
                        ? monitorConfig.getEarlyEntryBlockEnd()
                        : LocalTime.of(0, 0),
                monitorConfig != null
                        ? monitorConfig.getLatestAutoEntryTime()
                        : LocalTime.of(13, 5),
                monitorConfig != null && monitorConfig.isStopLossCooldownEnabled()
                        ? monitorConfig.getStopLossCooldownMinutes()
                        : 0,
                monitorConfig != null ? monitorConfig.getDailyMaxLoss() : -3_000.0,
                monitorConfig != null ? monitorConfig.getDailyMaxStopLossCount() : 3,
                monitorConfig != null ? monitorConfig.getConsecutiveLossLimit() : 3,
                monitorConfig == null || monitorConfig.isDisableTradingAfterLossLimit(),
                monitorConfig != null ? monitorConfig.getDailyMaxAutoTrades() : 5,
                monitorConfig != null ? monitorConfig.getEntryPacingMinutes() : 5,
                monitorConfig == null || monitorConfig.isOneEntryPerFiveMinuteBar());
    }

    private String buildDayTradeBacktestConfigSummary(String scope, int symbolCount, Timeframe timeframe) {
        return buildDayTradeBacktestConfigSummary(scope, symbolCount, timeframe, selectedQueryDate.toString());
    }

    private String buildDayTradeBacktestConfigSummary(String scope, int symbolCount, Timeframe timeframe, String dateLabel) {
        SignalMonitorConfig activeMonitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDefault();
        RadarStrategyConfig radar = activeMonitorConfig.getRadarStrategyConfig() != null
                ? activeMonitorConfig.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        StringBuilder sb = new StringBuilder();
        sb.append("回測範圍: ").append(scope).append('\n');
        sb.append("資料日期: ").append(dateLabel).append('\n');
        sb.append("回測檔數: ").append(Math.max(1, symbolCount)).append('\n');
        sb.append("資料源: MarketDataCollector SQL\n");
        sb.append("回測週期: ").append(timeframe != null ? timeframe.getLabel() : radar.getDayTradeTimeframe().getLabel()).append('\n');
        sb.append("交易模式: DAY_TRADE（盤中雷達回測強制當沖）\n");
        sb.append("每筆下單: ").append(DAY_TRADE_LOT_SIZE).append(" 股（1 張）\n");
        sb.append("每檔初始資金: 1000000.00\n");
        sb.append("手續費率: 0.001425\n");
        sb.append("當沖證交稅率: 0.0015\n");
        sb.append("滑價: 買進 +0.05% / 賣出 -0.05%（報告拆分為滑價成本）\n");
        sb.append("成交模型: 第 N 根 K 收完產生訊號，第 N+1 根 open 成交\n");
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
        sb.append("日損熔斷: ")
                .append(activeMonitorConfig.isDisableTradingAfterLossLimit() ? "啟用" : "停用")
                .append("，單日最大虧損=").append(String.format(Locale.US, "%.2f", activeMonitorConfig.getDailyMaxLoss()))
                .append("，單日停損上限=").append(activeMonitorConfig.getDailyMaxStopLossCount())
                .append("，連續虧損上限=").append(activeMonitorConfig.getConsecutiveLossLimit())
                .append('\n');
        sb.append("開單節奏: 每日最多 ")
                .append(activeMonitorConfig.getDailyMaxAutoTrades())
                .append(" 筆，開單間隔 ")
                .append(activeMonitorConfig.getEntryPacingMinutes())
                .append(" 分鐘，同一根 5 分 K ")
                .append(activeMonitorConfig.isOneEntryPerFiveMinuteBar() ? "只允許 1 筆" : "允許多筆")
                .append('\n');
        sb.append("雷達掃描間隔: ").append(activeMonitorConfig.getScanIntervalSeconds()).append(" 秒\n");
        sb.append("同股訊號間隔: ").append(activeMonitorConfig.getMinSignalIntervalMinutes()).append(" 分鐘\n");
        sb.append("雷達日內K棒數: ").append(radar.getDayTradeBarCount()).append('\n');
        sb.append("回測跨日暖機: ")
                .append(radar.isBacktestCrossDayWarmupEnabled() ? "啟用 " + radar.getBacktestWarmupBarCount() + " 根" : "停用")
                .append('\n');
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
        sb.append("EMA 單因子進場: ")
                .append(radar.isBlockMovingAverageOnlyEntry() ? "阻擋（需 RSI 或有效放量突破確認）" : "允許")
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
        sb.append("弱勢盤策略: ").append(radar.getWeakMarketLongPolicy().getDisplayName()).append('\n');
        sb.append("弱勢盤強勢股門檻: 強於內部基準 ")
                .append(String.format(Locale.US, "%.2f%%", radar.getWeakOutperformBenchmarkPercent()))
                .append("，強於觀察清單群體 ")
                .append(String.format(Locale.US, "%.2f%%", radar.getWeakOutperformIndustryPercent()))
                .append('\n');
        sb.append("內部市場 ALLOW/BLOCK 門檻: ALLOW VWAP>=")
                .append(String.format(Locale.US, "%.1f%%", radar.getInternalAllowVwapPassPercent()))
                .append("，平均漲跌>=")
                .append(String.format(Locale.US, "%.2f%%", radar.getInternalAllowAverageReturnPercent()))
                .append("，Volume Sustain>=")
                .append(String.format(Locale.US, "%.1f%%", radar.getInternalAllowVolumeSustainPercent()))
                .append("；BLOCK VWAP<")
                .append(String.format(Locale.US, "%.1f%%", radar.getInternalBlockVwapPassPercent()))
                .append("，平均漲跌<=")
                .append(String.format(Locale.US, "%.2f%%", radar.getInternalBlockAverageReturnPercent()))
                .append("，創低多於創高檔數>=")
                .append(radar.getInternalBlockNewLowExcessCount())
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
        List<CompletedTradePair> selectedPairs = selectBatchTradePairs(results);
        double finalValue = initialCapital;
        double realizedPnl = 0.0;
        for (CompletedTradePair pair : selectedPairs) {
            combined.addTrade(pair.buy());
            combined.addTrade(pair.sell());
            realizedPnl += pair.netPnl();
        }
        finalValue += realizedPnl;
        for (BacktestResult result : results) {
            for (BacktestResult.SignalObservation observation : result.getSignalObservations()) {
                combined.addSignalObservation(observation);
            }
            for (BacktestResult.WarmupDiagnostic diagnostic : result.getWarmupDiagnostics()) {
                combined.addWarmupDiagnostic(diagnostic);
            }
        }
        combined.addSnapshot(start, initialCapital, initialCapital, 0.0, 0);
        combined.addSnapshot(end, finalValue, finalValue, 0.0, 0);
        combined.calculate();
        return combined;
    }

    private List<CompletedTradePair> selectBatchTradePairs(List<BacktestResult> results) {
        List<CompletedTradePair> candidates = new ArrayList<>();
        for (BacktestResult result : results) {
            candidates.addAll(toCompletedTradePairs(result.getTrades()));
        }
        candidates.sort(Comparator
                .comparing((CompletedTradePair pair) -> pair.buy().getTimestamp())
                .thenComparing(pair -> pair.buy().getSymbol()));

        SignalMonitorConfig activeMonitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDefault();
        int maxTrades = Math.max(1, activeMonitorConfig.getDailyMaxAutoTrades());
        int pacingMinutes = Math.max(0, activeMonitorConfig.getEntryPacingMinutes());
        boolean oneEntryPerM5 = activeMonitorConfig.isOneEntryPerFiveMinuteBar();
        int maxPositions = monitorDecisionConfig != null
                ? Math.max(1, monitorDecisionConfig.getRiskConfig().getMaxConcurrentPositions())
                : 1;

        List<CompletedTradePair> selected = new ArrayList<>();
        List<CompletedTradePair> active = new ArrayList<>();
        Set<String> entryBuckets = new HashSet<>();
        LocalDateTime lastEntryTime = null;
        int tradesToday = 0;
        LocalDate currentDate = null;
        for (CompletedTradePair pair : candidates) {
            LocalDate entryDate = pair.buy().getTimestamp().toLocalDate();
            if (!entryDate.equals(currentDate)) {
                currentDate = entryDate;
                tradesToday = 0;
                lastEntryTime = null;
                entryBuckets.clear();
                active.clear();
            }
            active.removeIf(openPair -> !openPair.sell().getTimestamp().isAfter(pair.buy().getTimestamp()));
            if (tradesToday >= maxTrades) {
                continue;
            }
            if (active.size() >= maxPositions) {
                continue;
            }
            if (pacingMinutes > 0 && lastEntryTime != null
                    && pair.buy().getTimestamp().isBefore(lastEntryTime.plusMinutes(pacingMinutes))) {
                continue;
            }
            String bucket = fiveMinuteBacktestBucket(pair.buy().getTimestamp());
            if (oneEntryPerM5 && entryBuckets.contains(bucket)) {
                continue;
            }
            selected.add(pair);
            active.add(pair);
            entryBuckets.add(bucket);
            lastEntryTime = pair.buy().getTimestamp();
            tradesToday++;
        }
        return selected;
    }

    private List<CompletedTradePair> toCompletedTradePairs(List<Trade> trades) {
        Map<String, Trade> openBySymbol = new HashMap<>();
        List<CompletedTradePair> pairs = new ArrayList<>();
        List<Trade> sortedTrades = new ArrayList<>(trades != null ? trades : List.of());
        sortedTrades.sort(Comparator.comparing(Trade::getTimestamp));
        for (Trade trade : sortedTrades) {
            if (trade.getType() == TradeType.BUY) {
                openBySymbol.put(trade.getSymbol(), trade);
            } else if (trade.getType() == TradeType.SELL) {
                Trade buy = openBySymbol.remove(trade.getSymbol());
                if (buy != null) {
                    pairs.add(new CompletedTradePair(buy, trade));
                }
            }
        }
        return pairs;
    }

    private Map<String, List<Trade>> groupTradesBySymbol(List<Trade> trades) {
        Map<String, List<Trade>> grouped = new HashMap<>();
        for (Trade trade : trades != null ? trades : List.<Trade>of()) {
            grouped.computeIfAbsent(trade.getSymbol(), ignored -> new ArrayList<>()).add(trade);
        }
        return grouped;
    }

    private String fiveMinuteBacktestBucket(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        int bucketMinute = (time.getMinute() / 5) * 5;
        return time.toLocalDate() + "T" + String.format("%02d:%02d", time.getHour(), bucketMinute);
    }

    private record CompletedTradePair(Trade buy, Trade sell) {
        double netPnl() {
            return sell.getNetProceeds() - buy.getTotalCost();
        }
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
            case DAY_TRADE -> monitorDecisionConfig != null ? monitorDecisionConfig : createDayTradeStandardMonitorConfig();
            case SHORT_SWING -> createAggressiveMonitorConfig();
            case SWING_TRADE -> createBalancedMonitorConfig();
            default -> DecisionConfig.createDefault();
        };
    }

    private void initializeSignalMonitor() {
        monitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDayTradeStandardTemplate();
        monitorDecisionConfig = monitorDecisionConfig != null
                ? monitorDecisionConfig
                : createDayTradeStandardMonitorConfig();
        monitorPortfolio = monitorPortfolio != null ? monitorPortfolio : new Portfolio(1_000_000.0);
        executionEngine = new ExecutionEngine(ExecutionMode.PAPER_TRADING, monitorPortfolio, 0.001425, 0.0015);
        if (executionStatusDock != null) {
            executionStatusDock.setExecutionEngine(executionEngine);
        }

        signalMonitor = new SignalMonitorService(dataFeed, monitorConfig, monitorDecisionConfig);
        signalMonitor.setMarketContextProvider(symbols -> buildInternalMarketContext(new ArrayList<>(symbols), TradeMode.DAY_TRADE));
        signalMonitor.setOnMarketContextUpdate(this::updateMarketContextDocks);
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
        config.getRiskConfig().setMaxConcurrentPositions(3);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createDayTradeDefensiveMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.50);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.6);
        config.getRiskConfig().setMaxConcurrentPositions(2);
        config.getRiskConfig().setMaxPositionSizePercent(0.20);
        config.getRiskConfig().setMinCashReservePercent(0.20);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createDayTradeStandardMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.40);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.35);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.5);
        config.getRiskConfig().setMaxConcurrentPositions(3);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private DecisionConfig createDayTradeMomentumMonitorConfig() {
        DecisionConfig config = DecisionConfig.createDefault();
        config.setRegimeDetectionEnabled(false);
        config.setTrendAnalysisEnabled(false);
        config.setRiskManagementEnabled(true);
        config.getVotingConfig().setLongEntryThreshold(0.45);
        config.getVotingConfig().setShortEntryThreshold(0.95);
        config.getVotingConfig().setExitThreshold(0.30);
        config.getVotingConfig().setMinVotingStrategies(1);
        config.getRiskConfig().setMinRiskRewardRatio(1.8);
        config.getRiskConfig().setMaxConcurrentPositions(3);
        config.getRiskConfig().setMaxPositionSizePercent(0.25);
        config.getRiskConfig().setMinCashReservePercent(0.10);
        config.getRiskConfig().setAllowShortSelling(false);
        return config;
    }

    private String getMonitorTemplateDescription(int index) {
        return switch (index) {
            case 1 -> "A組穩健 EMA8/34：跨日暖機、阻擋均線單因子、突破後一根 K 確認，最低分數 0.45。";
            case 2 -> "B組放寬 EMA8/21：較快反應、阻擋均線單因子、不要求突破後一根 K 確認，最低分數 0.40。";
            case 3 -> "C組 SMA8/21 測試：使用 SMA 觀察均線放寬效果，阻擋均線單因子、量能倍數 1.45，最低分數 0.40。";
            default -> "保留目前畫面設定，不套用任何模板。";
        };
    }

    private String getMonitorTemplateName(int index) {
        return switch (index) {
            case 1 -> "A組穩健 EMA8/34";
            case 2 -> "B組放寬 EMA8/21";
            case 3 -> "C組 SMA8/21 測試";
            default -> "目前設定";
        };
    }

    private List<MonitorTemplate> loadMonitorTemplatesForDialog() {
        List<MonitorTemplate> templates = new ArrayList<>();
        templates.add(new MonitorTemplate(
                "目前設定",
                "保留目前畫面設定，不套用任何模板。",
                copyMonitorConfig(monitorConfig != null ? monitorConfig : SignalMonitorConfig.createDefault()),
                copyDecisionConfig(monitorDecisionConfig != null ? monitorDecisionConfig : createDayTradeStandardMonitorConfig()),
                false));
        templates.add(new MonitorTemplate(getMonitorTemplateName(1), getMonitorTemplateDescription(1),
                SignalMonitorConfig.createDayTradeGroupATemplate(), createBConvergenceMonitorConfig(), false));
        templates.add(new MonitorTemplate(getMonitorTemplateName(2), getMonitorTemplateDescription(2),
                SignalMonitorConfig.createDayTradeGroupBTemplate(), createBConvergenceMonitorConfig(), false));
        templates.add(new MonitorTemplate(getMonitorTemplateName(3), getMonitorTemplateDescription(3),
                SignalMonitorConfig.createDayTradeGroupCTemplate(), createBConvergenceMonitorConfig(), false));
        templates.addAll(loadUserMonitorTemplates());
        return templates;
    }

    private SignalMonitorConfig copyMonitorConfig(SignalMonitorConfig source) {
        SignalMonitorConfig copy = new SignalMonitorConfig();
        if (source == null) {
            return copy;
        }
        copy.setScanIntervalSeconds(source.getScanIntervalSeconds());
        copy.setTimeframe(source.getTimeframe());
        copy.setMinSignalIntervalMinutes(source.getMinSignalIntervalMinutes());
        copy.setBarCount(source.getBarCount());
        copy.setTradeMode(source.getTradeMode());
        copy.setBatchScanMode(source.isBatchScanMode());
        copy.setRadarStrategyConfig(source.getRadarStrategyConfig() != null
                ? source.getRadarStrategyConfig().copy()
                : RadarStrategyConfig.createDefault());
        copy.setEarlyEntryBlockEnabled(source.isEarlyEntryBlockEnabled());
        copy.setEarlyEntryBlockStart(source.getEarlyEntryBlockStart());
        copy.setEarlyEntryBlockEnd(source.getEarlyEntryBlockEnd());
        copy.setStopLossCooldownEnabled(source.isStopLossCooldownEnabled());
        copy.setStopLossCooldownMinutes(source.getStopLossCooldownMinutes());
        copy.setLatestAutoEntryTime(source.getLatestAutoEntryTime());
        copy.setDailyMaxLoss(source.getDailyMaxLoss());
        copy.setDailyMaxStopLossCount(source.getDailyMaxStopLossCount());
        copy.setConsecutiveLossLimit(source.getConsecutiveLossLimit());
        copy.setDisableTradingAfterLossLimit(source.isDisableTradingAfterLossLimit());
        copy.setDailyMaxAutoTrades(source.getDailyMaxAutoTrades());
        copy.setEntryPacingMinutes(source.getEntryPacingMinutes());
        copy.setPostExitCooldownMinutes(source.getPostExitCooldownMinutes());
        copy.setOneEntryPerFiveMinuteBar(source.isOneEntryPerFiveMinuteBar());
        copy.setRangeFailureExitEnabled(source.isRangeFailureExitEnabled());
        copy.setRangeFailureExitMinutes(source.getRangeFailureExitMinutes());
        copy.setRangeFailureMinR(source.getRangeFailureMinR());
        copy.setRangeFailureVolumeSustainExitEnabled(source.isRangeFailureVolumeSustainExitEnabled());
        copy.setRsiPeriod(source.getRsiPeriod());
        copy.setRsiOversold(source.getRsiOversold());
        copy.setRsiOverbought(source.getRsiOverbought());
        return copy;
    }

    private List<MonitorTemplate> loadUserMonitorTemplates() {
        if (!Files.exists(MONITOR_TEMPLATE_STORE)) {
            return List.of();
        }
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(MONITOR_TEMPLATE_STORE)) {
            props.load(input);
        } catch (IOException e) {
            System.err.println("[MonitorTemplates] load failed: " + e.getMessage());
            return List.of();
        }
        int count = parseInt(props.getProperty("count"), 0);
        List<MonitorTemplate> templates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "template." + i + ".";
            String name = props.getProperty(prefix + "name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            SignalMonitorConfig monitor = readMonitorConfig(props, prefix);
            DecisionConfig decision = readDecisionConfig(props, prefix);
            templates.add(new MonitorTemplate(
                    name,
                    props.getProperty(prefix + "description", "使用者自訂模板：" + name),
                    monitor,
                    decision,
                    true));
        }
        return templates;
    }

    private void saveUserMonitorTemplate(MonitorTemplate newTemplate) {
        LinkedHashMap<String, MonitorTemplate> templates = new LinkedHashMap<>();
        for (MonitorTemplate template : loadUserMonitorTemplates()) {
            templates.put(template.name(), template);
        }
        templates.put(newTemplate.name(), newTemplate);

        Properties props = new Properties();
        props.setProperty("count", String.valueOf(templates.size()));
        int index = 0;
        for (MonitorTemplate template : templates.values()) {
            String prefix = "template." + index++ + ".";
            props.setProperty(prefix + "name", template.name());
            props.setProperty(prefix + "description", template.description());
            writeMonitorConfig(props, prefix, template.monitorConfig());
            writeDecisionConfig(props, prefix, template.decisionConfig());
        }
        try {
            Files.createDirectories(MONITOR_TEMPLATE_STORE.getParent());
            try (OutputStream output = Files.newOutputStream(MONITOR_TEMPLATE_STORE)) {
                props.store(output, "DreamHouseTrading user monitor templates");
            }
        } catch (IOException e) {
            throw new IllegalStateException("儲存監控模板失敗：" + e.getMessage(), e);
        }
    }

    private boolean deleteUserMonitorTemplate(String templateName) {
        LinkedHashMap<String, MonitorTemplate> templates = new LinkedHashMap<>();
        for (MonitorTemplate template : loadUserMonitorTemplates()) {
            templates.put(template.name(), template);
        }
        if (templates.remove(templateName) == null) {
            return false;
        }

        Properties props = new Properties();
        props.setProperty("count", String.valueOf(templates.size()));
        int index = 0;
        for (MonitorTemplate template : templates.values()) {
            String prefix = "template." + index++ + ".";
            props.setProperty(prefix + "name", template.name());
            props.setProperty(prefix + "description", template.description());
            writeMonitorConfig(props, prefix, template.monitorConfig());
            writeDecisionConfig(props, prefix, template.decisionConfig());
        }
        try {
            Files.createDirectories(MONITOR_TEMPLATE_STORE.getParent());
            try (OutputStream output = Files.newOutputStream(MONITOR_TEMPLATE_STORE)) {
                props.store(output, "DreamHouseTrading user monitor templates");
            }
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("刪除監控模板失敗：" + e.getMessage(), e);
        }
    }

    private void writeMonitorConfig(Properties props, String prefix, SignalMonitorConfig monitor) {
        RadarStrategyConfig radar = monitor.getRadarStrategyConfig() != null
                ? monitor.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        props.setProperty(prefix + "scanIntervalSeconds", String.valueOf(monitor.getScanIntervalSeconds()));
        props.setProperty(prefix + "timeframe", monitor.getTimeframe().name());
        props.setProperty(prefix + "minSignalIntervalMinutes", String.valueOf(monitor.getMinSignalIntervalMinutes()));
        props.setProperty(prefix + "barCount", String.valueOf(monitor.getBarCount()));
        props.setProperty(prefix + "earlyEntryBlockEnabled", String.valueOf(monitor.isEarlyEntryBlockEnabled()));
        props.setProperty(prefix + "earlyEntryBlockStart", monitor.getEarlyEntryBlockStart().toString());
        props.setProperty(prefix + "earlyEntryBlockEnd", monitor.getEarlyEntryBlockEnd().toString());
        props.setProperty(prefix + "stopLossCooldownEnabled", String.valueOf(monitor.isStopLossCooldownEnabled()));
        props.setProperty(prefix + "stopLossCooldownMinutes", String.valueOf(monitor.getStopLossCooldownMinutes()));
        props.setProperty(prefix + "latestAutoEntryTime", monitor.getLatestAutoEntryTime().toString());
        props.setProperty(prefix + "dailyMaxLoss", String.valueOf(monitor.getDailyMaxLoss()));
        props.setProperty(prefix + "dailyMaxStopLossCount", String.valueOf(monitor.getDailyMaxStopLossCount()));
        props.setProperty(prefix + "consecutiveLossLimit", String.valueOf(monitor.getConsecutiveLossLimit()));
        props.setProperty(prefix + "disableTradingAfterLossLimit", String.valueOf(monitor.isDisableTradingAfterLossLimit()));
        props.setProperty(prefix + "dailyMaxAutoTrades", String.valueOf(monitor.getDailyMaxAutoTrades()));
        props.setProperty(prefix + "entryPacingMinutes", String.valueOf(monitor.getEntryPacingMinutes()));
        props.setProperty(prefix + "postExitCooldownMinutes", String.valueOf(monitor.getPostExitCooldownMinutes()));
        props.setProperty(prefix + "oneEntryPerFiveMinuteBar", String.valueOf(monitor.isOneEntryPerFiveMinuteBar()));
        props.setProperty(prefix + "rangeFailureExitEnabled", String.valueOf(monitor.isRangeFailureExitEnabled()));
        props.setProperty(prefix + "rangeFailureExitMinutes", String.valueOf(monitor.getRangeFailureExitMinutes()));
        props.setProperty(prefix + "rangeFailureMinR", String.valueOf(monitor.getRangeFailureMinR()));
        props.setProperty(prefix + "rangeFailureVolumeSustainExitEnabled", String.valueOf(monitor.isRangeFailureVolumeSustainExitEnabled()));

        props.setProperty(prefix + "radar.dayTradeTimeframe", radar.getDayTradeTimeframe().name());
        props.setProperty(prefix + "radar.executionConfirmationTimeframe", radar.getExecutionConfirmationTimeframe().name());
        props.setProperty(prefix + "radar.dayTradeBarCount", String.valueOf(radar.getDayTradeBarCount()));
        props.setProperty(prefix + "radar.rsiEnabled", String.valueOf(radar.isRsiEnabled()));
        props.setProperty(prefix + "radar.rsiPeriod", String.valueOf(radar.getRsiPeriod()));
        props.setProperty(prefix + "radar.rsiOversold", String.valueOf(radar.getRsiOversold()));
        props.setProperty(prefix + "radar.rsiOverbought", String.valueOf(radar.getRsiOverbought()));
        props.setProperty(prefix + "radar.rsiWeight", String.valueOf(radar.getRsiWeight()));
        props.setProperty(prefix + "radar.requireRsiEntryConfirmation", String.valueOf(radar.isRequireRsiEntryConfirmation()));
        props.setProperty(prefix + "radar.movingAverageEnabled", String.valueOf(radar.isMovingAverageEnabled()));
        props.setProperty(prefix + "radar.movingAverageType", radar.getMovingAverageType().name());
        props.setProperty(prefix + "radar.fastMovingAveragePeriod", String.valueOf(radar.getFastMovingAveragePeriod()));
        props.setProperty(prefix + "radar.slowMovingAveragePeriod", String.valueOf(radar.getSlowMovingAveragePeriod()));
        props.setProperty(prefix + "radar.movingAverageWeight", String.valueOf(radar.getMovingAverageWeight()));
        props.setProperty(prefix + "radar.volumeBreakoutEnabled", String.valueOf(radar.isVolumeBreakoutEnabled()));
        props.setProperty(prefix + "radar.breakoutLookbackBars", String.valueOf(radar.getBreakoutLookbackBars()));
        props.setProperty(prefix + "radar.volumeMultiplier", String.valueOf(radar.getVolumeMultiplier()));
        props.setProperty(prefix + "radar.volumeBreakoutWeight", String.valueOf(radar.getVolumeBreakoutWeight()));
        props.setProperty(prefix + "radar.minimumEntryScore", String.valueOf(radar.getMinimumEntryScore()));
        props.setProperty(prefix + "radar.blockBreakoutOnRsiOverbought", String.valueOf(radar.isBlockBreakoutOnRsiOverbought()));
        props.setProperty(prefix + "radar.requireBreakoutContinuation", String.valueOf(radar.isRequireBreakoutContinuation()));
        props.setProperty(prefix + "radar.requirePriceAboveVwapForLong", String.valueOf(radar.isRequirePriceAboveVwapForLong()));
        props.setProperty(prefix + "radar.requireBreakoutNextBarConfirmation", String.valueOf(radar.isRequireBreakoutNextBarConfirmation()));
        props.setProperty(prefix + "radar.blockMovingAverageOnlyEntry", String.valueOf(radar.isBlockMovingAverageOnlyEntry()));
        props.setProperty(prefix + "radar.maxEntryRiseFromRecentLowPercent", String.valueOf(radar.getMaxEntryRiseFromRecentLowPercent()));
        props.setProperty(prefix + "radar.marketRegimeFilterEnabled", String.valueOf(radar.isMarketRegimeFilterEnabled()));
        props.setProperty(prefix + "radar.weakMarketStrictLongEnabled", String.valueOf(radar.isWeakMarketStrictLongEnabled()));
        props.setProperty(prefix + "radar.weakMarketLongPolicy", radar.getWeakMarketLongPolicy().name());
        props.setProperty(prefix + "radar.weakOutperformBenchmarkPercent", String.valueOf(radar.getWeakOutperformBenchmarkPercent()));
        props.setProperty(prefix + "radar.weakOutperformIndustryPercent", String.valueOf(radar.getWeakOutperformIndustryPercent()));
        props.setProperty(prefix + "radar.internalAllowVwapPassPercent", String.valueOf(radar.getInternalAllowVwapPassPercent()));
        props.setProperty(prefix + "radar.internalAllowAverageReturnPercent", String.valueOf(radar.getInternalAllowAverageReturnPercent()));
        props.setProperty(prefix + "radar.internalAllowVolumeSustainPercent", String.valueOf(radar.getInternalAllowVolumeSustainPercent()));
        props.setProperty(prefix + "radar.internalBlockVwapPassPercent", String.valueOf(radar.getInternalBlockVwapPassPercent()));
        props.setProperty(prefix + "radar.internalBlockAverageReturnPercent", String.valueOf(radar.getInternalBlockAverageReturnPercent()));
        props.setProperty(prefix + "radar.internalBlockNewLowExcessCount", String.valueOf(radar.getInternalBlockNewLowExcessCount()));
        props.setProperty(prefix + "radar.rangeMarketRequiresVwapAndVolume", String.valueOf(radar.isRangeMarketRequiresVwapAndVolume()));
        props.setProperty(prefix + "radar.volumeSustainEnabled", String.valueOf(radar.isVolumeSustainEnabled()));
        props.setProperty(prefix + "radar.atrRiskEnabled", String.valueOf(radar.isAtrRiskEnabled()));
        props.setProperty(prefix + "radar.atrChaseLimitEnabled", String.valueOf(radar.isAtrChaseLimitEnabled()));
        props.setProperty(prefix + "radar.atrPeriod", String.valueOf(radar.getAtrPeriod()));
        props.setProperty(prefix + "radar.atrStopMultiplier", String.valueOf(radar.getAtrStopMultiplier()));
        props.setProperty(prefix + "radar.atrTakeProfitMultiplier", String.valueOf(radar.getAtrTakeProfitMultiplier()));
        props.setProperty(prefix + "radar.atrChaseLimitMultiplier", String.valueOf(radar.getAtrChaseLimitMultiplier()));
        props.setProperty(prefix + "radar.backtestCrossDayWarmupEnabled", String.valueOf(radar.isBacktestCrossDayWarmupEnabled()));
        props.setProperty(prefix + "radar.backtestWarmupBarCount", String.valueOf(radar.getBacktestWarmupBarCount()));
    }

    private SignalMonitorConfig readMonitorConfig(Properties props, String prefix) {
        SignalMonitorConfig monitor = SignalMonitorConfig.createDayTradeStandardTemplate();
        RadarStrategyConfig radar = monitor.getRadarStrategyConfig().copy();
        monitor.setScanIntervalSeconds(parseInt(props.getProperty(prefix + "scanIntervalSeconds"), monitor.getScanIntervalSeconds()));
        monitor.setTimeframe(parseEnum(props.getProperty(prefix + "timeframe"), Timeframe.class, monitor.getTimeframe()));
        monitor.setMinSignalIntervalMinutes(parseInt(props.getProperty(prefix + "minSignalIntervalMinutes"), monitor.getMinSignalIntervalMinutes()));
        monitor.setBarCount(parseInt(props.getProperty(prefix + "barCount"), monitor.getBarCount()));
        monitor.setEarlyEntryBlockEnabled(parseBoolean(props.getProperty(prefix + "earlyEntryBlockEnabled"), monitor.isEarlyEntryBlockEnabled()));
        monitor.setEarlyEntryBlockStart(parseTime(props.getProperty(prefix + "earlyEntryBlockStart"), monitor.getEarlyEntryBlockStart()));
        monitor.setEarlyEntryBlockEnd(parseTime(props.getProperty(prefix + "earlyEntryBlockEnd"), monitor.getEarlyEntryBlockEnd()));
        monitor.setStopLossCooldownEnabled(parseBoolean(props.getProperty(prefix + "stopLossCooldownEnabled"), monitor.isStopLossCooldownEnabled()));
        monitor.setStopLossCooldownMinutes(parseInt(props.getProperty(prefix + "stopLossCooldownMinutes"), monitor.getStopLossCooldownMinutes()));
        monitor.setLatestAutoEntryTime(parseTime(props.getProperty(prefix + "latestAutoEntryTime"), monitor.getLatestAutoEntryTime()));
        monitor.setDailyMaxLoss(parseDouble(props.getProperty(prefix + "dailyMaxLoss"), monitor.getDailyMaxLoss()));
        monitor.setDailyMaxStopLossCount(parseInt(props.getProperty(prefix + "dailyMaxStopLossCount"), monitor.getDailyMaxStopLossCount()));
        monitor.setConsecutiveLossLimit(parseInt(props.getProperty(prefix + "consecutiveLossLimit"), monitor.getConsecutiveLossLimit()));
        monitor.setDisableTradingAfterLossLimit(parseBoolean(props.getProperty(prefix + "disableTradingAfterLossLimit"), monitor.isDisableTradingAfterLossLimit()));
        monitor.setDailyMaxAutoTrades(parseInt(props.getProperty(prefix + "dailyMaxAutoTrades"), monitor.getDailyMaxAutoTrades()));
        monitor.setEntryPacingMinutes(parseInt(props.getProperty(prefix + "entryPacingMinutes"), monitor.getEntryPacingMinutes()));
        monitor.setPostExitCooldownMinutes(parseInt(props.getProperty(prefix + "postExitCooldownMinutes"), monitor.getPostExitCooldownMinutes()));
        monitor.setOneEntryPerFiveMinuteBar(parseBoolean(props.getProperty(prefix + "oneEntryPerFiveMinuteBar"), monitor.isOneEntryPerFiveMinuteBar()));
        monitor.setRangeFailureExitEnabled(parseBoolean(props.getProperty(prefix + "rangeFailureExitEnabled"), monitor.isRangeFailureExitEnabled()));
        monitor.setRangeFailureExitMinutes(parseInt(props.getProperty(prefix + "rangeFailureExitMinutes"), monitor.getRangeFailureExitMinutes()));
        monitor.setRangeFailureMinR(parseDouble(props.getProperty(prefix + "rangeFailureMinR"), monitor.getRangeFailureMinR()));
        monitor.setRangeFailureVolumeSustainExitEnabled(parseBoolean(props.getProperty(prefix + "rangeFailureVolumeSustainExitEnabled"), monitor.isRangeFailureVolumeSustainExitEnabled()));

        radar.setDayTradeTimeframe(parseEnum(props.getProperty(prefix + "radar.dayTradeTimeframe"), Timeframe.class, radar.getDayTradeTimeframe()));
        radar.setExecutionConfirmationTimeframe(parseEnum(props.getProperty(prefix + "radar.executionConfirmationTimeframe"), Timeframe.class, radar.getExecutionConfirmationTimeframe()));
        radar.setDayTradeBarCount(parseInt(props.getProperty(prefix + "radar.dayTradeBarCount"), radar.getDayTradeBarCount()));
        radar.setRsiEnabled(parseBoolean(props.getProperty(prefix + "radar.rsiEnabled"), radar.isRsiEnabled()));
        radar.setRsiPeriod(parseInt(props.getProperty(prefix + "radar.rsiPeriod"), radar.getRsiPeriod()));
        radar.setRsiOversold(parseDouble(props.getProperty(prefix + "radar.rsiOversold"), radar.getRsiOversold()));
        radar.setRsiOverbought(parseDouble(props.getProperty(prefix + "radar.rsiOverbought"), radar.getRsiOverbought()));
        radar.setRsiWeight(parseDouble(props.getProperty(prefix + "radar.rsiWeight"), radar.getRsiWeight()));
        radar.setRequireRsiEntryConfirmation(parseBoolean(props.getProperty(prefix + "radar.requireRsiEntryConfirmation"), radar.isRequireRsiEntryConfirmation()));
        radar.setMovingAverageEnabled(parseBoolean(props.getProperty(prefix + "radar.movingAverageEnabled"), radar.isMovingAverageEnabled()));
        radar.setMovingAverageType(parseEnum(props.getProperty(prefix + "radar.movingAverageType"), RadarStrategyConfig.MovingAverageType.class, radar.getMovingAverageType()));
        radar.setFastMovingAveragePeriod(parseInt(props.getProperty(prefix + "radar.fastMovingAveragePeriod"), radar.getFastMovingAveragePeriod()));
        radar.setSlowMovingAveragePeriod(parseInt(props.getProperty(prefix + "radar.slowMovingAveragePeriod"), radar.getSlowMovingAveragePeriod()));
        radar.setMovingAverageWeight(parseDouble(props.getProperty(prefix + "radar.movingAverageWeight"), radar.getMovingAverageWeight()));
        radar.setVolumeBreakoutEnabled(parseBoolean(props.getProperty(prefix + "radar.volumeBreakoutEnabled"), radar.isVolumeBreakoutEnabled()));
        radar.setBreakoutLookbackBars(parseInt(props.getProperty(prefix + "radar.breakoutLookbackBars"), radar.getBreakoutLookbackBars()));
        radar.setVolumeMultiplier(parseDouble(props.getProperty(prefix + "radar.volumeMultiplier"), radar.getVolumeMultiplier()));
        radar.setVolumeBreakoutWeight(parseDouble(props.getProperty(prefix + "radar.volumeBreakoutWeight"), radar.getVolumeBreakoutWeight()));
        radar.setMinimumEntryScore(parseDouble(props.getProperty(prefix + "radar.minimumEntryScore"), radar.getMinimumEntryScore()));
        radar.setBlockBreakoutOnRsiOverbought(parseBoolean(props.getProperty(prefix + "radar.blockBreakoutOnRsiOverbought"), radar.isBlockBreakoutOnRsiOverbought()));
        radar.setRequireBreakoutContinuation(parseBoolean(props.getProperty(prefix + "radar.requireBreakoutContinuation"), radar.isRequireBreakoutContinuation()));
        radar.setRequirePriceAboveVwapForLong(parseBoolean(props.getProperty(prefix + "radar.requirePriceAboveVwapForLong"), radar.isRequirePriceAboveVwapForLong()));
        radar.setRequireBreakoutNextBarConfirmation(parseBoolean(props.getProperty(prefix + "radar.requireBreakoutNextBarConfirmation"), radar.isRequireBreakoutNextBarConfirmation()));
        radar.setBlockMovingAverageOnlyEntry(parseBoolean(props.getProperty(prefix + "radar.blockMovingAverageOnlyEntry"), radar.isBlockMovingAverageOnlyEntry()));
        radar.setMaxEntryRiseFromRecentLowPercent(parseDouble(props.getProperty(prefix + "radar.maxEntryRiseFromRecentLowPercent"), radar.getMaxEntryRiseFromRecentLowPercent()));
        radar.setMarketRegimeFilterEnabled(parseBoolean(props.getProperty(prefix + "radar.marketRegimeFilterEnabled"), radar.isMarketRegimeFilterEnabled()));
        radar.setWeakMarketStrictLongEnabled(parseBoolean(props.getProperty(prefix + "radar.weakMarketStrictLongEnabled"), radar.isWeakMarketStrictLongEnabled()));
        radar.setWeakMarketLongPolicy(parseEnum(props.getProperty(prefix + "radar.weakMarketLongPolicy"), WeakMarketLongPolicy.class, radar.getWeakMarketLongPolicy()));
        radar.setWeakOutperformBenchmarkPercent(parseDouble(props.getProperty(prefix + "radar.weakOutperformBenchmarkPercent"), radar.getWeakOutperformBenchmarkPercent()));
        radar.setWeakOutperformIndustryPercent(parseDouble(props.getProperty(prefix + "radar.weakOutperformIndustryPercent"), radar.getWeakOutperformIndustryPercent()));
        radar.setInternalAllowVwapPassPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowVwapPassPercent"), radar.getInternalAllowVwapPassPercent()));
        radar.setInternalAllowAverageReturnPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowAverageReturnPercent"), radar.getInternalAllowAverageReturnPercent()));
        radar.setInternalAllowVolumeSustainPercent(parseDouble(props.getProperty(prefix + "radar.internalAllowVolumeSustainPercent"), radar.getInternalAllowVolumeSustainPercent()));
        radar.setInternalBlockVwapPassPercent(parseDouble(props.getProperty(prefix + "radar.internalBlockVwapPassPercent"), radar.getInternalBlockVwapPassPercent()));
        radar.setInternalBlockAverageReturnPercent(parseDouble(props.getProperty(prefix + "radar.internalBlockAverageReturnPercent"), radar.getInternalBlockAverageReturnPercent()));
        radar.setInternalBlockNewLowExcessCount(parseInt(props.getProperty(prefix + "radar.internalBlockNewLowExcessCount"), radar.getInternalBlockNewLowExcessCount()));
        radar.setRangeMarketRequiresVwapAndVolume(parseBoolean(props.getProperty(prefix + "radar.rangeMarketRequiresVwapAndVolume"), radar.isRangeMarketRequiresVwapAndVolume()));
        radar.setVolumeSustainEnabled(parseBoolean(props.getProperty(prefix + "radar.volumeSustainEnabled"), radar.isVolumeSustainEnabled()));
        radar.setAtrRiskEnabled(parseBoolean(props.getProperty(prefix + "radar.atrRiskEnabled"), radar.isAtrRiskEnabled()));
        radar.setAtrChaseLimitEnabled(parseBoolean(props.getProperty(prefix + "radar.atrChaseLimitEnabled"), radar.isAtrChaseLimitEnabled()));
        radar.setAtrPeriod(parseInt(props.getProperty(prefix + "radar.atrPeriod"), radar.getAtrPeriod()));
        radar.setAtrStopMultiplier(parseDouble(props.getProperty(prefix + "radar.atrStopMultiplier"), radar.getAtrStopMultiplier()));
        radar.setAtrTakeProfitMultiplier(parseDouble(props.getProperty(prefix + "radar.atrTakeProfitMultiplier"), radar.getAtrTakeProfitMultiplier()));
        radar.setAtrChaseLimitMultiplier(parseDouble(props.getProperty(prefix + "radar.atrChaseLimitMultiplier"), radar.getAtrChaseLimitMultiplier()));
        radar.setBacktestCrossDayWarmupEnabled(parseBoolean(props.getProperty(prefix + "radar.backtestCrossDayWarmupEnabled"), radar.isBacktestCrossDayWarmupEnabled()));
        radar.setBacktestWarmupBarCount(parseInt(props.getProperty(prefix + "radar.backtestWarmupBarCount"), radar.getBacktestWarmupBarCount()));
        monitor.setRadarStrategyConfig(radar);
        return monitor;
    }

    private void writeDecisionConfig(Properties props, String prefix, DecisionConfig decision) {
        props.setProperty(prefix + "decision.longEntryThreshold", String.valueOf(decision.getVotingConfig().getLongEntryThreshold()));
        props.setProperty(prefix + "decision.exitThreshold", String.valueOf(decision.getVotingConfig().getExitThreshold()));
        props.setProperty(prefix + "decision.minVotingStrategies", String.valueOf(decision.getVotingConfig().getMinVotingStrategies()));
        props.setProperty(prefix + "decision.riskManagementEnabled", String.valueOf(decision.isRiskManagementEnabled()));
        props.setProperty(prefix + "decision.maxConcurrentPositions", String.valueOf(decision.getRiskConfig().getMaxConcurrentPositions()));
        props.setProperty(prefix + "decision.minRiskRewardRatio", String.valueOf(decision.getRiskConfig().getMinRiskRewardRatio()));
        props.setProperty(prefix + "decision.minVolatilityPercent", String.valueOf(decision.getRiskConfig().getMinVolatilityPercent()));
        props.setProperty(prefix + "decision.maxVolatilityPercent", String.valueOf(decision.getRiskConfig().getMaxVolatilityPercent()));
    }

    private DecisionConfig readDecisionConfig(Properties props, String prefix) {
        DecisionConfig decision = createDayTradeStandardMonitorConfig();
        decision.getVotingConfig().setLongEntryThreshold(parseDouble(props.getProperty(prefix + "decision.longEntryThreshold"), decision.getVotingConfig().getLongEntryThreshold()));
        decision.getVotingConfig().setExitThreshold(parseDouble(props.getProperty(prefix + "decision.exitThreshold"), decision.getVotingConfig().getExitThreshold()));
        decision.getVotingConfig().setMinVotingStrategies(parseInt(props.getProperty(prefix + "decision.minVotingStrategies"), decision.getVotingConfig().getMinVotingStrategies()));
        decision.setRiskManagementEnabled(parseBoolean(props.getProperty(prefix + "decision.riskManagementEnabled"), decision.isRiskManagementEnabled()));
        decision.getRiskConfig().setMaxConcurrentPositions(parseInt(props.getProperty(prefix + "decision.maxConcurrentPositions"), decision.getRiskConfig().getMaxConcurrentPositions()));
        decision.getRiskConfig().setMinRiskRewardRatio(parseDouble(props.getProperty(prefix + "decision.minRiskRewardRatio"), decision.getRiskConfig().getMinRiskRewardRatio()));
        decision.getRiskConfig().setMinVolatilityPercent(parseDouble(props.getProperty(prefix + "decision.minVolatilityPercent"), decision.getRiskConfig().getMinVolatilityPercent()));
        decision.getRiskConfig().setMaxVolatilityPercent(parseDouble(props.getProperty(prefix + "decision.maxVolatilityPercent"), decision.getRiskConfig().getMaxVolatilityPercent()));
        decision.getRiskConfig().setAllowShortSelling(false);
        return decision;
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        try {
            return value == null ? fallback : LocalTime.parse(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private String buildMonitorStrategyDetails() {
        SignalMonitorConfig activeMonitorConfig = monitorConfig != null
                ? monitorConfig
                : SignalMonitorConfig.createDayTradeStandardTemplate();
        RadarStrategyConfig radar = activeMonitorConfig.getRadarStrategyConfig() != null
                ? activeMonitorConfig.getRadarStrategyConfig()
                : RadarStrategyConfig.createDefault();
        DecisionConfig activeDecisionConfig = monitorDecisionConfig != null
                ? monitorDecisionConfig
                : createDayTradeStandardMonitorConfig();

        return String.format(Locale.US,
                "strategy=%s;scanIntervalSec=%d;mainTimeframe=%s;dayTradeTimeframe=%s;executionConfirmTimeframe=%s;barCount=%d;dayTradeBars=%d;minSignalIntervalMin=%d;"
                        + "earlyBlock=%s %s-%s;latestEntry=%s;stopLossCooldown=%s %dmin;postExitCooldownMin=%d;"
                        + "maxPositions=%d;dailyMaxLoss=%.2f;dailyMaxStopLossCount=%d;consecutiveLossLimit=%d;disableAfterLossLimit=%s;dailyMaxAutoTrades=%d;entryPacingMin=%d;oneEntryPerM5=%s;"
                        + "longThreshold=%.3f;exitThreshold=%.3f;minStrategies=%d;minRR=%.3f;minVolatility=%.3f;maxVolatility=%.3f;"
                        + "rsiEnabled=%s;rsiPeriod=%d;rsiOversold=%.2f;rsiOverbought=%.2f;rsiWeight=%.2f;requireRsiConfirm=%s;blockRsiOverbought=%s;"
                        + "maEnabled=%s;maType=%s;maFast=%d;maSlow=%d;maWeight=%.2f;"
                        + "volumeBreakout=%s;breakoutLookback=%d;volumeMultiplier=%.2f;volumeWeight=%.2f;volumeSustain=%s;breakoutContinuation=%s;nextBarConfirm=%s;blockMaOnlyEntry=%s;"
                        + "requireAboveVwap=%s;rangeRequiresVwapVolume=%s;rangeFailureExit=%s;rangeFailureMinutes=%d;rangeFailureMinR=%.2f;rangeVolumeFailExit=%s;"
                        + "marketRegimeFilter=%s;internalAllowVwap=%.2f;internalAllowAvgReturn=%.2f;internalAllowVolumeSustain=%.2f;"
                        + "internalBlockVwap=%.2f;internalBlockAvgReturn=%.2f;internalBlockNewLowExcess=%d;"
                        + "weakStrictLong=%s;weakPolicy=%s;weakOutperformInternalBenchmark=%.2f;weakOutperformWatchlistGroup=%.2f;"
                        + "atrRisk=%s;atrChaseLimitEnabled=%s;atrPeriod=%d;atrStop=%.2f;atrTakeProfit=%.2f;atrChaseLimit=%.2f;maxEntryRiseFromRecentLow=%.3f;minimumEntryScore=%.3f",
                monitorStrategyName,
                activeMonitorConfig.getScanIntervalSeconds(),
                activeMonitorConfig.getTimeframe(),
                radar.getDayTradeTimeframe(),
                radar.getExecutionConfirmationTimeframe(),
                activeMonitorConfig.getBarCount(),
                radar.getDayTradeBarCount(),
                activeMonitorConfig.getMinSignalIntervalMinutes(),
                activeMonitorConfig.isEarlyEntryBlockEnabled(),
                activeMonitorConfig.getEarlyEntryBlockStart(),
                activeMonitorConfig.getEarlyEntryBlockEnd(),
                activeMonitorConfig.getLatestAutoEntryTime(),
                activeMonitorConfig.isStopLossCooldownEnabled(),
                activeMonitorConfig.getStopLossCooldownMinutes(),
                activeMonitorConfig.getPostExitCooldownMinutes(),
                activeDecisionConfig.getRiskConfig().getMaxConcurrentPositions(),
                activeMonitorConfig.getDailyMaxLoss(),
                activeMonitorConfig.getDailyMaxStopLossCount(),
                activeMonitorConfig.getConsecutiveLossLimit(),
                activeMonitorConfig.isDisableTradingAfterLossLimit(),
                activeMonitorConfig.getDailyMaxAutoTrades(),
                activeMonitorConfig.getEntryPacingMinutes(),
                activeMonitorConfig.isOneEntryPerFiveMinuteBar(),
                activeDecisionConfig.getVotingConfig().getLongEntryThreshold(),
                activeDecisionConfig.getVotingConfig().getExitThreshold(),
                activeDecisionConfig.getVotingConfig().getMinVotingStrategies(),
                activeDecisionConfig.getRiskConfig().getMinRiskRewardRatio(),
                activeDecisionConfig.getRiskConfig().getMinVolatilityPercent(),
                activeDecisionConfig.getRiskConfig().getMaxVolatilityPercent(),
                radar.isRsiEnabled(),
                radar.getRsiPeriod(),
                radar.getRsiOversold(),
                radar.getRsiOverbought(),
                radar.getRsiWeight(),
                radar.isRequireRsiEntryConfirmation(),
                radar.isBlockBreakoutOnRsiOverbought(),
                radar.isMovingAverageEnabled(),
                radar.getMovingAverageType(),
                radar.getFastMovingAveragePeriod(),
                radar.getSlowMovingAveragePeriod(),
                radar.getMovingAverageWeight(),
                radar.isVolumeBreakoutEnabled(),
                radar.getBreakoutLookbackBars(),
                radar.getVolumeMultiplier(),
                radar.getVolumeBreakoutWeight(),
                radar.isVolumeSustainEnabled(),
                radar.isRequireBreakoutContinuation(),
                radar.isRequireBreakoutNextBarConfirmation(),
                radar.isBlockMovingAverageOnlyEntry(),
                radar.isRequirePriceAboveVwapForLong(),
                radar.isRangeMarketRequiresVwapAndVolume(),
                activeMonitorConfig.isRangeFailureExitEnabled(),
                activeMonitorConfig.getRangeFailureExitMinutes(),
                activeMonitorConfig.getRangeFailureMinR(),
                activeMonitorConfig.isRangeFailureVolumeSustainExitEnabled(),
                radar.isMarketRegimeFilterEnabled(),
                radar.getInternalAllowVwapPassPercent(),
                radar.getInternalAllowAverageReturnPercent(),
                radar.getInternalAllowVolumeSustainPercent(),
                radar.getInternalBlockVwapPassPercent(),
                radar.getInternalBlockAverageReturnPercent(),
                radar.getInternalBlockNewLowExcessCount(),
                radar.isWeakMarketStrictLongEnabled(),
                radar.getWeakMarketLongPolicy(),
                radar.getWeakOutperformBenchmarkPercent(),
                radar.getWeakOutperformIndustryPercent(),
                radar.isAtrRiskEnabled(),
                radar.isAtrChaseLimitEnabled(),
                radar.getAtrPeriod(),
                radar.getAtrStopMultiplier(),
                radar.getAtrTakeProfitMultiplier(),
                radar.getAtrChaseLimitMultiplier(),
                radar.getMaxEntryRiseFromRecentLowPercent(),
                radar.getMinimumEntryScore());
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

        List<MonitorTemplate> monitorTemplates = new ArrayList<>(loadMonitorTemplatesForDialog());
        JComboBox<String> templateBox = new JComboBox<>(monitorTemplates.stream()
                .map(MonitorTemplate::name)
                .toArray(String[]::new));
        JLabel templateDescription = new JLabel(monitorTemplates.get(0).description());
        templateDescription.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel templatePanel = new JPanel(new BorderLayout(4, 4));
        templatePanel.add(templateBox, BorderLayout.NORTH);
        templatePanel.add(templateDescription, BorderLayout.CENTER);
        JSpinner scanInterval = new JSpinner(new SpinnerNumberModel(monitorConfig.getScanIntervalSeconds(), 3, 60, 1));
        JComboBox<Timeframe> timeframeBox = new JComboBox<>(new Timeframe[]{Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1});
        timeframeBox.setSelectedItem(monitorConfig.getTimeframe());
        timeframeBox.setEnabled(false);
        timeframeBox.setToolTipText("目前自動監控僅支援當沖，實際週期請使用「當沖週期」。");
        JSpinner barCountSpinner = new JSpinner(new SpinnerNumberModel(monitorConfig.getBarCount(), 60, 500, 20));
        barCountSpinner.setEnabled(false);
        barCountSpinner.setToolTipText("目前自動監控僅支援當沖，實際 K 線數量請使用「當沖 K 線數量」。");
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
        JSpinner dailyMaxLoss = decimalSpinner(monitorConfig.getDailyMaxLoss(), -100_000.0, 0.0, 500.0);
        JSpinner dailyMaxStopLossCount = new JSpinner(new SpinnerNumberModel(monitorConfig.getDailyMaxStopLossCount(), 0, 20, 1));
        JSpinner consecutiveLossLimit = new JSpinner(new SpinnerNumberModel(monitorConfig.getConsecutiveLossLimit(), 0, 20, 1));
        JCheckBox disableTradingAfterLossLimit = new JCheckBox("觸發日損/連敗後停止今日自動開倉", monitorConfig.isDisableTradingAfterLossLimit());
        JSpinner dailyMaxAutoTrades = new JSpinner(new SpinnerNumberModel(monitorConfig.getDailyMaxAutoTrades(), 1, 50, 1));
        JSpinner entryPacingMinutes = new JSpinner(new SpinnerNumberModel(monitorConfig.getEntryPacingMinutes(), 0, 60, 1));
        JSpinner postExitCooldownMinutes = new JSpinner(new SpinnerNumberModel(monitorConfig.getPostExitCooldownMinutes(), 0, 240, 5));
        JCheckBox oneEntryPerFiveMinuteBar = new JCheckBox("同一根 5 分 K 只允許 1 筆新倉", monitorConfig.isOneEntryPerFiveMinuteBar());
        JCheckBox rangeFailureExitEnabled = new JCheckBox("RANGE 盤啟用時間/動能失效出場", monitorConfig.isRangeFailureExitEnabled());
        JSpinner rangeFailureExitMinutes = new JSpinner(new SpinnerNumberModel(monitorConfig.getRangeFailureExitMinutes(), 5, 120, 5));
        JSpinner rangeFailureMinR = decimalSpinner(monitorConfig.getRangeFailureMinR(), 0.0, 3.0, 0.1);
        JCheckBox rangeFailureVolumeSustainExitEnabled = new JCheckBox("RANGE 盤 Volume Sustain 失效出場", monitorConfig.isRangeFailureVolumeSustainExitEnabled());
        JPanel earlyBlockPanel = createTimeRangePanel(
                earlyBlockEnabled, earlyStartHour, earlyStartMinute, earlyEndHour, earlyEndMinute);
        JPanel stopLossCooldownPanel = createCheckboxSpinnerPanel(
                stopLossCooldownEnabled, stopLossCooldownMinutes, "分鐘");
        JPanel latestEntryPanel = createTimePanel(latestEntryHour, latestEntryMinute);
        JComboBox<Timeframe> dayTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M1, Timeframe.M5, Timeframe.M15});
        dayTimeframe.setSelectedItem(radarConfig.getDayTradeTimeframe());
        JComboBox<Timeframe> executionConfirmationTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M1, Timeframe.M5});
        executionConfirmationTimeframe.setSelectedItem(radarConfig.getExecutionConfirmationTimeframe());
        JCheckBox backtestCrossDayWarmupEnabled = new JCheckBox(
                "啟用 SQL 回測跨日暖機",
                radarConfig.isBacktestCrossDayWarmupEnabled());
        JSpinner backtestWarmupBars = new JSpinner(new SpinnerNumberModel(
                radarConfig.getBacktestWarmupBarCount(), 0, 1000, 20));
        JPanel backtestWarmupPanel = createCheckboxSpinnerPanel(
                backtestCrossDayWarmupEnabled, backtestWarmupBars, "根");
        JComboBox<Timeframe> shortTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1});
        shortTimeframe.setSelectedItem(radarConfig.getShortSwingTimeframe());
        shortTimeframe.setEnabled(false);
        JComboBox<Timeframe> swingTimeframe = new JComboBox<>(new Timeframe[]{Timeframe.M15, Timeframe.H1, Timeframe.D1});
        swingTimeframe.setSelectedItem(radarConfig.getSwingTradeTimeframe());
        swingTimeframe.setEnabled(false);
        JSpinner dayBars = new JSpinner(new SpinnerNumberModel(radarConfig.getDayTradeBarCount(), 60, 600, 20));
        JSpinner shortBars = new JSpinner(new SpinnerNumberModel(radarConfig.getShortSwingBarCount(), 80, 800, 20));
        shortBars.setEnabled(false);
        JSpinner swingBars = new JSpinner(new SpinnerNumberModel(radarConfig.getSwingTradeBarCount(), 100, 1000, 20));
        swingBars.setEnabled(false);

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
        JCheckBox blockMovingAverageOnlyEntry = new JCheckBox(
                "阻擋 EMA 單因子進場",
                radarConfig.isBlockMovingAverageOnlyEntry());

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
        JCheckBox marketRegimeFilterEnabled = new JCheckBox("啟用內部市場狀態過濾", radarConfig.isMarketRegimeFilterEnabled());
        JCheckBox weakMarketStrictLongEnabled = new JCheckBox("弱勢盤只允許強勢股開多", radarConfig.isWeakMarketStrictLongEnabled());
        JComboBox<WeakMarketLongPolicy> weakMarketLongPolicy = new JComboBox<>(WeakMarketLongPolicy.values());
        weakMarketLongPolicy.setSelectedItem(radarConfig.getWeakMarketLongPolicy());
        JSpinner weakOutperformBenchmark = decimalSpinner(radarConfig.getWeakOutperformBenchmarkPercent(), 0.0, 5.0, 0.05);
        JSpinner weakOutperformIndustry = decimalSpinner(radarConfig.getWeakOutperformIndustryPercent(), 0.0, 5.0, 0.05);
        JSpinner internalAllowVwapPass = decimalSpinner(radarConfig.getInternalAllowVwapPassPercent(), 0.0, 100.0, 1.0);
        JSpinner internalAllowAverageReturn = decimalSpinner(radarConfig.getInternalAllowAverageReturnPercent(), -10.0, 10.0, 0.05);
        JSpinner internalAllowVolumeSustain = decimalSpinner(radarConfig.getInternalAllowVolumeSustainPercent(), 0.0, 100.0, 1.0);
        JSpinner internalBlockVwapPass = decimalSpinner(radarConfig.getInternalBlockVwapPassPercent(), 0.0, 100.0, 1.0);
        JSpinner internalBlockAverageReturn = decimalSpinner(radarConfig.getInternalBlockAverageReturnPercent(), -10.0, 10.0, 0.05);
        JSpinner internalBlockNewLowExcess = new JSpinner(new SpinnerNumberModel(radarConfig.getInternalBlockNewLowExcessCount(), 0, 200, 1));
        JCheckBox rangeRequiresVwapVolume = new JCheckBox("震盪盤要求 VWAP 與量能延續", radarConfig.isRangeMarketRequiresVwapAndVolume());
        JCheckBox volumeSustainEnabled = new JCheckBox("啟用 Volume Sustain Filter", radarConfig.isVolumeSustainEnabled());
        JCheckBox atrRiskEnabled = new JCheckBox("啟用 ATR 動態停損停利", radarConfig.isAtrRiskEnabled());
        JCheckBox atrChaseLimitEnabled = new JCheckBox("啟用 ATR 追高限制", radarConfig.isAtrChaseLimitEnabled());
        atrRiskEnabled.setText("啟用 ATR 停損/停利");
        JSpinner atrPeriod = new JSpinner(new SpinnerNumberModel(radarConfig.getAtrPeriod(), 3, 60, 1));
        JSpinner atrStopMultiplier = decimalSpinner(radarConfig.getAtrStopMultiplier(), 0.1, 5.0, 0.1);
        JSpinner atrTakeProfitMultiplier = decimalSpinner(radarConfig.getAtrTakeProfitMultiplier(), 0.1, 8.0, 0.1);
        JSpinner atrChaseLimitMultiplier = decimalSpinner(radarConfig.getAtrChaseLimitMultiplier(), 0.1, 8.0, 0.1);

        templateBox.addActionListener(e -> {
            int selectedIndex = templateBox.getSelectedIndex();
            if (selectedIndex < 0 || selectedIndex >= monitorTemplates.size()) {
                return;
            }
            MonitorTemplate selectedTemplate = monitorTemplates.get(selectedIndex);
            templateDescription.setText(selectedTemplate.description());
            if (selectedIndex == 0) {
                return;
            }
            monitorStrategyName = selectedTemplate.name();
            SignalMonitorConfig configTemplate = copyMonitorConfig(selectedTemplate.monitorConfig());
            DecisionConfig decisionTemplate = copyDecisionConfig(selectedTemplate.decisionConfig());
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
            dailyMaxLoss.setValue(configTemplate.getDailyMaxLoss());
            dailyMaxStopLossCount.setValue(configTemplate.getDailyMaxStopLossCount());
            consecutiveLossLimit.setValue(configTemplate.getConsecutiveLossLimit());
            disableTradingAfterLossLimit.setSelected(configTemplate.isDisableTradingAfterLossLimit());
            dailyMaxAutoTrades.setValue(configTemplate.getDailyMaxAutoTrades());
            entryPacingMinutes.setValue(configTemplate.getEntryPacingMinutes());
            postExitCooldownMinutes.setValue(configTemplate.getPostExitCooldownMinutes());
            oneEntryPerFiveMinuteBar.setSelected(configTemplate.isOneEntryPerFiveMinuteBar());
            rangeFailureExitEnabled.setSelected(configTemplate.isRangeFailureExitEnabled());
            rangeFailureExitMinutes.setValue(configTemplate.getRangeFailureExitMinutes());
            rangeFailureMinR.setValue(configTemplate.getRangeFailureMinR());
            rangeFailureVolumeSustainExitEnabled.setSelected(configTemplate.isRangeFailureVolumeSustainExitEnabled());
            RadarStrategyConfig radarTemplate = configTemplate.getRadarStrategyConfig();
            dayTimeframe.setSelectedItem(radarTemplate.getDayTradeTimeframe());
            executionConfirmationTimeframe.setSelectedItem(radarTemplate.getExecutionConfirmationTimeframe());
            backtestCrossDayWarmupEnabled.setSelected(radarTemplate.isBacktestCrossDayWarmupEnabled());
            backtestWarmupBars.setValue(radarTemplate.getBacktestWarmupBarCount());
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
            blockMovingAverageOnlyEntry.setSelected(radarTemplate.isBlockMovingAverageOnlyEntry());
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
            marketRegimeFilterEnabled.setSelected(radarTemplate.isMarketRegimeFilterEnabled());
            weakMarketStrictLongEnabled.setSelected(radarTemplate.isWeakMarketStrictLongEnabled());
            weakMarketLongPolicy.setSelectedItem(radarTemplate.getWeakMarketLongPolicy());
            weakOutperformBenchmark.setValue(radarTemplate.getWeakOutperformBenchmarkPercent());
            weakOutperformIndustry.setValue(radarTemplate.getWeakOutperformIndustryPercent());
            internalAllowVwapPass.setValue(radarTemplate.getInternalAllowVwapPassPercent());
            internalAllowAverageReturn.setValue(radarTemplate.getInternalAllowAverageReturnPercent());
            internalAllowVolumeSustain.setValue(radarTemplate.getInternalAllowVolumeSustainPercent());
            internalBlockVwapPass.setValue(radarTemplate.getInternalBlockVwapPassPercent());
            internalBlockAverageReturn.setValue(radarTemplate.getInternalBlockAverageReturnPercent());
            internalBlockNewLowExcess.setValue(radarTemplate.getInternalBlockNewLowExcessCount());
            rangeRequiresVwapVolume.setSelected(radarTemplate.isRangeMarketRequiresVwapAndVolume());
            volumeSustainEnabled.setSelected(radarTemplate.isVolumeSustainEnabled());
            atrRiskEnabled.setSelected(radarTemplate.isAtrRiskEnabled());
            atrChaseLimitEnabled.setSelected(radarTemplate.isAtrChaseLimitEnabled());
            atrPeriod.setValue(radarTemplate.getAtrPeriod());
            atrStopMultiplier.setValue(radarTemplate.getAtrStopMultiplier());
            atrTakeProfitMultiplier.setValue(radarTemplate.getAtrTakeProfitMultiplier());
            atrChaseLimitMultiplier.setValue(radarTemplate.getAtrChaseLimitMultiplier());
            longThreshold.setValue(decisionTemplate.getVotingConfig().getLongEntryThreshold());
            exitThreshold.setValue(decisionTemplate.getVotingConfig().getExitThreshold());
            minRiskReward.setValue(decisionTemplate.getRiskConfig().getMinRiskRewardRatio());
            minVolatility.setValue(decisionTemplate.getRiskConfig().getMinVolatilityPercent());
            maxVolatility.setValue(decisionTemplate.getRiskConfig().getMaxVolatilityPercent());
            minStrategies.setValue(decisionTemplate.getVotingConfig().getMinVotingStrategies());
            maxPositions.setValue(decisionTemplate.getRiskConfig().getMaxConcurrentPositions());
            riskEnabled.setSelected(decisionTemplate.isRiskManagementEnabled());
        });

        int row = 0;
        addSettingsSection(panel, gbc, row++, "模板與掃描", "選擇監控模板，設定雷達多久掃描一次。");
        addSettingsRow(panel, gbc, row++, "預設模板", templatePanel);
        addSettingsRow(panel, gbc, row++, "掃描間隔（秒）", scanInterval);

        addSettingsSection(panel, gbc, row++, "當沖週期", "目前自動監控只支援當沖；5 分 K 做主判斷，1 分 K 做執行確認。");
        addSettingsRow(panel, gbc, row++, "當沖週期", dayTimeframe);
        addSettingsRow(panel, gbc, row++, "執行確認週期", executionConfirmationTimeframe);
        addSettingsRow(panel, gbc, row++, "當沖 K 線數量", dayBars);
        addSettingsRow(panel, gbc, row++, "回測跨日暖機", backtestWarmupPanel);

        addSettingsSection(panel, gbc, row++, "進場評分", "控制雷達分數、投票門檻與 RSI / 均線基礎權重。");
        addSettingsRow(panel, gbc, row++, "同股訊號冷卻（分）", signalInterval);
        addSettingsRow(panel, gbc, row++, "做多門檻", longThreshold);
        addSettingsRow(panel, gbc, row++, "出場門檻", exitThreshold);
        addSettingsRow(panel, gbc, row++, "雷達最低進場分數", minimumRadarEntryScore);
        addSettingsRow(panel, gbc, row++, "最少策略數", minStrategies);
        addSettingsRow(panel, gbc, row++, "", rsiEnabled);
        addSettingsRow(panel, gbc, row++, "RSI 週期", rsiPeriod);
        addSettingsRow(panel, gbc, row++, "RSI 超賣", rsiOversold);
        addSettingsRow(panel, gbc, row++, "RSI 超買", rsiOverbought);
        addSettingsRow(panel, gbc, row++, "RSI 權重", rsiWeight);
        addSettingsRow(panel, gbc, row++, "", requireRsiEntryConfirmation);
        addSettingsRow(panel, gbc, row++, "", maEnabled);
        addSettingsRow(panel, gbc, row++, "均線類型", maType);
        addSettingsRow(panel, gbc, row++, "均線快線", fastMa);
        addSettingsRow(panel, gbc, row++, "均線慢線", slowMa);
        addSettingsRow(panel, gbc, row++, "均線權重", maWeight);
        addSettingsRow(panel, gbc, row++, "", blockMovingAverageOnlyEntry);

        addSettingsSection(panel, gbc, row++, "VWAP / 量能 / 追價", "控制做多結構、放量突破、Volume Sustain 與追高阻擋。");
        addSettingsRow(panel, gbc, row++, "", volumeEnabled);
        addSettingsRow(panel, gbc, row++, "突破回看 K 數", breakoutLookback);
        addSettingsRow(panel, gbc, row++, "成交量倍率", volumeMultiplier);
        addSettingsRow(panel, gbc, row++, "放量權重", volumeWeight);
        addSettingsRow(panel, gbc, row++, "", blockBreakoutOnRsiOverbought);
        addSettingsRow(panel, gbc, row++, "", requireBreakoutContinuation);
        addSettingsRow(panel, gbc, row++, "", requirePriceAboveVwap);
        addSettingsRow(panel, gbc, row++, "", requireBreakoutNextBarConfirmation);
        addSettingsRow(panel, gbc, row++, "", volumeSustainEnabled);
        addSettingsRow(panel, gbc, row++, "追價限制（近低漲幅）", maxEntryRiseFromRecentLow);

        addSettingsSection(panel, gbc, row++, "內部市場 / 弱勢盤", "用觀察清單 SQL 分 K 取代即時大盤與即時產業，判斷 ALLOW / LIMIT / BLOCK。");
        addSettingsRow(panel, gbc, row++, "", marketRegimeFilterEnabled);
        addSettingsRow(panel, gbc, row++, "", weakMarketStrictLongEnabled);
        addSettingsRow(panel, gbc, row++, "弱勢盤策略", weakMarketLongPolicy);
        addSettingsRow(panel, gbc, row++, "ALLOW VWAP通過比例%", internalAllowVwapPass);
        addSettingsRow(panel, gbc, row++, "ALLOW 最低平均漲跌%", internalAllowAverageReturn);
        addSettingsRow(panel, gbc, row++, "ALLOW Volume Sustain%", internalAllowVolumeSustain);
        addSettingsRow(panel, gbc, row++, "BLOCK VWAP通過低於%", internalBlockVwapPass);
        addSettingsRow(panel, gbc, row++, "BLOCK 平均漲跌低於%", internalBlockAverageReturn);
        addSettingsRow(panel, gbc, row++, "BLOCK 創低多於創高檔數", internalBlockNewLowExcess);
        addSettingsRow(panel, gbc, row++, "弱勢盤強於內部基準%", weakOutperformBenchmark);
        addSettingsRow(panel, gbc, row++, "弱勢盤強於觀察清單群體%", weakOutperformIndustry);
        addSettingsRow(panel, gbc, row++, "", rangeRequiresVwapVolume);

        addSettingsSection(panel, gbc, row++, "ATR 動態停損停利", "用波動決定停損、停利與追高限制。");
        addSettingsRow(panel, gbc, row++, "", atrRiskEnabled);
        addSettingsRow(panel, gbc, row++, "", atrChaseLimitEnabled);
        addSettingsRow(panel, gbc, row++, "ATR 週期", atrPeriod);
        addSettingsRow(panel, gbc, row++, "ATR 停損倍數", atrStopMultiplier);
        addSettingsRow(panel, gbc, row++, "ATR 停利倍數", atrTakeProfitMultiplier);
        addSettingsRow(panel, gbc, row++, "ATR 追高限制倍數", atrChaseLimitMultiplier);

        addSettingsSection(panel, gbc, row++, "風控與交易節奏", "控制單日風險、持倉上限、冷卻與尾盤禁開倉。");
        addSettingsRow(panel, gbc, row++, "最低風報比", minRiskReward);
        addSettingsRow(panel, gbc, row++, "最低波動", minVolatility);
        addSettingsRow(panel, gbc, row++, "最高波動", maxVolatility);
        addSettingsRow(panel, gbc, row++, "最大同時持倉", maxPositions);
        addSettingsRow(panel, gbc, row++, "", riskEnabled);
        addSettingsRow(panel, gbc, row++, "早盤禁開倉", earlyBlockPanel);
        addSettingsRow(panel, gbc, row++, "停損冷卻", stopLossCooldownPanel);
        addSettingsRow(panel, gbc, row++, "尾盤禁止新倉時間", latestEntryPanel);
        addSettingsRow(panel, gbc, row++, "單日最大虧損", dailyMaxLoss);
        addSettingsRow(panel, gbc, row++, "單日停損次數上限", dailyMaxStopLossCount);
        addSettingsRow(panel, gbc, row++, "連續虧損上限", consecutiveLossLimit);
        addSettingsRow(panel, gbc, row++, "", disableTradingAfterLossLimit);
        addSettingsRow(panel, gbc, row++, "每日最多交易", dailyMaxAutoTrades);
        addSettingsRow(panel, gbc, row++, "開單間隔分鐘", entryPacingMinutes);
        addSettingsRow(panel, gbc, row++, "平倉後冷卻分鐘", postExitCooldownMinutes);
        addSettingsRow(panel, gbc, row++, "", oneEntryPerFiveMinuteBar);

        addSettingsSection(panel, gbc, row++, "RANGE 盤出場", "震盪盤若時間或量能沒有延續，提前離場。");
        addSettingsRow(panel, gbc, row++, "", rangeFailureExitEnabled);
        addSettingsRow(panel, gbc, row++, "RANGE 失效等待分鐘", rangeFailureExitMinutes);
        addSettingsRow(panel, gbc, row++, "RANGE 最低達成 R", rangeFailureMinR);
        addSettingsRow(panel, gbc, row++, "", rangeFailureVolumeSustainExitEnabled);

        addSettingsSection(panel, gbc, row++, "目前停用欄位", "短線 / 波段自動監控尚未啟用，保留欄位但不參與當沖雷達。");
        addSettingsRow(panel, gbc, row++, "主掃描週期", createDisabledFieldPanel(timeframeBox, "目前自動監控僅支援當沖，實際週期請使用「當沖週期」。"));
        addSettingsRow(panel, gbc, row++, "K 線數量", createDisabledFieldPanel(barCountSpinner, "目前自動監控僅支援當沖，實際 K 線數量請使用「當沖 K 線數量」。"));
        addSettingsRow(panel, gbc, row++, "短線週期", createDisabledFieldPanel(shortTimeframe, "目前自動監控僅支援當沖"));
        addSettingsRow(panel, gbc, row++, "波段週期", createDisabledFieldPanel(swingTimeframe, "目前自動監控僅支援當沖"));
        addSettingsRow(panel, gbc, row++, "短線 K 線數量", createDisabledFieldPanel(shortBars, "目前自動監控僅支援當沖"));
        addSettingsRow(panel, gbc, row, "波段 K 線數量", createDisabledFieldPanel(swingBars, "目前自動監控僅支援當沖"));

        java.util.function.BiFunction<String, String, MonitorTemplate> buildTemplateFromForm = (name, description) -> {
            SignalMonitorConfig savedMonitor = copyMonitorConfig(monitorConfig);
            savedMonitor.setScanIntervalSeconds(((Number) scanInterval.getValue()).intValue());
            savedMonitor.setTimeframe((Timeframe) timeframeBox.getSelectedItem());
            savedMonitor.setBarCount(((Number) barCountSpinner.getValue()).intValue());
            savedMonitor.setMinSignalIntervalMinutes(((Number) signalInterval.getValue()).intValue());
            savedMonitor.setBatchScanMode(true);
            savedMonitor.setEarlyEntryBlockEnabled(earlyBlockEnabled.isSelected());
            savedMonitor.setEarlyEntryBlockStart(readTime(earlyStartHour, earlyStartMinute));
            savedMonitor.setEarlyEntryBlockEnd(readTime(earlyEndHour, earlyEndMinute));
            savedMonitor.setStopLossCooldownEnabled(stopLossCooldownEnabled.isSelected());
            savedMonitor.setStopLossCooldownMinutes(((Number) stopLossCooldownMinutes.getValue()).intValue());
            savedMonitor.setLatestAutoEntryTime(readTime(latestEntryHour, latestEntryMinute));
            savedMonitor.setDailyMaxLoss(((Number) dailyMaxLoss.getValue()).doubleValue());
            savedMonitor.setDailyMaxStopLossCount(((Number) dailyMaxStopLossCount.getValue()).intValue());
            savedMonitor.setConsecutiveLossLimit(((Number) consecutiveLossLimit.getValue()).intValue());
            savedMonitor.setDisableTradingAfterLossLimit(disableTradingAfterLossLimit.isSelected());
            savedMonitor.setDailyMaxAutoTrades(((Number) dailyMaxAutoTrades.getValue()).intValue());
            savedMonitor.setEntryPacingMinutes(((Number) entryPacingMinutes.getValue()).intValue());
            savedMonitor.setPostExitCooldownMinutes(((Number) postExitCooldownMinutes.getValue()).intValue());
            savedMonitor.setOneEntryPerFiveMinuteBar(oneEntryPerFiveMinuteBar.isSelected());
            savedMonitor.setRangeFailureExitEnabled(rangeFailureExitEnabled.isSelected());
            savedMonitor.setRangeFailureExitMinutes(((Number) rangeFailureExitMinutes.getValue()).intValue());
            savedMonitor.setRangeFailureMinR(((Number) rangeFailureMinR.getValue()).doubleValue());
            savedMonitor.setRangeFailureVolumeSustainExitEnabled(rangeFailureVolumeSustainExitEnabled.isSelected());

            RadarStrategyConfig savedRadar = radarConfig.copy();
            savedRadar.setDayTradeTimeframe((Timeframe) dayTimeframe.getSelectedItem());
            savedRadar.setExecutionConfirmationTimeframe((Timeframe) executionConfirmationTimeframe.getSelectedItem());
            savedRadar.setBacktestCrossDayWarmupEnabled(backtestCrossDayWarmupEnabled.isSelected());
            savedRadar.setBacktestWarmupBarCount(((Number) backtestWarmupBars.getValue()).intValue());
            savedRadar.setShortSwingTimeframe((Timeframe) shortTimeframe.getSelectedItem());
            savedRadar.setSwingTradeTimeframe((Timeframe) swingTimeframe.getSelectedItem());
            savedRadar.setDayTradeBarCount(((Number) dayBars.getValue()).intValue());
            savedRadar.setShortSwingBarCount(((Number) shortBars.getValue()).intValue());
            savedRadar.setSwingTradeBarCount(((Number) swingBars.getValue()).intValue());
            savedRadar.setRsiEnabled(rsiEnabled.isSelected());
            savedRadar.setRsiPeriod(((Number) rsiPeriod.getValue()).intValue());
            savedRadar.setRsiOversold(((Number) rsiOversold.getValue()).doubleValue());
            savedRadar.setRsiOverbought(((Number) rsiOverbought.getValue()).doubleValue());
            savedRadar.setRsiWeight(((Number) rsiWeight.getValue()).doubleValue());
            savedRadar.setRequireRsiEntryConfirmation(requireRsiEntryConfirmation.isSelected());
            savedRadar.setMovingAverageEnabled(maEnabled.isSelected());
            savedRadar.setMovingAverageType((RadarStrategyConfig.MovingAverageType) maType.getSelectedItem());
            savedRadar.setFastMovingAveragePeriod(((Number) fastMa.getValue()).intValue());
            savedRadar.setSlowMovingAveragePeriod(((Number) slowMa.getValue()).intValue());
            savedRadar.setMovingAverageWeight(((Number) maWeight.getValue()).doubleValue());
            savedRadar.setBlockMovingAverageOnlyEntry(blockMovingAverageOnlyEntry.isSelected());
            savedRadar.setVolumeBreakoutEnabled(volumeEnabled.isSelected());
            savedRadar.setBreakoutLookbackBars(((Number) breakoutLookback.getValue()).intValue());
            savedRadar.setVolumeMultiplier(((Number) volumeMultiplier.getValue()).doubleValue());
            savedRadar.setVolumeBreakoutWeight(((Number) volumeWeight.getValue()).doubleValue());
            savedRadar.setMinimumEntryScore(((Number) minimumRadarEntryScore.getValue()).doubleValue());
            savedRadar.setBlockBreakoutOnRsiOverbought(blockBreakoutOnRsiOverbought.isSelected());
            savedRadar.setRequireBreakoutContinuation(requireBreakoutContinuation.isSelected());
            savedRadar.setRequirePriceAboveVwapForLong(requirePriceAboveVwap.isSelected());
            savedRadar.setRequireBreakoutNextBarConfirmation(requireBreakoutNextBarConfirmation.isSelected());
            savedRadar.setMaxEntryRiseFromRecentLowPercent(((Number) maxEntryRiseFromRecentLow.getValue()).doubleValue());
            savedRadar.setMarketRegimeFilterEnabled(marketRegimeFilterEnabled.isSelected());
            savedRadar.setWeakMarketStrictLongEnabled(weakMarketStrictLongEnabled.isSelected());
            savedRadar.setWeakMarketLongPolicy((WeakMarketLongPolicy) weakMarketLongPolicy.getSelectedItem());
            savedRadar.setWeakOutperformBenchmarkPercent(((Number) weakOutperformBenchmark.getValue()).doubleValue());
            savedRadar.setWeakOutperformIndustryPercent(((Number) weakOutperformIndustry.getValue()).doubleValue());
            savedRadar.setInternalAllowVwapPassPercent(((Number) internalAllowVwapPass.getValue()).doubleValue());
            savedRadar.setInternalAllowAverageReturnPercent(((Number) internalAllowAverageReturn.getValue()).doubleValue());
            savedRadar.setInternalAllowVolumeSustainPercent(((Number) internalAllowVolumeSustain.getValue()).doubleValue());
            savedRadar.setInternalBlockVwapPassPercent(((Number) internalBlockVwapPass.getValue()).doubleValue());
            savedRadar.setInternalBlockAverageReturnPercent(((Number) internalBlockAverageReturn.getValue()).doubleValue());
            savedRadar.setInternalBlockNewLowExcessCount(((Number) internalBlockNewLowExcess.getValue()).intValue());
            savedRadar.setRangeMarketRequiresVwapAndVolume(rangeRequiresVwapVolume.isSelected());
            savedRadar.setVolumeSustainEnabled(volumeSustainEnabled.isSelected());
            savedRadar.setAtrRiskEnabled(atrRiskEnabled.isSelected());
            savedRadar.setAtrChaseLimitEnabled(atrChaseLimitEnabled.isSelected());
            savedRadar.setAtrPeriod(((Number) atrPeriod.getValue()).intValue());
            savedRadar.setAtrStopMultiplier(((Number) atrStopMultiplier.getValue()).doubleValue());
            savedRadar.setAtrTakeProfitMultiplier(((Number) atrTakeProfitMultiplier.getValue()).doubleValue());
            savedRadar.setAtrChaseLimitMultiplier(((Number) atrChaseLimitMultiplier.getValue()).doubleValue());
            savedMonitor.setRadarStrategyConfig(savedRadar);

            DecisionConfig savedDecision = copyDecisionConfig(monitorDecisionConfig);
            savedDecision.getVotingConfig().setLongEntryThreshold(((Number) longThreshold.getValue()).doubleValue());
            savedDecision.getVotingConfig().setExitThreshold(((Number) exitThreshold.getValue()).doubleValue());
            savedDecision.getVotingConfig().setMinVotingStrategies(((Number) minStrategies.getValue()).intValue());
            savedDecision.setRiskManagementEnabled(riskEnabled.isSelected());
            savedDecision.getRiskConfig().setMaxConcurrentPositions(((Number) maxPositions.getValue()).intValue());
            savedDecision.getRiskConfig().setMinRiskRewardRatio(((Number) minRiskReward.getValue()).doubleValue());
            savedDecision.getRiskConfig().setMinVolatilityPercent(((Number) minVolatility.getValue()).doubleValue());
            savedDecision.getRiskConfig().setMaxVolatilityPercent(((Number) maxVolatility.getValue()).doubleValue());
            savedDecision.getRiskConfig().setAllowShortSelling(false);
            return new MonitorTemplate(name, description, savedMonitor, savedDecision, true);
        };

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("取消");
        JButton saveTemplateButton = new JButton("儲存為模板");
        JButton deleteTemplateButton = new JButton("刪除模板");
        JButton applyButton = new JButton("套用");
        cancelButton.addActionListener(e -> dialog.dispose());
        saveTemplateButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(dialog, "請輸入新模板名稱：", "儲存監控模板", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            String trimmedName = name.trim();
            boolean builtInName = monitorTemplates.stream()
                    .anyMatch(template -> !template.userDefined() && template.name().equals(trimmedName));
            if (builtInName) {
                JOptionPane.showMessageDialog(dialog, "內建模板名稱不可覆蓋，請換一個名稱。", "儲存監控模板", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                MonitorTemplate savedTemplate = buildTemplateFromForm.apply(
                        trimmedName,
                        "使用者自訂模板：" + trimmedName);
                saveUserMonitorTemplate(savedTemplate);
                monitorTemplates.removeIf(template -> template.userDefined() && template.name().equals(trimmedName));
                monitorTemplates.add(savedTemplate);
                for (int i = templateBox.getItemCount() - 1; i >= 0; i--) {
                    if (trimmedName.equals(templateBox.getItemAt(i))) {
                        templateBox.removeItemAt(i);
                    }
                }
                templateBox.addItem(trimmedName);
                templateBox.setSelectedItem(trimmedName);
                templateDescription.setText(savedTemplate.description());
                statusBar.setText("已儲存監控模板：" + trimmedName);
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "儲存監控模板", JOptionPane.ERROR_MESSAGE);
            }
        });
        deleteTemplateButton.addActionListener(e -> {
            int selectedIndex = templateBox.getSelectedIndex();
            if (selectedIndex <= 0 || selectedIndex >= monitorTemplates.size()) {
                JOptionPane.showMessageDialog(dialog, "請先選擇一個使用者自訂模板。", "刪除監控模板", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            MonitorTemplate selectedTemplate = monitorTemplates.get(selectedIndex);
            if (!selectedTemplate.userDefined()) {
                JOptionPane.showMessageDialog(dialog, "內建測試模板不可刪除；如需修改，請另存為自訂模板。", "刪除監控模板", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    dialog,
                    "確定要刪除監控模板「" + selectedTemplate.name() + "」？",
                    "刪除監控模板",
                    JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                if (deleteUserMonitorTemplate(selectedTemplate.name())) {
                    monitorTemplates.remove(selectedIndex);
                    templateBox.removeItemAt(selectedIndex);
                    templateBox.setSelectedIndex(0);
                    templateDescription.setText(monitorTemplates.get(0).description());
                    statusBar.setText("已刪除監控模板：" + selectedTemplate.name());
                }
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "刪除監控模板", JOptionPane.ERROR_MESSAGE);
            }
        });
        applyButton.addActionListener(e -> {
            monitorConfig.setScanIntervalSeconds(((Number) scanInterval.getValue()).intValue());
            monitorConfig.setTimeframe((Timeframe) timeframeBox.getSelectedItem());
            monitorConfig.setBarCount(((Number) barCountSpinner.getValue()).intValue());
            monitorConfig.setMinSignalIntervalMinutes(((Number) signalInterval.getValue()).intValue());
            monitorConfig.setBatchScanMode(true);
            int selectedTemplateIndex = templateBox.getSelectedIndex();
            monitorStrategyName = selectedTemplateIndex >= 0 && selectedTemplateIndex < monitorTemplates.size()
                    ? monitorTemplates.get(selectedTemplateIndex).name()
                    : "目前設定";
            monitorConfig.setEarlyEntryBlockEnabled(earlyBlockEnabled.isSelected());
            monitorConfig.setEarlyEntryBlockStart(readTime(earlyStartHour, earlyStartMinute));
            monitorConfig.setEarlyEntryBlockEnd(readTime(earlyEndHour, earlyEndMinute));
            monitorConfig.setStopLossCooldownEnabled(stopLossCooldownEnabled.isSelected());
            monitorConfig.setStopLossCooldownMinutes(((Number) stopLossCooldownMinutes.getValue()).intValue());
            monitorConfig.setLatestAutoEntryTime(readTime(latestEntryHour, latestEntryMinute));
            monitorConfig.setDailyMaxLoss(((Number) dailyMaxLoss.getValue()).doubleValue());
            monitorConfig.setDailyMaxStopLossCount(((Number) dailyMaxStopLossCount.getValue()).intValue());
            monitorConfig.setConsecutiveLossLimit(((Number) consecutiveLossLimit.getValue()).intValue());
            monitorConfig.setDisableTradingAfterLossLimit(disableTradingAfterLossLimit.isSelected());
            monitorConfig.setDailyMaxAutoTrades(((Number) dailyMaxAutoTrades.getValue()).intValue());
            monitorConfig.setEntryPacingMinutes(((Number) entryPacingMinutes.getValue()).intValue());
            monitorConfig.setPostExitCooldownMinutes(((Number) postExitCooldownMinutes.getValue()).intValue());
            monitorConfig.setOneEntryPerFiveMinuteBar(oneEntryPerFiveMinuteBar.isSelected());
            monitorConfig.setRangeFailureExitEnabled(rangeFailureExitEnabled.isSelected());
            monitorConfig.setRangeFailureExitMinutes(((Number) rangeFailureExitMinutes.getValue()).intValue());
            monitorConfig.setRangeFailureMinR(((Number) rangeFailureMinR.getValue()).doubleValue());
            monitorConfig.setRangeFailureVolumeSustainExitEnabled(rangeFailureVolumeSustainExitEnabled.isSelected());
            RadarStrategyConfig updatedRadarConfig = radarConfig.copy();
            updatedRadarConfig.setDayTradeTimeframe((Timeframe) dayTimeframe.getSelectedItem());
            updatedRadarConfig.setExecutionConfirmationTimeframe((Timeframe) executionConfirmationTimeframe.getSelectedItem());
            updatedRadarConfig.setBacktestCrossDayWarmupEnabled(backtestCrossDayWarmupEnabled.isSelected());
            updatedRadarConfig.setBacktestWarmupBarCount(((Number) backtestWarmupBars.getValue()).intValue());
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
            updatedRadarConfig.setBlockMovingAverageOnlyEntry(blockMovingAverageOnlyEntry.isSelected());
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
            updatedRadarConfig.setMarketRegimeFilterEnabled(marketRegimeFilterEnabled.isSelected());
            updatedRadarConfig.setWeakMarketStrictLongEnabled(weakMarketStrictLongEnabled.isSelected());
            updatedRadarConfig.setWeakMarketLongPolicy((WeakMarketLongPolicy) weakMarketLongPolicy.getSelectedItem());
            updatedRadarConfig.setWeakOutperformBenchmarkPercent(((Number) weakOutperformBenchmark.getValue()).doubleValue());
            updatedRadarConfig.setWeakOutperformIndustryPercent(((Number) weakOutperformIndustry.getValue()).doubleValue());
            updatedRadarConfig.setInternalAllowVwapPassPercent(((Number) internalAllowVwapPass.getValue()).doubleValue());
            updatedRadarConfig.setInternalAllowAverageReturnPercent(((Number) internalAllowAverageReturn.getValue()).doubleValue());
            updatedRadarConfig.setInternalAllowVolumeSustainPercent(((Number) internalAllowVolumeSustain.getValue()).doubleValue());
            updatedRadarConfig.setInternalBlockVwapPassPercent(((Number) internalBlockVwapPass.getValue()).doubleValue());
            updatedRadarConfig.setInternalBlockAverageReturnPercent(((Number) internalBlockAverageReturn.getValue()).doubleValue());
            updatedRadarConfig.setInternalBlockNewLowExcessCount(((Number) internalBlockNewLowExcess.getValue()).intValue());
            updatedRadarConfig.setRangeMarketRequiresVwapAndVolume(rangeRequiresVwapVolume.isSelected());
            updatedRadarConfig.setVolumeSustainEnabled(volumeSustainEnabled.isSelected());
            updatedRadarConfig.setAtrRiskEnabled(atrRiskEnabled.isSelected());
            updatedRadarConfig.setAtrChaseLimitEnabled(atrChaseLimitEnabled.isSelected());
            updatedRadarConfig.setAtrPeriod(((Number) atrPeriod.getValue()).intValue());
            updatedRadarConfig.setAtrStopMultiplier(((Number) atrStopMultiplier.getValue()).doubleValue());
            updatedRadarConfig.setAtrTakeProfitMultiplier(((Number) atrTakeProfitMultiplier.getValue()).doubleValue());
            updatedRadarConfig.setAtrChaseLimitMultiplier(((Number) atrChaseLimitMultiplier.getValue()).doubleValue());
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
        buttons.add(saveTemplateButton);
        buttons.add(deleteTemplateButton);
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

    private JPanel createDisabledFieldPanel(JComponent field, String note) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        field.setEnabled(false);
        field.setToolTipText(note);
        JLabel noteLabel = new JLabel(note);
        noteLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(field, BorderLayout.CENTER);
        panel.add(noteLabel, BorderLayout.EAST);
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
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        JLabel labelComponent = new JLabel(label);
        String tooltip = monitorSettingTooltip(label, component);
        if (tooltip != null && !tooltip.isBlank()) {
            labelComponent.setToolTipText(tooltip);
            setTooltipRecursively(component, tooltip);
        }
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.65;
        panel.add(component, gbc);
    }

    private void addSettingsSection(JPanel panel, GridBagConstraints gbc, int row, String title, String description) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel sectionLabel = new JLabel(title + "  -  " + description);
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD));
        sectionLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(row == 0 ? 0 : 1, 0, 0, 0,
                        UIManager.getColor("Separator.foreground") != null
                                ? UIManager.getColor("Separator.foreground")
                                : Color.GRAY),
                BorderFactory.createEmptyBorder(row == 0 ? 0 : 10, 0, 4, 0)));
        String tooltip = htmlTooltip(title + "\n" + description);
        sectionLabel.setToolTipText(tooltip);
        panel.add(sectionLabel, gbc);
        gbc.gridwidth = 1;
    }

    private String monitorSettingTooltip(String label, JComponent component) {
        String key = label != null ? label.trim() : "";
        if (key.isEmpty() && component instanceof JCheckBox checkBox) {
            key = checkBox.getText() != null ? checkBox.getText().trim() : "";
        }
        return switch (key) {
            case "預設模板" -> htmlTooltip("""
                    選擇一組已保存的監控配置。選擇模板會把下方欄位改成該模板的值。
                    例：選「B組放寬 EMA8/21」後，再按套用，雷達會使用該組 RSI、VWAP、ATR 與風控門檻。""");
            case "掃描間隔（秒）" -> htmlTooltip("""
                    自動監控每隔幾秒掃描一次觀察清單。數值越小越即時，但 UI 與 SQL 負載較高。
                    例：10 代表每 10 秒掃描一次。""");
            case "主掃描週期" -> htmlTooltip("""
                    舊版通用掃描週期，目前自動監控只支援當沖，實際採用「當沖週期」。
                    例：此欄停用時不用調整，請改調當沖週期。""");
            case "K 線數量" -> htmlTooltip("""
                    舊版通用 K 線數量，目前自動監控只支援當沖，實際採用「當沖 K 線數量」。
                    例：此欄停用時不用調整。""");
            case "同股訊號冷卻（分）" -> htmlTooltip("""
                    同一檔股票兩次訊號之間至少間隔幾分鐘，避免同股連續觸發。
                    例：10 代表同一股票 10 分鐘內不重複產生新訊號。""");
            case "做多門檻" -> htmlTooltip("""
                    投票決策的做多門檻，採 0~1 小數。分數越高越嚴格。
                    例：0.35 代表多方投票分數達 0.35 才可能進入 OPEN_LONG。""");
            case "出場門檻" -> htmlTooltip("""
                    投票決策的出場門檻，採 0~1 小數。分數越低越容易出場。
                    例：0.35 代表出場訊號分數達 0.35 時可能觸發 EXIT。""");
            case "最低風報比" -> htmlTooltip("""
                    進場前要求預期報酬/風險至少達到此倍數。
                    例：1.5 代表預期可賺 1.5R 以上才允許開倉。""");
            case "最低波動" -> htmlTooltip("""
                    過濾波動太小的股票，避免價差不足支付成本。
                    例：0.00 代表不使用最低波動限制；0.01 約代表至少 1% 波動。""");
            case "最高波動" -> htmlTooltip("""
                    過濾波動過大的股票，避免異常急拉急殺。
                    例：1.00 代表上限很寬；0.08 約代表超過 8% 波動就擋。""");
            case "最少策略數" -> htmlTooltip("""
                    至少需要幾個策略參與投票，避免單一指標決定開倉。
                    例：2 代表至少 RSI、均線、放量等其中兩類有有效輸出。""");
            case "最大同時持倉" -> htmlTooltip("""
                    自動監控同時最多持有幾檔股票。
                    例：3 代表已有 3 檔 auto-managed 持倉時，不再開新倉。""");
            case "啟用風控" -> htmlTooltip("""
                    是否啟用 RiskManager 風控檢查，包含持倉上限、風報比、波動限制等。
                    例：建議保持啟用，避免只有策略分數通過就直接開倉。""");
            case "早盤禁開倉" -> htmlTooltip("""
                    指定早盤時間只收資料不自動開倉，用來避開開盤跳動與資料不足。
                    例：09:00~09:15 代表 09:15 前不開新倉。""");
            case "停損冷卻" -> htmlTooltip("""
                    同一檔股票停損後，冷卻期間不得由自動監控重進。
                    例：60 分鐘代表 2330.TW 停損後，一小時內不再買回。""");
            case "當沖週期" -> htmlTooltip("""
                    自動監控主要判斷用的 K 線週期。
                    例：5分代表以 M5 判斷 VWAP、EMA、突破、量能延續。""");
            case "執行確認週期" -> htmlTooltip("""
                    主週期條件成立後，用較細週期輔助進出場確認。
                    例：1分可用於更細緻的進場與理由失效觀察。""");
            case "短線週期", "波段週期" -> htmlTooltip("""
                    目前自動監控僅支援當沖，短線/波段欄位保留但不生效。
                    例：未來恢復短線自動監控後才會啟用。""");
            case "當沖 K 線數量" -> htmlTooltip("""
                    每次掃描讀取多少根當沖週期 K 線。
                    例：220 根 M5 約涵蓋多日資料；若指定單日 SQL 回測，仍依資料日期載入。""");
            case "回測跨日暖機" -> htmlTooltip("""
                    SQL 雷達回測可先載入指定日期前的 K 線暖機 EMA 等慢速指標，暖機資料不會產生當日交易。
                    例：B/C 組啟用 120 根 M5 暖機，可避免 EMA34 在單日回測早盤資料不足。""");
            case "短線 K 線數量", "波段 K 線數量" -> htmlTooltip("""
                    目前自動監控僅支援當沖，短線/波段 K 線數量不生效。
                    例：未來恢復短線/波段自動監控後才會使用。""");
            case "啟用 RSI" -> htmlTooltip("""
                    是否讓 RSI 策略參與評分與阻擋判斷。
                    例：啟用後，RSI SHORT 可阻擋 OPEN_LONG，RSI 轉強可加分。""");
            case "RSI 週期" -> htmlTooltip("""
                    RSI 計算使用幾根 K 線。
                    例：9 代表用最近 9 根 K 計算 RSI，較敏感；14 較平滑。""");
            case "RSI 超賣" -> htmlTooltip("""
                    RSI 低於此值視為偏超賣，可能產生多方輔助訊號。
                    例：35 代表 RSI <= 35 才算超賣，但仍需 EMA 或放量反轉確認。""");
            case "RSI 超買" -> htmlTooltip("""
                    RSI 高於此值視為偏超買，可能阻擋追高開多。
                    例：68 代表 RSI >= 68 時，量能突破做多可能被視為追高。""");
            case "RSI 權重" -> htmlTooltip("""
                    RSI 在雷達分數中的權重，採 0~1 小數。
                    例：0.80 代表 RSI 訊號對總分影響較高，但不應單獨決定進場。""");
            case "啟用均線趨勢" -> htmlTooltip("""
                    是否使用 EMA/SMA 快慢線判斷趨勢方向。
                    例：EMA 8 > EMA 34 且斜率向上時，多方結構較佳。""");
            case "均線類型" -> htmlTooltip("""
                    選擇均線計算方式。EMA 對近期價格較敏感，SMA 較平滑。
                    例：當沖通常用 EMA 反應較快。""");
            case "均線快線" -> htmlTooltip("""
                    短期均線週期。
                    例：8 代表 EMA8，用來觀察短線動能。""");
            case "均線慢線" -> htmlTooltip("""
                    長期均線週期。
                    例：34 代表 EMA34，快線大於慢線時偏多。""");
            case "均線權重" -> htmlTooltip("""
                    均線趨勢在雷達分數中的權重，採 0~1 小數。
                    例：0.80 代表 EMA 多頭排列對分數有明顯加分。""");
            case "阻擋 EMA 單因子進場" -> htmlTooltip("""
                    EMA 多頭只代表趨勢結構，不讓它單獨觸發當沖開倉。
                    例：啟用後，EMA8 > EMA34 仍需 RSI 轉強或有效放量突破一起確認。""");
            case "啟用放量突破" -> htmlTooltip("""
                    是否檢查價格突破近期高點且成交量放大。
                    例：收盤突破近 30 根高點，且量大於均量 1.6 倍。""");
            case "突破回看 K 數" -> htmlTooltip("""
                    放量突破與近低追價限制會參考最近幾根 K。
                    例：30 代表用最近 30 根 K 的高點/低點做比較。""");
            case "成交量倍率" -> htmlTooltip("""
                    突破 K 的成交量需大於平均量幾倍。
                    例：1.6 代表目前量 >= 近段平均量 1.6 倍才算放量。""");
            case "放量權重" -> htmlTooltip("""
                    放量突破在雷達分數中的權重，採 0~1 小數。
                    例：0.85 代表有效放量突破會提供較高加分。""");
            case "尾盤禁止新倉時間" -> htmlTooltip("""
                    超過此時間後，自動監控不允許新開倉。
                    例：13:05 代表 13:05 後不再 OPEN_LONG，但仍可掃描與平倉。""");
            case "雷達最低進場分數" -> htmlTooltip("""
                    MarketScanner 最終多頭分數需達到此值才允許進場。
                    這是做多門檻後的第二道進場分數門檻。
                    例：0.50 代表總分低於 0.50 時直接略過。""");
            case "量能突破遇 RSI 超買時禁止追高" -> htmlTooltip("""
                    當放量突破同時 RSI 過熱時，阻擋 OPEN_LONG。
                    例：RSI >= 68 且剛爆量突破，避免追到短線高點。""");
            case "量能突破需價格延續確認" -> htmlTooltip("""
                    放量後價格要維持在突破區上方，避免單根假突破。
                    例：爆量後下一根立刻跌回突破前區間，會被阻擋。""");
            case "做多需站上 VWAP" -> htmlTooltip("""
                    開多前要求價格在自身 VWAP 上方。
                    例：Close <= VWAP 時，不允許自動監控 OPEN_LONG。""");
            case "突破後一根 K 確認" -> htmlTooltip("""
                    要求突破後再等下一根 K 續強才進場，會更保守。
                    例：突破 K 後下一根沒有站穩高點，會阻擋。""");
            case "追價限制（近低漲幅）" -> htmlTooltip("""
                    目前價格距離最近 N 根 K 低點的漲幅上限，採小數比例。
                    例：0.03 = 從近期低點漲超過 3% 擋單；0 = 關閉此限制。""");
            case "RSI 接刀需 EMA 或放量確認" -> htmlTooltip("""
                    RSI 超賣不能單獨開多，必須搭配 EMA 未轉弱或放量反轉。
                    例：弱勢股 RSI 很低但 EMA 下彎且無放量，會被略過。""");
            case "啟用內部市場狀態過濾" -> htmlTooltip("""
                    用觀察清單 SQL 分K 建立內部市場狀態 ALLOW/LIMIT/BLOCK。
                    例：站上 VWAP 比例太低或平均跌幅過大時，阻擋一般做多。""");
            case "弱勢盤只允許強勢股開多" -> htmlTooltip("""
                    WEAK 狀態下只放行相對強勢股，其他 OPEN_LONG 會被擋。
                    例：個股需站上 VWAP 且強於內部基準與觀察清單群體。""");
            case "弱勢盤策略" -> htmlTooltip("""
                    決定 WEAK 盤如何處理做多。
                    例：BLOCK_ALL 完全不做多；ALLOW_EXTREME_STRENGTH_ONLY 只做極強股。""");
            case "ALLOW VWAP通過比例%" -> htmlTooltip("""
                    觀察清單中站上自身 VWAP 的股票比例，達標才偏 ALLOW_LONG。
                    例：60 表示至少 60% 股票 Close > VWAP。""");
            case "ALLOW 最低平均漲跌%" -> htmlTooltip("""
                    觀察清單平均日內漲跌幅需高於此值，才偏允許做多。
                    例：0.0 表示觀察清單平均至少不能是負報酬。""");
            case "ALLOW Volume Sustain%" -> htmlTooltip("""
                    觀察清單中通過量能延續的股票比例，達標才偏 ALLOW_LONG。
                    例：20 表示至少 20% 股票量能延續有效。""");
            case "BLOCK VWAP通過低於%" -> htmlTooltip("""
                    站上 VWAP 比例低於此值時，內部市場偏 BLOCK_LONG。
                    例：40 表示少於 40% 股票站上 VWAP，環境偏弱。""");
            case "BLOCK 平均漲跌低於%" -> htmlTooltip("""
                    觀察清單平均漲跌低於此值時，內部市場偏 BLOCK_LONG。
                    例：-0.8 表示平均跌幅達 0.8% 以上時偏弱。""");
            case "BLOCK 創低多於創高檔數" -> htmlTooltip("""
                    創低家數比創高家數多達此數量時，內部市場偏 BLOCK_LONG。
                    例：2 表示創低比創高多 2 檔以上就偏弱。""");
            case "弱勢盤強於內部基準%" -> htmlTooltip("""
                    WEAK 盤放行時，個股需比觀察清單內部基準強多少。
                    例：0.3 表示個股日內表現至少多 0.3%。""");
            case "弱勢盤強於觀察清單群體%" -> htmlTooltip("""
                    WEAK 盤放行時，個股需比觀察清單平均表現強多少。
                    例：0.2 表示個股比群體平均至少強 0.2%。""");
            case "震盪盤要求 VWAP 與量能延續" -> htmlTooltip("""
                    RANGE 盤提高進場門檻，要求 VWAP 結構與 Volume Sustain。
                    例：震盪盤中即使分數達標，VWAP 斜率不佳仍會擋。""");
            case "啟用 Volume Sustain Filter" -> htmlTooltip("""
                    要求最近 K 線量能能延續，而不是只有單根爆量。
                    例：突破後量縮且價格跌回突破區，會被阻擋。""");
            case "啟用 ATR 動態停損停利", "啟用 ATR 停損/停利" -> htmlTooltip("""
                    使用 ATR 動態計算停損與停利，不用固定百分比。
                    例：停損 = Entry - ATR x 停損倍數。""");
            case "啟用 ATR 追高限制" -> htmlTooltip("""
                    用 ATR 衡量目前價格是否離近期低點太遠。
                    例：距低點 > ATR x 1.8 時，視為追高並阻擋。""");
            case "ATR 週期" -> htmlTooltip("""
                    ATR 計算使用幾根 K 線。
                    例：14 代表用最近 14 根 K 估算平均波動。""");
            case "ATR 停損倍數" -> htmlTooltip("""
                    停損距離用 ATR 的幾倍。
                    例：1.0 表示停損約放在 Entry - 1 ATR。""");
            case "ATR 停利倍數" -> htmlTooltip("""
                    停利距離用 ATR 的幾倍。
                    例：1.6 表示停利約放在 Entry + 1.6 ATR。""");
            case "ATR 追高限制倍數" -> htmlTooltip("""
                    價格距離近期低點不可超過 ATR 幾倍。
                    例：1.8 表示漲幅超過 1.8 ATR 就擋追高。""");
            case "單日最大虧損" -> htmlTooltip("""
                    自動監控當日累計損益低於此值後停止新開倉。
                    例：-3000 表示今日虧損達 3000 元後停止交易。""");
            case "單日停損次數上限" -> htmlTooltip("""
                    當日停損次數達上限後停止新開倉。
                    例：3 表示今天停損 3 次後不再開新倉。""");
            case "連續虧損上限" -> htmlTooltip("""
                    連續虧損達上限後停止新開倉。
                    例：2 表示連虧 2 筆後啟動熔斷。""");
            case "觸發日損/連敗後停止今日自動開倉" -> htmlTooltip("""
                    啟用日損、停損次數或連敗熔斷後停止新開倉。
                    例：連虧 2 筆後，今日雷達仍掃描但不自動買入。""");
            case "每日最多交易" -> htmlTooltip("""
                    自動監控每日最多開幾筆新倉。
                    例：5 表示一天最多 5 次 OPEN_LONG。""");
            case "開單間隔分鐘" -> htmlTooltip("""
                    任兩筆自動新倉之間至少間隔幾分鐘。
                    例：5 表示 5 分鐘內最多開 1 筆。""");
            case "平倉後冷卻分鐘" -> htmlTooltip("""
                    同股平倉後等待幾分鐘才允許再次開倉。
                    例：30 表示賣出後 30 分鐘內不再買同一檔。""");
            case "同一根 5 分 K 只允許 1 筆新倉" -> htmlTooltip("""
                    限制同一個 M5 時間桶只開一筆新倉，避免同時追多檔。
                    例：09:35 這根 K 已開一筆，就等下一根 M5。""");
            case "RANGE 盤啟用時間/動能失效出場" -> htmlTooltip("""
                    RANGE 盤進場後若一段時間未達指定 R，提前出場或停止持有。
                    例：進場 25 分鐘未達 +0.5R，視為動能不足。""");
            case "RANGE 失效等待分鐘" -> htmlTooltip("""
                    RANGE 盤進場後等待多久檢查動能是否失效。
                    例：25 表示進場 25 分鐘後若未達標就提前出場。""");
            case "RANGE 最低達成 R" -> htmlTooltip("""
                    RANGE 盤等待時間內至少要達到多少 R。
                    例：0.5 表示至少要浮盈 0.5R，否則視為動能失效。""");
            case "RANGE 盤 Volume Sustain 失效出場" -> htmlTooltip("""
                    RANGE 盤持倉中若量能延續失效，提前出場。
                    例：突破後量縮回落，避免等到固定停損。""");
            default -> null;
        };
    }

    private String htmlTooltip(String text) {
        String escaped = text.strip()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<html><div style='width:360px'>" + escaped.replace("\n", "<br>") + "</div></html>";
    }

    private void setTooltipRecursively(JComponent component, String tooltip) {
        component.setToolTipText(tooltip);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent childComponent
                    && (childComponent.getToolTipText() == null || childComponent.getToolTipText().isBlank())) {
                setTooltipRecursively(childComponent, tooltip);
            }
        }
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
                        clearAutoPositionState(symbol);
                    }
                    case OPEN_SHORT, HOLD, NO_ACTION -> {
                        return;
                    }
                }

                if (result != null) {
                    paperTradeRecorder.record(result, autoSignal, latestScanResults.get(symbol), "auto-monitor", monitorStrategyName, buildMonitorStrategyDetails());
                    if (!result.isSuccess()) {
                        pendingAutoEntries.remove(symbol);
                    } else if (autoSignal.getAction() == DecisionResult.Action.OPEN_LONG) {
                        registerAutoMonitorEntry();
                        activeTradeModes.put(symbol, TradeMode.DAY_TRADE);
                        autoManagedPositions.add(symbol);
                        autoEntryTimes.put(symbol, result.getExecutionTime());
                        autoEntryPrices.put(symbol, result.getExecutedPrice());
                        if (result.getStopLoss() != null && result.getStopLoss() > 0.0) {
                            activeStopLosses.put(symbol, result.getStopLoss());
                            autoEntryRiskAmounts.put(symbol, Math.max(0.0, result.getExecutedPrice() - result.getStopLoss()));
                        }
                        if (result.getTakeProfit() != null && result.getTakeProfit() > 0.0) {
                            activeTakeProfits.put(symbol, result.getTakeProfit());
                        }
                        MarketScanResult scanResult = latestScanResults.get(symbol);
                        if (scanResult != null && scanResult.getMarketRegime() == MarketRegime.RANGE) {
                            autoRangeEntries.add(symbol);
                        }
                    } else if (autoSignal.getAction() == DecisionResult.Action.CLOSE_POSITION) {
                        updateAutoMonitorRiskAfterClose(symbol, result, autoSignal);
                        registerPostExitCooldown(symbol);
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
        resetAutoMonitorRiskIfNewDay();
        if (autoMonitorTradingHalted) {
            return String.format(Locale.US,
                    "自動監控熔斷中，今日停止新開倉：日損 %.2f，停損 %d 次，連虧 %d 次",
                    autoMonitorDailyPnl,
                    autoMonitorStopLossCount,
                    autoMonitorConsecutiveLosses);
        }
        LocalDateTime nowDateTime = LocalDateTime.now(TAIPEI_ZONE);
        LocalTime now = nowDateTime.toLocalTime();
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
            if (nowDateTime.isBefore(cooldownUntil)) {
                return "停損冷卻中，略過自動開倉：" + symbol + "，冷卻至 " + cooldownUntil.toLocalTime();
            }
            stopLossCooldownUntil.remove(symbol);
        }
        if (monitorConfig != null && autoMonitorEntryCount >= monitorConfig.getDailyMaxAutoTrades()) {
            return "每日最多自動交易已達上限：" + monitorConfig.getDailyMaxAutoTrades() + " 筆，阻擋 " + symbol;
        }
        if (monitorConfig != null && monitorConfig.getEntryPacingMinutes() > 0 && autoMonitorLastEntryTime != null
                && nowDateTime.isBefore(autoMonitorLastEntryTime.plusMinutes(monitorConfig.getEntryPacingMinutes()))) {
            return "開單節奏限制：距離上一筆未滿 "
                    + monitorConfig.getEntryPacingMinutes() + " 分鐘，阻擋 " + symbol;
        }
        if (monitorConfig != null && monitorConfig.isOneEntryPerFiveMinuteBar()) {
            String bucket = currentFiveMinuteEntryBucket(nowDateTime);
            if (autoMonitorEntryBuckets.contains(bucket)) {
                return "同一根 5 分 K 已有自動開倉，阻擋 " + symbol;
            }
        }
        int maxPositions = monitorDecisionConfig != null
                ? monitorDecisionConfig.getRiskConfig().getMaxConcurrentPositions()
                : 1;
        if (monitorPortfolio != null && monitorPortfolio.getPositionCount() >= maxPositions) {
            return "自動監控持倉已達上限 " + maxPositions + " 檔，略過：" + symbol;
        }
        return null;
    }

    private void updateAutoMonitorRiskAfterClose(String symbol, ExecutionResult result, DecisionResult signal) {
        resetAutoMonitorRiskIfNewDay();
        double realized = result != null ? result.getRealizedPnL() : 0.0;
        autoMonitorDailyPnl += realized;
        if (realized < 0.0) {
            autoMonitorConsecutiveLosses++;
        } else if (realized > 0.0) {
            autoMonitorConsecutiveLosses = 0;
        }
        String reason = signal != null && signal.getReason() != null ? signal.getReason() : "";
        if (reason.contains("停損") || reason.toUpperCase(Locale.ROOT).contains("STOP")) {
            autoMonitorStopLossCount++;
            registerStopLossCooldown(symbol);
        }
        if (monitorConfig != null && monitorConfig.isDisableTradingAfterLossLimit()
                && (autoMonitorDailyPnl <= monitorConfig.getDailyMaxLoss()
                || (monitorConfig.getDailyMaxStopLossCount() > 0
                && autoMonitorStopLossCount >= monitorConfig.getDailyMaxStopLossCount())
                || (monitorConfig.getConsecutiveLossLimit() > 0
                && autoMonitorConsecutiveLosses >= monitorConfig.getConsecutiveLossLimit()))) {
            autoMonitorTradingHalted = true;
            statusBar.setText(String.format(Locale.US,
                    "自動監控熔斷：%s 平倉後日損 %.2f，停損 %d 次，連虧 %d 次",
                    symbol,
                    autoMonitorDailyPnl,
                    autoMonitorStopLossCount,
                    autoMonitorConsecutiveLosses));
        }
    }

    private void resetAutoMonitorRiskIfNewDay() {
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        if (!today.equals(autoMonitorRiskDate)) {
            autoMonitorRiskDate = today;
            autoMonitorDailyPnl = 0.0;
            autoMonitorStopLossCount = 0;
            autoMonitorConsecutiveLosses = 0;
            autoMonitorTradingHalted = false;
            autoMonitorEntryCount = 0;
            autoMonitorLastEntryTime = null;
            autoMonitorEntryBuckets.clear();
        }
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
                : LocalTime.of(13, 5);
        return latestEntryTime != null && !time.isBefore(latestEntryTime);
    }

    private void registerStopLossCooldown(String symbol) {
        if (symbol == null || symbol.isBlank()
                || monitorConfig == null
                || !monitorConfig.isStopLossCooldownEnabled()) {
            return;
        }
        registerSymbolCooldown(symbol, monitorConfig.getStopLossCooldownMinutes());
    }

    private void registerPostExitCooldown(String symbol) {
        if (symbol == null || symbol.isBlank() || monitorConfig == null
                || monitorConfig.getPostExitCooldownMinutes() <= 0) {
            return;
        }
        registerSymbolCooldown(symbol, monitorConfig.getPostExitCooldownMinutes());
    }

    private void registerSymbolCooldown(String symbol, int minutes) {
        if (symbol == null || symbol.isBlank() || minutes <= 0) {
            return;
        }
        LocalDateTime until = LocalDateTime.now(TAIPEI_ZONE).plusMinutes(minutes);
        LocalDateTime existing = stopLossCooldownUntil.get(symbol);
        if (existing == null || until.isAfter(existing)) {
            stopLossCooldownUntil.put(symbol, until);
        }
    }

    private void registerAutoMonitorEntry() {
        resetAutoMonitorRiskIfNewDay();
        LocalDateTime now = LocalDateTime.now(TAIPEI_ZONE);
        autoMonitorEntryCount++;
        autoMonitorLastEntryTime = now;
        autoMonitorEntryBuckets.add(currentFiveMinuteEntryBucket(now));
    }

    private String currentFiveMinuteEntryBucket(LocalDateTime dateTime) {
        LocalDateTime normalized = dateTime != null ? dateTime : LocalDateTime.now(TAIPEI_ZONE);
        int minute = normalized.getMinute() - normalized.getMinute() % 5;
        return normalized.toLocalDate() + "T"
                + normalized.getHour() + ":"
                + String.format(Locale.US, "%02d", minute);
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

