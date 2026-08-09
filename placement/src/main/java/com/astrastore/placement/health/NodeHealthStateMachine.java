package com.astrastore.placement.health;

import com.astrastore.placement.config.ClusterProperties;
import com.astrastore.placement.model.HeartbeatResponse;
import com.astrastore.placement.model.NodeState;
import com.astrastore.placement.model.StorageNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Encapsulates all node health state-transition logic.
 *
 * <p>This class is intentionally stateless — it operates purely on the
 * mutable atomic fields inside the passed {@link StorageNode}.  Keeping
 * the transition logic separate from the HTTP polling ({@code HeartbeatService})
 * makes both classes independently testable.</p>
 *
 * <h2>Transition table</h2>
 * <pre>
 * Current state  │ Event                   │ Next state
 * ───────────────┼─────────────────────────┼────────────
 * HEALTHY        │ success                 │ HEALTHY
 * HEALTHY        │ failure                 │ DEGRADED
 * DEGRADED       │ success                 │ HEALTHY
 * DEGRADED       │ failure (< threshold)   │ DEGRADED
 * DEGRADED       │ failure (≥ threshold)   │ DOWN
 * DOWN           │ success                 │ RECOVERING
 * DOWN           │ failure                 │ DOWN
 * RECOVERING     │ success (< threshold)   │ RECOVERING
 * RECOVERING     │ success (≥ threshold)   │ HEALTHY
 * RECOVERING     │ failure                 │ DOWN
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NodeHealthStateMachine {

    private final ClusterProperties clusterProperties;

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Called by {@code HeartbeatService} when a node's heartbeat endpoint
     * responds successfully.
     *
     * <p>Updates disk metrics from the response payload and advances the
     * node's health state according to the transition table above.</p>
     *
     * @param node     the node to update
     * @param response the parsed heartbeat response body
     */
    public void onSuccess(StorageNode node, HeartbeatResponse response) {
        Instant now = Instant.now();
        node.getLastChecked().set(now);
        node.getLastSeen().set(now);

        // Refresh disk metrics
        if (response.getDiskTotalBytes() != null) {
            node.getDiskTotalBytes().set(response.getDiskTotalBytes());
        }
        if (response.getDiskFreeBytes() != null) {
            node.getDiskFreeBytes().set(response.getDiskFreeBytes());
        }
        if (response.getDiskUsedBytes() != null) {
            node.getDiskUsedBytes().set(response.getDiskUsedBytes());
        }

        // Reset failure counter unconditionally
        node.getConsecutiveFailures().set(0);

        NodeState current = node.getState().get();

        switch (current) {
            case HEALTHY -> {
                // Stay healthy — nothing to do
                log.debug("Heartbeat OK — node={}, state=HEALTHY", node.getNodeId());
            }
            case DEGRADED -> {
                // One success is enough to recover from DEGRADED
                transition(node, NodeState.HEALTHY,
                        "heartbeat recovered from DEGRADED");
            }
            case DOWN -> {
                // Start recovery process
                node.getConsecutiveSuccesses().set(1);
                transition(node, NodeState.RECOVERING,
                        "heartbeat responded after being DOWN");
            }
            case RECOVERING -> {
                int successes = node.getConsecutiveSuccesses().incrementAndGet();
                int threshold = clusterProperties.getHeartbeat().getRecoveryThreshold();
                log.debug("Recovering — node={}, successes={}/{}", node.getNodeId(), successes, threshold);
                if (successes >= threshold) {
                    node.getConsecutiveSuccesses().set(0);
                    transition(node, NodeState.HEALTHY,
                            "recovery complete after " + successes + " consecutive successes");
                }
            }
        }
    }

    /**
     * Called by {@code HeartbeatService} when a node's heartbeat call
     * times out or returns an error.
     *
     * @param node  the node to update
     * @param cause a short description of the failure (for log context)
     */
    public void onFailure(StorageNode node, String cause) {
        Instant now = Instant.now();
        node.getLastChecked().set(now);
        node.getConsecutiveSuccesses().set(0); // any failure resets recovery streak

        int failures = node.getConsecutiveFailures().incrementAndGet();
        int threshold = clusterProperties.getHeartbeat().getFailureThreshold();

        NodeState current = node.getState().get();
        log.warn("Heartbeat FAILED — node={}, state={}, failures={}/{}, cause={}",
                node.getNodeId(), current, failures, threshold, cause);

        switch (current) {
            case HEALTHY -> {
                // First failure — demote to DEGRADED immediately
                transition(node, NodeState.DEGRADED, "first heartbeat failure: " + cause);
            }
            case DEGRADED -> {
                if (failures >= threshold) {
                    transition(node, NodeState.DOWN,
                            "failure threshold reached (" + failures + "/" + threshold + "): " + cause);
                }
                // else: stay DEGRADED, counter keeps incrementing
            }
            case RECOVERING -> {
                // Any failure while recovering drops back to DOWN
                transition(node, NodeState.DOWN, "recovery interrupted by failure: " + cause);
            }
            case DOWN -> {
                // Already DOWN — just log, no state change
                log.debug("Node still DOWN — node={}, failures={}", node.getNodeId(), failures);
            }
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Atomically changes the node's state and emits a clear INFO log line
     * so state transitions are easy to spot in the log stream.
     */
    private void transition(StorageNode node, NodeState next, String reason) {
        NodeState previous = node.getState().getAndSet(next);
        log.info("Node state transition — node={}, {} → {}, reason={}",
                node.getNodeId(), previous, next, reason);
    }
}
