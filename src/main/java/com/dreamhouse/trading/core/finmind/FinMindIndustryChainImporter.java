package com.dreamhouse.trading.core.finmind;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Low-frequency importer for FinMind TaiwanStockIndustryChain.
 * Intraday scanner logic reads the local SQL cache only.
 */
public class FinMindIndustryChainImporter {

    private final FinMindGateway finMindGateway;
    private final MarketDataCollectorRepository repository;

    public FinMindIndustryChainImporter(FinMindGateway finMindGateway, MarketDataCollectorRepository repository) {
        this.finMindGateway = finMindGateway;
        this.repository = repository;
    }

    public ImportResult importIndustryChain() throws SQLException {
        JsonNode root = finMindGateway.queryData(FinMindRequest.dataset(FinMindDataset.TAIWAN_STOCK_INDUSTRY_CHAIN)
                .build());
        List<MarketDataCollectorRepository.IndustryInfo> rows = parse(root != null ? root.path("data") : null);
        int inserted = repository.replaceIndustryChain(rows);
        return new ImportResult(rows.size(), inserted);
    }

    public List<MarketDataCollectorRepository.IndustryInfo> parse(JsonNode dataArray) {
        Map<String, MarketDataCollectorRepository.IndustryInfo> rowsByStockId = new LinkedHashMap<>();
        if (dataArray == null || !dataArray.isArray()) {
            return List.of();
        }
        for (JsonNode row : dataArray) {
            String stockId = readText(row, "stock_id", "stockId", "code", "security_id");
            if (stockId == null || stockId.isBlank()) {
                continue;
            }
            String industry = readText(row,
                    "industry",
                    "industry_category",
                    "industry_name",
                    "industry_group",
                    "industry_category_zh",
                    "category");
            String subIndustry = readText(row,
                    "sub_industry",
                    "subindustry",
                    "sub_industry_name",
                    "sub_category",
                    "chain_sub_name");
            String chainName = readText(row,
                    "chain_name",
                    "industry_chain",
                    "industry_chain_name",
                    "name");
            String stockName = readText(row,
                    "stock_name",
                    "stockName",
                    "security_name");
            LocalDate date = parseDate(readText(row, "date", "source_date", "update_date"));
            String normalizedStockId = normalizeStockId(stockId);
            MarketDataCollectorRepository.IndustryInfo incoming = new MarketDataCollectorRepository.IndustryInfo(
                    normalizedStockId,
                    stockName,
                    industry,
                    subIndustry,
                    chainName,
                    date);
            MarketDataCollectorRepository.IndustryInfo existing = rowsByStockId.get(normalizedStockId);
            rowsByStockId.put(normalizedStockId, existing == null ? incoming : merge(existing, incoming));
        }
        return new ArrayList<>(rowsByStockId.values());
    }

    private MarketDataCollectorRepository.IndustryInfo merge(
            MarketDataCollectorRepository.IndustryInfo left,
            MarketDataCollectorRepository.IndustryInfo right) {
        return new MarketDataCollectorRepository.IndustryInfo(
                left.stockId(),
                mergeText(left.stockName(), right.stockName()),
                mergeText(left.industry(), right.industry()),
                mergeText(left.subIndustry(), right.subIndustry()),
                mergeText(left.chainName(), right.chainName()),
                latestDate(left.sourceDate(), right.sourceDate()));
    }

    private String mergeText(String left, String right) {
        Set<String> values = new LinkedHashSet<>();
        addTextPart(values, left);
        addTextPart(values, right);
        if (values.isEmpty()) {
            return "";
        }
        String merged = String.join(" / ", values);
        return merged.length() <= 128 ? merged : merged.substring(0, 128);
    }

    private void addTextPart(Set<String> values, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split("/")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
    }

    private LocalDate latestDate(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return right.isAfter(left) ? right : left;
    }

    private String readText(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText().trim();
            }
        }
        return "";
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

    private String normalizeStockId(String value) {
        String normalized = value != null ? value.trim().toUpperCase() : "";
        int dot = normalized.indexOf('.');
        return dot >= 0 ? normalized.substring(0, dot) : normalized;
    }

    public record ImportResult(int parsedRows, int insertedRows) {
    }
}
