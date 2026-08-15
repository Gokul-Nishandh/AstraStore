package com.astrastore.monitoring.dto;

import java.time.Instant;

/**
 * One downsampled bucket.
 *
 * <p>Buckets with no samples are omitted from the series rather than emitted
 * as {@code up: false} — a gap in observation is not an outage.
 *
 * @param t  start of the bucket
 * @param up true only if every sample in the bucket succeeded
 * @param ms mean response time of the successful samples; null if none succeeded
 */
public record SparklinePoint(
        Instant t,
        boolean up,
        Integer ms
) {
}
