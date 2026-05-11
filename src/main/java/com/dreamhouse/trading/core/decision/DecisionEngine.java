package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.TimeframeAggregator;
import com.dreamhouse.trading.core.backtest.AdvancedStopLossManager;
import com.dreamhouse.trading.core.backtest.AdvancedStopLossManager.StopTrigger;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.decision.classifier.ClassificationConfig;
import com.dreamhouse.trading.core.decision.classifier.ClassificationResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.decision.classifier.TradeModeClassifier;
import com.dreamhouse.trading.core.decision.regime.AllowedSide;
import com.dreamhouse.trading.core.decision.regime.MarketRegimeDetector;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.risk.RiskManager;
import com.dreamhouse.trading.core.decision.risk.RiskViolation;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalyzer;
import com.dreamhouse.trading.core.decision.voting.VotingEngine;
import com.dreamhouse.trading.core.decision.voting.VotingResult;
import com.dreamhouse.trading.core.execution.OrderSide;
import com.dreamhouse.trading.core.execution.OrderType;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central decision engine for normalized signal, risk and execution guidance.
 */
public class DecisionEngine {

    private final DecisionConfig config;
    private final Portfolio portfolio;
    private final MarketRegimeDetector regimeDetector;
    private final TrendAnalyzer trendAnalyzer;
    private final VotingEngine votingEngine;
    private final RiskManager riskManager;
    private final AdvancedStopLossManager stopManager;
    private final TradeModeClassifier tradeModeClassifier;
    private final Map<Timeframe, BarSeries> timeframeSeries;

    private BarSeries primarySeries;
    private boolean hasPosition = false;
    private String currentSymbol = "";
    private double entryPrice = 0.0;
    private int positionQuantity = 0;
    private RegimeAnalysis lastRegimeAnalysis;
    private TrendAnalysis lastTrendAnalysis;
    private VotingResult lastVotingResult;
    private ClassificationResult lastClassificationResult;
    private List<IStrategySignal> externalSignals = new ArrayList<>();

    public DecisionEngine(DecisionConfig config, Portfolio portfolio) {
        this.config = config;
        this.portfolio = portfolio;
        this.regimeDetector = new MarketRegimeDetector(config.getRegimeConfig());
        this.trendAnalyzer = new TrendAnalyzer(config.getTrendConfig());
        this.votingEngine = new VotingEngine(config.getVotingConfig());
        this.riskManager = new RiskManager(config.getRiskConfig(), portfolio);
        this.stopManager = new AdvancedStopLossManager();
        this.tradeModeClassifier = new TradeModeClassifier(ClassificationConfig.createDefault());
        this.timeframeSeries = new HashMap<>();
        initializeTimeframeSeries();
    }

    private void initializeTimeframeSeries() {
        timeframeSeries.put(Timeframe.M1, new BaseBarSeries("M1"));
        timeframeSeries.put(Timeframe.M5, new BaseBarSeries("M5"));
        timeframeSeries.put(Timeframe.M15, new BaseBarSeries("M15"));
        timeframeSeries.put(Timeframe.M30, new BaseBarSeries("M30"));
        timeframeSeries.put(Timeframe.H1, new BaseBarSeries("H1"));
        timeframeSeries.put(Timeframe.D1, new BaseBarSeries("D1"));
        timeframeSeries.put(Timeframe.W1, new BaseBarSeries("W1"));
        primarySeries = timeframeSeries.get(config.getMainLoopTimeframe());
    }

    public void setSymbol(String symbol) {
        this.currentSymbol = symbol;
    }

    public void setBarSeries(BarSeries bars, Timeframe sourceTimeframe) {
        timeframeSeries.put(sourceTimeframe, bars);
        primarySeries = timeframeSeries.computeIfAbsent(config.getMainLoopTimeframe(), tf -> new BaseBarSeries(tf.getLabel()));
        if (sourceTimeframe == config.getMainLoopTimeframe()) {
            primarySeries = bars;
        }
        aggregateToOtherTimeframes(bars, sourceTimeframe);
    }

