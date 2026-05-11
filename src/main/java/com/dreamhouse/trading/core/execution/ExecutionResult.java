package com.dreamhouse.trading.core.execution;

import java.time.LocalDateTime;

/**
 * Standard execution response.
 */
public class ExecutionResult {

    public enum Status {
        SUCCESS("Success"),
        FAILED("Failed"),
        PARTIAL("Partial"),
        PENDING("Pending"),
        CANCELLED("Cancelled"),
        REJECTED("Rejected");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Status status;
    private final String orderId;
    private final String symbol;
    private final OrderType orderType;
    private final OrderSide orderSide;
    private final OrderStatus orderStatus;
    private final int requestedQuantity;
    private final int executedQuantity;
    private final double requestedPrice;
    private final double executedPrice;
    private final Double stopLoss;
    private final Double takeProfit;
    private final double realizedPnL;
    private final double commission;
    private final LocalDateTime executionTime;
    private final String positionId;
    private final String message;
    private final String decisionReason;
    private final Exception error;

    private ExecutionResult(Builder builder) {
        this.status = builder.status;
        this.orderId = builder.orderId;
        this.symbol = builder.symbol;
        this.orderType = builder.orderType;
        this.orderSide = builder.orderSide;
        this.orderStatus = builder.orderStatus;
        this.requestedQuantity = builder.requestedQuantity;
        this.executedQuantity = builder.executedQuantity;
        this.requestedPrice = builder.requestedPrice;
        this.executedPrice = builder.executedPrice;
        this.stopLoss = builder.stopLoss;
        this.takeProfit = builder.takeProfit;
        this.realizedPnL = builder.realizedPnL;
        this.commission = builder.commission;
        this.executionTime = builder.executionTime;
        this.positionId = builder.positionId;
        this.message = builder.message;
        this.decisionReason = builder.decisionReason;
        this.error = builder.error;
    }

    public Status getStatus() {
        return status;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderSide getOrderSide() {
        return orderSide;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getExecutedQuantity() {
        return executedQuantity;
    }

    public double getRequestedPrice() {
        return requestedPrice;
    }

    public double getExecutedPrice() {
        return executedPrice;
    }

    public Double getStopLoss() {
        return stopLoss;
    }

    public Double getTakeProfit() {
        return takeProfit;
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }

    public double getCommission() {
        return commission;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public String getPositionId() {
        return positionId;
    }

    public String getMessage() {
        return message;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED || status == Status.REJECTED;
    }

    public boolean isPartial() {
        return status == Status.PARTIAL;
    }

    public double getFillRate() {
        if (requestedQuantity == 0) {
            return 0.0;
        }
        return (double) executedQuantity / requestedQuantity;
    }

    public double getSlippage() {
        return executedPrice - requestedPrice;
    }

    public double getTotalAmount() {
        return executedQuantity * executedPrice;
    }

    @Override
    public String toString() {
        return String.format(
                "ExecutionResult[%s %s %s %s: %d@%.2f, status=%s/%s]",
                orderId,
                symbol,
                orderSide,
                orderType.getShortCode(),
                executedQuantity,
                executedPrice,
                status.getDisplayName(),
                orderStatus);
    }

    public static class Builder {
        private Status status = Status.SUCCESS;
        private String orderId = "";
        private String symbol = "";
        private OrderType orderType = OrderType.MARKET;
        private OrderSide orderSide = OrderSide.BUY;
        private OrderStatus orderStatus = OrderStatus.FILLED;
        private int requestedQuantity;
        private int executedQuantity;
        private double requestedPrice;
        private double executedPrice;
        private Double stopLoss;
        private Double takeProfit;
        private double realizedPnL;
        private double commission;
        private LocalDateTime executionTime = LocalDateTime.now();
        private String positionId = "";
        private String message = "";
        private String decisionReason = "";
        private Exception error;

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder orderType(OrderType orderType) {
            this.orderType = orderType;
            return this;
        }

        public Builder orderSide(OrderSide orderSide) {
            this.orderSide = orderSide;
            return this;
        }

        public Builder orderStatus(OrderStatus orderStatus) {
            this.orderStatus = orderStatus;
            return this;
        }

        public Builder requestedQuantity(int requestedQuantity) {
            this.requestedQuantity = requestedQuantity;
            return this;
        }

        public Builder executedQuantity(int executedQuantity) {
            this.executedQuantity = executedQuantity;
            return this;
        }

        public Builder requestedPrice(double requestedPrice) {
            this.requestedPrice = requestedPrice;
            return this;
        }

        public Builder executedPrice(double executedPrice) {
            this.executedPrice = executedPrice;
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

        public Builder realizedPnL(double realizedPnL) {
            this.realizedPnL = realizedPnL;
            return this;
        }

        public Builder commission(double commission) {
            this.commission = commission;
            return this;
        }

        public Builder executionTime(LocalDateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder positionId(String positionId) {
            this.positionId = positionId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder decisionReason(String decisionReason) {
            this.decisionReason = decisionReason;
            return this;
        }

        public Builder error(Exception error) {
            this.error = error;
            this.status = Status.FAILED;
            this.orderStatus = OrderStatus.REJECTED;
            this.message = error.getMessage();
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }

    public static ExecutionResult success(String orderId, String symbol, int quantity, double price) {
        return new Builder()
                .status(Status.SUCCESS)
                .orderId(orderId)
                .symbol(symbol)
                .requestedQuantity(quantity)
                .executedQuantity(quantity)
                .requestedPrice(price)
                .executedPrice(price)
                .orderStatus(OrderStatus.FILLED)
                .build();
    }

    public static ExecutionResult failure(String orderId, String symbol, String message) {
        return new Builder()
                .status(Status.FAILED)
                .orderId(orderId)
                .symbol(symbol)
                .message(message)
                .orderStatus(OrderStatus.REJECTED)
                .build();
    }
}
