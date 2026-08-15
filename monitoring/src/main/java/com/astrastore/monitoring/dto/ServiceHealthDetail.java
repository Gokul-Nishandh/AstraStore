package com.astrastore.monitoring.dto;

import com.astrastore.monitoring.domain.ServiceStatus;

import java.time.Instant;
import java.util.List;

/** {@link ServiceHealth} with the window's incidents attached, flat on the wire. */
public record ServiceHealthDetail(
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
        long observedSeconds,
        List<IncidentDto> incidents
) {

    public static ServiceHealthDetail of(ServiceHealth health, List<IncidentDto> incidents) {
        return new ServiceHealthDetail(
                health.id(), health.name(), health.kind(), health.status(),
                health.uptimePercent(), health.lastStateChange(), health.downtimeSeconds(),
                health.incidentCount(), health.responseTimeMs(), health.sparkline(),
                health.insufficientData(), health.sampleCount(), health.observedSeconds(),
                incidents);
    }
}
