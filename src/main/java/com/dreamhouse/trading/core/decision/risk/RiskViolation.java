package com.dreamhouse.trading.core.decision.risk;

/**
 * Risk violation emitted by RiskManager.
 */
public class RiskViolation {

    public enum Type {
        DAILY_LOSS_LIMIT("Daily Loss Limit"),
        SYMBOL_LOSS_LIMIT("Symbol Loss Limit"),
        MAX_POSITIONS_EXCEEDED("Max Positions Exceeded"),
        POSITION_SIZE_EXCEEDED("Position Size Exceeded"),
        INSUFFICIENT_CASH("Insufficient Cash"),
        MARGIN_CALL("Margin Call"),
        HOLDING_PERIOD_EXCEEDED("Holding Period Exceeded"),
        END_OF_DAY_CLOSE("End Of Day Close"),
        MIN_RISK_REWARD("Minimum Risk/Reward"),
        VOLATILITY_TOO_LOW("Volatility Too Low"),
        VOLATILITY_TOO_HIGH("Volatility Too High"),
        SHORT_SELLING_DISABLED("Short Selling Disabled");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Type type;
    private final String symbol;
    private final double currentValue;
    private final double limitValue;
    private final String message;
    private final long timestamp;
    private final boolean shouldForceClose;

    public RiskViolation(
            Type type,
            String symbol,
            double currentValue,
            double limitValue,
            String message,
            boolean shouldForceClose) {
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
        String symbolInfo = symbol != null && !symbol.isEmpty() ? " [" + symbol + "]" : "";
        return String.format(
                "[RiskViolation%s] %s - current=%.4f limit=%.4f - %s%s",
                symbolInfo,
                type.getDisplayName(),
                currentValue,
                limitValue,
                message,
                shouldForceClose ? " (force close)" : "");
    }
}
