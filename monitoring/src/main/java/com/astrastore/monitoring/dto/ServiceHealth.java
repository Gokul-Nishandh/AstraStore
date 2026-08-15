package com.astrastore.monitoring.dto;

import com.astrastore.monitoring.domain.ServiceStatus;

import java.time.Instant;
import java.util.List;

/**
 * One service's availability over a window.
 *
 * @param uptimePercent     null whenever {@code insufficientData} is true
 * @param lastStateChange   null until a transition has actually been observed
 * @param downtimeSeconds   recorded downtime clipped to the window
 * @param incidentCount     incidents overlapping the window
 * @param insufficientData  true when the window holds too little observation
 *                          to support a percentage
 * @param sampleCount       probes recorded in the window
 * @param observedSeconds   the span the figures actually describe, which is
 *                          shorter than the window whenever probing started
 *                          inside it. It is the denominator of
 *                          {@code uptimePercent} and the client should label
 *                          the percentage with it rather than with the window.
 */
public record ServiceHealth(
        String id,
        String name,
        String kind,
        ServiceStatus status,
        Double uptimePercent,
        Instant lastStateChange,
        long downtimeSeconds,
        int incidentCount,
        ResponseTimeStats responseTimeMs,
        List<SparklinePoint> sparkline,
        boolean insufficientData,
        long sampleCount,
        long observedSeconds
) {
}
