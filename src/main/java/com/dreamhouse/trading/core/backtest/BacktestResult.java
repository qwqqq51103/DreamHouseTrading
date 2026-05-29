package com.dreamhouse.trading.core.backtest;

import com.dreamhouse.trading.core.scanner.RadarScoreComponent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回測結果類
 * 包含回測的所有統計數據和績效指標
 */
public class BacktestResult {
    
    // 基本信息
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final double initialCapital;
    
    // 績效數據
    private double finalValue;
    private double totalReturn;
    private double annualizedReturn;
    private double maxDrawdown;
    private double sharpeRatio;
    private double volatility;
    
    // 交易統計
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private double avgWin;
    private double avgLoss;
    private double profitFactor;
    
    // 詳細記錄
    private final List<Trade> trades;
    private final List<PortfolioSnapshot> snapshots;
    private final List<SignalObservation> signalObservations;
    private final List<WarmupDiagnostic> warmupDiagnostics;
    
    /**
     * 構造函數
     */
    public BacktestResult(LocalDateTime startDate, LocalDateTime endDate, double initialCapital) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialCapital = initialCapital;
        this.trades = new ArrayList<>();
        this.snapshots = new ArrayList<>();
        this.signalObservations = new ArrayList<>();
        this.warmupDiagnostics = new ArrayList<>();
    }
    
    /**
     * 添加交易記錄
     */
    public void addTrade(Trade trade) {
        trades.add(trade);
    }
    
    /**
     * 添加投資組合快照
     */
    public void addSnapshot(LocalDateTime timestamp, double totalValue, double cash, 
                           double positionValue, int positionCount) {
        snapshots.add(new PortfolioSnapshot(timestamp, totalValue, cash, positionValue, positionCount));
    }

    public void addSignalObservation(SignalObservation observation) {
        if (observation != null) {
            signalObservations.add(observation);
        }
    }

    public void addWarmupDiagnostic(WarmupDiagnostic diagnostic) {
        if (diagnostic != null) {
            warmupDiagnostics.add(diagnostic);
        }
    }
    
    /**
     * 計算所有績效指標
     */
    public void calculate() {
        if (snapshots.isEmpty()) {
            return;
        }
        
        // 基本績效
        finalValue = snapshots.get(snapshots.size() - 1).getTotalValue();
        totalReturn = (finalValue - initialCapital) / initialCapital;
        
        // 計算年化收益率
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (days > 0) {
            double years = days / 365.25;
            annualizedReturn = Math.pow(1 + totalReturn, 1.0 / years) - 1;
        }
        
        // 計算最大回撤
        calculateMaxDrawdown();
        
        // 計算波動率
        calculateVolatility();
        
        // 計算夏普比率
        calculateSharpeRatio();
        
        // 計算交易統計
        calculateTradeStatistics();
    }
    
    /**
     * 計算最大回撤
     */
    private void calculateMaxDrawdown() {
        double peak = initialCapital;
        maxDrawdown = 0.0;
        
        for (PortfolioSnapshot snapshot : snapshots) {
            if (snapshot.getTotalValue() > peak) {
                peak = snapshot.getTotalValue();
            }
            
            double drawdown = (peak - snapshot.getTotalValue()) / peak;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }
    }
    
    /**
     * 計算波動率
     */
    private void calculateVolatility() {
        if (snapshots.size() < 2) {
            volatility = 0.0;
            return;
        }
        
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            double prevValue = snapshots.get(i - 1).getTotalValue();
            double currValue = snapshots.get(i).getTotalValue();
            double dailyReturn = (currValue - prevValue) / prevValue;
            returns.add(dailyReturn);
        }
        
        // 計算標準差
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average().orElse(0.0);
        
        volatility = Math.sqrt(variance) * Math.sqrt(252); // 年化波動率
    }
    
    /**
     * 計算夏普比率
     */
    private void calculateSharpeRatio() {
        double riskFreeRate = 0.02; // 假設無風險利率為2%
        if (volatility > 0) {
            sharpeRatio = (annualizedReturn - riskFreeRate) / volatility;
        } else {
            sharpeRatio = 0.0;
        }
    }
    
    /**
     * 計算交易統計
     * 將買賣配對，計算每一輪交易的盈虧
     */
    private void calculateTradeStatistics() {
        totalTrades = trades.size();
        winningTrades = 0;
        losingTrades = 0;
        
        double totalWinAmount = 0.0;
        double totalLossAmount = 0.0;
        
        // 配對買賣交易，計算盈虧
        Trade lastBuy = null;
        for (Trade trade : trades) {
            if (trade.getType() == TradeType.BUY) {
                lastBuy = trade;
            } else if (trade.getType() == TradeType.SELL && lastBuy != null) {
                // 使用 Trade 類的方法計算成本和收入
                double buyTotal = lastBuy.getTotalCost();      // 買入總成本（含手續費）
                double sellTotal = trade.getNetProceeds();     // 賣出淨收入（扣除手續費）
                
                double profit = sellTotal - buyTotal;
                
                if (profit > 0) {
                    winningTrades++;
                    totalWinAmount += profit;
                } else if (profit < 0) {
                    losingTrades++;
                    totalLossAmount += Math.abs(profit);
                }
                
                // 調試輸出
                if (totalTrades <= 30) { // 只輸出前30筆
                    System.out.printf("交易配對: 買入%.2f (成本:%.2f) -> 賣出%.2f (收入:%.2f) = 盈虧:%.2f%n",
                                     lastBuy.getPrice(), buyTotal, 
                                     trade.getPrice(), sellTotal, profit);
                }
                
                lastBuy = null; // 重置，準備下一輪交易
            }
        }
        
        // 計算統計指標
        int completedTrades = winningTrades + losingTrades;
        winRate = completedTrades > 0 ? (double) winningTrades / completedTrades : 0.0;
        avgWin = winningTrades > 0 ? totalWinAmount / winningTrades : 0.0;
        avgLoss = losingTrades > 0 ? totalLossAmount / losingTrades : 0.0;
        profitFactor = totalLossAmount > 0 ? totalWinAmount / totalLossAmount : 0.0;
    }
    
    /**
     * 獲取收益曲線數據
     */
    public List<Double> getEquityCurve() {
        return snapshots.stream()
                .map(PortfolioSnapshot::getTotalValue)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 獲取回撤曲線數據
     */
    public List<Double> getDrawdownCurve() {
        List<Double> drawdowns = new ArrayList<>();
        double peak = initialCapital;
        
        for (PortfolioSnapshot snapshot : snapshots) {
            if (snapshot.getTotalValue() > peak) {
                peak = snapshot.getTotalValue();
            }
            
            double drawdown = (peak - snapshot.getTotalValue()) / peak;
            drawdowns.add(drawdown);
        }
        
        return drawdowns;
    }
    
    /**
     * 生成報告摘要
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 回測結果摘要 ===\n");
        sb.append(String.format("回測期間: %s 至 %s\n", startDate, endDate));
        sb.append(String.format("初始資金: $%.2f\n", initialCapital));
        sb.append(String.format("最終價值: $%.2f\n", finalValue));
        sb.append(String.format("總收益率: %.2f%%\n", totalReturn * 100));
        sb.append(String.format("年化收益率: %.2f%%\n", annualizedReturn * 100));
        sb.append(String.format("最大回撤: %.2f%%\n", maxDrawdown * 100));
        sb.append(String.format("夏普比率: %.2f\n", sharpeRatio));
        sb.append(String.format("波動率: %.2f%%\n", volatility * 100));
        sb.append(String.format("總交易次數: %d\n", totalTrades));
        sb.append(String.format("勝率: %.2f%%\n", winRate * 100));
        sb.append(String.format("盈虧比: %.2f\n", profitFactor));
        
        return sb.toString();
    }
    
    // Getters
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public double getInitialCapital() { return initialCapital; }
    public double getFinalValue() { return finalValue; }
    public double getTotalReturn() { return totalReturn; }
    public double getAnnualizedReturn() { return annualizedReturn; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public double getSharpeRatio() { return sharpeRatio; }
    public double getVolatility() { return volatility; }
    public int getTotalTrades() { return totalTrades; }
    public int getWinningTrades() { return winningTrades; }
    public int getLosingTrades() { return losingTrades; }
    public double getWinRate() { return winRate; }
    public double getAvgWin() { return avgWin; }
    public double getAvgLoss() { return avgLoss; }
    public double getProfitFactor() { return profitFactor; }
    public List<Trade> getTrades() { return new ArrayList<>(trades); }
    public List<PortfolioSnapshot> getSnapshots() { return new ArrayList<>(snapshots); }
    public List<SignalObservation> getSignalObservations() { return new ArrayList<>(signalObservations); }
    public List<WarmupDiagnostic> getWarmupDiagnostics() { return new ArrayList<>(warmupDiagnostics); }

    public record WarmupDiagnostic(
            String symbol,
            LocalDate sessionDate,
            String timeframe,
            boolean enabled,
            int requestedBars,
            int loadedBars,
            LocalDateTime firstWarmupTime,
            LocalDateTime lastWarmupTime) {
    }

    public record SignalObservation(
            String symbol,
            LocalDateTime timestamp,
            String action,
            boolean blocked,
            String reason,
            double score,
            String marketDecision,
            String marketRegime,
            String internalMarketState,
            String industry,
            Double watchlistRankPercent,
            Double vwap,
            Double vwapSlopePercent,
            Boolean volumeSustain,
            Double relativeToBenchmarkPercent,
            Double relativeToIndustryPercent,
            List<RadarScoreComponent> scoreComponentDetails,
            String scoreComponents,
            String longBonusComponents,
            double maxFavorablePercent,
            double maxAdversePercent,
            double closeReturnPercent) {
        public SignalObservation {
            scoreComponentDetails = scoreComponentDetails != null
                    ? List.copyOf(scoreComponentDetails)
                    : List.of();
        }
    }
}
