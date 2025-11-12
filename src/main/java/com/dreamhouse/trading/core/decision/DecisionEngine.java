package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.TimeframeAggregator;
import com.dreamhouse.trading.core.decision.regime.*;
import com.dreamhouse.trading.core.decision.trend.*;
import com.dreamhouse.trading.core.decision.voting.*;
import com.dreamhouse.trading.core.decision.risk.*;
import com.dreamhouse.trading.core.decision.signal.*;
import com.dreamhouse.trading.core.backtest.AdvancedStopLossManager;
import com.dreamhouse.trading.core.backtest.AdvancedStopLossManager.StopTrigger;
import com.dreamhouse.trading.core.backtest.Portfolio;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Bar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 多週期決策引擎
 *
 * 整合所有決策模組，按照規格定義的優先順序執行決策：
 * 1. 帳戶級風控（RiskManager）
 * 2. 停損停利（AdvancedStopLossManager）
 * 3. 策略出場投票（VotingEngine EXIT）
 * 4. 策略進場投票（VotingEngine ENTRY）
 */
public class DecisionEngine {

    private final DecisionConfig config;
    private final Portfolio portfolio;

    // 子模組
    private final MarketRegimeDetector regimeDetector;
    private final TrendAnalyzer trendAnalyzer;
    private final VotingEngine votingEngine;
    private final RiskManager riskManager;
    private final AdvancedStopLossManager stopManager;

    // 多週期數據
    private final Map<Timeframe, BarSeries> timeframeSeries;
    private BarSeries primarySeries;  // 主週期（M5）

    // 狀態
    private boolean hasPosition = false;
    private String currentSymbol = "";
    private double entryPrice = 0.0;
    private int positionQuantity = 0;

    // 最近的分析結果
    private RegimeAnalysis lastRegimeAnalysis;
    private TrendAnalysis lastTrendAnalysis;
    private VotingResult lastVotingResult;

    // 統計
    private int barCount = 0;
    private int decisionCount = 0;

    // 外部策略信號（由 MultiTimeframeDecisionStrategy 提供）
    private List<IStrategySignal> externalSignals = new ArrayList<>();

    public DecisionEngine(DecisionConfig config, Portfolio portfolio) {
        this.config = config;
        this.portfolio = portfolio;

        // 初始化子模組
        this.regimeDetector = new MarketRegimeDetector(config.getRegimeConfig());
        this.trendAnalyzer = new TrendAnalyzer(config.getTrendConfig());
        this.votingEngine = new VotingEngine(config.getVotingConfig());
        this.riskManager = new RiskManager(config.getRiskConfig(), portfolio);
        this.stopManager = new AdvancedStopLossManager();

        // 初始化多週期數據容器
        this.timeframeSeries = new HashMap<>();
        initializeTimeframeSeries();
    }

    /**
     * 初始化多週期數據容器
     */
    private void initializeTimeframeSeries() {
        // 創建各週期的 BarSeries
        timeframeSeries.put(Timeframe.M1, new BaseBarSeries("M1"));
        timeframeSeries.put(Timeframe.M5, new BaseBarSeries("M5"));
        timeframeSeries.put(Timeframe.M15, new BaseBarSeries("M15"));
        timeframeSeries.put(Timeframe.M30, new BaseBarSeries("M30"));
        timeframeSeries.put(Timeframe.H1, new BaseBarSeries("H1"));
        timeframeSeries.put(Timeframe.D1, new BaseBarSeries("D1"));
        timeframeSeries.put(Timeframe.W1, new BaseBarSeries("W1"));

        // 主週期
        primarySeries = timeframeSeries.get(config.getMainLoopTimeframe());
    }

    /**
     * 設定商品代號
     */
    public void setSymbol(String symbol) {
        this.currentSymbol = symbol;
    }

    /**
     * 設定完整的 BarSeries（供回測使用）
     *
     * @param bars 最小週期（通常為 M1）的完整數據
     */
    public void setBarSeries(BarSeries bars, Timeframe sourceTimeframe) {
        // 儲存原始數據
        timeframeSeries.put(sourceTimeframe, bars);

        // 聚合到其他週期
        aggregateToOtherTimeframes(bars, sourceTimeframe);

        System.out.println(String.format("[DecisionEngine] 已載入 %d 根 %s 數據",
                bars.getBarCount(), sourceTimeframe.getLabel()));
    }

