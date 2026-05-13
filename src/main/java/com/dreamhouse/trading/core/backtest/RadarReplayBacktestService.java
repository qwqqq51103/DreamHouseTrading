package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.MarketScannerService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays one trading session through the opportunity radar using already collected bars.
 */
public class RadarReplayBacktestService {

    private static final int DAY_TRADE_LOT_SIZE = 1000;
    private static final double DEFAULT_COMMISSION_RATE = 0.001425;
    private static final LocalTime FORCE_CLOSE_TIME = LocalTime.of(13, 25);

    private final double initialCapital;
    private final double commissionRate;
    private final LocalTime earlyEntryBlockStart;
    private final LocalTime earlyEntryBlockEnd;
    private final LocalTime latestAutoEntryTime;
    private final int stopLossCooldownMinutes;

    public RadarReplayBacktestService() {
        this(1_000_000.0, DEFAULT_COMMISSION_RATE);
    }

    public RadarReplayBacktestService(double initialCapital, double commissionRate) {
        this(initialCapital, commissionRate, LocalTime.of(9, 0), LocalTime.of(9, 10), 60);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            int stopLossCooldownMinutes) {
        this(initialCapital, commissionRate, earlyEntryBlockStart, earlyEntryBlockEnd, LocalTime.of(13, 10), stopLossCooldownMinutes);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            LocalTime latestAutoEntryTime,
            int stopLossCooldownMinutes) {
        this.initialCapital = initialCapital;
        this.commissionRate = Math.max(0.0, commissionRate);
        this.earlyEntryBlockStart = earlyEntryBlockStart != null ? earlyEntryBlockStart : LocalTime.of(9, 0);
        this.earlyEntryBlockEnd = earlyEntryBlockEnd != null ? earlyEntryBlockEnd : LocalTime.of(9, 10);
        this.latestAutoEntryTime = latestAutoEntryTime != null ? latestAutoEntryTime : LocalTime.of(13, 10);
        this.stopLossCooldownMinutes = Math.max(0, stopLossCooldownMinutes);
    }

    public BacktestResult replay(String symbol, List<Bar> sessionBars, MarketScannerService.ScanRequest scanRequest) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (sessionBars == null || sessionBars.size() < 2) {
            throw new IllegalArgumentException("At least two bars are required for radar replay");
        }

        List<Bar> bars = sessionBars.stream()
                .filter(bar -> bar != null && bar.getTimestamp() != null)
                .sorted(java.util.Comparator.comparing(Bar::getTimestamp))
                .toList();
        if (bars.size() < 2) {
            throw new IllegalArgumentException("No usable bars for radar replay");
        }

        BacktestResult result = new BacktestResult(
                bars.get(0).getTimestamp(),
                bars.get(bars.size() - 1).getTimestamp(),
                initialCapital);
        RollingBarFeed rollingFeed = new RollingBarFeed(symbol, bars);
        MarketScannerService scanner = new MarketScannerService(rollingFeed);
        MarketScannerService.ScanRequest effectiveRequest = scanRequest != null
                ? scanRequest
                : MarketScannerService.ScanRequest.createDefault();

        double cash = initialCapital;
        OpenPosition open = null;
        LocalDateTime cooldownUntil = null;

