package com.astrastore.replication.orchestrator;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.shared.events.ReplicationCommand;
import com.astrastore.shared.strategy.PlacementStrategy;
import com.astrastore.replication.client.ReplicationPushClient;
import com.astrastore.replication.config.ConcurrencyManager;
import com.astrastore.replication.metadata.MetadataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the replication of a chunk to replica nodes.
 * Coordinates target selection, semaphore acquisition, push execution, and metadata update.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationOrchestrator {

    private final PlacementStrategy placementStrategy;
    private final ReplicationPushClient replicationPushClient;
    private final ConcurrencyManager concurrencyManager;
    private final MetadataClient metadataClient;

    /**
     * Copies to keep per chunk, counting the primary.
     *
     * <p>This used to be hardcoded at "two additional nodes", which on a
     * three-node cluster put every chunk on every node — three copies for a
     * configured factor of two. Storage filled half again as fast as the
     * configuration implied, and the capacity figures, which divide raw bytes
     * by this factor to estimate logical usage, were correspondingly wrong.
     */
    @Value("${astrastore.replication-factor:2}")
    private int replicationFactor;

    /**
     * Replicates a chunk until it reaches the configured factor.
     *
     * @param event the chunk written event from Kafka
     */
    public void orchestrateReplication(ChunkWrittenEvent event) {
        String chunkId = event.chunkId();
        String primaryNodeIp = event.primaryNodeIp();

        log.info("Replication orchestration started — chunkId={}, primary={}",
                chunkId, primaryNodeIp);

        // The primary already holds one copy, so only the remainder is placed.
        int replicasNeeded = Math.max(0, replicationFactor - 1);
        if (replicasNeeded == 0) {
            log.info("Replication factor is 1 — chunkId={} stays on the primary alone", chunkId);
            return;
        }

        List<String> targetNodes = placementStrategy.getNextTargetNodes(replicasNeeded, primaryNodeIp);

        if (targetNodes.isEmpty()) {
            log.warn("No target node available for replication — chunkId={}", chunkId);
            return;
        }

        // Fewer nodes than the factor asks for is a degraded cluster, not a
        // reason to abandon the copy that can be made.
        if (targetNodes.size() < replicasNeeded) {
            log.warn("Under-replicating chunkId={} — wanted {} replicas, {} nodes available",
                    chunkId, replicasNeeded, targetNodes.size());
        }

        for (String replicaNode : targetNodes) {
            replicateToReplica(event, primaryNodeIp, replicaNode);
        }

        log.info("Replication orchestration complete — chunkId={}, primary={}, replicas={}",
                chunkId, primaryNodeIp, targetNodes);
    }

    private void replicateToReplica(ChunkWrittenEvent event, String primaryNodeIp, String replicaNodeIp) {
        String chunkId = event.chunkId();

        if (!concurrencyManager.tryAcquire(primaryNodeIp)) {
            log.warn("Could not acquire permit for node — nodeIp={}, chunkId={}, skipping",
                    primaryNodeIp, chunkId);
            return;
        }

        try {
            ReplicationCommand command = ReplicationCommand.builder()
                    .chunkId(chunkId)
                    .targetNodeIp(replicaNodeIp)
                    .build();

            boolean success = replicationPushClient.sendPushCommand(primaryNodeIp, command);

            if (success) {
                metadataClient.addReplicaLocation(chunkId, replicaNodeIp);
                log.info("Chunk replicated successfully — chunkId={}, replica={}", chunkId, replicaNodeIp);
            } else {
                log.error("Chunk replication failed — chunkId={}, replica={}", chunkId, replicaNodeIp);
            }

        } finally {
            concurrencyManager.release(primaryNodeIp);
        }
    }
}
