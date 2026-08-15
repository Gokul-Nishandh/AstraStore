package com.astrastore.placement.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * <h2>Capacity model</h2>
 * <p>Capacity is the node's <em>configured quota</em>, reported by the node
 * itself, and usage is the bytes it has genuinely written. These are the only
 * per-node figures that mean anything when added up: the host filesystem is
 * shared by every container in a local stack, so summing it counts one drive
 * three times. {@code hostDiskFreeBytes} is kept purely as an operational
 * warning signal and is deliberately never aggregated.</p>
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
    // Capacity metrics — updated on every successful heartbeat
    // ----------------------------------------------------------------

    /**
     * Whether this node has ever reported quota-based capacity.
     *
     * <p>Until it has, the figures below are placeholders and must not be
     * folded into any cluster total — a zero would silently shrink the
     * cluster's reported size rather than admitting the data is missing.
     */
    @Builder.Default
    private final AtomicBoolean capacityReported = new AtomicBoolean(false);

    /** Bytes this node is configured to hold (its quota). */
    @Builder.Default
    private final AtomicLong capacityBytes = new AtomicLong(0L);

    /** Bytes this node has actually stored. */
    @Builder.Default
    private final AtomicLong usedBytes = new AtomicLong(0L);

    /** Remaining quota, floored at zero. */
    @Builder.Default
    private final AtomicLong availableBytes = new AtomicLong(0L);

    /** Number of chunk files held by this node. */
    @Builder.Default
    private final AtomicLong chunkCount = new AtomicLong(0L);

    /**
     * Free space on the node's underlying filesystem.
     *
     * <p>Advisory only, and shared between every node on a single host.
     * Never sum this across nodes.
     */
    @Builder.Default
    private final AtomicLong hostDiskFreeBytes = new AtomicLong(0L);

    // ----------------------------------------------------------------
    // Convenience accessors
    // ----------------------------------------------------------------

    /**
     * Fraction of this node's quota that is in use.
     *
     * @return value in [0.0, ∞), or 0.0 if the quota is unknown.
     */
    public double getUsedRatio() {
        long capacity = capacityBytes.get();
        if (capacity <= 0L) return 0.0;
        return (double) usedBytes.get() / capacity;
    }

    /**
     * Fraction of this node's quota that is still free — the capacity term in
     * the placement score.
     *
     * @return value in [0.0, 1.0], or 1.0 if the quota is unknown (safe default:
     *         an unreported node is not preferentially avoided).
     */
    public double getFreeRatio() {
        long capacity = capacityBytes.get();
        if (capacity <= 0L) return 1.0;
        return (double) availableBytes.get() / capacity;
    }

    /** @deprecated use {@link #getFreeRatio()} — kept for existing callers. */
    @Deprecated
    public double getDiskFreeRatio() {
        return getFreeRatio();
    }

    /** @deprecated use {@link #getUsedRatio()} — kept for existing callers. */
    @Deprecated
    public double getDiskUsedRatio() {
        return getUsedRatio();
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
                ", used=" + usedBytes.get() + "/" + capacityBytes.get() + "B" +
                ", chunks=" + chunkCount.get() +
                ", failures=" + consecutiveFailures.get() +
                ", lastSeen=" + lastSeen.get() +
                '}';
    }
}
