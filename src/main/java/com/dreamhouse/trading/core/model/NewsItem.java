package com.dreamhouse.trading.core.model;

import java.time.LocalDateTime;

public class NewsItem {
    private final String symbol;
    private final LocalDateTime timestamp;
    private final String source;
    private final String title;
    private final String url;
    
    public NewsItem(LocalDateTime timestamp, String source, String title, String url) {
        this(null, timestamp, source, title, url);
    }

    public NewsItem(String symbol, LocalDateTime timestamp, String source, String title, String url) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.source = source;
        this.title = title;
        this.url = url;
    }
    
    public String getSymbol() { return symbol; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
}

