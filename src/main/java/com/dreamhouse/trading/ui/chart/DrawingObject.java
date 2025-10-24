package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.UUID;

/**
 * 繪圖對象基類
 * 所有圖表上的繪圖對象（趨勢線、水平線、形狀等）都繼承此類
 */
public abstract class DrawingObject implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    protected String id;
    protected Color color;
    protected BasicStroke stroke;
    protected boolean selected;
    protected boolean visible;
    
    public DrawingObject() {
        this.id = UUID.randomUUID().toString();
        this.color = Color.BLUE;
        this.stroke = new BasicStroke(2.0f);
        this.selected = false;
        this.visible = true;
    }
    
    /**
     * 繪製對象
     * @param g2 Graphics2D 對象
     * @param plotArea 圖表區域
     */
    public abstract void draw(Graphics2D g2, Rectangle2D plotArea);
    
    /**
     * 檢查點是否在對象上
     * @param x X 座標（螢幕座標）
     * @param y Y 座標（螢幕座標）
     * @param plotArea 圖表區域
     * @return 是否命中
     */
    public abstract boolean hitTest(double x, double y, Rectangle2D plotArea);
    
    /**
     * 獲取對象的邊界框
     * @param plotArea 圖表區域
     * @return 邊界矩形
     */
    public abstract Rectangle2D getBounds(Rectangle2D plotArea);
    
    /**
     * 移動對象
     * @param dx X 方向偏移量
     * @param dy Y 方向偏移量
     * @param plotArea 圖表區域
     */
    public abstract void move(double dx, double dy, Rectangle2D plotArea);
    
    /**
     * 克隆對象
     * @return 克隆的對象
     */
    public abstract DrawingObject clone();
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Color getColor() {
        return color;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public BasicStroke getStroke() {
        return stroke;
    }
    
    public void setStroke(BasicStroke stroke) {
        this.stroke = stroke;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 繪製選擇框（當對象被選中時）
     * @param g2 Graphics2D 對象
     * @param bounds 邊界矩形
     */
    protected void drawSelectionBox(Graphics2D g2, Rectangle2D bounds) {
        if (selected) {
            g2.setColor(new Color(0, 120, 215, 100));
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
                10.0f, new float[]{5.0f}, 0.0f));
            g2.draw(bounds);
            
            // 繪製控制點
            int handleSize = 6;
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.0f));
            
            // 四個角的控制點
            drawHandle(g2, bounds.getMinX(), bounds.getMinY(), handleSize);
            drawHandle(g2, bounds.getMaxX(), bounds.getMinY(), handleSize);
            drawHandle(g2, bounds.getMinX(), bounds.getMaxY(), handleSize);
            drawHandle(g2, bounds.getMaxX(), bounds.getMaxY(), handleSize);
        }
    }
    
    /**
     * 繪製控制點
     */
    private void drawHandle(Graphics2D g2, double x, double y, int size) {
        int halfSize = size / 2;
        g2.fillRect((int)(x - halfSize), (int)(y - halfSize), size, size);
        g2.setColor(Color.BLACK);
        g2.drawRect((int)(x - halfSize), (int)(y - halfSize), size, size);
        g2.setColor(Color.WHITE);
    }
}

