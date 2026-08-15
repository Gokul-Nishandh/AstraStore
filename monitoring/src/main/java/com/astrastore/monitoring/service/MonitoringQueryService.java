package com.astrastore.monitoring.service;

import com.astrastore.monitoring.config.MonitoringProperties;
import com.astrastore.monitoring.domain.ServiceStatus;
import com.astrastore.monitoring.domain.TimeWindow;
import com.astrastore.monitoring.dto.IncidentDto;
import com.astrastore.monitoring.dto.IncidentsResponse;
import com.astrastore.monitoring.dto.ResponseTimeStats;
import com.astrastore.monitoring.dto.ServiceHealth;
import com.astrastore.monitoring.dto.ServiceHealthDetail;
import com.astrastore.monitoring.dto.ServicesResponse;
import com.astrastore.monitoring.dto.SparklinePoint;
import com.astrastore.monitoring.dto.SummaryResponse;
import com.astrastore.monitoring.entity.HealthSample;
import com.astrastore.monitoring.entity.ServiceIncident;
import com.astrastore.monitoring.exception.UnknownServiceException;
import com.astrastore.monitoring.repository.HealthSampleRepository;
import com.astrastore.monitoring.repository.ServiceIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers the read API.
 *
 * <p>Two rules shape everything here.
 *
 * <p>First, downtime comes from the incident ledger, never from counting
 * failed samples. Counting samples would make the answer depend on the probe
 * interval and would require reading six figures of rows for a month-long
 * window; the ledger holds the transitions themselves and a handful of rows
 * answers the same question exactly.
 *
 * <p>Second, when the data cannot support a figure the figure is {@code null}
 * and {@code insufficientData} is true. Nothing here substitutes zero or a
 * hundred for "we have not been watching long enough" — a confident number
 * that nobody measured is worse than no number at all.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoringQueryService {

    private final MonitoringProperties properties;
    private final HealthSampleRepository sampleRepository;
    private final ServiceIncidentRepository incidentRepository;
    private final java.time.Clock clock;

    // --- Public API -------------------------------------------------------

    public ServicesResponse services(TimeWindow window) {
        Instant now = clock.instant();
        return new ServicesResponse(window.code(), now, computeAll(window, now.toEpochMilli()));
    }

    public ServiceHealthDetail service(String serviceId, TimeWindow window) {
        MonitoringProperties.Target target = properties.getTargets().stream()
                .filter(t -> t.getId() != null && t.getId().equals(serviceId))
                .findFirst()
                .orElseThrow(() -> new UnknownServiceException(serviceId));

        long nowMillis = clock.millis();
        long fromMillis = nowMillis - window.duration().toMillis();

        ServiceHealth health = computeAll(window, nowMillis).stream()
                .filter(s -> s.id().equals(target.getId()))
                .findFirst()
                .orElseThrow(() -> new UnknownServiceException(serviceId));

        List<IncidentDto> incidents = incidentRepository.findOverlapping(fromMillis, nowMillis).stream()
                .filter(i -> i.getServiceId().equals(target.getId()))
                .map(i -> toDto(i, target.getDisplayName(), nowMillis))
                .toList();

        return ServiceHealthDetail.of(health, incidents);
    }

    public IncidentsResponse incidents(TimeWindow window, int limit) {
        long nowMillis = clock.millis();
        long fromMillis = nowMillis - window.duration().toMillis();
        Map<String, String> names = displayNames();

        List<IncidentDto> incidents = incidentRepository
                .findOverlapping(fromMillis, nowMillis, PageRequest.of(0, limit)).stream()
                .map(i -> toDto(i, names.getOrDefault(i.getServiceId(), i.getServiceId()), nowMillis))
                .toList();

        return new IncidentsResponse(incidents);
    }

    public SummaryResponse summary(TimeWindow window) {
        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        long fromMillis = nowMillis - window.duration().toMillis();

        List<ServiceHealth> services = computeAll(window, nowMillis);

        int up = 0;
        int down = 0;
        int degraded = 0;
        int unknown = 0;
        long observedTotal = 0;
        long downtimeTotal = 0;
        int withData = 0;

        for (ServiceHealth service : services) {
            switch (service.status()) {
                case UP -> up++;
                case DOWN -> down++;
                case DEGRADED -> degraded++;
                case UNKNOWN -> unknown++;
            }
            if (!service.insufficientData()) {
                withData++;
                observedTotal += service.observedSeconds();
                downtimeTotal += service.downtimeSeconds();
            }
        }

        // Only services with enough observation contribute; if none do, the
        // cluster figure is withheld rather than reported as a perfect score.
        Double clusterUptime = withData == 0
                ? null
                : UptimeMath.weightedUptimePercent(observedTotal, downtimeTotal);

        int openIncidents = incidentRepository.findByEndedAtMillisIsNull().size();
        int incidentsInWindow = incidentRepository.findOverlapping(fromMillis, nowMillis).size();

        return new SummaryResponse(
                window.code(), now,
                services.size(), up, down, degraded, unknown,
                clusterUptime,
                openIncidents, incidentsInWindow,
                withData < services.size(),
                withData);
    }

    // --- Assembly ---------------------------------------------------------

    private List<ServiceHealth> computeAll(TimeWindow window, long nowMillis) {
        long fromMillis = nowMillis - window.duration().toMillis();

        Map<String, Aggregate> aggregates = loadAggregates(fromMillis, nowMillis);
        Map<String, ResponseTimeStats> percentiles = loadPercentiles(fromMillis, nowMillis);
        Map<String, List<SparklinePoint>> sparklines = loadSparklines(window, fromMillis, nowMillis);
        Map<String, List<ServiceIncident>> incidentsByService = new HashMap<>();
        for (ServiceIncident incident : incidentRepository.findOverlapping(fromMillis, nowMillis)) {
            incidentsByService.computeIfAbsent(incident.getServiceId(), id -> new ArrayList<>())
                    .add(incident);
        }

        List<ServiceHealth> result = new ArrayList<>();
        for (MonitoringProperties.Target target : properties.getTargets()) {
            if (target.getId() == null) {
                continue;
            }
            result.add(build(target, window, fromMillis, nowMillis,
                    aggregates.get(target.getId()),
                    percentiles.getOrDefault(target.getId(), ResponseTimeStats.EMPTY),
                    sparklines.getOrDefault(target.getId(), List.of()),
                    incidentsByService.getOrDefault(target.getId(), List.of())));
        }
        return result;
    }

    private ServiceHealth build(MonitoringProperties.Target target,
                                TimeWindow window,
                                long fromMillis,
                                long nowMillis,
                                Aggregate aggregate,
                                ResponseTimeStats percentiles,
                                List<SparklinePoint> sparkline,
                                List<ServiceIncident> incidents) {

        String serviceId = target.getId();

        long sampleCount = aggregate == null ? 0L : aggregate.total();
        long observedStart = aggregate == null ? nowMillis : Math.max(fromMillis, aggregate.firstMillis());
        // Credit at most one interval past the newest sample: the time since
        // then is either the current interval or a gap in which we observed
        // nothing and can claim nothing.
        long observedEnd = aggregate == null
                ? nowMillis
                : Math.min(nowMillis, aggregate.lastMillis() + properties.getProbeIntervalMs());
        long observedSeconds = Math.max(0L, (observedEnd - observedStart) / 1000L);

        // Clipped to the observed span rather than the nominal window so that
        // downtimeSeconds and uptimePercent describe the same period; an
        // outage running through a stretch we were not watching is not
        // something we can attest to.
        long downtimeSeconds = UptimeMath.downtimeSeconds(
                incidents.stream()
                        .map(i -> new UptimeMath.Outage(i.getStartedAtMillis(), i.getEndedAtMillis()))
                        .toList(),
                observedStart, observedEnd);

        boolean insufficientData = sampleCount < properties.getMinSamplesForUptime()
                || observedSeconds < properties.getMinObservedSecondsForUptime();

        Double uptimePercent = insufficientData
                ? null
                : UptimeMath.uptimePercent(observedSeconds, downtimeSeconds);

        Optional<HealthSample> latest =
                sampleRepository.findFirstByServiceIdOrderByProbedAtMillisDesc(serviceId);
        Optional<ServiceIncident> openIncident = incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(serviceId);

        Integer lastResponseTime = sampleRepository
                .findFirstByServiceIdAndUpTrueOrderByProbedAtMillisDesc(serviceId)
                .map(HealthSample::getResponseTimeMs)
                .orElse(null);

        return new ServiceHealth(
                serviceId,
                target.getDisplayName(),
                target.getKind(),
                deriveStatus(latest.orElse(null), openIncident.orElse(null), nowMillis),
                uptimePercent,
                lastStateChange(serviceId),
                downtimeSeconds,
                incidents.size(),
                new ResponseTimeStats(percentiles.p50(), percentiles.p95(), percentiles.p99(),
                        lastResponseTime),
                sparkline,
                insufficientData,
                sampleCount,
                observedSeconds);
    }

    /**
     * Derived entirely from persisted state — the newest sample plus whether
     * an incident is open — so a restarted monitor reports the same status it
     * did before, without carrying debounce counters across the restart.
     */
    ServiceStatus deriveStatus(HealthSample latest, ServiceIncident openIncident, long nowMillis) {
        if (latest == null) {
            return ServiceStatus.UNKNOWN;
        }
        if (nowMillis - latest.getProbedAtMillis() > properties.staleAfter().toMillis()) {
            return ServiceStatus.UNKNOWN;
        }
        if (openIncident != null) {
            // An open incident with a passing probe is a recovery that has not
            // yet met the success threshold.
            return latest.isUp() ? ServiceStatus.DEGRADED : ServiceStatus.DOWN;
        }
        return latest.isUp() ? ServiceStatus.UP : ServiceStatus.DEGRADED;
    }

    /**
     * The most recent transition of any kind. Null when none has ever been
     * recorded — a service that has simply been up since we started watching
     * has not changed state, and dating that to the first probe would invent
     * an event.
     */
    private Instant lastStateChange(String serviceId) {
        return incidentRepository.findFirstByServiceIdOrderByStartedAtMillisDesc(serviceId)
                .map(incident -> incident.getEndedAtMillis() == null
                        ? incident.startedAt()
                        : incident.endedAt())
                .orElse(null);
    }

    // --- Loading ----------------------------------------------------------

    private Map<String, Aggregate> loadAggregates(long fromMillis, long toMillis) {
        Map<String, Aggregate> result = new HashMap<>();
        for (Object[] row : sampleRepository.aggregateByService(fromMillis, toMillis)) {
            result.put((String) row[0], new Aggregate(
                    toLong(row[1]), toLong(row[3]), toLong(row[4])));
        }
        return result;
    }

    private Map<String, ResponseTimeStats> loadPercentiles(long fromMillis, long toMillis) {
        Map<String, ResponseTimeStats> result = new HashMap<>();
        for (Object[] row : sampleRepository.responseTimePercentiles(fromMillis, toMillis)) {
            result.put((String) row[0],
                    new ResponseTimeStats(toInteger(row[1]), toInteger(row[2]), toInteger(row[3]), null));
        }
        return result;
    }

    private Map<String, List<SparklinePoint>> loadSparklines(TimeWindow window,
                                                             long fromMillis,
                                                             long toMillis) {
        int maxBuckets = Math.max(1, properties.getMaxSparklineBuckets());
        long windowMillis = window.duration().toMillis();
        long bucketMillis = Math.max(1L, (windowMillis + maxBuckets - 1) / maxBuckets);

        Map<String, List<SparklinePoint>> result = new LinkedHashMap<>();
        for (Object[] row : sampleRepository.sparklineBuckets(fromMillis, toMillis, bucketMillis)) {
            String serviceId = (String) row[0];
            long index = toLong(row[1]);
            boolean allUp = toLong(row[2]) == 1L;
            Integer averageMs = toInteger(row[3]);
            Instant bucketStart = Instant.ofEpochMilli(fromMillis + index * bucketMillis);
            result.computeIfAbsent(serviceId, id -> new ArrayList<>())
                    .add(new SparklinePoint(bucketStart, allUp, averageMs));
        }
        return result;
    }

    private Map<String, String> displayNames() {
        Map<String, String> names = new HashMap<>();
        for (MonitoringProperties.Target target : properties.getTargets()) {
            if (target.getId() != null) {
                names.put(target.getId(), target.getDisplayName());
            }
        }
        return names;
    }

    private IncidentDto toDto(ServiceIncident incident, String serviceName, long nowMillis) {
        return new IncidentDto(
                incident.getId(),
                incident.getServiceId(),
                serviceName,
                incident.startedAt(),
                incident.endedAt(),
                incident.durationSeconds(nowMillis),
                incident.isOngoing(),
                incident.getLastError());
    }

    // --- Result-set coercion ----------------------------------------------
    // Native aggregates come back as Long, BigInteger, BigDecimal or Integer
    // depending on the database, so the mapping goes through Number.

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Integer toInteger(Object value) {
        return value == null ? null : (int) Math.round(((Number) value).doubleValue());
    }

    private record Aggregate(long total, long firstMillis, long lastMillis) {
    }
}
