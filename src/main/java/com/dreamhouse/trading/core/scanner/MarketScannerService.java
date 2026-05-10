package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.MarketDataNormalizer;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionEngine;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.strategies.AtrVolatilityFilterStrategy;
import com.dreamhouse.trading.core.decision.strategies.DayTradeCloseGuardStrategy;
import com.dreamhouse.trading.core.decision.strategies.DayTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.DecisionBaseStrategy;
import com.dreamhouse.trading.core.decision.strategies.LowVolumeConsolidationBreakoutStrategy;
import com.dreamhouse.trading.core.decision.strategies.MovingAverageAlignmentStrategy;
import com.dreamhouse.trading.core.decision.strategies.OpeningRangeBreakoutStrategy;
import com.dreamhouse.trading.core.decision.strategies.PlatformBreakoutStrategy;
import com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy;
import com.dreamhouse.trading.core.decision.strategies.PositionTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.SwingTradingStrategy;
import com.dreamhouse.trading.core.decision.strategies.VolumeBreakoutStrategy;
import com.dreamhouse.trading.core.decision.strategies.VwapPullbackStrategy;
import com.dreamhouse.trading.core.decision.strategies.WeeklyStrengthStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
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
import java.util.List;

/**
 * 市場候選股掃描核心服務。
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
            results.add(scan(symbol, request));
        }
        Collections.sort(results);
        return results;
    }

    public MarketScanResult scan(String symbol, ScanRequest request) {
        ScanRequest effectiveRequest = request != null ? request : ScanRequest.createDefault();
        try {
            List<Bar> bars = dataFeed.fetchHistoricalBars(
                symbol,
                effectiveRequest.getTimeframe(),
                effectiveRequest.getBarCount());
            bars = MarketDataNormalizer.normalizeBars(bars, effectiveRequest.getBarCount());
            bars = removeIncompleteLastBar(bars, effectiveRequest.getTimeframe());

            if (bars.size() < 2) {
                return noTrade(symbol, effectiveRequest.getTradeMode(), "K線數量不足，無法掃描");
            }

            Bar lastBar = bars.get(bars.size() - 1);
            List<Bar> historyBars = bars.subList(0, bars.size() - 1);
            BarSeries historySeries = toBarSeries(symbol, historyBars, effectiveRequest.getTimeframe());
            BarSeries signalSeries = toBarSeries(symbol, bars, effectiveRequest.getTimeframe());

            if (historySeries.getBarCount() == 0 || signalSeries.getBarCount() == 0) {
                return noTrade(symbol, effectiveRequest.getTradeMode(), "K線轉換後無有效資料");
            }

            List<IStrategySignal> signals = createStrategySignals(
                effectiveRequest.getTradeMode(),
                signalSeries,
                effectiveRequest.getTimeframe());
            String rawSignalSummary = summarizeSignals(signals);

            DecisionEngine decisionEngine = new DecisionEngine(
                effectiveRequest.getDecisionConfig(),
                new Portfolio(effectiveRequest.getInitialCapital()));
            decisionEngine.setSymbol(symbol);
            decisionEngine.setBarSeries(historySeries, effectiveRequest.getTimeframe());
            decisionEngine.setStrategySignals(signals);

            DecisionResult decision = decisionEngine.onBar(toTa4jBar(lastBar, effectiveRequest.getTimeframe()));
            double score = calculateScore(decision, effectiveRequest.getTradeMode());
            Double riskReward = calculateRiskReward(lastBar.getClose(), decision);

            return MarketScanResult.builder(symbol)
                .tradeMode(effectiveRequest.getTradeMode())
                .decisionResult(decision)
                .score(score)
                .riskRewardRatio(riskReward)
                .rawSignalSummary(rawSignalSummary)
                .blockReason(resolveBlockReason(decision))
                .build();
        } catch (UnsupportedOperationException e) {
            logger.warn("{} 不支援同步掃描: {}", symbol, e.getMessage());
            return noTrade(symbol, effectiveRequest.getTradeMode(), e.getMessage());
        } catch (Exception e) {
            logger.error("掃描 {} 失敗: {}", symbol, e.getMessage(), e);
            return noTrade(symbol, effectiveRequest.getTradeMode(), "掃描失敗：" + e.getMessage());
        }
    }

    private MarketScanResult noTrade(String symbol, TradeMode tradeMode, String reason) {
        DecisionResult decision = new DecisionResult.Builder()
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
            .rawSignalSummary("無可用策略訊號")
            .blockReason(reason)
            .build();
    }

    private String summarizeSignals(List<IStrategySignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "無策略訊號";
        }

        return signals.stream()
            .filter(signal -> signal != null && signal.getSignal() != null)
            .filter(signal -> signal.getSignal() != SignalType.HOLD && signal.getSignal() != SignalType.NO_TRADE)
            .sorted((a, b) -> Double.compare(weightedScore(b), weightedScore(a)))
            .limit(3)
            .map(signal -> String.format("%s:%s %.0f%% - %s",
                signal.getStrategyName(),
                signal.getSignal().name(),
                signal.getConfidence() * 100.0,
                signal.getReason()))
            .reduce((left, right) -> left + " | " + right)
            .orElse("無進出場策略訊號");
    }

    private double weightedScore(IStrategySignal signal) {
        return signal.getConfidence() * signal.getWeight();
    }

    private String resolveBlockReason(DecisionResult decision) {
        if (decision == null) {
            return "決策結果為空";
        }
        if (decision.shouldTrade()) {
            return "";
        }
        return decision.getReason();
    }

    private List<IStrategySignal> createStrategySignals(TradeMode mode, BarSeries series, Timeframe timeframe) {
        List<DecisionBaseStrategy> strategies = new ArrayList<>();

        SignalRSIStrategy rsi = new SignalRSIStrategy();

        if (mode == TradeMode.DAY_TRADE) {
            rsi.setRsiPeriod(5);
            rsi.setOversoldThreshold(40.0);
            rsi.setOverboughtThreshold(60.0);

            OpeningRangeBreakoutStrategy openingRange = new OpeningRangeBreakoutStrategy();
            openingRange.setWeight(0.9);

            VolumeBreakoutStrategy volumeBreakout = new VolumeBreakoutStrategy();
            volumeBreakout.setWeight(0.85);

            VwapPullbackStrategy vwapPullback = new VwapPullbackStrategy();
            vwapPullback.setWeight(0.8);

            DayTradeCloseGuardStrategy closeGuard = new DayTradeCloseGuardStrategy();
            AtrVolatilityFilterStrategy atrFilter = new AtrVolatilityFilterStrategy();
            atrFilter.setMinAtrPercent(0.001);
            atrFilter.setMaxAtrPercent(0.08);
            atrFilter.setWeight(0.4);

            strategies.add(openingRange);
            strategies.add(volumeBreakout);
            strategies.add(vwapPullback);
            strategies.add(closeGuard);
            strategies.add(atrFilter);
        } else if (mode == TradeMode.SWING_TRADE) {
            rsi.setRsiPeriod(21);
            rsi.setOversoldThreshold(25.0);
            rsi.setOverboughtThreshold(75.0);

            MovingAverageAlignmentStrategy maAlignment = new MovingAverageAlignmentStrategy();
            maAlignment.setPeriods(20, 50, 100);
            maAlignment.setWeight(0.95);

            PlatformBreakoutStrategy platformBreakout = new PlatformBreakoutStrategy();
            platformBreakout.setPlatformLookbackBars(30);
            platformBreakout.setMaxPlatformRangePercent(0.12);
            platformBreakout.setWeight(0.85);

            LowVolumeConsolidationBreakoutStrategy lowVolumeBreakout = new LowVolumeConsolidationBreakoutStrategy();
            lowVolumeBreakout.setContractionBars(15);
            lowVolumeBreakout.setBaselineBars(40);
            lowVolumeBreakout.setWeight(0.8);

            WeeklyStrengthStrategy weeklyStrength = new WeeklyStrengthStrategy();
            weeklyStrength.setTrendLookbackBars(20);
            weeklyStrength.setBreakoutLookbackBars(12);
            weeklyStrength.setWeight(0.9);

            AtrVolatilityFilterStrategy atrFilter = new AtrVolatilityFilterStrategy();
            atrFilter.setMinAtrPercent(0.003);
            atrFilter.setMaxAtrPercent(0.15);
            atrFilter.setWeight(0.4);

            strategies.add(maAlignment);
            strategies.add(platformBreakout);
            strategies.add(lowVolumeBreakout);
            strategies.add(weeklyStrength);
            strategies.add(atrFilter);
        } else if (mode == TradeMode.SHORT_SWING) {
            rsi.setRsiPeriod(14);
            rsi.setOversoldThreshold(30.0);
            rsi.setOverboughtThreshold(70.0);

            MovingAverageAlignmentStrategy maAlignment = new MovingAverageAlignmentStrategy();
            maAlignment.setPeriods(5, 10, 20);
            maAlignment.setWeight(0.9);

            PlatformBreakoutStrategy platformBreakout = new PlatformBreakoutStrategy();
            platformBreakout.setPlatformLookbackBars(16);
            platformBreakout.setMaxPlatformRangePercent(0.07);
            platformBreakout.setWeight(0.85);

            LowVolumeConsolidationBreakoutStrategy lowVolumeBreakout = new LowVolumeConsolidationBreakoutStrategy();
            lowVolumeBreakout.setContractionBars(8);
            lowVolumeBreakout.setBaselineBars(20);
            lowVolumeBreakout.setWeight(0.8);

            AtrVolatilityFilterStrategy atrFilter = new AtrVolatilityFilterStrategy();
            atrFilter.setMinAtrPercent(0.005);
            atrFilter.setMaxAtrPercent(0.12);
            atrFilter.setWeight(0.4);

            strategies.add(maAlignment);
            strategies.add(platformBreakout);
            strategies.add(lowVolumeBreakout);
            strategies.add(atrFilter);
        }

        rsi.setWeight(1.0);
        strategies.add(0, rsi);

        List<IStrategySignal> signals = new ArrayList<>();
        int lastIndex = series.getEndIndex();
        for (DecisionBaseStrategy strategy : strategies) {
            strategy.setTimeframe(timeframe);
            strategy.initialize(series);
            strategy.onBar(lastIndex, series.getBar(lastIndex));
            signals.add(strategy);
        }
        return signals;
    }

    private double calculateScore(DecisionResult decision, TradeMode mode) {
        if (decision == null) {
            return 0.0;
        }
        double modeWeight = mode == TradeMode.DAY_TRADE ? 1.0 : mode == TradeMode.SWING_TRADE ? 0.9 : 0.8;
        double actionWeight = decision.shouldTrade() ? 1.0 : decision.getAction() == DecisionResult.Action.HOLD ? 0.4 : 0.2;
        return Math.max(0.0, Math.min(1.0, decision.getConfidence() * modeWeight * actionWeight));
    }

    private Double calculateRiskReward(double currentPrice, DecisionResult decision) {
        if (decision == null || !decision.isEntry()
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

    private List<Bar> removeIncompleteLastBar(List<Bar> bars, Timeframe timeframe) {
        if (bars == null || bars.isEmpty()) {
            return new ArrayList<>();
        }

        Bar lastBar = bars.get(bars.size() - 1);
        LocalDateTime now = LocalDateTime.now();
        LocalDate barDate = lastBar.getTimestamp().toLocalDate();
        LocalDateTime barEnd = lastBar.getTimestamp().plus(timeframe.getMinutes(), ChronoUnit.MINUTES);
        if (barDate.equals(now.toLocalDate()) && now.isBefore(barEnd)) {
            return new ArrayList<>(bars.subList(0, bars.size() - 1));
        }
        return bars;
    }

    private BarSeries toBarSeries(String symbol, List<Bar> bars, Timeframe timeframe) {
        BaseBarSeries series = new BaseBarSeries(symbol);
        ZonedDateTime lastTime = null;
        for (Bar bar : bars) {
            org.ta4j.core.Bar ta4jBar = toTa4jBar(bar, timeframe);
            if (lastTime == null || ta4jBar.getEndTime().isAfter(lastTime)) {
                series.addBar(ta4jBar);
                lastTime = ta4jBar.getEndTime();
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

    public static class ScanRequest {
        private Timeframe timeframe = Timeframe.M5;
        private int barCount = 100;
        private TradeMode tradeMode = TradeMode.DAY_TRADE;
        private DecisionConfig decisionConfig = DecisionConfig.createDefault();
        private double initialCapital = 100000.0;

        public static ScanRequest createDefault() {
            return new ScanRequest();
        }

        public Timeframe getTimeframe() { return timeframe; }
        public int getBarCount() { return barCount; }
        public TradeMode getTradeMode() { return tradeMode; }
        public DecisionConfig getDecisionConfig() { return decisionConfig; }
        public double getInitialCapital() { return initialCapital; }

        public ScanRequest timeframe(Timeframe timeframe) {
            this.timeframe = timeframe != null ? timeframe : Timeframe.M5;
            return this;
        }

        public ScanRequest barCount(int barCount) {
            this.barCount = Math.max(2, barCount);
            return this;
        }

        public ScanRequest tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode != null ? tradeMode : TradeMode.NO_TRADE;
            this.decisionConfig = switch (this.tradeMode) {
                case DAY_TRADE -> DayTradingStrategy.createDayTradingConfig();
                case SHORT_SWING -> SwingTradingStrategy.createSwingTradingConfig();
                case SWING_TRADE -> PositionTradingStrategy.createPositionTradingConfig();
                case NO_TRADE -> DecisionConfig.createDefault();
            };
            return this;
        }

        public ScanRequest decisionConfig(DecisionConfig decisionConfig) {
            this.decisionConfig = decisionConfig != null ? decisionConfig : DecisionConfig.createDefault();
            return this;
        }

        public ScanRequest initialCapital(double initialCapital) {
            this.initialCapital = initialCapital > 0 ? initialCapital : 100000.0;
            return this;
        }
    }
}
