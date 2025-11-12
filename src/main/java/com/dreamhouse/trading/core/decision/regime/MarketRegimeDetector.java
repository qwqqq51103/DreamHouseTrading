package com.dreamhouse.trading.core.decision.regime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * 市場環境檢測器（週線層）
 * 負責分析週線級別的市場結構，判斷大趨勢方向與風險等級
 */
public class MarketRegimeDetector {

    private final RegimeConfig config;
    private RegimeAnalysis lastAnalysis;

    public MarketRegimeDetector(RegimeConfig config) {
        this.config = config;
    }

    /**
     * 分析當前市場環境
     *
     * @param weeklyBars 週線 K 線數據
     * @return 市場環境分析結果
     */
    public RegimeAnalysis analyze(BarSeries weeklyBars) {
        if (weeklyBars == null || weeklyBars.getBarCount() < config.getLongMaPeriod()) {
            // 數據不足，返回中性環境
            lastAnalysis = new RegimeAnalysis.Builder()
                    .marketRegime(MarketRegime.NEUTRAL)
                    .allowedSide(AllowedSide.BOTH)
                    .confidence(0.3)
                    .analysis("週線數據不足，無法判斷環境")
                    .build();
            return lastAnalysis;
        }

        int endIndex = weeklyBars.getEndIndex();

        // 1. 計算移動平均線
        ClosePriceIndicator closePrice = new ClosePriceIndicator(weeklyBars);
        SMAIndicator shortMA = new SMAIndicator(closePrice, config.getShortMaPeriod());
        SMAIndicator mediumMA = new SMAIndicator(closePrice, config.getMediumMaPeriod());
        SMAIndicator longMA = new SMAIndicator(closePrice, config.getLongMaPeriod());

        double shortValue = shortMA.getValue(endIndex).doubleValue();
        double mediumValue = mediumMA.getValue(endIndex).doubleValue();
        double longValue = longMA.getValue(endIndex).doubleValue();
        double currentPrice = closePrice.getValue(endIndex).doubleValue();

        // 2. 計算 ADX（趨勢強度）
        ADXIndicator adx = new ADXIndicator(weeklyBars, config.getAdxPeriod());
        double adxValue = adx.getValue(endIndex).doubleValue();

        // 3. 計算波動率（使用價格標準差）
        double volatility = calculateVolatility(weeklyBars, 20);

        // 4. 判斷趨勢方向
        boolean maAlignedUp = shortValue > mediumValue && mediumValue > longValue;
        boolean maAlignedDown = shortValue < mediumValue && mediumValue < longValue;
        boolean priceAboveLongMA = currentPrice > longValue;

        // 5. 判斷市場環境
        MarketRegime regime;
        double confidence;
        String analysis;

        if (adxValue >= config.getStrongTrendThreshold()) {
            // 強趨勢
            if (maAlignedUp && priceAboveLongMA) {
                regime = MarketRegime.BULL;
                confidence = Math.min(0.9, adxValue / 50.0);
                analysis = String.format("強勢牛市，MA排列順暢，ADX=%.1f", adxValue);
            } else if (maAlignedDown && !priceAboveLongMA) {
                regime = MarketRegime.BEAR;
                confidence = Math.min(0.9, adxValue / 50.0);
                analysis = String.format("強勢熊市，MA空頭排列，ADX=%.1f", adxValue);
            } else {
                // 強趨勢但方向不明確
                regime = MarketRegime.NEUTRAL;
                confidence = 0.5;
                analysis = String.format("趨勢強但方向混亂，ADX=%.1f", adxValue);
            }
        } else if (adxValue >= config.getTrendThreshold()) {
            // 中等趨勢
            if (priceAboveLongMA && shortValue > longValue) {
                regime = MarketRegime.BULL;
                confidence = 0.6;
                analysis = String.format("中性偏多，價格在長MA之上，ADX=%.1f", adxValue);
            } else if (!priceAboveLongMA && shortValue < longValue) {
                regime = MarketRegime.BEAR;
                confidence = 0.6;
                analysis = String.format("中性偏空，價格在長MA之下，ADX=%.1f", adxValue);
            } else {
                regime = MarketRegime.NEUTRAL;
                confidence = 0.5;
                analysis = String.format("中性盤整，無明確方向，ADX=%.1f", adxValue);
            }
        } else {
            // 弱趨勢或無趨勢
            if (volatility < config.getLowVolatilityThreshold()) {
                regime = MarketRegime.NO_TRADE;
                confidence = 0.7;
                analysis = String.format("低波動盤整，不建議交易，ADX=%.1f, Vol=%.2f", adxValue, volatility);
            } else {
                regime = MarketRegime.NEUTRAL;
                confidence = 0.4;
                analysis = String.format("橫盤震盪，無明確趨勢，ADX=%.1f", adxValue);
            }
        }

        // 6. 計算建議風險等級
        double maxRiskLevel = calculateRiskLevel(regime, adxValue, volatility);

        // 7. 建立分析結果
        lastAnalysis = new RegimeAnalysis.Builder()
                .marketRegime(regime)
                .allowedSide(AllowedSide.fromRegime(regime))
                .confidence(confidence)
                .trendStrength(adxValue)
                .volatility(volatility)
                .maxRiskLevel(maxRiskLevel)
                .analysis(analysis)
                .build();

        return lastAnalysis;
    }

    /**
     * 計算波動率（價格標準差）
     */
    private double calculateVolatility(BarSeries bars, int period) {
        int endIndex = bars.getEndIndex();
        if (bars.getBarCount() < period) {
            return 0.0;
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(bars);
        double sum = 0.0;
        double sumSquares = 0.0;
        int count = 0;

        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (i < 0) continue;
            double price = closePrice.getValue(i).doubleValue();
            sum += price;
            sumSquares += price * price;
            count++;
        }

        if (count == 0) return 0.0;

        double mean = sum / count;
        double variance = (sumSquares / count) - (mean * mean);
        double stdDev = Math.sqrt(Math.abs(variance));

        // 返回相對波動率（標準差 / 平均價格）
        return stdDev / mean;
    }

    /**
     * 計算建議的最大風險等級
     * 根據市場環境、趨勢強度和波動率決定
     */
    private double calculateRiskLevel(MarketRegime regime, double adxValue, double volatility) {
        double baseRisk = 0.5;

        // 根據市場環境調整
        switch (regime) {
            case BULL:
            case BEAR:
                baseRisk = 0.7;  // 趨勢明確，可提高風險
                break;
            case NEUTRAL:
                baseRisk = 0.5;  // 中性，適中風險
                break;
            case NO_TRADE:
                baseRisk = 0.2;  // 不適合交易，降低風險
                break;
        }

        // 根據 ADX 調整（趨勢越強，風險可越高）
        if (adxValue >= 40) {
            baseRisk += 0.2;
        } else if (adxValue >= 25) {
            baseRisk += 0.1;
        } else {
            baseRisk -= 0.1;
        }

        // 根據波動率調整（波動越大，風險應降低）
        if (volatility > 0.05) {
            baseRisk -= 0.2;
        } else if (volatility > 0.03) {
            baseRisk -= 0.1;
        }

        return Math.max(0.1, Math.min(1.0, baseRisk));
    }

    /**
     * 獲取最後一次分析結果
     */
    public RegimeAnalysis getLastAnalysis() {
        return lastAnalysis;
    }

    public RegimeConfig getConfig() {
        return config;
    }
}
