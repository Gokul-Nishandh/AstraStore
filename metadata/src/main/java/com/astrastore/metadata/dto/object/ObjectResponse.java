package com.astrastore.metadata.dto.object;

import com.astrastore.metadata.entity.ObjectStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The one object shape every listing and detail endpoint returns, so the
 * dashboard's table component works unchanged across buckets, search, recent,
 * starred and trash.
 *
 * @param starred   resolved for the <em>calling</em> user, never global
 * @param deletedAt when the object was moved to trash; {@code null} while active
 */
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

        Long chunksTotal,

        boolean starred,

        Instant deletedAt

) {
}
