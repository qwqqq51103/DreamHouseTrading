package com.dreamhouse.trading.core.finmind;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.util.BarAggregator;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual FinMind TaiwanStockKBar importer for filling the local collector database after market close.
 */
public class FinMindKBarSqlImporter {

    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<Timeframe, String> INTRADAY_INTERVALS = createIntradayIntervals();

    private final FinMindGateway finMindGateway;
    private final MarketDataCollectorRepository repository;

    public FinMindKBarSqlImporter(FinMindGateway finMindGateway, MarketDataCollectorRepository repository) {
        this.finMindGateway = finMindGateway;
        this.repository = repository;
    }

    private static Map<Timeframe, String> createIntradayIntervals() {
        Map<Timeframe, String> intervals = new LinkedHashMap<>();
        intervals.put(Timeframe.M1, "M1");
        intervals.put(Timeframe.M5, "M5");
        intervals.put(Timeframe.M15, "M15");
        intervals.put(Timeframe.M30, "M30");
        intervals.put(Timeframe.H1, "H1");
        return Collections.unmodifiableMap(intervals);
    }

    public ImportResult importSymbols(List<String> symbols, LocalDate date) {
        if (symbols == null || symbols.isEmpty() || date == null) {
            return new ImportResult(date, 0, 0, 0, List.of());
        }

        int successCount = 0;
        int totalBars = 0;
        List<SymbolImportResult> results = new ArrayList<>();
        for (String rawSymbol : symbols) {
            String symbol = normalizeSymbol(rawSymbol);
            if (symbol.isBlank()) {
                continue;
            }
            try {
                SymbolImportResult result = importSymbol(symbol, date);
                results.add(result);
                if (result.success()) {
                    successCount++;
                    totalBars += result.insertedBars();
                }
            } catch (Exception e) {
                results.add(SymbolImportResult.failed(symbol, e.getMessage()));
            }
        }
        return new ImportResult(date, results.size(), successCount, totalBars, List.copyOf(results));
    }

    public RangeImportResult importMissingSymbols(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        if (symbols == null || symbols.isEmpty() || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return new RangeImportResult(startDate, endDate, 0, 0, 0, 0, 0, 0, List.of());
        }

        List<String> normalizedSymbols = new ArrayList<>();
        for (String rawSymbol : symbols) {
            String symbol = normalizeSymbol(rawSymbol);
            if (!symbol.isBlank() && !normalizedSymbols.contains(symbol)) {
                normalizedSymbols.add(symbol);
            }
        }

        int daysScanned = 0;
        int apiRequests = 0;
        int successImports = 0;
        int skippedExisting = 0;
        int totalBars = 0;
        List<DateImportResult> dateResults = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            daysScanned++;
            if (isWeekend(date)) {
                dateResults.add(DateImportResult.marketClosed(date, normalizedSymbols.size()));
                continue;
            }

            List<SymbolImportResult> symbolResults = new ArrayList<>();
            for (String symbol : normalizedSymbols) {
                MarketDataCollectorRepository.SessionCandleCoverage coverage =
                        repository.findSessionCandleCoverage(symbol, "M1", date);
                if (coverage.isCompleteIntradayM1Session()) {
                    symbolResults.add(SymbolImportResult.skippedComplete(symbol, coverage.describeIntradayM1Coverage()));
                    skippedExisting++;
                    continue;
                }
                apiRequests++;
                try {
                    SymbolImportResult result = importSymbol(symbol, date);
                    symbolResults.add(result);
                    if (result.success()) {
                        successImports++;
                        totalBars += result.insertedBars();
                    }
                } catch (Exception e) {
                    symbolResults.add(SymbolImportResult.failed(symbol, e.getMessage()));
                }
            }
            dateResults.add(DateImportResult.completed(date, symbolResults));
        }

