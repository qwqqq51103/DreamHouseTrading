package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * 水平線
 * 用於標記價格水平、支撐位、阻力位等
 */
public class HorizontalLine extends DrawingObject {
    
    private static final long serialVersionUID = 1L;
    
    // 價格水平
    private double price;
    
    // 文字標籤
    private String label;
    
    // 線條樣式
    private LineStyle style;
    
    public enum LineStyle {
        SOLID,      // 實線
        DASHED,     // 虛線
        DOTTED      // 點線
    }
    
    public HorizontalLine(double price) {
        super();
        this.price = price;
        this.label = String.format("%.2f", price);
        this.style = LineStyle.SOLID;
    }
    
    public HorizontalLine(double price, String label) {
        super();
        this.price = price;
        this.label = label;
        this.style = LineStyle.SOLID;
    }
    
    @Override
    public void draw(Graphics2D g2, Rectangle2D plotArea) {
        if (!visible) return;
        
        // 設定線條樣式
        Stroke lineStroke;
        switch (style) {
            case DASHED:
                lineStroke = new BasicStroke(
                    stroke.getLineWidth(),
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10.0f,
                    new float[]{10.0f, 10.0f},
                    0.0f
                );
                break;
            case DOTTED:
                lineStroke = new BasicStroke(
                    stroke.getLineWidth(),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10.0f,
                    new float[]{2.0f, 6.0f},
                    0.0f
                );
                break;
            default:
                lineStroke = stroke;
        }
        
        // 繪製水平線（跨越整個圖表寬度）
        g2.setColor(color);
        g2.setStroke(lineStroke);
        
        double y = price;  // Y 座標就是價格值（在螢幕座標系統中）
        Line2D line = new Line2D.Double(plotArea.getMinX(), y, plotArea.getMaxX(), y);
        g2.draw(line);
        
        // 繪製價格標籤
        if (label != null && !label.isEmpty()) {
            g2.setFont(new Font("Arial", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int labelWidth = fm.stringWidth(label);
            int labelHeight = fm.getHeight();
            
            // 標籤背景
            int padding = 4;
            int labelX = (int) plotArea.getMaxX() - labelWidth - padding * 2;
            int labelY = (int) y - labelHeight / 2;
            
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
            g2.fillRect(labelX, labelY, labelWidth + padding * 2, labelHeight);
            
            // 標籤文字
            g2.setColor(Color.WHITE);
            g2.drawString(label, labelX + padding, labelY + fm.getAscent());
        }
        
        // 如果選中，繪製選擇指示器
        if (selected) {
            g2.setColor(new Color(0, 120, 215, 100));
            g2.setStroke(new BasicStroke(3.0f));
            g2.draw(line);
        }
    }
    
    @Override
    public boolean hitTest(double x, double y, Rectangle2D plotArea) {
        // 檢查 Y 座標是否接近價格水平
        double distance = Math.abs(y - price);
        return distance < 5.0;  // 5 像素容差
    }
    
    @Override
    public Rectangle2D getBounds(Rectangle2D plotArea) {
        return new Rectangle2D.Double(
            plotArea.getMinX(),
            price - 2,
            plotArea.getWidth(),
            4
        );
    }
    
    @Override
    public void move(double dx, double dy, Rectangle2D plotArea) {
        // 水平線只能上下移動
        price += dy;
        
        // 更新標籤
        if (label != null && label.matches("-?\\d+(\\.\\d+)?")) {
            label = String.format("%.2f", price);
        }
    }
    
    @Override
    public DrawingObject clone() {
        HorizontalLine clone = new HorizontalLine(price, label);
        clone.setColor(this.color);
        clone.setStroke(this.stroke);
        clone.setStyle(this.style);
        return clone;
    }
    
    // Getters and Setters
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
        if (label != null && label.matches("-?\\d+(\\.\\d+)?")) {
            this.label = String.format("%.2f", price);
        }
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public LineStyle getStyle() {
        return style;
    }
    
    public void setStyle(LineStyle style) {
        this.style = style;
    }
}

