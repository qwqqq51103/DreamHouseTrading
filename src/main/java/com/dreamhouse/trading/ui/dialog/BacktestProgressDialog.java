package com.dreamhouse.trading.ui.dialog;

import com.dreamhouse.trading.core.backtest.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.format.DateTimeFormatter;

/**
 * 回測進度對話框
 * 顯示回測執行進度和實時統計
 */
public class BacktestProgressDialog extends JDialog implements BacktestListener {
    
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel timeLabel;
    private JLabel tradesLabel;
    private JLabel returnLabel;
    private JLabel drawdownLabel;
    private JLabel positionLabel;  // 新增：持倉狀態標籤
    private JButton stopButton;
    private JButton closeButton;
    
    private BacktestEngine engine;
    private javax.swing.SwingWorker<?, ?> worker;
    private long startTime;
    private boolean completed = false;
    
    /**
     * 構造函數
     */
    public BacktestProgressDialog(JFrame parent, BacktestEngine engine) {
        super(parent, "回測進度", true);
        this.engine = engine;
        this.worker = null;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(450, 300);
        setLocationRelativeTo(parent);
        
        // 添加為回測監聽器
        engine.addListener(this);
    }
    
    /**
     * 構造函數 (帶 SwingWorker)
     */
    public BacktestProgressDialog(JFrame parent, BacktestEngine engine, javax.swing.SwingWorker<?, ?> worker) {
        super(parent, "回測進度", true);
        this.engine = engine;
        this.worker = worker;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(450, 300);
        setLocationRelativeTo(parent);
        
        // 添加為回測監聽器
        engine.addListener(this);
    }
    
    /**
     * 初始化組件
     */
    private void initializeComponents() {
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("準備中...");
        
        statusLabel = new JLabel("等待開始...");
        timeLabel = new JLabel("經過時間: 00:00:00");
        tradesLabel = new JLabel("交易次數: 0");
        returnLabel = new JLabel("當前收益: 0.00%");
        drawdownLabel = new JLabel("最大回撤: 0.00%");
        positionLabel = new JLabel("持倉狀態: 空倉");
        
        stopButton = new JButton("停止");
        closeButton = new JButton("關閉");
        closeButton.setEnabled(false);
    }
    
    /**
     * 佈局組件
     */
    private void layoutComponents() {
        setLayout(new BorderLayout());
        
        // 主面板
        JPanel mainPanel = new JPanel(new MigLayout("fillx", "[grow]", ""));
        
        // 進度區域
        JPanel progressPanel = new JPanel(new MigLayout("fillx", "[grow]", ""));
        progressPanel.setBorder(BorderFactory.createTitledBorder("執行進度"));
        
        progressPanel.add(statusLabel, "wrap");
        progressPanel.add(progressBar, "wrap, growx");
        progressPanel.add(timeLabel, "wrap");
        
        // 統計區域
        JPanel statsPanel = new JPanel(new MigLayout("fillx", "[grow]", ""));
        statsPanel.setBorder(BorderFactory.createTitledBorder("實時統計"));
        
        statsPanel.add(tradesLabel, "wrap");
        statsPanel.add(returnLabel, "wrap");
        statsPanel.add(drawdownLabel, "wrap");
        statsPanel.add(positionLabel, "wrap");
        
        mainPanel.add(progressPanel, "wrap, growx");
        mainPanel.add(statsPanel, "wrap, growx");
        
        add(mainPanel, BorderLayout.CENTER);
        
        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(stopButton);
        buttonPanel.add(closeButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 設定事件處理器
     */
    private void setupEventHandlers() {
        stopButton.addActionListener(e -> {
            System.out.println("停止按鈕被點擊");
            if (engine.isRunning()) {
                engine.stopBacktest();
                if (worker != null) {
                    System.out.println("取消 SwingWorker");
                    worker.cancel(true);
                }
                statusLabel.setText("正在停止...");
                stopButton.setEnabled(false);
            }
        });
        
        closeButton.addActionListener(e -> {
            if (completed || !engine.isRunning()) {
                dispose();
            }
        });
        
        // 時間更新定時器
        Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (startTime > 0) {
                    updateElapsedTime();
                }
            }
        });
        timer.start();
        
