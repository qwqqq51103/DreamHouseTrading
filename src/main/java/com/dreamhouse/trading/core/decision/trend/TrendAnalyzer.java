package com.dreamhouse.trading.core.decision.trend;

import com.dreamhouse.trading.core.decision.regime.AllowedSide;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * 趨勢分析器（日線層）
 * 負責分析日線級別的趨勢方向、強度與波動度
 */
public class TrendAnalyzer {

    private final TrendConfig config;
    private TrendAnalysis lastAnalysis;

    public TrendAnalyzer(TrendConfig config) {
        this.config = config;
    }

    /**
     * 分析當前趨勢
     *
     * @param dailyBars 日線 K 線數據
     * @return 趨勢分析結果
     */
    public TrendAnalysis analyze(BarSeries dailyBars) {
        if (dailyBars == null || dailyBars.getBarCount() < config.getSlowMaPeriod()) {
            // 數據不足，返回不明確趨勢
            lastAnalysis = new TrendAnalysis.Builder()
                    .direction(TrendDirection.UNCLEAR)
                    .strength(TrendStrength.NONE)
                    .confidence(0.3)
                    .analysis("日線數據不足，無法判斷趨勢")
                    .build();
            return lastAnalysis;
        }

        int endIndex = dailyBars.getEndIndex();

        // 1. 計算移動平均線
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailyBars);
        SMAIndicator fastMA = new SMAIndicator(closePrice, config.getFastMaPeriod());
        SMAIndicator slowMA = new SMAIndicator(closePrice, config.getSlowMaPeriod());

        double fastValue = fastMA.getValue(endIndex).doubleValue();
        double slowValue = slowMA.getValue(endIndex).doubleValue();
        double currentPrice = closePrice.getValue(endIndex).doubleValue();

        // 2. 計算 ATR（波動度）
        ATRIndicator atr = new ATRIndicator(dailyBars, config.getAtrPeriod());
        double atrValue = atr.getValue(endIndex).doubleValue();

        // 3. 計算 RSI
        RSIIndicator rsi = new RSIIndicator(closePrice, config.getRsiPeriod());
        double rsiValue = rsi.getValue(endIndex).doubleValue();

        // 4. 計算 MA 斜率（趨勢強度）
        double maSlope = calculateSlope(fastMA, endIndex, 5);
        double maSlopeAbs = Math.abs(maSlope);

        // 5. 判斷趨勢方向
        TrendDirection direction = determineTrendDirection(
                fastValue, slowValue, currentPrice, maSlope, dailyBars, endIndex);

        // 6. 判斷趨勢強度
        TrendStrength strength = determineTrendStrength(maSlopeAbs, atrValue);

        // 7. 計算信心度
        double confidence = calculateConfidence(direction, strength, fastValue, slowValue, rsiValue);

        // 8. 計算停損距離和停利比例
        double baseStopDistance = atrValue * config.getBaseStopLossAtrMultiplier();
        double baseTakeProfitRatio = config.getBaseTakeProfitRatio();

        // 調整停利比例（根據趨勢強度）
        if (strength == TrendStrength.STRONG) {
            baseTakeProfitRatio *= 1.5;  // 強趨勢，提高停利目標
        } else if (strength == TrendStrength.WEAK) {
            baseTakeProfitRatio *= 0.75; // 弱趨勢，降低停利目標
        }

        // 9. 生成分析說明
        String analysis = generateAnalysis(direction, strength, fastValue, slowValue, rsiValue, atrValue);

        // 10. 建立分析結果
        lastAnalysis = new TrendAnalysis.Builder()
                .direction(direction)
                .strength(strength)
                .confidence(confidence)
                .atr(atrValue)
                .rsi(rsiValue)
                .baseStopDistance(baseStopDistance)
                .baseTakeProfitRatio(baseTakeProfitRatio)
                .analysis(analysis)
                .build();

