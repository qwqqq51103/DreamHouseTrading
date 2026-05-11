package com.dreamhouse.trading.core.execution;

import java.time.LocalDateTime;

/**
 * Standard order payload used by the execution engine.
 */
public class Order {

    private final String orderId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType type;
    private final int quantity;
    private final double requestedPrice;
    private final Double stopLoss;
    private final Double takeProfit;
    private final String reason;
    private final LocalDateTime createdAt;

    private Order(Builder builder) {
        this.orderId = builder.orderId;
        this.symbol = builder.symbol;
        this.side = builder.side;
        this.type = builder.type;
        this.quantity = builder.quantity;
        this.requestedPrice = builder.requestedPrice;
        this.stopLoss = builder.stopLoss;
        this.takeProfit = builder.takeProfit;
        this.reason = builder.reason;
        this.createdAt = builder.createdAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getRequestedPrice() {
        return requestedPrice;
    }

    public Double getStopLoss() {
        return stopLoss;
    }

    public Double getTakeProfit() {
        return takeProfit;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public static class Builder {
        private String orderId = "";
        private String symbol = "";
        private OrderSide side = OrderSide.BUY;
        private OrderType type = OrderType.MARKET;
        private int quantity;
        private double requestedPrice;
        private Double stopLoss;
        private Double takeProfit;
        private String reason = "";
        private LocalDateTime createdAt = LocalDateTime.now();

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(OrderSide side) {
            this.side = side;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder requestedPrice(double requestedPrice) {
            this.requestedPrice = requestedPrice;
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

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }
}
