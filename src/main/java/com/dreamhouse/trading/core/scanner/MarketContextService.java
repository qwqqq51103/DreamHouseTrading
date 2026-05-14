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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds market and industry context from local SQL-backed bars.
 */
public class MarketContextService {

    public static final String TAIEX_SYMBOL = "TAIEX";
    public static final String TPEX_SYMBOL = "TPEx";
    public static final double DEFAULT_WEAK_OUTPERFORM_INDEX_PERCENT = 0.30;
    public static final double DEFAULT_WEAK_OUTPERFORM_INDUSTRY_PERCENT = 0.20;

    private static final List<String> TAIEX_ALIASES = List.of("TAIEX", "TAIEX.TW", "^TWII");
    private static final List<String> TPEX_ALIASES = List.of("TPEx", "TPEX", "TPEx.TW", "OTC");

    private final MarketDataFeed dataFeed;
    private final Function<String, String> industryResolver;

    public MarketContextService(MarketDataFeed dataFeed, Function<String, String> industryResolver) {
        this.dataFeed = dataFeed;
        this.industryResolver = industryResolver != null ? industryResolver : symbol -> "未分類";
    }

    public MarketContextSnapshot build(Collection<String> symbols, Timeframe timeframe, int barCount) {
        return build(symbols, timeframe, barCount, null);
    }

    public MarketContextSnapshot build(Collection<String> symbols, Timeframe timeframe, int barCount, LocalDate queryDate) {
        return build(symbols, timeframe, barCount, queryDate, Map.of(), Map.of());
    }

    public MarketContextSnapshot build(
            Collection<String> symbols,
            Timeframe timeframe,
            int barCount,
            LocalDate queryDate,
            Map<String, String> industryUniverse,
            Map<String, Integer> industryTotalCounts) {
        if (dataFeed == null || symbols == null) {
            return MarketContextSnapshot.empty("market data source missing");
        }
        Timeframe safeTimeframe = timeframe != null ? timeframe : Timeframe.M1;
        int safeBarCount = Math.max(20, barCount);

        List<String> watchSymbols = symbols.stream()
                .map(this::normalize)
                .filter(symbol -> !symbol.isBlank() && !isIndexSymbol(symbol))
                .distinct()
                .toList();
        Map<String, String> normalizedUniverse = normalizeIndustryUniverse(industryUniverse);
        Map<String, Integer> totalCounts = normalizeIndustryTotals(industryTotalCounts);

        List<String> symbolsToEvaluate = new ArrayList<>(watchSymbols);
        for (String symbol : normalizedUniverse.keySet()) {
            if (!symbolsToEvaluate.contains(symbol)) {
                symbolsToEvaluate.add(symbol);
            }
        }

        Map<String, List<Bar>> barsBySymbol = new LinkedHashMap<>();
        for (String symbol : symbolsToEvaluate) {
            List<Bar> bars = fetchBars(symbol, safeTimeframe, safeBarCount, queryDate);
            if (!bars.isEmpty()) {
                barsBySymbol.put(symbol, bars);
            }
        }

        IndexBars taiexBars = fetchFirstAvailable(TAIEX_ALIASES, safeTimeframe, safeBarCount, queryDate);
        IndexBars tpexBars = fetchFirstAvailable(TPEX_ALIASES, safeTimeframe, safeBarCount, queryDate);
        MarketMetric taiexMetric = metric(taiexBars.symbol(), taiexBars.bars());
        MarketMetric tpexMetric = metric(tpexBars.symbol(), tpexBars.bars());
        MarketRegime regime = resolveRegime(taiexMetric, tpexMetric);
        if (regime == MarketRegime.DATA_MISSING) {
            return new MarketContextSnapshot(
                    regime,
                    "TAIEX/TPEx data missing",
                    taiexMetric,
                    tpexMetric,
                    Map.of(),
                    Map.of(),
                    barsBySymbol,
                    LocalDateTime.now());
        }

        Map<String, String> industryBySymbol = new HashMap<>();
        Map<String, MarketMetric> metricsBySymbol = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bar>> entry : barsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            String industry = normalizedUniverse.getOrDefault(symbol, normalizeIndustry(industryResolver.apply(symbol)));
            industryBySymbol.put(symbol, industry);
            metricsBySymbol.put(symbol, metric(symbol, entry.getValue()));
        }

