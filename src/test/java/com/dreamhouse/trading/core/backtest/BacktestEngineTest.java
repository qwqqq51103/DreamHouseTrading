package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestEngineTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 5, 9, 0);

    @Test
    void shouldUseNextBarOpenWhenSignalGeneratedOnClosedBar() {
        BacktestEngine engine = engineWithBars(List.of(
                bar(0, 100.0, 101.0),
                bar(1, 110.0, 111.0),
                bar(2, 120.0, 121.0)));
        engine.addStrategy(new ScriptedStrategy(0, -1));

        BacktestResult result = engine.runBacktest();

        Trade buy = result.getTrades().stream()
                .filter(trade -> trade.getType() == TradeType.BUY)
                .findFirst()
                .orElseThrow();
        assertThat(buy.getTimestamp()).isEqualTo(START.plusMinutes(1));
        assertThat(buy.getPrice()).isEqualTo(110.0);
        assertThat(buy.getPrice()).isNotEqualTo(101.0);
    }

    @Test
    void shouldApplyCommissionTaxSlippageToNetProfit() {
        BacktestEngine engine = engineWithBars(List.of(
                bar(0, 100.0, 101.0),
                bar(1, 110.0, 111.0),
                bar(2, 120.0, 121.0),
                bar(3, 130.0, 131.0)));
        engine.setCommission(0.01);
        engine.setSellTaxRate(0.02);
        engine.setSlippage(0.005);
        engine.addStrategy(new ScriptedStrategy(0, 1));

        BacktestResult result = engine.runBacktest();

        assertThat(result.getGrossProfit()).isEqualTo(100.0);
        assertThat(result.getTotalCommission()).isEqualTo(23.0);
        assertThat(result.getTotalTax()).isEqualTo(24.0);
        assertThat(result.getTotalSlippageCost()).isEqualTo(11.5);
        assertThat(result.getNetProfit()).isEqualTo(41.5);
    }

    @Test
    void shouldSellWithExplicitExitReason() {
        BacktestEngine engine = engineWithBars(List.of(
                bar(0, 100.0, 101.0),
                bar(1, 110.0, 111.0),
                bar(2, 120.0, 121.0)));
        engine.addStrategy(new ScriptedStrategy(0, 1));

        BacktestResult result = engine.runBacktest();

        Trade sell = result.getTrades().stream()
                .filter(trade -> trade.getType() == TradeType.SELL)
                .findFirst()
                .orElseThrow();
        assertThat(sell.getExitReason()).isEqualTo("TAKE_PROFIT");
        assertThat(sell.getExitReason()).isNotBlank();
    }

    @Test
    void shouldKeepTradeModeAndTimeframeInResult() {
        BacktestEngine engine = engineWithBars(List.of(
                bar(0, 100.0, 101.0),
                bar(5, 110.0, 111.0)));
        engine.setTradeMode(TradeMode.DAY_TRADE);
        engine.setTimeframe(Timeframe.M5);
        engine.addStrategy(new ScriptedStrategy(-1, -1));

        BacktestResult result = engine.runBacktest();

        assertThat(result.getTradeMode()).isEqualTo(TradeMode.DAY_TRADE);
        assertThat(result.getTimeframe()).isEqualTo(Timeframe.M5);
    }

    private BacktestEngine engineWithBars(List<Bar> bars) {
        BacktestEngine engine = new BacktestEngine();
        engine.setInitialCapital(1_000_000.0);
        engine.setData(bars);
        return engine;
    }

    private static Bar bar(int minuteOffset, double open, double close) {
        return new Bar(START.plusMinutes(minuteOffset), open, Math.max(open, close) + 1.0,
                Math.min(open, close) - 1.0, close, 1_000);
    }

    private static final class ScriptedStrategy implements Strategy {
        private final int buyAt;
        private final int sellAt;
        private BacktestEngine engine;

        private ScriptedStrategy(int buyAt, int sellAt) {
            this.buyAt = buyAt;
            this.sellAt = sellAt;
        }

        @Override
        public String getName() {
            return "scripted";
        }

        @Override
        public String getDescription() {
            return "fixed test strategy";
        }

        @Override
        public void initialize(BarSeries barSeries) {
        }

        @Override
        public void onBar(int barIndex, org.ta4j.core.Bar bar) {
            if (barIndex == buyAt) {
                engine.buyWithStops("2330.TW", 10, OrderType.MARKET, 99.0, 130.0, "ENTRY_SETUP");
            }
            if (barIndex == sellAt) {
                engine.sellWithReason("2330.TW", 10, OrderType.MARKET, "TAKE_PROFIT");
            }
        }

        @Override
        public void cleanup() {
        }

        @Override
        public void setEngine(BacktestEngine engine) {
            this.engine = engine;
        }

        @Override
        public BacktestEngine getEngine() {
            return engine;
        }

        @Override
        public StrategyConfig getConfig() {
            return new StrategyConfig();
        }

        @Override
        public void setConfig(StrategyConfig config) {
        }
    }
}
