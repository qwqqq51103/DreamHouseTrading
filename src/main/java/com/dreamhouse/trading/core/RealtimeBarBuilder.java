package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 即時K線建構器
 * 從即時快照數據組合分K線
 */
public class RealtimeBarBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeBarBuilder.class);

    /**
     * 快照數據
     */
    public static class Snapshot {
        public final LocalDateTime timestamp;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final long volume;

        public Snapshot(LocalDateTime timestamp, double open, double high, double low, double close, long volume) {
            this.timestamp = timestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }

    // 每個商品的快照歷史（按時間排序）
    private final Map<String, List<Snapshot>> snapshotHistory = new ConcurrentHashMap<>();

    // 每個商品的今日開盤價
    private final Map<String, Double> todayOpenPrices = new ConcurrentHashMap<>();

    /**
     * 添加快照數據
     */
    public void addSnapshot(String symbol, LocalDateTime timestamp, double open, double high, double low, double close, long volume) {
        Snapshot snapshot = new Snapshot(timestamp, open, high, low, close, volume);

        snapshotHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(snapshot);

        // 記錄今日開盤價
        if (!todayOpenPrices.containsKey(symbol)) {
            todayOpenPrices.put(symbol, open);
        }

        logger.debug("{} - 添加快照: 時間={}, OHLC={}/{}/{}/{}, 量={}",
                symbol, timestamp, open, high, low, close, volume);
    }

    /**
     * 根據快照歷史組合分K線（真實OHLC）
     *
     * @param symbol 商品代號
     * @param timeframe 時間週期（M1, M5等）
     * @param maxBars 最多返回多少根K線
     * @return 組合出的K線列表
     */
    public List<Bar> buildBars(String symbol, Timeframe timeframe, int maxBars) {
        List<Snapshot> snapshots = snapshotHistory.get(symbol);
        if (snapshots == null || snapshots.isEmpty()) {
            logger.debug("{} - 無快照數據，無法組合K線", symbol);
            return new ArrayList<>();
        }

        // 按時間排序
        snapshots.sort(Comparator.comparing(s -> s.timestamp));

        LocalDateTime now = snapshots.get(snapshots.size() - 1).timestamp;
        LocalDateTime marketOpen = now.toLocalDate().atTime(9, 0);

        if (now.isBefore(marketOpen)) {
            logger.debug("{} - 尚未開盤，無法組合K線", symbol);
            return new ArrayList<>();
        }

        // ⭐ 將快照按K線週期分組，計算真實的OHLC
        Map<LocalDateTime, List<Snapshot>> barGroups = new LinkedHashMap<>();
        Map<LocalDateTime, Long> barVolumes = new LinkedHashMap<>();
        Long previousCumulativeVolume = null;

        for (Snapshot snapshot : snapshots) {
            // 計算這個快照屬於哪根K線
            LocalDateTime barTime = alignToTimeWindow(snapshot.timestamp, timeframe.getMinutes());
            long deltaVolume = previousCumulativeVolume == null
                    ? Math.max(0L, snapshot.volume)
                    : snapshot.volume >= previousCumulativeVolume
                            ? snapshot.volume - previousCumulativeVolume
                            : Math.max(0L, snapshot.volume);
            previousCumulativeVolume = snapshot.volume;

            if (!barTime.isBefore(marketOpen)) {
                barGroups.computeIfAbsent(barTime, k -> new ArrayList<>()).add(snapshot);
                barVolumes.merge(barTime, deltaVolume, Long::sum);
            }
        }

        // 將每組快照組合成一根K線
        List<Bar> bars = new ArrayList<>();

        for (Map.Entry<LocalDateTime, List<Snapshot>> entry : barGroups.entrySet()) {
            LocalDateTime barTime = entry.getKey();
            List<Snapshot> barSnapshots = entry.getValue();

            if (barSnapshots.isEmpty()) continue;

            // 計算真實的 OHLC
            double open = barSnapshots.get(0).close;  // 第一個快照的價格作為開盤
            double close = barSnapshots.get(barSnapshots.size() - 1).close;  // 最後一個快照作為收盤
            double high = barSnapshots.stream().mapToDouble(s -> s.high).max().orElse(close);
            double low = barSnapshots.stream().mapToDouble(s -> s.low).min().orElse(close);
            long volume = barVolumes.getOrDefault(barTime, 0L);

            Bar bar = new Bar(barTime, open, high, low, close, volume);
            bars.add(bar);
        }

        // 限制數量
        if (bars.size() > maxBars) {
            bars = new ArrayList<>(bars.subList(bars.size() - maxBars, bars.size()));
        }

        logger.info("{} - 組合完成 {} 根 {} K線（基於 {} 個快照）",
                symbol, bars.size(), timeframe.getLabel(), snapshots.size());

        return bars;
    }

    /**
     * 將時間對齊到K線窗口開始時間
     */
    private LocalDateTime alignToTimeWindow(LocalDateTime time, int periodMinutes) {
        LocalDateTime effectiveTime = time;
        if (periodMinutes > 0 && periodMinutes < Timeframe.D1.getMinutes()) {
            LocalDateTime marketClose = time.toLocalDate().atTime(13, 30);
            if (!effectiveTime.isBefore(marketClose)) {
                effectiveTime = marketClose.minusNanos(1);
            }
        }

        int totalMinutes = effectiveTime.getHour() * 60 + effectiveTime.getMinute();

        // 從09:00開始計算
        int marketOpenMinutes = 9 * 60;
        int minutesFromOpen = totalMinutes - marketOpenMinutes;

        if (minutesFromOpen < 0) {
            minutesFromOpen = 0;
        }

        // 對齊到週期
        int alignedMinutesFromOpen = (minutesFromOpen / periodMinutes) * periodMinutes;
        int alignedTotalMinutes = marketOpenMinutes + alignedMinutesFromOpen;

        int hour = alignedTotalMinutes / 60;
        int minute = alignedTotalMinutes % 60;

        return effectiveTime.toLocalDate().atTime(hour, minute);
    }

    /**
     * 清除指定商品的數據
     */
    public void clear(String symbol) {
        snapshotHistory.remove(symbol);
        todayOpenPrices.remove(symbol);
    }

    /**
     * 清除所有數據
     */
    public void clearAll() {
        snapshotHistory.clear();
        todayOpenPrices.clear();
    }

    /**
     * 獲取指定商品的快照數量
     */
    public int getSnapshotCount(String symbol) {
        List<Snapshot> snapshots = snapshotHistory.get(symbol);
        return snapshots == null ? 0 : snapshots.size();
    }
}
