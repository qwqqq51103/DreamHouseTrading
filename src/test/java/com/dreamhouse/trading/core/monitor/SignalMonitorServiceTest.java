package com.dreamhouse.trading.core.monitor;

import com.dreamhouse.trading.core.SimulatorFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import org.junit.jupiter.api.Test;

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
}
