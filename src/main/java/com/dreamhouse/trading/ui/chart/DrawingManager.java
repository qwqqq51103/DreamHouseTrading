package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 繪圖工具管理器
 * 管理所有繪圖對象的添加、刪除、選擇和繪製
 */
public class DrawingManager {
    
    private final List<DrawingObject> drawings;
    private DrawingObject selectedObject;
    private DrawingTool currentTool;
    private DrawingObject tempObject;  // 正在繪製中的臨時對象
    
    // 繪製狀態
    private boolean isDrawing;
    private double startX, startY;
    
    public enum DrawingTool {
        NONE,           // 選擇工具
        TREND_LINE,     // 趨勢線
        HORIZONTAL_LINE,// 水平線
        VERTICAL_LINE,  // 垂直線（未來）
        RECTANGLE,      // 矩形（未來）
        MEASURE,        // 測量工具
        FIBONACCI       // 斐波那契回調
    }
    
    public DrawingManager() {
        this.drawings = new ArrayList<>();
        this.currentTool = DrawingTool.NONE;
        this.isDrawing = false;
    }
    
    /**
     * 繪製所有對象
     */
    public void drawAll(Graphics2D g2, Rectangle2D plotArea) {
        // 繪製已完成的對象
        for (DrawingObject obj : drawings) {
            if (obj.isVisible()) {
                obj.draw(g2, plotArea);
            }
        }
        
        // 繪製正在繪製中的臨時對象
        if (tempObject != null) {
            tempObject.draw(g2, plotArea);
        }
    }
    
    /**
     * 開始繪製
     */
    public void startDrawing(double x, double y, Rectangle2D plotArea) {
        if (currentTool == DrawingTool.NONE) {
            // 選擇模式：檢查是否點擊到對象
            // 這個方法在選擇模式下不應該被調用，但為了安全起見保留
            return;
        }
        
        isDrawing = true;
        startX = x;
        startY = y;
        
        // 根據當前工具創建臨時對象
        switch (currentTool) {
            case TREND_LINE:
                tempObject = new TrendLine(startX, startY, startX, startY);
                tempObject.setColor(new Color(0, 120, 215));  // 預設藍色
                break;

            case HORIZONTAL_LINE:
                tempObject = new HorizontalLine(startY);
                tempObject.setColor(new Color(255, 0, 0));  // 預設紅色
                break;

            case MEASURE:
                tempObject = new MeasureTool(startX, startY, startX, startY);
                tempObject.setColor(new Color(255, 165, 0));  // 橙色
                break;

            case FIBONACCI:
                tempObject = new FibonacciRetracement(startX, startY, startX, startY);
                tempObject.setColor(new Color(128, 128, 128));  // 灰色
                break;

            default:
                break;
        }
    }
    
    /**
     * 更新繪製（滑鼠拖曳時）
     */
    public void updateDrawing(double x, double y, Rectangle2D plotArea) {
        if (!isDrawing || tempObject == null) {
            return;
        }
        
        // 更新臨時對象的座標
        if (tempObject instanceof TrendLine) {
            TrendLine line = (TrendLine) tempObject;
            line.setX2(x);
            line.setY2(y);
        } else if (tempObject instanceof HorizontalLine) {
            HorizontalLine line = (HorizontalLine) tempObject;
            line.setPrice(y);
        } else if (tempObject instanceof MeasureTool) {
            MeasureTool measure = (MeasureTool) tempObject;
            measure.setX2(x);
            measure.setY2(y);
        } else if (tempObject instanceof FibonacciRetracement) {
            FibonacciRetracement fib = (FibonacciRetracement) tempObject;
            fib.setX2(x);
            fib.setY2(y);
        }
    }
    
    /**
     * 完成繪製
     */
    public void finishDrawing(double x, double y, Rectangle2D plotArea) {
        if (!isDrawing || tempObject == null) {
            return;
        }
        
        // 更新最終座標
        updateDrawing(x, y, plotArea);
        
        // 將臨時對象添加到繪圖列表
        drawings.add(tempObject);
        
        // 清理狀態
        tempObject = null;
        isDrawing = false;
        
        // 繪製完成後自動切換回選擇工具（可選）
        // currentTool = DrawingTool.NONE;
    }
    
    /**
     * 取消繪製
     */
    public void cancelDrawing() {
        tempObject = null;
        isDrawing = false;
    }
    
    /**
     * 選擇指定位置的對象
     * @return true 如果選中了對象，false 如果沒有選中
     */
    public boolean selectObjectAt(double x, double y, Rectangle2D plotArea) {
        // 取消當前選擇
        if (selectedObject != null) {
            selectedObject.setSelected(false);
        }
        
        // 從後往前查找（後繪製的對象在上層）
        for (int i = drawings.size() - 1; i >= 0; i--) {
            DrawingObject obj = drawings.get(i);
            if (obj.isVisible() && obj.hitTest(x, y, plotArea)) {
                obj.setSelected(true);
                selectedObject = obj;
                return true;
            }
        }
        
        // 沒有選中任何對象
        selectedObject = null;
        return false;
    }
    
    /**
     * 取消所有選擇
     */
    public void deselectAll() {
        for (DrawingObject obj : drawings) {
            obj.setSelected(false);
        }
        selectedObject = null;
    }
    
    /**
     * 移動選中的對象
     */
    public void moveSelected(double dx, double dy, Rectangle2D plotArea) {
        if (selectedObject != null) {
            selectedObject.move(dx, dy, plotArea);
        }
    }
    
    /**
     * 刪除選中的對象
     */
    public void deleteSelected() {
        if (selectedObject != null) {
            drawings.remove(selectedObject);
            selectedObject = null;
        }
    }
    
    /**
     * 刪除所有對象
     */
    public void clearAll() {
        drawings.clear();
        selectedObject = null;
        tempObject = null;
        isDrawing = false;
    }
    
    /**
     * 獲取所有指定類型的對象
     */
    public <T extends DrawingObject> List<T> getObjectsOfType(Class<T> type) {
        return drawings.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }
    
    // Getters and Setters
    
    public DrawingTool getCurrentTool() {
        return currentTool;
    }
    
    public void setCurrentTool(DrawingTool tool) {
        // 切換工具時取消當前繪製
        if (isDrawing) {
            cancelDrawing();
        }
        
        // 切換到非選擇工具時，取消所有選擇
        if (tool != DrawingTool.NONE) {
            deselectAll();
        }
        
        this.currentTool = tool;
    }
    
    public DrawingObject getSelectedObject() {
        return selectedObject;
    }
    
    public List<DrawingObject> getAllDrawings() {
        return new ArrayList<>(drawings);
    }
    
    public int getDrawingCount() {
        return drawings.size();
    }
    
    public boolean isDrawing() {
        return isDrawing;
    }
    
}

