package com.dreamhouse.trading.core.finmind;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class FinMindRequest {
    private final FinMindDataset dataset;
    private final String dataId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<String, String> extraParams;

    private FinMindRequest(Builder builder) {
        this.dataset = builder.dataset;
        this.dataId = builder.dataId;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.extraParams = Map.copyOf(builder.extraParams);
    }

    public static Builder dataset(FinMindDataset dataset) {
        return new Builder(dataset);
    }

    public FinMindDataset getDataset() {
        return dataset;
    }

    public String getDataId() {
        return dataId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Map<String, String> getExtraParams() {
        return extraParams;
    }

    public static class Builder {
        private final FinMindDataset dataset;
        private String dataId;
        private LocalDate startDate;
        private LocalDate endDate;
        private final Map<String, String> extraParams = new LinkedHashMap<>();

        private Builder(FinMindDataset dataset) {
            if (dataset == null) {
                throw new IllegalArgumentException("dataset is required");
            }
            this.dataset = dataset;
        }

        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder param(String key, String value) {
            if (key != null && value != null && !value.isBlank()) {
                extraParams.put(key, value);
            }
            return this;
        }

        public FinMindRequest build() {
            if (dataset.isSingleDayQuery()
                    && startDate != null
                    && endDate != null
                    && !startDate.equals(endDate)) {
                throw new IllegalArgumentException(dataset.apiName() + " supports single-day queries only");
            }
            return new FinMindRequest(this);
        }
    }
}
