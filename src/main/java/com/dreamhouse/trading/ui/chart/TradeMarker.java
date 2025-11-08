package com.dreamhouse.trading.ui.chart;

import com.dreamhouse.trading.core.backtest.Trade;
import com.dreamhouse.trading.core.backtest.TradeType;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.chart.ui.TextAnchor;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 交易標記類
 * 在K線圖上顯示買賣交易點
 */
public class TradeMarker {
    
    private final Trade trade;
    private final RegularTimePeriod timePeriod;
    private final double price;
    private final Color markerColor;
    private final String markerText;
    
    // 標記樣式常數 - 增強版
    private static final double MARKER_SIZE = 16.0;  // 增大標記尺寸
    private static final double INNER_SIZE = 12.0;   // 內圈尺寸
    
    // 熱力圖風格顏色 - 更鮮豔和對比強烈
    private static final Color BUY_COLOR_OUTER = new Color(0, 255, 0);      // 亮綠色外圈
    private static final Color BUY_COLOR_INNER = new Color(34, 139, 34);    // 森林綠內圈
    private static final Color BUY_COLOR_GLOW = new Color(0, 255, 0, 100);  // 綠色光暈
    
    private static final Color SELL_COLOR_OUTER = new Color(255, 0, 0);     // 亮紅色外圈
    private static final Color SELL_COLOR_INNER = new Color(178, 34, 34);   // 火磚紅內圈
    private static final Color SELL_COLOR_GLOW = new Color(255, 0, 0, 100); // 紅色光暈
    
    private static final Font MARKER_FONT = new Font("微軟正黑體", Font.BOLD, 12);
    private static final BasicStroke THICK_STROKE = new BasicStroke(3.0f);
    private static final BasicStroke GLOW_STROKE = new BasicStroke(6.0f);
    
    /**
     * 構造函數
     */
    public TradeMarker(Trade trade, RegularTimePeriod timePeriod) {
        this.trade = trade;
        this.timePeriod = timePeriod;
        this.price = trade.getPrice();
        
        // 根據交易類型設定顏色和文字
        if (trade.getType() == TradeType.BUY) {
            this.markerColor = BUY_COLOR_OUTER;
            this.markerText = "B";
        } else {
            this.markerColor = SELL_COLOR_OUTER;
            this.markerText = "S";
        }
    }
    
    /**
     * 創建形狀標記 (多層熱力圖效果)
     */
    public java.util.List<XYShapeAnnotation> createShapeAnnotations() {
        java.util.List<XYShapeAnnotation> annotations = new ArrayList<>();
        long timeMillis = timePeriod.getFirstMillisecond();
        
        // 選擇顏色方案
        Color glowColor, outerColor, innerColor;
        if (trade.getType() == TradeType.BUY) {
            glowColor = BUY_COLOR_GLOW;
            outerColor = BUY_COLOR_OUTER;
            innerColor = BUY_COLOR_INNER;
        } else {
            glowColor = SELL_COLOR_GLOW;
            outerColor = SELL_COLOR_OUTER;
            innerColor = SELL_COLOR_INNER;
        }
        
        // 1. 光暈效果 (最大圓圈，半透明)
        Ellipse2D.Double glowCircle = new Ellipse2D.Double(
            timeMillis - MARKER_SIZE, 
            price - MARKER_SIZE, 
            MARKER_SIZE * 2, 
            MARKER_SIZE * 2
        );
        annotations.add(new XYShapeAnnotation(glowCircle, GLOW_STROKE, glowColor, glowColor));
        
        // 2. 外圈 (亮色邊框)
        Ellipse2D.Double outerCircle = new Ellipse2D.Double(
            timeMillis - MARKER_SIZE/2, 
            price - MARKER_SIZE/2, 
            MARKER_SIZE, 
            MARKER_SIZE
        );
        annotations.add(new XYShapeAnnotation(outerCircle, THICK_STROKE, outerColor, outerColor));
        
        // 3. 內圈 (深色填充)
        Ellipse2D.Double innerCircle = new Ellipse2D.Double(
            timeMillis - INNER_SIZE/2, 
            price - INNER_SIZE/2, 
            INNER_SIZE, 
            INNER_SIZE
        );
        annotations.add(new XYShapeAnnotation(innerCircle, new BasicStroke(1.0f), innerColor, innerColor));
        
        return annotations;
    }
    
