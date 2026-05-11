package com.dreamhouse.trading.core.backtest;

import java.time.LocalDateTime;

/**
 * Point-in-time portfolio snapshot.
 */
public class PortfolioSnapshot {

    private final LocalDateTime timestamp;
    private final double totalValue;
    private final double cash;
    private final double positionValue;
    private final int positionCount;
    private final double availableCash;
    private final double frozenCash;
    private final double realizedPnL;
    private final double unrealizedPnL;
    private final double maxDrawdown;

    public PortfolioSnapshot(
            LocalDateTime timestamp,
            double totalValue,
            double cash,
            double positionValue,
            int positionCount) {
        this(timestamp, totalValue, cash, positionValue, positionCount, cash, 0.0, 0.0, 0.0, 0.0);
    }

    public PortfolioSnapshot(
            LocalDateTime timestamp,
            double totalValue,
            double cash,
            double positionValue,
            int positionCount,
            double availableCash,
            double frozenCash,
            double realizedPnL,
            double unrealizedPnL,
            double maxDrawdown) {
        this.timestamp = timestamp;
        this.totalValue = totalValue;
        this.cash = cash;
        this.positionValue = positionValue;
        this.positionCount = positionCount;
        this.availableCash = availableCash;
        this.frozenCash = frozenCash;
        this.realizedPnL = realizedPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.maxDrawdown = maxDrawdown;
    }

    public double getCashRatio() {
        return totalValue > 0 ? cash / totalValue : 0.0;
    }

    public double getPositionRatio() {
        return totalValue > 0 ? positionValue / totalValue : 0.0;
    }

    public boolean hasPosition() {
        return positionCount > 0;
    }

    public String getPositionStatus() {
        return positionCount == 0 ? "Flat" : String.format("Open (%d)", positionCount);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public double getCash() {
        return cash;
    }

    public double getPositionValue() {
        return positionValue;
    }

    public int getPositionCount() {
        return positionCount;
    }

    public double getAvailableCash() {
        return availableCash;
    }

    public double getFrozenCash() {
        return frozenCash;
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }

    public double getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    @Override
    public String toString() {
        return String.format(
                "Snapshot{%s: total=%.2f, cash=%.2f, positions=%d, realized=%.2f, unrealized=%.2f}",
                timestamp,
                totalValue,
                cash,
                positionCount,
                realizedPnL,
                unrealizedPnL);
    }
}
