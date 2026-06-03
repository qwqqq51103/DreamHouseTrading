package com.dreamhouse.trading.core.finmind;

public class FinMindAccessDeniedException extends FinMindException {
    public FinMindAccessDeniedException(String message, int statusCode, String apiStatus) {
        super(message, statusCode, apiStatus);
    }
}
