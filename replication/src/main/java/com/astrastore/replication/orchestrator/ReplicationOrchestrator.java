package com.astrastore.replication.orchestrator;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.shared.events.ReplicationCommand;
import com.astrastore.shared.strategy.PlacementStrategy;
import com.astrastore.replication.client.ReplicationPushClient;
import com.astrastore.replication.config.ConcurrencyManager;
import com.astrastore.replication.metadata.MetadataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * Replicates a chunk to two additional storage nodes.
     *
     * @param event the chunk written event from Kafka
     */
    public void orchestrateReplication(ChunkWrittenEvent event) {
        String chunkId = event.chunkId();
        String primaryNodeIp = event.primaryNodeIp();

        log.info("Replication orchestration started — chunkId={}, primary={}",
                chunkId, primaryNodeIp);

        List<String> targetNodes = placementStrategy.getNextTargetNodes(2, primaryNodeIp);

        if (targetNodes.size() < 2) {
            log.warn("Not enough target nodes available — chunkId={}, available={}",
                    chunkId, targetNodes.size());
            return;
        }

        String replicaNode1 = targetNodes.get(0);
        String replicaNode2 = targetNodes.get(1);

        replicateToReplica(event, primaryNodeIp, replicaNode1);
        replicateToReplica(event, primaryNodeIp, replicaNode2);

        log.info("Replication orchestration complete — chunkId={}, primary={}, replicas=[{}, {}]",
                chunkId, primaryNodeIp, replicaNode1, replicaNode2);
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
