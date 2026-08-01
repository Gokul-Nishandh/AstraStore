/**
 * Mock database simulating the Metadata Service's chunk registry.
 * In-memory store tracking which chunks exist on which storage nodes.
 * Used during Phase 4 to track replication state and trigger self-healing.
 * Replace with real Metadata Service client for production use.
 */
package com.astrastore.replication.db;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class MockChunkDatabase {

    private final Map<String, ReplicaRecord> chunkStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("MockChunkDatabase initialized — tracking {} chunks", chunkStore.size());
    }

    public void registerChunk(String chunkId, long sizeBytes, String checksum, int targetReplicas, List<String> initialNodes) {
        ReplicaRecord record = ReplicaRecord.builder()
                .chunkId(chunkId)
                .sizeBytes(sizeBytes)
                .checksum(checksum)
                .targetReplicas(targetReplicas)
                .build();

        for (String nodeIp : initialNodes) {
            record.addReplicaNode(nodeIp);
        }

        chunkStore.put(chunkId, record);
        log.info("Chunk registered — chunkId={}, targetReplicas={}, currentReplicas={}, nodes={}",
                chunkId, targetReplicas, record.getCurrentReplicas(), initialNodes);
    }

    public Optional<ReplicaRecord> findByChunkId(String chunkId) {
        return Optional.ofNullable(chunkStore.get(chunkId));
    }

    public List<ReplicaRecord> findUnderReplicatedChunks() {
        return chunkStore.values().stream()
                .filter(ReplicaRecord::isUnderReplicated)
                .collect(Collectors.toList());
    }

    public void updateReplicaCount(String chunkId, String additionalNodeIp) {
        ReplicaRecord record = chunkStore.get(chunkId);
        if (record != null) {
            record.addReplicaNode(additionalNodeIp);
            log.info("Replica added — chunkId={}, node={}, newReplicaCount={}",
                    chunkId, additionalNodeIp, record.getCurrentReplicas());
        }
    }

    public void decrementReplicaCount(String chunkId, String nodeIp) {
        ReplicaRecord record = chunkStore.get(chunkId);
        if (record != null) {
            record.removeReplicaNode(nodeIp);
            log.info("Replica removed (simulated failure) — chunkId={}, node={}, newReplicaCount={}",
                    chunkId, nodeIp, record.getCurrentReplicas());
        }
    }

    public int getTotalTrackedChunks() {
        return chunkStore.size();
    }

    public int getUnderReplicatedCount() {
        return findUnderReplicatedChunks().size();
    }
}
