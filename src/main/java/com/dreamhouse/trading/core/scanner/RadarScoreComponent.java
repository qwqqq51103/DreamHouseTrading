package com.dreamhouse.trading.core.scanner;

import java.util.Locale;

/**
 * One strategy signal contribution captured during a radar scan.
 */
public record RadarScoreComponent(
        String name,
        String signal,
        double confidence,
        double weight,
        double longContribution,
        String reason) {

    public RadarScoreComponent {
        name = safe(name);
        signal = safe(signal);
        confidence = sanitize(confidence);
        weight = sanitize(weight);
        longContribution = sanitize(longContribution);
        reason = safe(reason);
    }

    public boolean contributesToLong() {
        return longContribution > 0.0;
    }

    public String toCompactText() {
        String details = reason.isBlank() ? "" : " - " + reason;
        return String.format(Locale.US,
                "%s[%s conf=%.3f weight=%.3f long=%.3f]%s",
                name,
                signal,
                confidence,
                weight,
                longContribution,
                details);
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
