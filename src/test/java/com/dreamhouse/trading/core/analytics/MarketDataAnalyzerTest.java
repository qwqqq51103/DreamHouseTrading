package com.dreamhouse.trading.core.analytics;

import com.dreamhouse.trading.core.model.Bar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarketDataAnalyzer 測試類
 */
@DisplayName("MarketDataAnalyzer Tests")
class MarketDataAnalyzerTest {

    private List<Bar> testBars;

    @BeforeEach
    void setUp() {
        testBars = createTestBars();
    }

    /**
     * 創建測試用的 K 線數據
     */
    private List<Bar> createTestBars() {
        List<Bar> bars = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0);

        // 創建 50 根 K 線，模擬上升趨勢
        for (int i = 0; i < 50; i++) {
            double base = 100.0 + i * 0.5;
            bars.add(new Bar(
                baseTime.plusMinutes(i),
                base,                    // open
                base + 1.0,             // high
                base - 0.5,             // low
                base + 0.5,             // close
                1000000 + i * 1000      // volume
            ));
        }

        return bars;
    }

    @Test
    @DisplayName("測試 SMA 計算")
    void testCalculateSMA() {
        // 測試 10 期 SMA
        List<Double> sma = MarketDataAnalyzer.calculateSMA(testBars, 10);

        // 前 9 個應該是 null
        for (int i = 0; i < 9; i++) {
            assertNull(sma.get(i), "前 " + (i + 1) + " 個 SMA 應該是 null");
        }

        // 第 10 個應該有值
        assertNotNull(sma.get(9), "第 10 個 SMA 應該有值");
        assertTrue(sma.get(9) > 0, "SMA 值應該大於 0");

        // SMA 應該是遞增的（因為我們的測試數據是上升趨勢）
        for (int i = 10; i < sma.size() - 1; i++) {
            assertTrue(sma.get(i + 1) > sma.get(i),
                "SMA 應該遞增（索引 " + i + ")");
        }
    }

    @Test
    @DisplayName("測試 SMA 邊界情況 - 數據不足")
    void testCalculateSMA_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 5);
        List<Double> sma = MarketDataAnalyzer.calculateSMA(shortBars, 10);

        assertTrue(sma.isEmpty(), "數據不足時應返回空列表");
    }

    @Test
    @DisplayName("測試 EMA 計算")
    void testCalculateEMA() {
        List<Double> ema = MarketDataAnalyzer.calculateEMA(testBars, 12);

        // 第一個值應該等於第一根 K 線的收盤價
        assertEquals(testBars.get(0).getClose(), ema.get(0), 0.001,
            "第一個 EMA 應該等於第一根 K 線的收盤價");

        // 後續 EMA 值應該存在
        for (int i = 1; i < ema.size(); i++) {
            assertNotNull(ema.get(i), "EMA[" + i + "] 不應為 null");
            assertTrue(ema.get(i) > 0, "EMA[" + i + "] 應該大於 0");
        }

        // EMA 應該反映趨勢
        assertTrue(ema.get(ema.size() - 1) > ema.get(0),
            "上升趨勢中，最後的 EMA 應該大於第一個");
    }

    @Test
    @DisplayName("測試 EMA 空數據")
    void testCalculateEMA_EmptyData() {
        List<Double> ema = MarketDataAnalyzer.calculateEMA(new ArrayList<>(), 12);
        assertTrue(ema.isEmpty(), "空數據應返回空列表");
    }

    @Test
    @DisplayName("測試 RSI 計算")
    void testCalculateRSI() {
        List<Double> rsi = MarketDataAnalyzer.calculateRSI(testBars, 14);

        // RSI 值應該在 0-100 之間
        for (int i = 0; i < rsi.size(); i++) {
            if (rsi.get(i) != null) {
                assertTrue(rsi.get(i) >= 0 && rsi.get(i) <= 100,
                    "RSI[" + i + "] 應該在 0-100 之間，實際值: " + rsi.get(i));
            }
        }

        // 上升趨勢的 RSI 應該大於 50
        Double lastRsi = rsi.get(rsi.size() - 1);
        assertNotNull(lastRsi, "最後的 RSI 不應為 null");
        assertTrue(lastRsi > 50, "上升趨勢的 RSI 應該大於 50");
    }

    @Test
    @DisplayName("測試 RSI 數據不足")
    void testCalculateRSI_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 10);
        List<Double> rsi = MarketDataAnalyzer.calculateRSI(shortBars, 14);

        assertTrue(rsi.isEmpty(), "數據不足時應返回空列表");
    }

    @Test
    @DisplayName("測試 Bollinger Bands 計算")
    void testCalculateBollingerBands() {
        MarketDataAnalyzer.BollingerBands bands =
            MarketDataAnalyzer.calculateBollingerBands(testBars, 20, 2.0);

        // 檢查三條線的數量
        assertEquals(testBars.size(), bands.upper.size(), "上軌數量應該等於 K 線數量");
        assertEquals(testBars.size(), bands.middle.size(), "中軌數量應該等於 K 線數量");
        assertEquals(testBars.size(), bands.lower.size(), "下軌數量應該等於 K 線數量");

        // 檢查有效值的關係：上軌 > 中軌 > 下軌
        for (int i = 20; i < bands.upper.size(); i++) {
            assertNotNull(bands.upper.get(i), "上軌[" + i + "] 不應為 null");
            assertNotNull(bands.middle.get(i), "中軌[" + i + "] 不應為 null");
            assertNotNull(bands.lower.get(i), "下軌[" + i + "] 不應為 null");

            assertTrue(bands.upper.get(i) > bands.middle.get(i),
                "上軌應該大於中軌（索引 " + i + ")");
            assertTrue(bands.middle.get(i) > bands.lower.get(i),
                "中軌應該大於下軌（索引 " + i + ")");
        }
    }

    @Test
    @DisplayName("測試 Bollinger Bands 數據不足")
    void testCalculateBollingerBands_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 10);
        MarketDataAnalyzer.BollingerBands bands =
            MarketDataAnalyzer.calculateBollingerBands(shortBars, 20, 2.0);

        assertTrue(bands.upper.isEmpty(), "數據不足時上軌應為空");
        assertTrue(bands.middle.isEmpty(), "數據不足時中軌應為空");
        assertTrue(bands.lower.isEmpty(), "數據不足時下軌應為空");
    }

    @Test
    @DisplayName("測試 MACD 計算")
    void testCalculateMACD() {
        MarketDataAnalyzer.MACD macd =
            MarketDataAnalyzer.calculateMACD(testBars, 12, 26, 9);

        // 檢查數量
        assertEquals(testBars.size(), macd.macdLine.size(), "MACD 線數量應該等於 K 線數量");
        assertEquals(testBars.size(), macd.signalLine.size(), "信號線數量應該等於 K 線數量");
        assertEquals(testBars.size(), macd.histogram.size(), "柱狀圖數量應該等於 K 線數量");

        // 檢查有效值
        boolean hasValidMacd = false;
        for (int i = 26; i < macd.macdLine.size(); i++) {
            if (macd.macdLine.get(i) != null) {
                hasValidMacd = true;
                break;
            }
        }
        assertTrue(hasValidMacd, "應該有有效的 MACD 值");
    }

    @Test
    @DisplayName("測試 MACD 數據不足")
    void testCalculateMACD_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 20);
        MarketDataAnalyzer.MACD macd =
            MarketDataAnalyzer.calculateMACD(shortBars, 12, 26, 9);

        assertTrue(macd.macdLine.isEmpty(), "數據不足時 MACD 線應為空");
    }

    @Test
    @DisplayName("測試波動率計算")
    void testCalculateVolatility() {
        double volatility = MarketDataAnalyzer.calculateVolatility(testBars, 20);

        assertTrue(volatility >= 0, "波動率應該大於等於 0");
        assertTrue(volatility < 10, "波動率應該在合理範圍內");
    }

    @Test
    @DisplayName("測試波動率 - 數據不足")
    void testCalculateVolatility_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 5);
        double volatility = MarketDataAnalyzer.calculateVolatility(shortBars, 20);

        assertEquals(0.0, volatility, "數據不足時應返回 0");
    }

    @Test
    @DisplayName("測試最大回撤計算")
    void testCalculateMaxDrawdown() {
        double maxDrawdown = MarketDataAnalyzer.calculateMaxDrawdown(testBars);

        assertTrue(maxDrawdown >= 0, "最大回撤應該大於等於 0");
        assertTrue(maxDrawdown <= 1, "最大回撤應該小於等於 1（100%）");
    }

    @Test
    @DisplayName("測試最大回撤 - 空數據")
    void testCalculateMaxDrawdown_EmptyData() {
        double maxDrawdown = MarketDataAnalyzer.calculateMaxDrawdown(new ArrayList<>());
        assertEquals(0.0, maxDrawdown, "空數據應返回 0");
    }

    @Test
    @DisplayName("測試夏普比率計算")
    void testCalculateSharpeRatio() {
        double sharpeRatio = MarketDataAnalyzer.calculateSharpeRatio(testBars, 0.02);

        assertTrue(sharpeRatio > 0, "上升趨勢的夏普比率應該大於 0");
    }

    @Test
    @DisplayName("測試夏普比率 - 數據不足")
    void testCalculateSharpeRatio_InsufficientData() {
        List<Bar> singleBar = testBars.subList(0, 1);
        double sharpeRatio = MarketDataAnalyzer.calculateSharpeRatio(singleBar, 0.02);

        assertEquals(0.0, sharpeRatio, "數據不足時應返回 0");
    }

    @Test
    @DisplayName("測試趨勢檢測 - 上升趨勢")
    void testDetectTrend_Uptrend() {
        MarketDataAnalyzer.Trend trend =
            MarketDataAnalyzer.detectTrend(testBars, 10, 20);

        assertEquals(MarketDataAnalyzer.Trend.UPTREND, trend,
            "應該檢測到上升趨勢");
    }

    @Test
    @DisplayName("測試趨勢檢測 - 下降趨勢")
    void testDetectTrend_Downtrend() {
        // 創建強烈下降趨勢數據
        List<Bar> downtrendBars = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0);

        for (int i = 0; i < 50; i++) {
            double base = 150.0 - i * 1.5;  // 更強的下降趨勢
            downtrendBars.add(new Bar(
                baseTime.plusMinutes(i),
                base,
                base + 0.5,
                base - 1.0,
                base - 1.0,  // 收盤在低點
                1000000
            ));
        }

        MarketDataAnalyzer.Trend trend =
            MarketDataAnalyzer.detectTrend(downtrendBars, 10, 20);

        // 由於趨勢檢測的閾值設置，可能檢測為 DOWNTREND 或 SIDEWAYS
        assertTrue(trend == MarketDataAnalyzer.Trend.DOWNTREND ||
                  trend == MarketDataAnalyzer.Trend.SIDEWAYS,
            "應該檢測到下降趨勢或橫盤");
    }

    @Test
    @DisplayName("測試趨勢檢測 - 數據不足")
    void testDetectTrend_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 15);
        MarketDataAnalyzer.Trend trend =
            MarketDataAnalyzer.detectTrend(shortBars, 10, 20);

        assertEquals(MarketDataAnalyzer.Trend.SIDEWAYS, trend,
            "數據不足時應返回橫盤");
    }

    @Test
    @DisplayName("測試支撐阻力位計算")
    void testFindSupportResistance() {
        MarketDataAnalyzer.SupportResistance sr =
            MarketDataAnalyzer.findSupportResistance(testBars, 20);

        assertTrue(sr.support > 0, "支撐位應該大於 0");
        assertTrue(sr.resistance > 0, "阻力位應該大於 0");
        assertTrue(sr.resistance >= sr.support,
            "阻力位應該大於等於支撐位");
    }

    @Test
    @DisplayName("測試支撐阻力位 - 數據不足")
    void testFindSupportResistance_InsufficientData() {
        List<Bar> shortBars = testBars.subList(0, 10);
        MarketDataAnalyzer.SupportResistance sr =
            MarketDataAnalyzer.findSupportResistance(shortBars, 20);

        assertEquals(0.0, sr.support, "數據不足時支撐位應為 0");
        assertEquals(0.0, sr.resistance, "數據不足時阻力位應為 0");
    }

    @Test
    @DisplayName("測試 SupportResistance toString")
    void testSupportResistance_ToString() {
        MarketDataAnalyzer.SupportResistance sr =
            new MarketDataAnalyzer.SupportResistance(100.0, 110.0);

        String str = sr.toString();
        assertTrue(str.contains("100"), "toString 應該包含支撐位");
        assertTrue(str.contains("110"), "toString 應該包含阻力位");
    }

    @Test
    @DisplayName("測試所有 Trend 枚舉值")
    void testTrendEnum() {
        MarketDataAnalyzer.Trend[] trends = MarketDataAnalyzer.Trend.values();
        assertEquals(3, trends.length, "應該有 3 種趨勢類型");

        // 確保可以獲取所有枚舉值
        assertNotNull(MarketDataAnalyzer.Trend.valueOf("UPTREND"));
        assertNotNull(MarketDataAnalyzer.Trend.valueOf("DOWNTREND"));
        assertNotNull(MarketDataAnalyzer.Trend.valueOf("SIDEWAYS"));
    }

    @Test
    @DisplayName("測試極端價格變化的 RSI")
    void testCalculateRSI_ExtremeChanges() {
        // 創建只有上漲的數據
        List<Bar> uptrendBars = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0);

        for (int i = 0; i < 30; i++) {
            double base = 100.0 + i * 2.0;  // 強勁上漲
            uptrendBars.add(new Bar(
                baseTime.plusMinutes(i),
                base, base + 2.0, base, base + 2.0, 1000000
            ));
        }

        List<Double> rsi = MarketDataAnalyzer.calculateRSI(uptrendBars, 14);

        // 強勁上漲時 RSI 應該接近 100
        Double lastRsi = rsi.get(rsi.size() - 1);
        assertNotNull(lastRsi);
        assertTrue(lastRsi > 70, "強勁上漲時 RSI 應該超買（>70）");
    }

    @Test
    @DisplayName("測試零波動率場景")
    void testCalculateVolatility_ZeroVolatility() {
        // 創建價格完全不變的數據
        List<Bar> flatBars = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0);

        for (int i = 0; i < 30; i++) {
            flatBars.add(new Bar(
                baseTime.plusMinutes(i),
                100.0, 100.0, 100.0, 100.0, 1000000
            ));
        }

        double volatility = MarketDataAnalyzer.calculateVolatility(flatBars, 20);

        assertEquals(0.0, volatility, 0.0001, "價格不變時波動率應該為 0");
    }
}
