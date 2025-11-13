package com.dreamhouse.trading.core.decision;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.decision.pattern.PatternContext;
import com.dreamhouse.trading.core.decision.pattern.PatternDetector;
import com.dreamhouse.trading.core.decision.voting.VotingConfig;
import com.dreamhouse.trading.core.decision.risk.RiskConfig;
import com.dreamhouse.trading.core.decision.regime.AllowedSide;
import com.dreamhouse.trading.core.decision.regime.RegimeConfig;
import com.dreamhouse.trading.core.decision.trend.TrendConfig;
import com.dreamhouse.trading.core.decision.regime.MarketRegimeDetector;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.risk.RiskManager;
import com.dreamhouse.trading.core.decision.risk.RiskViolation;
import com.dreamhouse.trading.core.decision.signal.IStrategySignal;
import com.dreamhouse.trading.core.decision.signal.SignalType;
import com.dreamhouse.trading.core.decision.signal.StrategySignal;
import com.dreamhouse.trading.core.decision.strategies.SignalRSIStrategy;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalyzer;
import com.dreamhouse.trading.core.decision.voting.VotingEngine;
import com.dreamhouse.trading.core.decision.voting.VotingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 決策系統整合測試
 *
 * 測試完整的決策流程，從信號生成到最終決策
 */
public class DecisionSystemIntegrationTest {

    private DecisionEngine decisionEngine;
    private Portfolio portfolio;
    private BarSeries testSeries;

    @BeforeEach
    public void setUp() {
        // 建立測試用的 Portfolio
        portfolio = new Portfolio(100000.0);

        // 建立測試用的 BarSeries（模擬上升趨勢）
        testSeries = createTestBarSeries("TEST", 100);

        // 建立 DecisionEngine
        DecisionConfig config = DecisionConfig.createDefault();
        decisionEngine = new DecisionEngine(config, portfolio);
        decisionEngine.setSymbol("TEST");

        // 設定數據（所有週期暫時用同一個數據）
        decisionEngine.setBarSeries(testSeries, Timeframe.M5);
    }

