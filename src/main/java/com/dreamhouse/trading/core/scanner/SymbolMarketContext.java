package com.dreamhouse.trading.core.scanner;

public record SymbolMarketContext(
        String symbol,
        String benchmarkSymbol,
        String industry,
        double close,
        double returnPercent,
        double benchmarkReturnPercent,
        double industryReturnPercent,
        double relativeToBenchmarkPercent,
        double relativeToIndustryPercent,
        double vwap,
        double vwapSlopePercent,
        boolean volumeSustain,
        boolean weakMarketQualified,
        String weakMarketReason) {

    public static SymbolMarketContext missing(String symbol, String reason) {
        return new SymbolMarketContext(
                symbol,
                "",
                "未分類",
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                false,
                reason != null ? reason : "市場脈絡資料不足");
    }
}
