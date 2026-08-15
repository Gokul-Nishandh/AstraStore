package com.astrastore.placement.service;

import com.astrastore.placement.config.ClusterProperties;
import com.astrastore.placement.model.ClusterCapacity;
import com.astrastore.placement.model.StorageNode;
import com.astrastore.placement.registry.NodeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Adds up the cluster's capacity from what nodes report about themselves.
 *
 * <p>The rule this service exists to enforce: only quota figures are summed.
 * Host filesystem numbers are per-<em>host</em>, not per-node, so adding them
 * across containers that share a disk produces a total no hardware could
 * satisfy. Nodes that have not reported a quota are counted as unknown and
 * excluded from the sums, never as zero.
 */
@Service
@RequiredArgsConstructor
public class ClusterCapacityService {

    private final NodeRegistry nodeRegistry;
    private final ClusterProperties clusterProperties;

    public ClusterCapacity current() {
        return summarise(nodeRegistry.getAllNodes(), clusterProperties.getReplicationFactor());
    }

    /**
     * Pure aggregation, separated from the registry so the arithmetic can be
     * exercised directly.
     *
     * @param nodes             every registered node
     * @param replicationFactor copies kept per chunk; values below 1 are
     *                          treated as 1 (no redundancy) rather than
     *                          producing a division that inflates the result
     */
    public static ClusterCapacity summarise(Collection<StorageNode> nodes, int replicationFactor) {
        int factor = Math.max(1, replicationFactor);
        List<StorageNode> reporting = nodes.stream()
                .filter(n -> n.getCapacityReported().get())
                .toList();

        if (reporting.isEmpty()) {
            return ClusterCapacity.unknown(nodes.size(), factor);
        }

        long capacity  = reporting.stream().mapToLong(n -> n.getCapacityBytes().get()).sum();
        long used      = reporting.stream().mapToLong(n -> n.getUsedBytes().get()).sum();
        long available = reporting.stream().mapToLong(n -> n.getAvailableBytes().get()).sum();
        long chunks    = reporting.stream().mapToLong(n -> n.getChunkCount().get()).sum();

        Double usedRatio = capacity > 0L ? (double) used / capacity : null;

        // Raw is measured; logical is derived from it and the configured
        // factor, which is why it is documented as an estimate everywhere it
        // is exposed rather than presented as a second measurement.
        long logicalStored   = used / factor;
        long logicalFree     = available / factor;
        long overheadBytes   = used - logicalStored;

        return new ClusterCapacity(
                nodes.size(),
                reporting.size(),
                false,
                factor,
                capacity,
                used,
                available,
                usedRatio,
                chunks,
                used,
                logicalStored,
                logicalFree,
                overheadBytes
        );
    }
}
