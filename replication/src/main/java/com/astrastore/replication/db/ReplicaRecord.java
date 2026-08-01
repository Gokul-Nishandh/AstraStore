/**
 * Represents a chunk's replication state in the MockChunkDatabase.
 * Tracks which nodes currently hold a replica vs the target replication factor.
 * Used by the UnderReplicationScanner to identify chunks needing self-healing.
 */
package com.astrastore.replication.db;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ReplicaRecord {

    private final String chunkId;
    private final long sizeBytes;
    private final String checksum;
    private int targetReplicas;
    private List<String> replicaNodeIps;

    @Builder
    public ReplicaRecord(String chunkId, long sizeBytes, String checksum, int targetReplicas) {
        this.chunkId = chunkId;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.targetReplicas = targetReplicas;
        this.replicaNodeIps = new ArrayList<>();
    }

    public void addReplicaNode(String nodeIp) {
        if (!replicaNodeIps.contains(nodeIp)) {
            replicaNodeIps.add(nodeIp);
        }
    }

    public void removeReplicaNode(String nodeIp) {
        replicaNodeIps.remove(nodeIp);
    }

    public int getCurrentReplicas() {
        return replicaNodeIps.size();
    }

    public boolean isUnderReplicated() {
        return getCurrentReplicas() < targetReplicas;
    }

    public List<String> getHealthyReplicas() {
        return new ArrayList<>(replicaNodeIps);
    }
}
