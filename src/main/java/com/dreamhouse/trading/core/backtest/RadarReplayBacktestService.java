package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataListener;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.monitor.AutoMonitorExecutionGate;
import com.dreamhouse.trading.core.scanner.MarketScanResult;
import com.dreamhouse.trading.core.scanner.MarketScannerService;
import com.dreamhouse.trading.core.scanner.RadarStrategyConfig;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replays one trading session through the opportunity radar using already collected bars.
 */
public class RadarReplayBacktestService {

    private static final int DAY_TRADE_LOT_SIZE = 1000;
    private static final double DEFAULT_COMMISSION_RATE = 0.001425;
    private static final double DEFAULT_DAY_TRADE_SELL_TAX_RATE = 0.0015;
    private static final double DEFAULT_SLIPPAGE_RATE = 0.0005;
    private static final LocalTime FORCE_CLOSE_TIME = LocalTime.of(13, 25);

    private final double initialCapital;
    private final double commissionRate;
    private final double dayTradeSellTaxRate;
    private final double slippageRate;
    private final LocalTime earlyEntryBlockStart;
    private final LocalTime earlyEntryBlockEnd;
    private final LocalTime latestAutoEntryTime;
    private final int stopLossCooldownMinutes;
    private final double dailyMaxLoss;
    private final int dailyMaxStopLossCount;
    private final int consecutiveLossLimit;
    private final boolean disableTradingAfterLossLimit;
    private final int dailyMaxAutoTrades;
    private final int entryPacingMinutes;
    private final boolean oneEntryPerFiveMinuteBar;

    public RadarReplayBacktestService() {
        this(1_000_000.0, DEFAULT_COMMISSION_RATE);
    }

    public RadarReplayBacktestService(double initialCapital, double commissionRate) {
        this(initialCapital, commissionRate, LocalTime.of(9, 0), LocalTime.of(9, 15), 60);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            int stopLossCooldownMinutes) {
        this(initialCapital, commissionRate, earlyEntryBlockStart, earlyEntryBlockEnd, LocalTime.of(13, 5), stopLossCooldownMinutes);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            LocalTime latestAutoEntryTime,
            int stopLossCooldownMinutes) {
        this(initialCapital, commissionRate, DEFAULT_DAY_TRADE_SELL_TAX_RATE, DEFAULT_SLIPPAGE_RATE,
                earlyEntryBlockStart, earlyEntryBlockEnd, latestAutoEntryTime, stopLossCooldownMinutes,
                -3_000.0, 3, 3, true, 5, 5, true);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            double dayTradeSellTaxRate,
            double slippageRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            LocalTime latestAutoEntryTime,
            int stopLossCooldownMinutes,
            double dailyMaxLoss,
            int dailyMaxStopLossCount,
            int consecutiveLossLimit,
            boolean disableTradingAfterLossLimit) {
        this(initialCapital, commissionRate, dayTradeSellTaxRate, slippageRate,
                earlyEntryBlockStart, earlyEntryBlockEnd, latestAutoEntryTime, stopLossCooldownMinutes,
                dailyMaxLoss, dailyMaxStopLossCount, consecutiveLossLimit, disableTradingAfterLossLimit,
                5, 5, true);
    }

