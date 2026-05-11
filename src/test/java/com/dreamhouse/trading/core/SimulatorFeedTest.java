package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SimulatorFeedTest {

    @Test
    void fetchHistoricalBarsWorksForUnsubscribedWatchlistSymbol() {
        SimulatorFeed feed = new SimulatorFeed();

        List<Bar> bars = feed.fetchHistoricalBars("2330.TW", Timeframe.M1, 80);

        assertEquals(80, bars.size());
        assertFalse(bars.stream().anyMatch(bar -> bar.getClose() <= 0.0));
    }
}
