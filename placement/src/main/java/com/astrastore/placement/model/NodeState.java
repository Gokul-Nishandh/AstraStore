package com.astrastore.placement.model;

/**
 * Represents the health lifecycle of a storage node as tracked by the
 * HeartbeatService.
 *
 * <pre>
 * State-transition diagram:
 *
 *   [HEALTHY] ──(timeout/error)──► [DEGRADED]
 *   [DEGRADED] ──(N consecutive failures)──► [DOWN]
 *   [DEGRADED] ──(success)──► [HEALTHY]
 *   [DOWN] ──(heartbeat responds)──► [RECOVERING]
 *   [RECOVERING] ──(M consecutive successes)──► [HEALTHY]
 *   [RECOVERING] ──(failure)──► [DOWN]
 * </pre>
 */
public enum NodeState {

    /**
     * Node is reachable and responding within the SLA timeout.
     * Eligible for new write placement and replication targets.
     */
    HEALTHY,

    /**
     * Node has missed one heartbeat or responded too slowly.
     * Still considered for placement, but with a lower score penalty.
     * One successful heartbeat returns it to HEALTHY.
     */
    DEGRADED,

    /**
     * Node has missed {@code failure-threshold} consecutive heartbeats.
     * Excluded from all new placement and replication decisions.
     */
    DOWN,

    /**
     * Node was DOWN but has just started responding again.
     * Must accumulate {@code recovery-threshold} consecutive successes
     * before returning to HEALTHY. Not yet eligible for placement.
     */
    RECOVERING
}
