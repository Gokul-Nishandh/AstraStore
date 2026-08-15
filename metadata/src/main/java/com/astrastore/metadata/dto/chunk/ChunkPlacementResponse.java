package com.astrastore.metadata.dto.chunk;

import com.astrastore.metadata.entity.ReplicationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Where one chunk of an object physically lives.
 *
 * <p>Distinct from {@link ChunkLocationResponse}, which is the internal
 * service-to-service shape download and replication consume. This one is for
 * a human reading the console: it carries {@code createdAt} (when the
 * placement was recorded) and omits {@code objectId}, which the caller
 * already supplied in the path.
 *
 * <p>{@code replicaNodeId} is null until replication has chosen a peer — that
 * is "not placed yet", not "placed nowhere", and the console renders it as an
 * em dash rather than inventing a node.
 */
public record ChunkPlacementResponse(

        UUID id,

        Integer chunkIndex,

        String nodeId,

        String replicaNodeId,

        ReplicationStatus replicationStatus,

        String checksum,

        Instant createdAt

) {
}
