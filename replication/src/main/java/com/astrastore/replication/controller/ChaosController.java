/**
 * Chaos engineering endpoint for Phase 4 testing.
 * Allows artificially dropping replica counts to simulate node failures.
 * Enables deterministic testing of the self-healing pipeline.
 */
package com.astrastore.replication.controller;

import com.astrastore.replication.db.MockChunkDatabase;
import com.astrastore.replication.db.ReplicaRecord;
import com.astrastore.replication.dto.KillNodeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/chaos")
@RequiredArgsConstructor
@Slf4j
public class ChaosController {

    private final MockChunkDatabase mockChunkDatabase;

    @PostMapping("/kill-node")
    public ResponseEntity<?> killNode(@Valid @RequestBody KillNodeRequest request) {
        ReplicaRecord record = mockChunkDatabase.findByChunkId(request.getChunkId())
                .orElse(null);

        if (record == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.getReplicasToDrop() > record.getCurrentReplicas()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot drop more replicas than exist",
                    "chunkId", request.getChunkId(),
                    "currentReplicas", record.getCurrentReplicas(),
                    "requestedDrop", request.getReplicasToDrop()
            ));
        }

        for (int i = 0; i < request.getReplicasToDrop(); i++) {
            if (!record.getReplicaNodeIps().isEmpty()) {
                String nodeToRemove = record.getReplicaNodeIps().get(record.getReplicaNodeIps().size() - 1);
                mockChunkDatabase.decrementReplicaCount(request.getChunkId(), nodeToRemove);
            }
        }

        log.info("Chaos: Simulated node failure — chunkId={}, replicasDropped={}, newReplicaCount={}",
                request.getChunkId(), request.getReplicasToDrop(), record.getCurrentReplicas());

        return ResponseEntity.ok(Map.of(
                "message", "Node failure simulated",
                "chunkId", request.getChunkId(),
                "replicasDropped", request.getReplicasToDrop(),
                "newReplicaCount", record.getCurrentReplicas(),
                "underReplicated", record.isUnderReplicated()
        ));
    }
}
