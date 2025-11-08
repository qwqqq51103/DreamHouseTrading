package com.dreamhouse.trading.ui.chart;

import com.dreamhouse.trading.core.backtest.Trade;
import com.dreamhouse.trading.core.backtest.TradeType;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.plot.XYPlot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 交易標記管理器
 * 負責管理交易標記的互動功能
 */
public class TradeMarkerManager implements ChartMouseListener {
    
    private final ChartPanel chartPanel;
    private final XYPlot plot;
    private List<TradeMarker> markers = new ArrayList<>();
    private List<Trade> allTrades = new ArrayList<>();
    
    // 標記篩選狀態
    private boolean showBuyMarkers = true;
    private boolean showSellMarkers = true;
    private String searchText = "";
    
    // 回調接口
    private TradeSelectionListener selectionListener;
    
    /**
     * 交易選擇監聽器接口
     */
    public interface TradeSelectionListener {
        void onTradeSelected(Trade trade);
    }
    
    /**
     * 構造函數
     */
    public TradeMarkerManager(ChartPanel chartPanel, XYPlot plot) {
        this.chartPanel = chartPanel;
        this.plot = plot;
        
        // 添加滑鼠監聽器
        chartPanel.addChartMouseListener(this);
    }
    
    /**
     * 設置交易選擇監聽器
     */
    public void setSelectionListener(TradeSelectionListener listener) {
        this.selectionListener = listener;
    }
    
    /**
     * 設置交易列表並創建標記
     */
    public void setTrades(List<Trade> trades, TradeMarker.TimeFrameConverter converter) {
        this.allTrades = new ArrayList<>(trades);
        this.markers = TradeMarker.createMarkersFromTrades(trades, converter);
        applyFilter();
    }
    
    /**
     * 應用篩選並更新顯示
     */
    private void applyFilter() {
        // 清除所有標記
        plot.clearAnnotations();
        
        // 根據篩選條件添加標記
        List<TradeMarker> filteredMarkers = markers.stream()
            .filter(this::shouldShowMarker)
            .collect(Collectors.toList());
        
        // 添加到圖表
        for (TradeMarker marker : filteredMarkers) {
            for (org.jfree.chart.annotations.XYShapeAnnotation annotation : marker.createShapeAnnotations()) {
                plot.addAnnotation(annotation);
            }
            for (org.jfree.chart.annotations.XYTextAnnotation annotation : marker.createTextAnnotations()) {
                plot.addAnnotation(annotation);
            }
        }
        
        chartPanel.repaint();
    }
    