    /**
     * 聚合數據到其他週期
     */
    private void aggregateToOtherTimeframes(BarSeries sourceBars, Timeframe sourceTimeframe) {
        // 使用 TimeframeAggregator 將數據轉換為所有更大的週期
        for (Timeframe target : Timeframe.values()) {
            if (target.getMinutes() > sourceTimeframe.getMinutes()) {
                try {
                    BarSeries aggregated = TimeframeAggregator.smartAggregate(
                        sourceBars, sourceTimeframe, target);

                    timeframeSeries.put(target, aggregated);

                    System.out.println(String.format("[DecisionEngine] ✅ 成功聚合 %s -> %s (%d 根 K 線)",
                            sourceTimeframe.getLabel(), target.getLabel(), aggregated.getBarCount()));
                } catch (Exception e) {
                    System.err.println(String.format("[DecisionEngine] ❌ 聚合失敗 %s -> %s: %s",
                            sourceTimeframe.getLabel(), target.getLabel(), e.getMessage()));
                }
            }
        }
    }

    /**
     * 處理新的 K 線（主入口）
     *
     * @param bar 新的 K 線
     * @return 決策結果
     */
    public DecisionResult onBar(Bar bar) {
        barCount++;

        // 1. 更新主週期數據
        primarySeries.addBar(bar);

        // 2. 更新日期（用於每日風控重置）
        LocalDate currentDate = bar.getBeginTime().toLocalDate();
        riskManager.onNewDay(currentDate);

        // 3. 更新持倉狀態
        updatePositionStatus();

        // 4. 執行決策流程
        return executeDecisionFlow(bar);
    }

    /**
     * 執行決策流程（按優先順序）
     */
    private DecisionResult executeDecisionFlow(Bar bar) {
        double currentPrice = bar.getClosePrice().doubleValue();
        decisionCount++;

        if (config.isVerboseLogging()) {
            System.out.println(String.format("\n[DecisionEngine] ===== 第 %d 次決策 (K線#%d, 價格:%.2f) =====",
                    decisionCount, barCount, currentPrice));
        }

        // === 優先順序 1: 帳戶級風控 ===
        if (config.isRiskManagementEnabled()) {
            RiskViolation riskViolation = riskManager.checkAccountRisk();
            if (riskViolation != null) {
                return handleRiskViolation(riskViolation, currentPrice);
            }
        }

        // === 優先順序 2 & 3: 有持倉時的處理 ===
        if (hasPosition) {
            return handleExistingPosition(currentPrice, bar);
        }

        // === 優先順序 4: 無持倉時的處理 ===
        return handleNoPosition(currentPrice, bar);
    }

    /**
     * 處理風險違規
     */
    private DecisionResult handleRiskViolation(RiskViolation violation, double currentPrice) {
        System.out.println("[DecisionEngine] 風險違規！" + violation.toString());

        if (violation.shouldForceClose() && hasPosition) {
            return new DecisionResult.Builder()
                    .action(DecisionResult.Action.CLOSE_POSITION)
                    .source(DecisionResult.Source.RISK_MANAGER)
                    .reason(violation.getMessage())
                    .riskViolation(violation)
                    .confidence(1.0)
                    .build();
        }

        return new DecisionResult.Builder()
                    .action(DecisionResult.Action.NO_ACTION)
                    .source(DecisionResult.Source.RISK_MANAGER)
                    .reason(violation.getMessage())
                    .riskViolation(violation)
                    .confidence(1.0)
                    .build();
    }

    /**
     * 處理現有持倉
     */
    private DecisionResult handleExistingPosition(double currentPrice, Bar bar) {
        // === 2. 檢查停損停利 ===
        StopTrigger stopTrigger = stopManager.checkStopTrigger(
                "DecisionStrategy",
                currentSymbol,
                currentPrice,
                bar.getBeginTime().toLocalDateTime(),
                primarySeries
        );

        if (stopTrigger != null) {
            System.out.println("[DecisionEngine] 停損停利觸發：" + stopTrigger.getReason());
            return new DecisionResult.Builder()
                    .action(DecisionResult.Action.CLOSE_POSITION)
                    .source(DecisionResult.Source.STOP_MANAGER)
                    .reason(stopTrigger.getReason())
                    .confidence(1.0)
                    .build();
        }

        // === 3. 策略出場投票 ===
        if (config.isVotingEnabled()) {
            List<IStrategySignal> signals = collectStrategySignals();
            VotingResult votingResult = votingEngine.voteForExit(signals);
            lastVotingResult = votingResult;

            if (votingResult.isExit()) {
                System.out.println("[DecisionEngine] 出場投票通過：" + votingResult.toString());
                return new DecisionResult.Builder()
                        .action(DecisionResult.Action.CLOSE_POSITION)
                        .source(DecisionResult.Source.VOTING_EXIT)
                        .reason(votingResult.getReason())
                        .votingResult(votingResult)
                        .confidence(votingResult.getExitScore())
                        .build();
            }
        }

        // 繼續持有
        return new DecisionResult.Builder()
                .action(DecisionResult.Action.HOLD)
                .source(DecisionResult.Source.VOTING_EXIT)
                .reason("持倉中，無出場信號")
                .confidence(0.5)
                .build();
    }

