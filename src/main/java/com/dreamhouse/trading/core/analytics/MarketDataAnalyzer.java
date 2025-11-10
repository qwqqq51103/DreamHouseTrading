package com.dreamhouse.trading.core.analytics;

import com.dreamhouse.trading.core.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 市場數據分析工具
 * 提供各種技術分析和統計計算功能
 */
public class MarketDataAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataAnalyzer.class);

    /**
     * 計算簡單移動平均線 (SMA)
     */
    public static List<Double> calculateSMA(List<Bar> bars, int period) {
        List<Double> sma = new ArrayList<>();

        if (bars.size() < period) {
            return sma;
        }

        for (int i = 0; i < bars.size(); i++) {
            if (i < period - 1) {
                sma.add(null);
            } else {
                double sum = 0;
                for (int j = 0; j < period; j++) {
                    sum += bars.get(i - j).getClose();
                }
                sma.add(sum / period);
            }
        }

        return sma;
    }

    /**
     * 計算指數移動平均線 (EMA)
     */
    public static List<Double> calculateEMA(List<Bar> bars, int period) {
        List<Double> ema = new ArrayList<>();

        if (bars.isEmpty()) {
            return ema;
        }

        double multiplier = 2.0 / (period + 1);
        double previousEMA = bars.get(0).getClose();
        ema.add(previousEMA);

        for (int i = 1; i < bars.size(); i++) {
            double currentEMA = (bars.get(i).getClose() - previousEMA) * multiplier + previousEMA;
            ema.add(currentEMA);
            previousEMA = currentEMA;
        }

        return ema;
    }

    /**
     * 計算相對強弱指數 (RSI)
     */
    public static List<Double> calculateRSI(List<Bar> bars, int period) {
        List<Double> rsi = new ArrayList<>();

        if (bars.size() < period + 1) {
            return rsi;
        }

        // 計算價格變化
        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < bars.size(); i++) {
            double change = bars.get(i).getClose() - bars.get(i - 1).getClose();
            gains.add(change > 0 ? change : 0);
            losses.add(change < 0 ? -change : 0);
        }

        // 初始平均值
        double avgGain = gains.subList(0, period).stream()
                             .mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = losses.subList(0, period).stream()
                              .mapToDouble(Double::doubleValue).average().orElse(0);

        rsi.add(null); // 第一個值沒有RSI
        for (int i = 0; i < period; i++) {
            rsi.add(null);
        }

        // 計算RSI
        for (int i = period; i < gains.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains.get(i)) / period;
            avgLoss = (avgLoss * (period - 1) + losses.get(i)) / period;

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsiValue = 100 - (100 / (1 + rs));
            rsi.add(rsiValue);
        }

        return rsi;
    }

    /**
     * 計算布林帶 (Bollinger Bands)
     */
    public static BollingerBands calculateBollingerBands(List<Bar> bars, int period, double stdDev) {
        BollingerBands bands = new BollingerBands();

        if (bars.size() < period) {
            return bands;
        }

        List<Double> sma = calculateSMA(bars, period);

        for (int i = 0; i < bars.size(); i++) {
            if (i < period - 1 || sma.get(i) == null) {
                bands.middle.add(null);
                bands.upper.add(null);
                bands.lower.add(null);
            } else {
                double middleBand = sma.get(i);

                // 計算標準差
                double sumSquares = 0;
                for (int j = 0; j < period; j++) {
                    double diff = bars.get(i - j).getClose() - middleBand;
                    sumSquares += diff * diff;
                }
                double standardDeviation = Math.sqrt(sumSquares / period);

                bands.middle.add(middleBand);
                bands.upper.add(middleBand + stdDev * standardDeviation);
                bands.lower.add(middleBand - stdDev * standardDeviation);
            }
        }

        return bands;
    }

    /**
     * 計算MACD指標
     */
    public static MACD calculateMACD(List<Bar> bars, int fastPeriod, int slowPeriod, int signalPeriod) {
        MACD macd = new MACD();

        if (bars.size() < slowPeriod) {
            return macd;
        }

        List<Double> fastEMA = calculateEMA(bars, fastPeriod);
        List<Double> slowEMA = calculateEMA(bars, slowPeriod);

        // MACD線 = 快線 - 慢線
        for (int i = 0; i < bars.size(); i++) {
            if (i < slowPeriod - 1) {
                macd.macdLine.add(null);
            } else {
                macd.macdLine.add(fastEMA.get(i) - slowEMA.get(i));
            }
        }

        // 信號線 = MACD的EMA
        List<Bar> macdBars = new ArrayList<>();
        for (int i = 0; i < macd.macdLine.size(); i++) {
            if (macd.macdLine.get(i) != null) {
                macdBars.add(new Bar(
                    bars.get(i).getTimestamp(),
                    macd.macdLine.get(i), macd.macdLine.get(i),
                    macd.macdLine.get(i), macd.macdLine.get(i), 0
                ));
            }
        }

        if (!macdBars.isEmpty()) {
            List<Double> signalEMA = calculateEMA(macdBars, signalPeriod);

            int offset = slowPeriod - 1;
            for (int i = 0; i < macd.macdLine.size(); i++) {
                if (i < offset) {
                    macd.signalLine.add(null);
                    macd.histogram.add(null);
                } else {
                    int idx = i - offset;
                    if (idx < signalEMA.size()) {
                        macd.signalLine.add(signalEMA.get(idx));
                        if (macd.macdLine.get(i) != null) {
                            macd.histogram.add(macd.macdLine.get(i) - signalEMA.get(idx));
                        } else {
                            macd.histogram.add(null);
                        }
                    } else {
                        macd.signalLine.add(null);
                        macd.histogram.add(null);
                    }
                }
            }
        }

        return macd;
    }

    /**
     * 計算波動率 (Volatility)
     */
    public static double calculateVolatility(List<Bar> bars, int period) {
        if (bars.size() < period) {
            return 0;
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < bars.size(); i++) {
            double ret = Math.log(bars.get(i).getClose() / bars.get(i - 1).getClose());
            returns.add(ret);
        }

        // 計算最近period個回報的標準差
        List<Double> recentReturns = returns.subList(
            Math.max(0, returns.size() - period),
            returns.size()
        );

        double mean = recentReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = recentReturns.stream()
                                      .mapToDouble(r -> Math.pow(r - mean, 2))
                                      .average()
                                      .orElse(0);

        return Math.sqrt(variance) * Math.sqrt(252); // 年化波動率
    }

    /**
     * 計算最大回撤 (Maximum Drawdown)
     */
    public static double calculateMaxDrawdown(List<Bar> bars) {
        if (bars.isEmpty()) {
            return 0;
        }

        double maxPrice = bars.get(0).getClose();
        double maxDrawdown = 0;

        for (Bar bar : bars) {
            double price = bar.getClose();
            if (price > maxPrice) {
                maxPrice = price;
            }

            double drawdown = (maxPrice - price) / maxPrice;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }

    /**
     * 計算夏普比率 (Sharpe Ratio)
     */
    public static double calculateSharpeRatio(List<Bar> bars, double riskFreeRate) {
        if (bars.size() < 2) {
            return 0;
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < bars.size(); i++) {
            double ret = (bars.get(i).getClose() - bars.get(i - 1).getClose()) /
                        bars.get(i - 1).getClose();
            returns.add(ret);
        }

        DoubleSummaryStatistics stats = returns.stream()
                                              .mapToDouble(Double::doubleValue)
                                              .summaryStatistics();

        double avgReturn = stats.getAverage();
        double stdDev = Math.sqrt(returns.stream()
                                        .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                                        .average()
                                        .orElse(0));

        if (stdDev == 0) {
            return 0;
        }

        return (avgReturn - riskFreeRate / 252) / stdDev * Math.sqrt(252);
    }

    /**
     * 檢測趨勢 (Trend Detection)
     */
    public static Trend detectTrend(List<Bar> bars, int shortPeriod, int longPeriod) {
        if (bars.size() < longPeriod) {
            return Trend.SIDEWAYS;
        }

        List<Double> shortSMA = calculateSMA(bars, shortPeriod);
        List<Double> longSMA = calculateSMA(bars, longPeriod);

        int lastIdx = bars.size() - 1;
        Double shortValue = shortSMA.get(lastIdx);
        Double longValue = longSMA.get(lastIdx);

        if (shortValue == null || longValue == null) {
            return Trend.SIDEWAYS;
        }

        double diff = (shortValue - longValue) / longValue;

        if (diff > 0.02) {
            return Trend.UPTREND;
        } else if (diff < -0.02) {
            return Trend.DOWNTREND;
        } else {
            return Trend.SIDEWAYS;
        }
    }

    /**
     * 支撐位和阻力位檢測
     */
    public static SupportResistance findSupportResistance(List<Bar> bars, int lookback) {
        if (bars.size() < lookback) {
            return new SupportResistance(0, 0);
        }

        List<Bar> recentBars = bars.subList(bars.size() - lookback, bars.size());

        double support = recentBars.stream()
                                  .mapToDouble(Bar::getLow)
                                  .min()
                                  .orElse(0);

        double resistance = recentBars.stream()
                                     .mapToDouble(Bar::getHigh)
                                     .max()
                                     .orElse(0);

        return new SupportResistance(support, resistance);
    }

    // 內部類
    public static class BollingerBands {
        public List<Double> upper = new ArrayList<>();
        public List<Double> middle = new ArrayList<>();
        public List<Double> lower = new ArrayList<>();
    }

    public static class MACD {
        public List<Double> macdLine = new ArrayList<>();
        public List<Double> signalLine = new ArrayList<>();
        public List<Double> histogram = new ArrayList<>();
    }

    public enum Trend {
        UPTREND, DOWNTREND, SIDEWAYS
    }

    public static class SupportResistance {
        public final double support;
        public final double resistance;

        public SupportResistance(double support, double resistance) {
            this.support = support;
            this.resistance = resistance;
        }

        @Override
        public String toString() {
            return String.format("Support: %.2f, Resistance: %.2f", support, resistance);
        }
    }
}
