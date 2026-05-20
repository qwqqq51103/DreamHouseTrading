package com.dreamhouse.trading.core.stockpool;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.StockNameResolver;
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.core.finmind.FinMindGateway;
import com.dreamhouse.trading.core.finmind.FinMindRequest;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a next-session day-trading candidate pool from low-frequency after-hours data.
 * This service is intentionally not used by intraday auto-entry decisions.
 */
public class AfterHoursStockPoolService {
    private static final String BROKER_FETCH_ERROR_KEY = "__BROKER_FETCH_ERROR__";

    private final FinMindGateway finMindGateway;
    private final MarketDataCollectorRepository repository;

    public AfterHoursStockPoolService(FinMindGateway finMindGateway, MarketDataCollectorRepository repository) {
        this.finMindGateway = finMindGateway;
        this.repository = repository;
    }

    public StockPoolResult buildPool(List<String> symbols, LocalDate date, int topN, boolean includeBrokerChips) {
        LocalDate queryDate = date != null ? date : LocalDate.now().minusDays(1);
        List<String> normalizedSymbols = symbols == null
                ? List.of()
                : symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(this::normalizeSymbol)
                .distinct()
                .toList();
        List<StockPoolCandidate> candidates = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, BrokerMetrics> brokerMetricsByStock = includeBrokerChips
                ? fetchBrokerMetricsForSymbols(normalizedSymbols, queryDate)
                : Map.of();
        String brokerDataSummary = includeBrokerChips
                ? buildBrokerDataSummary(normalizedSymbols, brokerMetricsByStock)
                : "分點資料：未啟用";

        for (String symbol : normalizedSymbols) {
            try {
                StockPoolCandidate candidate = evaluateSymbol(symbol, queryDate, includeBrokerChips, brokerMetricsByStock);
                if (candidate != null) {
                    candidates.add(candidate);
                } else {
                    skipped.add(symbol + "：日線資料不足");
                }
            } catch (Exception e) {
                skipped.add(symbol + "：" + e.getMessage());
            }
        }

        candidates.sort(Comparator.comparingDouble(StockPoolCandidate::totalScore).reversed()
                .thenComparing(StockPoolCandidate::symbol));
        int limit = Math.max(1, topN);
        List<StockPoolCandidate> selected = candidates.stream().limit(limit).toList();
        return new StockPoolResult(queryDate, candidates, selected, skipped, brokerDataSummary);
    }

    private String buildBrokerDataSummary(List<String> symbols, Map<String, BrokerMetrics> brokerMetricsByStock) {
        int requestedCount = symbols == null ? 0 : symbols.size();
        if (requestedCount == 0) {
            return "分點資料：觀察清單無股票；最大分點買超/賣超以張顯示（1張=1000股）";
        }
        long availableCount = symbols.stream()
                .map(this::stripTaiwanSuffix)
                .filter(stockId -> brokerMetricsByStock.containsKey(stockId))
                .count();
        int missingCount = Math.max(0, requestedCount - (int) availableCount);
        BrokerMetrics fetchError = brokerMetricsByStock.get(BROKER_FETCH_ERROR_KEY);
        String errorSuffix = fetchError != null && !fetchError.riskFlag().isBlank()
                ? "；" + fetchError.riskFlag()
                : "";
        return String.format(Locale.US,
                "分點資料：%d/%d 檔有資料，%d 檔無資料；最大分點買超/賣超以張顯示（1張=1000股）%s",
                availableCount,
                requestedCount,
                missingCount,
                errorSuffix);
    }

