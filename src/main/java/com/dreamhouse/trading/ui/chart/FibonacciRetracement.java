package com.dreamhouse.trading.ui.chart;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;

/**
 * 斐波那契回調線工具
 * 顯示關鍵的支撐/阻力位：0%, 23.6%, 38.2%, 50%, 61.8%, 100%
 */
public class FibonacciRetracement extends DrawingObject {

    private static final long serialVersionUID = 1L;

    // 斐波那契比率
    private static final double[] FIB_LEVELS = {
        0.000,   // 0%
        0.236,   // 23.6%
        0.382,   // 38.2%
        0.500,   // 50%
        0.618,   // 61.8%
        0.786,   // 78.6%
        1.000    // 100%
    };

    private static final String[] FIB_LABELS = {
        "0.0%",
        "23.6%",
        "38.2%",
        "50.0%",
        "61.8%",
        "78.6%",
        "100.0%"
    };

    // 顏色配置
    private static final Color[] FIB_COLORS = {
        new Color(255, 0, 0),      // 0% - 紅色
        new Color(255, 153, 0),    // 23.6% - 橙色
        new Color(255, 255, 0),    // 38.2% - 黃色
        new Color(0, 255, 0),      // 50% - 綠色
        new Color(0, 255, 255),    // 61.8% - 青色
        new Color(138, 43, 226),   // 78.6% - 紫色
        new Color(255, 0, 0)       // 100% - 紅色
    };

    // 起點和終點
    private double x1, y1;  // 起點 (100%)
    private double x2, y2;  // 終點 (0%)

    // 價格數據
    private double price1;  // 起點價格
    private double price2;  // 終點價格

    // 是否顯示標籤
    private boolean showLabels;

    // 格式化工具
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");

    public FibonacciRetracement(double x1, double y1, double x2, double y2) {
        super();
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.price1 = 0;
        this.price2 = 0;
        this.showLabels = true;

        this.stroke = new BasicStroke(1.5f);
        this.color = new Color(128, 128, 128);  // 預設灰色
    }

    @Override
    public void draw(Graphics2D g2, Rectangle2D plotArea) {
        if (!visible) return;

        // 儲存原始設定
        Color originalColor = g2.getColor();
        Stroke originalStroke = g2.getStroke();
        Font originalFont = g2.getFont();

        // 設定字體
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();

        // 計算價格範圍
        double priceRange = price2 - price1;

        // 計算X範圍（用於繪製水平線）
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);

        // 繪製每個斐波那契水平
        for (int i = 0; i < FIB_LEVELS.length; i++) {
            double level = FIB_LEVELS[i];
            String label = FIB_LABELS[i];
            Color levelColor = FIB_COLORS[i];

            // 計算價格和Y座標
            double price = price1 + priceRange * level;
            double y = y1 + (y2 - y1) * level;

            // 繪製水平線
            g2.setColor(new Color(levelColor.getRed(), levelColor.getGreen(),
                                  levelColor.getBlue(), 150));
            g2.setStroke(new BasicStroke(
                1.5f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10.0f,
                new float[]{5.0f, 5.0f},
                0.0f
            ));

            Line2D line = new Line2D.Double(minX, y, maxX, y);
            g2.draw(line);

            // 繪製標籤
            if (showLabels && price != 0) {
                String labelText = String.format("%s (%s)", label, PRICE_FORMAT.format(price));
                int textWidth = fm.stringWidth(labelText);
                int textHeight = fm.getHeight();

                // 繪製標籤背景
                int padding = 4;
                int labelX = (int)maxX + 5;
                int labelY = (int)y;

                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRoundRect(
                    labelX,
                    labelY - textHeight + 3,
                    textWidth + padding * 2,
                    textHeight,
                    4, 4
                );

                // 繪製標籤文字
                g2.setColor(levelColor);
                g2.drawString(labelText, labelX + padding, labelY);
            }
        }

        // 繪製端點標記
        if (selected) {
            drawEndPoint(g2, x1, y1, "Start (100%)");
            drawEndPoint(g2, x2, y2, "End (0%)");
        }

        // 恢復原始設定
        g2.setColor(originalColor);
        g2.setStroke(originalStroke);
        g2.setFont(originalFont);
    }

    /**
     * 繪製端點
     */
    private void drawEndPoint(Graphics2D g2, double x, double y, String label) {
        int size = 10;
        int halfSize = size / 2;

        // 繪製圓形端點
        g2.setColor(new Color(255, 0, 0, 150));
        g2.fillOval((int)(x - halfSize), (int)(y - halfSize), size, size);

        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval((int)(x - halfSize), (int)(y - halfSize), size, size);

        // 繪製標籤
        if (label != null) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.drawString(label, (int)x + 10, (int)y - 5);
        }
    }

    @Override
    public boolean hitTest(double x, double y, Rectangle2D plotArea) {
        // 檢查是否點擊任何斐波那契水平線
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);

        // 檢查X範圍
        if (x < minX - 10 || x > maxX + 10) {
            return false;
        }

        // 檢查是否接近任何水平線
        for (double level : FIB_LEVELS) {
            double lineY = y1 + (y2 - y1) * level;
            if (Math.abs(y - lineY) < 5.0) {
                return true;
            }
        }

        return false;
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
        FibonacciRetracement clone = new FibonacciRetracement(x1, y1, x2, y2);
        clone.setColor(this.color);
        clone.setStroke(this.stroke);
        clone.setPrice1(this.price1);
        clone.setPrice2(this.price2);
        clone.setShowLabels(this.showLabels);
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

    public boolean isShowLabels() {
        return showLabels;
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }
}
