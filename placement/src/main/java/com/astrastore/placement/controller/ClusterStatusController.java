package com.astrastore.placement.controller;

import com.astrastore.placement.model.ClusterCapacity;
import com.astrastore.placement.model.NodeState;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import com.astrastore.placement.service.ClusterCapacityService;
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
 * REST API for querying the real-time health and capacity of the storage cluster.
 *
 * <h2>Capacity reporting</h2>
 * <p>This endpoint used to sum each node's {@code FileStore} total. Every
 * node in a local stack is a container on the operator's single drive, so the
 * "cluster" appeared three times larger than the machine it ran on, and its
 * "used" figure tracked the operator's own files rather than anything
 * AstraStore had stored.
 *
 * <p>Totals are now built from per-node quotas and per-node real usage, and
 * both the raw and the logical view of stored bytes are published so the
 * replication cost is visible instead of hidden. Existing field names are
 * preserved; the new fields sit alongside them.
 */
@RestController
@RequestMapping("/api/v1/cluster")
@RequiredArgsConstructor
@Slf4j
public class ClusterStatusController {

    private final NodeRegistry nodeRegistry;
    private final ClusterCapacityService capacityService;

    // ----------------------------------------------------------------
    // Endpoints
    // ----------------------------------------------------------------

    /**
     * Returns an aggregated view of the entire cluster.
     *
     * <p>Sample response (3 nodes, 20 GiB quota each, replication factor 2):</p>
     * <pre>
     * {
     *   "totalNodes": 3,
     *   "healthyNodes": 3,
     *   "degradedNodes": 0,
     *   "downNodes": 0,
     *   "recoveringNodes": 0,
     *   "eligibleNodes": 3,
     *   "reportingNodes": 3,
     *   "insufficientData": false,
     *   "totalCapacityBytes": 64424509440,
     *   "usedBytes": 8388608,
     *   "availableBytes": 64416120832,
     *   "usedRatio": 0.00013,
     *   "totalChunkCount": 24,
     *   "replicationFactor": 2,
     *   "rawBytesStored": 8388608,
     *   "logicalBytesStored": 4194304,
     *   "logicalBytesAvailable": 32208060416,
     *   "replicationOverheadBytes": 4194304,
     *   "checkedAt": "2026-08-13T10:30:00Z",
     *   "nodes": [ ... ]
     * }
     * </pre>
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> clusterStatus() {
        Collection<StorageNode> all = nodeRegistry.getAllNodes();
        ClusterCapacity capacity = capacityService.current();

        long healthy    = countByState(all, NodeState.HEALTHY);
        long degraded   = countByState(all, NodeState.DEGRADED);
        long down       = countByState(all, NodeState.DOWN);
        long recovering = countByState(all, NodeState.RECOVERING);
        long eligible   = all.stream().filter(StorageNode::isEligibleForPlacement).count();

        List<Map<String, Object>> nodeViews = all.stream()
                .map(ClusterStatusController::toNodeView)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        // --- Unchanged keys the dashboard already binds to ---------------
        response.put("totalNodes",       all.size());
        response.put("healthyNodes",     healthy);
        response.put("degradedNodes",    degraded);
        response.put("downNodes",        down);
        response.put("recoveringNodes",  recovering);
        response.put("eligibleNodes",    eligible);

        // --- Honest capacity ---------------------------------------------
        response.put("reportingNodes",           capacity.reportingNodes());
        // True when no node has reported a quota yet. Everything below is
        // null in that case; it is never padded out with zeros.
        response.put("insufficientData",         capacity.insufficientData());
        response.put("totalCapacityBytes",       capacity.totalCapacityBytes());
        response.put("usedBytes",                capacity.usedBytes());
        response.put("availableBytes",           capacity.availableBytes());
        response.put("usedRatio",                capacity.usedRatio());
        response.put("totalChunkCount",          capacity.totalChunkCount());

        // --- Replication economics ---------------------------------------
        response.put("replicationFactor",        capacity.replicationFactor());
        response.put("rawBytesStored",           capacity.rawBytesStored());
        response.put("logicalBytesStored",       capacity.logicalBytesStored());
        response.put("logicalBytesAvailable",    capacity.logicalBytesAvailable());
        response.put("replicationOverheadBytes", capacity.replicationOverheadBytes());
        // logicalBytesStored is derived from rawBytesStored and the configured
        // factor, not measured per object. Flagged so the UI can label it.
        response.put("logicalBytesIsEstimate",   true);

        response.put("checkedAt",        Instant.now().toString());
        response.put("nodes",            nodeViews);

        // --- Deprecated aliases, now backed by quotas rather than host disks
        response.put("totalDiskBytes",   orZero(capacity.totalCapacityBytes()));
        response.put("totalFreeBytes",   orZero(capacity.availableBytes()));
        response.put("clusterFreeRatio", freeRatio(capacity));

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
                .map(ClusterStatusController::toNodeView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(eligible);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /** Converts a {@link StorageNode} to a flat map for JSON serialisation. */
    private static Map<String, Object> toNodeView(StorageNode node) {
        boolean reported = node.getCapacityReported().get();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("nodeId",            node.getNodeId());
        view.put("baseUrl",           node.getBaseUrl());
        view.put("state",             node.getState().get().name());
        view.put("lastSeen",          asIso(node.getLastSeen().get()));
        view.put("lastChecked",       asIso(node.getLastChecked().get()));
        view.put("consecutiveFailures", node.getConsecutiveFailures().get());

        // Null until the node has actually told us its quota — a nought here
        // would read as "this node holds nothing", which is a different claim.
        view.put("capacityReported",  reported);
        view.put("capacityBytes",     reported ? node.getCapacityBytes().get() : null);
        view.put("usedBytes",         reported ? node.getUsedBytes().get() : null);
        view.put("availableBytes",    reported ? node.getAvailableBytes().get() : null);
        view.put("chunkCount",        reported ? node.getChunkCount().get() : null);
        view.put("usedRatio",         reported ? node.getUsedRatio() : null);

        // Advisory only. Shared by every container on one host — do not sum.
        long hostFree = node.getHostDiskFreeBytes().get();
        view.put("hostDiskFreeBytes", hostFree > 0 ? hostFree : null);

        view.put("eligibleForPlacement", node.isEligibleForPlacement());

        // Deprecated aliases, kept so existing clients keep parsing.
        view.put("diskTotalBytes",    reported ? node.getCapacityBytes().get() : 0L);
        view.put("diskFreeBytes",     reported ? node.getAvailableBytes().get() : 0L);
        view.put("diskUsedBytes",     reported ? node.getUsedBytes().get() : 0L);
        view.put("diskFreeRatio",     node.getFreeRatio());
        return view;
    }

    private static String asIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private static double freeRatio(ClusterCapacity capacity) {
        if (capacity.totalCapacityBytes() == null || capacity.totalCapacityBytes() == 0L) return 0.0;
        return (double) capacity.availableBytes() / capacity.totalCapacityBytes();
    }

    private static long countByState(Collection<StorageNode> nodes, NodeState state) {
        return nodes.stream().filter(n -> n.getState().get() == state).count();
    }
}
