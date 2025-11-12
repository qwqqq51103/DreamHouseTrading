package com.dreamhouse.trading.core.decision.intraday;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * 盤中分析器（分鐘層）
 * 負責分析分鐘級別的流動性、波動度與交易時段
 * 判斷當前市場是否適合當沖、短線或波段交易
 */
public class IntradayAnalyzer {

    private final IntradayConfig config;
    private IntradayAnalysis lastAnalysis;

    public IntradayAnalyzer(IntradayConfig config) {
        this.config = config;
    }

    /**
     * 分析當前盤中狀態
     *
     * @param minuteBars 分鐘 K 線數據（建議使用 5 分鐘或更短週期）
     * @return 盤中分析結果
     */
    public IntradayAnalysis analyze(BarSeries minuteBars) {
        if (minuteBars == null || minuteBars.getBarCount() < config.getAtrPeriod()) {
            // 數據不足，返回保守結果
            lastAnalysis = new IntradayAnalysis.Builder()
                    .liquidityLevel(LiquidityLevel.LOW)
                    .suitableForDayTrade(false)
                    .suitableForShortSwing(false)
                    .suitableForSwingTrade(true)
                    .analysis("分鐘線數據不足，無法判斷盤中狀態")
                    .build();
            return lastAnalysis;
        }

        int endIndex = minuteBars.getEndIndex();

        // 1. 計算平均成交量
        double avgVolume = calculateAverageVolume(minuteBars, endIndex);

        // 2. 計算點差百分比
        double spreadPercent = calculateSpreadPercent(minuteBars, endIndex);

        // 3. 評估流動性等級
        LiquidityLevel liquidity = assessLiquidity(avgVolume, spreadPercent);

        // 4. 計算 ATR 百分比（波動度）
        double atrPercent = calculateATRPercent(minuteBars, endIndex);

        // 5. 檢查是否為活躍交易時段
        boolean isActiveTime = checkActiveTime(minuteBars, endIndex);

        // 6. 綜合判斷適合的交易模式
        boolean suitableForDayTrade = evaluateDayTradeSuitability(
                liquidity, atrPercent, isActiveTime);

        boolean suitableForShortSwing = evaluateShortSwingSuitability(
                liquidity, atrPercent);

        boolean suitableForSwingTrade = evaluateSwingTradeSuitability(
                liquidity);

        // 7. 生成分析說明
        String analysis = generateAnalysis(liquidity, avgVolume, spreadPercent,
                atrPercent, isActiveTime, suitableForDayTrade,
                suitableForShortSwing, suitableForSwingTrade);

        lastAnalysis = new IntradayAnalysis.Builder()
                .liquidityLevel(liquidity)
                .averageVolume(avgVolume)
                .spreadPercent(spreadPercent)
                .atrPercent(atrPercent)
                .isActiveTime(isActiveTime)
                .suitableForDayTrade(suitableForDayTrade)
                .suitableForShortSwing(suitableForShortSwing)
                .suitableForSwingTrade(suitableForSwingTrade)
                .analysis(analysis)
                .build();

        return lastAnalysis;
    }

    /**
     * 計算平均成交量
     */
    private double calculateAverageVolume(BarSeries bars, int endIndex) {
        int window = Math.min(config.getVolumeWindow(), endIndex + 1);
        int startIndex = endIndex - window + 1;

        double totalVolume = 0.0;
        for (int i = startIndex; i <= endIndex; i++) {
            Bar bar = bars.getBar(i);
            if (bar.getVolume() != null) {
                totalVolume += bar.getVolume().doubleValue();
            }
        }

        return totalVolume / window;
    }

    /**
     * 計算點差百分比
     * spread = (high - low) / close
     */
    private double calculateSpreadPercent(BarSeries bars, int endIndex) {
        HighPriceIndicator high = new HighPriceIndicator(bars);
        LowPriceIndicator low = new LowPriceIndicator(bars);
        ClosePriceIndicator close = new ClosePriceIndicator(bars);

        double highValue = high.getValue(endIndex).doubleValue();
        double lowValue = low.getValue(endIndex).doubleValue();
        double closeValue = close.getValue(endIndex).doubleValue();

        if (closeValue == 0.0) {
            return 0.0;
        }

        return (highValue - lowValue) / closeValue;
    }

