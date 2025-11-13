package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.*;

import java.util.List;

public interface MarketDataListener {
    default void onTick(Tick tick) {}
    default void onBar(Bar bar) {}
    default void onDepthUpdate(List<DepthLevel> depth) {}
    default void onTrade(Trade trade) {}
    default void onNews(NewsItem news) {}
}

