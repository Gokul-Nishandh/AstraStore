package com.astrastore.shared.strategy;

import java.util.List;

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

    /**
     * Returns multiple target node addresses, excluding the specified node.
     * Used by replication to select replica targets.
     *
     * @param count       the number of target nodes to return
     * @param excludeNode the node to exclude from selection (typically the primary)
     * @return list of node addresses
     */
    List<String> getNextTargetNodes(int count, String excludeNode);
}
