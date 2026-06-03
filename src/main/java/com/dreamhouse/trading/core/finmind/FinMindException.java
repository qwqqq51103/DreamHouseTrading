package com.dreamhouse.trading.core.finmind;

public class FinMindException extends RuntimeException {
    private final int statusCode;
    private final String apiStatus;

    public FinMindException(String message) {
        this(message, -1, null);
    }

    public FinMindException(String message, int statusCode, String apiStatus) {
        super(message);
        this.statusCode = statusCode;
        this.apiStatus = apiStatus;
    }

    public FinMindException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.apiStatus = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getApiStatus() {
        return apiStatus;
    }
}
