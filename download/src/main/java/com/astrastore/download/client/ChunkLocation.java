package com.astrastore.download.client;

import java.util.UUID;

public record ChunkLocation(
        UUID id,
        UUID objectId,
        Integer chunkIndex,
        String nodeId,
        String replicaNodeId,
        String replicationStatus,
        String checksum
) {
}
