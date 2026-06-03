package com.dreamhouse.trading.core.backtest;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Portfolio state used by backtest and simulated execution.
 */
public class Portfolio {

    private double initialCash;
    private double cash;
    private final Map<String, Position> positions;
    private double totalValue;
    private double maxDrawdown = 0.0;
    private double maxValue = 0.0;
    private int totalTrades = 0;
    private int winningTrades = 0;
    private double realizedPnL = 0.0;
    private double totalCommission = 0.0;

    public Portfolio(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.positions = new HashMap<>();
        this.totalValue = initialCash;
        this.maxValue = initialCash;
    }

    public void reset(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.positions.clear();
        this.totalValue = initialCash;
        this.maxValue = initialCash;
        this.maxDrawdown = 0.0;
        this.totalTrades = 0;
        this.winningTrades = 0;
        this.realizedPnL = 0.0;
        this.totalCommission = 0.0;
    }

    public void addPosition(String symbol, int quantity, double price, double costRate) {
        double totalCost = quantity * price * (1 + costRate);

        if (cash < totalCost) {
            throw new IllegalStateException("Insufficient cash for purchase");
        }

        cash -= totalCost;
        totalCommission += quantity * price * costRate;

        Position existingPosition = positions.get(symbol);
        if (existingPosition != null) {
            existingPosition.addQuantity(quantity, price, costRate);
        } else {
            positions.put(symbol, new Position(symbol, quantity, price, costRate));
        }

        totalTrades++;
    }

    public void reducePosition(String symbol, int quantity, double price, double costRate) {
        reducePosition(symbol, quantity, price, costRate, 0.0);
    }

    public void reducePosition(String symbol, int quantity, double price, double commissionRate, double taxRate) {
        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalStateException("No position found for symbol: " + symbol);
        }

        if (position.getQuantity() < quantity) {
            throw new IllegalStateException("Insufficient position quantity");
        }

        double profit = position.calculateProfit(quantity, price, commissionRate, taxRate);
        double proceeds = quantity * price * (1 - commissionRate - taxRate);

        cash += proceeds;
        realizedPnL += profit;
        totalCommission += quantity * price * commissionRate;

        if (profit > 0) {
            winningTrades++;
        }

        position.reduceQuantity(quantity, price, commissionRate, taxRate);
        if (position.getQuantity() == 0) {
            positions.remove(symbol);
        }

        totalTrades++;
    }

    public void closeAllPositions(double currentPrice) {
        for (Position position : positions.values()) {
            double proceeds = position.getQuantity() * currentPrice * 0.999;
            cash += proceeds;
        }
        positions.clear();
    }

    public void updateMarketValue(double currentPrice) {
        double positionValue = 0.0;
        for (Position position : positions.values()) {
            positionValue += position.getQuantity() * currentPrice;
        }

        totalValue = cash + positionValue;
        if (totalValue > maxValue) {
            maxValue = totalValue;
        }

        double currentDrawdown = maxValue > 0.0 ? (maxValue - totalValue) / maxValue : 0.0;
        if (currentDrawdown > maxDrawdown) {
            maxDrawdown = currentDrawdown;
        }
    }

    public Position getPosition(String symbol) {
        return positions.get(symbol);
    }

    public Collection<Position> getPositions() {
        return positions.values();
    }

    public double getPositionValue() {
        return totalValue - cash;
    }

    public double getTotalReturn() {
        return (totalValue - initialCash) / initialCash;
    }

    public double getWinRate() {
        return totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;
    }

    public int getPositionCount() {
        return positions.size();
    }

    public boolean hasEnoughCash(double amount) {
        return cash >= amount;
    }

    public boolean hasPosition(String symbol) {
        return positions.containsKey(symbol);
    }

    public double getFrozenCash() {
        return 0.0;
    }

    public double getAvailableCash() {
        return cash;
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }

    public double getTotalCommission() {
        return totalCommission;
    }

    public double getUnrealizedPnL(Map<String, Double> markPrices) {
        double total = 0.0;
        for (Position position : positions.values()) {
            Double markPrice = markPrices.get(position.getSymbol());
            if (markPrice != null) {
                total += position.getUnrealizedPnL(markPrice);
            }
        }
        return total;
    }

    public PortfolioSnapshot createSnapshot(LocalDateTime timestamp, Map<String, Double> markPrices) {
        double unrealized = getUnrealizedPnL(markPrices);
        return new PortfolioSnapshot(
                timestamp,
                totalValue,
                cash,
                getPositionValue(),
                positions.size(),
                getAvailableCash(),
                getFrozenCash(),
                realizedPnL,
                unrealized,
                maxDrawdown);
    }

    public double getInitialCash() {
        return initialCash;
    }

    public double getCash() {
        return cash;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public int getTotalTrades() {
        return totalTrades;
    }

    public int getWinningTrades() {
        return winningTrades;
    }

    @Override
    public String toString() {
        return String.format(
                "Portfolio{cash=%.2f,totalValue=%.2f,positions=%d,return=%.2f%%}",
                cash,
                totalValue,
                positions.size(),
                getTotalReturn() * 100);
    }
}
