package com.astrastore.monitoring.service;

import com.astrastore.monitoring.config.MonitoringProperties;
import com.astrastore.monitoring.entity.HealthSample;
import com.astrastore.monitoring.probe.HealthProbe;
import com.astrastore.monitoring.probe.ProbeOutcome;
import com.astrastore.monitoring.repository.HealthSampleRepository;
import com.astrastore.monitoring.repository.ServiceIncidentRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One sweep per interval: probe every target, store a sample each, and turn
 * runs of results into incidents.
 *
 * <p>Targets are probed concurrently. Serially, eleven targets against a 2s
 * timeout could take 22 seconds — longer than the interval — so a single
 * unreachable service would delay every other service's sample and put false
 * gaps in their history.
 */
@Service
@Slf4j
public class ProbeSweepService {

    private final MonitoringProperties properties;
    private final HealthProbe healthProbe;
    private final HealthSampleRepository sampleRepository;
    private final ServiceIncidentRepository incidentRepository;
    private final IncidentService incidentService;
    private final TransitionTracker tracker;
    private final ExecutorService executor;

    public ProbeSweepService(MonitoringProperties properties,
                             HealthProbe healthProbe,
                             HealthSampleRepository sampleRepository,
                             ServiceIncidentRepository incidentRepository,
                             IncidentService incidentService) {
        this.properties = properties;
        this.healthProbe = healthProbe;
        this.sampleRepository = sampleRepository;
        this.incidentRepository = incidentRepository;
        this.incidentService = incidentService;
        this.tracker = new TransitionTracker(
                properties.getFailureThreshold(), properties.getSuccessThreshold());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Restores the debounce state from the ledger. An outage that was open
     * when this process stopped is still open now; without this the first
     * failing probe after a restart would look like a new transition.
     */
    @PostConstruct
    void restoreOpenIncidents() {
        try {
            incidentRepository.findByEndedAtMillisIsNull()
                    .forEach(incident -> tracker.seed(incident.getServiceId(), true));
        } catch (Exception e) {
            // A monitor that cannot read its own history should still probe.
            log.warn("Could not restore open incidents; starting with empty transition state", e);
        }
    }

    @Scheduled(
            fixedDelayString = "${astrastore.monitoring.probe-interval-ms:15000}",
            initialDelayString = "${astrastore.monitoring.probe-initial-delay-ms:5000}")
    public void sweep() {
        List<MonitoringProperties.Target> targets = properties.getTargets();
        if (targets.isEmpty()) {
            log.warn("No monitoring targets configured; nothing to probe");
            return;
        }

        try {
            List<ProbeOutcome> outcomes = probeAll(targets);
            persist(outcomes);
            outcomes.forEach(this::applyTransition);
        } catch (Exception e) {
            // The scheduler silently stops re-firing a task that throws, which
            // would leave the history looking like a total cluster outage.
            log.error("Probe sweep failed", e);
        }
    }

    private List<ProbeOutcome> probeAll(List<MonitoringProperties.Target> targets) {
        List<CompletableFuture<ProbeOutcome>> futures = targets.stream()
                .filter(target -> target.getId() != null && target.getBaseUrl() != null)
                .map(target -> CompletableFuture.supplyAsync(() -> healthProbe.probe(target), executor))
                .toList();

        List<ProbeOutcome> outcomes = new ArrayList<>(futures.size());
        for (CompletableFuture<ProbeOutcome> future : futures) {
            try {
                outcomes.add(future.join());
            } catch (Exception e) {
                // A probe that throws instead of returning an outcome tells us
                // nothing about the target, so no sample is recorded for it.
                log.warn("Probe task failed to produce a result", e);
            }
        }
        return outcomes;
    }

    private void persist(List<ProbeOutcome> outcomes) {
        List<HealthSample> samples = outcomes.stream()
                .map(outcome -> HealthSample.builder()
                        .serviceId(outcome.serviceId())
                        .probedAtMillis(outcome.probedAtMillis())
                        .up(outcome.up())
                        .responseTimeMs(outcome.responseTimeMs())
                        .httpStatus(outcome.httpStatus())
                        .reportedStatus(outcome.reportedStatus())
                        .build())
                .toList();
        sampleRepository.saveAll(samples);
    }

    private void applyTransition(ProbeOutcome outcome) {
        String serviceId = outcome.serviceId();
        TransitionTracker.Decision decision =
                tracker.record(serviceId, outcome.up(), outcome.probedAtMillis());

        switch (decision) {
            case OPEN_INCIDENT -> {
                Long startedAt = tracker.firstFailureMillis(serviceId);
                incidentService.open(serviceId,
                        startedAt == null ? outcome.probedAtMillis() : startedAt,
                        outcome.error());
            }
            case CLOSE_INCIDENT -> {
                Long recoveredAt = tracker.firstSuccessMillis(serviceId);
                incidentService.close(serviceId,
                        recoveredAt == null ? outcome.probedAtMillis() : recoveredAt);
            }
            case NONE -> {
                if (!outcome.up() && tracker.isConfirmedDown(serviceId)) {
                    incidentService.updateOpenIncidentError(serviceId, outcome.error());
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
