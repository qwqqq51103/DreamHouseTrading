package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.*;
import com.dreamhouse.trading.core.model.*;
import com.dreamhouse.trading.ui.dock.*;
import com.dreamhouse.trading.util.I18n;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Locale;

public class MainFrameAdvanced extends JFrame {
    private final MarketDataFeed dataFeed;
    private final StatusBar statusBar;
    
    private ChartDock chartDock;
    private IndicatorDock indicatorDock;
    private WatchlistPanel watchlistPanel;
    private OrderBookDock orderBookDock;
    private TimeSalesDock timeSalesDock;
    private NewsDock newsDock;
    
    private String currentSymbol = "";
    private Timeframe currentTimeframe = Timeframe.M1;
    private double lastPrice = 0;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();
    
    public MainFrameAdvanced() {
        setTitle(I18n.get("app.title"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);
        
        // 建立資料源
        dataFeed = new SimulatorFeed();
        
        // 建立 MenuBar
        setJMenuBar(createMenuBar());
        
        // 建立 ToolBar
        JToolBar toolBar = createToolBar();
        
        // 建立 StatusBar
        statusBar = new StatusBar();
        statusBar.setSymbol(currentSymbol);
        statusBar.setTimeframe(currentTimeframe.getLabel());
        statusBar.setConnectionStatus(true);
        
        // 建立面板
        createPanels();
        
        // 建立佈局
        JPanel mainPanel = createLayout();
        
        // 組裝視窗
        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        
        // 訂閱市場資料
        subscribeMarketData();
        
        // 啟動資料源
        dataFeed.start();
        
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
    
    private void createPanels() {
        // 主圖
        chartDock = new ChartDock();
        
        // 副圖
        indicatorDock = new IndicatorDock();
        indicatorDock.setIndicatorService(chartDock.indicatorService);
        
        // 觀察清單
        watchlistPanel = new WatchlistPanel();
        watchlistPanel.setOnSymbolDoubleClick(this::changeSymbol);
        
        // 五檔掛單
        orderBookDock = new OrderBookDock();
        
        // 逐筆成交
        timeSalesDock = new TimeSalesDock();
        
        // 市場消息
        newsDock = new NewsDock();
    }
    
    private JPanel createLayout() {
        // 包裝面板加上標題邊框
        JPanel chartPanel = wrapWithBorder(chartDock, I18n.get("dock.chart"));
        JPanel indicatorPanel = wrapWithBorder(indicatorDock, I18n.get("dock.indicator"));
        JPanel watchlistWrapPanel = wrapWithBorder(watchlistPanel, I18n.get("dock.watchlist"));
        JPanel orderBookWrapPanel = wrapWithBorder(orderBookDock, I18n.get("dock.orderbook"));
        JPanel timeSalesWrapPanel = wrapWithBorder(timeSalesDock, I18n.get("dock.timesales"));
        JPanel newsWrapPanel = wrapWithBorder(newsDock, I18n.get("dock.news"));
        
        // 右側主圖+副圖（上下分割）
        JSplitPane rightTopSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, indicatorPanel);
        rightTopSplit.setDividerLocation(500);
        rightTopSplit.setResizeWeight(0.75);
        
        // 底部三個面板（水平分割）
        JSplitPane bottomLeftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, orderBookWrapPanel, timeSalesWrapPanel);
        bottomLeftSplit.setDividerLocation(300);
        bottomLeftSplit.setResizeWeight(0.5);
        
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bottomLeftSplit, newsWrapPanel);
        bottomSplit.setDividerLocation(600);
        bottomSplit.setResizeWeight(0.7);
        
        // 右側整體（主圖+副圖 + 底部）
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTopSplit, bottomSplit);
        rightSplit.setDividerLocation(550);
        rightSplit.setResizeWeight(0.65);
        
        // 最外層（左側觀察清單 + 右側所有）
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, watchlistWrapPanel, rightSplit);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.15);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(mainSplit);
        
        return mainPanel;
    }
    
    private JPanel wrapWithBorder(JComponent component, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File Menu
        JMenu fileMenu = new JMenu(I18n.get("menu.file"));
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
        
        menuBar.add(viewMenu);
        
        // Layout Menu
        JMenu layoutMenu = new JMenu(I18n.get("menu.layout"));
        JMenuItem resetLayout = new JMenuItem(I18n.get("menu.layout.reset"));
        JMenuItem saveLayout = new JMenuItem(I18n.get("menu.layout.save"));
        resetLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "重設佈局功能（可保留分割位置）"));
        saveLayout.addActionListener(e -> JOptionPane.showMessageDialog(this, "儲存佈局功能（可保存分割位置）"));
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
        ToolBarFactory.ToolBarCallbacks callbacks = new ToolBarFactory.ToolBarCallbacks();
        callbacks.onSymbolChange = this::changeSymbol;
        callbacks.onTimeframeChange = this::changeTimeframe;
        callbacks.onIndicatorChange = this::changeIndicator;
        
        return ToolBarFactory.createToolBar(callbacks);
    }
    
    private void subscribeMarketData() {
        dataFeed.subscribe(currentSymbol, new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                lastPrice = tick.getPrice();
                statusBar.setLastPrice(lastPrice, 0);
                frameCount++;
                chartDock.onTick(tick);
                indicatorDock.updateChart();
            }
            
            @Override
            public void onDepthUpdate(List<DepthLevel> depth) {
                orderBookDock.updateDepth(depth);
            }
            
            @Override
            public void onTrade(Trade trade) {
                timeSalesDock.addTrade(trade);
            }
        });
    }
    
    private void changeSymbol(String symbol) {
        currentSymbol = symbol;
        statusBar.setSymbol(symbol);
        subscribeMarketData();
    }
    
    private void changeTimeframe(Timeframe tf) {
        currentTimeframe = tf;
        statusBar.setTimeframe(tf.getLabel());
        chartDock.setTimeframe(tf);
    }
    
    private void changeIndicator(String indicator) {
        if ("RSI".equals(indicator)) {
            indicatorDock.setIndicatorType(IndicatorDock.IndicatorType.RSI);
        } else if ("MACD".equals(indicator)) {
            indicatorDock.setIndicatorType(IndicatorDock.IndicatorType.MACD);
        }
        
        if ("SMA".equals(indicator)) {
            chartDock.setOverlayIndicator("SMA");
        } else if ("EMA".equals(indicator)) {
            chartDock.setOverlayIndicator("EMA");
        }
    }
    
    private void setTheme(boolean light) {
        try {
            if (light) {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } else {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            }
            SwingUtilities.updateComponentTreeUI(this);
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
        // 重建佈局以更新所有標題
        getContentPane().removeAll();
        
        JToolBar toolBar = createToolBar();
        JPanel mainPanel = createLayout();
        
        add(toolBar, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        
        SwingUtilities.updateComponentTreeUI(this);
        revalidate();
        repaint();
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
}

