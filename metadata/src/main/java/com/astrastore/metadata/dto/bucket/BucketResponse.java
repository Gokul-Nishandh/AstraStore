package com.astrastore.metadata.dto.bucket;

import java.time.Instant;
import java.util.UUID;

/**
 * @param id          bucket id
 * @param name        bucket name
 * @param ownerId     legacy UUID owner key, derived from {@code ownerUserId}
 * @param createdAt   creation timestamp
 * @param ownerUserId the authenticated owner's numeric user id — the field to
 *                    compare against the signed-in user
 */
public record BucketResponse(

        UUID id,

        String name,

        UUID ownerId,

        Instant createdAt,

        Long ownerUserId

) {
}
