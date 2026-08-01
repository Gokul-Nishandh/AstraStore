package com.astrastore.shared.events;

import lombok.Builder;

/**
 * Command sent to a storage node to trigger push replication of a chunk to a target node.
 * The primary node receives this command and streams the chunk to the specified target node.
 *
 * @param chunkId      the unique identifier for the chunk to replicate
 * @param targetNodeIp the IP address of the target storage node to receive the chunk
 */
@Builder
public record ReplicationCommand(
        String chunkId,
        String targetNodeIp
) {
}