    /**
     * 創建形狀標記 (向下兼容方法)
     */
    public XYShapeAnnotation createShapeAnnotation() {
        // 返回主要的外圈標記以保持向下兼容
        java.util.List<XYShapeAnnotation> annotations = createShapeAnnotations();
        return annotations.get(1); // 返回外圈
    }
    
    /**
     * 創建文字標記 (B/S) - 增強版
     */
    public java.util.List<XYTextAnnotation> createTextAnnotations() {
        java.util.List<XYTextAnnotation> annotations = new ArrayList<>();
        long timeMillis = timePeriod.getFirstMillisecond();
        
        // 1. 文字陰影效果 (黑色，稍微偏移)
        XYTextAnnotation shadowText = new XYTextAnnotation(markerText, timeMillis + 0.5, price - 0.1);
        shadowText.setFont(MARKER_FONT);
        shadowText.setPaint(Color.BLACK);
        shadowText.setTextAnchor(TextAnchor.CENTER);
        annotations.add(shadowText);
        
        // 2. 主要文字 (白色，粗體)
        XYTextAnnotation mainText = new XYTextAnnotation(markerText, timeMillis, price);
        mainText.setFont(MARKER_FONT);
        mainText.setPaint(Color.WHITE);
        mainText.setTextAnchor(TextAnchor.CENTER);
        annotations.add(mainText);
        
        return annotations;
    }
    
    /**
     * 創建文字標記 (向下兼容方法)
     */
    public XYTextAnnotation createTextAnnotation() {
        // 返回主要文字以保持向下兼容
        java.util.List<XYTextAnnotation> annotations = createTextAnnotations();
        return annotations.get(1); // 返回主要文字
    }
    
    /**
     * 獲取工具提示文字
     */
    public String getTooltipText() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append(String.format("<html><b>%s交易</b><br>", 
                      trade.getType() == TradeType.BUY ? "買入" : "賣出"));
        tooltip.append(String.format("時間: %s<br>", 
                      trade.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))));
        tooltip.append(String.format("價格: %.2f<br>", trade.getPrice()));
        tooltip.append(String.format("數量: %d<br>", trade.getQuantity()));
        tooltip.append(String.format("金額: %.2f<br>", trade.getTotalAmount()));
        
        // 如果有停利停損資訊
        if (trade.getStopLoss() != null) {
            tooltip.append(String.format("停損: %.2f<br>", trade.getStopLoss()));
        }
        if (trade.getTakeProfit() != null) {
            tooltip.append(String.format("停利: %.2f<br>", trade.getTakeProfit()));
        }
        if (trade.getExitReason() != null && !trade.getExitReason().isEmpty()) {
            tooltip.append(String.format("出場原因: %s<br>", trade.getExitReason()));
        }
        
        tooltip.append("</html>");
        return tooltip.toString();
    }
    
    // Getters
    public Trade getTrade() { return trade; }
    public RegularTimePeriod getTimePeriod() { return timePeriod; }
    public double getPrice() { return price; }
    public Color getMarkerColor() { return markerColor; }
    public String getMarkerText() { return markerText; }
    
    /**
     * 靜態工具方法：從交易列表創建標記列表
     */
    public static List<TradeMarker> createMarkersFromTrades(List<Trade> trades, TimeFrameConverter converter) {
        List<TradeMarker> markers = new ArrayList<>();
        
        for (Trade trade : trades) {
            try {
                RegularTimePeriod period = converter.convertToTimePeriod(trade.getTimestamp());
                TradeMarker marker = new TradeMarker(trade, period);
                markers.add(marker);
            } catch (Exception e) {
                System.err.println("創建交易標記失敗: " + e.getMessage());
            }
        }
        
        return markers;
    }
    
    /**
     * 時間框架轉換器接口
     */
    public interface TimeFrameConverter {
        RegularTimePeriod convertToTimePeriod(LocalDateTime dateTime);
    }
}
