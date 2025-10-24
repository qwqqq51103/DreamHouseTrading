package com.dreamhouse.trading.core.model;

import java.time.LocalDateTime;

public class NewsItem {
    private final LocalDateTime timestamp;
    private final String source;
    private final String title;
    private final String url;
    
    public NewsItem(LocalDateTime timestamp, String source, String title, String url) {
        this.timestamp = timestamp;
        this.source = source;
        this.title = title;
        this.url = url;
    }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
}

