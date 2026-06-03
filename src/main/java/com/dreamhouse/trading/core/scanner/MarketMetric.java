package com.dreamhouse.trading.core.scanner;

public record MarketMetric(
        String symbol,
        double close,
        double returnPercent,
        double vwap,
        double vwapSlopePercent,
        boolean volumeSustain,
        double totalVolume,
        int barCount) {

    public static MarketMetric empty(String symbol) {
        return new MarketMetric(symbol, 0.0, 0.0, 0.0, 0.0, false, 0.0, 0);
    }

    public boolean hasData() {
        return barCount > 0 && close > 0.0;
    }
}