    public RadarReplayBacktestService(
            double initialCapital,
            double commissionRate,
            double dayTradeSellTaxRate,
            double slippageRate,
            LocalTime earlyEntryBlockStart,
            LocalTime earlyEntryBlockEnd,
            LocalTime latestAutoEntryTime,
            int stopLossCooldownMinutes,
            double dailyMaxLoss,
            int dailyMaxStopLossCount,
            int consecutiveLossLimit,
            boolean disableTradingAfterLossLimit,
            int dailyMaxAutoTrades,
            int entryPacingMinutes,
            boolean oneEntryPerFiveMinuteBar) {
        this.initialCapital = initialCapital;
        this.commissionRate = Math.max(0.0, commissionRate);
        this.dayTradeSellTaxRate = Math.max(0.0, dayTradeSellTaxRate);
        this.slippageRate = Math.max(0.0, slippageRate);
        this.earlyEntryBlockStart = earlyEntryBlockStart != null ? earlyEntryBlockStart : LocalTime.of(9, 0);
        this.earlyEntryBlockEnd = earlyEntryBlockEnd != null ? earlyEntryBlockEnd : LocalTime.of(9, 15);
        this.latestAutoEntryTime = latestAutoEntryTime != null ? latestAutoEntryTime : LocalTime.of(13, 5);
        this.stopLossCooldownMinutes = Math.max(0, stopLossCooldownMinutes);
        this.dailyMaxLoss = Math.min(0.0, dailyMaxLoss);
        this.dailyMaxStopLossCount = Math.max(0, dailyMaxStopLossCount);
        this.consecutiveLossLimit = Math.max(0, consecutiveLossLimit);
        this.disableTradingAfterLossLimit = disableTradingAfterLossLimit;
        this.dailyMaxAutoTrades = Math.max(1, dailyMaxAutoTrades);
        this.entryPacingMinutes = Math.max(0, entryPacingMinutes);
        this.oneEntryPerFiveMinuteBar = oneEntryPerFiveMinuteBar;
    }

    public BacktestResult replay(String symbol, List<Bar> sessionBars, MarketScannerService.ScanRequest scanRequest) {
        return replay(symbol, List.of(), sessionBars, scanRequest);
    }

    public BacktestResult replay(
            String symbol,
            List<Bar> warmupBars,
            List<Bar> sessionBars,
            MarketScannerService.ScanRequest scanRequest) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (sessionBars == null || sessionBars.size() < 2) {
            throw new IllegalArgumentException("At least two bars are required for radar replay");
        }

        List<Bar> session = sessionBars.stream()
                .filter(bar -> bar != null && bar.getTimestamp() != null)
                .sorted(java.util.Comparator.comparing(Bar::getTimestamp))
                .toList();
        if (session.size() < 2) {
            throw new IllegalArgumentException("No usable bars for radar replay");
        }
        List<Bar> bars = mergeWarmupBars(warmupBars, session);
        int sessionStartIndex = Math.max(0, bars.size() - session.size());

        BacktestResult result = new BacktestResult(
                session.get(0).getTimestamp(),
                session.get(session.size() - 1).getTimestamp(),
                initialCapital);
        RollingBarFeed rollingFeed = new RollingBarFeed(symbol, bars);
        MarketScannerService scanner = new MarketScannerService(rollingFeed);
        MarketScannerService.ScanRequest effectiveRequest = scanRequest != null
                ? scanRequest
                : MarketScannerService.ScanRequest.createDefault();
        Timeframe replayTimeframe = effectiveRequest.getTimeframe() != null
                ? effectiveRequest.getTimeframe()
                : Timeframe.M1;
        recordWarmupDiagnostic(result, symbol, warmupBars, session.get(0), replayTimeframe, effectiveRequest);

        double cash = initialCapital;
        OpenPosition open = null;
        LocalDateTime cooldownUntil = null;
        double realizedPnl = 0.0;
        int stopLossCount = 0;
        int consecutiveLosses = 0;
        boolean tradingHalted = false;
        int autoEntryCount = 0;
        LocalDateTime lastEntryTime = null;
        java.util.Set<String> entryBuckets = new java.util.HashSet<>();

