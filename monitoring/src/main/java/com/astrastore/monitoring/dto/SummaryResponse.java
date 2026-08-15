package com.astrastore.monitoring.dto;

import java.time.Instant;

/**
 * Cluster headline figures.
 *
 * @param servicesUnknown            targets with no recent sample. They are
 *                                   counted separately so up + down + degraded
 *                                   + unknown reconciles with the total,
 *                                   instead of silently landing in "up".
 * @param clusterUptimePercent       null when no service has enough
 *                                   observation to support a percentage
 * @param insufficientData           true when any service is short of data,
 *                                   including when the percentage above is
 *                                   computed from the remaining ones
 * @param servicesWithSufficientData how many services the percentage covers
 */
public record SummaryResponse(
        String window,
        Instant generatedAt,
        int servicesTotal,
        int servicesUp,
        int servicesDown,
        int servicesDegraded,
        int servicesUnknown,
        Double clusterUptimePercent,
        int openIncidents,
        int incidentsInWindow,
        boolean insufficientData,
        int servicesWithSufficientData
) {
}
