package com.dreamhouse.trading.core.logging;

import com.dreamhouse.trading.core.decision.classifier.TradeMode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 增強的交易記錄
 * 包含完整的交易資訊，用於詳細分析和匯出
 */
public class TradeRecord {

    // ========== 基本資訊 ==========
    private final String tradeId;              // 交易ID
    private final String symbol;               // 商品代碼
    private final TradeMode tradeMode;         // 交易模式（DAY/SHORT/SWING）

    // ========== 進場資訊 ==========
    private final LocalDateTime entryTime;     // 進場時間
    private final double entryPrice;           // 進場價格
    private final int quantity;                // 數量
    private final double entryCommission;      // 進場手續費
    private final int entryBarIndex;           // 進場 K 線索引

    // ========== 出場資訊 ==========
    private final LocalDateTime exitTime;      // 出場時間
    private final double exitPrice;            // 出場價格
    private final double exitCommission;       // 出場手續費
    private final ExitReason exitReason;       // 出場原因
    private final int exitBarIndex;            // 出場 K 線索引

    // ========== 停損停利設置 ==========
    private final Double stopLoss;             // 停損價格
    private final Double takeProfit;           // 停利價格
    private final Double trailingStop;         // 移動停損距離

    // ========== 績效指標 ==========
    private final double grossProfit;          // 毛利
    private final double netProfit;            // 淨利（扣除手續費）
    private final double returnPercent;        // 報酬率
    private final double mae;                  // 最大不利價差 (Maximum Adverse Excursion)
    private final double mfe;                  // 最大有利價差 (Maximum Favorable Excursion)

    // ========== 持倉時間 ==========
    private final int holdingBars;             // 持倉 K 線數量
    private final long holdingMinutes;         // 持倉分鐘數
    private final long holdingDays;            // 持倉天數

    // ========== 其他資訊 ==========
    private final String strategyName;         // 策略名稱
    private final String notes;                // 備註

    /**
     * 建構子（使用 Builder 模式）
     */
    private TradeRecord(Builder builder) {
        this.tradeId = builder.tradeId;
        this.symbol = builder.symbol;
        this.tradeMode = builder.tradeMode;
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
        this.netProfit = builder.netProfit;
        this.returnPercent = builder.returnPercent;
        this.mae = builder.mae;
        this.mfe = builder.mfe;
        this.holdingBars = builder.holdingBars;
        this.holdingMinutes = builder.holdingMinutes;
        this.holdingDays = builder.holdingDays;
        this.strategyName = builder.strategyName;
        this.notes = builder.notes;
    }

    // ========== Getters ==========

    public String getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public TradeMode getTradeMode() { return tradeMode; }
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
    public double getNetProfit() { return netProfit; }
    public double getReturnPercent() { return returnPercent; }
    public double getMae() { return mae; }
    public double getMfe() { return mfe; }
    public int getHoldingBars() { return holdingBars; }
    public long getHoldingMinutes() { return holdingMinutes; }
    public long getHoldingDays() { return holdingDays; }
    public String getStrategyName() { return strategyName; }
    public String getNotes() { return notes; }

    /**
     * 是否為獲利交易
     */
    public boolean isProfitable() {
        return netProfit > 0;
    }

    /**
     * 獲取風險報酬比
     */
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

    /**
     * Builder 類別
     */
    public static class Builder {
        // 必要欄位
        private String tradeId;
        private String symbol;
        private TradeMode tradeMode = TradeMode.NO_TRADE;
        private LocalDateTime entryTime;
        private double entryPrice;
        private int quantity;
        private LocalDateTime exitTime;
        private double exitPrice;
        private ExitReason exitReason = ExitReason.OTHER;

        // 可選欄位（有預設值）
        private double entryCommission = 0.0;
        private double exitCommission = 0.0;
        private int entryBarIndex = -1;
        private int exitBarIndex = -1;
        private Double stopLoss = null;
        private Double takeProfit = null;
        private Double trailingStop = null;
        private double grossProfit = 0.0;
        private double netProfit = 0.0;
        private double returnPercent = 0.0;
        private double mae = 0.0;
        private double mfe = 0.0;
        private int holdingBars = 0;
        private long holdingMinutes = 0;
        private long holdingDays = 0;
        private String strategyName = "";
        private String notes = "";

        public Builder(String tradeId, String symbol) {
            this.tradeId = tradeId;
            this.symbol = symbol;
        }

        public Builder tradeMode(TradeMode tradeMode) {
            this.tradeMode = tradeMode;
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
            this.exitReason = exitReason;
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

        public Builder mae(double mae) {
            this.mae = mae;
            return this;
        }

        public Builder mfe(double mfe) {
            this.mfe = mfe;
            return this;
        }

        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public TradeRecord build() {
            // 自動計算績效指標（如果未手動設置）
            if (grossProfit == 0.0 && entryPrice > 0 && exitPrice > 0) {
                grossProfit = (exitPrice - entryPrice) * quantity;
            }

            if (netProfit == 0.0) {
                double totalCommission = (entryPrice * quantity * entryCommission) +
                                       (exitPrice * quantity * exitCommission);
                netProfit = grossProfit - totalCommission;
            }

            if (returnPercent == 0.0 && entryPrice > 0) {
                returnPercent = (exitPrice - entryPrice) / entryPrice;
            }

            // 自動計算持倉時間（如果未手動設置）
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
