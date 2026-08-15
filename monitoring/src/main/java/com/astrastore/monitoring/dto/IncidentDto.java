package com.astrastore.monitoring.dto;

import java.time.Instant;

/**
 * A recorded outage.
 *
 * @param durationSeconds measured to now while {@code ongoing} is true
 * @param lastError       the most recent probe failure reason, display-safe
 */
public record IncidentDto(
        Long id,
        String serviceId,
        String serviceName,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        boolean ongoing,
        String lastError
) {
}
