package com.dreamhouse.trading.util;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.model.Bar;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * K線聚合工具類
 * 將較小週期的K線聚合成較大週期的K線
 */
public class BarAggregator {

    /**
     * 聚合K線數據
     * @param sourceBars 原始K線數據（假設為1分鐘K線）
     * @param targetTimeframe 目標週期
     * @return 聚合後的K線列表
     */
    public static List<Bar> aggregate(List<Bar> sourceBars, Timeframe targetTimeframe) {
        if (sourceBars == null || sourceBars.isEmpty()) {
            return new ArrayList<>();
        }

        // 如果目標週期是M1，直接返回原始數據
        if (targetTimeframe == Timeframe.M1) {
            return new ArrayList<>(sourceBars);
        }

        List<Bar> aggregatedBars = new ArrayList<>();
        int intervalMinutes = targetTimeframe.getMinutes();

        // 第一根K線的開始時間
        if (sourceBars.isEmpty()) {
            return aggregatedBars;
        }

        LocalDateTime periodStart = alignToPeriod(sourceBars.get(0).getTimestamp(), intervalMinutes);
        double open = sourceBars.get(0).getOpen();
        double high = sourceBars.get(0).getHigh();
        double low = sourceBars.get(0).getLow();
        double close = sourceBars.get(0).getClose();
        long volume = sourceBars.get(0).getVolume();

        for (int i = 1; i < sourceBars.size(); i++) {
            Bar bar = sourceBars.get(i);
            LocalDateTime barTime = bar.getTimestamp();
            LocalDateTime barPeriodStart = alignToPeriod(barTime, intervalMinutes);

            // 如果還在同一個週期內
            if (barPeriodStart.equals(periodStart)) {
                // 更新高低收和成交量
                high = Math.max(high, bar.getHigh());
                low = Math.min(low, bar.getLow());
                close = bar.getClose();
                volume += bar.getVolume();
            } else {
                // 新週期開始，保存上一個週期的K線
                aggregatedBars.add(new Bar(periodStart, open, high, low, close, volume));

                // 開始新週期
                periodStart = barPeriodStart;
                open = bar.getOpen();
                high = bar.getHigh();
                low = bar.getLow();
                close = bar.getClose();
                volume = bar.getVolume();
            }
        }

        // 添加最後一根K線
        aggregatedBars.add(new Bar(periodStart, open, high, low, close, volume));

        return aggregatedBars;
    }

    /**
     * 將時間對齊到週期的起始時間
     * 例如：10:23 對齊到5分鐘週期 -> 10:20
     *      10:23 對齊到15分鐘週期 -> 10:15
     */
    private static LocalDateTime alignToPeriod(LocalDateTime time, int intervalMinutes) {
        if (intervalMinutes >= 1440) {
            // 日線或週線：對齊到當天0點
            return time.truncatedTo(ChronoUnit.DAYS);
        } else if (intervalMinutes >= 60) {
            // 小時線：對齊到整點
            int hours = intervalMinutes / 60;
            int alignedHour = (time.getHour() / hours) * hours;
            return time.withHour(alignedHour).withMinute(0).withSecond(0).withNano(0);
        } else {
            // 分鐘線：對齊到週期分鐘
            int minute = time.getMinute();
            int alignedMinute = (minute / intervalMinutes) * intervalMinutes;
            return time.withMinute(alignedMinute).withSecond(0).withNano(0);
        }
    }

    /**
     * 檢查是否需要聚合
     * @param sourceTimeframe 來源週期
     * @param targetTimeframe 目標週期
     * @return true表示需要聚合
     */
    public static boolean needsAggregation(Timeframe sourceTimeframe, Timeframe targetTimeframe) {
        return targetTimeframe.getMinutes() > sourceTimeframe.getMinutes();
    }

    /**
     * 獲取聚合比例
     * @param sourceTimeframe 來源週期
     * @param targetTimeframe 目標週期
     * @return 聚合比例（例如5分鐘到15分鐘返回3）
     */
    public static int getAggregationRatio(Timeframe sourceTimeframe, Timeframe targetTimeframe) {
        return targetTimeframe.getMinutes() / sourceTimeframe.getMinutes();
    }
}
