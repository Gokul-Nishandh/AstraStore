package com.astrastore.shared.strategy;

/**
 * Strategy interface for selecting the next storage node target.
 * Implementations provide different node selection algorithms
 * (e.g., round-robin, least-disk-space, random).
 */
public interface PlacementStrategy {

    /**
     * Returns the next target node address.
     *
     * @return the node address (e.g., "http://storage-node-1:8088")
     */
    String getNextTargetNode();
}
