package com.dreamhouse.trading.core.finmind;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

public class FinMindAccessGuard {
    public static final Duration DEFAULT_ACCESS_DENIED_COOLDOWN = Duration.ofMinutes(15);
    public static final Duration DEFAULT_QUOTA_COOLDOWN = Duration.ofHours(1);

    private final Clock clock;
    private final Duration accessDeniedCooldown;
    private final Duration quotaCooldown;

    private LocalDateTime pausedUntil;
    private String pausedReason;

    public FinMindAccessGuard() {
        this(Clock.systemDefaultZone(), DEFAULT_ACCESS_DENIED_COOLDOWN, DEFAULT_QUOTA_COOLDOWN);
    }

    public FinMindAccessGuard(Clock clock, Duration accessDeniedCooldown, Duration quotaCooldown) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.accessDeniedCooldown = accessDeniedCooldown != null ? accessDeniedCooldown : DEFAULT_ACCESS_DENIED_COOLDOWN;
        this.quotaCooldown = quotaCooldown != null ? quotaCooldown : DEFAULT_QUOTA_COOLDOWN;
    }

    public synchronized void beforeRequest() {
        if (!isPaused()) {
            return;
        }
        throw new FinMindRequestBlockedException(
                "FinMind request blocked until " + pausedUntil + ": " + pausedReason,
                pausedUntil,
                pausedReason);
    }

    public synchronized void recordSuccess() {
        if (pausedUntil != null && !isPaused()) {
            clear();
        }
    }

    public synchronized void recordFailure(FinMindException exception) {
        if (exception instanceof FinMindQuotaExceededException) {
            pause(quotaCooldown, exception.getMessage());
            return;
        }
        if (exception instanceof FinMindAccessDeniedException) {
            pause(accessDeniedCooldown, exception.getMessage());
        }
    }

    public synchronized void pause(Duration duration, String reason) {
        LocalDateTime candidate = LocalDateTime.now(clock).plus(duration);
        if (pausedUntil == null || candidate.isAfter(pausedUntil)) {
            pausedUntil = candidate;
            pausedReason = reason != null && !reason.isBlank() ? reason : "upstream rejected request";
        }
    }

    public synchronized void clear() {
        pausedUntil = null;
        pausedReason = null;
    }

    public synchronized boolean isPaused() {
        return pausedUntil != null && LocalDateTime.now(clock).isBefore(pausedUntil);
    }

    public synchronized LocalDateTime getPausedUntil() {
        return pausedUntil;
    }

    public synchronized String getPausedReason() {
        return pausedReason;
    }
}
