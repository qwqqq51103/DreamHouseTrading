package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * 趨勢線
 * 由兩個點定義的直線
 */
public class TrendLine extends DrawingObject {
    
    private static final long serialVersionUID = 1L;
    
    // 數據座標（時間軸和價格軸的值）
    private double x1; // 時間點1（毫秒時間戳或索引）
    private double y1; // 價格1
    private double x2; // 時間點2
    private double y2; // 價格2
    
    // 是否延伸線條
    private boolean extend;
    
    public TrendLine(double x1, double y1, double x2, double y2) {
        super();
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.extend = false;
    }
    
    @Override
    public void draw(Graphics2D g2, Rectangle2D plotArea) {
        if (!visible) return;
        
        // 設定繪圖屬性
        g2.setColor(color);
        g2.setStroke(stroke);
        
        // 繪製線條
        Line2D line = new Line2D.Double(x1, y1, x2, y2);
        g2.draw(line);
        
        // 如果選中，繪製選擇框和控制點
        if (selected) {
            // 繪製端點
            drawEndPoint(g2, x1, y1);
            drawEndPoint(g2, x2, y2);
        }
    }
    
    /**
     * 繪製端點
     */
    private void drawEndPoint(Graphics2D g2, double x, double y) {
        int size = 8;
        int halfSize = size / 2;
        
        g2.setColor(Color.WHITE);
        g2.fillOval((int)(x - halfSize), (int)(y - halfSize), size, size);
        
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval((int)(x - halfSize), (int)(y - halfSize), size, size);
    }
    
    @Override
    public boolean hitTest(double x, double y, Rectangle2D plotArea) {
        // 計算點到線的距離
        double distance = Line2D.ptLineDist(x1, y1, x2, y2, x, y);
        
        // 檢查是否在線段範圍內
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        
        boolean inRange = x >= minX - 10 && x <= maxX + 10 && 
                         y >= minY - 10 && y <= maxY + 10;
        
        // 如果距離小於閾值（5像素）且在範圍內，則命中
        return distance < 5.0 && inRange;
    }
    
    @Override
    public Rectangle2D getBounds(Rectangle2D plotArea) {
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }
    
    @Override
    public void move(double dx, double dy, Rectangle2D plotArea) {
        x1 += dx;
        y1 += dy;
        x2 += dx;
        y2 += dy;
    }
    
    /**
     * 移動端點
     * @param pointIndex 端點索引（0=起點，1=終點）
     * @param newX 新的 X 座標
     * @param newY 新的 Y 座標
     */
    public void moveEndPoint(int pointIndex, double newX, double newY) {
        if (pointIndex == 0) {
            x1 = newX;
            y1 = newY;
        } else if (pointIndex == 1) {
            x2 = newX;
            y2 = newY;
        }
    }
    
    /**
     * 檢查是否點擊端點
     * @param x X 座標
     * @param y Y 座標
     * @return 端點索引（0, 1），如果未命中返回 -1
     */
    public int hitTestEndPoint(double x, double y) {
        int threshold = 10;
        
        if (Point2D.distance(x1, y1, x, y) < threshold) {
            return 0;
        }
        if (Point2D.distance(x2, y2, x, y) < threshold) {
            return 1;
        }
        
        return -1;
    }
    
    @Override
    public DrawingObject clone() {
        TrendLine clone = new TrendLine(x1, y1, x2, y2);
        clone.setColor(this.color);
        clone.setStroke(this.stroke);
        clone.setExtend(this.extend);
        return clone;
    }
    
    // Getters and Setters
    
    public double getX1() {
        return x1;
    }
    
    public void setX1(double x1) {
        this.x1 = x1;
    }
    
    public double getY1() {
        return y1;
    }
    
    public void setY1(double y1) {
        this.y1 = y1;
    }
    
    public double getX2() {
        return x2;
    }
    
    public void setX2(double x2) {
        this.x2 = x2;
    }
    
    public double getY2() {
        return y2;
    }
    
    public void setY2(double y2) {
        this.y2 = y2;
    }
    
    public boolean isExtend() {
        return extend;
    }
    
    public void setExtend(boolean extend) {
        this.extend = extend;
    }
}