        int minimumWarmupBars = Math.min(Math.max(20, replayTimeframe.getMinutes() >= 5 ? 12 : 30), bars.size());
        int lastEntryIndexExclusive = Math.max(0, bars.size() - 1);
        for (int index = sessionStartIndex; index < bars.size(); index++) {
            Bar bar = bars.get(index);
            LocalDateTime barTime = bar.getTimestamp();
            LocalDateTime decisionTime = barTime.plusMinutes(replayTimeframe.getMinutes());

            if (open != null) {
                ExitDecision exit = resolveExit(open, bars, index);
                if (exit != null) {
                    Trade sell = createSellTrade(
                            barTime,
                            symbol,
                            open.quantity(),
                            exit.price(),
                            open.stopLoss(),
                            open.takeProfit(),
                            exit.reason());
                    result.addTrade(sell);
                    cash += sell.getNetProceeds();
                    double tradePnl = sell.getNetProceeds() - open.entryTrade().getTotalCost();
                    realizedPnl += tradePnl;
                    if (tradePnl < 0.0) {
                        consecutiveLosses++;
                    } else {
                        consecutiveLosses = 0;
                    }
                    if (exit.type() == ExitType.STOP_LOSS) {
                        stopLossCount++;
                        cooldownUntil = barTime.plusMinutes(stopLossCooldownMinutes);
                    }
                    if (shouldHaltTrading(realizedPnl, stopLossCount, consecutiveLosses)) {
                        tradingHalted = true;
                    }
                    open = null;
                } else {
                    open = updateTrailingStop(open, bar);
                }
            }

            if (open == null
                    && !tradingHalted
                    && index < lastEntryIndexExclusive
                    && index + 1 >= minimumWarmupBars) {
                AutoMonitorExecutionGate.GateDecision gateDecision = AutoMonitorExecutionGate.evaluateOpenLong(
                        replayGateConfig(),
                        new AutoMonitorExecutionGate.GateState(
                                symbol,
                                decisionTime,
                                tradingHalted,
                                false,
                                cooldownUntil,
                                autoEntryCount,
                                lastEntryTime,
                                entryBuckets,
                                open != null ? 1 : 0,
                                1));
                rollingFeed.setVisibleBarCount(index + 1);
                MarketScannerService.ScanRequest rollingRequest = effectiveRequest.copy()
                        .barCount(Math.min(effectiveRequest.getBarCount(), index + 1))
                        .asOfTime(decisionTime);
                MarketScanResult scanResult = scanner.scan(symbol, rollingRequest);
                DecisionResult decision = scanResult != null ? scanResult.getDecisionResult() : null;
                if (!gateDecision.allowed()) {
                    if (decision != null && decision.getAction() == DecisionResult.Action.OPEN_LONG) {
                        recordSignalObservation(result, symbol, bars, index, scanResult, decision, true, gateDecision.reason());
                    }
                    continue;
                }
                if (decision != null && decision.getAction() == DecisionResult.Action.OPEN_LONG) {
                    Bar entryBar = bars.get(index + 1);
                    double entryPrice = entryBar.getOpen() > 0.0 ? entryBar.getOpen() : entryBar.getClose();
                    LocalDateTime entryTime = entryBar.getTimestamp();
                    int quantity = DAY_TRADE_LOT_SIZE;
                    String costBlockReason = resolveNetRewardBlockReason(
                            entryPrice,
                            quantity,
                            scanResult.getSuggestedTakeProfit());
                    if (costBlockReason != null) {
                        recordSignalObservation(result, symbol, bars, index, scanResult, decision, true, costBlockReason);
                        continue;
                    }
                    recordSignalObservation(result, symbol, bars, index, scanResult, decision, false, null);
                    Trade buy = createBuyTrade(
                            entryTime,
                            symbol,
                            quantity,
                            entryPrice,
                            scanResult.getSuggestedStopLoss(),
                            scanResult.getSuggestedTakeProfit(),
                            formatEntryReason(scanResult, decision));
                    double requiredCash = buy.getTotalCost();
                    if (cash >= requiredCash) {
                        result.addTrade(buy);
                        cash -= buy.getTotalCost();
                        autoEntryCount++;
                        lastEntryTime = entryTime;
                        entryBuckets.add(AutoMonitorExecutionGate.fiveMinuteBucket(entryTime));
                        open = new OpenPosition(
                                quantity,
                                entryPrice,
                                scanResult.getSuggestedStopLoss(),
                                scanResult.getSuggestedTakeProfit(),
                                buy);
                    }
                } else {
                    recordSignalObservation(result, symbol, bars, index, scanResult, decision, false, null);
                }
            }

            double positionValue = open != null ? open.quantity() * bar.getClose() : 0.0;
            result.addSnapshot(barTime, cash + positionValue, cash, positionValue, open != null ? 1 : 0);
        }

