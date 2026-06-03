package com.dreamhouse.trading.core.finmind;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FinMindDatasetRegistry {
    public List<FinMindDataset> listAll() {
        return Arrays.asList(FinMindDataset.values());
    }

    public Optional<FinMindDataset> findByApiName(String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return Optional.empty();
        }
        String normalized = apiName.trim().toLowerCase(Locale.ROOT);
        return listAll().stream()
                .filter(dataset -> dataset.apiName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public List<FinMindDataset> listByTier(FinMindDataset.Tier tier) {
        return listAll().stream()
                .filter(dataset -> dataset.tier() == tier)
                .toList();
    }

    public List<FinMindDataset> listSingleDayDatasets() {
        return listAll().stream()
                .filter(FinMindDataset::isSingleDayQuery)
                .toList();
    }

    public List<FinMindDataset> listAllStockSingleDayDatasets() {
        return listAll().stream()
                .filter(FinMindDataset::isAllStockSingleDaySupported)
                .toList();
    }

    public boolean requiresPaidTier(FinMindDataset dataset) {
        return dataset != null && dataset.tier() != FinMindDataset.Tier.FREE;
    }
}
