package com.astrastore.upload.placement;

import com.astrastore.shared.strategy.PlacementStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock implementation of PlacementStrategy for testing.
 * Rotates through a predefined list of storage node addresses.
 */
@Service
@Slf4j
public class RoundRobinPlacementMock implements PlacementStrategy {

    private static final List<String> NODES = List.of(
            "http://storage-node-1:8088",
            "http://storage-node-2:8088",
            "http://storage-node-3:8088"
    );

    private int currentIndex;

    @Override
    public String getNextTargetNode() {
        String node = NODES.get(currentIndex);
        currentIndex = (currentIndex + 1) % NODES.size();
        log.debug("Round-robin selected node — index={}, node={}", currentIndex - 1, node);
        return node;
    }

    /**
     * Returns the list of all available nodes.
     *
     * @return immutable list of node addresses
     */
    public List<String> getAllNodes() {
        return NODES;
    }

    /**
     * Resets the round-robin counter to the beginning.
     */
    public void reset() {
        currentIndex = 0;
        log.debug("Round-robin counter reset");
    }
}
