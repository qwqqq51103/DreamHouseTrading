package com.dreamhouse.trading.core.finmind;

import java.time.LocalDateTime;

public class FinMindRequestBlockedException extends FinMindAccessDeniedException {
    private final LocalDateTime pausedUntil;
    private final String reason;

    public FinMindRequestBlockedException(String message, LocalDateTime pausedUntil, String reason) {
        super(message, -1, "blocked");
        this.pausedUntil = pausedUntil;
        this.reason = reason;
    }

    public LocalDateTime getPausedUntil() {
        return pausedUntil;
    }

    public String getReason() {
        return reason;
    }
}
