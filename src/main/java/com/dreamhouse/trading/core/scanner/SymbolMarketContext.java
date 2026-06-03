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
        String weakMarketReason,
        double watchlistReturnRankPercent,
        double watchlistVolumeRankPercent) {

    public SymbolMarketContext(
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
        this(symbol,
                benchmarkSymbol,
                industry,
                close,
                returnPercent,
                benchmarkReturnPercent,
                industryReturnPercent,
                relativeToBenchmarkPercent,
                relativeToIndustryPercent,
                vwap,
                vwapSlopePercent,
                volumeSustain,
                weakMarketQualified,
                weakMarketReason,
                0.0,
                0.0);
    }

    public static SymbolMarketContext missing(String symbol, String reason) {
        return new SymbolMarketContext(
                symbol,
                "",
                "未知",
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
                reason != null ? reason : "市場資料不足",
                0.0,
                0.0);
    }
}