        Map<String, IndustryStrength> industries = buildIndustryStrength(
                metricsBySymbol,
                industryBySymbol,
                totalCounts,
                taiexMetric,
                tpexMetric);
        Map<String, SymbolMarketContext> symbolContexts = new LinkedHashMap<>();
        for (String symbol : watchSymbols) {
            MarketMetric stock = metricsBySymbol.get(symbol);
            if (stock == null) {
                continue;
            }
            String benchmarkSymbol = resolveBenchmarkSymbol(symbol);
            MarketMetric benchmark = TPEX_SYMBOL.equals(benchmarkSymbol) ? tpexMetric : taiexMetric;
            String industry = industryBySymbol.getOrDefault(symbol, "未分類");
            IndustryStrength industryStrength = industries.getOrDefault(industry, IndustryStrength.unknown());

            double relativeIndex = stock.returnPercent() - benchmark.returnPercent();
            double relativeIndustry = industryStrength.hasData()
                    ? stock.returnPercent() - industryStrength.averageReturnPercent()
                    : 0.0;
            WeakQualification weak = qualifyWeakMarket(stock, benchmark, industryStrength, relativeIndex, relativeIndustry);
            symbolContexts.put(symbol, new SymbolMarketContext(
                    symbol,
                    benchmarkSymbol,
                    industry,
                    stock.close(),
                    stock.returnPercent(),
                    benchmark.returnPercent(),
                    industryStrength.averageReturnPercent(),
                    relativeIndex,
                    relativeIndustry,
                    stock.vwap(),
                    stock.vwapSlopePercent(),
                    stock.volumeSustain(),
                    weak.qualified(),
                    weak.reason()));
        }

