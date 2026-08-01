package com.astrastore.replication.placement;

import com.astrastore.shared.strategy.PlacementStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock implementation of PlacementStrategy for replication.
 * Selects replica nodes, excluding the primary, using round-robin.
 */
@Service
@Slf4j
public class ReplicaPlacementStrategy implements PlacementStrategy {

    private static final List<String> ALL_NODES = List.of(
            "http://storage-node-1:8088",
            "http://storage-node-2:8088",
            "http://storage-node-3:8088"
    );

    private int currentIndex = 0;

    @Override
    public String getNextTargetNode() {
        String node = ALL_NODES.get(currentIndex);
        currentIndex = (currentIndex + 1) % ALL_NODES.size();
        log.debug("Round-robin selected node — index={}, node={}", currentIndex - 1, node);
        return node;
    }

    @Override
    public List<String> getNextTargetNodes(int count, String excludeNode) {
        List<String> targets = new ArrayList<>();
        List<String> availableNodes = new ArrayList<>(ALL_NODES);

        availableNodes.remove(excludeNode);

        int attempts = 0;
        while (targets.size() < count && attempts < ALL_NODES.size()) {
            String node = ALL_NODES.get((currentIndex + attempts) % ALL_NODES.size());
            if (!node.equals(excludeNode)) {
                targets.add(node);
            }
            attempts++;
        }

        currentIndex = (currentIndex + 1) % ALL_NODES.size();
        log.debug("Selected {} replica targets (excluding {}) — {}", count, excludeNode, targets);
        return targets;
    }
}
