/**
 * Admin API for managing chunk metadata in MockChunkDatabase.
 * Endpoints to register chunks and query replication tracking status.
 * Used for testing Phase 4 self-healing with real chunk IDs.
 */
package com.astrastore.replication.controller;

import com.astrastore.replication.db.MockChunkDatabase;
import com.astrastore.replication.dto.RegisterChunkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/metadata")
@RequiredArgsConstructor
@Slf4j
public class MetadataAdminController {

    private final MockChunkDatabase mockChunkDatabase;

    @PostMapping("/register")
    public ResponseEntity<?> registerChunk(@Valid @RequestBody RegisterChunkRequest request) {
        mockChunkDatabase.registerChunk(
                request.getChunkId(),
                request.getSizeBytes(),
                request.getChecksum(),
                request.getTargetReplicas(),
                request.getReplicaNodes()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Chunk registered successfully",
                "chunkId", request.getChunkId(),
                "targetReplicas", request.getTargetReplicas(),
                "currentReplicas", request.getReplicaNodes().size()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "totalTrackedChunks", mockChunkDatabase.getTotalTrackedChunks(),
                "underReplicatedChunks", mockChunkDatabase.getUnderReplicatedCount()
        ));
    }
}
