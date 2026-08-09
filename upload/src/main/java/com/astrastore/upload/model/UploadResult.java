package com.astrastore.upload.model;

import java.time.Instant;
import java.util.UUID;

public record UploadResult(
        UUID objectId,
        UUID bucketId,
        String key,
        Long sizeBytes,
        String checksum,
        int chunkCount,
        Instant createdAt
) {
}
