package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.util.I18n;

import javax.swing.*;
import java.awt.*;

public class StatusBar extends JPanel {
    private final JLabel connectionLabel;
    private final JLabel priceLabel;
    private final JLabel symbolLabel;
    private final JLabel timeframeLabel;
    private final JLabel fpsLabel;
    private final JLabel messageLabel;
    
    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 2));
        setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
        connectionLabel = new JLabel("● " + I18n.get("status.simulated"));
        connectionLabel.setForeground(new Color(34, 177, 76));
        add(connectionLabel);
        
        add(new JSeparator(SwingConstants.VERTICAL));
        
        symbolLabel = new JLabel(I18n.get("status.symbol") + " --");
        add(symbolLabel);
        
        priceLabel = new JLabel(I18n.get("status.last") + " --");
        add(priceLabel);
        
        timeframeLabel = new JLabel(I18n.get("status.tf") + " 1m");
        add(timeframeLabel);
        
        add(new JSeparator(SwingConstants.VERTICAL));
        
        fpsLabel = new JLabel(I18n.get("status.fps") + " 0");
        add(fpsLabel);
        
        add(new JSeparator(SwingConstants.VERTICAL));
        
        messageLabel = new JLabel(I18n.get("status.ready"));
        messageLabel.setForeground(new Color(100, 100, 100));
        add(messageLabel);
    }
    
    public void setConnectionStatus(boolean connected) {
        if (connected) {
            connectionLabel.setText("● " + I18n.get("status.simulated"));
            connectionLabel.setForeground(new Color(34, 177, 76));
        } else {
            connectionLabel.setText("● " + I18n.get("status.disconnected"));
            connectionLabel.setForeground(Color.RED);
        }
    }
    
    public void setLastPrice(double price, double change) {
        Color color = change >= 0 ? new Color(34, 177, 76) : new Color(237, 28, 36);
        priceLabel.setText(String.format(I18n.get("status.last") + " %.2f (%.2f%%)", price, change));
        priceLabel.setForeground(color);
    }
    
    public void setSymbol(String symbol) {
        symbolLabel.setText(I18n.get("status.symbol") + " " + symbol);
    }
    
    public void setTimeframe(String timeframe) {
        timeframeLabel.setText(I18n.get("status.tf") + " " + timeframe);
    }
    
    public void setFPS(int fps) {
        fpsLabel.setText(I18n.get("status.fps") + " " + fps);
    }
    
    /**
     * 設定通用狀態訊息
     * @param message 要顯示的訊息
     */
    public void setText(String message) {
        messageLabel.setText(message);
    }
    
    /**
     * 清除狀態訊息
     */
    public void clearMessage() {
        messageLabel.setText("");
    }
}

