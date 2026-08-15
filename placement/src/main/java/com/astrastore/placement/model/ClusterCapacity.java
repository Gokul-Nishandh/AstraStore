package com.astrastore.placement.model;

/**
 * The cluster's capacity, as measured rather than assumed.
 *
 * <p>Every field is either a sum of numbers a node reported about itself, or
 * {@code null} because no node has reported yet. There is no default,
 * placeholder or fallback value anywhere in this record: a fresh stack shows
 * {@code insufficientData} instead of a confident zero.
 *
 * <h2>Raw versus logical</h2>
 * <p>{@code rawBytesStored} is what is physically on the nodes.
 * {@code logicalBytesStored} is what users actually uploaded — with a
 * replication factor of 2, a 1 GB object occupies 2 GB of the cluster. Both
 * are true, they answer different questions, and showing only one is how a
 * capacity number becomes misleading.
 *
 * @param totalNodes             every registered node
 * @param reportingNodes         nodes that have reported a quota at least once
 * @param insufficientData       true when no node has reported yet
 * @param replicationFactor      configured copies per chunk
 * @param totalCapacityBytes     sum of per-node quotas, never host disks
 * @param usedBytes              sum of per-node real usage (== rawBytesStored)
 * @param availableBytes         sum of per-node remaining quota
 * @param usedRatio              usedBytes / totalCapacityBytes, as a number
 * @param totalChunkCount        chunk files held cluster-wide
 * @param rawBytesStored         bytes physically occupied across all nodes
 * @param logicalBytesStored     estimated user-visible bytes (raw / factor)
 * @param logicalBytesAvailable  estimated user-visible bytes still uploadable
 * @param replicationOverheadBytes bytes spent purely on redundancy
 */
public record ClusterCapacity(
        int totalNodes,
        int reportingNodes,
        boolean insufficientData,
        int replicationFactor,
        Long totalCapacityBytes,
        Long usedBytes,
        Long availableBytes,
        Double usedRatio,
        Long totalChunkCount,
        Long rawBytesStored,
        Long logicalBytesStored,
        Long logicalBytesAvailable,
        Long replicationOverheadBytes
) {

    /** No node has reported a quota yet — say so rather than inventing zeros. */
    public static ClusterCapacity unknown(int totalNodes, int replicationFactor) {
        return new ClusterCapacity(
                totalNodes, 0, true, replicationFactor,
                null, null, null, null, null, null, null, null, null);
    }
}
