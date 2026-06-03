package com.dreamhouse.trading.core.stockpool;

import com.dreamhouse.trading.core.MarketDataCollectorRepository;
import com.dreamhouse.trading.core.finmind.FinMindApiUsage;
import com.dreamhouse.trading.core.finmind.FinMindDataset;
import com.dreamhouse.trading.core.finmind.FinMindGateway;
import com.dreamhouse.trading.core.finmind.FinMindRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AfterHoursStockPoolServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void keepsDailyScoreWhenBrokerApiFails() {
        AfterHoursStockPoolService service = new AfterHoursStockPoolService(
                new FakeGateway(true),
                MarketDataCollectorRepository.unavailable("test"));

        AfterHoursStockPoolService.StockPoolResult result = service.buildPool(
                List.of("2330.TW"),
                LocalDate.parse("2026-05-18"),
                10,
                true);

        assertThat(result.allCandidates()).hasSize(1);
        AfterHoursStockPoolService.StockPoolCandidate candidate = result.allCandidates().get(0);
        assertThat(candidate.totalScore()).isGreaterThan(0.0);
        assertThat(candidate.trendScore()).isGreaterThan(0.0);
        assertThat(candidate.volumeScore()).isGreaterThan(0.0);
        assertThat(candidate.brokerRiskFlag()).startsWith("分點資料失敗");
        assertThat(candidate.reason()).contains("分點資料失敗");
    }

    @Test
    void readsFinMindPriceFieldsCaseInsensitively() {
        AfterHoursStockPoolService service = new AfterHoursStockPoolService(
                new FakeGateway(false),
                MarketDataCollectorRepository.unavailable("test"));

        AfterHoursStockPoolService.StockPoolResult result = service.buildPool(
                List.of("2330"),
                LocalDate.parse("2026-05-18"),
                1,
                false);

        assertThat(result.allCandidates()).hasSize(1);
        AfterHoursStockPoolService.StockPoolCandidate candidate = result.allCandidates().get(0);
        assertThat(candidate.symbol()).isEqualTo("2330.TW");
        assertThat(candidate.totalScore()).isGreaterThan(30.0);
        assertThat(candidate.reason()).contains("日線趨勢");
    }

    @Test
    void aggregatesWholeMarketTradingDailyReportByStock() {
        AfterHoursStockPoolService service = new AfterHoursStockPoolService(
                new FakeGateway(false),
                MarketDataCollectorRepository.unavailable("test"));

        AfterHoursStockPoolService.StockPoolResult result = service.buildPool(
                List.of("2330.TW"),
                LocalDate.parse("2026-05-18"),
                1,
                true);

        AfterHoursStockPoolService.StockPoolCandidate candidate = result.allCandidates().get(0);
        assertThat(candidate.brokerNetBuy()).isEqualTo(70000.0);
        assertThat(candidate.brokerNetBuyLots()).isEqualTo(70.0);
        assertThat(candidate.brokerChipScore()).isGreaterThan(0.0);
        assertThat(candidate.brokerRiskFlag()).isBlank();
    }

    private static class FakeGateway implements FinMindGateway {
        private final boolean brokerFails;

        private FakeGateway(boolean brokerFails) {
            this.brokerFails = brokerFails;
        }

        @Override
        public JsonNode queryData(FinMindRequest request) {
            try {
                if (request.getDataset() == FinMindDataset.TAIWAN_STOCK_PRICE) {
                    return MAPPER.readTree("""
                            {"data":[
                              {"date":"2026-05-12","open":90,"max":92,"min":89,"close":91,"trading_volume":800000},
                              {"date":"2026-05-13","open":91,"max":94,"min":90,"close":93,"trading_volume":900000},
                              {"date":"2026-05-14","open":93,"max":95,"min":92,"close":94,"trading_volume":1000000},
                              {"date":"2026-05-15","open":94,"max":97,"min":93,"close":96,"trading_volume":1100000},
                              {"date":"2026-05-18","open":96,"max":103,"min":95,"close":102,"trading_volume":2600000}
                            ]}
                            """);
                }
                if (request.getDataset() == FinMindDataset.TAIWAN_STOCK_DAY_TRADING) {
                    return MAPPER.readTree("""
                            {"data":[{"date":"2026-05-18","trading_volume":500000}]}
                            """);
                }
                if (request.getDataset() == FinMindDataset.TAIWAN_STOCK_TRADING_DAILY_REPORT) {
                    if (brokerFails) {
                        throw new RuntimeException("sponsor permission required");
                    }
                    return MAPPER.readTree("""
                            {"data":[
                              {"date":"2026-05-18","stock_id":"2330","securities_trader_id":"9200","securities_trader":"凱基","price":102.0,"buy":120000,"sell":50000},
                              {"date":"2026-05-18","stock_id":"2330","securities_trader_id":"9200","securities_trader":"凱基","price":102.5,"buy":10000,"sell":10000},
                              {"date":"2026-05-18","stock_id":"2317","securities_trader_id":"9800","securities_trader":"元大","price":200.0,"buy":50000,"sell":100000}
                            ]}
                            """);
                }
                return MAPPER.readTree("{\"data\":[]}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public JsonNode queryDataset(FinMindDataset dataset, String dataId, LocalDate startDate, LocalDate endDate) {
            return queryData(FinMindRequest.dataset(dataset).dataId(dataId).startDate(startDate).endDate(endDate).build());
        }

        @Override
        public JsonNode queryDatalist(FinMindDataset dataset) {
            return empty();
        }

        @Override
        public JsonNode queryTranslation(FinMindDataset dataset) {
            return empty();
        }

        @Override
        public String login(String userId, String password) {
            return "";
        }

        @Override
        public FinMindApiUsage fetchApiUsage() {
            return new FinMindApiUsage(0, 0);
        }

        @Override
        public JsonNode fetchTaiwanStockTickSnapshot(Collection<String> symbols) {
            return empty();
        }

        @Override
        public JsonNode fetchTaiwanStockTradingDailyReport(String dataId, LocalDate date) {
            return empty();
        }

        @Override
        public JsonNode fetchTaiwanStockTradingDailyReportSecIdAgg(String dataId, LocalDate startDate, LocalDate endDate) {
            return empty();
        }

        @Override
        public JsonNode fetchStorageObject(FinMindDataset dataset, LocalDate date) {
            return empty();
        }

        @Override
        public boolean testToken() {
            return true;
        }

        private JsonNode empty() {
            try {
                return MAPPER.readTree("{\"data\":[]}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
