package com.astrastore.metadata.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when an object is permanently deleted, one event per chunk, so
 * storage nodes can reclaim the bytes.
 *
 * <p>{@code chunkId} uses the same {@code <objectId>-chunk-<index>} form the
 * upload and replication services already key chunks by, so consumers need no
 * new parsing.
 *
 * <p>Declared here rather than in {@code astrastore-shared} because this
 * module owns the deletion lifecycle; promoting it to the shared module is a
 * reasonable follow-up once a consumer exists.
 */
public record ChunkCleanupEvent(

        UUID objectId,

        String chunkId,

        Integer chunkIndex,

        String nodeId,

        String replicaNodeId,

        Instant deletedAt

) {
    public static String chunkId(UUID objectId, Integer chunkIndex) {
        return objectId + "-chunk-" + chunkIndex;
    }
}