    private void aggregateToOtherTimeframes(BarSeries sourceBars, Timeframe sourceTimeframe) {
        for (Timeframe target : Timeframe.values()) {
            if (target.getMinutes() > sourceTimeframe.getMinutes()) {
                try {
                    BarSeries aggregated = TimeframeAggregator.smartAggregate(sourceBars, sourceTimeframe, target);
                    timeframeSeries.put(target, aggregated);
                } catch (Exception ignored) {
                    // Keep the last usable aggregated series.
                }
            }
        }
    }

    public DecisionResult onBar(Bar bar) {
        if (primarySeries.getBarCount() == 0
                || bar.getEndTime().isAfter(primarySeries.getLastBar().getEndTime())) {
            primarySeries.addBar(bar);
        }
        riskManager.onNewDay(bar.getBeginTime().toLocalDate());
        updatePositionStatus();
        return executeDecisionFlow(bar);
    }

    private DecisionResult executeDecisionFlow(Bar bar) {
        double currentPrice = bar.getClosePrice().doubleValue();

        if (config.isRiskManagementEnabled()) {
            RiskViolation accountRisk = riskManager.checkAccountRisk();
            if (accountRisk != null) {
                return buildRiskResult(accountRisk, hasPosition ? DecisionResult.Action.CLOSE_POSITION : DecisionResult.Action.NO_ACTION);
            }
        }

        return hasPosition ? handleExistingPosition(currentPrice, bar) : handleNoPosition(currentPrice);
    }

    private DecisionResult handleExistingPosition(double currentPrice, Bar bar) {
        TradeMode tradeMode = resolveTradeMode();
        StopTrigger stopTrigger = stopManager.checkStopTrigger(
                "DecisionStrategy",
                currentSymbol,
                currentPrice,
                bar.getBeginTime().toLocalDateTime(),
                primarySeries);

        if (stopTrigger != null) {
            return new DecisionResult.Builder()
                    .action(DecisionResult.Action.CLOSE_POSITION)
                    .source(DecisionResult.Source.STOP_MANAGER)
                    .symbol(currentSymbol)
                    .tradeMode(tradeMode)
                    .orderType(OrderType.MARKET)
                    .orderSide(OrderSide.SELL)
                    .reason(stopTrigger.getReason())
                    .confidence(1.0)
                    .build();
        }

        if (config.isVotingEnabled()) {
            VotingResult votingResult = votingEngine.voteForExit(collectStrategySignals());
            lastVotingResult = votingResult;
            if (votingResult.isExit()) {
                return new DecisionResult.Builder()
                        .action(DecisionResult.Action.CLOSE_POSITION)
                        .source(DecisionResult.Source.VOTING_EXIT)
                        .symbol(currentSymbol)
                        .tradeMode(tradeMode)
                        .orderType(OrderType.MARKET)
                        .orderSide(OrderSide.SELL)
                        .reason(votingResult.getReason())
                        .votingResult(votingResult)
                        .confidence(votingResult.getExitScore())
                        .build();
            }
        }

        return new DecisionResult.Builder()
                .action(DecisionResult.Action.HOLD)
                .source(DecisionResult.Source.VOTING_EXIT)
                .symbol(currentSymbol)
                .tradeMode(tradeMode)
                .reason("Position remains valid")
                .confidence(0.5)
                .build();
    }

