package com.astrastore.download.client;

import java.util.UUID;

public record ObjectMetadata(
        UUID id,
        UUID bucketId,
        String key,
        Long sizeBytes,
        String checksum,
        String contentType,
        String status,
        String createdAt,
        Long chunksReplicated,
        Long chunksTotal
) {
}
