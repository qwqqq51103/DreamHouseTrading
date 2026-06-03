package com.dreamhouse.trading.core.backtest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易統計分析器
 * 提供停損停利觸發率、出場原因分布、風險收益比等分析
 */
public class TradeStatisticsAnalyzer {
    
    private final List<Trade> trades;
    private final BacktestResult result;
    
    public TradeStatisticsAnalyzer(List<Trade> trades, BacktestResult result) {
        this.trades = new ArrayList<>(trades);
        this.result = result;
    }

    private void analyzeCostBreakdown(StatisticsReport report) {
        for (TradePair pair : pairTrades()) {
            report.grossProfit += (pair.sellTrade.getPrice() - pair.buyTrade.getPrice()) * pair.buyTrade.getQuantity();
            report.commission += pair.buyTrade.getCommissionAmount() + pair.sellTrade.getCommissionAmount();
            report.tax += pair.buyTrade.getTaxAmount() + pair.sellTrade.getTaxAmount();
            report.slippageCost += pair.buyTrade.getSlippageCost() + pair.sellTrade.getSlippageCost();
            report.netProfit += pair.sellTrade.getNetProceeds() - pair.buyTrade.getTotalCost();
        }
    }

    private void analyzeBlockReasons(StatisticsReport report) {
        if (result != null) {
            report.blockReasonDistribution = result.getBlockReasonStatistics();
        }
    }

    /**
     * 生成完整的統計報告
     */
    public StatisticsReport generateReport() {
        StatisticsReport report = new StatisticsReport();
        
        // 基本統計
        report.totalTrades = trades.size();
        report.buyTrades = (int) trades.stream().filter(t -> t.getType() == TradeType.BUY).count();
        report.sellTrades = (int) trades.stream().filter(t -> t.getType() == TradeType.SELL).count();
        
        // 分析停損停利
        analyzeStopLossAndTakeProfit(report);
        
        // 分析出場原因
        analyzeExitReasons(report);
        
        // 計算風險收益比
        calculateRiskRewardRatio(report);
        analyzeCostBreakdown(report);
        analyzeBlockReasons(report);

        // 生成優化建議
        generateOptimizationSuggestions(report);
        
        return report;
    }
    
    /**
     * 分析停損停利觸發情況
     */
    private void analyzeStopLossAndTakeProfit(StatisticsReport report) {
        List<TradePair> pairs = pairTrades();
        
        report.completedPairs = pairs.size();
        
        for (TradePair pair : pairs) {
            Trade buy = pair.buyTrade;
            Trade sell = pair.sellTrade;
            
            if (buy.getStopLoss() == null || buy.getTakeProfit() == null) {
                continue;
            }
            
            double profit = sell.getPrice() - buy.getPrice();
            double profitPercent = profit / buy.getPrice();
            
            // 判斷是否觸發停損
            if (sell.getPrice() <= buy.getStopLoss() * 1.01) { // 允許 1% 誤差
                report.stopLossTriggered++;
                report.stopLossProfits.add(profitPercent);
            }
            // 判斷是否觸發停利
            else if (sell.getPrice() >= buy.getTakeProfit() * 0.99) { // 允許 1% 誤差
                report.takeProfitTriggered++;
                report.takeProfitProfits.add(profitPercent);
            }
            // 其他出場
            else {
                report.otherExits++;
                report.otherExitProfits.add(profitPercent);
            }
            
            // 記錄實際價格與停損停利的距離
            double stopLossDistance = (sell.getPrice() - buy.getStopLoss()) / buy.getPrice();
            double takeProfitDistance = (buy.getTakeProfit() - sell.getPrice()) / buy.getPrice();
            
            report.stopLossDistances.add(stopLossDistance);
            report.takeProfitDistances.add(takeProfitDistance);
        }
        
        // 計算觸發率
        if (report.completedPairs > 0) {
            report.stopLossRate = (double) report.stopLossTriggered / report.completedPairs;
            report.takeProfitRate = (double) report.takeProfitTriggered / report.completedPairs;
            report.otherExitRate = (double) report.otherExits / report.completedPairs;
        }
    }
    
