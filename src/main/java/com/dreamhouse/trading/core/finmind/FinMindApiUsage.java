package com.dreamhouse.trading.core.finmind;

public record FinMindApiUsage(int userCount, int apiRequestLimit) {
    public String displayText() {
        if (apiRequestLimit <= 0) {
            return userCount + " / unknown";
        }
        return userCount + " / " + apiRequestLimit;
    }
}
