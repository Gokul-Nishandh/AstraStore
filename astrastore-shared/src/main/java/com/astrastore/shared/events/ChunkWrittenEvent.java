package com.astrastore.shared.events;

import lombok.Builder;

/**
 * Event published to Kafka when a chunk has been successfully written to a storage node.
 * Consumed by the Replication Service to trigger async replication to replica nodes.
 *
 * @param chunkId      the unique identifier for this chunk
 * @param primaryNodeIp the IP address of the storage node where the chunk was written
 * @param sizeBytes    the size of the chunk in bytes
 * @param checksum     the SHA-256 checksum of the chunk data
 */
@Builder
public record ChunkWrittenEvent(
        String chunkId,
        String primaryNodeIp,
        Long sizeBytes,
        String checksum
) {
}
