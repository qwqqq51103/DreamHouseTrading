package com.dreamhouse.trading.core.decision.classifier;

import com.dreamhouse.trading.core.decision.intraday.IntradayAnalysis;
import com.dreamhouse.trading.core.decision.regime.MarketRegime;
import com.dreamhouse.trading.core.decision.regime.RegimeAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendAnalysis;
import com.dreamhouse.trading.core.decision.trend.TrendDirection;
import com.dreamhouse.trading.core.decision.trend.TrendStrength;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易模式分類器
 * 根據多週期分析結果（週線環境、日線趨勢、盤中狀態），自動分類適合的交易模式
 */
public class TradeModeClassifier {

    private final ClassificationConfig config;
    private ClassificationResult lastResult;

    public TradeModeClassifier(ClassificationConfig config) {
        this.config = config;
    }

    /**
     * 分類交易模式
     *
     * @param regimeAnalysis 週線環境分析（可選）
     * @param trendAnalysis  日線趨勢分析（可選）
     * @param intradayAnalysis 盤中分析（可選）
     * @return 分類結果
     */
    public ClassificationResult classify(RegimeAnalysis regimeAnalysis,
                                          TrendAnalysis trendAnalysis,
                                          IntradayAnalysis intradayAnalysis) {

        // 計算各模式的得分
        double dayTradeScore = evaluateDayTradeScore(regimeAnalysis, trendAnalysis, intradayAnalysis);
        double shortSwingScore = evaluateShortSwingScore(regimeAnalysis, trendAnalysis, intradayAnalysis);
        double swingTradeScore = evaluateSwingTradeScore(regimeAnalysis, trendAnalysis, intradayAnalysis);

        // 選擇最高分的模式
        TradeMode primaryMode;
        TradeMode secondaryMode = null;
        double confidence;
        List<String> reasons = new ArrayList<>();

        if (dayTradeScore >= shortSwingScore && dayTradeScore >= swingTradeScore) {
            primaryMode = TradeMode.DAY_TRADE;
            confidence = dayTradeScore;
            reasons = buildDayTradeReasons(intradayAnalysis, trendAnalysis);

            // 次要選擇
            if (shortSwingScore > swingTradeScore && shortSwingScore >= config.getMinConfidenceThreshold()) {
                secondaryMode = TradeMode.SHORT_SWING;
            } else if (swingTradeScore >= config.getMinConfidenceThreshold()) {
                secondaryMode = TradeMode.SWING_TRADE;
            }

        } else if (shortSwingScore >= swingTradeScore) {
            primaryMode = TradeMode.SHORT_SWING;
            confidence = shortSwingScore;
            reasons = buildShortSwingReasons(trendAnalysis, intradayAnalysis);

            // 次要選擇
            if (swingTradeScore >= config.getMinConfidenceThreshold()) {
                secondaryMode = TradeMode.SWING_TRADE;
            }

        } else {
            primaryMode = TradeMode.SWING_TRADE;
            confidence = swingTradeScore;
            reasons = buildSwingTradeReasons(regimeAnalysis, trendAnalysis);

            // 次要選擇
            if (shortSwingScore >= config.getMinConfidenceThreshold()) {
                secondaryMode = TradeMode.SHORT_SWING;
            }
        }

        // 如果信心度過低，建議不交易
        if (confidence < config.getMinConfidenceThreshold()) {
            primaryMode = TradeMode.NO_TRADE;
            secondaryMode = null;
            reasons.clear();
            reasons.add("市場條件不佳");
            reasons.add(String.format("最高信心度僅%.0f%%", confidence * 100));
        }

        lastResult = new ClassificationResult.Builder()
                .primaryMode(primaryMode)
                .secondaryMode(secondaryMode)
                .confidence(confidence)
                .reasons(reasons)
                .build();

        return lastResult;
    }

