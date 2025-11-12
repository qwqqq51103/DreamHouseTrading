package com.dreamhouse.trading.core.decision.risk;

/**
 * 風險違規記錄
 * 當觸發風險限制時，記錄違規詳情
 */
public class RiskViolation {

    /**
     * 違規類型
     */
    public enum Type {
        DAILY_LOSS_LIMIT("每日虧損限制"),
        SYMBOL_LOSS_LIMIT("單檔虧損限制"),
        MAX_POSITIONS_EXCEEDED("超過最大持倉數"),
        POSITION_SIZE_EXCEEDED("單一部位過大"),
        INSUFFICIENT_CASH("現金不足"),
        MARGIN_CALL("保證金不足"),
        HOLDING_PERIOD_EXCEEDED("超過最大持倉時間"),
        END_OF_DAY_CLOSE("收盤前強制平倉");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Type type;
    private final String symbol;          // 相關商品（可能為 null）
    private final double currentValue;    // 當前值
    private final double limitValue;      // 限制值
    private final String message;         // 詳細訊息
    private final long timestamp;         // 觸發時間
    private final boolean shouldForceClose;  // 是否應強制平倉

    public RiskViolation(Type type, String symbol, double currentValue, double limitValue, String message, boolean shouldForceClose) {
        this.type = type;
        this.symbol = symbol;
        this.currentValue = currentValue;
        this.limitValue = limitValue;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.shouldForceClose = shouldForceClose;
    }

    public Type getType() {
        return type;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public double getLimitValue() {
        return limitValue;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean shouldForceClose() {
        return shouldForceClose;
    }

    @Override
    public String toString() {
        String symbolInfo = symbol != null ? " [" + symbol + "]" : "";
        return String.format("[風險違規%s] %s - 當前%.2f 超過限制%.2f - %s %s",
                symbolInfo,
                type.getDisplayName(),
                currentValue,
                limitValue,
                message,
                shouldForceClose ? "(強制平倉)" : "");
    }
}
