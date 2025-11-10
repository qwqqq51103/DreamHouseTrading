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

    private String currentSymbol = "AAPL";
    private Timeframe currentTimeframe = Timeframe.M1;
    private double lastPrice = 0;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();

    // 保存當前的市場數據監聽器引用，用於取消訂閱
    private MarketDataListener currentMarketDataListener;

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
        
        // 訂閱市場資料
        subscribeMarketData();
        
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
        DockableWrapper watchlistWrapper = new DockableWrapper("watchlist", I18n.get("dock.watchlist"), watchlistPanel);
        Docking.registerDockable(watchlistWrapper);
        Docking.dock(watchlistWrapper, chartWrapper, DockingRegion.WEST);
        
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
        JTextField symbolField = new JTextField("AAPL", 8);
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
        simulationBtn.setSelected(false);  // 預設為運行狀態
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
        };

        // 訂閱當前商品
        dataFeed.subscribe(currentSymbol, currentMarketDataListener);
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
        statusBar.setSymbol(symbol);
        statusBar.setText("正在載入 " + symbol + " 的歷史數據...");

        // 訂閱新商品
        subscribeMarketData();

        // 如果是SimulatorFeed，觸發歷史數據生成
        if (dataFeed instanceof SimulatorFeed) {
            final SimulatorFeed simulatorFeed = (SimulatorFeed) dataFeed;
            // 在背景執行緒中生成歷史數據，避免阻塞UI
            new Thread(() -> {
                try {
                    // 確保subscribe完成後再生成歷史數據
                    Thread.sleep(50);

                    System.out.println("[MainFrame] 開始生成 " + symbol + " 的歷史數據");
                    simulatorFeed.generateHistoricalDataForSymbol(symbol);
                    System.out.println("[MainFrame] " + symbol + " 歷史數據生成完成");

                    SwingUtilities.invokeLater(() -> {
                        statusBar.setText(symbol + " 數據載入完成");
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("[MainFrame] 歷史數據載入被中斷");
                }
            }, "HistoricalDataLoader-" + symbol).start();
        } else {
            statusBar.setText(symbol + " 已切換");
        }

        System.out.println("[MainFrame] 商品切換完成: " + symbol);
    }
    
    private void changeTimeframe(Timeframe tf) {
        currentTimeframe = tf;
        statusBar.setTimeframe(tf.getLabel());
        chartDock.setTimeframe(tf);
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
        
        // 3. 轉換數據格式
        List<com.dreamhouse.trading.core.model.Bar> bars = new ArrayList<>();
        for (org.ta4j.core.Bar ta4jBar : ta4jBars) {
            com.dreamhouse.trading.core.model.Bar bar = new com.dreamhouse.trading.core.model.Bar(
                ta4jBar.getBeginTime().toLocalDateTime(),
                ta4jBar.getOpenPrice().doubleValue(),
                ta4jBar.getHighPrice().doubleValue(),
                ta4jBar.getLowPrice().doubleValue(),
                ta4jBar.getClosePrice().doubleValue(),
                ta4jBar.getVolume().longValue()
            );
            bars.add(bar);
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

