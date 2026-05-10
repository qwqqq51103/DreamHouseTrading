package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 市場資料正規化工具，統一監控與回測使用的 K 線輸入格式。
 */
public final class MarketDataNormalizer {

    private MarketDataNormalizer() {
    }

    public static List<Bar> normalizeBars(List<Bar> bars, int limit) {
        if (bars == null || bars.isEmpty()) {
            return new ArrayList<>();
        }

        Map<java.time.LocalDateTime, Bar> deduped = new LinkedHashMap<>();
        bars.stream()
            .filter(MarketDataNormalizer::isValidBar)
            .sorted(Comparator.comparing(Bar::getTimestamp))
            .forEach(bar -> deduped.put(bar.getTimestamp(), bar));

        List<Bar> normalized = new ArrayList<>(deduped.values());
        if (limit > 0 && normalized.size() > limit) {
            return new ArrayList<>(normalized.subList(normalized.size() - limit, normalized.size()));
        }
        return normalized;
    }

    public static boolean isValidBar(Bar bar) {
        if (bar == null || bar.getTimestamp() == null) {
            return false;
        }
        if (bar.getOpen() <= 0 || bar.getHigh() <= 0 || bar.getLow() <= 0 || bar.getClose() <= 0) {
            return false;
        }
        return bar.getHigh() >= Math.max(bar.getOpen(), bar.getClose())
            && bar.getLow() <= Math.min(bar.getOpen(), bar.getClose())
            && bar.getVolume() >= 0;
    }
}
