package com.dreamhouse.trading.core.backtest;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 績效圖表類
 * 用於生成收益曲線和回撤曲線圖表
 */
public class PerformanceChart {
    
    /**
     * 創建收益曲線圖表
     */
    public static ChartPanel createEquityChart(BacktestResult result) {
        TimeSeries equitySeries = new TimeSeries("資產淨值");
        TimeSeries benchmarkSeries = new TimeSeries("基準線");
        
        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        double initialValue = result.getInitialCapital();
        
        System.out.println("[圖表] 創建資產淨值圖表，快照數量: " + snapshots.size());
        
        for (PortfolioSnapshot snapshot : snapshots) {
            Date date = Date.from(snapshot.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            Day day = new Day(date);
            
            // 使用 addOrUpdate 避免重複時間點錯誤
            equitySeries.addOrUpdate(day, snapshot.getTotalValue());
            benchmarkSeries.addOrUpdate(day, initialValue); // 基準線保持初始資金不變
        }
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(equitySeries);
        dataset.addSeries(benchmarkSeries);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            "資產淨值曲線",
            "時間",
            "資產價值",
            dataset,
            true,
            true,
            false
        );
        
        // 自定義圖表樣式
        customizeChart(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(0, 120, 215)); // 藍色
        renderer.setSeriesPaint(1, Color.GRAY); // 灰色基準線
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(1.0f, BasicStroke.CAP_BUTT, 
                                                   BasicStroke.JOIN_MITER, 10.0f, 
                                                   new float[]{5.0f}, 0.0f)); // 虛線
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        plot.setRenderer(renderer);
        
