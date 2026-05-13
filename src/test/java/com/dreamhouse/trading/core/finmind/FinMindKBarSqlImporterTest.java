package com.dreamhouse.trading.core.finmind;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.model.Bar;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinMindKBarSqlImporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void importsSingleDayKBarIntoIntradayCandlestickIntervals() throws Exception {
        try (Connection connection = createSchema();
             MarketDataCollectorRepository repository = new MarketDataCollectorRepository(connection)) {
            FakeGateway gateway = new FakeGateway(objectMapper.readTree("""
                    {
                      "msg": "success",
                      "status": 200,
                      "data": [
                        {"date":"2026-05-12","minute":"09:00","open":100,"high":101,"low":99,"close":100.5,"volume":10},
                        {"date":"2026-05-12","minute":"09:01","open":100.5,"high":102,"low":100,"close":101,"volume":20},
                        {"date":"2026-05-12","minute":"09:02","open":101,"high":103,"low":100.5,"close":102,"volume":30},
                        {"date":"2026-05-12","minute":"09:03","open":102,"high":104,"low":101,"close":103,"volume":40},
                        {"date":"2026-05-12","minute":"09:04","open":103,"high":105,"low":102,"close":104,"volume":50}
                      ]
                    }
                    """));
            FinMindKBarSqlImporter importer = new FinMindKBarSqlImporter(gateway, repository);

            FinMindKBarSqlImporter.ImportResult result = importer.importSymbols(
                    List.of("2330.TW"),
                    LocalDate.of(2026, 5, 12));

            assertThat(result.successSymbols()).isEqualTo(1);
            assertThat(result.totalInsertedBars()).isEqualTo(9);
            assertThat(gateway.lastRequest.getDataset()).isEqualTo(FinMindDataset.TAIWAN_STOCK_K_BAR);
            assertThat(gateway.lastRequest.getDataId()).isEqualTo("2330");
            assertThat(gateway.lastRequest.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 12));

            assertThat(repository.findCandlesByTimeRange(
                    "2330.TW",
                    "M1",
                    LocalDate.of(2026, 5, 12).atTime(9, 0),
                    LocalDate.of(2026, 5, 12).atTime(13, 30))).hasSize(5);

            List<Bar> m5Bars = repository.findCandlesByTimeRange(
                    "2330.TW",
                    "M5",
                    LocalDate.of(2026, 5, 12).atTime(9, 0),
                    LocalDate.of(2026, 5, 12).atTime(13, 30));
            assertThat(m5Bars).hasSize(1);
            assertThat(m5Bars.get(0).getOpen()).isEqualTo(100.0);
            assertThat(m5Bars.get(0).getHigh()).isEqualTo(105.0);
            assertThat(m5Bars.get(0).getLow()).isEqualTo(99.0);
            assertThat(m5Bars.get(0).getClose()).isEqualTo(104.0);
            assertThat(m5Bars.get(0).getVolume()).isEqualTo(150L);
        }
    }

    @Test
    void replacesExistingCandlesForSameSymbolDateAndInterval() throws Exception {
        try (Connection connection = createSchema();
             MarketDataCollectorRepository repository = new MarketDataCollectorRepository(connection)) {
            insertOldCandle(connection);
            FakeGateway gateway = new FakeGateway(objectMapper.readTree("""
                    {"data":[
                      {"date":"2026-05-12","minute":"09:00","open":200,"high":201,"low":199,"close":200.5,"volume":10}
                    ]}
                    """));
            FinMindKBarSqlImporter importer = new FinMindKBarSqlImporter(gateway, repository);

            importer.importSymbols(List.of("2330.TW"), LocalDate.of(2026, 5, 12));

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT COUNT(*) AS count
                         FROM candlesticks
                         WHERE symbol = '2330.TW' AND interval_type = 'M1'
                         """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("count")).isEqualTo(1);
            }
        }
    }

    private Connection createSchema() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE candlesticks (
                        symbol VARCHAR(32),
                        ts TIMESTAMP,
                        interval_type VARCHAR(16),
                        open_price DOUBLE,
                        high_price DOUBLE,
                        low_price DOUBLE,
                        close_price DOUBLE,
                        volume BIGINT,
                        amount DOUBLE,
                        created_at TIMESTAMP
                    )
                    """);
        }
        return connection;
    }

    private void insertOldCandle(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO candlesticks
                        (symbol, ts, interval_type, open_price, high_price, low_price, close_price, volume, amount, created_at)
                    VALUES
                        ('2330.TW', '2026-05-12 09:00:00', 'M1', 1, 1, 1, 1, 1, 1, CURRENT_TIMESTAMP)
                    """);
        }
    }

    private static class FakeGateway implements FinMindGateway {
        private final JsonNode response;
        private FinMindRequest lastRequest;

        private FakeGateway(JsonNode response) {
            this.response = response;
        }

        @Override
        public String login(String userId, String password) {
            return "";
        }

        @Override
        public JsonNode queryData(FinMindRequest request) {
            lastRequest = request;
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
