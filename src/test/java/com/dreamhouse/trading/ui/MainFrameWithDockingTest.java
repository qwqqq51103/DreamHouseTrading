package com.dreamhouse.trading.ui;

import com.dreamhouse.trading.core.decision.classifier.TradeMode;
import com.dreamhouse.trading.core.model.Tick;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class MainFrameWithDockingTest {

    @Test
    void dayTradeQuantityIsAlwaysOneTaiwanLot() {
        int quantity = MainFrameWithDocking.calculateOrderQuantity(
                500.0,
                10_000.0,
                0.35,
                0.05,
                TradeMode.DAY_TRADE);

        assertThat(quantity).isEqualTo(1_000);
    }

    @Test
    void nonDayTradeQuantityKeepsCashBudgetCalculation() {
        int quantity = MainFrameWithDocking.calculateOrderQuantity(
                100.0,
                100_000.0,
                0.30,
                0.05,
                TradeMode.SWING_TRADE);

        assertThat(quantity).isEqualTo(250);
    }

    @Test
    void watchlistVolumeUsesLatestTickValueWithoutAccumulating() {
        Tick firstTick = new Tick("2330.TW", LocalDateTime.now(), 100.0, 10);
        Tick latestTick = new Tick("2330.TW", LocalDateTime.now(), 101.0, 25);

        assertThat(MainFrameWithDocking.resolveWatchlistVolume(firstTick)).isEqualTo(10L);
        assertThat(MainFrameWithDocking.resolveWatchlistVolume(latestTick)).isEqualTo(25L);
    }

    @Test
    void dayTradeForceCloseStartsAtThirteenTwentyFive() {
        assertThat(MainFrameWithDocking.isDayTradeForceCloseTime(LocalTime.of(13, 24, 59))).isFalse();
        assertThat(MainFrameWithDocking.isDayTradeForceCloseTime(LocalTime.of(13, 25))).isTrue();
        assertThat(MainFrameWithDocking.isDayTradeForceCloseTime(LocalTime.of(13, 30))).isTrue();
    }

    @Test
    void autoMonitorDayTradeQuantityIgnoresClassifierModeAfterNormalization() {
        int normalizedQuantity = MainFrameWithDocking.calculateOrderQuantity(
                120.0,
                1_000_000.0,
                0.35,
                0.05,
                TradeMode.DAY_TRADE);

        assertThat(normalizedQuantity).isEqualTo(1_000);
    }
}
