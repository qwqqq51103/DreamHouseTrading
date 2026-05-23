package com.dreamhouse.trading.ui.dock;

import com.dreamhouse.trading.core.*;
import com.dreamhouse.trading.core.model.*;
import com.dreamhouse.trading.core.backtest.Trade;
import com.dreamhouse.trading.ui.chart.TradeMarker;
import com.dreamhouse.trading.ui.chart.TradeMarkerManager;
import com.dreamhouse.trading.util.I18n;
import com.dreamhouse.trading.util.BarAggregator;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Day;
import org.jfree.data.time.Week;
import org.jfree.data.time.Month;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartDock extends JPanel implements MarketDataListener {
    private static final int MAX_BARS = 500;  // 最多儲存 500 根 K 線
    
    public final IndicatorService indicatorService;
    private final OHLCSeries ohlcSeries;
    private final TimeSeries smaSeries;
    private final TimeSeries emaSeries;
    private final TimeSeries volumeSeries;
    private final OHLCSeriesCollection ohlcDataset;
    private final TimeSeriesCollection overlayDataset;
    private final TimeSeriesCollection volumeDataset;
    
    // 技術指標相關
    private final TimeSeries rsiSeries;
    private final TimeSeries macdSeries;
    private final TimeSeries signalSeries;
    private final TimeSeries histogramSeries;
    private final TimeSeriesCollection rsiDataset;
    private final TimeSeriesCollection macdLineDataset;
    private final TimeSeriesCollection macdBarDataset;
    
    // 新增指標系列
    private final TimeSeries bollUpperSeries;
    private final TimeSeries bollMiddleSeries;
    private final TimeSeries bollLowerSeries;
    private final TimeSeries kdKSeries;
    private final TimeSeries kdDSeries;
    private final TimeSeries obvSeries;
    private final TimeSeries adxSeries;
    private final TimeSeries plusDISeries;
    private final TimeSeries minusDISeries;
    private final TimeSeries cciSeries;
    private final TimeSeries wrSeries;
    private final TimeSeriesCollection bollDataset;
    private final TimeSeriesCollection kdDataset;
    private final TimeSeriesCollection obvDataset;
    private final TimeSeriesCollection adxDataset;
    private final TimeSeriesCollection cciDataset;
    private final TimeSeriesCollection wrDataset;
    
    private XYPlot pricePlot;
    private XYPlot indicatorPlot;
    private CombinedDomainXYPlot combinedPlot;
    private ChartPanel chartPanel;
    
    // 繪圖工具
    
    // 交易標記
    private List<TradeMarker> tradeMarkers = new ArrayList<>();
    private TradeMarkerManager markerManager;

    // 其他 Dock 引用
    private TimeSalesDock timeSalesDock;
    private NewsDock newsDock;
    private WatchlistPanel watchlistPanel;

    // 當前商品和價格追蹤
    private String currentSymbol = "";
    private double dayOpenPrice = 0.0;  // 當日開盤價（用於計算漲跌%）
    private long totalVolume = 0;       // 累積成交量

    private Timeframe currentTimeframe = Timeframe.M1;
    private String currentOverlayIndicator = "SMA";  // SMA/EMA/None
    private String currentSubIndicator = "RSI";      // RSI/MACD/None

    // 指標參數配置
    private final Map<String, IndicatorConfig> indicatorConfigs = new HashMap<>();

    private double lastOpen = 100.0;
    private double lastClose = 100.0;
    private double lastHigh = 100.0;
    private double lastLow = 100.0;
    private long lastVolume = 0;
    private LocalDateTime lastBarTime = null;

    
    // 時間週期類型（根據數據自動檢測）
    private enum TimeFrame {
        MINUTE, HOUR, DAY, WEEK, MONTH
    }
    private TimeFrame detectedTimeFrame = TimeFrame.MINUTE;
    
    // 主題顏色（會根據 FlatLaf 主題自動調整）
    private Color plotBackgroundColor;
    private Color gridLineColor;
    private Color chartBackgroundColor;
    
    public ChartDock() {
        setLayout(new BorderLayout());
        
        // 初始化主題顏色
        updateThemeColors();
        
        // 初始化預設指標配置
        initializeDefaultConfigs();
        
        // 初始化資料結構
        indicatorService = new IndicatorService("Chart", MAX_BARS);
        ohlcSeries = new OHLCSeries("Price");
        ohlcSeries.setMaximumItemCount(MAX_BARS);
        smaSeries = new TimeSeries("SMA(20)");
        smaSeries.setMaximumItemCount(MAX_BARS);
        emaSeries = new TimeSeries("EMA(20)");
        emaSeries.setMaximumItemCount(MAX_BARS);
        volumeSeries = new TimeSeries(I18n.get("chart.volume"));
        volumeSeries.setMaximumItemCount(MAX_BARS);
        
        // 技術指標
        rsiSeries = new TimeSeries(I18n.get("indicator.rsi.title"));
        rsiSeries.setMaximumItemCount(MAX_BARS);
        macdSeries = new TimeSeries("MACD");
        macdSeries.setMaximumItemCount(MAX_BARS);
        signalSeries = new TimeSeries("Signal");
        signalSeries.setMaximumItemCount(MAX_BARS);
        histogramSeries = new TimeSeries("Histogram");
        histogramSeries.setMaximumItemCount(MAX_BARS);
        
        // 新增指標初始化
        bollUpperSeries = new TimeSeries("Upper");
        bollUpperSeries.setMaximumItemCount(MAX_BARS);
        bollMiddleSeries = new TimeSeries("Middle");
        bollMiddleSeries.setMaximumItemCount(MAX_BARS);
        bollLowerSeries = new TimeSeries("Lower");
        bollLowerSeries.setMaximumItemCount(MAX_BARS);
        
        kdKSeries = new TimeSeries("%K");
        kdKSeries.setMaximumItemCount(MAX_BARS);
        kdDSeries = new TimeSeries("%D");
        kdDSeries.setMaximumItemCount(MAX_BARS);
        
        obvSeries = new TimeSeries("OBV");
        obvSeries.setMaximumItemCount(MAX_BARS);
        
        adxSeries = new TimeSeries("ADX");
        adxSeries.setMaximumItemCount(MAX_BARS);
        plusDISeries = new TimeSeries("+DI");
        plusDISeries.setMaximumItemCount(MAX_BARS);
        minusDISeries = new TimeSeries("-DI");
        minusDISeries.setMaximumItemCount(MAX_BARS);
        
        cciSeries = new TimeSeries("CCI");
        cciSeries.setMaximumItemCount(MAX_BARS);
        
        wrSeries = new TimeSeries("Williams %R");
        wrSeries.setMaximumItemCount(MAX_BARS);
        
        ohlcDataset = new OHLCSeriesCollection();
        overlayDataset = new TimeSeriesCollection();
        volumeDataset = new TimeSeriesCollection();
        rsiDataset = new TimeSeriesCollection();
        macdLineDataset = new TimeSeriesCollection();
        macdBarDataset = new TimeSeriesCollection();
        
        bollDataset = new TimeSeriesCollection();
        kdDataset = new TimeSeriesCollection();
        obvDataset = new TimeSeriesCollection();
        adxDataset = new TimeSeriesCollection();
        cciDataset = new TimeSeriesCollection();
        wrDataset = new TimeSeriesCollection();
        
        ohlcDataset.addSeries(ohlcSeries);
        overlayDataset.addSeries(smaSeries);
        overlayDataset.addSeries(emaSeries);
        overlayDataset.addSeries(bollUpperSeries);
        overlayDataset.addSeries(bollMiddleSeries);
        overlayDataset.addSeries(bollLowerSeries);
        volumeDataset.addSeries(volumeSeries);
        rsiDataset.addSeries(rsiSeries);
        macdLineDataset.addSeries(macdSeries);
        macdLineDataset.addSeries(signalSeries);
        macdBarDataset.addSeries(histogramSeries);
        
        kdDataset.addSeries(kdKSeries);
        kdDataset.addSeries(kdDSeries);
        obvDataset.addSeries(obvSeries);
        adxDataset.addSeries(adxSeries);
        adxDataset.addSeries(plusDISeries);
        adxDataset.addSeries(minusDISeries);
        cciDataset.addSeries(cciSeries);
        wrDataset.addSeries(wrSeries);
        
        createChart();
    }
    
    private void createChart() {
        // === 價格圖（K線 + 成交量背景 + 指標疊線）===
        DateAxis priceTimeAxis = new DateAxis(I18n.get("chart.time"));
        priceTimeAxis.setLowerMargin(0.02);
        priceTimeAxis.setUpperMargin(0.02);
        
        NumberAxis priceAxis = new NumberAxis(I18n.get("chart.price"));
        priceAxis.setAutoRangeIncludesZero(false);
        
        // 成交量軸（在右側，作為背景）
        NumberAxis volumeAxis = new NumberAxis(I18n.get("chart.volume"));
        volumeAxis.setAutoRangeIncludesZero(true);
        
        // K 線渲染器
        CandlestickRenderer candleRenderer = new CandlestickRenderer();
        candleRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_AVERAGE);
        candleRenderer.setAutoWidthFactor(0.95);
        candleRenderer.setAutoWidthGap(0.02);
        candleRenderer.setDrawVolume(false);
        candleRenderer.setUpPaint(new Color(34, 177, 76));   // 綠漲
        candleRenderer.setDownPaint(new Color(237, 28, 36)); // 紅跌
        
        pricePlot = new XYPlot(ohlcDataset, priceTimeAxis, priceAxis, candleRenderer);
        pricePlot.setBackgroundPaint(plotBackgroundColor);
        pricePlot.setDomainGridlinePaint(gridLineColor);
        pricePlot.setRangeGridlinePaint(gridLineColor);
        
        // 成交量渲染器（作為背景，半透明，根據漲跌顯示顏色）
        final OHLCSeries ohlcSeriesRef = ohlcSeries; // 為匿名內部類創建 final 引用
        XYBarRenderer volumeRenderer = new XYBarRenderer(0.20) {
            @Override
            public Paint getItemPaint(int row, int column) {
                // 根據對應 K 線的漲跌決定顏色
                try {
                    if (column >= 0 && column < ohlcSeriesRef.getItemCount()) {
                        // 從 OHLCDataset 獲取數據
                        double open = ohlcDataset.getOpenValue(row, column);
                        double close = ohlcDataset.getCloseValue(row, column);
                        
                        if (close >= open) {
                            return new Color(34, 177, 76, 80); // 綠色半透明（漲）
                        } else {
                            return new Color(237, 28, 36, 80); // 紅色半透明（跌）
                        }
                    }
                } catch (Exception e) {
                    // 如果出錯，返回灰色
                }
                return new Color(100, 100, 100, 80); // 灰色半透明（預設）
            }
        };
        volumeRenderer.setShadowVisible(false);
        volumeRenderer.setBarPainter(new StandardXYBarPainter());
        
        // 將成交量添加到價格圖，使用右側軸
        pricePlot.setDataset(1, volumeDataset);
        pricePlot.setRenderer(1, volumeRenderer);
        pricePlot.setRangeAxis(1, volumeAxis);
        pricePlot.mapDatasetToRangeAxis(1, 1);
        
        // 疊加指標線（SMA/EMA）
        pricePlot.setDataset(2, overlayDataset);
        XYLineAndShapeRenderer overlayRenderer = new XYLineAndShapeRenderer(true, false);
        overlayRenderer.setSeriesPaint(0, new Color(255, 215, 0));  // SMA 金色
        overlayRenderer.setSeriesPaint(1, new Color(0, 191, 255));  // EMA 天藍色
        overlayRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        overlayRenderer.setSeriesStroke(1, new BasicStroke(2.0f));
        pricePlot.setRenderer(2, overlayRenderer);
        
        // 十字線
        
        // === 技術指標圖（下方）===
        createIndicatorPlot();
        
        // === 組合圖表 ===
        combinedPlot = new CombinedDomainXYPlot(new DateAxis(I18n.get("chart.time")));
        combinedPlot.setGap(10.0);
        combinedPlot.add(pricePlot, 7);       // 70% 高度給主圖
        combinedPlot.add(indicatorPlot, 3);   // 30% 高度給指標圖
        combinedPlot.setOrientation(org.jfree.chart.plot.PlotOrientation.VERTICAL);
        
        JFreeChart chart = new JFreeChart(
                I18n.get("chart.title"),
                JFreeChart.DEFAULT_TITLE_FONT,
                combinedPlot,
                false
        );
        chart.setBackgroundPaint(chartBackgroundColor);
        
        chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        add(chartPanel, BorderLayout.CENTER);
        
        // 初始化繪圖工具
        initializeTradeMarkers();
    }
    
    private void createIndicatorPlot() {
        // 預設創建 RSI 圖
        NumberAxis indicatorAxis = new NumberAxis("RSI");
        indicatorAxis.setAutoRangeIncludesZero(false);
        indicatorAxis.setRange(0, 100);
        
        XYLineAndShapeRenderer indicatorRenderer = new XYLineAndShapeRenderer(true, false);
        indicatorRenderer.setSeriesPaint(0, new Color(255, 165, 0)); // 橘色
        indicatorRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(rsiDataset, null, indicatorAxis, indicatorRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // RSI 的 30 和 70 參考線
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(30, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(70, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
    }
    
    private void createRSIPlot() {
        NumberAxis rsiAxis = new NumberAxis("RSI");
        rsiAxis.setAutoRangeIncludesZero(false);
        rsiAxis.setRange(0, 100);
        
        XYLineAndShapeRenderer rsiRenderer = new XYLineAndShapeRenderer(true, false);
        rsiRenderer.setSeriesPaint(0, new Color(255, 165, 0));
        rsiRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(rsiDataset, null, rsiAxis, rsiRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(30, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(70, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
    }
    
    private void createMACDPlot() {
        NumberAxis macdAxis = new NumberAxis("MACD");
        macdAxis.setAutoRangeIncludesZero(true);
        
        // MACD 線渲染器
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, new Color(0, 191, 255));  // MACD 天藍色
        lineRenderer.setSeriesPaint(1, new Color(255, 140, 0));  // Signal 橘色
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        lineRenderer.setSeriesStroke(1, new BasicStroke(1.5f));
        
        indicatorPlot = new XYPlot(macdLineDataset, null, macdAxis, lineRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // Histogram 柱狀圖
        XYBarRenderer barRenderer = new XYBarRenderer(0.20);
        barRenderer.setSeriesPaint(0, new Color(100, 100, 100, 150));
        barRenderer.setShadowVisible(false);
        barRenderer.setBarPainter(new StandardXYBarPainter());
        
        indicatorPlot.setDataset(1, macdBarDataset);
        indicatorPlot.setRenderer(1, barRenderer);
    }
    
    // ==================== 新增指標副圖創建方法 ====================
    
    private void createKDPlot() {
        NumberAxis kdAxis = new NumberAxis(I18n.get("indicator.kd.title"));
        kdAxis.setAutoRangeIncludesZero(false);
        kdAxis.setRange(0, 100);
        
        XYLineAndShapeRenderer kdRenderer = new XYLineAndShapeRenderer(true, false);
        kdRenderer.setSeriesPaint(0, new Color(0, 191, 255));  // %K 天藍色
        kdRenderer.setSeriesPaint(1, new Color(255, 140, 0));  // %D 橘色
        kdRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        kdRenderer.setSeriesStroke(1, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(kdDataset, null, kdAxis, kdRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // 超買超賣線
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(20, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(80, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
    }
    
    private void createOBVPlot() {
        NumberAxis obvAxis = new NumberAxis(I18n.get("indicator.obv.title"));
        obvAxis.setAutoRangeIncludesZero(false);
        
        XYLineAndShapeRenderer obvRenderer = new XYLineAndShapeRenderer(true, false);
        obvRenderer.setSeriesPaint(0, new Color(147, 112, 219));  // 紫色
        obvRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(obvDataset, null, obvAxis, obvRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
    }
    
    private void createADXPlot() {
        NumberAxis adxAxis = new NumberAxis(I18n.get("indicator.adx.title"));
        adxAxis.setAutoRangeIncludesZero(false);
        adxAxis.setRange(0, 100);
        
        XYLineAndShapeRenderer adxRenderer = new XYLineAndShapeRenderer(true, false);
        adxRenderer.setSeriesPaint(0, new Color(255, 215, 0));   // ADX 金色
        adxRenderer.setSeriesPaint(1, new Color(0, 255, 0));     // +DI 綠色
        adxRenderer.setSeriesPaint(2, new Color(255, 0, 0));     // -DI 紅色
        adxRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        adxRenderer.setSeriesStroke(1, new BasicStroke(1.5f));
        adxRenderer.setSeriesStroke(2, new BasicStroke(1.5f));
        
        indicatorPlot = new XYPlot(adxDataset, null, adxAxis, adxRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // 趨勢強弱參考線
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(25, 
            new Color(100, 100, 100), new BasicStroke(1.0f, BasicStroke.CAP_BUTT, 
            BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f)));
    }
    
    private void createCCIPlot() {
        NumberAxis cciAxis = new NumberAxis(I18n.get("indicator.cci.title"));
        cciAxis.setAutoRangeIncludesZero(true);
        
        XYLineAndShapeRenderer cciRenderer = new XYLineAndShapeRenderer(true, false);
        cciRenderer.setSeriesPaint(0, new Color(255, 105, 180));  // 粉紅色
        cciRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(cciDataset, null, cciAxis, cciRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // CCI 參考線
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(0, 
            new Color(120, 120, 120), new BasicStroke(1.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(100, 
            new Color(100, 100, 100), new BasicStroke(1.0f, BasicStroke.CAP_BUTT, 
            BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f}, 0.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(-100, 
            new Color(100, 100, 100), new BasicStroke(1.0f, BasicStroke.CAP_BUTT, 
            BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f}, 0.0f)));
    }
    
    private void createWRPlot() {
        NumberAxis wrAxis = new NumberAxis(I18n.get("indicator.wr.title"));
        wrAxis.setAutoRangeIncludesZero(false);
        wrAxis.setRange(-100, 0);
        
        XYLineAndShapeRenderer wrRenderer = new XYLineAndShapeRenderer(true, false);
        wrRenderer.setSeriesPaint(0, new Color(255, 99, 71));  // 番茄紅
        wrRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        indicatorPlot = new XYPlot(wrDataset, null, wrAxis, wrRenderer);
        indicatorPlot.setBackgroundPaint(plotBackgroundColor);
        indicatorPlot.setDomainGridlinePaint(gridLineColor);
        indicatorPlot.setRangeGridlinePaint(gridLineColor);
        
        // 超買超賣線
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(-20, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
        indicatorPlot.addRangeMarker(new org.jfree.chart.plot.ValueMarker(-80, 
            new Color(100, 100, 100), new BasicStroke(1.0f)));
    }
    
    @Override
    public void onTick(Tick tick) {
        if (!isTickForCurrentSymbol(tick)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            LocalDateTime now = tick.getTimestamp();
            LocalDateTime normalizedNow = now.withSecond(0).withNano(0);

            if (lastBarTime == null) {
                // 第一根 K 線
                lastBarTime = normalizedNow;
                lastOpen = tick.getPrice();
                lastHigh = tick.getPrice();
                lastLow = tick.getPrice();
                lastClose = tick.getPrice();
                lastVolume = tick.getVolume(); // 使用 tick 的成交量

                // ⭐ 記錄當日開盤價（用於計算漲跌%）
                if (dayOpenPrice == 0.0) {
                    dayOpenPrice = tick.getPrice();
                }
                totalVolume = tick.getVolume();

                addNewBar();
            } else if (normalizedNow.isAfter(lastBarTime)) {
                // 時間到了下一分鐘，開始新的 K 線
                // 開始新 K 線之前，先更新一次當前 K 線（確保最後的數據被保存）
                updateLastBar();

                // 開始新 K 線
                lastBarTime = normalizedNow;
                lastOpen = lastClose; // 新K線開盤價 = 上一根的收盤價
                lastHigh = tick.getPrice();
                lastLow = tick.getPrice();
                lastClose = tick.getPrice();
                lastVolume = tick.getVolume(); // 使用 tick 的成交量

                // ⭐ 累積成交量
                totalVolume += tick.getVolume();

                // 添加新 K 線
                addNewBar();
            } else {
                // 在當前分鐘內，更新當前 K 線
                lastClose = tick.getPrice();
                lastHigh = Math.max(lastHigh, tick.getPrice());
                lastLow = Math.min(lastLow, tick.getPrice());
                lastVolume += tick.getVolume(); // 累積 tick 的成交量

                // ⭐ 累積成交量
                totalVolume += tick.getVolume();

                updateLastBar();
            }

            // 觀察清單漲跌幅由 MainFrame 以昨收為基準統一更新，避免圖表用開盤價覆蓋。
        });
    }

    private boolean isTickForCurrentSymbol(Tick tick) {
        if (tick == null) {
            return false;
        }
        String tickSymbol = tick.getSymbol();
        if (tickSymbol == null || tickSymbol.isBlank()) {
            return true;
        }
        if (currentSymbol == null || currentSymbol.isBlank()) {
            return true;
        }
        return StockNameResolver.normalize(currentSymbol).equals(StockNameResolver.normalize(tickSymbol));
    }
    
    @Override
    public void onTrade(com.dreamhouse.trading.core.model.Trade trade) {
        SwingUtilities.invokeLater(() -> {
            if (lastBarTime != null) {
                lastVolume += trade.getQuantity();
                updateLastBar();
            }

            // 轉發到逐筆成交面板
            if (timeSalesDock != null) {
                timeSalesDock.addTrade(trade);
            }
        });
    }

    @Override
    public void onNews(NewsItem news) {
        SwingUtilities.invokeLater(() -> {
            // 轉發到市場消息面板
            if (newsDock != null) {
                newsDock.addNews(news);
            }
        });
    }

    @Override
    public void onDepthUpdate(List<DepthLevel> depth) {
        // Not used in chart
    }

    /**
     * 設置逐筆成交面板引用
     */
    public void setTimeSalesDock(TimeSalesDock timeSalesDock) {
        this.timeSalesDock = timeSalesDock;
    }

    /**
     * 設置市場消息面板引用
     */
    public void setNewsDock(NewsDock newsDock) {
        this.newsDock = newsDock;
    }

    /**
     * 設置觀察清單面板引用
     */
    public void setWatchlistPanel(WatchlistPanel watchlistPanel) {
        this.watchlistPanel = watchlistPanel;
    }

    /**
     * 設置當前商品代號（用於觀察清單更新）
     */
    public void setCurrentSymbol(String symbol) {
        this.currentSymbol = symbol;
        this.dayOpenPrice = 0.0;  // 重置開盤價
        this.totalVolume = 0;      // 重置成交量
    }

    /**
     * 更新觀察清單的即時數據
     */
    private void updateWatchlist() {
        if (watchlistPanel == null || currentSymbol == null || currentSymbol.isEmpty()) {
            return;
        }

        // 計算漲跌百分比
        double changePct = 0.0;
        if (dayOpenPrice > 0) {
            changePct = ((lastClose - dayOpenPrice) / dayOpenPrice) * 100.0;
        }

        // 更新觀察清單
        watchlistPanel.updateItem(currentSymbol, lastClose, changePct, totalVolume);
    }

    /**
     * 根據 LocalDateTime 創建適當的時間週期
     */
    private RegularTimePeriod createTimePeriod(LocalDateTime dateTime) {
        ZonedDateTime zdt = dateTime.atZone(ZoneId.systemDefault());
        Date date = Date.from(zdt.toInstant());
        
        switch (detectedTimeFrame) {
            case MONTH:
                return new Month(date);
            case WEEK:
                return new Week(date);
            case DAY:
                return new Day(date);
            case HOUR:
                return new Hour(date);
            case MINUTE:
            default:
                return new Minute(date);
        }
    }
    
    /**
     * 檢測數據的時間週期類型
     */
    private void detectTimeFrame(java.util.List<Bar> bars) {
        if (bars == null || bars.size() < 2) {
            detectedTimeFrame = TimeFrame.MINUTE;
            return;
        }
        
        // 計算前幾根 K 線的平均時間間隔
        long totalMinutes = 0;
        int count = 0;
        for (int i = 1; i < Math.min(bars.size(), 10); i++) {
            Duration duration = Duration.between(
                bars.get(i - 1).getTimestamp(),
                bars.get(i).getTimestamp()
            );
            totalMinutes += duration.toMinutes();
            count++;
        }
        
        long avgMinutes = totalMinutes / count;
        
        // 根據平均時間間隔判斷週期類型
        if (avgMinutes >= 20000) { // 約 14 天以上
            detectedTimeFrame = TimeFrame.MONTH;
            System.out.println("[ChartDock] 檢測到月線數據 (平均間隔: " + avgMinutes + " 分鐘)");
        } else if (avgMinutes >= 5000) { // 約 3.5 天以上
            detectedTimeFrame = TimeFrame.WEEK;
            System.out.println("[ChartDock] 檢測到週線數據 (平均間隔: " + avgMinutes + " 分鐘)");
        } else if (avgMinutes >= 1000) { // 約 16 小時以上
            detectedTimeFrame = TimeFrame.DAY;
            System.out.println("[ChartDock] 檢測到日線數據 (平均間隔: " + avgMinutes + " 分鐘)");
        } else if (avgMinutes >= 30) { // 30 分鐘以上
            detectedTimeFrame = TimeFrame.HOUR;
            System.out.println("[ChartDock] 檢測到小時線數據 (平均間隔: " + avgMinutes + " 分鐘)");
        } else {
            detectedTimeFrame = TimeFrame.MINUTE;
            System.out.println("[ChartDock] 檢測到分鐘線數據 (平均間隔: " + avgMinutes + " 分鐘)");
        }
    }
    
    private void addNewBar() {
        RegularTimePeriod period = createTimePeriod(lastBarTime);

        // 檢查是否已存在相同時間的數據點，使用 addOrUpdate 避免衝突
        try {
            // 嘗試直接添加
            replaceOhlcBar(period, lastOpen, lastHigh, lastLow, lastClose);
        } catch (org.jfree.data.general.SeriesException e) {
            // 如果已存在，則移除舊的並添加新的
            System.out.println("警告: 時間點已存在，更新數據: " + period);
            int existingIndex = ohlcSeries.indexOf(period);
            if (existingIndex >= 0) {
                ohlcSeries.remove(existingIndex);
            }
            replaceOhlcBar(period, lastOpen, lastHigh, lastLow, lastClose);
        }
        volumeSeries.addOrUpdate(period, lastVolume);

        // 添加到 ta4j BarSeries
        ZonedDateTime zdt = lastBarTime.atZone(ZoneId.systemDefault());
        indicatorService.addBar(
            zdt,  // 使用 ZonedDateTime
            lastOpen,
            lastHigh,
            lastLow,
            lastClose,
            lastVolume
        );

        // 重算指標
        updateIndicators();
    }
    
    private void updateLastBar() {
        if (ohlcSeries.getItemCount() == 0) return;
        
        RegularTimePeriod period = createTimePeriod(lastBarTime);
        
        // 刪除最後一根並重新添加（JFreeChart OHLCSeries 沒有 update 方法）
        replaceOhlcBar(period, lastOpen, lastHigh, lastLow, lastClose);
        volumeSeries.addOrUpdate(period, lastVolume);
        
        // 同步更新 ta4j BarSeries 的最後一根
        if (indicatorService.getBarSeries().getBarCount() > 0) {
            int ta4jLastIndex = indicatorService.getBarSeries().getBarCount() - 1;
            // ta4j 的 BarSeries 沒有 update 方法，所以我們需要重新添加
            // 但這會導致問題，所以我們只在新 K 線時才更新 ta4j
            // 這裡暫時不更新 ta4j，只在 addNewBar 時更新
        }
        
        // 每次更新都重算指標（可能會有性能問題，但確保指標即時）
        updateIndicators();
    }
    
    private void replaceOhlcBar(RegularTimePeriod period, double open, double high, double low, double close) {
        int existingIndex = ohlcSeries.indexOf(period);
        if (existingIndex >= 0) {
            ohlcSeries.remove(existingIndex);
        }
        ohlcSeries.add(period, open, high, low, close);
    }

    private void updateIndicators() {
        // 清除所有疊線指標
        smaSeries.clear();
        emaSeries.clear();
        bollUpperSeries.clear();
        bollMiddleSeries.clear();
        bollLowerSeries.clear();
        
        // 更新選中的疊線指標
        if ("SMA".equals(currentOverlayIndicator)) {
            updateSMA();
        } else if ("EMA".equals(currentOverlayIndicator)) {
            updateEMA();
        } else if ("BOLL".equals(currentOverlayIndicator)) {
            updateBOLL();
        }
        
        // 清除所有副圖指標
        rsiSeries.clear();
        macdSeries.clear();
        signalSeries.clear();
        histogramSeries.clear();
        kdKSeries.clear();
        kdDSeries.clear();
        obvSeries.clear();
        adxSeries.clear();
        plusDISeries.clear();
        minusDISeries.clear();
        cciSeries.clear();
        wrSeries.clear();
        
        // 更新選中的副圖指標
        if ("RSI".equals(currentSubIndicator)) {
            updateRSI();
        } else if ("MACD".equals(currentSubIndicator)) {
            updateMACD();
        } else if ("KD".equals(currentSubIndicator)) {
            updateKD();
        } else if ("ADX".equals(currentSubIndicator)) {
            updateADX();
        } else if ("OBV".equals(currentSubIndicator)) {
            updateOBV();
        } else if ("CCI".equals(currentSubIndicator)) {
            updateCCI();
        } else if ("WR".equals(currentSubIndicator)) {
            updateWR();
        }
    }
    
    private void updateSMA() {
        IndicatorConfig config = indicatorConfigs.get("SMA");
        int smaPeriod = (config != null) ? config.getPeriod() : 20;
        
        List<Double> smaValues = indicatorService.getSMA(smaPeriod);
        
        for (int i = 0; i < smaValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (smaValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod timePeriod = item.getPeriod();
                smaSeries.addOrUpdate(timePeriod, smaValues.get(i));
            }
        }
        
        // 更新顏色 - SMA 在 overlayRenderer (index 2) 的 series 0
        if (config != null && config.getColor() != null && combinedPlot != null) {
            XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0); // 主圖是第0個 subplot
            XYItemRenderer renderer = plot.getRenderer(2); // overlayRenderer 是第2個 renderer
            if (renderer != null) {
                renderer.setSeriesPaint(0, config.getColor()); // SMA 是 series 0
            }
        }
    }
    
    private void updateEMA() {
        IndicatorConfig config = indicatorConfigs.get("EMA");
        int emaPeriod = (config != null) ? config.getPeriod() : 20;
        
        List<Double> emaValues = indicatorService.getEMA(emaPeriod);
        
        for (int i = 0; i < emaValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (emaValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod timePeriod = item.getPeriod();
                emaSeries.addOrUpdate(timePeriod, emaValues.get(i));
            }
        }
        
        // 更新顏色 - EMA 在 overlayRenderer (index 2) 的 series 1
        if (config != null && config.getColor() != null && combinedPlot != null) {
            XYPlot plot = (XYPlot) combinedPlot.getSubplots().get(0); // 主圖是第0個 subplot
            XYItemRenderer renderer = plot.getRenderer(2); // overlayRenderer 是第2個 renderer
            if (renderer != null) {
                renderer.setSeriesPaint(1, config.getColor()); // EMA 是 series 1
            }
        }
    }
    
    private void updateRSI() {
        IndicatorConfig config = indicatorConfigs.get("RSI");
        int rsiPeriod = (config != null) ? config.getPeriod() : 14;
        
        List<Double> rsiValues = indicatorService.getRSI(rsiPeriod);
        
        for (int i = 0; i < rsiValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (rsiValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod timePeriod = item.getPeriod();
                rsiSeries.addOrUpdate(timePeriod, rsiValues.get(i));
            }
        }
        
        // 更新顏色
        if (config != null && config.getColor() != null && indicatorPlot != null) {
            XYItemRenderer renderer = indicatorPlot.getRenderer();
            if (renderer != null) {
                renderer.setSeriesPaint(0, config.getColor());
            }
        }
    }
    
    private void updateMACD() {
        IndicatorConfig config = indicatorConfigs.get("MACD");
        int fastPeriod = (config != null) ? config.getFastPeriod() : 12;
        int slowPeriod = (config != null) ? config.getSlowPeriod() : 26;
        int signalPeriod = (config != null) ? config.getSignalPeriod() : 9;
        
        Map<String, List<Double>> macdData = indicatorService.getMACD(fastPeriod, slowPeriod, signalPeriod);
        List<Double> macdValues = macdData.get("macd");
        List<Double> signalValues = macdData.get("signal");
        List<Double> histogramValues = macdData.get("histogram");
        
        for (int i = 0; i < macdValues.size() && i < ohlcSeries.getItemCount(); i++) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod period = item.getPeriod();
            
            if (macdValues.get(i) != null) {
                macdSeries.addOrUpdate(period, macdValues.get(i));
            }
            if (signalValues.get(i) != null) {
                signalSeries.addOrUpdate(period, signalValues.get(i));
            }
            if (histogramValues.get(i) != null) {
                histogramSeries.addOrUpdate(period, histogramValues.get(i));
            }
        }
    }
    
    // ==================== 新增指標更新方法 ====================
    
    private void updateBOLL() {
        IndicatorConfig config = indicatorConfigs.get("BOLL");
        int bollPeriod = (config != null) ? config.getPeriod() : 20;
        double multiplier = (config != null) ? config.getMultiplier() : 2.0;
        
        Map<String, List<Double>> bollData = indicatorService.getBollingerBands(bollPeriod, multiplier);
        List<Double> upperValues = bollData.get("upper");
        List<Double> middleValues = bollData.get("middle");
        List<Double> lowerValues = bollData.get("lower");
        
        for (int i = 0; i < upperValues.size() && i < ohlcSeries.getItemCount(); i++) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod timePeriod = item.getPeriod();
            
            if (upperValues.get(i) != null) {
                bollUpperSeries.addOrUpdate(timePeriod, upperValues.get(i));
            }
            if (middleValues.get(i) != null) {
                bollMiddleSeries.addOrUpdate(timePeriod, middleValues.get(i));
            }
            if (lowerValues.get(i) != null) {
                bollLowerSeries.addOrUpdate(timePeriod, lowerValues.get(i));
            }
        }
    }
    
    private void updateKD() {
        IndicatorConfig config = indicatorConfigs.get("KD");
        int kPeriod = (config != null) ? config.getPeriod() : 9;
        int dPeriod = (config != null) ? config.getPeriod2() : 3;
        
        Map<String, List<Double>> kdData = indicatorService.getStochastic(kPeriod, dPeriod);
        List<Double> kValues = kdData.get("k");
        List<Double> dValues = kdData.get("d");
        
        for (int i = 0; i < kValues.size() && i < ohlcSeries.getItemCount(); i++) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod period = item.getPeriod();
            
            if (kValues.get(i) != null) {
                kdKSeries.addOrUpdate(period, kValues.get(i));
            }
            if (dValues.get(i) != null) {
                kdDSeries.addOrUpdate(period, dValues.get(i));
            }
        }
    }
    
    private void updateOBV() {
        List<Double> obvValues = indicatorService.getOBV();
        
        for (int i = 0; i < obvValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (obvValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod period = item.getPeriod();
                obvSeries.addOrUpdate(period, obvValues.get(i));
            }
        }
    }
    
    private void updateADX() {
        IndicatorConfig config = indicatorConfigs.get("ADX");
        int adxPeriod = (config != null) ? config.getPeriod() : 14;
        
        Map<String, List<Double>> adxData = indicatorService.getADX(adxPeriod);
        List<Double> adxValues = adxData.get("adx");
        List<Double> plusDIValues = adxData.get("plusDI");
        List<Double> minusDIValues = adxData.get("minusDI");
        
        for (int i = 0; i < adxValues.size() && i < ohlcSeries.getItemCount(); i++) {
            org.jfree.data.time.ohlc.OHLCItem item = 
                (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
            RegularTimePeriod timePeriod = item.getPeriod();
            
            if (adxValues.get(i) != null) {
                adxSeries.addOrUpdate(timePeriod, adxValues.get(i));
            }
            if (plusDIValues.get(i) != null) {
                plusDISeries.addOrUpdate(timePeriod, plusDIValues.get(i));
            }
            if (minusDIValues.get(i) != null) {
                minusDISeries.addOrUpdate(timePeriod, minusDIValues.get(i));
            }
        }
    }
    
    private void updateCCI() {
        IndicatorConfig config = indicatorConfigs.get("CCI");
        int cciPeriod = (config != null) ? config.getPeriod() : 14;
        
        List<Double> cciValues = indicatorService.getCCI(cciPeriod);
        
        for (int i = 0; i < cciValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (cciValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod timePeriod = item.getPeriod();
                cciSeries.addOrUpdate(timePeriod, cciValues.get(i));
            }
        }
    }
    
    private void updateWR() {
        IndicatorConfig config = indicatorConfigs.get("WR");
        int wrPeriod = (config != null) ? config.getPeriod() : 14;
        
        List<Double> wrValues = indicatorService.getWilliamsR(wrPeriod);
        
        for (int i = 0; i < wrValues.size() && i < ohlcSeries.getItemCount(); i++) {
            if (wrValues.get(i) != null) {
                org.jfree.data.time.ohlc.OHLCItem item = 
                    (org.jfree.data.time.ohlc.OHLCItem) ohlcSeries.getDataItem(i);
                RegularTimePeriod timePeriod = item.getPeriod();
                wrSeries.addOrUpdate(timePeriod, wrValues.get(i));
            }
        }
    }
    
    public void setOverlayIndicator(String indicator) {
        System.out.println("設置疊線指標: " + indicator);
        this.currentOverlayIndicator = indicator;
        updateIndicators();
    }
    
    public void setSubIndicator(String indicator) {
        System.out.println("設置副圖指標: " + indicator);
        this.currentSubIndicator = indicator;
        
        // 重建副圖
        rebuildSubIndicatorPlot();
        updateIndicators();
    }
    
    private void rebuildSubIndicatorPlot() {
        // 移除舊的副圖
        if (combinedPlot != null && indicatorPlot != null) {
            combinedPlot.remove(indicatorPlot);
        }
        
        // 根據選擇的指標創建新副圖
        if ("RSI".equals(currentSubIndicator)) {
            createRSIPlot();
        } else if ("MACD".equals(currentSubIndicator)) {
            createMACDPlot();
        } else if ("KD".equals(currentSubIndicator)) {
            createKDPlot();
        } else if ("ADX".equals(currentSubIndicator)) {
            createADXPlot();
        } else if ("OBV".equals(currentSubIndicator)) {
            createOBVPlot();
        } else if ("CCI".equals(currentSubIndicator)) {
            createCCIPlot();
        } else if ("WR".equals(currentSubIndicator)) {
            createWRPlot();
        } else if ("None".equals(currentSubIndicator)) {
            indicatorPlot = null;
        }
        
        // 重新添加副圖到組合圖表
        if (indicatorPlot != null) {
            combinedPlot.add(indicatorPlot, 1);  // 權重 1 (主圖權重 3)
        }
        
        // 刷新圖表
        if (chartPanel != null) {
            chartPanel.repaint();
        }
    }
    
    /**
     * 設定時間週期
     * 注意：現在切換週期時會由MainFrame重新加載對應週期的數據
     * @param timeframe 目標時間週期
     */
    public void setTimeframe(Timeframe timeframe) {
        System.out.println("[ChartDock] 設定週期: " + currentTimeframe + " -> " + timeframe);
        this.currentTimeframe = timeframe;
        System.out.println("[ChartDock] 週期已更新，等待數據重新載入");
    }
    
    /**
     * 根據當前 UIManager 更新主題顏色
     */
    private void updateThemeColors() {
        // 從 UIManager 獲取當前主題顏色
        Color panelBg = UIManager.getColor("Panel.background");
        
        if (panelBg != null) {
            // 判斷是深色還是淺色主題
            int brightness = (panelBg.getRed() + panelBg.getGreen() + panelBg.getBlue()) / 3;
            boolean isDark = brightness < 128;
            
            if (isDark) {
                // 深色主題
                plotBackgroundColor = new Color(30, 30, 30);
                gridLineColor = new Color(60, 60, 60);
                chartBackgroundColor = new Color(40, 40, 40);
            } else {
                // 淺色主題
                plotBackgroundColor = Color.WHITE;
                gridLineColor = new Color(220, 220, 220);
                chartBackgroundColor = new Color(245, 245, 245);
            }
        } else {
            // 預設使用深色主題
            plotBackgroundColor = new Color(30, 30, 30);
            gridLineColor = new Color(60, 60, 60);
            chartBackgroundColor = new Color(40, 40, 40);
        }
    }
    
    /**
     * 刷新圖表主題（當主題切換時調用）
     */
    public void refreshTheme() {
        updateThemeColors();
        
        if (pricePlot != null) {
            pricePlot.setBackgroundPaint(plotBackgroundColor);
            pricePlot.setDomainGridlinePaint(gridLineColor);
            pricePlot.setRangeGridlinePaint(gridLineColor);
        }
        
        if (indicatorPlot != null) {
            indicatorPlot.setBackgroundPaint(plotBackgroundColor);
            indicatorPlot.setDomainGridlinePaint(gridLineColor);
            indicatorPlot.setRangeGridlinePaint(gridLineColor);
        }
        
        if (chartPanel != null && chartPanel.getChart() != null) {
            chartPanel.getChart().setBackgroundPaint(chartBackgroundColor);
            chartPanel.repaint();
        }
    }
    
    /**
     * 清除所有圖表數據
     */
    public void clearAllData() {
        SwingUtilities.invokeLater(() -> {
            // 清空現有數據
            ohlcSeries.clear();
            volumeSeries.clear();
            indicatorService.clearAllData();

            // 清空所有指標系列
            smaSeries.clear();
            emaSeries.clear();
            rsiSeries.clear();
            macdSeries.clear();
            signalSeries.clear();
            histogramSeries.clear();

            bollUpperSeries.clear();
            bollMiddleSeries.clear();
            bollLowerSeries.clear();

            kdKSeries.clear();
            kdDSeries.clear();

            obvSeries.clear();

            adxSeries.clear();
            plusDISeries.clear();
            minusDISeries.clear();

            cciSeries.clear();
            wrSeries.clear();

            // 重置狀態
            lastBarTime = null;

            // 刷新圖表
            if (chartPanel != null) {
                chartPanel.repaint();
            }

            System.out.println("[ChartDock] 清除所有圖表數據");
        });
    }

    /**
     * 載入歷史數據到圖表
     * @param bars 歷史 K 線數據
     */
    public void loadHistoricalData(List<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            List<Bar> displayBars = normalizeFlatSnapshotBars(bars);

            // 清空現有數據
            ohlcSeries.clear();
            volumeSeries.clear();
            indicatorService.clearAllData();
            
            // 清空所有指標系列
            smaSeries.clear();
            emaSeries.clear();
            rsiSeries.clear();
            macdSeries.clear();
            signalSeries.clear();
            histogramSeries.clear();
            
            bollUpperSeries.clear();
            bollMiddleSeries.clear();
            bollLowerSeries.clear();
            
            kdKSeries.clear();
            kdDSeries.clear();
            
            obvSeries.clear();
            
            adxSeries.clear();
            plusDISeries.clear();
            minusDISeries.clear();
            
            cciSeries.clear();
            wrSeries.clear();
            
            // 檢測數據的時間週期類型
            detectTimeFrame(displayBars);
            
            // 載入新數據
            for (Bar bar : displayBars) {
                // 使用智能時間週期創建
                RegularTimePeriod timePeriod = createTimePeriod(bar.getTimestamp());
                
                replaceOhlcBar(
                    timePeriod,
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose()
                );
                
                // 添加成交量數據
                volumeSeries.addOrUpdate(timePeriod, bar.getVolume());
                
                // 添加到 ta4j BarSeries
                indicatorService.addBar(
                    bar.getTimestamp().atZone(java.time.ZoneId.systemDefault()),
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose(),
                    bar.getVolume()
                );
            }
            
            // 重新計算所有指標
            updateIndicators();
            
            // 自動調整視圖範圍
            if (chartPanel != null) {
                chartPanel.restoreAutoBounds();
            }
        });
    }

    private List<Bar> normalizeFlatSnapshotBars(List<Bar> bars) {
        List<Bar> displayBars = new ArrayList<>(bars.size());
        Double previousClose = null;
        for (Bar bar : bars) {
            Bar displayBar = bar;
            if (previousClose != null && isFlatSnapshotBar(bar)
                    && Math.abs(previousClose - bar.getClose()) > 0.000001) {
                double open = previousClose;
                double close = bar.getClose();
                displayBar = new Bar(
                        bar.getTimestamp(),
                        open,
                        Math.max(open, close),
                        Math.min(open, close),
                        close,
                        bar.getVolume());
            }
            displayBars.add(displayBar);
            previousClose = bar.getClose();
        }
        return displayBars;
    }

    private boolean isFlatSnapshotBar(Bar bar) {
        double open = bar.getOpen();
        return Math.abs(open - bar.getHigh()) < 0.000001
                && Math.abs(open - bar.getLow()) < 0.000001
                && Math.abs(open - bar.getClose()) < 0.000001;
    }
    
    /**
     * 獲取當前圖表中的所有 K 線數據
     * @return K 線數據列表
     */
    public List<org.ta4j.core.Bar> getCurrentBars() {
        return indicatorService.getBarSeries().getBarData();
    }
    
    /**
     * 顯示交易標記
     */
    public void showTradeMarkers(List<com.dreamhouse.trading.core.backtest.Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            clearTradeMarkers();
            return;
        }
        
        // 使用新的標記管理器
        TradeMarker.TimeFrameConverter converter = this::createTimePeriod;
        markerManager.setTrades(trades, converter);
        
        System.out.println("[ChartDock] 顯示 " + trades.size() + " 個交易標記");
    }
    
    /**
     * 設置交易選擇監聽器
     */
    public void setTradeSelectionListener(TradeMarkerManager.TradeSelectionListener listener) {
        if (markerManager != null) {
            markerManager.setSelectionListener(listener);
        }
    }
    
    /**
     * 顯示交易標記篩選對話框
     */
    public void showTradeMarkerFilter() {
        if (markerManager != null) {
            markerManager.showFilterDialog();
        }
    }
    
    /**
     * 清除交易標記
     */
    public void clearTradeMarkers() {
        if (markerManager != null) {
            markerManager.clearMarkers();
            System.out.println("[ChartDock] 清除所有交易標記");
        }
    }
    
    /**
     * 添加標記到圖表
     */
    private void addMarkersToChart() {
        if (pricePlot == null || tradeMarkers.isEmpty()) {
            return;
        }
        
        for (TradeMarker marker : tradeMarkers) {
            try {
                // 添加多層形狀標記 (熱力圖效果)
                for (org.jfree.chart.annotations.XYShapeAnnotation shapeAnnotation : marker.createShapeAnnotations()) {
                    pricePlot.addAnnotation(shapeAnnotation);
                }
                
                // 添加多層文字標記 (陰影效果)
                for (org.jfree.chart.annotations.XYTextAnnotation textAnnotation : marker.createTextAnnotations()) {
                    pricePlot.addAnnotation(textAnnotation);
                }
                
            } catch (Exception e) {
                System.err.println("添加交易標記失敗: " + e.getMessage());
            }
        }
        
        // 刷新圖表
        if (chartPanel != null) {
            chartPanel.repaint();
        }
    }
    
    /**
     * 初始化預設指標配置
     */
    private void initializeDefaultConfigs() {
        // SMA 預設配置
        IndicatorConfig smaConfig = new IndicatorConfig();
        smaConfig.setPeriod(20);
        smaConfig.setColor(new Color(0, 123, 255));
        indicatorConfigs.put("SMA", smaConfig);
        
        // EMA 預設配置
        IndicatorConfig emaConfig = new IndicatorConfig();
        emaConfig.setPeriod(20);
        emaConfig.setColor(new Color(255, 87, 34));
        indicatorConfigs.put("EMA", emaConfig);
        
        // RSI 預設配置
        IndicatorConfig rsiConfig = new IndicatorConfig();
        rsiConfig.setPeriod(14);
        rsiConfig.setColor(new Color(156, 39, 176));
        indicatorConfigs.put("RSI", rsiConfig);
        
        // MACD 預設配置
        IndicatorConfig macdConfig = new IndicatorConfig();
        macdConfig.setFastPeriod(12);
        macdConfig.setSlowPeriod(26);
        macdConfig.setSignalPeriod(9);
        macdConfig.setColor(new Color(33, 150, 243));
        macdConfig.setColor2(new Color(255, 193, 7));
        macdConfig.setColor3(new Color(76, 175, 80));
        indicatorConfigs.put("MACD", macdConfig);
        
        // BOLL 預設配置
        IndicatorConfig bollConfig = new IndicatorConfig();
        bollConfig.setPeriod(20);
        bollConfig.setMultiplier(2.0);
        bollConfig.setColor(new Color(255, 152, 0));
        bollConfig.setColor2(new Color(96, 125, 139));
        bollConfig.setColor3(new Color(255, 152, 0));
        indicatorConfigs.put("BOLL", bollConfig);
        
        // 其他指標配置...
        IndicatorConfig kdConfig = new IndicatorConfig();
        kdConfig.setPeriod(9);
        kdConfig.setPeriod2(3);
        kdConfig.setColor(new Color(244, 67, 54));
        kdConfig.setColor2(new Color(33, 150, 243));
        indicatorConfigs.put("KD", kdConfig);
    }
    
    /**
     * 設定指標參數並重新計算
     */
    public void setIndicatorParameters(String indicatorName, IndicatorConfig config) {
        indicatorConfigs.put(indicatorName, config);
        
        SwingUtilities.invokeLater(() -> {
            updateIndicators();
            updateSeriesTitles();
            
            if (chartPanel != null) {
                chartPanel.repaint();
            }
        });
    }
    
    /**
     * 更新系列標題
     */
    private void updateSeriesTitles() {
        IndicatorConfig smaConfig = indicatorConfigs.get("SMA");
        if (smaConfig != null) {
            smaSeries.setKey(String.format("SMA(%d)", smaConfig.getPeriod()));
        }
        
        IndicatorConfig emaConfig = indicatorConfigs.get("EMA");
        if (emaConfig != null) {
            emaSeries.setKey(String.format("EMA(%d)", emaConfig.getPeriod()));
        }
    }
    
    /**
     * 獲取指標配置
     */
    public IndicatorConfig getIndicatorConfig(String indicatorName) {
        return indicatorConfigs.getOrDefault(indicatorName, new IndicatorConfig());
    }
    
    private void initializeTradeMarkers() {
        markerManager = new TradeMarkerManager(chartPanel, pricePlot);
    }
}

