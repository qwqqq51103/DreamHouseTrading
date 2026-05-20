package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.MarketDataCollectorFeed;
import com.dreamhouse.trading.core.MarketDataFeed;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.model.Bar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a market context from the symbols that are actually tradable by the
 * current intraday monitor. This is used when real-time index/industry data is
 * unavailable or not trusted for day-trading decisions.
 */
public class InternalMarketContextService {

    public static final String INTERNAL_BENCHMARK = "WATCHLIST";
    public static final String INTERNAL_INDUSTRY = "觀察清單";

    private final MarketDataFeed dataFeed;

    public InternalMarketContextService(MarketDataFeed dataFeed) {
        this.dataFeed = dataFeed;
    }

    public MarketContextSnapshot build(Collection<String> symbols, Timeframe timeframe, int barCount) {
        return build(symbols, timeframe, barCount, null);
    }

    public MarketContextSnapshot build(Collection<String> symbols, Timeframe timeframe, int barCount, LocalDate queryDate) {
        if (dataFeed == null || symbols == null || symbols.isEmpty()) {
            return MarketContextSnapshot.empty("觀察清單內部市場資料不足");
        }
        Timeframe safeTimeframe = timeframe != null ? timeframe : Timeframe.M5;
        int safeBarCount = Math.max(20, barCount);

        List<String> normalizedSymbols = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase())
                .distinct()
                .toList();
        Map<String, List<Bar>> barsBySymbol = new LinkedHashMap<>();
        Map<String, MarketMetric> metricsBySymbol = new LinkedHashMap<>();
        for (String symbol : normalizedSymbols) {
            List<Bar> bars = fetchBars(symbol, safeTimeframe, safeBarCount, queryDate);
            if (bars.size() >= 2) {
                barsBySymbol.put(symbol, bars);
                metricsBySymbol.put(symbol, MarketContextService.metric(symbol, bars));
            }
        }
        if (metricsBySymbol.isEmpty()) {
            return MarketContextSnapshot.empty("觀察清單沒有足夠 SQL 分K資料");
        }

        List<MarketMetric> metrics = new ArrayList<>(metricsBySymbol.values());
        double averageReturn = metrics.stream().mapToDouble(MarketMetric::returnPercent).average().orElse(0.0);
        double averageVolume = metrics.stream().mapToDouble(MarketMetric::totalVolume).average().orElse(0.0);
        double vwapPassPercent = ratioPercent(metrics.stream()
                .filter(metric -> metric.vwap() > 0.0 && metric.close() > metric.vwap())
                .count(), metrics.size());
        double vwapSlopePassPercent = ratioPercent(metrics.stream()
                .filter(metric -> metric.vwapSlopePercent() > 0.0)
                .count(), metrics.size());
        double volumeSustainPercent = ratioPercent(metrics.stream()
                .filter(MarketMetric::volumeSustain)
                .count(), metrics.size());
        int newHighCount = 0;
        int newLowCount = 0;
        for (Map.Entry<String, List<Bar>> entry : barsBySymbol.entrySet()) {
            if (isNewHigh(entry.getValue())) {
                newHighCount++;
            }
            if (isNewLow(entry.getValue())) {
                newLowCount++;
            }
        }

        MarketDecision decision = resolveDecision(
                averageReturn,
                vwapPassPercent,
                volumeSustainPercent,
                newHighCount,
                newLowCount);
        MarketRegime regime = switch (decision) {
            case ALLOW_LONG -> MarketRegime.TREND_UP;
            case LIMIT_LONG -> MarketRegime.RANGE;
            case BLOCK_LONG -> MarketRegime.WEAK;
        };

        Map<String, Double> returnRanks = rankPercent(metrics, Comparator.comparingDouble(MarketMetric::returnPercent));
        Map<String, Double> volumeRanks = rankPercent(metrics, Comparator.comparingDouble(MarketMetric::totalVolume));
        Map<String, SymbolMarketContext> symbolContexts = new LinkedHashMap<>();
        for (Map.Entry<String, MarketMetric> entry : metricsBySymbol.entrySet()) {
            MarketMetric metric = entry.getValue();
            double returnRank = returnRanks.getOrDefault(entry.getKey(), 0.0);
            double volumeRank = volumeRanks.getOrDefault(entry.getKey(), 0.0);
            boolean extremeStrength = returnRank >= 90.0
                    && metric.close() > metric.vwap()
                    && metric.vwapSlopePercent() > 0.0
                    && metric.volumeSustain();
            symbolContexts.put(entry.getKey(), new SymbolMarketContext(
                    entry.getKey(),
                    INTERNAL_BENCHMARK,
                    INTERNAL_INDUSTRY,
                    metric.close(),
                    metric.returnPercent(),
                    averageReturn,
                    averageReturn,
                    metric.returnPercent() - averageReturn,
                    metric.returnPercent() - averageReturn,
                    metric.vwap(),
                    metric.vwapSlopePercent(),
                    metric.volumeSustain(),
                    extremeStrength,
                    extremeStrength ? "觀察清單極強股通過" : "未達觀察清單極強股條件",
                    returnRank,
                    volumeRank));
        }