    private DecisionResult handleNoPosition(double currentPrice) {
        RegimeAnalysis regimeAnalysis = analyzeRegime();
        TrendAnalysis trendAnalysis = analyzeTrend();
        lastClassificationResult = tradeModeClassifier.classify(regimeAnalysis, trendAnalysis, null);
        TradeMode tradeMode = resolveTradeMode();

        if (regimeAnalysis != null && !regimeAnalysis.isTradeable()) {
            return new DecisionResult.Builder()
                    .action(DecisionResult.Action.NO_ACTION)
                    .source(DecisionResult.Source.REGIME_FILTER)
                    .symbol(currentSymbol)
                    .tradeMode(tradeMode)
                    .reason(regimeAnalysis.getAnalysis())
                    .regimeAnalysis(regimeAnalysis)
                    .confidence(regimeAnalysis.getConfidence())
                    .build();
        }

        if (!config.isVotingEnabled()) {
            return buildNoAction(regimeAnalysis, trendAnalysis, tradeMode, "Voting is disabled");
        }

        AllowedSide allowedSide = determineAllowedSide(regimeAnalysis, trendAnalysis);
        VotingResult votingResult = votingEngine.voteForEntry(collectStrategySignals(), allowedSide);
        lastVotingResult = votingResult;
        if (!votingResult.isEntry()) {
            return buildNoAction(regimeAnalysis, trendAnalysis, tradeMode, "No entry signal");
        }

        if (!votingResult.isLong() && !config.getRiskConfig().isAllowShortSelling()) {
            return buildRiskResult(
                    new RiskViolation(
                            RiskViolation.Type.SHORT_SELLING_DISABLED,
                            currentSymbol,
                            0.0,
                            0.0,
                            "Short selling is disabled for this platform",
                            false),
                    DecisionResult.Action.NO_ACTION);
        }

        double stopLoss = calculateStopLoss(currentPrice, votingResult.isLong(), trendAnalysis);
        double takeProfit = calculateTakeProfit(currentPrice, votingResult.isLong(), trendAnalysis);
        int quantity = riskManager.calculatePositionSize(currentPrice, stopLoss);
        double positionValue = currentPrice * quantity;

        RiskViolation sizingRisk = riskManager.checkNewPosition(currentSymbol, positionValue);
        if (sizingRisk != null) {
            return buildRiskResult(sizingRisk, DecisionResult.Action.NO_ACTION);
        }

        double volatilityPercent = regimeAnalysis != null ? regimeAnalysis.getVolatility() : 0.0;
        RiskViolation entryRisk = riskManager.checkEntrySetup(
                currentSymbol,
                currentPrice,
                stopLoss,
                takeProfit,
                volatilityPercent);
        if (entryRisk != null) {
            return buildRiskResult(entryRisk, DecisionResult.Action.NO_ACTION);
        }

        double confidence = votingResult.getLongScore() > 0 ? votingResult.getLongScore() : votingResult.getShortScore();
        return new DecisionResult.Builder()
                .action(votingResult.isLong() ? DecisionResult.Action.OPEN_LONG : DecisionResult.Action.OPEN_SHORT)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol(currentSymbol)
                .tradeMode(tradeMode)
                .orderType(OrderType.MARKET)
                .orderSide(votingResult.isLong() ? OrderSide.BUY : OrderSide.SHORT)
                .reason(votingResult.getReason())
                .suggestedStopLoss(stopLoss)
                .suggestedTakeProfit(takeProfit)
                .suggestedQuantity(quantity)
                .riskRewardRatio(calculateRiskRewardRatio(currentPrice, stopLoss, takeProfit))
                .regimeAnalysis(regimeAnalysis)
                .trendAnalysis(trendAnalysis)
                .votingResult(votingResult)
                .confidence(confidence)
                .build();
    }

    public void setStrategySignals(List<IStrategySignal> signals) {
        this.externalSignals = signals != null ? new ArrayList<>(signals) : new ArrayList<>();
    }

    private List<IStrategySignal> collectStrategySignals() {
        return new ArrayList<>(externalSignals);
    }

    private AllowedSide determineAllowedSide(RegimeAnalysis regime, TrendAnalysis trend) {
        AllowedSide regimeSide = regime != null ? regime.getAllowedSide() : AllowedSide.BOTH;
        AllowedSide trendSide = trend != null ? trend.getBiasSide() : AllowedSide.BOTH;

        if (regimeSide == AllowedSide.NONE || trendSide == AllowedSide.NONE) {
            return AllowedSide.NONE;
        }
        if (regimeSide == AllowedSide.LONG_ONLY && trendSide == AllowedSide.LONG_ONLY) {
            return AllowedSide.LONG_ONLY;
        }
        if (regimeSide == AllowedSide.SHORT_ONLY && trendSide == AllowedSide.SHORT_ONLY) {
            return AllowedSide.SHORT_ONLY;
        }
        if ((regimeSide == AllowedSide.LONG_ONLY && trendSide == AllowedSide.SHORT_ONLY)
                || (regimeSide == AllowedSide.SHORT_ONLY && trendSide == AllowedSide.LONG_ONLY)) {
            return AllowedSide.NONE;
        }
        return AllowedSide.BOTH;
    }

    private double calculateStopLoss(double entryPrice, boolean isLong, TrendAnalysis trend) {
        if (trend != null) {
            return trend.calculateStopLoss(entryPrice, isLong);
        }
        double stopDistance = entryPrice * 0.02;
        return isLong ? entryPrice - stopDistance : entryPrice + stopDistance;
    }

