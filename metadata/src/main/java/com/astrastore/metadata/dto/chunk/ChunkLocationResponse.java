package com.astrastore.metadata.dto.chunk;

import com.astrastore.metadata.entity.ReplicationStatus;

import java.util.UUID;

public record ChunkLocationResponse(

        UUID id,

        UUID objectId,

        Integer chunkIndex,

        String nodeId,

        String replicaNodeId,

        ReplicationStatus replicationStatus,

        String checksum

) {
}