        return lastAnalysis;
    }

    /**
     * 判斷趨勢方向
     */
    private TrendDirection determineTrendDirection(
            double fastMA, double slowMA, double currentPrice,
            double maSlope, BarSeries bars, int endIndex) {

        boolean goldenCross = fastMA > slowMA;
        boolean deathCross = fastMA < slowMA;
        boolean priceAboveFastMA = currentPrice > fastMA;
        boolean slopePositive = maSlope > config.getTrendSlopeThreshold();
        boolean slopeNegative = maSlope < -config.getTrendSlopeThreshold();

        // 檢查連續上漲/下跌天數
        int consecutiveUpDays = countConsecutiveDays(bars, endIndex, true);
        int consecutiveDownDays = countConsecutiveDays(bars, endIndex, false);

        // 上升趨勢判斷
        if (goldenCross && slopePositive && priceAboveFastMA) {
            return TrendDirection.UP;
        }
        if (goldenCross && consecutiveUpDays >= config.getUptrendConfirmDays()) {
            return TrendDirection.UP;
        }

        // 下降趨勢判斷
        if (deathCross && slopeNegative && !priceAboveFastMA) {
            return TrendDirection.DOWN;
        }
        if (deathCross && consecutiveDownDays >= config.getDowntrendConfirmDays()) {
            return TrendDirection.DOWN;
        }

        // 橫向整理判斷
        double priceRange = calculatePriceRange(bars, endIndex, 10);
        if (priceRange < config.getSidewayRangePercent()) {
            return TrendDirection.SIDEWAY;
        }

        return TrendDirection.UNCLEAR;
    }

    /**
     * 判斷趨勢強度
     */
    private TrendStrength determineTrendStrength(double maSlopeAbs, double atr) {
        // 根據 MA 斜率判斷
        if (maSlopeAbs >= config.getStrongTrendSlopeThreshold()) {
            return TrendStrength.STRONG;
        } else if (maSlopeAbs >= config.getTrendSlopeThreshold()) {
            return TrendStrength.MEDIUM;
        } else if (maSlopeAbs >= config.getTrendSlopeThreshold() * 0.5) {
            return TrendStrength.WEAK;
        } else {
            return TrendStrength.NONE;
        }
    }

    /**
     * 計算信心度
     */
    private double calculateConfidence(TrendDirection direction, TrendStrength strength,
                                       double fastMA, double slowMA, double rsi) {
        double confidence = 0.5;

        // 根據趨勢方向調整
        if (direction == TrendDirection.UP || direction == TrendDirection.DOWN) {
            confidence += 0.2;
        } else if (direction == TrendDirection.SIDEWAY) {
            confidence += 0.1;
        }

        // 根據趨勢強度調整
        if (strength == TrendStrength.STRONG) {
            confidence += 0.2;
        } else if (strength == TrendStrength.MEDIUM) {
            confidence += 0.1;
        }

        // 根據 MA 距離調整
        double maDivergence = Math.abs(fastMA - slowMA) / slowMA;
        if (maDivergence > 0.05) {
            confidence += 0.1;
        }

        // RSI 確認
        if (direction == TrendDirection.UP && rsi > 50 && rsi < 80) {
            confidence += 0.05;
        } else if (direction == TrendDirection.DOWN && rsi < 50 && rsi > 20) {
            confidence += 0.05;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * 計算 MA 斜率
     */
    private double calculateSlope(SMAIndicator ma, int endIndex, int lookback) {
        if (endIndex < lookback) {
            return 0.0;
        }

        double currentValue = ma.getValue(endIndex).doubleValue();
        double previousValue = ma.getValue(endIndex - lookback).doubleValue();

        return (currentValue - previousValue) / previousValue / lookback;
    }

    /**
     * 計算價格區間（百分比）
     */
    private double calculatePriceRange(BarSeries bars, int endIndex, int period) {
        if (endIndex < period) {
            return 0.0;
        }

        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (i < 0) continue;
            double high = bars.getBar(i).getHighPrice().doubleValue();
            double low = bars.getBar(i).getLowPrice().doubleValue();
            highest = Math.max(highest, high);
            lowest = Math.min(lowest, low);
        }

        double mid = (highest + lowest) / 2.0;
        return (highest - lowest) / mid;
    }

    /**
     * 計算連續上漲/下跌天數
     */
    private int countConsecutiveDays(BarSeries bars, int endIndex, boolean countUp) {
        int count = 0;
        for (int i = endIndex; i > 0; i--) {
            double close = bars.getBar(i).getClosePrice().doubleValue();
            double prevClose = bars.getBar(i - 1).getClosePrice().doubleValue();

            if (countUp && close > prevClose) {
                count++;
            } else if (!countUp && close < prevClose) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 生成分析說明
     */
    private String generateAnalysis(TrendDirection direction, TrendStrength strength,
                                     double fastMA, double slowMA, double rsi, double atr) {
        String maStatus = fastMA > slowMA ? "黃金交叉" : "死亡交叉";
        String rsiStatus;
        if (rsi >= 70) {
            rsiStatus = "超買";
        } else if (rsi <= 30) {
            rsiStatus = "超賣";
        } else if (rsi > 50) {
            rsiStatus = "偏強";
        } else {
            rsiStatus = "偏弱";
        }

        return String.format("%s%s，MA%s，RSI=%.1f(%s)，ATR=%.2f",
                direction.getDisplayName(),
                strength.getDisplayName(),
                maStatus,
                rsi,
                rsiStatus,
                atr);
    }

    /**
     * 獲取最後一次分析結果
     */
    public TrendAnalysis getLastAnalysis() {
        return lastAnalysis;
    }

    public TrendConfig getConfig() {
        return config;
    }
}
