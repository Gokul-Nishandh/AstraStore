package com.astrastore.monitoring.domain;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/** The lookback ranges the read API accepts. */
public enum TimeWindow {

    ONE_HOUR("1h", Duration.ofHours(1)),
    ONE_DAY("24h", Duration.ofHours(24)),
    SEVEN_DAYS("7d", Duration.ofDays(7)),
    THIRTY_DAYS("30d", Duration.ofDays(30));

    private final String code;
    private final Duration duration;

    TimeWindow(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }

    /**
     * @throws IllegalArgumentException for anything not in the fixed set — an
     *         unrecognised window must fail loudly rather than silently fall
     *         back to a different range than the caller asked for.
     */
    public static TimeWindow fromCode(String value) {
        if (value == null || value.isBlank()) {
            return ONE_DAY;
        }
        String normalised = value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(w -> w.code.equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported window '" + value + "'. Supported: " + codes()));
    }

    public static String codes() {
        return Arrays.stream(values()).map(TimeWindow::code).collect(Collectors.joining(", "));
    }
}
