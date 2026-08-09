package com.astrastore.metadata.dto.object;

import com.astrastore.metadata.entity.ObjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ObjectResponse(

        UUID id,

        UUID bucketId,

        String key,

        Long sizeBytes,

        String checksum,

        String contentType,

        ObjectStatus status,

        Instant createdAt,

        Long chunksReplicated,

        Long chunksTotal

) {
}