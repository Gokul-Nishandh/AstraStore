package com.astrastore.placement.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration properties for the AstraStore cluster topology,
 * bound from the {@code astrastore.cluster} prefix in {@code application.yaml}.
 *
 * <p>Injected wherever the list of known storage nodes is needed.
 * Validated at startup: missing or empty {@code nodes} list will prevent boot.</p>
 */
@Data
@ConfigurationProperties(prefix = "astrastore.cluster")
public class ClusterProperties {

    /**
     * Ordered list of all known storage nodes.
     * Each entry binds to a {@link NodeConfig} (id + url).
     */
    private List<NodeConfig> nodes = new ArrayList<>();

    /**
     * Heartbeat / health-check tuning parameters.
     */
    private HeartbeatConfig heartbeat = new HeartbeatConfig();

    /**
     * How many copies of each chunk the cluster keeps.
     *
     * <p>This is what separates the bytes on disk from the bytes a user
     * actually stored: at a factor of 2, a 1 GB upload consumes 2 GB of
     * cluster capacity. Reporting only one of those two numbers is how a
     * capacity dashboard ends up misleading.
     */
    private int replicationFactor = 2;

    // ----------------------------------------------------------------

    /**
     * Nested config class for heartbeat timing and thresholds.
     */
    @Data
    public static class HeartbeatConfig {

        /** Polling interval in milliseconds (default: 10 000 ms = 10 s). */
        private long intervalMs = 10_000L;

        /** Per-request HTTP connect+read timeout in milliseconds (default: 5 s). */
        private int timeoutMs = 5_000;

        /**
         * Number of consecutive heartbeat failures before a DEGRADED node
         * transitions to DOWN (default: 3).
         */
        private int failureThreshold = 3;

        /**
         * Number of consecutive heartbeat successes required while RECOVERING
         * before returning to HEALTHY (default: 2).
         */
        private int recoveryThreshold = 2;
    }
}