        // 窗口關閉處理
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (engine.isRunning()) {
                    int result = JOptionPane.showConfirmDialog(
                        BacktestProgressDialog.this,
                        "回測正在進行中，確定要停止嗎？",
                        "確認停止",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (result == JOptionPane.YES_OPTION) {
                        engine.stopBacktest();
                        dispose();
                    }
                } else {
                    dispose();
                }
            }
        });
    }
    
    /**
     * 更新經過時間
     */
    private void updateElapsedTime() {
        if (startTime > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long seconds = elapsed / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            String timeStr = String.format("經過時間: %02d:%02d:%02d", 
                                         hours % 24, minutes % 60, seconds % 60);
            timeLabel.setText(timeStr);
        }
    }
    
    /**
     * 更新統計信息
     */
    private void updateStatistics() {
        if (engine.getPortfolio() != null) {
            Portfolio portfolio = engine.getPortfolio();
            
            // 交易次數
            tradesLabel.setText("交易次數: " + portfolio.getTotalTrades());
            
            // 當前收益
            double currentReturn = portfolio.getTotalReturn() * 100;
            String returnText = String.format("當前收益: %.2f%%", currentReturn);
            returnLabel.setText(returnText);
            returnLabel.setForeground(currentReturn >= 0 ? new Color(0, 150, 0) : Color.RED);
            
            // 最大回撤
            double maxDrawdown = portfolio.getMaxDrawdown() * 100;
            String drawdownText = String.format("最大回撤: %.2f%%", maxDrawdown);
            drawdownLabel.setText(drawdownText);
            drawdownLabel.setForeground(Color.RED);
            
            // 持倉狀態
            int positionCount = portfolio.getPositions().size();
            if (positionCount > 0) {
                positionLabel.setText(String.format("持倉狀態: 持倉中 (%d個)", positionCount));
                positionLabel.setForeground(new Color(0, 100, 200)); // 藍色
            } else {
                positionLabel.setText("持倉狀態: 空倉");
                positionLabel.setForeground(Color.GRAY);
            }
        }
    }
    
    // BacktestListener 實現
    
    @Override
    public void onBacktestStarted() {
        SwingUtilities.invokeLater(() -> {
            startTime = System.currentTimeMillis();
            statusLabel.setText("回測執行中...");
            progressBar.setString("0%");
            stopButton.setEnabled(true);
            closeButton.setEnabled(false);
        });
    }
    
    @Override
    public void onProgressUpdate(double progress) {
        SwingUtilities.invokeLater(() -> {
            int percent = (int) (progress * 100);
            progressBar.setValue(percent);
            progressBar.setString(percent + "%");
            
            // 更新統計信息
            updateStatistics();
        });
    }
    
    @Override
    public void onTradeExecuted(String symbol, TradeType type, int quantity, double price) {
        SwingUtilities.invokeLater(() -> {
            String tradeInfo = String.format("%s %s %d@%.2f", 
                                           type.getDisplayName(), symbol, quantity, price);
            statusLabel.setText("交易執行: " + tradeInfo);
            
            // 更新統計信息
            updateStatistics();
        });
    }
    
    @Override
    public void onBacktestCompleted(BacktestResult result) {
        SwingUtilities.invokeLater(() -> {
            completed = true;
            progressBar.setValue(100);
            progressBar.setString("100% - 完成");
            
            statusLabel.setText("回測完成！");
            stopButton.setEnabled(false);
            closeButton.setEnabled(true);
            
            // 最終統計更新
            updateFinalStatistics(result);
            
            // 顯示完成通知
            showCompletionNotification(result);
        });
    }
    
    @Override
    public void onBacktestError(Exception error) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("回測錯誤: " + error.getMessage());
            progressBar.setString("錯誤");
            stopButton.setEnabled(false);
            closeButton.setEnabled(true);
            
            JOptionPane.showMessageDialog(this, 
                "回測執行時發生錯誤:\n" + error.getMessage(),
                "錯誤", 
                JOptionPane.ERROR_MESSAGE);
        });
    }
    
    /**
     * 更新最終統計
     */
    private void updateFinalStatistics(BacktestResult result) {
        tradesLabel.setText("交易次數: " + result.getTotalTrades());
        
        double totalReturn = result.getTotalReturn() * 100;
        String returnText = String.format("總收益: %.2f%%", totalReturn);
        returnLabel.setText(returnText);
        returnLabel.setForeground(totalReturn >= 0 ? new Color(0, 150, 0) : Color.RED);
        
        double maxDrawdown = result.getMaxDrawdown() * 100;
        String drawdownText = String.format("最大回撤: %.2f%%", maxDrawdown);
        drawdownLabel.setText(drawdownText);
    }
    
    /**
     * 顯示完成通知
     */
    private void showCompletionNotification(BacktestResult result) {
        String message = String.format(
            "回測已完成！\n\n" +
            "總收益率: %.2f%%\n" +
            "最大回撤: %.2f%%\n" +
            "夏普比率: %.2f\n" +
            "總交易次數: %d\n" +
            "勝率: %.1f%%",
            result.getTotalReturn() * 100,
            result.getMaxDrawdown() * 100,
            result.getSharpeRatio(),
            result.getTotalTrades(),
            result.getWinRate() * 100
        );
        
        JOptionPane.showMessageDialog(this, message, "回測完成", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 獲取回測結果
     */
    public BacktestResult getResult() {
        return engine.getResult();
    }
    
    /**
     * 檢查是否完成
     */
    public boolean isCompleted() {
        return completed;
    }
}