        return new ChartPanel(chart);
    }
    
    /**
     * 創建回撤曲線圖表
     */
    public static ChartPanel createDrawdownChart(BacktestResult result) {
        TimeSeries drawdownSeries = new TimeSeries("回撤");
        
        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        double peak = result.getInitialCapital();
        
        for (PortfolioSnapshot snapshot : snapshots) {
            Date date = Date.from(snapshot.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            Day day = new Day(date);
            
            double currentValue = snapshot.getTotalValue();
            if (currentValue > peak) {
                peak = currentValue;
            }
            
            double drawdown = (peak - currentValue) / peak * 100; // 轉換為百分比
            drawdownSeries.addOrUpdate(day, -drawdown); // 負值顯示，使用 addOrUpdate 避免重複
        }
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(drawdownSeries);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            "回撤曲線",
            "時間",
            "回撤 (%)",
            dataset,
            false,
            true,
            false
        );
        
        // 自定義圖表樣式
        customizeChart(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(220, 53, 69)); // 紅色
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, false);
        plot.setRenderer(renderer);
        
        // 設定Y軸範圍（回撤總是負值或零）
        plot.getRangeAxis().setUpperBound(1.0);
        
        return new ChartPanel(chart);
    }
    
    /**
     * 創建收益分布直方圖
     */
    public static ChartPanel createReturnsHistogram(BacktestResult result) {
        // 計算日收益率
        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        if (snapshots.size() < 2) {
            return createEmptyChart("收益分布", "收益率 (%)", "頻率");
        }
        
        // 創建收益率數據
        TimeSeries returnsSeries = new TimeSeries("日收益率");
        
        for (int i = 1; i < snapshots.size(); i++) {
            PortfolioSnapshot prev = snapshots.get(i - 1);
            PortfolioSnapshot curr = snapshots.get(i);
            
            double dailyReturn = (curr.getTotalValue() - prev.getTotalValue()) / prev.getTotalValue() * 100;
            
            Date date = Date.from(curr.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            Day day = new Day(date);
            
            returnsSeries.addOrUpdate(day, dailyReturn); // 使用 addOrUpdate 避免重複時間點
        }
        
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(returnsSeries);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            "日收益率分布",
            "時間",
            "收益率 (%)",
            dataset,
            false,
            true,
            false
        );
        
        customizeChart(chart);
        
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(40, 167, 69)); // 綠色
        renderer.setSeriesStroke(0, new BasicStroke(1.5f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-2, -2, 4, 4));
        plot.setRenderer(renderer);
        
        // 添加零線
        plot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(0.0, Color.BLACK, 
                           new BasicStroke(1.0f)));
        
        return new ChartPanel(chart);
    }
    
    /**
     * 創建組合圖表面板
     */
    public static JPanel createCombinedChartsPanel(BacktestResult result) {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 創建選項卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 添加各種圖表
        tabbedPane.addTab("資產淨值", createEquityChart(result));
        tabbedPane.addTab("回撤曲線", createDrawdownChart(result));
        tabbedPane.addTab("收益分布", createReturnsHistogram(result));
        
        panel.add(tabbedPane, BorderLayout.CENTER);
        
        // 添加統計信息面板
        JPanel statsPanel = createStatsPanel(result);
        panel.add(statsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 創建統計信息面板
     */
    private static JPanel createStatsPanel(BacktestResult result) {
        JPanel panel = new JPanel(new GridLayout(2, 4, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("關鍵指標"));
        
        // 第一行
        panel.add(createStatLabel("總收益率", String.format("%.2f%%", result.getTotalReturn() * 100)));
        panel.add(createStatLabel("年化收益率", String.format("%.2f%%", result.getAnnualizedReturn() * 100)));
        panel.add(createStatLabel("最大回撤", String.format("%.2f%%", result.getMaxDrawdown() * 100)));
        panel.add(createStatLabel("夏普比率", String.format("%.2f", result.getSharpeRatio())));
        
        // 第二行
        panel.add(createStatLabel("總交易次數", String.valueOf(result.getTotalTrades())));
        panel.add(createStatLabel("勝率", String.format("%.1f%%", result.getWinRate() * 100)));
        panel.add(createStatLabel("盈虧比", String.format("%.2f", result.getProfitFactor())));
        panel.add(createStatLabel("波動率", String.format("%.2f%%", result.getVolatility() * 100)));
        
        return panel;
    }
    
    /**
     * 創建統計標籤
     */
    private static JPanel createStatLabel(String title, String value) {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        titleLabel.setForeground(Color.GRAY);
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 14f));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 自定義圖表樣式 - 黑色主題
     */
    private static void customizeChart(JFreeChart chart) {
        // 黑色背景主題
        Color backgroundColor = new Color(43, 43, 43); // 深灰色背景
        Color gridColor = new Color(80, 80, 80); // 網格線顏色
        Color textColor = Color.WHITE; // 文字顏色
        
        chart.setBackgroundPaint(backgroundColor);
        chart.getTitle().setFont(new Font("微軟正黑體", Font.BOLD, 16));
        chart.getTitle().setPaint(textColor);
        
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(backgroundColor);
        plot.setDomainGridlinePaint(gridColor);
        plot.setRangeGridlinePaint(gridColor);
        plot.setOutlinePaint(gridColor);
        
        // 設定軸顏色
        plot.getDomainAxis().setLabelPaint(textColor);
        plot.getDomainAxis().setTickLabelPaint(textColor);
        plot.getRangeAxis().setLabelPaint(textColor);
        plot.getRangeAxis().setTickLabelPaint(textColor);
        
        // 設定日期軸格式
        if (plot.getDomainAxis() instanceof DateAxis) {
            DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
            dateAxis.setDateFormatOverride(new java.text.SimpleDateFormat("MM/dd HH:mm"));
        }
        
        // 設定圖例顏色
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(backgroundColor);
            chart.getLegend().setItemPaint(textColor);
        }
    }
    
    /**
     * 創建空圖表
     */
    private static ChartPanel createEmptyChart(String title, String xLabel, String yLabel) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title, xLabel, yLabel, dataset, false, true, false);
        customizeChart(chart);
        return new ChartPanel(chart);
    }
}
