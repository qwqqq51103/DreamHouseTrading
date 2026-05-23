package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.*;
import com.dreamhouse.trading.core.model.*;
import com.dreamhouse.trading.ui.dock.*;
import com.formdev.flatlaf.FlatDarkLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class MainFrame extends JFrame {
    private final MarketDataFeed dataFeed;
    private final StatusBar statusBar;
    private final WatchlistPanel watchlistPanel;
    private final OrderBookDock orderBookDock;
    private final TimeSalesDock timeSalesDock;
    private final NewsDock newsDock;
    
    private String currentSymbol = "";
    private Timeframe currentTimeframe = Timeframe.M1;
    private double lastPrice = 0;
    private double previousPrice = 0;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();
    
    public MainFrame() {
        setTitle("DreamHouse Trading Workstation");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);
        
        // 建立資料源
        dataFeed = new SimulatorFeed();
        
        // 建立 MenuBar
        MenuBarFactory.Callbacks menuCallbacks = new MenuBarFactory.Callbacks();
        // MainFrame 不需要指標設定回調（使用舊版 UI）
        setJMenuBar(MenuBarFactory.createMenuBar(this, menuCallbacks));
        
        // 建立 ToolBar
        ToolBarFactory.ToolBarCallbacks callbacks = new ToolBarFactory.ToolBarCallbacks();
        callbacks.onSymbolChange = this::changeSymbol;
        callbacks.onTimeframeChange = this::changeTimeframe;
        callbacks.onIndicatorChange = this::changeIndicator;
        
        JToolBar toolBar = ToolBarFactory.createToolBar(callbacks);
        
        // 建立 StatusBar
        statusBar = new StatusBar();
        statusBar.setSymbol(currentSymbol);
        statusBar.setTimeframe(currentTimeframe.getLabel());
        statusBar.setConnectionStatus(true);
        
        // 建立面板
        watchlistPanel = new WatchlistPanel();
        watchlistPanel.setOnSymbolDoubleClick(this::changeSymbol);
        
        orderBookDock = new OrderBookDock();
        timeSalesDock = new TimeSalesDock();
        newsDock = new NewsDock();
        
        // 主圖區域（簡化版：使用之前的 ChartView）
        JPanel chartPlaceholder = new JPanel(new BorderLayout());
        chartPlaceholder.setBorder(BorderFactory.createTitledBorder("主圖 (K Line + Indicators)"));
        chartPlaceholder.add(new JLabel("主圖區域 - K 線圖 + SMA/EMA", SwingConstants.CENTER));
        
        // 副圖區域
        JPanel indicatorPlaceholder = new JPanel(new BorderLayout());
        indicatorPlaceholder.setBorder(BorderFactory.createTitledBorder("副圖 (RSI/MACD)"));
        indicatorPlaceholder.add(new JLabel("副圖區域 - RSI/MACD", SwingConstants.CENTER));
        
        // 組合右側主圖+副圖
        JPanel rightChartArea = new JPanel(new MigLayout("insets 0, gap 0", "[grow,fill]", "[grow 3,fill][grow 1,fill]"));
        rightChartArea.add(chartPlaceholder, "wrap");
        rightChartArea.add(indicatorPlaceholder);
        
        // 中央面板：五檔+逐筆
        JPanel centerMiddle = new JPanel(new MigLayout("insets 0, gap 5", "[grow,fill][grow,fill]", "[grow,fill]"));
        centerMiddle.add(orderBookDock);
        centerMiddle.add(timeSalesDock);
        
        // 主佈局
        JPanel mainPanel = new JPanel(new MigLayout(
            "insets 8, gap 8",
            "[250!][grow,fill]",
            "[grow 2,fill][grow 1,fill]"
        ));
        
        // 左側：Watchlist
        mainPanel.add(watchlistPanel, "spany 2, grow");
        
        // 右上：主圖+副圖
        mainPanel.add(rightChartArea, "wrap");
        
        // 右下：五檔+逐筆+新聞（分三欄）
        JPanel bottomRight = new JPanel(new MigLayout("insets 0, gap 5", "[grow,fill][grow,fill][grow,fill]", "[grow,fill]"));
        bottomRight.add(centerMiddle, "span 2");
        bottomRight.add(newsDock);
        mainPanel.add(bottomRight);
        
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
    
    private void subscribeMarketData() {
        dataFeed.subscribe(currentSymbol, new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                previousPrice = lastPrice;
                lastPrice = tick.getPrice();

                // 計算價格變化百分比
                double changePercent = 0;
                if (previousPrice > 0) {
                    changePercent = ((lastPrice - previousPrice) / previousPrice) * 100.0;
                }

                statusBar.setLastPrice(lastPrice, changePercent);
                frameCount++;
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
        // 重新訂閱
        subscribeMarketData();
    }
    
    private void changeTimeframe(Timeframe tf) {
        currentTimeframe = tf;
        statusBar.setTimeframe(tf.getLabel());
        // 注意: 切換時間週期後需要重新聚合資料
        // 實現建議: 使用 BarAggregator 將 Tick 數據聚合為新週期的 K 線
        // dataFeed.unsubscribe(currentSymbol);
        // subscribeMarketData();
    }

    private void changeIndicator(String indicator) {
        System.out.println("Change indicator to: " + indicator);
        // 注意: 需要更新圖表面板的指標顯示
        // 實現建議: 通知 ChartDock 更新指標配置並重繪
        // chartDock.setIndicator(indicator);
        // chartDock.repaint();
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
                try {
                    if (isDark) {
                        UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
                    } else {
                        UIManager.setLookAndFeel(new FlatDarkLaf());
                    }
                    SwingUtilities.updateComponentTreeUI(MainFrame.this);
                    isDark = !isDark;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}

