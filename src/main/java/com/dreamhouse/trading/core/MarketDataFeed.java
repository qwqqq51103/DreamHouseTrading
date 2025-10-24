package com.dreamhouse.trading.core;

public interface MarketDataFeed {
    void subscribe(String symbol, MarketDataListener listener);
    void unsubscribe(String symbol, MarketDataListener listener);
    void start();
    void stop();
    boolean isConnected();
}