        MarketMetric internalMetric = new MarketMetric(
                INTERNAL_BENCHMARK,
                0.0,
                averageReturn,
                0.0,
                vwapSlopePassPercent,
                volumeSustainPercent >= 40.0,
                averageVolume,
                metrics.size());
        IndustryStrength internalStrength = new IndustryStrength(
                INTERNAL_INDUSTRY,
                averageReturn,
                averageVolume,
                metrics.size(),
                (int) metrics.stream().filter(metric -> metric.returnPercent() > averageReturn).count(),
                score(vwapPassPercent, volumeSustainPercent, averageReturn),
                metrics.size(),
                metrics.size(),
                100.0,
                0.0,
                vwapPassPercent,
                volumeSustainPercent,
                "觀察清單內部市場");

        return new MarketContextSnapshot(
                regime,
                String.format("InternalMarketDecision=%s avgReturn=%.2f%% vwapPass=%.1f%% volumeSustain=%.1f%% newHigh=%d newLow=%d",
                        decision,
                        averageReturn,
                        vwapPassPercent,
                        volumeSustainPercent,
                        newHighCount,
                        newLowCount),
                internalMetric,
                internalMetric,
                symbolContexts,
                Map.of(INTERNAL_INDUSTRY, internalStrength),
                barsBySymbol,
                LocalDateTime.now());
    }

    private List<Bar> fetchBars(String symbol, Timeframe timeframe, int barCount, LocalDate queryDate) {
        try {
            List<Bar> bars;
            if (queryDate != null && dataFeed instanceof MarketDataCollectorFeed collectorFeed) {
                bars = collectorFeed.fetchHistoricalBars(symbol, timeframe, barCount, queryDate);
            } else {
                bars = dataFeed.fetchHistoricalBars(symbol, timeframe, barCount);
            }
            return bars == null
                    ? List.of()
                    : bars.stream()
                    .filter(bar -> bar != null && bar.getTimestamp() != null)
                    .sorted(Comparator.comparing(Bar::getTimestamp))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private MarketDecision resolveDecision(
            double averageReturn,
            double vwapPassPercent,
            double volumeSustainPercent,
            int newHighCount,
            int newLowCount) {
        if (vwapPassPercent < 40.0 || averageReturn <= -0.8 || newLowCount > newHighCount + 2) {
            return MarketDecision.BLOCK_LONG;
        }
        if (vwapPassPercent >= 60.0 && averageReturn >= 0.0 && volumeSustainPercent >= 20.0) {
            return MarketDecision.ALLOW_LONG;
        }
        return MarketDecision.LIMIT_LONG;
    }

    private static double ratioPercent(long count, int total) {
        return total > 0 ? count * 100.0 / total : 0.0;
    }

    private static Map<String, Double> rankPercent(List<MarketMetric> metrics, Comparator<MarketMetric> comparator) {
        List<MarketMetric> ranked = metrics.stream()
                .sorted(comparator.reversed())
                .toList();
        Map<String, Double> result = new LinkedHashMap<>();
        if (ranked.isEmpty()) {
            return result;
        }
        if (ranked.size() == 1) {
            result.put(ranked.get(0).symbol(), 100.0);
            return result;
        }
        for (int i = 0; i < ranked.size(); i++) {
            double percent = 100.0 - (i * 100.0 / (ranked.size() - 1));
            result.put(ranked.get(i).symbol(), percent);
        }
        return result;
    }

    private static boolean isNewHigh(List<Bar> bars) {
        if (bars == null || bars.size() < 3) {
            return false;
        }
        Bar last = bars.get(bars.size() - 1);
        double previousHigh = bars.subList(0, bars.size() - 1).stream()
                .mapToDouble(Bar::getHigh)
                .max()
                .orElse(Double.NaN);
        return !Double.isNaN(previousHigh) && last.getClose() >= previousHigh;
    }

    private static boolean isNewLow(List<Bar> bars) {
        if (bars == null || bars.size() < 3) {
            return false;
        }
        Bar last = bars.get(bars.size() - 1);
        double previousLow = bars.subList(0, bars.size() - 1).stream()
                .mapToDouble(Bar::getLow)
                .min()
                .orElse(Double.NaN);
        return !Double.isNaN(previousLow) && last.getClose() <= previousLow;
    }

    private static double score(double vwapPassPercent, double volumeSustainPercent, double averageReturn) {
        double returnComponent = Math.max(0.0, Math.min(1.0, 0.5 + averageReturn / 4.0));
        return Math.max(0.0, Math.min(1.0,
                vwapPassPercent / 100.0 * 0.45
                        + volumeSustainPercent / 100.0 * 0.25
                        + returnComponent * 0.30));
    }
}
