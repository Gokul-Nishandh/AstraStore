package com.astrastore.placement.registry;

import com.astrastore.placement.model.NodeState;
import com.astrastore.placement.model.StorageNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory, thread-safe registry of all known storage nodes.
 *
 * <p>Uses a {@link ConcurrentHashMap} keyed by node ID.  Individual node
 * metric fields are further guarded by atomic types inside {@link StorageNode},
 * so the registry never needs to lock the entire map for a health update.</p>
 *
 * <p>The registry is populated once at startup by {@code PlacementConfig#seedNodeRegistry}
 * and then updated in-place by the {@code HeartbeatService} on every poll cycle.</p>
 */
@Component
@Slf4j
public class NodeRegistry {

    /** Primary store: nodeId → StorageNode. */
    private final Map<String, StorageNode> registry = new ConcurrentHashMap<>();

    // ----------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------

    /**
     * Registers a new storage node with default (HEALTHY) state.
     * Called once at startup by {@code PlacementConfig}.
     *
     * @param nodeId  human-readable identifier (e.g. "storage-node-1")
     * @param baseUrl full URL (e.g. "http://storage-node-1:8088")
     */
    public void registerNode(String nodeId, String baseUrl) {
        StorageNode node = StorageNode.builder()
                .nodeId(nodeId)
                .baseUrl(baseUrl)
                .build();
        registry.put(nodeId, node);
        log.debug("Registered node — id={}, url={}", nodeId, baseUrl);
    }

    // ----------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------

    /**
     * Returns the {@link StorageNode} for the given ID, or {@link Optional#empty()}
     * if the node is not registered.
     */
    public Optional<StorageNode> findById(String nodeId) {
        return Optional.ofNullable(registry.get(nodeId));
    }

    /**
     * Returns an unmodifiable view of <em>all</em> registered nodes,
     * regardless of their current health state.
     */
    public Collection<StorageNode> getAllNodes() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * Returns only nodes that are currently eligible for new data placement
     * (i.e. HEALTHY or DEGRADED).
     *
     * @see StorageNode#isEligibleForPlacement()
     */
    public Collection<StorageNode> getEligibleNodes() {
        return registry.values().stream()
                .filter(StorageNode::isEligibleForPlacement)
                .collect(Collectors.toList());
    }

    /**
     * Returns nodes filtered by a specific {@link NodeState}.
     */
    public Collection<StorageNode> getNodesByState(NodeState state) {
        return registry.values().stream()
                .filter(n -> n.getState().get() == state)
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // Summary / diagnostics
    // ----------------------------------------------------------------

    /**
     * Returns the total number of registered nodes.
     */
    public int size() {
        return registry.size();
    }

    /**
     * Returns a formatted summary string of all node states — useful for
     * periodic log lines inside the HeartbeatService.
     */
    public String summary() {
        return registry.values().stream()
                .map(n -> String.format("%s(%s)", n.getNodeId(), n.getState().get()))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
