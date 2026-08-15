package com.astrastore.metadata.service;

import com.astrastore.metadata.dto.chunk.ChunkPlacementResponse;
import com.astrastore.metadata.dto.chunk.NodeChunkResponse;
import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads chunk placement — which node holds which chunk — for the operations
 * console.
 *
 * <p>Everything here is cluster-wide operational data and none of it is
 * owner-scoped, which is why the only caller is an ADMIN-gated controller.
 * The ownership rule that governs {@link ObjectService} does not apply and
 * cannot be applied: a node's chunk listing spans every account by
 * definition. Do not reach for these methods from a user-facing handler.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChunkPlacementService {

    private final ChunkLocationRepository chunkLocationRepository;
    private final ObjectRepository objectRepository;

    /**
     * Every chunk of one object, in index order.
     *
     * <p>Not paged. An object's chunk count is bounded by its size — a 1 GB
     * object is 128 chunks — so the whole placement fits comfortably in one
     * response, and paging it would only make the console's table harder to
     * read for no benefit.
     */
    public List<ChunkPlacementResponse> placementsForObject(UUID objectId) {
        return chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(objectId).stream()
                .map(location -> new ChunkPlacementResponse(
                        location.getId(),
                        location.getChunkIndex(),
                        location.getNodeId(),
                        location.getReplicaNodeId(),
                        location.getReplicationStatus(),
                        location.getChecksum(),
                        location.getCreatedAt()))
                .toList();
    }

    /**
     * Every chunk one node holds, in either role, one page at a time.
     *
     * <p>The owning object is resolved for the page as a whole rather than per
     * row: fifty chunks commonly belong to a handful of objects, so this is
     * one extra query instead of fifty. An object that no longer exists leaves
     * its fields null — see {@link NodeChunkResponse}.
     *
     * @param nodeIds every identifier the node is known by. One node has two:
     *                the base URL the upload service records in
     *                {@code chunk_locations.node_id}, and the short name the
     *                placement registry uses. See
     *                {@link ChunkLocationRepository#findByNode}.
     */
    public Page<NodeChunkResponse> chunksOnNode(Collection<String> nodeIds, Pageable pageable) {
        Set<String> identities = Set.copyOf(nodeIds);

        Page<ChunkLocation> page = chunkLocationRepository.findByNode(identities, pageable);
        Map<UUID, ObjectRecord> objects = objectsFor(page.getContent());

        return page.map(location -> toResponse(location, identities, objects.get(location.getObjectId())));
    }

    private Map<UUID, ObjectRecord> objectsFor(List<ChunkLocation> locations) {
        if (locations.isEmpty()) {
            return Map.of();
        }

        List<UUID> objectIds = locations.stream()
                .map(ChunkLocation::getObjectId)
                .distinct()
                .toList();

        Map<UUID, ObjectRecord> byId = new HashMap<>();
        for (ObjectRecord record : objectRepository.findAllByIdInFetchingBucket(objectIds)) {
            byId.put(record.getId(), record);
        }
        return byId;
    }

    /**
     * A node holds a chunk as its primary or as its replica, and which one
     * decides what the other copy is. Primary wins if a row somehow names the
     * same node in both columns — that is a placement bug worth seeing rather
     * than a row worth hiding, and calling it PRIMARY at least reports the
     * node's real relationship to the bytes.
     */
    private NodeChunkResponse toResponse(ChunkLocation location, Set<String> nodeIds, ObjectRecord object) {
        boolean primary = nodeIds.contains(location.getNodeId());

        return new NodeChunkResponse(
                location.getId(),
                location.getObjectId(),
                object == null ? null : object.getKey(),
                object == null || object.getBucket() == null ? null : object.getBucket().getId(),
                object == null || object.getBucket() == null ? null : object.getBucket().getName(),
                object == null ? null : object.getSizeBytes(),
                location.getChunkIndex(),
                primary ? NodeChunkResponse.ChunkRole.PRIMARY : NodeChunkResponse.ChunkRole.REPLICA,
                primary ? location.getReplicaNodeId() : location.getNodeId(),
                location.getReplicationStatus(),
                location.getChecksum(),
                location.getCreatedAt());
    }
}
