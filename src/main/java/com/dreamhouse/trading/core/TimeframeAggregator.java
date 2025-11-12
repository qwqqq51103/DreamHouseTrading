package com.dreamhouse.trading.core;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * K 線時間週期聚合器
 *
 * 將小週期的 K 線聚合成大週期
 * 例如：5 分鐘 → 15 分鐘、30 分鐘、1 小時、日線、週線
 */
public class TimeframeAggregator {

    /**
     * 將 BarSeries 聚合到指定時間週期
     *
     * @param sourceBarSeries 原始 BarSeries（小週期）
     * @param sourceTimeframe 原始時間週期
     * @param targetTimeframe 目標時間週期
     * @return 聚合後的 BarSeries
     */
    public static BarSeries aggregate(BarSeries sourceBarSeries,
                                      Timeframe sourceTimeframe,
                                      Timeframe targetTimeframe) {
        if (sourceTimeframe == targetTimeframe) {
            return sourceBarSeries;
        }

        // 檢查目標週期是否大於原始週期
        if (targetTimeframe.getMinutes() < sourceTimeframe.getMinutes()) {
            throw new IllegalArgumentException(
                String.format("無法將 %s 聚合到更小的週期 %s",
                    sourceTimeframe.getLabel(), targetTimeframe.getLabel()));
        }

        // 計算倍數（目標週期是原始週期的幾倍）
        int multiplier = targetTimeframe.getMinutes() / sourceTimeframe.getMinutes();

        if (multiplier * sourceTimeframe.getMinutes() != targetTimeframe.getMinutes()) {
            throw new IllegalArgumentException(
                String.format("%s 無法整除聚合到 %s",
                    sourceTimeframe.getLabel(), targetTimeframe.getLabel()));
        }

        return aggregateByMultiplier(sourceBarSeries, targetTimeframe, multiplier);
    }

    /**
     * 按倍數聚合 K 線
     */
    private static BarSeries aggregateByMultiplier(BarSeries source,
                                                    Timeframe targetTimeframe,
                                                    int multiplier) {
        String newName = source.getName() + "_" + targetTimeframe.name();
        BaseBarSeries result = new BaseBarSeries(newName);

        List<Bar> buffer = new ArrayList<>();

        for (int i = 0; i < source.getBarCount(); i++) {
            Bar bar = source.getBar(i);
            buffer.add(bar);

            // 當緩衝區達到倍數時，聚合成一根新 K 線
            if (buffer.size() == multiplier) {
                Bar aggregatedBar = aggregateBars(buffer);
                result.addBar(aggregatedBar);
                buffer.clear();
            }
        }

        // 處理剩餘的 K 線（不足一個完整週期）
        if (!buffer.isEmpty()) {
            Bar aggregatedBar = aggregateBars(buffer);
            result.addBar(aggregatedBar);
        }

        return result;
    }

    /**
     * 將多根 K 線聚合成一根
     *
     * OHLCV 聚合規則：
     * - Open: 第一根的開盤價
     * - High: 所有 K 線的最高價
     * - Low: 所有 K 線的最低價
     * - Close: 最後一根的收盤價
     * - Volume: 所有 K 線的成交量總和
     */
    private static Bar aggregateBars(List<Bar> bars) {
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("無法聚合空的 K 線列表");
        }

        if (bars.size() == 1) {
            return bars.get(0);
        }

        // 第一根 K 線
        Bar firstBar = bars.get(0);
        Bar lastBar = bars.get(bars.size() - 1);

        // 開盤價：第一根的開盤價
        double open = firstBar.getOpenPrice().doubleValue();

        // 收盤價：最後一根的收盤價
        double close = lastBar.getClosePrice().doubleValue();

        // 最高價：所有 K 線的最高價
        double high = bars.stream()
            .mapToDouble(bar -> bar.getHighPrice().doubleValue())
            .max()
            .orElse(0.0);

        // 最低價：所有 K 線的最低價
        double low = bars.stream()
            .mapToDouble(bar -> bar.getLowPrice().doubleValue())
            .min()
            .orElse(0.0);

        // 成交量：所有 K 線的成交量總和
        double volume = bars.stream()
            .mapToDouble(bar -> bar.getVolume() != null ? bar.getVolume().doubleValue() : 0.0)
            .sum();

        // 成交金額：所有 K 線的成交金額總和
        double amount = bars.stream()
            .mapToDouble(bar -> {
                // 處理 amount 為 null 的情況
                if (bar.getAmount() != null) {
                    return bar.getAmount().doubleValue();
                } else {
                    // 如果沒有成交金額，用價格 × 成交量估算
                    if (bar.getVolume() != null && bar.getClosePrice() != null) {
                        return bar.getClosePrice().doubleValue() * bar.getVolume().doubleValue();
                    }
                    return 0.0;
                }
            })
            .sum();

        // 時間：使用最後一根 K 線的結束時間
        ZonedDateTime endTime = lastBar.getEndTime();

        // 計算時間跨度
        Duration duration = Duration.between(firstBar.getBeginTime(), lastBar.getEndTime());

