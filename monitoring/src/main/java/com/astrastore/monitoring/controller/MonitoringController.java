package com.astrastore.monitoring.controller;

import com.astrastore.monitoring.domain.TimeWindow;
import com.astrastore.monitoring.dto.IncidentsResponse;
import com.astrastore.monitoring.dto.ServiceHealthDetail;
import com.astrastore.monitoring.dto.ServicesResponse;
import com.astrastore.monitoring.dto.SummaryResponse;
import com.astrastore.monitoring.service.MonitoringQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Availability history for the admin dashboard.
 *
 * <p>Gated to administrators by {@code SecurityConfig}: the shape of the
 * cluster and which parts of it are failing is operational detail, not
 * something an ordinary account needs.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    /**
     * Upper bound on {@code limit}. A caller asking for everything still gets
     * a bounded response rather than the whole ledger in one body.
     */
    private static final int MAX_INCIDENT_LIMIT = 500;
    private static final int DEFAULT_INCIDENT_LIMIT = 50;

    private final MonitoringQueryService queryService;

    @GetMapping("/services")
    public ResponseEntity<ServicesResponse> services(
            @RequestParam(name = "window", defaultValue = "24h") String window) {
        return ResponseEntity.ok(queryService.services(TimeWindow.fromCode(window)));
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<ServiceHealthDetail> service(
            @PathVariable("id") String id,
            @RequestParam(name = "window", defaultValue = "24h") String window) {
        return ResponseEntity.ok(queryService.service(id, TimeWindow.fromCode(window)));
    }

    @GetMapping("/incidents")
    public ResponseEntity<IncidentsResponse> incidents(
            @RequestParam(name = "window", defaultValue = "7d") String window,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(
                queryService.incidents(TimeWindow.fromCode(window), clampLimit(limit)));
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> summary(
            @RequestParam(name = "window", defaultValue = "24h") String window) {
        return ResponseEntity.ok(queryService.summary(TimeWindow.fromCode(window)));
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_INCIDENT_LIMIT;
        }
        return Math.min(limit, MAX_INCIDENT_LIMIT);
    }
}
