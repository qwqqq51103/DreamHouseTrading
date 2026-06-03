package com.dreamhouse.trading.core.logging;

import com.dreamhouse.trading.core.Timeframe;
import com.dreamhouse.trading.core.decision.DecisionResult;
import com.dreamhouse.trading.core.decision.classifier.TradeMode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Semantic trade record used by paper trade, replay, backtest, and log export flows.
 */
public class TradeRecord {

    private final String tradeId;
    private final String symbol;
    private final TradeMode tradeMode;
    private final Timeframe timeframe;
    private final DecisionResult.DecisionSource decisionSource;
    private final boolean autoManaged;

    private final LocalDateTime entryTime;
    private final double entryPrice;
    private final int quantity;
    private final double entryCommission;
    private final int entryBarIndex;

    private final LocalDateTime exitTime;
    private final double exitPrice;
    private final double exitCommission;
    private final ExitReason exitReason;
    private final int exitBarIndex;

    private final Double stopLoss;
    private final Double takeProfit;
    private final Double trailingStop;

    private final double grossProfit;
    private final double commission;
    private final double tax;
    private final double slippageCost;
    private final double netProfit;
    private final double returnPercent;
    private final double mae;
    private final double mfe;

    private final int holdingBars;
    private final long holdingMinutes;
    private final long holdingDays;

    private final String strategyName;
    private final String notes;
    private final String entryReason;
    private final String exitReasonText;
    private final String blockReason;
    private final double setupScore;
    private final String radarScoreComponents;
    private final String strategySettingSummary;

    private TradeRecord(Builder builder) {
        this.tradeId = builder.tradeId;
        this.symbol = builder.symbol;
        this.tradeMode = builder.tradeMode;
        this.timeframe = builder.timeframe;
        this.decisionSource = builder.decisionSource;
        this.autoManaged = builder.autoManaged;
        this.entryTime = builder.entryTime;
        this.entryPrice = builder.entryPrice;
        this.quantity = builder.quantity;
        this.entryCommission = builder.entryCommission;
        this.entryBarIndex = builder.entryBarIndex;
        this.exitTime = builder.exitTime;
        this.exitPrice = builder.exitPrice;
        this.exitCommission = builder.exitCommission;
        this.exitReason = builder.exitReason;
        this.exitBarIndex = builder.exitBarIndex;
        this.stopLoss = builder.stopLoss;
        this.takeProfit = builder.takeProfit;
        this.trailingStop = builder.trailingStop;
        this.grossProfit = builder.grossProfit;
        this.commission = builder.commission;
        this.tax = builder.tax;
        this.slippageCost = builder.slippageCost;
        this.netProfit = builder.netProfit;
        this.returnPercent = builder.returnPercent;
        this.mae = builder.mae;
        this.mfe = builder.mfe;
        this.holdingBars = builder.holdingBars;
        this.holdingMinutes = builder.holdingMinutes;
        this.holdingDays = builder.holdingDays;
        this.strategyName = builder.strategyName;
        this.notes = builder.notes;
        this.entryReason = builder.entryReason;
        this.exitReasonText = builder.exitReasonText;
        this.blockReason = builder.blockReason;
        this.setupScore = builder.setupScore;
        this.radarScoreComponents = builder.radarScoreComponents;
        this.strategySettingSummary = builder.strategySettingSummary;
    }

