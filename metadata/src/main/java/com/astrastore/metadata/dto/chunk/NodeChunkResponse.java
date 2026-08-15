package com.astrastore.metadata.dto.chunk;

import com.astrastore.metadata.entity.ReplicationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One chunk held by one node, seen from the node's side.
 *
 * <p>The object fields are nullable on purpose. A chunk row outliving its
 * object is a real state — a cleanup event that never landed, a chunk written
 * against an object whose creation then failed — and it is precisely the kind
 * of thing an operator opens this view to find. Reporting the chunk with a
 * blank key is honest; suppressing the row would hide the orphan.
 *
 * @param role            whether this node holds the primary copy or the replica
 * @param peerNodeId      the node holding the other copy, or null when there is
 *                        not one yet
 * @param objectSizeBytes the size of the whole <em>object</em>, not of this
 *                        chunk. Per-chunk byte counts are not recorded — the
 *                        upload service splits on an 8 MiB boundary but writes
 *                        no length into {@code chunk_locations} — so this is
 *                        the only size figure that exists, and the console
 *                        labels it as the object's.
 */
public record NodeChunkResponse(

        UUID id,

        UUID objectId,

        String objectKey,

        UUID bucketId,

        String bucketName,

        Long objectSizeBytes,

        Integer chunkIndex,

        ChunkRole role,

        String peerNodeId,

        ReplicationStatus replicationStatus,

        String checksum,

        Instant createdAt

) {
    /** Which copy of the chunk the node being inspected is holding. */
    public enum ChunkRole {
        PRIMARY,
        REPLICA
    }
}
