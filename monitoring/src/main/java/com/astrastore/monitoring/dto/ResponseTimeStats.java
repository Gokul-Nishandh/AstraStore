package com.astrastore.monitoring.dto;

/**
 * Latency over successful probes in the window.
 *
 * <p>Every field is nullable and every field is null when the window holds no
 * successful probe. A service that has never answered has no latency, and
 * reporting zero would read as "instant" on the dashboard.
 */
public record ResponseTimeStats(
        Integer p50,
        Integer p95,
        Integer p99,
        Integer last
) {

    public static final ResponseTimeStats EMPTY = new ResponseTimeStats(null, null, null, null);
}
