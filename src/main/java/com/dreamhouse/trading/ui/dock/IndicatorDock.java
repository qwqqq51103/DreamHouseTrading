package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.IndicatorService;
import com.dreamhouse.trading.util.I18n;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class IndicatorDock extends JPanel {
    public enum IndicatorType { RSI, MACD }
    
    private IndicatorType currentType = IndicatorType.RSI;
    private IndicatorService indicatorService;
    private ChartPanel chartPanel;
    
    public IndicatorDock() {
        setLayout(new BorderLayout());
        createRSIChart();
    }
    
    public void setIndicatorService(IndicatorService service) {
        this.indicatorService = service;
        updateChart();
    }
    
    public void setIndicatorType(IndicatorType type) {
        this.currentType = type;
        if (type == IndicatorType.RSI) {
            createRSIChart();
        } else {
            createMACDChart();
        }
        updateChart();
    }
    
    private void createRSIChart() {
        TimeSeries rsiSeries = new TimeSeries(I18n.get("indicator.rsi.title"));
        TimeSeriesCollection dataset = new TimeSeriesCollection(rsiSeries);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                I18n.get("indicator.rsi.title"),
                I18n.get("chart.time"),
                "RSI",
                dataset,
                false,
                true,
                false
        );
        
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(30, 30, 30));
        plot.setDomainGridlinePaint(new Color(60, 60, 60));
        plot.setRangeGridlinePaint(new Color(60, 60, 60));
        
        // RSI 線
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(255, 165, 0)); // 橘色
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(renderer);
        
        // 設定 Y 軸範圍 0-100
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0, 100);
        
        chart.setBackgroundPaint(new Color(40, 40, 40));
        
        if (chartPanel != null) {
            remove(chartPanel);
        }
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        add(chartPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
    
    private void createMACDChart() {
        TimeSeries macdSeries = new TimeSeries("MACD");
        TimeSeries signalSeries = new TimeSeries("Signal");
        TimeSeries histogramSeries = new TimeSeries("Histogram");
        
        TimeSeriesCollection lineDataset = new TimeSeriesCollection();
        lineDataset.addSeries(macdSeries);
        lineDataset.addSeries(signalSeries);
        
        TimeSeriesCollection barDataset = new TimeSeriesCollection(histogramSeries);
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                I18n.get("indicator.macd.title"),
                I18n.get("chart.time"),
                "MACD",
                lineDataset,
                false,
                true,
                false
        );
        
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(30, 30, 30));
        plot.setDomainGridlinePaint(new Color(60, 60, 60));
        plot.setRangeGridlinePaint(new Color(60, 60, 60));
        
        // MACD 線
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, new Color(0, 191, 255));  // MACD 天藍色
        lineRenderer.setSeriesPaint(1, new Color(255, 140, 0));  // Signal 橘色
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        lineRenderer.setSeriesStroke(1, new BasicStroke(1.5f));
        plot.setRenderer(0, lineRenderer);
        
        // Histogram 柱狀圖
        plot.setDataset(1, barDataset);
        XYBarRenderer barRenderer = new XYBarRenderer();
        barRenderer.setSeriesPaint(0, new Color(100, 150, 200, 180));
        barRenderer.setShadowVisible(false);
        barRenderer.setBarPainter(new StandardXYBarPainter());
        plot.setRenderer(1, barRenderer);
        plot.mapDatasetToRangeAxis(1, 0);
        
        chart.setBackgroundPaint(new Color(40, 40, 40));
        
        if (chartPanel != null) {
            remove(chartPanel);
        }
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        add(chartPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
    
    public void updateChart() {
        if (indicatorService == null || chartPanel == null) return;
        
        SwingUtilities.invokeLater(() -> {
            if (currentType == IndicatorType.RSI) {
                updateRSI();
            } else {
                updateMACD();
            }
        });
    }
    
    private void updateRSI() {
        List<Double> rsiValues = indicatorService.getRSI(14);
        
        XYPlot plot = chartPanel.getChart().getXYPlot();
        TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(0);
        TimeSeries rsiSeries = dataset.getSeries(0);
        rsiSeries.clear();
        
        org.ta4j.core.BarSeries barSeries = indicatorService.getBarSeries();
        for (int i = 0; i < rsiValues.size() && i < barSeries.getBarCount(); i++) {
            if (rsiValues.get(i) != null) {
                org.ta4j.core.Bar bar = barSeries.getBar(i);
                Date date = Date.from(bar.getEndTime().toInstant());
                org.jfree.data.time.Minute period = new org.jfree.data.time.Minute(date);
                rsiSeries.addOrUpdate(period, rsiValues.get(i));
            }
        }
    }
    
    private void updateMACD() {
        Map<String, List<Double>> macdData = indicatorService.getMACD(12, 26, 9);
        List<Double> macdLine = macdData.get("macd");
        List<Double> signalLine = macdData.get("signal");
        List<Double> histogram = macdData.get("histogram");
        
        XYPlot plot = chartPanel.getChart().getXYPlot();
        TimeSeriesCollection lineDataset = (TimeSeriesCollection) plot.getDataset(0);
        TimeSeriesCollection barDataset = (TimeSeriesCollection) plot.getDataset(1);
        
        TimeSeries macdSeries = lineDataset.getSeries(0);
        TimeSeries signalSeries = lineDataset.getSeries(1);
        TimeSeries histogramSeries = barDataset.getSeries(0);
        
        macdSeries.clear();
        signalSeries.clear();
        histogramSeries.clear();
        
        org.ta4j.core.BarSeries barSeries = indicatorService.getBarSeries();
        for (int i = 0; i < macdLine.size() && i < barSeries.getBarCount(); i++) {
            org.ta4j.core.Bar bar = barSeries.getBar(i);
            Date date = Date.from(bar.getEndTime().toInstant());
            org.jfree.data.time.Minute period = new org.jfree.data.time.Minute(date);
            
            macdSeries.addOrUpdate(period, macdLine.get(i));
            signalSeries.addOrUpdate(period, signalLine.get(i));
            histogramSeries.addOrUpdate(period, histogram.get(i));
        }
    }
}

