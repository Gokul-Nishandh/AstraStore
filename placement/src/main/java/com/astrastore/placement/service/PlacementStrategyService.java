package com.astrastore.placement.service;

import com.astrastore.placement.model.NodeState;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import com.astrastore.shared.strategy.PlacementStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Intelligent placement strategy based on weighted node scoring.
 * Replaces the mock round-robin implementation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlacementStrategyService implements PlacementStrategy {

    private final NodeRegistry nodeRegistry;

    @Override
    public String getNextTargetNode() {
        return getTopNodes(1, null).stream().findFirst().orElse(null);
    }

    @Override
    public List<String> getNextTargetNodes(int count, String excludeNode) {
        return getTopNodes(count, excludeNode);
    }

    private List<String> getTopNodes(int count, String excludeNode) {
        Collection<StorageNode> eligibleNodes = nodeRegistry.getEligibleNodes();

        return eligibleNodes.stream()
                .filter(node -> {
                    if (excludeNode == null) return true;
                    return !excludeNode.equals(node.getBaseUrl()) && !excludeNode.equals(node.getNodeId());
                })
                .sorted(Comparator.comparingDouble(this::calculateScore).reversed())
                .limit(count)
                .map(StorageNode::getBaseUrl)
                .collect(Collectors.toList());
    }

    /**
     * Calculates a score for the given node using the formula:
     * (0.6 * Capacity) + (0.3 * Load) + (0.1 * Health)
     *
     * - Capacity = Free disk ratio (0.0 to 1.0)
     * - Load = 1.0 / (1.0 + activeConnections)
     * - Health = 1.0 if HEALTHY, 0.5 if DEGRADED
     */
    private double calculateScore(StorageNode node) {
        // Free share of the node's own quota — meaningful now that capacity is
        // per-node, rather than the identical host-disk ratio every container
        // used to report (which made this term a constant).
        double capacityScore = node.getFreeRatio();
        
        // Inverse load: fewer connections = higher score
        double inverseLoadScore = 1.0 / (1.0 + node.getActiveConnections().get());
        
        double healthScore = 0.0;
        if (node.getState().get() == NodeState.HEALTHY) {
            healthScore = 1.0;
        } else if (node.getState().get() == NodeState.DEGRADED) {
            healthScore = 0.5;
        }

        double score = (0.6 * capacityScore) + (0.3 * inverseLoadScore) + (0.1 * healthScore);
        
        log.debug("Node {} score = {} (cap={}, load={}, health={})", 
                node.getNodeId(), String.format("%.4f", score), 
                String.format("%.4f", capacityScore), 
                String.format("%.4f", inverseLoadScore), 
                String.format("%.4f", healthScore));
                
        return score;
    }
}
