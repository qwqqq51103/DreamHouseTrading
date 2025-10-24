package com.dreamhouse.trading.ui;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class ChartView extends JPanel {
    private static final int MAX_BARS = 120;
    private static final int UPDATE_INTERVAL_MS = 1000; // 1 秒更新一次

    // 週期定義（分鐘數，0 表示日線）
    private enum Period {
        M1("1分", 1),
        M5("5分", 5),
        M15("15分", 15),
        M30("30分", 30),
        M60("60分", 60),
        D1("日線", 1440);

        final String label;
        final int minutes;

        Period(String label, int minutes) {
            this.label = label;
            this.minutes = minutes;
        }
    }

    private final Random random = new Random();
    
    // 原始 tick 資料（1分鐘）
    private final List<TickData> rawTicks = new ArrayList<>();
    
    // 當前週期的聚合資料
    private BarSeries barSeries;
    private final OHLCSeries ohlcSeries;
    private final TimeSeries smaSeries;
    private final TimeSeries volumeSeries;
    private final OHLCSeriesCollection ohlcDataset;
    private final TimeSeriesCollection smaDataset;
    private final TimeSeriesCollection volumeDataset;

    private double lastClose = 100.0;
    private long currentTimeMillis;
    private Period currentPeriod = Period.M1;
    
    private XYPlot pricePlot;
    private XYPlot volumePlot;
    private ChartPanel chartPanel;

    public ChartView() {
        setLayout(new BorderLayout());

        // 初始化資料結構
        barSeries = new BaseBarSeries("Stock");
        barSeries.setMaximumBarCount(MAX_BARS);
        ohlcSeries = new OHLCSeries("Price");
        ohlcSeries.setMaximumItemCount(MAX_BARS);
        smaSeries = new TimeSeries("SMA(20)");
        volumeSeries = new TimeSeries("Volume");
        ohlcDataset = new OHLCSeriesCollection();
        smaDataset = new TimeSeriesCollection();
        volumeDataset = new TimeSeriesCollection();

        ohlcDataset.addSeries(ohlcSeries);
        smaDataset.addSeries(smaSeries);
        volumeDataset.addSeries(volumeSeries);

        // 建立初始 120 根 1 分鐘 tick
        currentTimeMillis = System.currentTimeMillis() - (MAX_BARS * 60_000L);
        for (int i = 0; i < MAX_BARS; i++) {
            addNewTick();
            currentTimeMillis += 60_000;
        }

        // 聚合為當前週期並建立圖表
        aggregateBars();
        createChart();
        createTopPanel();

        // 啟動定時更新
        Timer updateTimer = new Timer(UPDATE_INTERVAL_MS, e -> updateChart());
        updateTimer.start();
    }

    /**
     * 建立頂部控制面板（週期選擇器）
     */
    private void createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topPanel.add(new JLabel("週期:"));

        ButtonGroup group = new ButtonGroup();
        for (Period period : Period.values()) {
            JRadioButton rb = new JRadioButton(period.label, period == currentPeriod);
            rb.addActionListener(e -> {
                currentPeriod = period;
                aggregateBars();
                recalculateSMA();
                updateVolumeData();
            });
            group.add(rb);
            topPanel.add(rb);
        }

        add(topPanel, BorderLayout.NORTH);
    }

    /**
     * 建立組合圖表（上方 K 線 + SMA，下方成交量）
     */
    private void createChart() {
        // === 價格圖（上方）===
        DateAxis priceTimeAxis = new DateAxis("Time");
        NumberAxis priceAxis = new NumberAxis("Price");
        priceAxis.setAutoRangeIncludesZero(false);

        // K 線渲染器
        CandlestickRenderer candleRenderer = new CandlestickRenderer();
        candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        candleRenderer.setUpPaint(new Color(34, 177, 76));   // 綠漲
        candleRenderer.setDownPaint(new Color(237, 28, 36)); // 紅跌

        pricePlot = new XYPlot(ohlcDataset, priceTimeAxis, priceAxis, candleRenderer);

        // SMA 線
        pricePlot.setDataset(1, smaDataset);
        XYLineAndShapeRenderer smaRenderer = new XYLineAndShapeRenderer(true, false);
        smaRenderer.setSeriesPaint(0, new Color(255, 215, 0));
        smaRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        pricePlot.setRenderer(1, smaRenderer);
        pricePlot.mapDatasetToRangeAxis(1, 0);

        // 啟用十字線
        pricePlot.setDomainCrosshairVisible(true);
        pricePlot.setRangeCrosshairVisible(true);
        pricePlot.setDomainCrosshairPaint(Color.GRAY);
        pricePlot.setRangeCrosshairPaint(Color.GRAY);
        pricePlot.setDomainCrosshairStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 3}, 0));
        pricePlot.setRangeCrosshairStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5, 3}, 0));

        pricePlot.setBackgroundPaint(new Color(30, 30, 30));
        pricePlot.setDomainGridlinePaint(new Color(60, 60, 60));
        pricePlot.setRangeGridlinePaint(new Color(60, 60, 60));

        // === 成交量圖（下方）===
        NumberAxis volumeAxis = new NumberAxis("Volume");
        volumeAxis.setAutoRangeIncludesZero(true);

        XYBarRenderer volumeRenderer = new XYBarRenderer();
        volumeRenderer.setSeriesPaint(0, new Color(100, 150, 200, 180));
        volumeRenderer.setShadowVisible(false);

        volumePlot = new XYPlot(volumeDataset, null, volumeAxis, volumeRenderer);
        volumePlot.setBackgroundPaint(new Color(30, 30, 30));
        volumePlot.setDomainGridlinePaint(new Color(60, 60, 60));
        volumePlot.setRangeGridlinePaint(new Color(60, 60, 60));

        // === 組合圖表 ===
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(new DateAxis("Time"));
        combinedPlot.setGap(10.0);
        combinedPlot.add(pricePlot, 3);    // 價格圖占 3 份
        combinedPlot.add(volumePlot, 1);   // 成交量占 1 份
        combinedPlot.setOrientation(org.jfree.chart.plot.PlotOrientation.VERTICAL);

        JFreeChart chart = new JFreeChart(
                "K Line (Realtime) with SMA(20) & Volume",
                JFreeChart.DEFAULT_TITLE_FONT,
                combinedPlot,
                false);

        chart.setBackgroundPaint(new Color(40, 40, 40));

        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setDisplayToolTips(true);

        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * 新增一筆原始 tick（1分鐘）
     */
    private void addNewTick() {
        double open = lastClose + (random.nextDouble() - 0.5) * 0.6;
        double close = open + (random.nextDouble() - 0.5) * 1.2;
        double high = Math.max(open, close) + random.nextDouble() * 0.5;
        double low = Math.min(open, close) - random.nextDouble() * 0.5;
        double volume = 1000 + random.nextInt(4000);

        open = round2(open);
        high = round2(high);
        low = round2(low);
        close = round2(close);
        lastClose = close;

        TickData tick = new TickData(currentTimeMillis, open, high, low, close, volume);
        rawTicks.add(tick);

        // 維持固定數量（保留最近 MAX_BARS * 最大週期倍數）
        int maxKeep = MAX_BARS * 1440; // 日線最大，保留足夠資料
        while (rawTicks.size() > maxKeep) {
            rawTicks.remove(0);
        }
    }

    /**
     * 根據當前週期聚合 K 線
     */
    private void aggregateBars() {
        ohlcSeries.clear();
        
        // 重新建立 BarSeries（因為沒有 clear 方法）
        barSeries = new BaseBarSeries("Stock");
        barSeries.setMaximumBarCount(MAX_BARS);

        if (rawTicks.isEmpty()) return;

        int periodMinutes = currentPeriod.minutes;
        List<TickData> currentBucket = new ArrayList<>();
        long bucketStartTime = 0;

        for (TickData tick : rawTicks) {
            long tickBucketStart = (tick.timestamp / (periodMinutes * 60_000L)) * (periodMinutes * 60_000L);

            if (bucketStartTime == 0) {
                bucketStartTime = tickBucketStart;
            }

            if (tickBucketStart != bucketStartTime) {
                // 產生聚合 K 線
                if (!currentBucket.isEmpty()) {
                    addAggregatedBar(bucketStartTime, currentBucket, periodMinutes);
                }
                currentBucket.clear();
                bucketStartTime = tickBucketStart;
            }

            currentBucket.add(tick);
        }

        // 最後一個 bucket
        if (!currentBucket.isEmpty()) {
            addAggregatedBar(bucketStartTime, currentBucket, periodMinutes);
        }
    }

    /**
     * 加入聚合後的 K 線
     */
    private void addAggregatedBar(long timestamp, List<TickData> ticks, int periodMinutes) {
        double open = ticks.get(0).open;
        double close = ticks.get(ticks.size() - 1).close;
        double high = ticks.stream().mapToDouble(t -> t.high).max().orElse(0);
        double low = ticks.stream().mapToDouble(t -> t.low).min().orElse(0);
        double volume = ticks.stream().mapToDouble(t -> t.volume).sum();

        Date barDate = new Date(timestamp);
        RegularTimePeriod period = new Minute(barDate);

        ohlcSeries.add(period, open, high, low, close);

        ZonedDateTime zdt = ZonedDateTime.ofInstant(barDate.toInstant(), ZoneId.systemDefault());
        Bar bar = new BaseBar(
                Duration.ofMinutes(periodMinutes),
                zdt,
                BigDecimal.valueOf(open),
                BigDecimal.valueOf(high),
                BigDecimal.valueOf(low),
                BigDecimal.valueOf(close),
                BigDecimal.valueOf(volume)
        );
        barSeries.addBar(bar);
    }

    /**
     * 更新成交量資料
     */
    private void updateVolumeData() {
        volumeSeries.clear();

        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            Date barDate = Date.from(bar.getEndTime().toInstant());
            RegularTimePeriod period = new Minute(barDate);
            double vol = bar.getVolume().doubleValue();
            volumeSeries.addOrUpdate(period, vol);
        }
    }

    /**
     * 定時更新圖表（每秒新增一根 1 分鐘 tick）
     */
    private void updateChart() {
        currentTimeMillis += 60_000;
        addNewTick();
        aggregateBars();
        recalculateSMA();
        updateVolumeData();
    }

    /**
     * 重新計算 SMA(20)
     */
    private void recalculateSMA() {
        smaSeries.clear();

        if (barSeries.getBarCount() < 20) {
            return;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);

        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            Date barDate = Date.from(bar.getEndTime().toInstant());
            RegularTimePeriod period = new Minute(barDate);

            if (!sma20.getValue(i).isNaN()) {
                double smaValue = sma20.getValue(i).doubleValue();
                smaSeries.addOrUpdate(period, smaValue);
            }
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 原始 tick 資料結構
     */
    private static class TickData {
        long timestamp;
        double open, high, low, close, volume;

        TickData(long timestamp, double open, double high, double low, double close, double volume) {
            this.timestamp = timestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}