    /**
     * 測試 1：基本決策流程
     */
    @Test
    public void testBasicDecisionFlow() {
        // 1. 建立信號
        List<IStrategySignal> signals = new ArrayList<>();
        signals.add(createSignal("RSI", SignalType.LONG, 0.8));
        signals.add(createSignal("MACD", SignalType.LONG, 0.7));
        signals.add(createSignal("MA", SignalType.HOLD, 0.5));

        // 2. 設定信號到引擎
        decisionEngine.setStrategySignals(signals);

        // 3. 執行決策
        Bar currentBar = testSeries.getLastBar();
        DecisionResult result = decisionEngine.onBar(currentBar);

        // 4. 驗證結果
        assertNotNull(result, "決策結果不應為 null");
        assertNotNull(result.getAction(), "決策動作不應為 null");

        System.out.println("=== 測試 1: 基本決策流程 ===");
        System.out.println(result.toString());
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 2：投票機制
     */
    @Test
    public void testVotingMechanism() {
        VotingConfig config = new VotingConfig();
        VotingEngine votingEngine = new VotingEngine(config);

        // 測試多數做多信號
        List<IStrategySignal> longSignals = new ArrayList<>();
        longSignals.add(createSignal("Strategy1", SignalType.LONG, 0.8));
        longSignals.add(createSignal("Strategy2", SignalType.LONG, 0.7));
        longSignals.add(createSignal("Strategy3", SignalType.SHORT, 0.5));

        VotingResult result = votingEngine.voteForEntry(longSignals, AllowedSide.BOTH);

        System.out.println("=== 測試 2: 投票機制 ===");
        System.out.println(result.toString());

        // 驗證投票結果
        assertTrue(result.isLong() || result.getDecision() == VotingResult.Decision.HOLD,
                "多數做多信號應該產生做多或持有決策");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 3：風險管理
     */
    @Test
    public void testRiskManagement() {
        RiskConfig config = new RiskConfig();
        RiskManager riskManager = new RiskManager(config, portfolio);

        // 測試初始狀態（無風險違規）
        RiskViolation violation = riskManager.checkAccountRisk();

        System.out.println("=== 測試 3: 風險管理 ===");
        if (violation == null) {
            System.out.println("無風險違規");
        } else {
            System.out.println(violation.toString());
        }

        assertNull(violation, "初始狀態不應有風險違規");

        // 測試倉位數量計算
        int positionSize = riskManager.calculatePositionSize(100.0, 95.0);
        System.out.println("建議倉位數量: " + positionSize);
        assertTrue(positionSize > 0, "倉位數量應大於 0");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 4：市場環境檢測（週線）
     */
    @Test
    public void testMarketRegimeDetection() {
        RegimeConfig config = new RegimeConfig();
        MarketRegimeDetector detector = new MarketRegimeDetector(config);

        // 使用上升趨勢數據
        RegimeAnalysis analysis = detector.analyze(testSeries);

        System.out.println("=== 測試 4: 市場環境檢測 ===");
        System.out.println(analysis.toString());

        assertNotNull(analysis, "環境分析結果不應為 null");
        assertNotNull(analysis.getMarketRegime(), "市場環境不應為 null");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 5：趨勢分析（日線）
     */
    @Test
    public void testTrendAnalysis() {
        TrendConfig config = new TrendConfig();
        TrendAnalyzer analyzer = new TrendAnalyzer(config);

        // 使用上升趨勢數據
        TrendAnalysis analysis = analyzer.analyze(testSeries);

        System.out.println("=== 測試 5: 趨勢分析 ===");
        System.out.println(analysis.toString());

        assertNotNull(analysis, "趨勢分析結果不應為 null");
        assertNotNull(analysis.getDirection(), "趨勢方向不應為 null");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 6：型態檢測
     */
    @Test
    public void testPatternDetection() {
        PatternDetector detector = new PatternDetector(testSeries, "TEST");

        // 檢測最後一根 K 線的型態
        int lastIndex = testSeries.getEndIndex();
        PatternContext context = detector.detect(lastIndex);

        System.out.println("=== 測試 6: 型態檢測 ===");
        System.out.println(context.toString());

        assertNotNull(context, "型態上下文不應為 null");
        assertNotNull(context.getPrimaryPattern(), "主要型態不應為 null");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 7：RSI 策略信號生成
     */
    @Test
    public void testRSIStrategySignal() {
        SignalRSIStrategy strategy = new SignalRSIStrategy();
        strategy.initialize(testSeries);

        // 測試信號生成
        int testIndex = testSeries.getEndIndex();
        Bar testBar = testSeries.getBar(testIndex);
        strategy.onBar(testIndex, testBar);

        // 驗證信號
        SignalType signal = strategy.getSignal();
        double confidence = strategy.getConfidence();

        System.out.println("=== 測試 7: RSI 策略信號 ===");
        System.out.println(String.format("信號: %s, 信心度: %.2f", signal, confidence));
        System.out.println("理由: " + strategy.getReason());

        assertNotNull(signal, "信號不應為 null");
        assertTrue(confidence >= 0.0 && confidence <= 1.0, "信心度應在 0-1 之間");
        System.out.println("通過 ✓\n");
    }

    /**
     * 測試 8：完整決策流程（包含持倉）
     */
    @Test
    public void testCompleteDecisionFlowWithPosition() {
        // 1. 模擬開倉
        portfolio.addPosition("TEST", 100, 100.0, 0.001);  // 100股, 價格100, 手續費0.1%

        // 2. 建立出場信號
        List<IStrategySignal> exitSignals = new ArrayList<>();
        exitSignals.add(createSignal("RSI", SignalType.EXIT, 0.9));
        exitSignals.add(createSignal("MACD", SignalType.SHORT, 0.8));

        decisionEngine.setStrategySignals(exitSignals);

        // 3. 執行決策
        Bar currentBar = testSeries.getLastBar();
        DecisionResult result = decisionEngine.onBar(currentBar);

        System.out.println("=== 測試 8: 完整決策流程（包含持倉）===");
        System.out.println(result.toString());

        assertNotNull(result, "決策結果不應為 null");

        // 應該產生平倉或持有決策
        assertTrue(result.getAction() == DecisionResult.Action.CLOSE_POSITION ||
                   result.getAction() == DecisionResult.Action.HOLD,
                "持倉狀態下應該考慮平倉或持有");
        System.out.println("通過 ✓\n");
    }

    // ===== 輔助方法 =====

    /**
     * 建立測試用的 BarSeries
     */
    private BarSeries createTestBarSeries(String symbol, int barCount) {
        BarSeries series = new BaseBarSeries(symbol);

        ZonedDateTime time = ZonedDateTime.now().minusDays(barCount);
        double basePrice = 100.0;

        for (int i = 0; i < barCount; i++) {
            // 模擬上升趨勢 + 隨機波動
            double trend = i * 0.2;
            double noise = (Math.random() - 0.5) * 2.0;
            double price = basePrice + trend + noise;

            double open = price;
            double high = price + Math.random() * 2.0;
            double low = price - Math.random() * 2.0;
            double close = low + (high - low) * Math.random();
            double volume = 10000 + Math.random() * 5000;

            Bar bar = new BaseBar(
                    Duration.ofMinutes(5),
                    time.plusMinutes(i * 5),
                    DecimalNum.valueOf(open),
                    DecimalNum.valueOf(high),
                    DecimalNum.valueOf(low),
                    DecimalNum.valueOf(close),
                    DecimalNum.valueOf(volume),
                    DecimalNum.valueOf(0)  // amount (default to 0)
            );

            series.addBar(bar);
        }

        return series;
    }

    /**
     * 建立測試用的策略信號
     */
    private IStrategySignal createSignal(String strategyName, SignalType signal, double confidence) {
        return new StrategySignal.Builder(strategyName, Timeframe.M5, signal)
                .confidence(confidence)
                .weight(1.0)
                .reason("測試信號")
                .build();
    }
}