    private StockPoolCandidate evaluateSymbol(
            String symbol,
            LocalDate date,
            boolean includeBrokerChips,
            Map<String, BrokerMetrics> brokerMetricsByStock) throws SQLException {
        String stockId = stripTaiwanSuffix(symbol);
        JsonNode priceRoot = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_PRICE)
                .dataId(stockId)
                .startDate(date.minusDays(45))
                .endDate(date)
                .build());
        JsonNode priceData = priceRoot.path("data");
        repository.writeFinMindDatasetRows(FinMindDataset.TAIWAN_STOCK_PRICE, stockId, date.minusDays(45), date, priceData);
        DailyMetrics daily = calculateDailyMetrics(priceData, date);
        if (daily == null || daily.close <= 0.0) {
            return null;
        }

        Optional<String> dataWarning = validatePriceCoverage(priceData, date);
        double dayTradingScore = safeFetchDayTradingScore(stockId, date);
        BrokerMetrics broker = includeBrokerChips
                ? brokerMetricsByStock.getOrDefault(
                        stockId,
                        brokerMetricsByStock.getOrDefault(BROKER_FETCH_ERROR_KEY, BrokerMetrics.empty()))
                : BrokerMetrics.empty();
        double total = daily.trendScore
                + daily.volumeScore
                + daily.patternScore
                + dayTradingScore
                + broker.score
                - daily.riskPenalty
                - broker.riskPenalty;
        String reason = String.join("；", buildReasons(daily, dayTradingScore, broker, includeBrokerChips, dataWarning));

        return new StockPoolCandidate(
                symbol,
                StockNameResolver.resolveChineseName(symbol),
                round(total),
                round(daily.trendScore),
                round(daily.volumeScore),
                round(daily.patternScore),
                round(dayTradingScore),
                round(broker.score),
                round(daily.riskPenalty + broker.riskPenalty),
                round(broker.netBuy),
                broker.topBuyer,
                round(broker.topSellerNetSell),
                broker.topSeller,
                broker.brokerCount,
                broker.rowCount,
                round(broker.concentration),
                broker.riskFlag,
                reason);
    }

    private double safeFetchDayTradingScore(String stockId, LocalDate date) {
        try {
            return fetchDayTradingScore(stockId, date);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double fetchDayTradingScore(String stockId, LocalDate date) throws SQLException {
        JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_DAY_TRADING)
                .dataId(stockId)
                .startDate(date.minusDays(20))
                .endDate(date)
                .build());
        JsonNode data = root.path("data");
        repository.writeFinMindDatasetRows(FinMindDataset.TAIWAN_STOCK_DAY_TRADING, stockId, date.minusDays(20), date, data);
        if (!data.isArray() || data.isEmpty()) {
            return 0.0;
        }
        JsonNode latest = latestByDate(data, date);
        if (latest == null) {
            return 0.0;
        }
        double amount = firstDouble(latest,
                "buy_after_sell", "BuyAfterSale", "Trading_Volume", "volume", "trade_volume");
        if (amount <= 0.0) {
            return 0.0;
        }
        return Math.min(10.0, Math.log10(amount + 1.0) * 1.5);
    }

    private Map<String, BrokerMetrics> fetchBrokerMetricsForSymbols(List<String> symbols, LocalDate date) {
        if (useBulkBrokerDailyReport()) {
            return fetchBulkBrokerMetricsForSymbols(symbols, date);
        }
        Map<String, BrokerAccumulator> byStock = new LinkedHashMap<>();
        String firstError = "";
        int successCount = 0;
        for (String symbol : symbols) {
            String requestedStockId = stripTaiwanSuffix(symbol);
            try {
                JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT)
                        .dataId(requestedStockId)
                        .startDate(date)
                        .build());
                JsonNode data = root.path("data");
                if (!data.isArray()) {
                    String message = "API 回傳缺少 data array: status="
                            + root.path("status").asText("")
                            + ", msg=" + root.path("msg").asText(root.path("message").asText(""));
                    if (firstError.isBlank()) {
                        firstError = message;
                    }
                    continue;
                }
                repository.writeFinMindDatasetRows(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT, requestedStockId, date, date, data);
                if (data.isEmpty()) {
                    continue;
                }
                successCount++;
                for (JsonNode row : data) {
                    String stockId = normalizeStockId(readText(row, "stock_id", "StockId", "data_id"));
                    if (stockId.isBlank()) {
                        stockId = requestedStockId;
                    }
                    String brokerId = readText(row, "securities_trader_id", "SecuritiesTraderId", "broker_id");
                    String brokerName = readText(row, "securities_trader", "SecuritiesTrader", "broker_name");
                    String brokerKey = brokerDisplayKey(brokerId, brokerName);
                    double buy = firstDouble(row, "buy", "Buy", "buy_volume", "buy_qty");
                    double sell = firstDouble(row, "sell", "Sell", "sell_volume", "sell_qty");
                    BrokerAccumulator accumulator = byStock.computeIfAbsent(stockId, ignored -> new BrokerAccumulator());
                    accumulator.add(brokerKey, Math.max(0.0, buy), Math.max(0.0, sell));
                }
            } catch (Exception e) {
                if (firstError.isBlank()) {
                    firstError = conciseMessage(e);
                }
            }
        }

        if (firstError.isBlank()) {
            firstError = "指定日期無分點資料，且最近交易日快取也沒有觀察清單股票";
        }
        Map<String, BrokerMetrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, BrokerAccumulator> entry : byStock.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMetrics());
        }
        if (successCount == 0 && !symbols.isEmpty()) {
            String message = firstError.isBlank() ? "全部觀察清單分點查詢無資料" : firstError;
            result.put(BROKER_FETCH_ERROR_KEY, BrokerMetrics.unavailable("分點資料失敗: " + message));
        }
        return result;
    }

    private boolean useBulkBrokerDailyReport() {
        return true;
    }

    private Map<String, BrokerMetrics> fetchBulkBrokerMetricsForSymbols(List<String> symbols, LocalDate date) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        Set<String> requestedStockIds = symbols.stream()
                .map(this::stripTaiwanSuffix)
                .filter(stockId -> stockId != null && !stockId.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String firstError = "";
        Map<String, BrokerMetrics> exactCachedMetrics = Map.of();
        try {
            List<JsonNode> exactCachedRows = repository.readFinMindDatasetRows(
                    FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT,
                    date);
            exactCachedMetrics = aggregateBrokerMetrics(exactCachedRows, requestedStockIds);
            if (exactCachedMetrics.keySet().containsAll(requestedStockIds)) {
                return exactCachedMetrics;
            }
        } catch (Exception ignored) {
            // Exact-date cache miss or older schema; continue with FinMind fetch.
        }

        Set<String> missingStockIds = new LinkedHashSet<>(requestedStockIds);
        missingStockIds.removeAll(exactCachedMetrics.keySet());
        FetchBrokerRowsResult apiResult = fetchBrokerRowsByStockId(missingStockIds, date);
        if (!apiResult.error().isBlank()) {
            firstError = apiResult.error();
        }

        Map<String, BrokerMetrics> combinedMetrics = new LinkedHashMap<>(exactCachedMetrics);
        combinedMetrics.putAll(aggregateBrokerMetrics(apiResult.rows(), missingStockIds));
        if (combinedMetrics.keySet().containsAll(requestedStockIds)) {
            return combinedMetrics;
        }

        try {
            Set<String> unresolvedStockIds = new LinkedHashSet<>(requestedStockIds);
            unresolvedStockIds.removeAll(combinedMetrics.keySet());
            List<JsonNode> fallbackRows = repository.readLatestFinMindDatasetRowsOnOrBefore(
                    FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT,
                    date.minusDays(1),
                    10,
                    unresolvedStockIds);
            combinedMetrics.putAll(aggregateBrokerMetrics(fallbackRows, unresolvedStockIds));
            if (hasAnyRequestedBrokerMetric(combinedMetrics, requestedStockIds)) {
                return combinedMetrics;
            }
        } catch (Exception ignored) {
            // Keep the original API/cache error below.
        }

        if (firstError.isBlank()) {
            firstError = "指定日期無分點資料，且最近交易日快取也沒有觀察清單股票";
        }
        Map<String, BrokerMetrics> result = new LinkedHashMap<>();
        result.put(BROKER_FETCH_ERROR_KEY, BrokerMetrics.unavailable("分點資料失敗: " + firstError));
        return result;
    }

    private FetchBrokerRowsResult fetchBrokerRowsByStockId(Set<String> stockIds, LocalDate date) {
        if (stockIds == null || stockIds.isEmpty()) {
            return new FetchBrokerRowsResult(List.of(), "");
        }
        List<JsonNode> rows = new ArrayList<>();
        String firstError = "";
        for (String stockId : stockIds) {
            try {
                JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT)
                        .dataId(stockId)
                        .startDate(date)
                        .build());
                JsonNode data = root.path("data");
                if (!data.isArray()) {
                    if (firstError.isBlank()) {
                        firstError = stockId + ": API did not return data array: status="
                                + root.path("status").asText("")
                                + ", msg=" + root.path("msg").asText(root.path("message").asText(""));
                    }
                    continue;
                }
                repository.writeFinMindDatasetRows(FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT, stockId, date, date, data);
                data.forEach(rows::add);
            } catch (Exception e) {
                if (firstError.isBlank()) {
                    firstError = stockId + ": " + conciseMessage(e);
                }
            }
        }
        return new FetchBrokerRowsResult(rows, firstError);
    }

    private Map<String, BrokerMetrics> aggregateBrokerMetrics(Iterable<JsonNode> rows, Set<String> requestedStockIds) {
        Map<String, BrokerAccumulator> byStock = new LinkedHashMap<>();
        if (rows == null || requestedStockIds == null || requestedStockIds.isEmpty()) {
            return Map.of();
        }
        for (JsonNode row : rows) {
            if (row == null || row.isNull()) {
                continue;
            }
            String stockId = normalizeStockId(readText(row, "stock_id", "StockId", "data_id"));
            if (stockId.isBlank() || !requestedStockIds.contains(stockId)) {
                continue;
            }
            String brokerId = readText(row, "securities_trader_id", "SecuritiesTraderId", "broker_id");
            String brokerName = readText(row, "securities_trader", "SecuritiesTrader", "broker_name");
            String brokerKey = brokerDisplayKey(brokerId, brokerName);
            double buy = firstDouble(row, "buy", "Buy", "buy_volume", "buy_qty");
            double sell = firstDouble(row, "sell", "Sell", "sell_volume", "sell_qty");
            BrokerAccumulator accumulator = byStock.computeIfAbsent(stockId, ignored -> new BrokerAccumulator());
            accumulator.add(brokerKey, Math.max(0.0, buy), Math.max(0.0, sell));
        }
        Map<String, BrokerMetrics> result = new LinkedHashMap<>();
        for (Map.Entry<String, BrokerAccumulator> entry : byStock.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMetrics());
        }
        return result;
    }

    private boolean hasAnyRequestedBrokerMetric(Map<String, BrokerMetrics> metrics, Set<String> requestedStockIds) {
        if (metrics == null || metrics.isEmpty() || requestedStockIds == null || requestedStockIds.isEmpty()) {
            return false;
        }
        return requestedStockIds.stream().anyMatch(metrics::containsKey);
    }

    private record FetchBrokerRowsResult(List<JsonNode> rows, String error) {
    }

    private DailyMetrics calculateDailyMetrics(JsonNode data, LocalDate date) {
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }
        List<JsonNode> rows = new ArrayList<>();
        data.forEach(rows::add);
        rows.sort(Comparator.comparing(row -> readDate(row, LocalDate.MIN)));
        JsonNode latest = latestByDate(data, date);
        if (latest == null) {
            latest = rows.get(rows.size() - 1);
        }
        int latestIndex = rows.indexOf(latest);
        if (latestIndex < 0) {
            latestIndex = rows.size() - 1;
        }
        double close = firstDouble(latest, "close", "Close", "closing_price");
        double open = firstDouble(latest, "open", "Open", "opening_price");
        double high = firstDouble(latest, "max", "Max", "high", "High", "highest_price");
        double low = firstDouble(latest, "min", "Min", "low", "Low", "lowest_price");
        double volume = firstDouble(latest,
                "Trading_Volume", "trading_volume", "volume", "trade_volume",
                "Trading_turnover", "turnover");
        if (close <= 0.0) {
            return null;
        }
        double ma5 = averageClose(rows, Math.max(0, latestIndex - 4), latestIndex);
        double ma10 = averageClose(rows, Math.max(0, latestIndex - 9), latestIndex);
        double ma20 = averageClose(rows, Math.max(0, latestIndex - 19), latestIndex);
        double avgVolume5 = averageVolume(rows, Math.max(0, latestIndex - 5), Math.max(0, latestIndex - 1));
        double avgVolume20 = averageVolume(rows, Math.max(0, latestIndex - 20), Math.max(0, latestIndex - 1));
        double volumeRatio5 = avgVolume5 > 0.0 ? volume / avgVolume5 : 0.0;
        double volumeRatio20 = avgVolume20 > 0.0 ? volume / avgVolume20 : 0.0;
        double changePct = open > 0.0 ? (close - open) / open * 100.0 : 0.0;
        double range = Math.max(0.0, high - low);
        double upperShadowRatio = range > 0.0 ? Math.max(0.0, high - Math.max(open, close)) / range : 0.0;

        double trendScore = 0.0;
        if (ma5 > 0.0 && close > ma5) trendScore += 6.0;
        if (ma10 > 0.0 && close > ma10) trendScore += 5.0;
        if (ma20 > 0.0 && close > ma20) trendScore += 4.0;
        if (ma5 > ma10 && ma10 > ma20 && ma20 > 0.0) trendScore += 5.0;

        double volumeScore = Math.min(20.0, Math.max(volumeRatio5, volumeRatio20) * 6.0);
        double patternScore = 0.0;
        if (changePct > 0.0) patternScore += Math.min(8.0, changePct * 2.0);
        if (close >= high * 0.97) patternScore += 4.0;
        if (close > open && volumeRatio5 >= 1.2) patternScore += 5.0;

        double riskPenalty = 0.0;
        if (upperShadowRatio > 0.45) riskPenalty += 8.0;
        if (changePct > 7.0) riskPenalty += 5.0;
        if (volumeRatio5 > 4.0 && close < open) riskPenalty += 10.0;
        if (volume < 500_000) riskPenalty += 6.0;

        return new DailyMetrics(close, volume, trendScore, volumeScore, patternScore, riskPenalty, volumeRatio5, changePct, upperShadowRatio);
    }

    private Optional<String> validatePriceCoverage(JsonNode data, LocalDate date) {
        JsonNode latest = latestByDate(data, date);
        if (latest == null) {
            return Optional.of("日線查無指定日期前資料");
        }
        LocalDate latestDate = readDate(latest, null);
        if (latestDate != null && latestDate.isBefore(date)) {
            return Optional.of("使用最近日線 " + latestDate);
        }
        return Optional.empty();
    }

    private List<String> buildReasons(
            DailyMetrics daily,
            double dayTradingScore,
            BrokerMetrics broker,
            boolean includeBrokerChips,
            Optional<String> dataWarning) {
        List<String> reasons = new ArrayList<>();
        dataWarning.ifPresent(reasons::add);
        reasons.add(String.format(Locale.US, "日線趨勢 %.1f", daily.trendScore));
        reasons.add(String.format(Locale.US, "量能分 %.1f，5日量比 %.2f", daily.volumeScore, daily.volumeRatio5));
        reasons.add(String.format(Locale.US, "型態分 %.1f，日內漲跌 %.2f%%", daily.patternScore, daily.changePct));
        if (dayTradingScore > 0.0) {
            reasons.add(String.format(Locale.US, "當沖活躍 %.1f", dayTradingScore));
        }
        if (includeBrokerChips) {
            reasons.add(String.format(Locale.US, "分點籌碼 %.1f，買賣超 %.2f 張，集中度 %.2f",
                    broker.score, sharesToLots(broker.netBuy), broker.concentration));
            if (!broker.riskFlag.isBlank()) {
                reasons.add(broker.riskFlag);
            }
        }
        if (daily.riskPenalty > 0.0 || broker.riskPenalty > 0.0) {
            reasons.add(String.format(Locale.US, "風險扣分 %.1f", daily.riskPenalty + broker.riskPenalty));
        }
        return reasons;
    }

    private JsonNode latestByDate(JsonNode data, LocalDate date) {
        JsonNode latest = null;
        LocalDate latestDate = LocalDate.MIN;
        for (JsonNode row : data) {
            LocalDate rowDate = readDate(row, null);
            if (rowDate == null || rowDate.isAfter(date)) {
                continue;
            }
            if (rowDate.isAfter(latestDate)) {
                latest = row;
                latestDate = rowDate;
            }
        }
        return latest;
    }

    private double averageClose(List<JsonNode> rows, int start, int end) {
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= end && i < rows.size(); i++) {
            double value = firstDouble(rows.get(i), "close", "Close");
            if (value > 0.0) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private double averageVolume(List<JsonNode> rows, int start, int end) {
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= end && i < rows.size(); i++) {
            double value = firstDouble(rows.get(i), "Trading_Volume", "volume", "Trading_turnover");
            if (value > 0.0) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private LocalDate readDate(JsonNode row, LocalDate fallback) {
        String value = readText(row, "date", "Date", "row_date");
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private double firstDouble(JsonNode row, String... fields) {
        for (String field : fields) {
            JsonNode value = fieldValue(row, field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            try {
                return Double.parseDouble(value.asText().replace(",", "").trim());
            } catch (RuntimeException ignored) {
            }
        }
        return 0.0;
    }

    private String readText(JsonNode row, String... fields) {
        for (String field : fields) {
            JsonNode value = fieldValue(row, field);
            if (value != null && !value.isNull()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private JsonNode fieldValue(JsonNode row, String field) {
        if (row == null || field == null) {
            return null;
        }
        JsonNode direct = row.get(field);
        if (direct != null) {
            return direct;
        }
        String normalizedTarget = normalizeFieldName(field);
        for (Map.Entry<String, JsonNode> entry : iterable(row.fields())) {
            if (normalizeFieldName(entry.getKey()).equals(normalizedTarget)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Iterable<Map.Entry<String, JsonNode>> iterable(java.util.Iterator<Map.Entry<String, JsonNode>> iterator) {
        return () -> iterator;
    }

    private String normalizeFieldName(String field) {
        return field.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String conciseMessage(Exception e) {
        String message = e.getMessage();
        if ((message == null || message.isBlank()) && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private String normalizeSymbol(String symbol) {
        String value = symbol.trim().toUpperCase(Locale.ROOT);
        if (value.matches("\\d{4}")) {
            return value + ".TW";
        }
        return value;
    }

    private String stripTaiwanSuffix(String symbol) {
        return normalizeStockId(symbol);
    }

    private String normalizeStockId(String symbol) {
        return StockNameResolver.normalize(symbol)
                .replace(".TW", "")
                .replace(".TWO", "");
    }

    private String brokerDisplayKey(String brokerId, String brokerName) {
        String id = brokerId == null ? "" : brokerId.trim();
        String name = brokerName == null ? "" : brokerName.trim();
        if (!name.isBlank() && !id.isBlank()) {
            return name + "(" + id + ")";
        }
        if (!name.isBlank()) {
            return name;
        }
        return id;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double sharesToLots(double shares) {
        return round(shares / 1000.0);
    }

    private record DailyMetrics(
            double close,
            double volume,
            double trendScore,
            double volumeScore,
            double patternScore,
            double riskPenalty,
            double volumeRatio5,
            double changePct,
            double upperShadowRatio) {
    }

    private record BrokerMetrics(
            double netBuy,
            String topBuyer,
            double topSellerNetSell,
            String topSeller,
            int brokerCount,
            int rowCount,
            double concentration,
            double score,
            double riskPenalty,
            String riskFlag) {
        static BrokerMetrics empty() {
            return new BrokerMetrics(0.0, "", 0.0, "", 0, 0, 0.0, 0.0, 0.0, "");
        }

        static BrokerMetrics unavailable(String reason) {
            return new BrokerMetrics(0.0, "", 0.0, "", 0, 0, 0.0, 0.0, 0.0, reason);
        }

        BrokerMetrics(double netBuy, double concentration, double score, double riskPenalty, String riskFlag) {
            this(netBuy, "", 0.0, "", 0, 0, concentration, score, riskPenalty, riskFlag);
        }
    }

    private static class BrokerAccumulator {
        private double totalBuy;
        private double totalSell;
        private int rowCount;
        private final Map<String, Double> netByBroker = new LinkedHashMap<>();

        void add(String brokerKey, double buy, double sell) {
            rowCount++;
            totalBuy += buy;
            totalSell += sell;
            String key = brokerKey == null || brokerKey.isBlank() ? "UNKNOWN" : brokerKey;
            netByBroker.merge(key, buy - sell, Double::sum);
        }

        BrokerMetrics toMetrics() {
            {
            Map.Entry<String, Double> topBuyerEntry = netByBroker.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            Map.Entry<String, Double> topSellerEntry = netByBroker.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);
            double topBuyerNetBuy = topBuyerEntry != null ? Math.max(0.0, topBuyerEntry.getValue()) : 0.0;
            double topSellerNetSell = topSellerEntry != null ? Math.max(0.0, -topSellerEntry.getValue()) : 0.0;
            String topBuyer = topBuyerNetBuy > 0.0 && topBuyerEntry != null ? topBuyerEntry.getKey() : "";
            String topSeller = topSellerNetSell > 0.0 && topSellerEntry != null ? topSellerEntry.getKey() : "";
            double positiveNetBuy = netByBroker.values().stream()
                    .filter(value -> value > 0.0)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            double top3Positive = netByBroker.values().stream()
                    .filter(value -> value > 0.0)
                    .sorted(Comparator.reverseOrder())
                    .limit(3)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            double concentration = positiveNetBuy > 0.0 ? top3Positive / positiveNetBuy : 0.0;
            double volumeBase = Math.max(1.0, totalBuy + totalSell);
            double buyPressureRatio = positiveNetBuy / volumeBase;
            double positiveScore = Math.max(0.0, Math.min(10.0, buyPressureRatio * 30.0));
            double concentrationScore = topBuyerNetBuy > 0.0 ? Math.min(5.0, concentration * 5.0) : 0.0;
            double score = positiveScore + concentrationScore;
            double sellDominanceRatio = Math.max(0.0, topSellerNetSell - topBuyerNetBuy) / volumeBase;
            double penalty = Math.min(12.0, sellDominanceRatio * 40.0);
            String dominantRiskFlag = topSellerNetSell > topBuyerNetBuy && concentration >= 0.35
                    ? "最大分點賣超大於買超：" + topSeller
                    : "";
            if (!netByBroker.isEmpty() || netByBroker.isEmpty()) {
                return new BrokerMetrics(
                        topBuyerNetBuy,
                        topBuyer,
                        topSellerNetSell,
                        topSeller,
                        netByBroker.size(),
                        rowCount,
                        concentration,
                        score,
                        penalty,
                        dominantRiskFlag);
            }
            }
            double netBuy = totalBuy - totalSell;
            double totalAbs = netByBroker.values().stream().mapToDouble(Math::abs).sum();
            double top3Abs = netByBroker.values().stream()
                    .sorted(Comparator.comparingDouble((Double value) -> Math.abs(value)).reversed())
                    .limit(3)
                    .mapToDouble(Math::abs)
                    .sum();
            double concentration = totalAbs > 0.0 ? top3Abs / totalAbs : 0.0;
            double volumeBase = Math.max(1.0, totalBuy + totalSell);
            double netRatio = netBuy / volumeBase;
            double positiveScore = Math.max(0.0, Math.min(10.0, netRatio * 30.0));
            double concentrationScore = netBuy > 0.0 ? Math.min(5.0, concentration * 5.0) : 0.0;
            double score = positiveScore + concentrationScore;
            double penalty = netBuy < 0.0 ? Math.min(12.0, Math.abs(netRatio) * 40.0) : 0.0;
            String riskFlag = netBuy < 0.0 && concentration >= 0.35
                    ? "分點集中賣超"
                    : "";
            return new BrokerMetrics(netBuy, concentration, score, penalty, riskFlag);
        }
    }

    public record StockPoolCandidate(
            String symbol,
            String chineseName,
            double totalScore,
            double trendScore,
            double volumeScore,
            double patternScore,
            double dayTradingScore,
            double brokerChipScore,
            double riskPenalty,
            double brokerNetBuy,
            String brokerTopBuyer,
            double brokerTopSellerNetSell,
            String brokerTopSeller,
            int brokerBranchCount,
            int brokerRowCount,
            double brokerConcentration,
            String brokerRiskFlag,
            String reason) {
        public double brokerNetBuyLots() {
            return Math.round(brokerNetBuy / 10.0) / 100.0;
        }

        public double brokerTopSellerNetSellLots() {
            return Math.round(brokerTopSellerNetSell / 10.0) / 100.0;
        }
    }

    public record StockPoolResult(
            LocalDate date,
            List<StockPoolCandidate> allCandidates,
            List<StockPoolCandidate> selectedCandidates,
            List<String> skipped,
            String brokerDataSummary) {
    }
}
