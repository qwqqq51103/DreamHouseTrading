package com.dreamhouse.trading.core.execution;

import java.time.LocalDateTime;

/**
 * 執行結果
 * 記錄交易執行的結果資訊
 */
public class ExecutionResult {

    /**
     * 執行狀態
     */
    public enum Status {
        SUCCESS("成功"),
        FAILED("失敗"),
        PARTIAL("部分成交"),
        PENDING("待成交"),
        CANCELLED("已取消"),
        REJECTED("已拒絕");

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
    private final int requestedQuantity;
    private final int executedQuantity;
    private final double requestedPrice;
    private final double executedPrice;
    private final double commission;
    private final LocalDateTime executionTime;
    private final String message;
    private final Exception error;

    /**
     * 建構子（使用 Builder 模式）
     */
    private ExecutionResult(Builder builder) {
        this.status = builder.status;
        this.orderId = builder.orderId;
        this.symbol = builder.symbol;
        this.orderType = builder.orderType;
        this.requestedQuantity = builder.requestedQuantity;
        this.executedQuantity = builder.executedQuantity;
        this.requestedPrice = builder.requestedPrice;
        this.executedPrice = builder.executedPrice;
        this.commission = builder.commission;
        this.executionTime = builder.executionTime;
        this.message = builder.message;
        this.error = builder.error;
    }

    // Getters

    public Status getStatus() { return status; }
    public String getOrderId() { return orderId; }
    public String getSymbol() { return symbol; }
    public OrderType getOrderType() { return orderType; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getExecutedQuantity() { return executedQuantity; }
    public double getRequestedPrice() { return requestedPrice; }
    public double getExecutedPrice() { return executedPrice; }
    public double getCommission() { return commission; }
    public LocalDateTime getExecutionTime() { return executionTime; }
    public String getMessage() { return message; }
    public Exception getError() { return error; }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 是否失敗
     */
    public boolean isFailed() {
        return status == Status.FAILED || status == Status.REJECTED;
    }

    /**
     * 是否部分成交
     */
    public boolean isPartial() {
        return status == Status.PARTIAL;
    }

    /**
     * 獲取成交率
     */
    public double getFillRate() {
        if (requestedQuantity == 0) {
            return 0.0;
        }
        return (double) executedQuantity / requestedQuantity;
    }

    /**
     * 獲取滑價（實際成交價與請求價的差異）
     */
    public double getSlippage() {
        return executedPrice - requestedPrice;
    }

    /**
     * 獲取總成交金額
     */
    public double getTotalAmount() {
        return executedQuantity * executedPrice;
    }

    @Override
    public String toString() {
        return String.format("ExecutionResult[%s %s %s: %d@%.2f, status=%s]",
                orderId, symbol, orderType.getShortCode(),
                executedQuantity, executedPrice, status.getDisplayName());
    }

    /**
     * Builder 類別
     */
    public static class Builder {
        private Status status = Status.SUCCESS;
        private String orderId = "";
        private String symbol = "";
        private OrderType orderType = OrderType.MARKET;
        private int requestedQuantity = 0;
        private int executedQuantity = 0;
        private double requestedPrice = 0.0;
        private double executedPrice = 0.0;
        private double commission = 0.0;
        private LocalDateTime executionTime = LocalDateTime.now();
        private String message = "";
        private Exception error = null;

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

        public Builder commission(double commission) {
            this.commission = commission;
            return this;
        }

        public Builder executionTime(LocalDateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder error(Exception error) {
            this.error = error;
            this.status = Status.FAILED;
            this.message = error.getMessage();
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }

    /**
     * 快速創建成功結果
     */
    public static ExecutionResult success(String orderId, String symbol, int quantity, double price) {
        return new Builder()
                .status(Status.SUCCESS)
                .orderId(orderId)
                .symbol(symbol)
                .requestedQuantity(quantity)
                .executedQuantity(quantity)
                .requestedPrice(price)
                .executedPrice(price)
                .build();
    }

    /**
     * 快速創建失敗結果
     */
    public static ExecutionResult failure(String orderId, String symbol, String message) {
        return new Builder()
                .status(Status.FAILED)
                .orderId(orderId)
                .symbol(symbol)
                .message(message)
                .build();
    }
}