        return new MarketContextSnapshot(
                regime,
                describeRegime(regime, taiexMetric, tpexMetric),
                taiexMetric,
                tpexMetric,
                symbolContexts,
                industries,
                barsBySymbol,
                LocalDateTime.now());
    }

    public static MarketMetric metric(String symbol, List<Bar> bars) {
        List<Bar> sorted = sortedBars(bars);
        if (sorted.isEmpty()) {
            return MarketMetric.empty(symbol);
        }
        Bar first = sorted.get(0);
        Bar last = sorted.get(sorted.size() - 1);
        double firstPrice = first.getOpen() > 0.0 ? first.getOpen() : first.getClose();
        double returnPercent = firstPrice > 0.0 ? ((last.getClose() - firstPrice) / firstPrice) * 100.0 : 0.0;
        double vwap = calculateVwap(sorted, sorted.size());
        double previousVwap = calculateVwap(sorted, Math.max(1, sorted.size() - 5));
        double vwapSlopePercent = previousVwap > 0.0 ? ((vwap - previousVwap) / previousVwap) * 100.0 : 0.0;
        double totalVolume = sorted.stream().mapToDouble(Bar::getVolume).sum();
        return new MarketMetric(
                symbol != null ? symbol : "",
                last.getClose(),
                returnPercent,
                vwap,
                vwapSlopePercent,
                isVolumeSustained(sorted),
                totalVolume,
                sorted.size());
    }

    public static boolean isVolumeSustained(List<Bar> bars) {
        List<Bar> sorted = sortedBars(bars);
        if (sorted.size() < 8) {
            return false;
        }
        int sustainBars = Math.min(5, Math.max(3, sorted.size() / 20));
        int lookbackEnd = sorted.size() - sustainBars;
        int lookbackStart = Math.max(0, lookbackEnd - 20);
        if (lookbackEnd <= lookbackStart) {
            return false;
        }
        double baseline = sorted.subList(lookbackStart, lookbackEnd).stream()
                .mapToLong(Bar::getVolume)
                .average()
                .orElse(0.0);
        if (baseline <= 0.0) {
            return false;
        }
        List<Bar> recent = sorted.subList(lookbackEnd, sorted.size());
        long strongVolumeBars = recent.stream()
                .filter(bar -> bar.getVolume() >= baseline * 1.10)
                .count();
        Bar firstRecent = recent.get(0);
        Bar lastRecent = recent.get(recent.size() - 1);
        boolean priceHeld = lastRecent.getClose() >= Math.min(firstRecent.getOpen(), firstRecent.getClose());
        return strongVolumeBars >= Math.max(2, sustainBars - 1) && priceHeld;
    }

    private List<Bar> fetchBars(String symbol, Timeframe timeframe, int barCount, LocalDate queryDate) {
        try {
            if (queryDate != null && dataFeed instanceof MarketDataCollectorFeed collectorFeed) {
                return sortedBars(collectorFeed.fetchHistoricalBars(symbol, timeframe, barCount, queryDate));
            }
            return sortedBars(dataFeed.fetchHistoricalBars(symbol, timeframe, barCount));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private IndexBars fetchFirstAvailable(List<String> aliases, Timeframe timeframe, int barCount, LocalDate queryDate) {
        for (String alias : aliases) {
            List<Bar> bars = fetchBars(alias, timeframe, barCount, queryDate);
            if (!bars.isEmpty()) {
                return new IndexBars(alias, bars);
            }
        }
        return new IndexBars(aliases.isEmpty() ? "" : aliases.get(0), List.of());
    }

    private MarketRegime resolveRegime(MarketMetric taiex, MarketMetric tpex) {
        List<MarketMetric> metrics = List.of(taiex, tpex).stream()
                .filter(MarketMetric::hasData)
                .toList();
        if (metrics.isEmpty()) {
            return MarketRegime.DATA_MISSING;
        }
        double averageReturn = metrics.stream().mapToDouble(MarketMetric::returnPercent).average().orElse(0.0);
        long weakStructureCount = metrics.stream()
                .filter(metric -> metric.close() < metric.vwap() && metric.vwapSlopePercent() < 0.0)
                .count();
        long strongStructureCount = metrics.stream()
                .filter(metric -> metric.close() > metric.vwap() && metric.vwapSlopePercent() > 0.0)
                .count();
        if (averageReturn <= -0.30 || weakStructureCount == metrics.size()) {
            return MarketRegime.WEAK;
        }
        if (averageReturn >= 0.30 && strongStructureCount > 0) {
            return MarketRegime.TREND_UP;
        }
        return MarketRegime.RANGE;
    }

    private Map<String, IndustryStrength> buildIndustryStrength(
            Map<String, MarketMetric> metricsBySymbol,
            Map<String, String> industryBySymbol,
            Map<String, Integer> industryTotalCounts,
            MarketMetric taiex,
            MarketMetric tpex) {
        Map<String, List<MarketMetric>> grouped = metricsBySymbol.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> industryBySymbol.getOrDefault(entry.getKey(), "未分類"),
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        for (String industry : industryTotalCounts.keySet()) {
            grouped.putIfAbsent(industry, List.of());
        }

        double benchmarkReturn = List.of(taiex, tpex).stream()
                .filter(MarketMetric::hasData)
                .mapToDouble(MarketMetric::returnPercent)
                .average()
                .orElse(0.0);
        Map<String, IndustryStrength> strengths = new LinkedHashMap<>();
        for (Map.Entry<String, List<MarketMetric>> entry : grouped.entrySet()) {
            List<MarketMetric> metrics = entry.getValue().stream()
                    .filter(MarketMetric::hasData)
                    .toList();
            int totalCount = Math.max(industryTotalCounts.getOrDefault(entry.getKey(), 0), metrics.size());
            int dataCount = metrics.size();
            double coverage = totalCount > 0 ? (dataCount * 100.0) / totalCount : 0.0;
            if (metrics.isEmpty()) {
                strengths.put(entry.getKey(), new IndustryStrength(
                        entry.getKey(),
                        0.0,
                        0.0,
                        0,
                        0,
                        0.0,
                        totalCount,
                        0,
                        coverage,
                        0.0,
                        0.0,
                        0.0,
                        "SQL產業成分股"));
                continue;
            }
            double avgReturn = metrics.stream().mapToDouble(MarketMetric::returnPercent).average().orElse(0.0);
            double avgVolume = metrics.stream().mapToDouble(MarketMetric::totalVolume).average().orElse(0.0);
            int strongCount = (int) metrics.stream()
                    .filter(metric -> metric.returnPercent() > 0.0 && metric.close() > metric.vwap())
                    .count();
            double strongRatio = (double) strongCount / dataCount;
            double vwapPassRatio = metrics.stream()
                    .filter(metric -> metric.close() > metric.vwap() && metric.vwap() > 0.0)
                    .count() / (double) dataCount;
            double volumeSustainRatio = metrics.stream()
                    .filter(MarketMetric::volumeSustain)
                    .count() / (double) dataCount;
            double relativeBenchmark = avgReturn - benchmarkReturn;
            double returnComponent = clamp01(0.5 + relativeBenchmark / 4.0);
            double coverageComponent = clamp01(coverage / 100.0);
            double score = clamp01(
                    returnComponent * 0.35
                            + strongRatio * 0.25
                            + vwapPassRatio * 0.20
                            + volumeSustainRatio * 0.15
                            + coverageComponent * 0.05);
            strengths.put(entry.getKey(), new IndustryStrength(
                    entry.getKey(),
                    avgReturn,
                    avgVolume,
                    dataCount,
                    strongCount,
                    score,
                    totalCount,
                    dataCount,
                    coverage,
                    relativeBenchmark,
                    vwapPassRatio * 100.0,
                    volumeSustainRatio * 100.0,
                    "SQL產業成分股"));
        }
        return strengths;
    }

    private WeakQualification qualifyWeakMarket(
            MarketMetric stock,
            MarketMetric benchmark,
            IndustryStrength industry,
            double relativeIndex,
            double relativeIndustry) {
        if (stock == null || !stock.hasData()) {
            return new WeakQualification(false, "stock data missing");
        }
        if (benchmark == null || !benchmark.hasData()) {
            return new WeakQualification(false, "benchmark data missing");
        }
        if (industry == null || !industry.hasData()) {
            return new WeakQualification(false, "industry data missing");
        }
        if (stock.close() <= stock.vwap()) {
            return new WeakQualification(false, "close below VWAP");
        }
        if (stock.vwapSlopePercent() <= 0.0) {
            return new WeakQualification(false, "VWAP slope not positive");
        }
        if (relativeIndex < DEFAULT_WEAK_OUTPERFORM_INDEX_PERCENT) {
            return new WeakQualification(false, String.format(Locale.US,
                    "relative index %.2f%% < %.2f%%",
                    relativeIndex,
                    DEFAULT_WEAK_OUTPERFORM_INDEX_PERCENT));
        }
        if (relativeIndustry < DEFAULT_WEAK_OUTPERFORM_INDUSTRY_PERCENT) {
            return new WeakQualification(false, String.format(Locale.US,
                    "relative industry %.2f%% < %.2f%%",
                    relativeIndustry,
                    DEFAULT_WEAK_OUTPERFORM_INDUSTRY_PERCENT));
        }
        return new WeakQualification(true, "weak-market strong stock qualified");
    }

    private Map<String, String> normalizeIndustryUniverse(Map<String, String> universe) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (universe == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : universe.entrySet()) {
            String symbol = normalize(entry.getKey());
            if (!symbol.isBlank() && !isIndexSymbol(symbol)) {
                normalized.put(symbol, normalizeIndustry(entry.getValue()));
            }
        }
        return normalized;
    }

    private Map<String, Integer> normalizeIndustryTotals(Map<String, Integer> totals) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (totals == null) {
            return normalized;
        }
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            String industry = normalizeIndustry(entry.getKey());
            int count = Math.max(0, entry.getValue() != null ? entry.getValue() : 0);
            normalized.merge(industry, count, Integer::sum);
        }
        return normalized;
    }

    private String resolveBenchmarkSymbol(String symbol) {
        String normalized = normalize(symbol);
        if (normalized.endsWith(".TWO") || normalized.endsWith(".OTC")) {
            return TPEX_SYMBOL;
        }
        return TAIEX_SYMBOL;
    }

    private String normalizeIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return "未分類";
        }
        return industry.trim();
    }

    private String normalize(String symbol) {
        return symbol != null ? symbol.trim().toUpperCase(Locale.ROOT) : "";
    }

    private boolean isIndexSymbol(String symbol) {
        String normalized = normalize(symbol);
        return TAIEX_ALIASES.stream().map(this::normalize).anyMatch(normalized::equals)
                || TPEX_ALIASES.stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private String describeRegime(MarketRegime regime, MarketMetric taiex, MarketMetric tpex) {
        return String.format(Locale.US,
                "%s | TAIEX %.2f%% | TPEx %.2f%%",
                regime.getDisplayName(),
                taiex.returnPercent(),
                tpex.returnPercent());
    }

    private static double calculateVwap(List<Bar> bars, int exclusiveEnd) {
        int end = Math.max(0, Math.min(exclusiveEnd, bars.size()));
        double priceVolume = 0.0;
        double totalVolume = 0.0;
        for (int i = 0; i < end; i++) {
            Bar bar = bars.get(i);
            long volume = Math.max(0L, bar.getVolume());
            if (volume <= 0L) {
                continue;
            }
            double typical = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
            priceVolume += typical * volume;
            totalVolume += volume;
        }
        return totalVolume > 0.0 ? priceVolume / totalVolume : 0.0;
    }

    private static List<Bar> sortedBars(List<Bar> bars) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        return bars.stream()
                .filter(Objects::nonNull)
                .filter(bar -> bar.getTimestamp() != null)
                .sorted(Comparator.comparing(Bar::getTimestamp))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record IndexBars(String symbol, List<Bar> bars) {
    }

    private record WeakQualification(boolean qualified, String reason) {
    }
}
