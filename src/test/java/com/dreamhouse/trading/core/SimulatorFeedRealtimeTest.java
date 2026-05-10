package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Tick;
import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorFeedRealtimeTest {

    @Test
    void subscribedWatchlistSymbolsReceiveRealtimeTicks() throws Exception {
        SimulatorFeed feed = new SimulatorFeed();
        List<String> symbols = List.of("2330.TW", "2317.TW", "2454.TW");
        CountDownLatch latch = new CountDownLatch(symbols.size());
        Set<String> receivedSymbols = ConcurrentHashMap.newKeySet();

        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                if (receivedSymbols.add(tick.getSymbol())) {
                    latch.countDown();
                }
            }
        };

        try {
            for (String symbol : symbols) {
                feed.subscribe(symbol, listener);
            }

            feed.start();

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All subscribed symbols should receive ticks");
            assertEquals(Set.copyOf(symbols), receivedSymbols);
        } finally {
            feed.stop();
        }
    }

    @Test
    void realtimeTicksUpdateHistoricalMinuteBars() throws Exception {
        SimulatorFeed feed = new SimulatorFeed();
        String symbol = "2330.TW";
        CountDownLatch latch = new CountDownLatch(1);

        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                latch.countDown();
            }
        };

        try {
            feed.subscribe(symbol, listener);
            double initialClose = feed.fetchHistoricalBars(symbol, Timeframe.M1, 1).get(0).getClose();

            feed.start();

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscribed symbol should receive realtime ticks");
            Thread.sleep(1200);

            List<Bar> latestBars = feed.fetchHistoricalBars(symbol, Timeframe.M1, 2);
            Bar latest = latestBars.get(latestBars.size() - 1);

            assertTrue(latest.getVolume() > 0, "Realtime bar should accumulate tick volume");
            assertTrue(latest.getClose() > 0, "Realtime bar should have a valid close price");
            assertTrue(latest.getClose() != initialClose, "Realtime ticks should change the latest historical close");
        } finally {
            feed.stop();
        }
    }
}
