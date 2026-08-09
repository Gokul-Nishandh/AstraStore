package com.astrastore.placement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AstraStore Placement Service entry point.
 *
 * <p>Responsibilities (Phase 1 — Cluster Health Monitoring):</p>
 * <ul>
 *   <li>Heartbeat polling of all storage nodes every 10 s</li>
 *   <li>Node health state machine (HEALTHY → DEGRADED → DOWN → RECOVERING)</li>
 *   <li>In-memory NodeRegistry exposed via REST API</li>
 * </ul>
 */
@SpringBootApplication
@Slf4j
public class PlacementApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlacementApplication.class, args);
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║   AstraStore Placement Service — STARTED         ║");
        log.info("║   Phase 1: Cluster Health Monitoring Foundation  ║");
        log.info("╚══════════════════════════════════════════════════╝");
    }
}

