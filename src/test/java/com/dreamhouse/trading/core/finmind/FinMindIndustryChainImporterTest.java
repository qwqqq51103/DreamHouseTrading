package com.dreamhouse.trading.core.finmind;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinMindIndustryChainImporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void importerMergesDuplicateStockRowsBeforeWritingSql() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
             MarketDataCollectorRepository repository = new MarketDataCollectorRepository(connection)) {
            FakeGateway gateway = new FakeGateway(objectMapper.readTree("""
                    {
                      "data": [
                        {"stock_id":"9951","industry":"建材營造","sub_industry":"水泥","chain_name":"房地產","date":"2026-05-14"},
                        {"stock_id":"9951","industry":"建材營造","sub_industry":"建材","chain_name":"營建","date":"2026-05-14"},
                        {"stock_id":"2330","industry":"半導體","sub_industry":"晶圓代工","chain_name":"AI","date":"2026-05-14"}
                      ]
                    }
                    """));
            FinMindIndustryChainImporter importer = new FinMindIndustryChainImporter(gateway, repository);

            FinMindIndustryChainImporter.ImportResult result = importer.importIndustryChain();

            assertThat(result.parsedRows()).isEqualTo(2);
            assertThat(result.insertedRows()).isEqualTo(2);
            MarketDataCollectorRepository.IndustryInfo info = repository.findIndustryInfo("9951.TW");
            assertThat(info).isNotNull();
            assertThat(info.subIndustry()).contains("水泥").contains("建材");
            assertThat(info.chainName()).contains("房地產").contains("營建");
        }
    }

    private static class FakeGateway implements FinMindGateway {
        private final JsonNode response;

        private FakeGateway(JsonNode response) {
            this.response = response;
        }

        @Override
        public String login(String userId, String password) {
            return "";
        }

        @Override
        public JsonNode queryData(FinMindRequest request) {
            return response;
        }

        @Override
        public JsonNode queryDataset(FinMindDataset dataset, String dataId, LocalDate startDate, LocalDate endDate) {
            return response;
        }

        @Override
        public JsonNode queryDatalist(FinMindDataset dataset) {
            return response;
        }

        @Override
        public JsonNode queryTranslation(FinMindDataset dataset) {
            return response;
        }

        @Override
        public FinMindApiUsage fetchApiUsage() {
            return new FinMindApiUsage(0, 0);
        }

        @Override
        public JsonNode fetchTaiwanStockTickSnapshot(Collection<String> symbols) {
            return response;
        }

        @Override
        public JsonNode fetchTaiwanStockTradingDailyReport(String dataId, LocalDate date) {
            return response;
        }

        @Override
        public JsonNode fetchTaiwanStockTradingDailyReportSecIdAgg(String dataId, LocalDate startDate, LocalDate endDate) {
            return response;
        }

        @Override
        public JsonNode fetchStorageObject(FinMindDataset dataset, LocalDate date) {
            return response;
        }

        @Override
        public boolean testToken() {
            return true;
        }
    }
}
