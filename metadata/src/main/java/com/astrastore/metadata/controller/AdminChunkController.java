package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.chunk.ChunkPlacementResponse;
import com.astrastore.metadata.dto.chunk.NodeChunkResponse;
import com.astrastore.metadata.service.ChunkPlacementService;
import com.astrastore.metadata.web.Pageables;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Chunk placement, for operators.
 *
 * <p><strong>ADMIN only, and the class-level {@code @PreAuthorize} is what
 * makes that true.</strong> Every other read surface in this service is scoped
 * to the caller's own rows; these two are not, and cannot be. A node's chunk
 * listing spans every account in the cluster by definition, and an object's
 * placement describes infrastructure rather than content. Without the
 * annotation, {@code GET /api/v1/admin/objects/{id}/chunks} would let any
 * authenticated caller confirm that another account's object exists and count
 * its chunks — which is exactly the disclosure
 * {@code ObjectService.requireOwned} exists to prevent. The dashboard's
 * {@code AdminGuard} hides the navigation; it does not enforce anything.
 *
 * <p>Kept apart from {@link ChunkIndexController}, which serves the same
 * underlying rows to download and replication over {@code /internal/v1} on a
 * shared service token. Two surfaces, two audiences, two authorisation models
 * — merging them would mean one of the two is wrong.
 *
 * <p>Note for anyone adding a route: {@code /api/v1/admin/**} at the gateway
 * belongs to the <em>replication</em> service. These two paths are carved out
 * ahead of it by name, so a third path added here needs a gateway route of its
 * own or it will be answered by the wrong service.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminChunkController {

    /**
     * Newest placements first. A chunk written seconds ago is the one an
     * operator watching a write land is looking for.
     */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ChunkPlacementService chunkPlacementService;

    /**
     * Every chunk a node holds, as primary or as replica.
     *
     * <p>Paged — a node in a real deployment holds far more chunks than any
     * single response should carry, and {@code Pageables.sanitize} clamps the
     * size a caller can ask for.
     *
     * <p>{@code nodeId} is repeatable, because a node has more than one name.
     * The upload service records the node's base URL in
     * {@code chunk_locations.node_id} — the download service reads that same
     * column and fetches the bytes from it — while the placement registry
     * knows the node as {@code storage-node-1}. Send every identifier you hold
     * for the node ({@code ?nodeId=storage-node-1&nodeId=http://storage-node-1:8088})
     * and each is matched exactly. Sending only one is not wrong, it just
     * finds only the rows written under that name.
     */
    @GetMapping("/chunks")
    public ResponseEntity<Page<NodeChunkResponse>> chunksOnNode(
            @RequestParam @NotEmpty List<@NotBlank @Size(max = 64) String> nodeId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(chunkPlacementService.chunksOnNode(
                nodeId.stream().map(String::trim).toList(),
                Pageables.sanitize(pageable, Pageables.CHUNK_SORTS, DEFAULT_SORT)));
    }

    /**
     * Where each chunk of one object lives.
     *
     * <p>An object with no recorded placements returns an empty list rather
     * than a 404: the object may exist and simply not have been chunk-indexed
     * yet, and this endpoint cannot tell those apart without a second lookup
     * that would answer a question the caller did not ask.
     */
    @GetMapping("/objects/{objectId}/chunks")
    public ResponseEntity<List<ChunkPlacementResponse>> chunksOfObject(@PathVariable UUID objectId) {
        return ResponseEntity.ok(chunkPlacementService.placementsForObject(objectId));
    }
}
