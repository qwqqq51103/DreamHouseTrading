package com.dreamhouse.trading.core.finmind;

public class FinMindQuotaExceededException extends FinMindException {
    public FinMindQuotaExceededException(String message, int statusCode, String apiStatus) {
        super(message, statusCode, apiStatus);
    }
}
