package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionEngine;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.strategies.DayTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.DecisionBaseStrategy;
import com.dreamhouse.trading.core.decision.strategies.MovingAverageTrendSignalStrategy;
import com.dreamhouse.trading.core.decision.strategies.PositionTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy;
import com.dreamhouse.trading.core.decision.strategies.SwingTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.VolumeBreakoutSignalStrategy;
import com.dreamhouse.trading.core.finmind.FinMindAccessDeniedException;
import com.dreamhouse.trading.core.finmind.FinMindQuotaExceededException;
import com.dreamhouse.trading.core.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scanner service used by the opportunity radar.
 */
public class MarketScannerService {

    private static final Logger logger = LoggerFactory.getLogger(MarketScannerService.class);

    private final MarketDataFeed dataFeed;

    public MarketScannerService(MarketDataFeed dataFeed) {
        this.dataFeed = dataFeed;
    }

    public List<MarketScanResult> scan(Collection<String> symbols, ScanRequest request) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyList();
        }

        List<MarketScanResult> results = new ArrayList<>();
        for (String symbol : symbols) {
            MarketScanResult result = scan(symbol, request);
            if (result != null) {
                results.add(result);
            }
        }
        results.sort(Comparator.naturalOrder());
        return results;
    }

    public MarketScanResult scan(String symbol, ScanRequest request) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        ScanRequest effectiveRequest = request != null ? request : ScanRequest.createDefault();
        StrategyProfile profile = resolveProfile(effectiveRequest.getTradeMode(), effectiveRequest.getDecisionConfig());
        Timeframe timeframe = effectiveRequest.getTimeframe() != null
                ? effectiveRequest.getTimeframe()
                : profile.timeframe();

        try {
            List<Bar> bars = normalizeBars(
                    dataFeed.fetchHistoricalBars(symbol, timeframe, effectiveRequest.getBarCount()),
                    effectiveRequest.getBarCount());
            bars = removeIncompleteLastBar(bars, timeframe);

            if (bars.size() < 2) {
                return noTrade(symbol, effectiveRequest.getTradeMode(), "Not enough bars to scan");
            }

            Bar lastBar = bars.get(bars.size() - 1);
            List<Bar> historyBars = bars.subList(0, bars.size() - 1);
            BarSeries historySeries = toBarSeries(symbol, historyBars, timeframe);
            BarSeries signalSeries = toBarSeries(symbol, bars, timeframe);

            if (historySeries.getBarCount() == 0 || signalSeries.getBarCount() == 0) {
                return noTrade(symbol, effectiveRequest.getTradeMode(), "No usable bars after normalization");
            }

            List<IStrategySignal> signals = createStrategySignals(
                    effectiveRequest.getTradeMode(),
                    signalSeries,
                    effectiveRequest.getRadarStrategyConfig());
            DecisionEngine decisionEngine = new DecisionEngine(profile.decisionConfig(), new Portfolio(effectiveRequest.getInitialCapital()));
            decisionEngine.setSymbol(symbol);
            decisionEngine.setBarSeries(historySeries, timeframe);
            decisionEngine.setStrategySignals(signals);

            DecisionResult decision = decisionEngine.onBar(toTa4jBar(lastBar, timeframe));
            Double riskReward = resolveRiskReward(lastBar.getClose(), decision);
            String rawSignalSummary = summarizeSignals(signals);
            String entryBlockReason = resolveEntryQualityBlockReason(
                    decision,
                    signals,
                    effectiveRequest.getRadarStrategyConfig());
            if (entryBlockReason != null) {
                DecisionResult blockedDecision = new DecisionResult.Builder()
                        .symbol(symbol)
                        .tradeMode(effectiveRequest.getTradeMode())
                        .action(DecisionResult.Action.NO_ACTION)
                        .source(DecisionResult.Source.TECHNICAL)
                        .reason(entryBlockReason)
                        .confidence(0.0)
                        .build();
                return MarketScanResult.builder(symbol)
                        .tradeMode(effectiveRequest.getTradeMode())
                        .decisionResult(blockedDecision)
                        .score(0.0)
                        .riskRewardRatio(riskReward)
                        .rawSignalSummary(rawSignalSummary)
                        .blockReason(entryBlockReason)
                        .build();
            }

            return MarketScanResult.builder(symbol)
                    .tradeMode(effectiveRequest.getTradeMode())
                    .decisionResult(decision)
                    .score(calculateScore(decision, effectiveRequest.getTradeMode()))
                    .riskRewardRatio(riskReward)
                    .rawSignalSummary(rawSignalSummary)
                    .blockReason(resolveBlockReason(decision))
                    .build();
        } catch (UnsupportedOperationException e) {
            logger.warn("{} does not support synchronous bar scans: {}", symbol, e.getMessage());
            return noTrade(symbol, effectiveRequest.getTradeMode(), e.getMessage());
        } catch (FinMindAccessDeniedException | FinMindQuotaExceededException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Scan failed for {}: {}", symbol, e.getMessage(), e);
            return noTrade(symbol, effectiveRequest.getTradeMode(), "Scan failed: " + e.getMessage());
        }
    }

    private StrategyProfile resolveProfile(TradeMode mode, DecisionConfig overrideConfig) {
        TradeMode safeMode = mode != null ? mode : TradeMode.DAY_TRADE;
        DecisionConfig config;
        Timeframe timeframe;
        switch (safeMode) {
            case SWING_TRADE -> {
                config = overrideConfig != null ? overrideConfig : PositionTradingStrategy.createPositionTradingConfig();
                timeframe = config.getMainLoopTimeframe();
            }
            case SHORT_SWING -> {
                config = overrideConfig != null ? overrideConfig : SwingTradingStrategy.createSwingTradingConfig();
                timeframe = config.getMainLoopTimeframe();
            }
            case DAY_TRADE, NO_TRADE -> {
                config = overrideConfig != null ? overrideConfig : DayTradingStrategy.createDayTradingConfig();
                timeframe = config.getMainLoopTimeframe();
            }
            default -> {
                config = overrideConfig != null ? overrideConfig : DecisionConfig.createDefault();
                timeframe = config.getMainLoopTimeframe();
            }
        }
        return new StrategyProfile(config, timeframe);
    }

    private List<IStrategySignal> createStrategySignals(TradeMode mode, BarSeries series, RadarStrategyConfig config) {
        RadarStrategyConfig modeConfig = (config != null ? config : RadarStrategyConfig.createDefault())
                .copyForMode(mode);
        List<IStrategySignal> signals = new ArrayList<>();

        if (modeConfig.isRsiEnabled()) {
            SignalRSIStrategy rsi = new SignalRSIStrategy();
            rsi.setRsiPeriod(modeConfig.getRsiPeriod());
            rsi.setOversoldThreshold(modeConfig.getRsiOversold());
            rsi.setOverboughtThreshold(modeConfig.getRsiOverbought());
            rsi.setWeight(modeConfig.getRsiWeight());
            initializeAndRun(rsi, series);
            signals.add(rsi);
        }

        if (modeConfig.isMovingAverageEnabled()) {
            MovingAverageTrendSignalStrategy movingAverage = new MovingAverageTrendSignalStrategy();
            movingAverage.setAverageType(modeConfig.getMovingAverageType());
            movingAverage.setFastPeriod(modeConfig.getFastMovingAveragePeriod());
            movingAverage.setSlowPeriod(modeConfig.getSlowMovingAveragePeriod());
            movingAverage.setWeight(modeConfig.getMovingAverageWeight());
            initializeAndRun(movingAverage, series);
            signals.add(movingAverage);
        }

        if (modeConfig.isVolumeBreakoutEnabled()) {
            VolumeBreakoutSignalStrategy breakout = new VolumeBreakoutSignalStrategy();
            breakout.setLookbackBars(modeConfig.getBreakoutLookbackBars());
            breakout.setVolumeMultiplier(modeConfig.getVolumeMultiplier());
            breakout.setWeight(modeConfig.getVolumeBreakoutWeight());
            initializeAndRun(breakout, series);
            signals.add(breakout);
        }

        return signals;
    }

    private void initializeAndRun(DecisionBaseStrategy strategy, BarSeries series) {
        strategy.initialize(series);
        strategy.onBar(series.getEndIndex(), series.getLastBar());
    }

    private List<Bar> normalizeBars(List<Bar> bars, int barCount) {
        if (bars == null || bars.isEmpty()) {
            return new ArrayList<>();
        }

        List<Bar> sortedBars = bars.stream()
                .filter(bar -> bar != null && bar.getTimestamp() != null)
                .sorted(Comparator.comparing(Bar::getTimestamp))
                .toList();

        Map<LocalDateTime, Bar> uniqueBars = new LinkedHashMap<>();
        for (Bar bar : sortedBars) {
            uniqueBars.put(bar.getTimestamp(), bar);
        }

        List<Bar> normalized = new ArrayList<>(uniqueBars.values());
        if (barCount > 0 && normalized.size() > barCount) {
            return new ArrayList<>(normalized.subList(normalized.size() - barCount, normalized.size()));
        }
        return normalized;
    }

    private List<Bar> removeIncompleteLastBar(List<Bar> bars, Timeframe timeframe) {
        if (bars == null || bars.isEmpty()) {
            return new ArrayList<>();
        }

        Bar lastBar = bars.get(bars.size() - 1);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime barEnd = lastBar.getTimestamp().plus(timeframe.getMinutes(), ChronoUnit.MINUTES);
        if (lastBar.getTimestamp().toLocalDate().equals(LocalDate.now()) && now.isBefore(barEnd)) {
            return new ArrayList<>(bars.subList(0, bars.size() - 1));
        }
        return bars;
    }

    private BarSeries toBarSeries(String symbol, List<Bar> bars, Timeframe timeframe) {
        BaseBarSeries series = new BaseBarSeries(symbol);
        ZonedDateTime lastEndTime = null;
        for (Bar bar : bars) {
            org.ta4j.core.Bar ta4jBar = toTa4jBar(bar, timeframe);
            if (lastEndTime == null || ta4jBar.getEndTime().isAfter(lastEndTime)) {
                series.addBar(ta4jBar);
                lastEndTime = ta4jBar.getEndTime();
            }
        }
        return series;
    }

    private org.ta4j.core.Bar toTa4jBar(Bar bar, Timeframe timeframe) {
        Duration duration = Duration.ofMinutes(timeframe.getMinutes());
        ZonedDateTime endTime = bar.getTimestamp()
                .plus(duration)
                .atZone(ZoneId.systemDefault());
        return new BaseBar(
                duration,
                endTime,
                BigDecimal.valueOf(bar.getOpen()),
                BigDecimal.valueOf(bar.getHigh()),
                BigDecimal.valueOf(bar.getLow()),
                BigDecimal.valueOf(bar.getClose()),
                BigDecimal.valueOf(bar.getVolume()));
    }

    private MarketScanResult noTrade(String symbol, TradeMode tradeMode, String reason) {
        DecisionResult decision = new DecisionResult.Builder()
                .symbol(symbol)
                .tradeMode(tradeMode)
                .action(DecisionResult.Action.NO_ACTION)
                .source(DecisionResult.Source.TECHNICAL)
                .reason(reason)
                .confidence(0.0)
                .build();
        return MarketScanResult.builder(symbol)
                .tradeMode(tradeMode)
                .decisionResult(decision)
                .reason(reason)
                .score(0.0)
                .rawSignalSummary("No active strategy signals")
                .blockReason(reason)
                .build();
    }

    private String resolveEntryQualityBlockReason(
            DecisionResult decision,
            List<IStrategySignal> signals,
            RadarStrategyConfig config) {
        RadarStrategyConfig effectiveConfig = config != null ? config : RadarStrategyConfig.createDefault();
        if (!effectiveConfig.isRequireRsiEntryConfirmation()
                || decision == null
                || decision.getAction() != DecisionResult.Action.OPEN_LONG
                || signals == null
                || signals.isEmpty()) {
            return null;
        }

        boolean rsiLong = false;
        boolean hasMovingAverageSignal = false;
        boolean movingAverageDown = false;
        boolean volumeLong = false;

        for (IStrategySignal signal : signals) {
            if (signal == null || signal.getStrategyName() == null || signal.getSignal() == null) {
                continue;
            }
            String strategyName = signal.getStrategyName();
            if ("SignalRSI".equals(strategyName) && signal.getSignal() == SignalType.LONG) {
                rsiLong = true;
            } else if ("MovingAverageTrend".equals(strategyName)) {
                hasMovingAverageSignal = true;
                movingAverageDown = signal.getSignal() == SignalType.SHORT;
            } else if ("VolumeBreakout".equals(strategyName) && signal.getSignal() == SignalType.LONG) {
                volumeLong = true;
            }
        }

        if (!rsiLong) {
            return null;
        }
        boolean emaConfirmed = hasMovingAverageSignal && !movingAverageDown;
        if (emaConfirmed || volumeLong) {
            return null;
        }
        return "RSI 超賣訊號缺少 EMA 趨勢或放量反轉確認，略過接刀進場";
    }

    private String summarizeSignals(List<IStrategySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "No strategy signals";
        }

        return signals.stream()
                .filter(signal -> signal != null && signal.getSignal() != null)
                .filter(signal -> signal.getSignal() != SignalType.HOLD && signal.getSignal() != SignalType.NO_TRADE)
                .sorted((left, right) -> Double.compare(weightedScore(right), weightedScore(left)))
                .limit(3)
                .map(signal -> String.format(
                        "%s:%s %.0f%% - %s",
                        signal.getStrategyName(),
                        signal.getSignal().name(),
                        signal.getConfidence() * 100.0,
                        signal.getReason()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("No entry or exit signals");
    }

    private double weightedScore(IStrategySignal signal) {
        return signal.getConfidence() * signal.getWeight();
    }

    private String resolveBlockReason(DecisionResult decision) {
        if (decision == null) {
            return "No decision returned";
        }
        return decision.shouldTrade() ? "" : decision.getReason();
    }

    private double calculateScore(DecisionResult decision, TradeMode mode) {
        if (decision == null) {
            return 0.0;
        }

        double modeWeight = switch (mode) {
            case DAY_TRADE -> 1.0;
            case SHORT_SWING -> 0.9;
            case SWING_TRADE -> 0.85;
            default -> 0.8;
        };
        double actionWeight = decision.shouldTrade()
                ? 1.0
                : decision.getAction() == DecisionResult.Action.HOLD ? 0.45 : 0.2;
        return Math.max(0.0, Math.min(1.0, decision.getConfidence() * modeWeight * actionWeight));
    }

    private Double resolveRiskReward(double currentPrice, DecisionResult decision) {
        if (decision == null) {
            return null;
        }
        if (decision.getRiskRewardRatio() != null) {
            return decision.getRiskRewardRatio();
        }
        if (!decision.isEntry()
                || decision.getSuggestedStopLoss() == null
                || decision.getSuggestedTakeProfit() == null) {
            return null;
        }

        double risk = Math.abs(currentPrice - decision.getSuggestedStopLoss());
        double reward = Math.abs(decision.getSuggestedTakeProfit() - currentPrice);
        if (risk <= 0.0) {
            return null;
        }
        return reward / risk;
    }

    private record StrategyProfile(DecisionConfig decisionConfig, Timeframe timeframe) {
    }

    public static class ScanRequest {
        private Timeframe timeframe;
        private int barCount = 160;
        private TradeMode tradeMode = TradeMode.DAY_TRADE;
        private DecisionConfig decisionConfig;
        private RadarStrategyConfig radarStrategyConfig = RadarStrategyConfig.createDefault();
        private double initialCapital = 100000.0;

        public static ScanRequest createDefault() {
            return new ScanRequest();
        }

        public Timeframe getTimeframe() {
            return timeframe;
        }

        public ScanRequest timeframe(Timeframe timeframe) {
            this.timeframe = timeframe;
            return this;
        }

        public int getBarCount() {
            return barCount;
        }

        public ScanRequest barCount(int barCount) {
            this.barCount = Math.max(20, barCount);
            return this;
        }

        public TradeMode getTradeMode() {
            return tradeMode;
        }

        public ScanRequest tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode != null ? tradeMode : TradeMode.DAY_TRADE;
            return this;
        }

        public DecisionConfig getDecisionConfig() {
            return decisionConfig;
        }

        public ScanRequest decisionConfig(DecisionConfig decisionConfig) {
            this.decisionConfig = decisionConfig;
            return this;
        }

        public RadarStrategyConfig getRadarStrategyConfig() {
            return radarStrategyConfig;
        }

        public ScanRequest radarStrategyConfig(RadarStrategyConfig radarStrategyConfig) {
            this.radarStrategyConfig = radarStrategyConfig != null ? radarStrategyConfig : RadarStrategyConfig.createDefault();
            return this;
        }

        public double getInitialCapital() {
            return initialCapital;
        }

        public ScanRequest initialCapital(double initialCapital) {
            this.initialCapital = initialCapital;
            return this;
        }
    }
}
