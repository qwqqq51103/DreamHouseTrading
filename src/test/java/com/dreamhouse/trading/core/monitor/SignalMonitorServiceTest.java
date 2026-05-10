package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.SimulatorFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalMonitorServiceTest {

    @Test
    void monitorPublishesScanResultsForEverySymbol() throws Exception {
        SimulatorFeed feed = new SimulatorFeed();
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(1);
        config.setTimeframe(Timeframe.M1);
        config.setBarCount(30);

        SignalMonitorService monitor = new SignalMonitorService(feed, config);
        List<String> symbols = List.of("2330.TW", "2317.TW", "2454.TW");
        Set<String> scannedSymbols = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(symbols.size());

        monitor.setOnScanResult(result -> {
            if (scannedSymbols.add(result.getSymbol())) {
                latch.countDown();
            }
        });

        try {
            feed.start();
            monitor.start(symbols);

            assertTrue(latch.await(6, TimeUnit.SECONDS), "Monitor should scan every watched symbol");
            assertEquals(Set.copyOf(symbols), scannedSymbols);
        } finally {
            monitor.stop();
            feed.stop();
        }
    }

    @Test
    void monitorUsesProvidedDecisionConfigForScannerSignals() throws Exception {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(1);
        config.setTimeframe(Timeframe.M1);
        config.setBarCount(40);

        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.15);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);
        decisionConfig.getRiskConfig().setMaxConcurrentPositions(50);
        decisionConfig.getRiskConfig().setMaxPositionSizePercent(0.95);
        decisionConfig.getRiskConfig().setMinCashReservePercent(0.0);

        SignalMonitorService monitor = new SignalMonitorService(new FallingPriceFeed(), config, decisionConfig);
        CountDownLatch latch = new CountDownLatch(1);
        List<DecisionResult.Action> actions = new ArrayList<>();

        monitor.setOnSignalDetected((symbol, signal) -> {
            actions.add(signal.getAction());
            latch.countDown();
        });

        try {
            monitor.start(List.of("2330.TW"));

            assertTrue(latch.await(6, TimeUnit.SECONDS), "Low-threshold config should allow scanner signal");
            assertEquals(List.of(DecisionResult.Action.OPEN_LONG), actions);
        } finally {
            monitor.stop();
        }
    }

    @Test
    void monitorPublishesBatchBeforeTradingSignals() throws Exception {
        SignalMonitorConfig config = new SignalMonitorConfig();
        config.setScanIntervalSeconds(1);
        config.setTimeframe(Timeframe.M1);
        config.setBarCount(40);

        DecisionConfig decisionConfig = DecisionConfig.createAggressive();
        decisionConfig.setRegimeDetectionEnabled(false);
        decisionConfig.setTrendAnalysisEnabled(false);
        decisionConfig.setRiskManagementEnabled(false);
        decisionConfig.getVotingConfig().setLongEntryThreshold(0.15);
        decisionConfig.getVotingConfig().setMinVotingStrategies(1);
        decisionConfig.getRiskConfig().setMaxConcurrentPositions(50);
        decisionConfig.getRiskConfig().setMaxPositionSizePercent(0.95);
        decisionConfig.getRiskConfig().setMinCashReservePercent(0.0);

        SignalMonitorService monitor = new SignalMonitorService(new FallingPriceFeed(), config, decisionConfig);
        CountDownLatch signalLatch = new CountDownLatch(1);
        List<String> eventOrder = new CopyOnWriteArrayList<>();
        List<String> batchSymbols = new CopyOnWriteArrayList<>();

        monitor.setOnScanResults(results -> {
            eventOrder.add("batch");
            batchSymbols.addAll(results.stream().map(MarketScanResult::getSymbol).toList());
        });
        monitor.setOnSignalDetected((symbol, signal) -> {
            eventOrder.add("signal");
            signalLatch.countDown();
        });

        try {
            monitor.start(List.of("2330.TW", "2317.TW"));

            assertTrue(signalLatch.await(6, TimeUnit.SECONDS), "Signals should be emitted after batch scan");
            assertEquals("batch", eventOrder.get(0));
            assertTrue(batchSymbols.containsAll(List.of("2330.TW", "2317.TW")));
        } finally {
            monitor.stop();
        }
    }

    private static class FallingPriceFeed implements MarketDataFeed {
        @Override public void subscribe(String symbol, MarketDataListener listener) {}
        @Override public void unsubscribe(String symbol, MarketDataListener listener) {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isConnected() { return true; }

        @Override
        public List<Bar> fetchHistoricalBars(String symbol, Timeframe timeframe, int barCount) {
            List<Bar> bars = new ArrayList<>();
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            double price = 120.0;
            for (int i = 0; i < barCount; i++) {
                double next = price - 1.0;
                bars.add(new Bar(start.plusMinutes(i), price, price + 0.5, next - 0.5, next, 10_000 + i));
                price = next;
            }
            return bars;
        }
    }
}