    /**
     * 處理無持倉情況
     */
    private DecisionResult handleNoPosition(double currentPrice, Bar bar) {
        // 1. 週線環境檢測（如果啟用）
        RegimeAnalysis regimeAnalysis = null;
        if (config.isRegimeDetectionEnabled()) {
            BarSeries weeklyBars = timeframeSeries.get(Timeframe.W1);
            if (weeklyBars != null && weeklyBars.getBarCount() > 0) {
                regimeAnalysis = regimeDetector.analyze(weeklyBars);
                lastRegimeAnalysis = regimeAnalysis;

                if (!regimeAnalysis.isTradeable()) {
                    return new DecisionResult.Builder()
                            .action(DecisionResult.Action.NO_ACTION)
                            .source(DecisionResult.Source.REGIME_FILTER)
                            .reason(regimeAnalysis.getAnalysis())
                            .regimeAnalysis(regimeAnalysis)
                            .confidence(regimeAnalysis.getConfidence())
                            .build();
                }
            }
        }

        // 2. 日線趨勢分析（如果啟用）
        TrendAnalysis trendAnalysis = null;
        if (config.isTrendAnalysisEnabled()) {
            BarSeries dailyBars = timeframeSeries.get(Timeframe.D1);
            if (dailyBars != null && dailyBars.getBarCount() > 0) {
                trendAnalysis = trendAnalyzer.analyze(dailyBars);
                lastTrendAnalysis = trendAnalysis;
            }
        }

        // 3. 策略進場投票（如果啟用）
        if (config.isVotingEnabled()) {
            List<IStrategySignal> signals = collectStrategySignals();
            AllowedSide allowedSide = determineAllowedSide(regimeAnalysis, trendAnalysis);

            VotingResult votingResult = votingEngine.voteForEntry(signals, allowedSide);
            lastVotingResult = votingResult;

            if (votingResult.isEntry()) {
                // 計算停損停利
                double stopLoss = calculateStopLoss(currentPrice, votingResult.isLong(), trendAnalysis);
                double takeProfit = calculateTakeProfit(currentPrice, votingResult.isLong(), trendAnalysis);

                // 計算數量
                int quantity = riskManager.calculatePositionSize(currentPrice, stopLoss);

                // 檢查是否可以開倉
                double positionValue = currentPrice * quantity;
                RiskViolation riskCheck = riskManager.checkNewPosition(currentSymbol, positionValue);
                if (riskCheck != null) {
                    return new DecisionResult.Builder()
                            .action(DecisionResult.Action.NO_ACTION)
                            .source(DecisionResult.Source.RISK_MANAGER)
                            .reason(riskCheck.getMessage())
                            .riskViolation(riskCheck)
                            .confidence(0.0)
                            .build();
                }

                // 決定開倉方向
                DecisionResult.Action action = votingResult.isLong() ?
                        DecisionResult.Action.OPEN_LONG : DecisionResult.Action.OPEN_SHORT;

                return new DecisionResult.Builder()
                        .action(action)
                        .source(DecisionResult.Source.VOTING_ENTRY)
                        .reason(votingResult.getReason())
                        .suggestedStopLoss(stopLoss)
                        .suggestedTakeProfit(takeProfit)
                        .suggestedQuantity(quantity)
                        .regimeAnalysis(regimeAnalysis)
                        .trendAnalysis(trendAnalysis)
                        .votingResult(votingResult)
                        .confidence(votingResult.getLongScore() > 0 ? votingResult.getLongScore() : votingResult.getShortScore())
                        .build();
            }
        }

        // 無動作
        return new DecisionResult.Builder()
                .action(DecisionResult.Action.NO_ACTION)
                .source(DecisionResult.Source.VOTING_ENTRY)
                .reason("無進場信號")
                .regimeAnalysis(regimeAnalysis)
                .trendAnalysis(trendAnalysis)
                .confidence(0.3)
                .build();
    }

    /**
     * 設定外部策略信號（由 MultiTimeframeDecisionStrategy 調用）
     */
    public void setStrategySignals(List<IStrategySignal> signals) {
        this.externalSignals = signals != null ? new ArrayList<>(signals) : new ArrayList<>();
    }

