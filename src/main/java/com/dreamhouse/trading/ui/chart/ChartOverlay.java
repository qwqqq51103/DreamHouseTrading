package com.dreamhouse.trading.ui.chart;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.annotations.AbstractXYAnnotation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * 圖表覆蓋層
 * 在 JFreeChart 上繪製自訂對象
 */
public class ChartOverlay extends AbstractXYAnnotation {
    
    private final DrawingManager drawingManager;
    
    public ChartOverlay(DrawingManager drawingManager) {
        this.drawingManager = drawingManager;
    }
    
    @Override
    public void draw(Graphics2D g2, XYPlot plot, Rectangle2D dataArea,
                    ValueAxis domainAxis, ValueAxis rangeAxis,
                    int rendererIndex, PlotRenderingInfo info) {
        
        // 繪製所有繪圖對象
        drawingManager.drawAll(g2, dataArea);
    }
    
    /**
     * 將螢幕座標轉換為數據座標
     */
    public static double[] screenToData(double screenX, double screenY,
                                       Rectangle2D dataArea,
                                       ValueAxis domainAxis, ValueAxis rangeAxis,
                                       XYPlot plot) {
        double dataX = domainAxis.java2DToValue(screenX, dataArea, plot.getDomainAxisEdge());
        double dataY = rangeAxis.java2DToValue(screenY, dataArea, plot.getRangeAxisEdge());
        return new double[]{dataX, dataY};
    }
    
    /**
     * 將數據座標轉換為螢幕座標
     */
    public static double[] dataToScreen(double dataX, double dataY,
                                       Rectangle2D dataArea,
                                       ValueAxis domainAxis, ValueAxis rangeAxis,
                                       XYPlot plot) {
        double screenX = domainAxis.valueToJava2D(dataX, dataArea, plot.getDomainAxisEdge());
        double screenY = rangeAxis.valueToJava2D(dataY, dataArea, plot.getRangeAxisEdge());
        return new double[]{screenX, screenY};
    }
}

