package com.astrastore.shared.manifest;

import lombok.Builder;

/**
 * Record representing metadata for a single stored chunk.
 *
 * @param chunkId    the unique identifier for this chunk
 * @param nodeIp     the IP address of the storage node holding this chunk
 * @param checksum   the SHA-256 checksum of the chunk data
 * @param sizeBytes  the size of the chunk in bytes
 */
@Builder
public record ChunkManifest(
        String chunkId,
        String nodeIp,
        String checksum,
        Long sizeBytes
) {
}