    public String getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public TradeMode getTradeMode() { return tradeMode; }
    public Timeframe getTimeframe() { return timeframe; }
    public DecisionResult.DecisionSource getDecisionSource() { return decisionSource; }
    public boolean isAutoManaged() { return autoManaged; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public double getEntryPrice() { return entryPrice; }
    public int getQuantity() { return quantity; }
    public double getEntryCommission() { return entryCommission; }
    public int getEntryBarIndex() { return entryBarIndex; }
    public LocalDateTime getExitTime() { return exitTime; }
    public double getExitPrice() { return exitPrice; }
    public double getExitCommission() { return exitCommission; }
    public ExitReason getExitReason() { return exitReason; }
    public int getExitBarIndex() { return exitBarIndex; }
    public Double getStopLoss() { return stopLoss; }
    public Double getTakeProfit() { return takeProfit; }
    public Double getTrailingStop() { return trailingStop; }
    public double getGrossProfit() { return grossProfit; }
    public double getCommission() { return commission; }
    public double getTax() { return tax; }
    public double getSlippageCost() { return slippageCost; }
    public double getNetProfit() { return netProfit; }
    public double getReturnPercent() { return returnPercent; }
    public double getMae() { return mae; }
    public double getMfe() { return mfe; }
    public int getHoldingBars() { return holdingBars; }
    public long getHoldingMinutes() { return holdingMinutes; }
    public long getHoldingDays() { return holdingDays; }
    public String getStrategyName() { return strategyName; }
    public String getNotes() { return notes; }
    public String getEntryReason() { return entryReason; }
    public String getExitReasonText() { return exitReasonText; }
    public String getBlockReason() { return blockReason; }
    public double getSetupScore() { return setupScore; }
    public String getRadarScoreComponents() { return radarScoreComponents; }
    public String getStrategySettingSummary() { return strategySettingSummary; }

    public boolean isProfitable() {
        return netProfit > 0;
    }

    public double getRiskRewardRatio() {
        if (stopLoss == null || stopLoss == 0) {
            return 0.0;
        }
        double risk = Math.abs(entryPrice - stopLoss) * quantity;
        if (risk == 0) {
            return 0.0;
        }
        return netProfit / risk;
    }

    @Override
    public String toString() {
        return String.format("TradeRecord[%s %s %s: Entry@%.2f Exit@%.2f P/L=%.2f (%.2f%%) Reason=%s]",
                tradeId, symbol, tradeMode.getShortCode(),
                entryPrice, exitPrice, netProfit, returnPercent * 100,
                exitReason.getShortCode());
    }

    public static class Builder {
        private final String tradeId;
        private final String symbol;
        private TradeMode tradeMode = TradeMode.NO_TRADE;
        private Timeframe timeframe;
        private DecisionResult.DecisionSource decisionSource = DecisionResult.DecisionSource.MANUAL;
        private boolean autoManaged;
        private LocalDateTime entryTime;
        private double entryPrice;
        private int quantity;
        private LocalDateTime exitTime;
        private double exitPrice;
        private ExitReason exitReason = ExitReason.OTHER;

        private double entryCommission = 0.0;
        private double exitCommission = 0.0;
        private int entryBarIndex = -1;
        private int exitBarIndex = -1;
        private Double stopLoss;
        private Double takeProfit;
        private Double trailingStop;
        private double grossProfit = 0.0;
        private double commission = 0.0;
        private double tax = 0.0;
        private double slippageCost = 0.0;
        private double netProfit = 0.0;
        private double returnPercent = 0.0;
        private double mae = 0.0;
        private double mfe = 0.0;
        private int holdingBars = 0;
        private long holdingMinutes = 0;
        private long holdingDays = 0;
        private String strategyName = "";
        private String notes = "";
        private String entryReason = "";
        private String exitReasonText = "";
        private String blockReason = "";
        private double setupScore = 0.0;
        private String radarScoreComponents = "";
        private String strategySettingSummary = "";

        public Builder(String tradeId, String symbol) {
            this.tradeId = tradeId;
            this.symbol = symbol;
        }

        public Builder tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode != null ? tradeMode : TradeMode.NO_TRADE;
            return this;
        }

        public Builder timeframe(Timeframe timeframe) {
            this.timeframe = timeframe;
            return this;
        }

        public Builder decisionSource(DecisionResult.DecisionSource decisionSource) {
            this.decisionSource = decisionSource != null ? decisionSource : DecisionResult.DecisionSource.MANUAL;
            return this;
        }

        public Builder autoManaged(boolean autoManaged) {
            this.autoManaged = autoManaged;
            return this;
        }

        public Builder entryTime(LocalDateTime entryTime) {
            this.entryTime = entryTime;
            return this;
        }

        public Builder entryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder entryCommission(double entryCommission) {
            this.entryCommission = entryCommission;
            return this;
        }

        public Builder entryBarIndex(int entryBarIndex) {
            this.entryBarIndex = entryBarIndex;
            return this;
        }

        public Builder exitTime(LocalDateTime exitTime) {
            this.exitTime = exitTime;
            return this;
        }

        public Builder exitPrice(double exitPrice) {
            this.exitPrice = exitPrice;
            return this;
        }

        public Builder exitCommission(double exitCommission) {
            this.exitCommission = exitCommission;
            return this;
        }

        public Builder exitReason(ExitReason exitReason) {
            this.exitReason = exitReason != null ? exitReason : ExitReason.OTHER;
            return this;
        }

        public Builder exitBarIndex(int exitBarIndex) {
            this.exitBarIndex = exitBarIndex;
            return this;
        }

        public Builder stopLoss(Double stopLoss) {
            this.stopLoss = stopLoss;
            return this;
        }

        public Builder takeProfit(Double takeProfit) {
            this.takeProfit = takeProfit;
            return this;
        }

        public Builder trailingStop(Double trailingStop) {
            this.trailingStop = trailingStop;
            return this;
        }

        public Builder grossProfit(double grossProfit) {
            this.grossProfit = grossProfit;
            return this;
        }

        public Builder commission(double commission) {
            this.commission = Math.max(0.0, commission);
            return this;
        }

        public Builder tax(double tax) {
            this.tax = Math.max(0.0, tax);
            return this;
        }

        public Builder slippageCost(double slippageCost) {
            this.slippageCost = Math.max(0.0, slippageCost);
            return this;
        }

        public Builder netProfit(double netProfit) {
            this.netProfit = netProfit;
            return this;
        }

        public Builder returnPercent(double returnPercent) {
            this.returnPercent = returnPercent;
            return this;
        }

        public Builder mae(double mae) {
            this.mae = mae;
            return this;
        }

        public Builder mfe(double mfe) {
            this.mfe = mfe;
            return this;
        }

        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName != null ? strategyName : "";
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes != null ? notes : "";
            return this;
        }