    private double calculateTakeProfit(double entryPrice, boolean isLong, TrendAnalysis trend) {
        if (trend != null) {
            return trend.calculateTakeProfit(entryPrice, isLong);
        }
        double profitDistance = entryPrice * 0.04;
        return isLong ? entryPrice + profitDistance : entryPrice - profitDistance;
    }

    private double calculateRiskRewardRatio(double entryPrice, double stopLoss, double takeProfit) {
        double risk = Math.abs(entryPrice - stopLoss);
        if (risk == 0.0) {
            return 0.0;
        }
        return Math.abs(takeProfit - entryPrice) / risk;
    }

    private void updatePositionStatus() {
        hasPosition = !portfolio.getPositions().isEmpty();
        votingEngine.setHasPosition(hasPosition);
    }

    public void onPositionOpened(String symbol, double entryPrice, int quantity, double stopLoss, double takeProfit) {
        this.currentSymbol = symbol;
        this.entryPrice = entryPrice;
        this.positionQuantity = quantity;
        LocalDateTime entryTime = LocalDateTime.now();
        if (primarySeries != null && primarySeries.getBarCount() > 0) {
            entryTime = primarySeries.getLastBar().getBeginTime().toLocalDateTime();
        }
        stopManager.setPositionStop("DecisionStrategy", symbol, entryPrice, entryTime, stopLoss, takeProfit);
    }

    public void onPositionClosed() {
        this.entryPrice = 0.0;
        this.positionQuantity = 0;
    }

    private RegimeAnalysis analyzeRegime() {
        if (!config.isRegimeDetectionEnabled()) {
            return null;
        }
        BarSeries weeklyBars = timeframeSeries.get(Timeframe.W1);
        if (weeklyBars != null && weeklyBars.getBarCount() > 0) {
            lastRegimeAnalysis = regimeDetector.analyze(weeklyBars);
        }
        return lastRegimeAnalysis;
    }

    private TrendAnalysis analyzeTrend() {
        if (!config.isTrendAnalysisEnabled()) {
            return null;
        }
        BarSeries dailyBars = timeframeSeries.get(Timeframe.D1);
        if (dailyBars != null && dailyBars.getBarCount() > 0) {
            lastTrendAnalysis = trendAnalyzer.analyze(dailyBars);
        }
        return lastTrendAnalysis;
    }

    private TradeMode resolveTradeMode() {
        if (lastClassificationResult == null) {
            return TradeMode.NO_TRADE;
        }
        return lastClassificationResult.getPrimaryMode();
    }

    private DecisionResult buildNoAction(
            RegimeAnalysis regimeAnalysis,
            TrendAnalysis trendAnalysis,
            TradeMode tradeMode,
            String reason) {
        return new DecisionResult.Builder()
                .action(DecisionResult.Action.NO_ACTION)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .symbol(currentSymbol)
                .tradeMode(tradeMode)
                .reason(reason)
                .regimeAnalysis(regimeAnalysis)
                .trendAnalysis(trendAnalysis)
                .confidence(0.3)
                .build();
    }

    private DecisionResult buildRiskResult(RiskViolation violation, DecisionResult.Action action) {
        TradeMode tradeMode = resolveTradeMode();
        OrderSide orderSide = action == DecisionResult.Action.CLOSE_POSITION ? OrderSide.SELL : OrderSide.BUY;
        return new DecisionResult.Builder()
                .action(action)
                .source(DecisionResult.Source.RISK_MANAGER)
                .symbol(currentSymbol)
                .tradeMode(tradeMode)
                .orderType(OrderType.MARKET)
                .orderSide(orderSide)
                .reason(violation.getMessage())
                .riskViolation(violation)
                .confidence(1.0)
                .build();
    }

    public RegimeAnalysis getLastRegimeAnalysis() {
        return lastRegimeAnalysis;
    }

    public TrendAnalysis getLastTrendAnalysis() {
        return lastTrendAnalysis;
    }

    public VotingResult getLastVotingResult() {
        return lastVotingResult;
    }

    public ClassificationResult getLastClassificationResult() {
        return lastClassificationResult;
    }

    public DecisionConfig getConfig() {
        return config;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public int getPositionQuantity() {
        return positionQuantity;
    }
}
