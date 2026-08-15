package com.astrastore.monitoring.service;

import com.astrastore.monitoring.entity.ServiceIncident;
import com.astrastore.monitoring.repository.ServiceIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Writes the outage ledger.
 *
 * <p>Both operations are idempotent with respect to the open/closed state, so
 * a duplicate signal — a monitor restart mid-outage, two sweeps overlapping —
 * cannot produce two rows for one outage or an orphaned open one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    private static final int MAX_ERROR_LENGTH = 512;

    private final ServiceIncidentRepository incidentRepository;

    @Transactional
    public ServiceIncident open(String serviceId, long startedAtMillis, String lastError) {
        Optional<ServiceIncident> existing = incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(serviceId);
        if (existing.isPresent()) {
            ServiceIncident incident = existing.get();
            incident.setLastError(truncate(lastError));
            return incidentRepository.save(incident);
        }

        ServiceIncident incident = ServiceIncident.builder()
                .serviceId(serviceId)
                .startedAtMillis(startedAtMillis)
                .lastError(truncate(lastError))
                .build();
        log.warn("Service {} confirmed DOWN: {}", serviceId, lastError);
        return incidentRepository.save(incident);
    }

    @Transactional
    public Optional<ServiceIncident> close(String serviceId, long endedAtMillis) {
        return incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(serviceId)
                .map(incident -> {
                    // A clock adjustment must not be able to record a negative
                    // outage; the floor keeps the ledger monotonic.
                    long end = Math.max(endedAtMillis, incident.getStartedAtMillis());
                    incident.setEndedAtMillis(end);
                    incident.setDurationSeconds((end - incident.getStartedAtMillis()) / 1000L);
                    log.info("Service {} recovered after {}s", serviceId, incident.getDurationSeconds());
                    return incidentRepository.save(incident);
                });
    }

    /** Keeps the most recent reason on an outage that is still running. */
    @Transactional
    public void updateOpenIncidentError(String serviceId, String lastError) {
        incidentRepository
                .findFirstByServiceIdAndEndedAtMillisIsNullOrderByStartedAtMillisDesc(serviceId)
                .ifPresent(incident -> {
                    incident.setLastError(truncate(lastError));
                    incidentRepository.save(incident);
                });
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