    /**
     * 判斷是否應該顯示標記
     */
    private boolean shouldShowMarker(TradeMarker marker) {
        Trade trade = marker.getTrade();
        
        // 類型篩選
        if (trade.getType() == TradeType.BUY && !showBuyMarkers) {
            return false;
        }
        if (trade.getType() == TradeType.SELL && !showSellMarkers) {
            return false;
        }
        
        // 文字搜尋篩選
        if (!searchText.isEmpty()) {
            String lowerSearch = searchText.toLowerCase();
            String symbol = trade.getSymbol().toLowerCase();
            String exitReason = trade.getExitReason() != null ? trade.getExitReason().toLowerCase() : "";
            
            if (!symbol.contains(lowerSearch) && !exitReason.contains(lowerSearch)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 設置買入標記顯示狀態
     */
    public void setShowBuyMarkers(boolean show) {
        this.showBuyMarkers = show;
        applyFilter();
    }
    
    /**
     * 設置賣出標記顯示狀態
     */
    public void setShowSellMarkers(boolean show) {
        this.showSellMarkers = show;
        applyFilter();
    }
    
    /**
     * 設置搜尋文字
     */
    public void setSearchText(String text) {
        this.searchText = text != null ? text : "";
        applyFilter();
    }
    
    /**
     * 清除所有標記
     */
    public void clearMarkers() {
        markers.clear();
        allTrades.clear();
        plot.clearAnnotations();
        chartPanel.repaint();
    }
    
    /**
     * 顯示篩選對話框
     */
    public void showFilterDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(chartPanel), "交易標記篩選", true);
        dialog.setLayout(new BorderLayout(10, 10));
        
        // 創建篩選面板
        JPanel filterPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 買入標記複選框
        JCheckBox buyCheckBox = new JCheckBox("顯示買入標記 (B)", showBuyMarkers);
        buyCheckBox.addActionListener(e -> {
            showBuyMarkers = buyCheckBox.isSelected();
            applyFilter();
        });
        
        // 賣出標記複選框
        JCheckBox sellCheckBox = new JCheckBox("顯示賣出標記 (S)", showSellMarkers);
        sellCheckBox.addActionListener(e -> {
            showSellMarkers = sellCheckBox.isSelected();
            applyFilter();
        });
        
        // 搜尋文字框
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel("搜尋:"), BorderLayout.WEST);
        JTextField searchField = new JTextField(searchText);
        searchField.addActionListener(e -> {
            searchText = searchField.getText();
            applyFilter();
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // 統計信息
        int buyCount = (int) allTrades.stream().filter(t -> t.getType() == TradeType.BUY).count();
        int sellCount = (int) allTrades.stream().filter(t -> t.getType() == TradeType.SELL).count();
        JLabel statsLabel = new JLabel(String.format("總計: %d 筆交易 (買入: %d, 賣出: %d)", 
            allTrades.size(), buyCount, sellCount));
        
        filterPanel.add(buyCheckBox);
        filterPanel.add(sellCheckBox);
        filterPanel.add(searchPanel);
        filterPanel.add(statsLabel);
        
        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("關閉");
        closeButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeButton);
        
        dialog.add(filterPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.pack();
        dialog.setLocationRelativeTo(chartPanel);
        dialog.setVisible(true);
    }
    
    /**
     * 顯示標記右鍵菜單
     */
    private void showContextMenu(Trade trade, Point point) {
        JPopupMenu popup = new JPopupMenu();
        
        // 標題
        JMenuItem titleItem = new JMenuItem(trade.getType().getDisplayName() + " 交易");
        titleItem.setEnabled(false);
        titleItem.setFont(titleItem.getFont().deriveFont(Font.BOLD));
        popup.add(titleItem);
        popup.addSeparator();
        
        // 查看詳情
        JMenuItem detailsItem = new JMenuItem("查看交易詳情");
        detailsItem.addActionListener(e -> {
            if (selectionListener != null) {
                selectionListener.onTradeSelected(trade);
            }
        });
        popup.add(detailsItem);
        
        // 複製信息
        JMenuItem copyItem = new JMenuItem("複製交易信息");
        copyItem.addActionListener(e -> copyTradeInfo(trade));
        popup.add(copyItem);
        
        popup.addSeparator();
        
        // 隱藏此類型
        String hideLabel = trade.getType() == TradeType.BUY ? "隱藏所有買入標記" : "隱藏所有賣出標記";
        JMenuItem hideItem = new JMenuItem(hideLabel);
        hideItem.addActionListener(e -> {
            if (trade.getType() == TradeType.BUY) {
                setShowBuyMarkers(false);
            } else {
                setShowSellMarkers(false);
            }
        });
        popup.add(hideItem);
        
        // 顯示篩選對話框
        JMenuItem filterItem = new JMenuItem("篩選設定...");
        filterItem.addActionListener(e -> showFilterDialog());
        popup.add(filterItem);
        
        popup.show(chartPanel, point.x, point.y);
    }
    
    /**
     * 複製交易信息到剪貼板
     */
    private void copyTradeInfo(Trade trade) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("交易類型: %s\n", trade.getType().getDisplayName()));
        info.append(String.format("時間: %s\n", trade.getTimestamp()));
        info.append(String.format("商品: %s\n", trade.getSymbol()));
        info.append(String.format("價格: %.2f\n", trade.getPrice()));
        info.append(String.format("數量: %d\n", trade.getQuantity()));
        info.append(String.format("金額: %.2f\n", trade.getTotalAmount()));
        
        if (trade.getStopLoss() != null) {
            info.append(String.format("停損: %.2f\n", trade.getStopLoss()));
        }
        if (trade.getTakeProfit() != null) {
            info.append(String.format("停利: %.2f\n", trade.getTakeProfit()));
        }
        if (trade.getExitReason() != null) {
            info.append(String.format("原因: %s\n", trade.getExitReason()));
        }
        
        java.awt.datatransfer.StringSelection selection = 
            new java.awt.datatransfer.StringSelection(info.toString());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        
        JOptionPane.showMessageDialog(chartPanel, "交易信息已複製到剪貼板", "提示", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * 查找點擊位置附近的交易
     */
    private Trade findTradeNearPoint(double x, double y) {
        final double threshold = 20.0; // 像素閾值
        
        for (TradeMarker marker : markers) {
            if (!shouldShowMarker(marker)) {
                continue;
            }
            
            Trade trade = marker.getTrade();
            long tradeTime = marker.getTimePeriod().getFirstMillisecond();
            double tradePrice = marker.getPrice();
            
            // 計算距離
            double dx = Math.abs(tradeTime - x);
            double dy = Math.abs(tradePrice - y) * 100; // 調整Y軸比例
            
            if (dx < threshold && dy < threshold) {
                return trade;
            }
        }
        
        return null;
    }
    
    // ChartMouseListener 實現
    
    @Override
    public void chartMouseClicked(ChartMouseEvent event) {
        MouseEvent trigger = event.getTrigger();
        
        // 獲取點擊位置的數據坐標
        Point2D p = chartPanel.translateScreenToJava2D(trigger.getPoint());
        Rectangle2D plotArea = chartPanel.getScreenDataArea();
        
        double x = plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
        double y = plot.getRangeAxis().java2DToValue(p.getY(), plotArea, plot.getRangeAxisEdge());
        
        Trade trade = findTradeNearPoint(x, y);
        
        if (trade != null) {
            if (SwingUtilities.isRightMouseButton(trigger)) {
                // 右鍵：顯示上下文菜單
                showContextMenu(trade, trigger.getPoint());
            } else if (SwingUtilities.isLeftMouseButton(trigger)) {
                // 左鍵：觸發選擇事件
                if (selectionListener != null) {
                    selectionListener.onTradeSelected(trade);
                }
            }
        }
    }
    
    @Override
    public void chartMouseMoved(ChartMouseEvent event) {
        // 可以在這裡實現懸停提示功能
    }
}