    /**
     * 分析出場原因分布
     */
    private void analyzeExitReasons(StatisticsReport report) {
        Map<String, Integer> reasonCount = new HashMap<>();
        Map<String, Double> reasonProfit = new HashMap<>();
        
        List<TradePair> pairs = pairTrades();
        
        for (TradePair pair : pairs) {
            String reason = pair.sellTrade.getExitReason();
            if (reason == null || reason.equals("-")) {
                reason = "未指定原因";
            }
            
            reasonCount.put(reason, reasonCount.getOrDefault(reason, 0) + 1);
            
            double profit = pair.sellTrade.getPrice() - pair.buyTrade.getPrice();
            reasonProfit.put(reason, reasonProfit.getOrDefault(reason, 0.0) + profit);
        }
        
        // 轉換為列表並排序
        report.exitReasonDistribution = reasonCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        
        report.exitReasonProfits = reasonProfit;
    }
    
    /**
     * 計算風險收益比
     */
    private void calculateRiskRewardRatio(StatisticsReport report) {
        List<TradePair> pairs = pairTrades();
        
        for (TradePair pair : pairs) {
            Trade buy = pair.buyTrade;
            Trade sell = pair.sellTrade;
            
            if (buy.getStopLoss() == null || buy.getTakeProfit() == null) {
                continue;
            }
            
            double risk = buy.getPrice() - buy.getStopLoss();
            double reward = buy.getTakeProfit() - buy.getPrice();
            
            if (risk > 0) {
                double ratio = reward / risk;
                report.riskRewardRatios.add(ratio);
            }
            
            // 實際風險收益比
            double actualProfit = sell.getPrice() - buy.getPrice();
            if (risk > 0) {
                double actualRatio = actualProfit / risk;
                report.actualRiskRewardRatios.add(actualRatio);
            }
        }
        
        // 計算平均值
        if (!report.riskRewardRatios.isEmpty()) {
            report.avgPlannedRiskReward = report.riskRewardRatios.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        if (!report.actualRiskRewardRatios.isEmpty()) {
            report.avgActualRiskReward = report.actualRiskRewardRatios.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
    
    /**
     * 生成優化建議
     */
    private void generateOptimizationSuggestions(StatisticsReport report) {
        List<String> suggestions = new ArrayList<>();
        
        // 1. 停損觸發率分析
        if (report.stopLossRate > 0.6) {
            suggestions.add("⚠️ 停損觸發率過高 (" + String.format("%.1f%%", report.stopLossRate * 100) + 
                          ")，建議放寬停損幅度或改進入場時機");
        } else if (report.stopLossRate < 0.1) {
            suggestions.add("✓ 停損觸發率較低 (" + String.format("%.1f%%", report.stopLossRate * 100) + 
                          ")，策略風險控制良好");
        }
        
        // 2. 停利達成率分析
        if (report.takeProfitRate < 0.2) {
            suggestions.add("⚠️ 停利達成率較低 (" + String.format("%.1f%%", report.takeProfitRate * 100) + 
                          ")，建議降低停利目標或優化出場策略");
        } else if (report.takeProfitRate > 0.4) {
            suggestions.add("✓ 停利達成率良好 (" + String.format("%.1f%%", report.takeProfitRate * 100) + 
                          ")，可考慮提高停利目標以獲取更大利潤");
        }
        
        // 3. 風險收益比分析
        if (report.avgPlannedRiskReward < 1.5) {
            suggestions.add("⚠️ 計劃風險收益比偏低 (" + String.format("%.2f", report.avgPlannedRiskReward) + 
                          ")，建議調整停損停利比例至少達到 1:2");
        } else if (report.avgPlannedRiskReward > 3.0) {
            suggestions.add("✓ 風險收益比設定合理 (" + String.format("%.2f", report.avgPlannedRiskReward) + 
                          ")，保持良好的風險管理");
        }
        
        // 4. 實際 vs 計劃風險收益比
        if (report.avgActualRiskReward < report.avgPlannedRiskReward * 0.5) {
            suggestions.add("⚠️ 實際風險收益比 (" + String.format("%.2f", report.avgActualRiskReward) + 
                          ") 遠低於計劃 (" + String.format("%.2f", report.avgPlannedRiskReward) + 
                          ")，建議檢討出場邏輯");
        }
        
        // 5. 出場原因分析
        if (!report.exitReasonDistribution.isEmpty()) {
            String topReason = report.exitReasonDistribution.keySet().iterator().next();
            int topCount = report.exitReasonDistribution.get(topReason);
            double topRatio = (double) topCount / report.completedPairs;
            
            if (topRatio > 0.7) {
                suggestions.add("⚠️ 出場原因過於集中在「" + topReason + "」(" + 
                              String.format("%.1f%%", topRatio * 100) + 
                              ")，建議多樣化出場條件");
            }
        }
        
        // 6. 整體績效建議
        if (result != null) {
            double winRate = result.getWinRate();
            if (winRate < 0.4 && report.avgPlannedRiskReward < 2.0) {
                suggestions.add("⚠️ 勝率偏低 (" + String.format("%.1f%%", winRate * 100) + 
                              ") 且風險收益比不足，建議提高風險收益比至 1:3 以上");
            }
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("✓ 策略整體表現良好，繼續保持!");
        }
        
        report.optimizationSuggestions = suggestions;
    }
    
    /**
     * 配對買賣交易
     */
    private List<TradePair> pairTrades() {
        List<TradePair> pairs = new ArrayList<>();
        Trade lastBuy = null;
        
        for (Trade trade : trades) {
            if (trade.getType() == TradeType.BUY) {
                lastBuy = trade;
            } else if (trade.getType() == TradeType.SELL && lastBuy != null) {
                pairs.add(new TradePair(lastBuy, trade));
                lastBuy = null;
            }
        }
        
        return pairs;
    }
    
    /**
     * 交易對
     */
    private static class TradePair {
        final Trade buyTrade;
        final Trade sellTrade;
        
        TradePair(Trade buyTrade, Trade sellTrade) {
            this.buyTrade = buyTrade;
            this.sellTrade = sellTrade;
        }
    }
    
    /**
     * 統計報告
     */
    public static class StatisticsReport {
        // 基本統計
        public int totalTrades;
        public int buyTrades;
        public int sellTrades;
        public int completedPairs;
        
        // 停損停利統計
        public int stopLossTriggered;
        public int takeProfitTriggered;
        public int otherExits;
        
        public double stopLossRate;
        public double takeProfitRate;
        public double otherExitRate;
        
        public List<Double> stopLossProfits = new ArrayList<>();
        public List<Double> takeProfitProfits = new ArrayList<>();
        public List<Double> otherExitProfits = new ArrayList<>();
        
        public List<Double> stopLossDistances = new ArrayList<>();
        public List<Double> takeProfitDistances = new ArrayList<>();
        
        // 出場原因分布
        public Map<String, Integer> exitReasonDistribution = new LinkedHashMap<>();
        public Map<String, Double> exitReasonProfits = new HashMap<>();
        public Map<String, Long> blockReasonDistribution = new LinkedHashMap<>();
        public double grossProfit;
        public double commission;
        public double tax;
        public double slippageCost;
        public double netProfit;
        
        // 風險收益比
        public List<Double> riskRewardRatios = new ArrayList<>();
        public List<Double> actualRiskRewardRatios = new ArrayList<>();
        public double avgPlannedRiskReward;
        public double avgActualRiskReward;
        
        // 優化建議
        public List<String> optimizationSuggestions = new ArrayList<>();
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== 交易統計分析報告 ===\n\n");
            
            sb.append("【基本統計】\n");
            sb.append(String.format("總交易次數: %d (買入: %d, 賣出: %d)\n", 
                totalTrades, buyTrades, sellTrades));
            sb.append(String.format("完整交易對: %d\n\n", completedPairs));
            
            sb.append("【停損停利分析】\n");
            sb.append(String.format("停損觸發: %d 次 (%.1f%%)\n", 
                stopLossTriggered, stopLossRate * 100));
            sb.append(String.format("停利觸發: %d 次 (%.1f%%)\n", 
                takeProfitTriggered, takeProfitRate * 100));
            sb.append(String.format("其他出場: %d 次 (%.1f%%)\n\n", 
                otherExits, otherExitRate * 100));
            
            sb.append("【風險收益比】\n");
            sb.append(String.format("計劃風險收益比: %.2f\n", avgPlannedRiskReward));
            sb.append(String.format("實際風險收益比: %.2f\n\n", avgActualRiskReward));
            
            sb.append("【出場原因分布】\n");
            exitReasonDistribution.forEach((reason, count) -> {
                double ratio = (double) count / completedPairs * 100;
                double avgProfit = exitReasonProfits.getOrDefault(reason, 0.0) / count;
                sb.append(String.format("- %s: %d 次 (%.1f%%), 平均盈虧: %.2f\n", 
                    reason, count, ratio, avgProfit));
            });
            
            sb.append("\n【優化建議】\n");
            optimizationSuggestions.forEach(s -> sb.append(s).append("\n"));
            
            return sb.toString();
        }
    }
}