        Timeframe replayTimeframe = effectiveRequest.getTimeframe() != null
                ? effectiveRequest.getTimeframe()
                : Timeframe.M1;
        int warmupBars = Math.min(Math.max(20, replayTimeframe.getMinutes() >= 5 ? 12 : 30), bars.size());
        int lastEntryIndexExclusive = Math.max(0, bars.size() - 1);
        for (int index = 0; index < bars.size(); index++) {
            Bar bar = bars.get(index);
            LocalDateTime barTime = bar.getTimestamp();
            LocalDateTime decisionTime = barTime.plusMinutes(replayTimeframe.getMinutes());

            if (open != null) {
                ExitDecision exit = resolveExit(open, bar);
                if (exit != null) {
                    Trade sell = new Trade(
                            barTime,
                            symbol,
                            TradeType.SELL,
                            open.quantity(),
                            exit.price(),
                            commissionRate,
                            open.stopLoss(),
                            open.takeProfit(),
                            exit.reason());
                    result.addTrade(sell);
                    cash += sell.getNetProceeds();
                    if ("停損".equals(exit.reason())) {
                        cooldownUntil = barTime.plusMinutes(stopLossCooldownMinutes);
                    }
                    open = null;
                }
            }

            if (open == null
                    && index < lastEntryIndexExclusive
                    && index + 1 >= warmupBars
                    && !isEarlyEntryBlock(barTime.toLocalTime())
                    && (cooldownUntil == null || !barTime.isBefore(cooldownUntil))
                    && decisionTime.toLocalTime().isBefore(latestAutoEntryTime)
                    && decisionTime.toLocalTime().isBefore(FORCE_CLOSE_TIME)) {
                rollingFeed.setVisibleBarCount(index + 1);
                MarketScanResult scanResult = scanner.scan(symbol, effectiveRequest.barCount(index + 1));
                DecisionResult decision = scanResult != null ? scanResult.getDecisionResult() : null;
                if (decision != null && decision.getAction() == DecisionResult.Action.OPEN_LONG) {
                    double entryPrice = bar.getClose();
                    int quantity = DAY_TRADE_LOT_SIZE;
                    double requiredCash = entryPrice * quantity * (1 + commissionRate);
                    if (cash >= requiredCash) {
                        String entryReason = formatEntryReason(scanResult, decision);
                        Trade buy = new Trade(
                                decisionTime,
                                symbol,
                                TradeType.BUY,
                                quantity,
                                entryPrice,
                                commissionRate,
                                scanResult.getSuggestedStopLoss(),
                                scanResult.getSuggestedTakeProfit(),
                                entryReason);
                        result.addTrade(buy);
                        cash -= buy.getTotalCost();
                        open = new OpenPosition(
                                quantity,
                                entryPrice,
                                scanResult.getSuggestedStopLoss(),
                                scanResult.getSuggestedTakeProfit());
                    }
                }
            }

            double positionValue = open != null ? open.quantity() * bar.getClose() : 0.0;
            result.addSnapshot(barTime, cash + positionValue, cash, positionValue, open != null ? 1 : 0);
        }

        if (open != null) {
            Bar lastBar = bars.get(bars.size() - 1);
            Trade sell = new Trade(
                    lastBar.getTimestamp(),
                    symbol,
                    TradeType.SELL,
                    open.quantity(),
                    lastBar.getClose(),
                    commissionRate,
                    open.stopLoss(),
                    open.takeProfit(),
                    "回測結束平倉");
            result.addTrade(sell);
            cash += sell.getNetProceeds();
            result.addSnapshot(lastBar.getTimestamp(), cash, cash, 0.0, 0);
        }

        result.calculate();
        return result;
    }

    private String formatEntryReason(MarketScanResult scanResult, DecisionResult decision) {
        List<String> parts = new ArrayList<>();
        if (scanResult.getRawSignalSummary() != null && !scanResult.getRawSignalSummary().isBlank()) {
            parts.add(scanResult.getRawSignalSummary());
        }
        if (decision.getReason() != null && !decision.getReason().isBlank()) {
            parts.add(decision.getReason());
        }
        if (scanResult.getRiskRewardRatio() != null) {
            parts.add(String.format("RR %.2f", scanResult.getRiskRewardRatio()));
        }
        return String.join(" | ", parts);
    }

    private boolean isEarlyEntryBlock(LocalTime time) {
        return !time.isBefore(earlyEntryBlockStart) && time.isBefore(earlyEntryBlockEnd);
    }

    private ExitDecision resolveExit(OpenPosition open, Bar bar) {
        if (open.stopLoss() != null && bar.getLow() <= open.stopLoss()) {
            return new ExitDecision(open.stopLoss(), "停損");
        }
        if (open.takeProfit() != null && bar.getHigh() >= open.takeProfit()) {
            return new ExitDecision(open.takeProfit(), "停利");
        }
        if (!bar.getTimestamp().toLocalTime().isBefore(FORCE_CLOSE_TIME)) {
            return new ExitDecision(bar.getClose(), "13:25 當沖強制平倉");
        }
        return null;
    }

    private record OpenPosition(int quantity, double entryPrice, Double stopLoss, Double takeProfit) {
    }

    private record ExitDecision(double price, String reason) {
    }

    private static class RollingBarFeed implements MarketDataFeed {
        private final String symbol;
        private final List<Bar> bars;
        private int visibleBarCount;

        private RollingBarFeed(String symbol, List<Bar> bars) {
            this.symbol = symbol;
            this.bars = new ArrayList<>(bars);
            this.visibleBarCount = bars.size();
        }

        private void setVisibleBarCount(int visibleBarCount) {
            this.visibleBarCount = Math.max(0, Math.min(visibleBarCount, bars.size()));
        }

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
        public List<Bar> fetchHistoricalBars(String requestedSymbol, Timeframe timeframe, int barCount) {
            if (!symbol.equals(requestedSymbol) || visibleBarCount <= 0) {
                return List.of();
            }
            int end = Math.min(visibleBarCount, bars.size());
            int start = Math.max(0, end - Math.max(1, barCount));
            return new ArrayList<>(bars.subList(start, end));
        }

    }
}
