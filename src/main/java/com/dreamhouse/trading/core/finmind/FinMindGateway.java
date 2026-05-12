package com.dreamhouse.trading.core.finmind;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Collection;

public interface FinMindGateway {
    String login(String userId, String password);

    JsonNode queryData(FinMindRequest request);

    JsonNode queryDataset(FinMindDataset dataset, String dataId, LocalDate startDate, LocalDate endDate);

    JsonNode queryDatalist(FinMindDataset dataset);

    JsonNode queryTranslation(FinMindDataset dataset);

    FinMindApiUsage fetchApiUsage();

    JsonNode fetchTaiwanStockTickSnapshot(Collection<String> symbols);

    JsonNode fetchTaiwanStockTradingDailyReport(String dataId, LocalDate date);

    JsonNode fetchTaiwanStockTradingDailyReportSecIdAgg(String dataId, LocalDate startDate, LocalDate endDate);

    JsonNode fetchStorageObject(FinMindDataset dataset, LocalDate date);

    boolean testToken();
}