    /**
     * 收集所有策略信號
     */
    private List<IStrategySignal> collectStrategySignals() {
        // 返回外部設定的信號
        return new ArrayList<>(externalSignals);
    }

    /**
     * 決定允許的交易方向
     */
    private AllowedSide determineAllowedSide(RegimeAnalysis regime, TrendAnalysis trend) {
        AllowedSide regimeSide = regime != null ? regime.getAllowedSide() : AllowedSide.BOTH;
        AllowedSide trendSide = trend != null ? trend.getBiasSide() : AllowedSide.BOTH;

        // 取兩者的交集（更保守）
        if (regimeSide == AllowedSide.NONE || trendSide == AllowedSide.NONE) {
            return AllowedSide.NONE;
        }

        if (regimeSide == AllowedSide.LONG_ONLY && trendSide == AllowedSide.LONG_ONLY) {
            return AllowedSide.LONG_ONLY;
        }

        if (regimeSide == AllowedSide.SHORT_ONLY && trendSide == AllowedSide.SHORT_ONLY) {
            return AllowedSide.SHORT_ONLY;
        }

        if ((regimeSide == AllowedSide.LONG_ONLY && trendSide == AllowedSide.SHORT_ONLY) ||
            (regimeSide == AllowedSide.SHORT_ONLY && trendSide == AllowedSide.LONG_ONLY)) {
            return AllowedSide.NONE;  // 衝突，禁止交易
        }

        return AllowedSide.BOTH;
    }

    /**
     * 計算停損價格
     */
    private double calculateStopLoss(double entryPrice, boolean isLong, TrendAnalysis trend) {
        if (trend != null) {
            return trend.calculateStopLoss(entryPrice, isLong);
        }

        // 預設 2% 停損
        double stopDistance = entryPrice * 0.02;
        return isLong ? entryPrice - stopDistance : entryPrice + stopDistance;
    }

    /**
     * 計算停利價格
     */
    private double calculateTakeProfit(double entryPrice, boolean isLong, TrendAnalysis trend) {
        if (trend != null) {
            return trend.calculateTakeProfit(entryPrice, isLong);
        }

        // 預設 4% 停利（2:1 風險報酬比）
        double profitDistance = entryPrice * 0.04;
        return isLong ? entryPrice + profitDistance : entryPrice - profitDistance;
    }

    /**
     * 更新持倉狀態
     */
    private void updatePositionStatus() {
        boolean wasHolding = hasPosition;
        hasPosition = !portfolio.getPositions().isEmpty();
        votingEngine.setHasPosition(hasPosition);

        if (!wasHolding && hasPosition) {
            System.out.println("[DecisionEngine] 已開倉");
        } else if (wasHolding && !hasPosition) {
            System.out.println("[DecisionEngine] 已平倉");
        }
    }

    /**
     * 通知開倉（由外部調用，用於設定停損停利）
     */
    public void onPositionOpened(String symbol, double entryPrice, int quantity,
                                  double stopLoss, double takeProfit) {
        this.currentSymbol = symbol;
        this.entryPrice = entryPrice;
        this.positionQuantity = quantity;

        // 取得當前時間（從最新的 Bar 中獲取）
        LocalDateTime entryTime = LocalDateTime.now();
        if (primarySeries != null && primarySeries.getBarCount() > 0) {
            entryTime = primarySeries.getLastBar().getBeginTime().toLocalDateTime();
        }

        // ✅ 修復：實際設定停損停利到 stopManager
        stopManager.setPositionStop("DecisionStrategy", symbol, entryPrice, entryTime,
                                    stopLoss, takeProfit);

        System.out.println(String.format("[DecisionEngine] 開倉記錄：%s, 價格:%.2f, 數量:%d, 停損:%.2f, 停利:%.2f",
                symbol, entryPrice, quantity, stopLoss, takeProfit));
    }

    /**
     * 通知平倉
     */
    public void onPositionClosed() {
        // AdvancedStopLossManager 在內部管理停損狀態
        System.out.println("[DecisionEngine] 平倉記錄");
    }

    // Getters
    public RegimeAnalysis getLastRegimeAnalysis() {
        return lastRegimeAnalysis;
    }

    public TrendAnalysis getLastTrendAnalysis() {
        return lastTrendAnalysis;
    }

    public VotingResult getLastVotingResult() {
        return lastVotingResult;
    }

    public DecisionConfig getConfig() {
        return config;
    }
}
