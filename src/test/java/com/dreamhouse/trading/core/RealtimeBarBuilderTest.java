package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeBarBuilderTest {

    @Test
    void shouldConvertCumulativeSnapshotVolumeToDeltaBars() {
        RealtimeBarBuilder builder = new RealtimeBarBuilder();
        LocalDateTime base = LocalDateTime.of(2026, 1, 5, 9, 0);

        builder.addSnapshot("2330.TW", base.plusSeconds(1), 100.0, 100.2, 99.9, 100.0, 100);
        builder.addSnapshot("2330.TW", base.plusSeconds(10), 100.0, 100.4, 99.9, 100.3, 130);
        builder.addSnapshot("2330.TW", base.plusSeconds(30), 100.0, 100.5, 99.9, 100.4, 150);
        builder.addSnapshot("2330.TW", base.plusMinutes(1).plusSeconds(10), 100.0, 100.8, 100.2, 100.6, 180);
        builder.addSnapshot("2330.TW", base.plusMinutes(1).plusSeconds(40), 100.0, 100.9, 100.4, 100.8, 190);

        List<Bar> bars = builder.buildBars("2330.TW", Timeframe.M1, 10);

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).getVolume()).isEqualTo(150);
        assertThat(bars.get(1).getVolume()).isEqualTo(40);
    }

    @Test
    void shouldNotCreateExtraM5BarAt1330CloseAuction() {
        RealtimeBarBuilder builder = new RealtimeBarBuilder();
        LocalDateTime date = LocalDateTime.of(2026, 1, 5, 13, 25);

        builder.addSnapshot("2330.TW", date.plusSeconds(5), 500.0, 500.5, 499.5, 500.0, 1_000);
        builder.addSnapshot("2330.TW", date.plusMinutes(4).plusSeconds(30), 500.0, 501.0, 499.5, 500.8, 1_300);
        builder.addSnapshot("2330.TW", date.plusMinutes(5), 500.0, 501.5, 499.5, 501.0, 1_500);

        List<Bar> bars = builder.buildBars("2330.TW", Timeframe.M5, 10);

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).getTimestamp()).isEqualTo(date);
        assertThat(bars.get(0).getClose()).isEqualTo(501.0);
        assertThat(bars.get(0).getVolume()).isEqualTo(1_500);
    }

    @Test
    void shouldResetDeltaWhenCumulativeVolumeRestarts() {
        RealtimeBarBuilder builder = new RealtimeBarBuilder();
        LocalDateTime base = LocalDateTime.of(2026, 1, 5, 9, 0);

        builder.addSnapshot("2330.TW", base.plusSeconds(10), 100.0, 100.0, 100.0, 100.0, 500);
        builder.addSnapshot("2330.TW", base.plusMinutes(1).plusSeconds(10), 100.0, 100.2, 99.9, 100.1, 50);
        builder.addSnapshot("2330.TW", base.plusMinutes(1).plusSeconds(30), 100.0, 100.3, 99.9, 100.2, 80);

        List<Bar> bars = builder.buildBars("2330.TW", Timeframe.M1, 10);

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).getVolume()).isEqualTo(500);
        assertThat(bars.get(1).getVolume()).isEqualTo(80);
    }
}
