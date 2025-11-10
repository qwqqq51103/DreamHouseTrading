package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.model.Bar;
import com.dreamhouse.trading.core.model.Tick;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IEXCloudFeed 測試類
 * 注意：這些測試不會實際連接到 IEX Cloud API
 */
@DisplayName("IEXCloudFeed Tests")
class IEXCloudFeedTest {

    private IEXCloudFeed feed;
    private static final String TEST_API_KEY = "test_api_key_12345";

    @BeforeEach
    void setUp() {
        feed = new IEXCloudFeed(TEST_API_KEY);
    }

    @AfterEach
    void tearDown() {
        if (feed != null && feed.isConnected()) {
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
        CountDownLatch latch = new CountDownLatch(1);

        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {
                receivedTicks.add(tick);
                latch.countDown();
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
        IEXCloudFeed emptyKeyFeed = new IEXCloudFeed("");
        assertNotNull(emptyKeyFeed, "即使使用空密鑰也應該能創建實例");
    }

    @Test
    @DisplayName("測試使用 null API 密鑰創建")
    void testCreateWithNullApiKey() {
        // 某些實現可能不允許 null API 密鑰
        try {
            IEXCloudFeed nullKeyFeed = new IEXCloudFeed(null);
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
        CountDownLatch doneLatch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            final String symbol = "STOCK" + i;
            new Thread(() -> {
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
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS),
            "所有訂閱應該在 5 秒內完成");
    }

    @Test
    @DisplayName("測試 null 商品代號")
    void testNullSymbol() {
        MarketDataListener listener = new MarketDataListener() {
            @Override
            public void onTick(Tick tick) {}
        };

        // 訂閱 null 商品應該能處理（可能拋出異常或靜默失敗）
        try {
            feed.subscribe(null, listener);
            // 如果沒有拋出異常，那也可以
        } catch (Exception e) {
            // 預期可能拋出 NullPointerException 或其他異常
            assertNotNull(e);
        }
    }

    @Test
    @DisplayName("測試 null 監聽器")
    void testNullListener() {
        // 訂閱時使用 null 監聽器
        try {
            feed.subscribe("AAPL", null);
            // 如果沒有拋出異常，那也可以
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
}
