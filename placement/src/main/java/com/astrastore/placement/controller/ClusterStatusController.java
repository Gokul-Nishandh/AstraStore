package com.astrastore.placement.controller;

import com.astrastore.placement.model.NodeState;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for querying the real-time health of the storage cluster.
 *
 * <p>Intended consumers:</p>
 * <ul>
 *   <li>Module 4 React Dashboard — polls {@code GET /api/v1/cluster/status}</li>
 *   <li>Ops engineers for ad-hoc diagnostics</li>
 *   <li>Prometheus (via a custom metric endpoint, added in Phase 2)</li>
 * </ul>
 *
 * <p>All responses are plain {@link Map} objects to keep the controller
 * free of additional DTO classes for Phase 1. Dedicated response records
 * can be introduced in Phase 2 as the API stabilises.</p>
 */
@RestController
@RequestMapping("/api/v1/cluster")
@RequiredArgsConstructor
@Slf4j
public class ClusterStatusController {

    private final NodeRegistry nodeRegistry;

    // ----------------------------------------------------------------
    // Endpoints
    // ----------------------------------------------------------------

    /**
     * Returns an aggregated view of the entire cluster.
     *
     * <p>Sample response:</p>
     * <pre>
     * {
     *   "totalNodes": 3,
     *   "healthyNodes": 2,
     *   "degradedNodes": 1,
     *   "downNodes": 0,
     *   "recoveringNodes": 0,
     *   "eligibleNodes": 3,
     *   "totalDiskBytes": 322122547200,
     *   "totalFreeBytes": 280000000000,
     *   "clusterFreeRatio": 0.87,
     *   "checkedAt": "2025-01-15T10:30:00Z",
     *   "nodes": [ ... ]
     * }
     * </pre>
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> clusterStatus() {
        Collection<StorageNode> all = nodeRegistry.getAllNodes();

        long healthy    = countByState(all, NodeState.HEALTHY);
        long degraded   = countByState(all, NodeState.DEGRADED);
        long down       = countByState(all, NodeState.DOWN);
        long recovering = countByState(all, NodeState.RECOVERING);
        long eligible   = all.stream().filter(StorageNode::isEligibleForPlacement).count();

        long totalDisk = all.stream().mapToLong(n -> n.getDiskTotalBytes().get()).sum();
        long totalFree = all.stream().mapToLong(n -> n.getDiskFreeBytes().get()).sum();
        double freeRatio = totalDisk == 0 ? 0.0 : (double) totalFree / totalDisk;

        List<Map<String, Object>> nodeViews = all.stream()
                .map(this::toNodeView)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalNodes",       all.size());
        response.put("healthyNodes",     healthy);
        response.put("degradedNodes",    degraded);
        response.put("downNodes",        down);
        response.put("recoveringNodes",  recovering);
        response.put("eligibleNodes",    eligible);
        response.put("totalDiskBytes",   totalDisk);
        response.put("totalFreeBytes",   totalFree);
        response.put("clusterFreeRatio", String.format("%.4f", freeRatio));
        response.put("checkedAt",        Instant.now().toString());
        response.put("nodes",            nodeViews);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns the detailed status of a single storage node by its ID.
     *
     * <p>Returns 404 if no node with the given ID is registered.</p>
     *
     * @param nodeId the node ID (e.g. "storage-node-1")
     */
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<Map<String, Object>> nodeStatus(@PathVariable String nodeId) {
        return nodeRegistry.findById(nodeId)
                .map(node -> ResponseEntity.ok(toNodeView(node)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the list of nodes currently eligible for write placement
     * (state = HEALTHY or DEGRADED).
     */
    @GetMapping("/nodes/eligible")
    public ResponseEntity<List<Map<String, Object>>> eligibleNodes() {
        List<Map<String, Object>> eligible = nodeRegistry.getEligibleNodes()
                .stream()
                .map(this::toNodeView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(eligible);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /** Converts a {@link StorageNode} to a flat map for JSON serialisation. */
    private Map<String, Object> toNodeView(StorageNode node) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nodeId",            node.getNodeId());
        view.put("baseUrl",           node.getBaseUrl());
        view.put("state",             node.getState().get().name());
        view.put("lastSeen",          node.getLastSeen().get() != null ? node.getLastSeen().get().toString() : null);
        view.put("lastChecked",       node.getLastChecked().get() != null ? node.getLastChecked().get().toString() : null);
        view.put("consecutiveFailures", node.getConsecutiveFailures().get());
        view.put("diskTotalBytes",    node.getDiskTotalBytes().get());
        view.put("diskFreeBytes",     node.getDiskFreeBytes().get());
        view.put("diskUsedBytes",     node.getDiskUsedBytes().get());
        view.put("diskFreeRatio",     String.format("%.4f", node.getDiskFreeRatio()));
        view.put("eligibleForPlacement", node.isEligibleForPlacement());
        return view;
    }

    private long countByState(Collection<StorageNode> nodes, NodeState state) {
        return nodes.stream().filter(n -> n.getState().get() == state).count();
    }
}
