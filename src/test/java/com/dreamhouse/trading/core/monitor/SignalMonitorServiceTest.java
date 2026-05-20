package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.SimulatorFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.finmind.FinMindAccessDeniedException;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalMonitorServiceTest {

    @Test
    void autoMonitorRiskDefaultsBlockEarlyEntryAndEnableStopLossCooldown() {
        SignalMonitorConfig config = SignalMonitorConfig.createDefault();

        assertTrue(config.isEarlyEntryBlockEnabled());
        assertEquals(LocalTime.of(9, 0), config.getEarlyEntryBlockStart());
        assertEquals(LocalTime.of(9, 15), config.getEarlyEntryBlockEnd());
        assertTrue(config.isStopLossCooldownEnabled());
        assertEquals(60, config.getStopLossCooldownMinutes());
        assertEquals(LocalTime.of(13, 5), config.getLatestAutoEntryTime());
        assertEquals(5, config.getDailyMaxAutoTrades());
    }

    @Test
    void batchScanPublishesEveryWatchlistSymbol() throws Exception {
        SignalMonitorConfig config = SignalMonitorConfig.createSimulationTestTemplate();
        config.setScanIntervalSeconds(1);
        config.setTimeframe(Timeframe.M1);
        config.setBarCount(60);
        config.setMinSignalIntervalMinutes(1);

        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.05);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);

        SignalMonitorService monitor = new SignalMonitorService(new SimulatorFeed(), config, decisionConfig);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<MarketScanResult>> batchRef = new AtomicReference<>();

        monitor.setOnScanResults(results -> {
            batchRef.set(results);
            latch.countDown();
        });

        try {
            monitor.start(List.of("2330.TW", "2317.TW", "2454.TW"));

            assertTrue(latch.await(8, TimeUnit.SECONDS), "Monitor should publish one batch scan result");
            Set<String> symbols = batchRef.get().stream().map(MarketScanResult::getSymbol).collect(Collectors.toSet());
            assertEquals(Set.of("2330.TW", "2317.TW", "2454.TW"), symbols);
        } finally {
            monitor.stop();
        }
    }

    @Test
    void finMindAccessFailurePausesLaterScheduledScans() throws Exception {
        SignalMonitorConfig config = SignalMonitorConfig.createSimulationTestTemplate();
        config.setScanIntervalSeconds(1);

        AccessDeniedFeed feed = new AccessDeniedFeed();
        SignalMonitorService monitor = new SignalMonitorService(feed, config, DecisionConfig.createDefault());
        CountDownLatch pausedLatch = new CountDownLatch(1);

        monitor.setOnStatusUpdate(status -> {
            if (status.contains("Market data scan paused for 15 minutes")) {
                pausedLatch.countDown();
            }
        });

        try {
            monitor.start(List.of("2330.TW", "2317.TW"));

            assertTrue(pausedLatch.await(4, TimeUnit.SECONDS), "Monitor should publish FinMind pause status");
            Thread.sleep(1_200);
            assertEquals(1, feed.getRequestCount(), "Cooldown should prevent later scheduled API requests");
        } finally {
            monitor.stop();
        }
    }

    private static class AccessDeniedFeed implements MarketDataFeed {
        private final java.util.concurrent.atomic.AtomicInteger requestCount = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public void subscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void unsubscribe(String symbol, MarketDataListener listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            requestCount.incrementAndGet();
            throw new FinMindAccessDeniedException("FinMind authentication or permission failed: ip banned", 403, "403");
        }

        private int getRequestCount() {
            return requestCount.get();
        }
    }
}
