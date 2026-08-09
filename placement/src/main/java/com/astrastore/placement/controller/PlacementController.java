package com.astrastore.placement.controller;

import com.astrastore.shared.strategy.PlacementStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for providing placement targets to other modules (Upload, Replication).
 */
@RestController
@RequestMapping("/api/v1/placement")
@RequiredArgsConstructor
public class PlacementController {

    private final PlacementStrategy placementStrategyService;


    /**
     * Returns the base URL of the single best storage node for a new upload.
     */
    @GetMapping("/next")
    public ResponseEntity<String> getNextTargetNode() {
        String target = placementStrategyService.getNextTargetNode();
        if (target == null) {
            return ResponseEntity.status(503).body("No eligible storage nodes available");
        }
        return ResponseEntity.ok(target);
    }

    /**
     * Returns a list of base URLs for multiple storage nodes.
     * Used primarily for replication (e.g., get 2 nodes, excluding the primary).
     *
     * @param count       the number of target nodes requested
     * @param excludeNode the base URL or node ID to exclude (e.g., primary node)
     */
    @GetMapping("/next/multiple")
    public ResponseEntity<List<String>> getNextTargetNodes(
            @RequestParam int count,
            @RequestParam(required = false) String excludeNode) {
        
        List<String> targets = placementStrategyService.getNextTargetNodes(count, excludeNode);
        if (targets.isEmpty()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(targets);
    }
}
