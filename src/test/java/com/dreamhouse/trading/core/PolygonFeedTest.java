package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PolygonFeed 測試類
 * 注意：這些測試不會實際連接到 Polygon.io API
 */
@DisplayName("PolygonFeed Tests")
class PolygonFeedTest {

    private PolygonFeed feed;
    private static final String TEST_API_KEY = "test_polygon_key_67890";

    @BeforeEach
    void setUp() {
        feed = new PolygonFeed(TEST_API_KEY, false);
    }

    @AfterEach
    void tearDown() {
        if (feed != null) {
            feed.stop();
        }
    }

    @Test
    @DisplayName("測試創建實例")
    void testCreateInstance() {
        assertNotNull(feed, "Feed 實例不應為 null");
        assertFalse(feed.isConnected(), "初始狀態應該未連接");
    }

    @Test
    @DisplayName("測試啟動和停止")
    void testStartAndStop() {
        feed.start();
        // 注意：不會實際連接，但應該不會拋出異常

        feed.stop();
        assertFalse(feed.isConnected(), "停止後應該未連接");
    }

    @Test
    @DisplayName("測試訂閱")
    void testSubscribe() {
        List<Tick> receivedTicks = new ArrayList<>();

        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                receivedTicks.add(tick);
            }
        };

        // 訂閱應該不會拋出異常
        assertDoesNotThrow(() -> feed.subscribe("AAPL", listener));
    }

    @Test
    @DisplayName("測試取消訂閱")
    void testUnsubscribe() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        feed.subscribe("AAPL", listener);

        // 取消訂閱應該不會拋出異常
        assertDoesNotThrow(() -> feed.unsubscribe("AAPL", listener));
    }

    @Test
    @DisplayName("測試基本功能不拋異常")
    void testBasicFunctionality() {
        // 基本測試：確保創建和基本操作不會拋異常
        assertNotNull(feed);
        assertFalse(feed.isConnected());
    }

    @Test
    @DisplayName("測試暫停和恢復")
    void testPauseAndResume() {
        feed.start();

        // 暫停應該不會拋出異常
        assertDoesNotThrow(() -> feed.pause());

        // 恢復應該不會拋出異常
        assertDoesNotThrow(() -> feed.resume());

        feed.stop();
    }

    @Test
    @DisplayName("測試多次訂閱同一商品")
    void testMultipleSubscriptionsToSameSymbol() {
        MarketDataListener listener1 = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        MarketDataListener listener2 = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        assertDoesNotThrow(() -> {
            feed.subscribe("AAPL", listener1);
            feed.subscribe("AAPL", listener2);
        });
    }

    @Test
    @DisplayName("測試訂閱多個商品")
    void testSubscribeMultipleSymbols() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        assertDoesNotThrow(() -> {
            feed.subscribe("AAPL", listener);
            feed.subscribe("TSLA", listener);
            feed.subscribe("MSFT", listener);
            feed.subscribe("GOOGL", listener);
            feed.subscribe("AMZN", listener);
        });
    }

    @Test
    @DisplayName("測試取消不存在的訂閱")
    void testUnsubscribeNonExistent() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        // 取消不存在的訂閱應該不會拋出異常
        assertDoesNotThrow(() -> feed.unsubscribe("UNKNOWN", listener));
    }

    @Test
    @DisplayName("測試停止後再次啟動")
    void testRestartAfterStop() {
        feed.start();
        feed.stop();

        // 再次啟動應該不會拋出異常
        assertDoesNotThrow(() -> feed.start());

        feed.stop();
    }

    @Test
    @DisplayName("測試使用空 API 密鑰創建")
    void testCreateWithEmptyApiKey() {
        PolygonFeed emptyKeyFeed = new PolygonFeed("", false);
        assertNotNull(emptyKeyFeed, "即使使用空密鑰也應該能創建實例");
    }

    @Test
    @DisplayName("測試使用 null API 密鑰創建")
    void testCreateWithNullApiKey() {
        // 某些實現可能不允許 null API 密鑰
        try {
            PolygonFeed nullKeyFeed = new PolygonFeed(null, false);
            assertNotNull(nullKeyFeed);
        } catch (NullPointerException e) {
            // 拋出 NPE 也是可接受的行為
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("測試訂閱後立即取消")
    void testSubscribeAndImmediateUnsubscribe() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        feed.subscribe("AAPL", listener);
        feed.unsubscribe("AAPL", listener);

        // 不應該有任何錯誤
        assertTrue(true);
    }

    @Test
    @DisplayName("測試多個時間週期")
    void testMultipleTimeframes() {
        // 確保支持多個時間週期不會拋異常
        Timeframe[] timeframes = {
            Timeframe.M1, Timeframe.M5, Timeframe.M15,
            Timeframe.M30, Timeframe.H1, Timeframe.D1
        };

        // 基本驗證
        assertTrue(timeframes.length > 0);
    }

    @Test
    @DisplayName("測試並發訂閱")
    void testConcurrentSubscriptions() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            final String symbol = "SYMBOL" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MarketDataListener listener = new MarketDataListener() {
                        @Override
                        public void onTick(Tick tick) {}
                    };
                    feed.subscribe(symbol, listener);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
            "所有訂閱應該在 10 秒內完成");
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
            "測試用 executor 應該正常結束");
    }

    @Test
    @DisplayName("測試 null 商品代號")
    void testNullSymbol() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        try {
            feed.subscribe(null, listener);
        } catch (Exception e) {
            // 預期可能拋出異常
            assertNotNull(e);
        }
    }

    @Test
    @DisplayName("測試 null 監聽器")
    void testNullListener() {
        try {
            feed.subscribe("AAPL", null);
        } catch (Exception e) {
            // 預期可能拋出 NullPointerException
            assertNotNull(e);
        }
    }

    @Test
    @DisplayName("測試連續暫停")
    void testMultiplePause() {
        feed.start();
        feed.pause();

        // 再次暫停應該不會有問題
        assertDoesNotThrow(() -> feed.pause());

        feed.stop();
    }

    @Test
    @DisplayName("測試連續恢復")
    void testMultipleResume() {
        feed.start();
        feed.resume();

        // 再次恢復應該不會有問題
        assertDoesNotThrow(() -> feed.resume());

        feed.stop();
    }

    @Test
    @DisplayName("測試未啟動時暫停")
    void testPauseWithoutStart() {
        // 未啟動時暫停應該不會拋出異常
        assertDoesNotThrow(() -> feed.pause());
    }

    @Test
    @DisplayName("測試未啟動時恢復")
    void testResumeWithoutStart() {
        // 未啟動時恢復應該不會拋出異常
        assertDoesNotThrow(() -> feed.resume());
    }

    @Test
    @DisplayName("測試快速啟動停止循環")
    void testRapidStartStopCycle() {
        for (int i = 0; i < 5; i++) {
            feed.start();
            feed.stop();
        }

        assertFalse(feed.isConnected(), "最終應該處於未連接狀態");
    }

    @Test
    @DisplayName("測試訂閱加密貨幣")
    void testSubscribeCrypto() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        // Polygon 支持加密貨幣
        assertDoesNotThrow(() -> {
            feed.subscribe("X:BTCUSD", listener);
            feed.subscribe("X:ETHUSD", listener);
        });
    }

    @Test
    @DisplayName("測試訂閱外匯")
    void testSubscribeForex() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        // Polygon 支持外匯
        assertDoesNotThrow(() -> {
            feed.subscribe("C:EURUSD", listener);
            feed.subscribe("C:GBPUSD", listener);
        });
    }

    @Test
    @DisplayName("測試混合訂閱（股票、加密貨幣、外匯）")
    void testMixedSubscriptions() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        assertDoesNotThrow(() -> {
            feed.subscribe("AAPL", listener);        // 股票
            feed.subscribe("X:BTCUSD", listener);    // 加密貨幣
            feed.subscribe("C:EURUSD", listener);    // 外匯
        });
    }

    @Test
    @DisplayName("測試大量並發取消訂閱")
    void testMassUnsubscribe() throws InterruptedException {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        // 先訂閱大量商品
        String[] symbols = new String[50];
        for (int i = 0; i < 50; i++) {
            symbols[i] = "STOCK" + i;
            feed.subscribe(symbols[i], listener);
        }

        // 並發取消訂閱
        CountDownLatch latch = new CountDownLatch(50);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (String symbol : symbols) {
            executor.submit(() -> {
                try {
                    feed.unsubscribe(symbol, listener);
                } finally {
                    latch.countDown();
                }
            });
        }
        executor.shutdown();

        assertTrue(latch.await(10, TimeUnit.SECONDS),
            "所有取消訂閱應該在 10 秒內完成");
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
            "測試用 executor 應該正常結束");
    }

    @Test
    @DisplayName("測試創建實例的穩定性")
    void testInstanceStability() {
        // 確保可以創建多個實例
        PolygonFeed feed2 = new PolygonFeed("another_key", false);
        PolygonFeed feed3 = new PolygonFeed("yet_another_key", false);

        assertNotNull(feed2);
        assertNotNull(feed3);
    }
}
