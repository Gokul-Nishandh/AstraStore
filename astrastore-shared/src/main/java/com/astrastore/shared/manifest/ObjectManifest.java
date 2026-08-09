package com.astrastore.shared.manifest;

import lombok.Builder;

import java.util.List;

/**
 * Record representing the complete manifest for an uploaded object.
 * Contains the object id, the global hash and a list of all chunk manifests.
 *
 * @param objectId     the committed object id as persisted in the Metadata Service
 * @param globalHash   the SHA-256 checksum of the entire object
 * @param chunks       the list of chunk manifests in order
 */
@Builder
public record ObjectManifest(
        String objectId,
        String globalHash,
        List<ChunkManifest> chunks
) {
}
