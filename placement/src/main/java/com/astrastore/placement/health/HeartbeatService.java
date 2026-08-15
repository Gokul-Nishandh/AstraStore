package com.astrastore.placement.health;

import com.astrastore.placement.model.HeartbeatResponse;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;

/**
 * Periodically polls every registered storage node's heartbeat endpoint
 * and delegates the result to the {@link NodeHealthStateMachine}.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>fixedRate vs fixedDelay</strong>: {@code fixedRate} is used so polls
 *       start at consistent wall-clock intervals regardless of how long each round
 *       takes.  This is important for accurate MTTR tracking in monitoring.</li>
 *   <li><strong>Sequential polling</strong>: Nodes are polled one-by-one in the
 *       scheduler thread.  For Phase 1 (3 nodes × 5 s timeout = max 15 s per cycle,
 *       well under the 10 s interval for healthy clusters) this is fine.  Phase 2
 *       can move to a parallel {@code CompletableFuture} fan-out if needed.</li>
 *   <li><strong>No retry inside the loop</strong>: Retry logic lives in the state
 *       machine (failure-threshold).  A single failed call is enough evidence to
 *       demote to DEGRADED; the next scheduled cycle provides the implicit retry.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatService {

    private static final String HEARTBEAT_PATH = "/api/v1/health/heartbeat";

    private final NodeRegistry nodeRegistry;
    private final NodeHealthStateMachine stateMachine;

    // Named qualifier to avoid ambiguity if another RestTemplate bean exists
    @Qualifier("heartbeatRestTemplate")
    private final RestTemplate restTemplate;

    // ----------------------------------------------------------------
    // Scheduled polling
    // ----------------------------------------------------------------

    /**
     * Main heartbeat loop — runs every {@code astrastore.cluster.heartbeat.interval-ms}
     * milliseconds (default: 10 000 ms).
     *
     * <p>The initial delay of 5 s gives the application time to finish startup
     * before the first poll, preventing noisy "connection refused" logs during boot.</p>
     */
    @Scheduled(fixedRateString = "${astrastore.cluster.heartbeat.interval-ms:10000}",
               initialDelay    = 5000)
    public void runHeartbeatCycle() {
        Collection<StorageNode> nodes = nodeRegistry.getAllNodes();

        if (nodes.isEmpty()) {
            log.warn("Heartbeat cycle skipped — no nodes registered");
            return;
        }

        log.debug("Heartbeat cycle started — polling {} nodes", nodes.size());

        for (StorageNode node : nodes) {
            pollNode(node);
        }

        log.info("Heartbeat cycle complete — cluster state: {}", nodeRegistry.summary());
    }

    // ----------------------------------------------------------------
    // Per-node polling
    // ----------------------------------------------------------------

    /**
     * Calls the heartbeat endpoint of a single node and delegates the
     * outcome to the state machine.
     *
     * @param node the node to poll
     */
    private void pollNode(StorageNode node) {
        String url = node.getBaseUrl() + HEARTBEAT_PATH;
        log.debug("Polling node — id={}, url={}", node.getNodeId(), url);

        try {
            HeartbeatResponse response =
                    restTemplate.getForObject(url, HeartbeatResponse.class);

            if (response == null) {
                stateMachine.onFailure(node, "null response body");
                return;
            }

            if (!response.isUp()) {
                // Node responded but reported itself as not UP
                stateMachine.onFailure(node,
                        "node reported status=" + response.getStatus());
                return;
            }

            stateMachine.onSuccess(node, response);

            log.debug("Heartbeat success — node={}, used={}B/{}B, chunks={}, hostDiskFree={}B",
                    node.getNodeId(),
                    response.getUsedBytes(),
                    response.getCapacityBytes(),
                    response.getChunkCount(),
                    response.getHostDiskFreeBytes());

        } catch (ResourceAccessException e) {
            // Connection refused, socket timeout, hostname not found, etc.
            stateMachine.onFailure(node, "connection error: " + e.getMessage());

        } catch (RestClientException e) {
            // 4xx / 5xx HTTP status, JSON parse error, etc.
            stateMachine.onFailure(node, "HTTP error: " + e.getMessage());

        } catch (Exception e) {
            // Catch-all: never let an unexpected exception kill the scheduler thread
            log.error("Unexpected error polling node — id={}", node.getNodeId(), e);
            stateMachine.onFailure(node, "unexpected: " + e.getMessage());
        }
    }
}
