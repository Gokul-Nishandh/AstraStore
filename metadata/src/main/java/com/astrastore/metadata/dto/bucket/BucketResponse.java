package com.astrastore.metadata.dto.bucket;

import java.time.Instant;
import java.util.UUID;

public record BucketResponse(

        UUID id,

        String name,

        UUID ownerId,

        Instant createdAt

) {
}