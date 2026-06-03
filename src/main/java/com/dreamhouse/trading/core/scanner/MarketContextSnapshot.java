package com.dreamhouse.trading.core.scanner;

import com.dreamhouse.trading.core.model.Bar;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record MarketContextSnapshot(
        MarketRegime regime,
        String status,
        MarketMetric taiex,
        MarketMetric tpex,
        Map<String, SymbolMarketContext> symbols,
        Map<String, IndustryStrength> industries,
        Map<String, List<Bar>> preloadedBars,
        LocalDateTime createdAt) {

    public static MarketContextSnapshot empty(String status) {
        return new MarketContextSnapshot(
                MarketRegime.DATA_MISSING,
                status != null ? status : "市場資料不足",
                MarketMetric.empty("TAIEX"),
                MarketMetric.empty("TPEx"),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                LocalDateTime.now());
    }

    public SymbolMarketContext symbolContext(String symbol) {
        if (symbol == null || symbols == null) {
            return null;
        }
        SymbolMarketContext context = symbols.get(symbol);
        if (context != null) {
            return context;
        }
        return symbols.get(normalize(symbol));
    }

    public List<Bar> barsFor(String symbol) {
        if (symbol == null || preloadedBars == null) {
            return List.of();
        }
        List<Bar> bars = preloadedBars.get(symbol);
        if (bars != null) {
            return bars;
        }
        bars = preloadedBars.get(normalize(symbol));
        return bars != null ? bars : List.of();
    }

    public boolean hasMarketData() {
        return regime != null && regime != MarketRegime.DATA_MISSING;
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase();
    }
}