        public Builder entryReason(String entryReason) {
            this.entryReason = entryReason != null ? entryReason : "";
            return this;
        }

        public Builder exitReasonText(String exitReasonText) {
            this.exitReasonText = exitReasonText != null ? exitReasonText : "";
            return this;
        }

        public Builder blockReason(String blockReason) {
            this.blockReason = blockReason != null ? blockReason : "";
            return this;
        }

        public Builder setupScore(double setupScore) {
            this.setupScore = setupScore;
            return this;
        }

        public Builder radarScoreComponents(String radarScoreComponents) {
            this.radarScoreComponents = radarScoreComponents != null ? radarScoreComponents : "";
            return this;
        }

        public Builder strategySettingSummary(String strategySettingSummary) {
            this.strategySettingSummary = strategySettingSummary != null ? strategySettingSummary : "";
            return this;
        }

        public TradeRecord build() {
            if (grossProfit == 0.0 && entryPrice > 0 && exitPrice > 0) {
                grossProfit = (exitPrice - entryPrice) * quantity;
            }

            if (commission == 0.0) {
                commission = (entryPrice * quantity * entryCommission)
                        + (exitPrice * quantity * exitCommission);
            }

            if (netProfit == 0.0) {
                netProfit = grossProfit - commission - tax - slippageCost;
            }

            if (returnPercent == 0.0 && entryPrice > 0) {
                returnPercent = (exitPrice - entryPrice) / entryPrice;
            }

            if (holdingBars == 0 && entryBarIndex >= 0 && exitBarIndex >= 0) {
                holdingBars = exitBarIndex - entryBarIndex;
            }

            if (holdingMinutes == 0 && entryTime != null && exitTime != null) {
                holdingMinutes = ChronoUnit.MINUTES.between(entryTime, exitTime);
            }

            if (holdingDays == 0 && entryTime != null && exitTime != null) {
                holdingDays = ChronoUnit.DAYS.between(entryTime.toLocalDate(), exitTime.toLocalDate());
            }

            return new TradeRecord(this);
        }
    }
}