    /**
     * 評估流動性等級
     */
    private LiquidityLevel assessLiquidity(double avgVolume, double spreadPercent) {
        // 同時考慮成交量和點差
        // 成交量越大越好，點差越小越好

        if (avgVolume >= config.getVeryHighVolumeThreshold() &&
            spreadPercent <= config.getVeryHighSpreadThreshold()) {
            return LiquidityLevel.VERY_HIGH;
        }

        if (avgVolume >= config.getHighVolumeThreshold() &&
            spreadPercent <= config.getHighSpreadThreshold()) {
            return LiquidityLevel.HIGH;
        }

        if (avgVolume >= config.getMediumVolumeThreshold() &&
            spreadPercent <= config.getMediumSpreadThreshold()) {
            return LiquidityLevel.MEDIUM;
        }

        if (avgVolume >= config.getLowVolumeThreshold() &&
            spreadPercent <= config.getLowSpreadThreshold()) {
            return LiquidityLevel.LOW;
        }

        return LiquidityLevel.VERY_LOW;
    }

    /**
     * 計算 ATR 百分比（波動度）
     */
    private double calculateATRPercent(BarSeries bars, int endIndex) {
        ATRIndicator atr = new ATRIndicator(bars, config.getAtrPeriod());
        ClosePriceIndicator close = new ClosePriceIndicator(bars);

        double atrValue = atr.getValue(endIndex).doubleValue();
        double closeValue = close.getValue(endIndex).doubleValue();

        if (closeValue == 0.0) {
            return 0.0;
        }

        return atrValue / closeValue;
    }

    /**
     * 檢查是否為活躍交易時段
     */
    private boolean checkActiveTime(BarSeries bars, int endIndex) {
        if (!config.isEnableTimeFilter()) {
            return true;  // 未啟用時間過濾，全時段可交易
        }

        Bar currentBar = bars.getBar(endIndex);
        ZonedDateTime barTime = currentBar.getEndTime();
        LocalTime time = barTime.toLocalTime();

        // 台股交易時段：09:00 - 13:30
        LocalTime marketOpen = LocalTime.of(9, 0);
        LocalTime marketClose = LocalTime.of(13, 30);

        // 避開開盤後的混亂期
        LocalTime safeStartTime = marketOpen.plusMinutes(config.getAvoidMinutesAfterOpen());

        // 避開收盤前的尾盤
        LocalTime safeEndTime = marketClose.minusMinutes(config.getAvoidMinutesBeforeClose());

        // 檢查是否在安全交易時段內
        return time.isAfter(safeStartTime) && time.isBefore(safeEndTime);
    }

    /**
     * 評估當沖適合度
     */
    private boolean evaluateDayTradeSuitability(LiquidityLevel liquidity,
                                                  double atrPercent,
                                                  boolean isActiveTime) {
        // 當沖需要：高流動性 + 適度波動 + 活躍時段
        return liquidity.isSuitableForDayTrading() &&
               atrPercent >= config.getMediumVolatilityThreshold() &&
               isActiveTime;
    }

    /**
     * 評估短線適合度
     */
    private boolean evaluateShortSwingSuitability(LiquidityLevel liquidity,
                                                    double atrPercent) {
        // 短線需要：中等以上流動性 + 適度波動
        return liquidity.isSuitableForShortSwing() &&
               atrPercent >= config.getLowVolatilityThreshold();
    }

    /**
     * 評估波段適合度
     */
    private boolean evaluateSwingTradeSuitability(LiquidityLevel liquidity) {
        // 波段只需要基本流動性即可
        return liquidity.isSuitableForSwingTrading();
    }

    /**
     * 生成分析說明
     */
    private String generateAnalysis(LiquidityLevel liquidity,
                                      double avgVolume,
                                      double spreadPercent,
                                      double atrPercent,
                                      boolean isActiveTime,
                                      boolean suitableForDayTrade,
                                      boolean suitableForShortSwing,
                                      boolean suitableForSwingTrade) {
        StringBuilder sb = new StringBuilder();

        sb.append("盤中分析：");
        sb.append(String.format("流動性=%s", liquidity.getDisplayName()));
        sb.append(String.format("，平均量=%.0f", avgVolume));
        sb.append(String.format("，點差=%.2f%%", spreadPercent * 100));
        sb.append(String.format("，ATR=%.2f%%", atrPercent * 100));

        if (!isActiveTime && config.isEnableTimeFilter()) {
            sb.append("，非活躍時段");
        }

        sb.append(" → 適合：");

        if (suitableForDayTrade) {
            sb.append("當沖 ");
        }
        if (suitableForShortSwing) {
            sb.append("短線 ");
        }
        if (suitableForSwingTrade) {
            sb.append("波段");
        }
        if (!suitableForDayTrade && !suitableForShortSwing && !suitableForSwingTrade) {
            sb.append("不建議交易");
        }

        return sb.toString();
    }

    /**
     * 獲取上次分析結果
     */
    public IntradayAnalysis getLastAnalysis() {
        return lastAnalysis;
    }

    /**
     * 獲取配置
     */
    public IntradayConfig getConfig() {
        return config;
    }
}