    /**
     * 評估當沖交易得分
     */
    private double evaluateDayTradeScore(RegimeAnalysis regime,
                                          TrendAnalysis trend,
                                          IntradayAnalysis intraday) {
        if (intraday == null) {
            return 0.0;  // 沒有盤中數據，無法當沖
        }

        double score = 0.0;
        int criteria = 0;
        int met = 0;

        // 1. 流動性檢查（必要條件）
        criteria++;
        if (intraday.getLiquidityLevel().getLevel() >= config.getDayTradeMinLiquidityLevel()) {
            met++;
            score += 0.4;
        } else if (!config.isStrictMode()) {
            score += 0.2 * (intraday.getLiquidityLevel().getLevel() / (double) config.getDayTradeMinLiquidityLevel());
        }

        // 2. 波動度檢查（必要條件）
        criteria++;
        if (intraday.getAtrPercent() >= config.getDayTradeMinVolatility()) {
            met++;
            score += 0.3;
        } else if (!config.isStrictMode()) {
            score += 0.15 * (intraday.getAtrPercent() / config.getDayTradeMinVolatility());
        }

        // 3. 活躍時段檢查（可選條件）
        if (config.isDayTradeRequireActiveTime()) {
            criteria++;
            if (intraday.isActiveTime()) {
                met++;
                score += 0.2;
            }
        } else {
            score += 0.2;  // 不要求活躍時段，直接給分
        }

        // 4. 趨勢方向檢查（加分項）
        if (trend != null && trend.getDirection() != TrendDirection.UNCLEAR) {
            score += 0.1;
        }

        // 嚴格模式：所有必要條件都要滿足
        if (config.isStrictMode() && met < criteria) {
            return 0.0;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 評估短線交易得分
     */
    private double evaluateShortSwingScore(RegimeAnalysis regime,
                                            TrendAnalysis trend,
                                            IntradayAnalysis intraday) {
        if (trend == null) {
            return 0.0;  // 沒有趨勢數據，無法短線
        }

        double score = 0.0;
        int criteria = 0;
        int met = 0;

        // 1. 趨勢方向檢查（必要條件）
        criteria++;
        if (trend.getDirection() != TrendDirection.UNCLEAR) {
            met++;
            score += 0.4;
        }

        // 2. 趨勢信心度檢查（必要條件）
        criteria++;
        if (trend.getConfidence() >= config.getShortSwingMinTrendConfidence()) {
            met++;
            score += 0.3;
        } else if (!config.isStrictMode()) {
            score += 0.15 * (trend.getConfidence() / config.getShortSwingMinTrendConfidence());
        }

        // 3. 流動性檢查（可選條件）
        if (intraday != null) {
            if (intraday.getLiquidityLevel().getLevel() >= config.getShortSwingMinLiquidityLevel()) {
                score += 0.2;
            } else if (!config.isStrictMode()) {
                score += 0.1 * (intraday.getLiquidityLevel().getLevel() / (double) config.getShortSwingMinLiquidityLevel());
            }
        } else {
            score += 0.1;  // 沒有盤中數據，給一半分數
        }

        // 4. 波動度檢查（加分項）
        if (intraday != null && intraday.getAtrPercent() >= config.getShortSwingMinVolatility()) {
            score += 0.1;
        }

        // 嚴格模式：所有必要條件都要滿足
        if (config.isStrictMode() && met < criteria) {
            return 0.0;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 評估波段交易得分
     */
    private double evaluateSwingTradeScore(RegimeAnalysis regime,
                                            TrendAnalysis trend,
                                            IntradayAnalysis intraday) {
        if (regime == null || trend == null) {
            return 0.3;  // 沒有完整數據，給基礎分
        }

        double score = 0.0;
        int criteria = 0;
        int met = 0;

        // 1. 週線環境檢查（必要條件）
        if (config.isSwingTradeRequireBullRegime()) {
            criteria++;
            if (regime.getMarketRegime() == MarketRegime.BULL) {
                met++;
                score += 0.4;
            } else if (regime.getMarketRegime() == MarketRegime.NEUTRAL && !config.isStrictMode()) {
                score += 0.2;  // 中性環境給一半分
            }
        } else {
            score += 0.4;  // 不要求多頭環境，直接給分
        }

        // 2. 週線信心度檢查
        criteria++;
        if (regime.getConfidence() >= config.getSwingTradeMinRegimeConfidence()) {
            met++;
            score += 0.3;
        } else if (!config.isStrictMode()) {
            score += 0.15 * (regime.getConfidence() / config.getSwingTradeMinRegimeConfidence());
        }

        // 3. 趨勢強度檢查
        if (trend.getStrength() == TrendStrength.STRONG) {
            score += 0.2;
        } else if (trend.getStrength() == TrendStrength.MEDIUM) {
            score += 0.1;
        }

        // 4. 流動性檢查（基本要求）
        if (intraday != null) {
            if (intraday.getLiquidityLevel().getLevel() >= config.getSwingTradeMinLiquidityLevel()) {
                score += 0.1;
            }
        } else {
            score += 0.05;  // 沒有盤中數據，給一半分數
        }

        // 嚴格模式：所有必要條件都要滿足
        if (config.isStrictMode() && met < criteria) {
            return 0.0;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 建立當沖交易理由
     */
    private List<String> buildDayTradeReasons(IntradayAnalysis intraday, TrendAnalysis trend) {
        List<String> reasons = new ArrayList<>();

        if (intraday != null) {
            reasons.add(String.format("流動性%s", intraday.getLiquidityLevel().getDisplayName()));
            reasons.add(String.format("波動度%.1f%%", intraday.getAtrPercent() * 100));

            if (intraday.isActiveTime()) {
                reasons.add("活躍交易時段");
            }
        }

        if (trend != null && trend.getDirection() != TrendDirection.UNCLEAR) {
            reasons.add(String.format("日線%s趨勢", trend.getDirection().getDisplayName()));
        }

        return reasons;
    }

    /**
     * 建立短線交易理由
     */
    private List<String> buildShortSwingReasons(TrendAnalysis trend, IntradayAnalysis intraday) {
        List<String> reasons = new ArrayList<>();

        if (trend != null) {
            reasons.add(String.format("日線%s趨勢", trend.getDirection().getDisplayName()));
            reasons.add(String.format("趨勢強度%s", trend.getStrength().getDisplayName()));
            reasons.add(String.format("信心度%.0f%%", trend.getConfidence() * 100));
        }

        if (intraday != null) {
            reasons.add(String.format("流動性%s", intraday.getLiquidityLevel().getDisplayName()));
        }

        return reasons;
    }

    /**
     * 建立波段交易理由
     */
    private List<String> buildSwingTradeReasons(RegimeAnalysis regime, TrendAnalysis trend) {
        List<String> reasons = new ArrayList<>();

        if (regime != null) {
            reasons.add(String.format("週線%s環境", regime.getMarketRegime().getDisplayName()));
            reasons.add(String.format("環境信心度%.0f%%", regime.getConfidence() * 100));
        }

        if (trend != null) {
            reasons.add(String.format("日線趨勢強度%s", trend.getStrength().getDisplayName()));
        }

        return reasons;
    }

    /**
     * 獲取上次分類結果
     */
    public ClassificationResult getLastResult() {
        return lastResult;
    }

    /**
     * 獲取配置
     */
    public ClassificationConfig getConfig() {
        return config;
    }
}