        // 建立聚合後的 K 線
        return BaseBar.builder(DecimalNum::valueOf, Double.class)
            .timePeriod(duration)
            .endTime(endTime)
            .openPrice(open)
            .highPrice(high)
            .lowPrice(low)
            .closePrice(close)
            .volume(volume)
            .amount(amount)
            .build();
    }

    /**
     * 聚合成日線
     * 特殊處理：按日期分組聚合
     */
    public static BarSeries aggregateToDailyBars(BarSeries source) {
        String newName = source.getName() + "_D1";
        BaseBarSeries result = new BaseBarSeries(newName);

        List<Bar> dailyBuffer = new ArrayList<>();
        ZonedDateTime currentDate = null;

        for (int i = 0; i < source.getBarCount(); i++) {
            Bar bar = source.getBar(i);
            ZonedDateTime barDate = bar.getEndTime().toLocalDate().atStartOfDay(bar.getEndTime().getZone());

            // 如果是新的一天，聚合前一天的數據
            if (currentDate != null && !barDate.equals(currentDate)) {
                if (!dailyBuffer.isEmpty()) {
                    Bar dailyBar = aggregateBars(dailyBuffer);
                    result.addBar(dailyBar);
                    dailyBuffer.clear();
                }
            }

            currentDate = barDate;
            dailyBuffer.add(bar);
        }

        // 處理最後一天的數據
        if (!dailyBuffer.isEmpty()) {
            Bar dailyBar = aggregateBars(dailyBuffer);
            result.addBar(dailyBar);
        }

        return result;
    }

    /**
     * 聚合成週線
     * 特殊處理：按週分組聚合（週一到週日）
     */
    public static BarSeries aggregateToWeeklyBars(BarSeries source) {
        String newName = source.getName() + "_W1";
        BaseBarSeries result = new BaseBarSeries(newName);

        List<Bar> weeklyBuffer = new ArrayList<>();
        int currentWeek = -1;

        for (int i = 0; i < source.getBarCount(); i++) {
            Bar bar = source.getBar(i);

            // 取得該 K 線所屬的週數（年份 + 週數）
            int year = bar.getEndTime().getYear();
            int weekOfYear = bar.getEndTime().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int weekId = year * 100 + weekOfYear;

            // 如果是新的一週，聚合前一週的數據
            if (currentWeek != -1 && weekId != currentWeek) {
                if (!weeklyBuffer.isEmpty()) {
                    Bar weeklyBar = aggregateBars(weeklyBuffer);
                    result.addBar(weeklyBar);
                    weeklyBuffer.clear();
                }
            }

            currentWeek = weekId;
            weeklyBuffer.add(bar);
        }

        // 處理最後一週的數據
        if (!weeklyBuffer.isEmpty()) {
            Bar weeklyBar = aggregateBars(weeklyBuffer);
            result.addBar(weeklyBar);
        }

        return result;
    }

    /**
     * 智能聚合：根據目標時間週期自動選擇聚合方法
     */
    public static BarSeries smartAggregate(BarSeries source,
                                           Timeframe sourceTimeframe,
                                           Timeframe targetTimeframe) {
        // 如果目標是日線或週線，使用特殊的聚合方法
        if (targetTimeframe == Timeframe.D1) {
            return aggregateToDailyBars(source);
        } else if (targetTimeframe == Timeframe.W1) {
            return aggregateToWeeklyBars(source);
        } else {
            // 使用一般的倍數聚合
            return aggregate(source, sourceTimeframe, targetTimeframe);
        }
    }

    /**
     * 批量聚合：將一個 BarSeries 聚合成多個時間週期
     */
    public static TimeframeDataBundle aggregateToMultipleTimeframes(
            BarSeries source,
            Timeframe sourceTimeframe,
            List<Timeframe> targetTimeframes) {

        TimeframeDataBundle bundle = new TimeframeDataBundle(source.getName());

        // 添加原始數據
        bundle.addBarSeries(sourceTimeframe, source);

        // 聚合到各個目標週期
        for (Timeframe targetTimeframe : targetTimeframes) {
            if (targetTimeframe != sourceTimeframe) {
                try {
                    BarSeries aggregated = smartAggregate(source, sourceTimeframe, targetTimeframe);
                    bundle.addBarSeries(targetTimeframe, aggregated);

                    System.out.printf("[TimeframeAggregator] 成功聚合 %s -> %s (%d 根 K 線)\n",
                        sourceTimeframe.getLabel(), targetTimeframe.getLabel(),
                        aggregated.getBarCount());
                } catch (Exception e) {
                    System.err.printf("[TimeframeAggregator] 聚合失敗 %s -> %s: %s\n",
                        sourceTimeframe.getLabel(), targetTimeframe.getLabel(),
                        e.getMessage());
                }
            }
        }

        return bundle;
    }

    /**
     * 時間週期數據包
     * 儲存多個時間週期的 BarSeries
     */
    public static class TimeframeDataBundle {
        private final String symbol;
        private final java.util.Map<Timeframe, BarSeries> dataMap = new java.util.HashMap<>();

        public TimeframeDataBundle(String symbol) {
            this.symbol = symbol;
        }

        public void addBarSeries(Timeframe timeframe, BarSeries barSeries) {
            dataMap.put(timeframe, barSeries);
        }

        public BarSeries getBarSeries(Timeframe timeframe) {
            return dataMap.get(timeframe);
        }

        public boolean hasTimeframe(Timeframe timeframe) {
            return dataMap.containsKey(timeframe);
        }

        public java.util.Set<Timeframe> getAvailableTimeframes() {
            return dataMap.keySet();
        }

        public String getSymbol() {
            return symbol;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("TimeframeDataBundle[").append(symbol).append("]\n");
            for (Timeframe tf : dataMap.keySet()) {
                BarSeries bars = dataMap.get(tf);
                sb.append(String.format("  %s: %d bars\n", tf.getLabel(), bars.getBarCount()));
            }
            return sb.toString();
        }
    }
}
