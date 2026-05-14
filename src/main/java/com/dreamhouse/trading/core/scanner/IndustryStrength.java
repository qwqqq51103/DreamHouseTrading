package com.dreamhouse.trading.core.scanner;

public record IndustryStrength(
        String industry,
        double averageReturnPercent,
        double averageVolume,
        int symbolCount,
        int strongSymbolCount,
        double score,
        int totalSymbolCount,
        int dataSymbolCount,
        double coveragePercent,
        double relativeBenchmarkPercent,
        double vwapPassPercent,
        double volumeSustainPercent,
        String source) {

    public IndustryStrength(
            String industry,
            double averageReturnPercent,
            double averageVolume,
            int symbolCount,
            int strongSymbolCount,
            double score) {
        this(
                industry,
                averageReturnPercent,
                averageVolume,
                symbolCount,
                strongSymbolCount,
                score,
                symbolCount,
                symbolCount,
                symbolCount > 0 ? 100.0 : 0.0,
                0.0,
                0.0,
                0.0,
                "觀察清單");
    }

    public static IndustryStrength unknown() {
        return new IndustryStrength("未分類", 0.0, 0.0, 0, 0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0, "無資料");
    }

    public boolean hasData() {
        return dataSymbolCount > 0 || symbolCount > 0;
    }
}
