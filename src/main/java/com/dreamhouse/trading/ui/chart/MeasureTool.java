package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;

/**
 * 測量工具
 * 用於測量價格變化、漲跌幅、時間跨度和K線數量
 */
public class MeasureTool extends DrawingObject {

    private static final long serialVersionUID = 1L;

    // 測量起點和終點（螢幕座標）
    private double x1, y1;  // 起點
    private double x2, y2;  // 終點

    // 價格數據（用於計算漲跌幅）
    private double price1;  // 起點價格
    private double price2;  // 終點價格

    // 時間數據（用於計算時間跨度和K線數量）
    private long time1;     // 起點時間（毫秒時間戳）
    private long time2;     // 終點時間
    private int barCount;   // K線數量

    // 格式化工具
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.00");

    public MeasureTool(double x1, double y1, double x2, double y2) {
        super();
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.price1 = 0;
        this.price2 = 0;
        this.time1 = 0;
        this.time2 = 0;
        this.barCount = 0;

        // 測量工具使用虛線
        this.stroke = new BasicStroke(
            1.5f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[]{5.0f, 5.0f},
            0.0f
        );
        this.color = new Color(255, 165, 0);  // 橙色
    }

    @Override
    public void draw(Graphics2D g2, Rectangle2D plotArea) {
        if (!visible) return;

        // 儲存原始設定
        Color originalColor = g2.getColor();
        Stroke originalStroke = g2.getStroke();
        Font originalFont = g2.getFont();

        // 設定繪圖屬性
        g2.setColor(color);
        g2.setStroke(stroke);

        // 繪製測量線
        Line2D line = new Line2D.Double(x1, y1, x2, y2);
        g2.draw(line);

        // 繪製端點
        drawEndPoint(g2, x1, y1);
        drawEndPoint(g2, x2, y2);

        // 繪製測量資訊
        drawMeasurementInfo(g2);

        // 恢復原始設定
        g2.setColor(originalColor);
        g2.setStroke(originalStroke);
        g2.setFont(originalFont);
    }

    /**
     * 繪製端點
     */
    private void drawEndPoint(Graphics2D g2, double x, double y) {
        int size = 10;
        int halfSize = size / 2;

        // 繪製圓形端點
        g2.setColor(new Color(255, 165, 0, 150));
        g2.fillOval((int)(x - halfSize), (int)(y - halfSize), size, size);

        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval((int)(x - halfSize), (int)(y - halfSize), size, size);
    }

    /**
     * 繪製測量資訊標籤
     */
    private void drawMeasurementInfo(Graphics2D g2) {
        // 計算標籤位置（線段中點偏上）
        double midX = (x1 + x2) / 2;
        double midY = (y1 + y2) / 2;

        // 計算測量數據
        double priceDiff = price2 - price1;
        double priceChange = (price1 != 0) ? (priceDiff / price1) * 100 : 0;
        long timeDiff = Math.abs(time2 - time1);

        // 構建標籤文字
        StringBuilder label = new StringBuilder();

        // 價格變化
        label.append(String.format("Δ Price: %s", PRICE_FORMAT.format(priceDiff)));

        // 漲跌幅
        if (price1 != 0) {
            String sign = priceChange >= 0 ? "+" : "";
            label.append(String.format(" (%s%s%%)", sign, PERCENT_FORMAT.format(priceChange)));
        }

        // K線數量
        if (barCount > 0) {
            label.append(String.format(" | %d Bars", barCount));
        }

        // 時間跨度
        if (timeDiff > 0) {
            label.append(String.format(" | %s", formatTimeDiff(timeDiff)));
        }

        // 繪製標籤背景
        String labelText = label.toString();
        g2.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(labelText);
        int textHeight = fm.getHeight();

        int padding = 8;
        int labelX = (int)(midX - textWidth / 2);
        int labelY = (int)(midY - 30);

        // 繪製半透明背景
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(
            labelX - padding,
            labelY - textHeight,
            textWidth + padding * 2,
            textHeight + padding,
            8, 8
        );

        // 繪製邊框
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawRoundRect(
            labelX - padding,
            labelY - textHeight,
            textWidth + padding * 2,
            textHeight + padding,
            8, 8
        );

        // 繪製文字
        g2.setColor(priceChange >= 0 ? new Color(0, 255, 0) : new Color(255, 0, 0));
        g2.drawString(labelText, labelX, labelY - 2);
    }

    /**
     * 格式化時間差
     */
    private String formatTimeDiff(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d Days", days);
        } else if (hours > 0) {
            return String.format("%d Hours", hours);
        } else if (minutes > 0) {
            return String.format("%d Mins", minutes);
        } else {
            return String.format("%d Secs", seconds);
        }
    }

    @Override
    public boolean hitTest(double x, double y, Rectangle2D plotArea) {
        // 計算點到線的距離
        double distance = Line2D.ptLineDist(x1, y1, x2, y2, x, y);

        // 檢查是否在線段範圍內
        double minX = Math.min(x1, x2) - 10;
        double maxX = Math.max(x1, x2) + 10;
        double minY = Math.min(y1, y2) - 10;
        double maxY = Math.max(y1, y2) + 10;

        boolean inRange = x >= minX && x <= maxX && y >= minY && y <= maxY;

        // 如果距離小於閾值且在範圍內，則命中
        return distance < 8.0 && inRange;
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

    @Override
    public DrawingObject clone() {
        MeasureTool clone = new MeasureTool(x1, y1, x2, y2);
        clone.setColor(this.color);
        clone.setStroke(this.stroke);
        clone.setPrice1(this.price1);
        clone.setPrice2(this.price2);
        clone.setTime1(this.time1);
        clone.setTime2(this.time2);
        clone.setBarCount(this.barCount);
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

    public double getPrice1() {
        return price1;
    }

    public void setPrice1(double price1) {
        this.price1 = price1;
    }

    public double getPrice2() {
        return price2;
    }

    public void setPrice2(double price2) {
        this.price2 = price2;
    }

    public long getTime1() {
        return time1;
    }

    public void setTime1(long time1) {
        this.time1 = time1;
    }

    public long getTime2() {
        return time2;
    }

    public void setTime2(long time2) {
        this.time2 = time2;
    }

    public int getBarCount() {
        return barCount;
    }

    public void setBarCount(int barCount) {
        this.barCount = barCount;
    }
}
