package com.astrastore.monitoring.probe;

/**
 * The result of a single health probe.
 *
 * @param serviceId      configured target id
 * @param probedAtMillis when the probe was issued
 * @param up             2xx <em>and</em> a reported status of UP
 * @param httpStatus     null when no response was received at all
 * @param responseTimeMs measured for failures too, so a slow refusal is visible
 * @param reportedStatus the {@code status} field of the actuator body, if parsed
 * @param error          short, display-safe reason; null when the probe succeeded
 */
public record ProbeOutcome(
        String serviceId,
        long probedAtMillis,
        boolean up,
        Integer httpStatus,
        int responseTimeMs,
        String reportedStatus,
        String error
) {
}