        return new RangeImportResult(
                startDate,
                endDate,
                daysScanned,
                normalizedSymbols.size(),
                apiRequests,
                successImports,
                skippedExisting,
                totalBars,
                List.copyOf(dateResults));
    }

    public SymbolImportResult importSymbol(String symbol, LocalDate date) throws SQLException {
        JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_K_BAR)
                .dataId(toFinMindStockId(symbol))
                .startDate(date)
                .build());
        List<Bar> oneMinuteBars = parseKBars(root != null ? root.path("data") : null);
        if (oneMinuteBars.isEmpty()) {
            SymbolImportResult dailyOnly = importDailyOnly(symbol, date);
            return dailyOnly.success() ? dailyOnly : SymbolImportResult.empty(symbol);
        }

        Map<String, Integer> insertedByInterval = new LinkedHashMap<>();
        int insertedTotal = 0;
        for (Map.Entry<Timeframe, String> entry : INTRADAY_INTERVALS.entrySet()) {
            List<Bar> bars = entry.getKey() == Timeframe.M1
                    ? oneMinuteBars
                    : BarAggregator.aggregate(oneMinuteBars, entry.getKey());
            int inserted = repository.replaceCandlesForDate(symbol, entry.getValue(), date, bars);
            insertedByInterval.put(entry.getValue(), inserted);
            insertedTotal += inserted;
        }
        int dailyInserted = importDailyCandle(symbol, date, oneMinuteBars);
        insertedByInterval.put("D1", dailyInserted);
        insertedTotal += dailyInserted;
        return SymbolImportResult.success(symbol, insertedTotal, insertedByInterval);
    }

    public SymbolImportResult importDailyOnly(String symbol, LocalDate date) throws SQLException {
        int inserted = importDailyCandle(symbol, date, List.of());
        if (inserted <= 0) {
            return SymbolImportResult.empty(symbol);
        }
        return SymbolImportResult.success(symbol, inserted, Map.of("D1", inserted));
    }

    private int importDailyCandle(String symbol, LocalDate date, List<Bar> intradayBars) throws SQLException {
        List<Bar> dailyBars;
        if (intradayBars != null && !intradayBars.isEmpty()) {
            dailyBars = List.of(aggregateDailyBar(date, intradayBars));
        } else {
            dailyBars = fetchDailyPriceBar(symbol, date);
        }
        if (dailyBars.isEmpty()) {
            return 0;
        }
        return repository.replaceCandlesForDate(symbol, "D1", date, dailyBars);
    }

    private List<Bar> fetchDailyPriceBar(String symbol, LocalDate date) {
        try {
            JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_PRICE)
                    .dataId(toFinMindStockId(symbol))
                    .startDate(date)
                    .endDate(date)
                    .build());
            return parseDailyPriceBars(root != null ? root.path("data") : null);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<Bar> parseDailyPriceBars(JsonNode dataArray) {
        List<Bar> bars = new ArrayList<>();
        if (dataArray == null || !dataArray.isArray()) {
            return bars;
        }
        for (JsonNode row : dataArray) {
            if (row.hasNonNull("minute")) {
                continue;
            }
            LocalDate date = parseDate(row.path("date").asText(null));
            if (date == null) {
                continue;
            }
            double open = readDouble(row, "open", "Open");
            double high = readDouble(row, "max", "high", "Max", "High");
            double low = readDouble(row, "min", "low", "Min", "Low");
            double close = readDouble(row, "close", "Close");
            long volume = Math.round(readDouble(row, "Trading_Volume", "volume", "Volume"));
            if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
                continue;
            }
            bars.add(new Bar(date.atStartOfDay(), open, high, low, close, Math.max(0L, volume)));
        }
        bars.sort(Comparator.comparing(Bar::getTimestamp));
        return bars;
    }

    private Bar aggregateDailyBar(LocalDate date, List<Bar> intradayBars) {
        List<Bar> sorted = intradayBars.stream()
                .filter(bar -> bar != null && bar.getTimestamp() != null)
                .sorted(Comparator.comparing(Bar::getTimestamp))
                .toList();
        if (sorted.isEmpty()) {
            return new Bar(date.atStartOfDay(), 0, 0, 0, 0, 0);
        }
        double open = sorted.get(0).getOpen();
        double high = sorted.stream().mapToDouble(Bar::getHigh).max().orElse(open);
        double low = sorted.stream().mapToDouble(Bar::getLow).min().orElse(open);
        double close = sorted.get(sorted.size() - 1).getClose();
        long volume = sorted.stream().mapToLong(Bar::getVolume).sum();
        return new Bar(date.atStartOfDay(), open, high, low, close, Math.max(0L, volume));
    }

    public List<Bar> parseKBars(JsonNode dataArray) {
        List<Bar> bars = new ArrayList<>();
        if (dataArray == null || !dataArray.isArray()) {
            return bars;
        }
        for (JsonNode row : dataArray) {
            LocalDate date = parseDate(row.path("date").asText(null));
            if (date == null) {
                continue;
            }
            LocalDateTime timestamp = parseMinuteTimestamp(date, row.path("minute").asText("09:00"));
            double open = readDouble(row, "open", "Open");
            double high = readDouble(row, "high", "max", "Max", "High");
            double low = readDouble(row, "low", "min", "Min", "Low");
            double close = readDouble(row, "close", "Close");
            long volume = Math.round(readDouble(row, "volume", "Trading_Volume", "Trading_Volume_K"));
            if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) {
                continue;
            }
            bars.add(new Bar(timestamp, open, high, low, close, Math.max(0L, volume)));
        }
        bars.sort(Comparator.comparing(Bar::getTimestamp));
        return bars;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseMinuteTimestamp(LocalDate date, String minuteText) {
        String safeMinute = minuteText != null && !minuteText.isBlank() ? minuteText.trim() : "09:00";
        try {
            if (safeMinute.length() == 5) {
                return LocalDateTime.parse(date + " " + safeMinute + ":00", MINUTE_FORMAT);
            }
            if (safeMinute.length() >= 8) {
                return LocalDateTime.parse(date + " " + safeMinute.substring(0, 8), MINUTE_FORMAT);
            }
        } catch (Exception ignored) {
            return date.atTime(9, 0);
        }
        return date.atTime(9, 0);
    }

    private double readDouble(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            try {
                return Double.parseDouble(value.asText().trim());
            } catch (Exception ignored) {
                // Try the next candidate column.
            }
        }
        return 0.0;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private String toFinMindStockId(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.endsWith(".TW") || normalized.endsWith(".TWO")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }
        return normalized;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public record ImportResult(
            LocalDate date,
            int requestedSymbols,
            int successSymbols,
            int totalInsertedBars,
            List<SymbolImportResult> symbolResults) {
    }

    public record RangeImportResult(
            LocalDate startDate,
            LocalDate endDate,
            int daysScanned,
            int requestedSymbols,
            int apiRequests,
            int successImports,
            int skippedExisting,
            int totalInsertedBars,
            List<DateImportResult> dateResults) {
    }

    public record DateImportResult(
            LocalDate date,
            boolean marketClosed,
            int marketClosedSkippedSymbols,
            List<SymbolImportResult> symbolResults) {

        static DateImportResult marketClosed(LocalDate date, int skippedSymbols) {
            return new DateImportResult(date, true, Math.max(0, skippedSymbols), List.of());
        }

        static DateImportResult completed(LocalDate date, List<SymbolImportResult> symbolResults) {
            return new DateImportResult(date, false, 0, List.copyOf(symbolResults));
        }
    }

    public record SymbolImportResult(
            String symbol,
            boolean success,
            boolean skippedExisting,
            int insertedBars,
            Map<String, Integer> insertedByInterval,
            String message) {

        static SymbolImportResult success(String symbol, int insertedBars, Map<String, Integer> insertedByInterval) {
            return new SymbolImportResult(symbol, true, false, insertedBars, Map.copyOf(insertedByInterval), "");
        }

        static SymbolImportResult skippedComplete(String symbol, String coverage) {
            return new SymbolImportResult(symbol, false, true, 0, Map.of(), "SQL 分K完整，略過 API（" + coverage + "）");
        }

        static SymbolImportResult empty(String symbol) {
            return new SymbolImportResult(symbol, false, false, 0, Map.of(), "FinMind 回傳 0 筆分 K");
        }

        static SymbolImportResult failed(String symbol, String message) {
            return new SymbolImportResult(symbol, false, false, 0, Map.of(), message != null ? message : "匯入失敗");
        }
    }
}
