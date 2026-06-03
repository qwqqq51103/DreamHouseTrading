package com.dreamhouse.trading.core.decision.risk;

import com.dreamhouse.trading.core.backtest.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RiskManagerTest {

    private RiskConfig riskConfig;
    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        riskConfig = new RiskConfig();
        riskConfig.setMinRiskRewardRatio(1.8);
        riskConfig.setMinVolatilityPercent(0.01);
        riskConfig.setMaxVolatilityPercent(0.04);
        riskManager = new RiskManager(riskConfig, new Portfolio(1_000_000.0));
    }

    @Test
    void rejectsEntryWhenRiskRewardIsTooLow() {
        RiskViolation violation = riskManager.checkEntrySetup("2330.TW", 100.0, 97.0, 104.0, 0.02);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.MIN_RISK_REWARD);
    }

    @Test
    void rejectsEntryWhenVolatilityIsTooLow() {
        RiskViolation violation = riskManager.checkEntrySetup("2330.TW", 100.0, 98.0, 105.0, 0.005);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.VOLATILITY_TOO_LOW);
    }

    @Test
    void rejectsEntryWhenVolatilityIsTooHigh() {
        RiskViolation violation = riskManager.checkEntrySetup("2330.TW", 100.0, 98.0, 105.0, 0.05);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.VOLATILITY_TOO_HIGH);
    }

    @Test
    void acceptsEntryWhenRiskSetupIsWithinBounds() {
        RiskViolation violation = riskManager.checkEntrySetup("2330.TW", 100.0, 98.0, 104.5, 0.02);

        assertThat(violation).isNull();
    }

    @Test
    void shouldRejectDayTradeWhenCashCannotBuyOneLot() {
        RiskConfig config = new RiskConfig();
        config.setMaxPositionSizePercent(1.0);
        config.setMinCashReservePercent(0.05);
        RiskManager manager = new RiskManager(config, new Portfolio(100_000.0));

        RiskViolation violation = manager.checkDayTradeOpenLong(
                "2330.TW",
                LocalDateTime.of(2026, 1, 5, 10, 0),
                96.0,
                1_000);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.INSUFFICIENT_CASH);
        assertThat(violation.getMessage()).contains("Available cash");
    }

    @Test
    void shouldBlockOpenLongAfter1325() {
        RiskViolation violation = riskManager.checkDayTradeOpenLong(
                "2330.TW",
                LocalDateTime.of(2026, 1, 5, 13, 25),
                100.0,
                1_000);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.DAY_TRADE_TIME_BLOCK);
    }

    @Test
    void shouldBlockOpenLongDuringStopLossCooldown() {
        riskManager.registerStopLossCooldown(
                "2330.TW",
                LocalDateTime.of(2026, 1, 5, 10, 0),
                60);

        RiskViolation violation = riskManager.checkDayTradeOpenLong(
                "2330.TW",
                LocalDateTime.of(2026, 1, 5, 10, 30),
                100.0,
                1_000);

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.STOP_LOSS_COOLDOWN);
    }

    @Test
    void shouldBlockOpenLongWhenDailyLossCircuitBreakerTriggered() {
        riskManager.updateDailyPnL(-31_000.0);

        RiskViolation violation = riskManager.checkDayTradeOpenLong(
                "2330.TW",
                LocalDateTime.of(2026, 1, 5, 10, 0),
                100.0,
                1_000);

        assertThat(riskManager.isDailyLimitHit()).isTrue();
        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.DAILY_LOSS_LIMIT);
    }

    @Test
    void shouldDetectHoldingPeriodViolation() {
        RiskConfig config = new RiskConfig();
        config.setMaxHoldingBars(3);
        Portfolio portfolio = new Portfolio(1_000_000.0);
        portfolio.addPosition("2330.TW", 1_000, 100.0, 0.0);
        portfolio.getPosition("2330.TW").setEntryBarIndex(2);
        RiskManager manager = new RiskManager(config, portfolio);

        var violations = manager.checkHoldingPeriodViolations(5, 54);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getType()).isEqualTo(RiskViolation.Type.HOLDING_PERIOD_EXCEEDED);
        assertThat(violations.get(0).getSymbol()).isEqualTo("2330.TW");
        assertThat(violations.get(0).shouldForceClose()).isTrue();
    }

    @Test
    void shouldDetectSymbolLossViolation() {
        RiskConfig config = new RiskConfig();
        config.setMaxSymbolLossPercent(0.01);
        Portfolio portfolio = new Portfolio(1_000_000.0);
        portfolio.addPosition("2330.TW", 1_000, 100.0, 0.0);
        portfolio.reducePosition("2330.TW", 500, 70.0, 0.0, 0.0);
        RiskManager manager = new RiskManager(config, portfolio);

        RiskViolation violation = manager.checkAccountRisk();

        assertThat(violation).isNotNull();
        assertThat(violation.getType()).isEqualTo(RiskViolation.Type.SYMBOL_LOSS_LIMIT);
        assertThat(violation.getSymbol()).isEqualTo("2330.TW");
    }

    @Test
    void shouldResetDailyRiskStateForNewTradingDay() {
        RiskConfig config = new RiskConfig();
        config.setMaxPositionSizePercent(1.0);
        RiskManager manager = new RiskManager(config, new Portfolio(1_000_000.0));
        manager.updateDailyPnL(-31_000.0);
        assertThat(manager.isDailyLimitHit()).isTrue();

        manager.onNewDay(LocalDateTime.of(2026, 1, 6, 0, 0).toLocalDate());
        RiskViolation violation = manager.checkDayTradeOpenLong(
                "2330.TW",
                LocalDateTime.of(2026, 1, 6, 10, 0),
                100.0,
                1_000);

        assertThat(manager.isDailyLimitHit()).isFalse();
        assertThat(manager.getTodayPnL()).isEqualTo(0.0);
        assertThat(violation).isNull();
    }
}
