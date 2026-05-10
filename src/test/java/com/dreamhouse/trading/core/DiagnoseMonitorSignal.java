package com.dreamhouse.trading.core;

import com.dreamhouse.trading.core.decision.DecisionConfig;
import com.dreamhouse.trading.core.decision.DecisionEngine;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.strategies.DayTradingStrategy;
import com.dreamhouse.trading.core.backtest.Portfolio;
import com.dreamhouse.trading.core.model.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 診斷監控信號問題
 *
 * 用途：
 * 1. 測試是否能獲取 K 線數據
 * 2. 檢查 K 線是否完成
 * 3. 測試策略是否產生信號
 * 4. 顯示 RSI 指標值
 */
public class DiagnoseMonitorSignal {

    public static void main(String[] args) {
        // 設置控制台編碼為 UTF-8
        try {
            System.setProperty("file.encoding", "UTF-8");
        } catch (Exception e) {
            // Ignore
        }

        String symbol = "3706.TW";  // 預設測試商品

        if (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            symbol = args[0].trim();
        }

        System.out.println("========================================");
        System.out.println("Diagnose Monitor Signal Issue");
        System.out.println("Testing Symbol: " + symbol);
        System.out.println("========================================\n");

        try {
            // 1. Create data source
            System.out.println("Step 1: Creating data source...");
            DataSourceManager manager = new DataSourceManager();
            MarketDataFeed dataFeed = manager.getCurrentDataSource();
            dataFeed.start();
            System.out.println("OK Data source created: " + dataFeed.getClass().getSimpleName() + "\n");

            // 2. Fetch K-line data
            System.out.println("Step 2: Fetching K-line data...");
            Timeframe timeframe = Timeframe.M5;
            int barCount = 100;

            List<Bar> bars = dataFeed.fetchHistoricalBars(symbol, timeframe, barCount);

            if (bars == null || bars.isEmpty()) {
                System.out.println("❌ 無法獲取 K 線數據！");
                System.out.println("\n可能原因：");
                System.out.println("1. 商品代號錯誤");
                System.out.println("2. 數據源沒有該商品的數據");
                System.out.println("3. 資料庫中沒有數據");
                return;
            }

            System.out.println("✅ 獲取到 " + bars.size() + " 根 K 線");

            // 顯示前 3 根和後 3 根
            System.out.println("\n前 3 根 K 線:");
            for (int i = 0; i < Math.min(3, bars.size()); i++) {
                Bar bar = bars.get(i);
                System.out.println(String.format("  [%d] %s | O:%.2f H:%.2f L:%.2f C:%.2f V:%d",
                    i, bar.getTimestamp(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), bar.getVolume()));
            }

            System.out.println("\n後 3 根 K 線:");
            for (int i = Math.max(0, bars.size() - 3); i < bars.size(); i++) {
                Bar bar = bars.get(i);
                System.out.println(String.format("  [%d] %s | O:%.2f H:%.2f L:%.2f C:%.2f V:%d",
                    i, bar.getTimestamp(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), bar.getVolume()));
            }

            // 3. 檢查 K 線是否完成
            System.out.println("\n步驟 3: 檢查最後一根 K 線是否完成...");
            Bar lastBar = bars.get(bars.size() - 1);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime barEnd = lastBar.getTimestamp().plusMinutes(timeframe.getMinutes());
            boolean isComplete = !now.isBefore(barEnd);

            System.out.println("  最後一根 K 線時間: " + lastBar.getTimestamp());
            System.out.println("  K 線結束時間: " + barEnd);
            System.out.println("  當前時間: " + now);
            System.out.println("  是否完成: " + (isComplete ? "✅ 是" : "❌ 否"));

            if (!isComplete) {
                System.out.println("\n⚠️ 最後一根 K 線未完成，監控系統會跳過此 K 線！");
                System.out.println("解決方案：");
                System.out.println("1. 等待 K 線完成後再測試");
                System.out.println("2. 或使用歷史數據測試（非今日數據）\n");
            }

            // 4. 轉換為 ta4j BarSeries
            System.out.println("\n步驟 4: 轉換為 ta4j BarSeries...");

            // 排除最後一根（模擬監控邏輯）
            List<Bar> historyBars = bars.subList(0, bars.size() - 1);
            BarSeries series = convertToBarSeries(symbol, historyBars);

            System.out.println("✅ 轉換成功，共 " + series.getBarCount() + " 根 K 線\n");

            // 5. 計算 RSI 指標
            System.out.println("步驟 5: 計算 RSI(5) 指標...");
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            RSIIndicator rsi = new RSIIndicator(closePrice, 5);

            System.out.println("最後 10 根 K 線的 RSI(5) 值:");
            int start = Math.max(0, series.getBarCount() - 10);
            for (int i = start; i < series.getBarCount(); i++) {
                double rsiValue = rsi.getValue(i).doubleValue();
                String signal = "";
                if (rsiValue < 40) {
                    signal = " ← 超賣（買入信號）";
                } else if (rsiValue > 60) {
                    signal = " ← 超買（賣出信號）";
                }
                System.out.println(String.format("  [%d] RSI: %.2f%s", i, rsiValue, signal));
            }

            // 6. 測試決策引擎
            System.out.println("\n步驟 6: 測試決策引擎...");
            DecisionConfig config = DayTradingStrategy.createDayTradingConfig();
            Portfolio portfolio = new Portfolio(100000.0);
            DecisionEngine engine = new DecisionEngine(config, portfolio);

            engine.setSymbol(symbol);
            engine.setBarSeries(series, timeframe);

            // 處理最後一根 K 線（轉換為 ta4j Bar）
            org.ta4j.core.Bar ta4jBar = convertToTa4jBar(lastBar);
            DecisionResult result = engine.onBar(ta4jBar);

            if (result == null) {
                System.out.println("❌ 決策引擎沒有產生信號");
            } else {
                System.out.println("✅ 決策引擎產生信號:");
                System.out.println("  動作: " + result.getAction().getDisplayName());
                System.out.println("  來源: " + result.getSource().getDisplayName());
                System.out.println("  原因: " + result.getReason());
                System.out.println("  置信度: " + String.format("%.1f%%", result.getConfidence() * 100));

                if (result.shouldTrade()) {
                    System.out.println("\n🔔 這是一個有效的交易信號！");
                } else {
                    System.out.println("\n⏸ 這不是交易信號（HOLD 或 NO_ACTION）");
                }
            }

            // 7. 總結
            System.out.println("\n========================================");
            System.out.println("診斷總結");
            System.out.println("========================================");
            System.out.println("✅ K 線數據: " + bars.size() + " 根");
            System.out.println((isComplete ? "✅" : "⚠️") + " K 線完成: " + (isComplete ? "是" : "否"));
            System.out.println((result != null && result.shouldTrade() ? "✅" : "❌") +
                " 交易信號: " + (result != null && result.shouldTrade() ? "有" : "無"));

            if (!isComplete) {
                System.out.println("\n⚠️ 問題：最後一根 K 線未完成");
                System.out.println("解決方案：等待 K 線完成或使用歷史數據測試");
            } else if (result == null || !result.shouldTrade()) {
                System.out.println("\n❌ 問題：策略條件不滿足");
                System.out.println("可能原因：");
                System.out.println("1. RSI(5) 未達到超賣（<40）或超買（>60）");
                System.out.println("2. 投票閾值未達到（需要 >= 0.3）");
                System.out.println("3. 風險管理限制");
            } else {
                System.out.println("\n✅ 一切正常！應該會產生信號。");
            }

            dataFeed.stop();

        } catch (Exception e) {
            System.err.println("❌ 發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static BarSeries convertToBarSeries(String symbol, List<Bar> bars) {
        BarSeries series = new BaseBarSeries(symbol);
        for (Bar bar : bars) {
            org.ta4j.core.Bar ta4jBar = convertToTa4jBar(bar);
            series.addBar(ta4jBar);
        }
        return series;
    }

    private static org.ta4j.core.Bar convertToTa4jBar(Bar bar) {
        ZonedDateTime time = bar.getTimestamp().atZone(ZoneId.systemDefault());
        return BaseBar.builder()
            .timePeriod(Duration.ofMinutes(5))
            .endTime(time)
            .openPrice(DecimalNum.valueOf(bar.getOpen()))
            .highPrice(DecimalNum.valueOf(bar.getHigh()))
            .lowPrice(DecimalNum.valueOf(bar.getLow()))
            .closePrice(DecimalNum.valueOf(bar.getClose()))
            .volume(DecimalNum.valueOf(bar.getVolume()))
            .build();
    }
}
