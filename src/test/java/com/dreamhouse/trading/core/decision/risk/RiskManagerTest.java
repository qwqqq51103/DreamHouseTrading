package com.dreamhouse.trading.core.decision.risk;

import com.dreamhouse.trading.core.backtest.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