        if (open != null) {
            Bar lastBar = bars.get(bars.size() - 1);
            Trade sell = createSellTrade(
                    lastBar.getTimestamp(),
                    symbol,
                    open.quantity(),
                    lastBar.getClose(),
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

    private List<Bar> mergeWarmupBars(List<Bar> warmupBars, List<Bar> sessionBars) {
        java.util.LinkedHashMap<LocalDateTime, Bar> unique = new java.util.LinkedHashMap<>();
        if (warmupBars != null) {
            warmupBars.stream()
                    .filter(bar -> bar != null && bar.getTimestamp() != null)
                    .sorted(java.util.Comparator.comparing(Bar::getTimestamp))
                    .forEach(bar -> unique.put(bar.getTimestamp(), bar));
        }
        sessionBars.stream()
                .filter(bar -> bar != null && bar.getTimestamp() != null)
                .sorted(java.util.Comparator.comparing(Bar::getTimestamp))
                .forEach(bar -> unique.put(bar.getTimestamp(), bar));
        return new ArrayList<>(unique.values());
    }

    private void recordWarmupDiagnostic(
            BacktestResult result,
            String symbol,
            List<Bar> warmupBars,
            Bar firstSessionBar,
            Timeframe timeframe,
            MarketScannerService.ScanRequest request) {
        RadarStrategyConfig radar = request != null ? request.getRadarStrategyConfig() : null;
        boolean enabled = radar != null && radar.isBacktestCrossDayWarmupEnabled();
        int requestedBars = 0;
        if (enabled) {
            int minimumForSlowAverage = Math.max(0, radar.getSlowMovingAveragePeriod() * 3);
            requestedBars = Math.max(radar.getBacktestWarmupBarCount(), minimumForSlowAverage);
        }
        List<Bar> loadedWarmup = warmupBars == null
                ? List.of()
                : warmupBars.stream()
                        .filter(bar -> bar != null && bar.getTimestamp() != null)
                        .sorted(Comparator.comparing(Bar::getTimestamp))
                        .toList();
        LocalDateTime firstWarmupTime = loadedWarmup.isEmpty() ? null : loadedWarmup.get(0).getTimestamp();
        LocalDateTime lastWarmupTime = loadedWarmup.isEmpty() ? null : loadedWarmup.get(loadedWarmup.size() - 1).getTimestamp();
        result.addWarmupDiagnostic(new BacktestResult.WarmupDiagnostic(
                symbol,
                firstSessionBar != null && firstSessionBar.getTimestamp() != null
                        ? firstSessionBar.getTimestamp().toLocalDate()
                        : null,
                timeframe != null ? timeframe.name() : "",
                enabled,
                requestedBars,
                loadedWarmup.size(),
                firstWarmupTime,
                lastWarmupTime));
    }

    private Trade createBuyTrade(
            LocalDateTime timestamp,
            String symbol,
            int quantity,
            double referencePrice,
            Double stopLoss,
            Double takeProfit,
            String reason) {
        double slippageCost = referencePrice * quantity * slippageRate;
        return new Trade(timestamp, symbol, TradeType.BUY, quantity, referencePrice, commissionRate,
                0.0, slippageCost, stopLoss, takeProfit, reason);
    }

    private Trade createSellTrade(
            LocalDateTime timestamp,
            String symbol,
            int quantity,
            double referencePrice,
            Double stopLoss,
            Double takeProfit,
            String reason) {
        double slippageCost = referencePrice * quantity * slippageRate;
        return new Trade(timestamp, symbol, TradeType.SELL, quantity, referencePrice, commissionRate,
                dayTradeSellTaxRate, slippageCost, stopLoss, takeProfit, reason);
    }

    private boolean shouldHaltTrading(double realizedPnl, int stopLossCount, int consecutiveLosses) {
        if (!disableTradingAfterLossLimit) {
            return false;
        }
        if (dailyMaxLoss < 0.0 && realizedPnl <= dailyMaxLoss) {
            return true;
        }
        if (dailyMaxStopLossCount > 0 && stopLossCount >= dailyMaxStopLossCount) {
            return true;
        }
        return consecutiveLossLimit > 0 && consecutiveLosses >= consecutiveLossLimit;
    }

    private void recordSignalObservation(
            BacktestResult result,
            String symbol,
            List<Bar> bars,
            int signalIndex,
            MarketScanResult scanResult,
            DecisionResult decision,
            boolean forceBlocked,
            String forcedReason) {
        if (scanResult == null) {
            return;
        }
        Bar signalBar = bars.get(signalIndex);
        double basePrice = signalBar.getClose();
        double maxFavorable = 0.0;
        double maxAdverse = 0.0;
        double closeReturn = 0.0;
        if (basePrice > 0.0 && signalIndex + 1 < bars.size()) {
            double highest = basePrice;
            double lowest = basePrice;
            for (int i = signalIndex + 1; i < bars.size(); i++) {
                highest = Math.max(highest, bars.get(i).getHigh());
                lowest = Math.min(lowest, bars.get(i).getLow());
            }
            Bar last = bars.get(bars.size() - 1);
            maxFavorable = (highest - basePrice) / basePrice * 100.0;
            maxAdverse = (lowest - basePrice) / basePrice * 100.0;
            closeReturn = (last.getClose() - basePrice) / basePrice * 100.0;
        }
        boolean blocked = forceBlocked || decision == null || decision.getAction() != DecisionResult.Action.OPEN_LONG;
        String reason = blocked
                ? firstNonBlank(forcedReason, firstNonBlank(scanResult.getBlockReason(), decision != null ? decision.getReason() : null))
                : formatEntryReason(scanResult, decision);
        result.addSignalObservation(new BacktestResult.SignalObservation(
                symbol,
                signalBar.getTimestamp(),
                decision != null ? decision.getAction().name() : "NO_DECISION",
                blocked,
                reason,
                scanResult.getScore(),
                scanResult.getMarketDecision() != null ? scanResult.getMarketDecision().name() : "",
                scanResult.getMarketRegime() != null ? scanResult.getMarketRegime().name() : "",
                scanResult.getMarketRegime() != null ? scanResult.getMarketRegime().name() : "",
                scanResult.getIndustry(),
                scanResult.getWatchlistRankPercent(),
                scanResult.getVwap(),
                scanResult.getVwapSlopePercent(),
                scanResult.getVolumeSustain(),
                scanResult.getRelativeToBenchmarkPercent(),
                scanResult.getRelativeToIndustryPercent(),
                scanResult.getScoreComponents(),
                scanResult.getScoreComponentSummary(),
                scanResult.getLongBonusSummary(),
                maxFavorable,
                maxAdverse,
                closeReturn));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
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
        if (scanResult.getScoreComponentSummary() != null && !scanResult.getScoreComponentSummary().isBlank()) {
            parts.add("加分明細 " + scanResult.getScoreComponentSummary());
        }
        return String.join(" | ", parts);
    }

    private String resolveNetRewardBlockReason(double entryPrice, int quantity, Double takeProfit) {
        if (entryPrice <= 0.0 || quantity <= 0 || takeProfit == null || takeProfit <= entryPrice) {
            return "成本後停利空間不足：缺少有效停利價";
        }
        Trade buy = createBuyTrade(LocalDateTime.MIN, "COST_CHECK", quantity, entryPrice, null, takeProfit, "");
        Trade sellAtTarget = createSellTrade(LocalDateTime.MIN, "COST_CHECK", quantity, takeProfit, null, takeProfit, "");
        double netReward = sellAtTarget.getNetProceeds() - buy.getTotalCost();
        if (netReward <= 0.0) {
            return String.format(
                    "成本後停利空間不足：進場 %.2f、停利 %.2f，目標淨利 %.2f",
                    entryPrice,
                    takeProfit,
                    netReward);
        }
        return null;
    }

    private boolean isEarlyEntryBlock(LocalTime time) {
        return !time.isBefore(earlyEntryBlockStart) && time.isBefore(earlyEntryBlockEnd);
    }

    private AutoMonitorExecutionGate.GateConfig replayGateConfig() {
        return new AutoMonitorExecutionGate.GateConfig(
                true,
                earlyEntryBlockStart,
                earlyEntryBlockEnd,
                latestAutoEntryTime,
                dailyMaxAutoTrades,
                entryPacingMinutes,
                oneEntryPerFiveMinuteBar,
                FORCE_CLOSE_TIME);
    }

    private boolean isEntryPacingBlocked(LocalDateTime decisionTime, LocalDateTime lastEntryTime) {
        return entryPacingMinutes > 0
                && decisionTime != null
                && lastEntryTime != null
                && decisionTime.isBefore(lastEntryTime.plusMinutes(entryPacingMinutes));
    }

    private boolean isEntryBucketBlocked(LocalDateTime decisionTime, java.util.Set<String> entryBuckets) {
        return oneEntryPerFiveMinuteBar
                && decisionTime != null
                && entryBuckets != null
                && entryBuckets.contains(fiveMinuteBucket(decisionTime));
    }

    private String fiveMinuteBucket(LocalDateTime time) {
        return AutoMonitorExecutionGate.fiveMinuteBucket(time);
    }

    private ExitDecision resolveExit(OpenPosition open, List<Bar> bars, int index) {
        Bar bar = bars.get(index);
        if (open.stopLoss() != null && bar.getLow() <= open.stopLoss()) {
            return new ExitDecision(open.stopLoss(), "STOP_LOSS", ExitType.STOP_LOSS);
        }
        if (open.takeProfit() != null && bar.getHigh() >= open.takeProfit()) {
            return new ExitDecision(open.takeProfit(), "TAKE_PROFIT", ExitType.TAKE_PROFIT);
        }
        if (isVwapBreak(bars, index, bar)) {
            return new ExitDecision(bar.getClose(), "VWAP_BREAK", ExitType.VWAP_BREAK);
        }
        if (isVolumeFail(bars, index, bar)) {
            return new ExitDecision(bar.getClose(), "VOLUME_FAIL", ExitType.VOLUME_FAIL);
        }
        if (!bar.getTimestamp().toLocalTime().isBefore(FORCE_CLOSE_TIME)) {
            return new ExitDecision(bar.getClose(), "FORCE_CLOSE", ExitType.FORCE_CLOSE);
        }
        return null;
    }

    private OpenPosition updateTrailingStop(OpenPosition open, Bar bar) {
        if (open == null || bar == null || open.stopLoss() == null || open.stopLoss() <= 0.0) {
            return open;
        }
        double risk = open.entryPrice() - open.stopLoss();
        if (risk <= 0.0 || bar.getHigh() < open.entryPrice() + risk) {
            return open;
        }
        double breakEvenStop = open.entryPrice() + risk * 0.05;
        if (breakEvenStop <= open.stopLoss()) {
            return open;
        }
        return new OpenPosition(
                open.quantity(),
                open.entryPrice(),
                breakEvenStop,
                open.takeProfit(),
                open.entryTrade());
    }

    private boolean isVwapBreak(List<Bar> bars, int index, Bar current) {
        if (index < 5 || current.getVolume() <= 0) {
            return false;
        }
        double priceVolume = 0.0;
        double totalVolume = 0.0;
        for (int i = 0; i <= index; i++) {
            Bar bar = bars.get(i);
            double typical = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
            priceVolume += typical * bar.getVolume();
            totalVolume += bar.getVolume();
        }
        if (totalVolume <= 0.0) {
            return false;
        }
        double vwap = priceVolume / totalVolume;
        return current.getClose() < vwap;
    }

    private boolean isVolumeFail(List<Bar> bars, int index, Bar current) {
        if (index < 8 || current.getClose() >= current.getOpen()) {
            return false;
        }
        int start = Math.max(0, index - 8);
        double avg = bars.subList(start, index).stream().mapToLong(Bar::getVolume).average().orElse(0.0);
        return avg > 0.0 && current.getVolume() >= avg * 1.5;
    }

    private record OpenPosition(int quantity, double entryPrice, Double stopLoss, Double takeProfit, Trade entryTrade) {
    }

    private record ExitDecision(double price, String reason, ExitType type) {
    }

    private enum ExitType {
        STOP_LOSS,
        TAKE_PROFIT,
        VWAP_BREAK,
        VOLUME_FAIL,
        FORCE_CLOSE
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
