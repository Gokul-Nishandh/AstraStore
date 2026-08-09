package com.astrastore.placement.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a storage node tracked by the Node Registry.
 *
 * <p>All mutable fields use atomic types so the HeartbeatService (scheduled thread)
 * and the placement scorer (request threads) can read/write concurrently without
 * taking a lock on the whole registry.</p>
 *
 * <p>Immutable fields ({@code nodeId}, {@code baseUrl}) are set at construction
 * time and never change for the lifetime of this entry.</p>
 */
@Getter
@Builder
public class StorageNode {

    // ----------------------------------------------------------------
    // Immutable identity
    // ----------------------------------------------------------------

    /** Human-readable node name, e.g. "storage-node-1". */
    private final String nodeId;

    /** Full base URL used to call the node, e.g. "http://storage-node-1:8088". */
    private final String baseUrl;

    // ----------------------------------------------------------------
    // Health state — written only by the HeartbeatService
    // ----------------------------------------------------------------

    /**
     * Current health state of this node.
     * AtomicReference ensures visibility without coarse-grained locking.
     */
    @Builder.Default
    private final AtomicReference<NodeState> state =
            new AtomicReference<>(NodeState.HEALTHY);

    /**
     * Timestamp of the last successful heartbeat response.
     * Null until the first successful poll.
     */
    @Builder.Default
    private final AtomicReference<Instant> lastSeen =
            new AtomicReference<>(null);

    /**
     * Timestamp of the last heartbeat attempt (success or failure).
     */
    @Builder.Default
    private final AtomicReference<Instant> lastChecked =
            new AtomicReference<>(null);

    /**
     * Number of consecutive failed heartbeat calls.
     * Reset to 0 on any successful heartbeat.
     */
    @Builder.Default
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * Number of consecutive successful heartbeats while in RECOVERING state.
     * Reset to 0 when transitioning away from RECOVERING.
     */
    @Builder.Default
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);

    /**
     * Number of active connections currently routed to this node.
     * Note: This acts as a placeholder metric for load until real telemetry is added.
     */
    @Builder.Default
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    // ----------------------------------------------------------------
    // Disk capacity metrics — updated on every successful heartbeat
    // ----------------------------------------------------------------

    /** Total disk space on this node in bytes (as reported by the node). */
    @Builder.Default
    private final AtomicLong diskTotalBytes = new AtomicLong(0L);

    /** Available (usable) disk space in bytes. */
    @Builder.Default
    private final AtomicLong diskFreeBytes = new AtomicLong(0L);

    /** Bytes currently used for storage. */
    @Builder.Default
    private final AtomicLong diskUsedBytes = new AtomicLong(0L);

    // ----------------------------------------------------------------
    // Convenience accessors
    // ----------------------------------------------------------------

    /**
     * Calculates the fraction of disk space that is used.
     *
     * @return value in [0.0, 1.0], or 0.0 if total is unknown.
     */
    public double getDiskUsedRatio() {
        long total = diskTotalBytes.get();
        if (total == 0L) return 0.0;
        return (double) diskUsedBytes.get() / total;
    }

    /**
     * Calculates the fraction of disk space that is free.
     *
     * @return value in [0.0, 1.0], or 1.0 if total is unknown (safe default).
     */
    public double getDiskFreeRatio() {
        long total = diskTotalBytes.get();
        if (total == 0L) return 1.0;
        return (double) diskFreeBytes.get() / total;
    }

    /**
     * Returns {@code true} if this node is eligible to receive new data.
     * A node is eligible only when it is HEALTHY or (optionally) DEGRADED.
     */
    public boolean isEligibleForPlacement() {
        NodeState s = state.get();
        return s == NodeState.HEALTHY || s == NodeState.DEGRADED;
    }

    @Override
    public String toString() {
        return "StorageNode{" +
                "nodeId='" + nodeId + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", state=" + state.get() +
                ", diskFreeRatio=" + String.format("%.2f", getDiskFreeRatio()) +
                ", failures=" + consecutiveFailures.get() +
                ", lastSeen=" + lastSeen.get() +
                '}';
    }
}
