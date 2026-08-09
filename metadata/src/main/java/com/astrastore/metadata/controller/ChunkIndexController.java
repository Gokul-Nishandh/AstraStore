package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.chunk.ChunkLocationResponse;
import com.astrastore.metadata.dto.chunk.RecordChunkLocationsRequest;
import com.astrastore.metadata.dto.chunk.RecordChunkLocationsResponse;
import com.astrastore.metadata.dto.chunk.UpdateReplicaRequest;
import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.service.ObjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/objects/{objectId}/chunk-locations")
@RequiredArgsConstructor
public class ChunkIndexController {

    private final ObjectService objectService;

    @PostMapping
    public ResponseEntity<RecordChunkLocationsResponse> recordChunkLocations(
            @PathVariable UUID objectId,
            @Valid @RequestBody RecordChunkLocationsRequest request) {

        List<ChunkLocation> locations = request.chunks().stream()
                .map(item -> ChunkLocation.builder()
                        .objectId(objectId)
                        .chunkIndex(item.chunkIndex())
                        .nodeId(item.nodeId())
                        .checksum(item.checksum() != null ? item.checksum() : "")
                        .replicationStatus(ReplicationStatus.PENDING)
                        .build())
                .toList();

        List<ChunkLocation> saved = objectService.recordChunkLocations(locations);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RecordChunkLocationsResponse(objectId, saved.size()));
    }

    @GetMapping
    public ResponseEntity<List<ChunkLocationResponse>> getChunkLocations(
            @PathVariable UUID objectId) {

        List<ChunkLocationResponse> responses = objectService.getChunkLocations(objectId).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{chunkIndex}")
    public ResponseEntity<ChunkLocationResponse> updateChunkReplica(
            @PathVariable UUID objectId,
            @PathVariable Integer chunkIndex,
            @RequestBody UpdateReplicaRequest request) {

        ChunkLocation updated = objectService.updateChunkReplica(
                objectId,
                chunkIndex,
                request.replicaNodeId(),
                request.replicationStatus()
        );

        return ResponseEntity.ok(toResponse(updated));
    }

    private ChunkLocationResponse toResponse(ChunkLocation loc) {
        return new ChunkLocationResponse(
                loc.getId(),
                loc.getObjectId(),
                loc.getChunkIndex(),
                loc.getNodeId(),
                loc.getReplicaNodeId(),
                loc.getReplicationStatus(),
                loc.getChecksum()
        );
    }
}
