package com.astrastore.placement.service;

import com.astrastore.placement.model.ClusterCapacity;
import com.astrastore.placement.model.StorageNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The arithmetic behind the cluster capacity figure the dashboard shows.
 * The regression these guard against: three containers on one laptop drive
 * reporting that drive as their own capacity, so the cluster claimed several
 * times the storage that physically existed.
 */
class ClusterCapacityServiceTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long TWENTY_GIB = 20 * GIB;

    private static StorageNode node(String id, long capacity, long used, long chunks) {
        StorageNode node = StorageNode.builder().nodeId(id).baseUrl("http://" + id + ":8088").build();
        node.getCapacityBytes().set(capacity);
        node.getUsedBytes().set(used);
        node.getAvailableBytes().set(Math.max(0L, capacity - used));
        node.getChunkCount().set(chunks);
        node.getCapacityReported().set(true);
        return node;
    }

    private static StorageNode silentNode(String id, long hostDiskFree) {
        StorageNode node = StorageNode.builder().nodeId(id).baseUrl("http://" + id + ":8088").build();
        // Reachable, but has never reported a quota — only the shared host disk.
        node.getHostDiskFreeBytes().set(hostDiskFree);
        return node;
    }

    @Test
    void sumsPerNodeQuotasNotHostDisks() {
        List<StorageNode> nodes = List.of(
                node("storage-node-1", TWENTY_GIB, 2 * GIB, 10),
                node("storage-node-2", TWENTY_GIB, 2 * GIB, 10),
                node("storage-node-3", TWENTY_GIB, 0L, 0));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 2);

        // 3 x 20 GiB of quota — not 3 x the operator's 1 TB laptop drive.
        assertThat(capacity.totalCapacityBytes()).isEqualTo(60 * GIB);
        assertThat(capacity.usedBytes()).isEqualTo(4 * GIB);
        assertThat(capacity.availableBytes()).isEqualTo(56 * GIB);
        assertThat(capacity.totalChunkCount()).isEqualTo(20L);
        assertThat(capacity.reportingNodes()).isEqualTo(3);
        assertThat(capacity.insufficientData()).isFalse();
    }

    @Test
    void usedRatioIsANumberDerivedFromQuotas() {
        List<StorageNode> nodes = List.of(
                node("storage-node-1", 100L, 25L, 1),
                node("storage-node-2", 100L, 15L, 1));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 2);

        assertThat(capacity.usedRatio()).isEqualTo(0.2);
    }

    @Test
    void separatesRawBytesFromLogicalBytesAtReplicationFactorTwo() {
        // 2 GiB physically present across the cluster with two copies of
        // everything means users uploaded 1 GiB.
        List<StorageNode> nodes = List.of(
                node("storage-node-1", TWENTY_GIB, GIB, 8),
                node("storage-node-2", TWENTY_GIB, GIB, 8));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 2);

        assertThat(capacity.rawBytesStored()).isEqualTo(2 * GIB);
        assertThat(capacity.logicalBytesStored()).isEqualTo(GIB);
        assertThat(capacity.replicationOverheadBytes()).isEqualTo(GIB);
        // 38 GiB of free quota only buys 19 GiB of further uploads.
        assertThat(capacity.logicalBytesAvailable()).isEqualTo(19 * GIB);
        assertThat(capacity.replicationFactor()).isEqualTo(2);
    }

    @Test
    void replicationFactorOneMeansNoOverhead() {
        List<StorageNode> nodes = List.of(node("storage-node-1", 1_000L, 400L, 4));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 1);

        assertThat(capacity.rawBytesStored()).isEqualTo(400L);
        assertThat(capacity.logicalBytesStored()).isEqualTo(400L);
        assertThat(capacity.replicationOverheadBytes()).isZero();
    }

    @Test
    void reportsInsufficientDataRatherThanZeroWhenNoNodeHasAnswered() {
        // A freshly started stack: nodes registered, none polled yet.
        List<StorageNode> nodes = List.of(
                silentNode("storage-node-1", 900L * GIB),
                silentNode("storage-node-2", 900L * GIB));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 2);

        assertThat(capacity.insufficientData()).isTrue();
        assertThat(capacity.reportingNodes()).isZero();
        assertThat(capacity.totalNodes()).isEqualTo(2);
        // Null, not 0 and not the 1.8 TB of host disk those two nodes see.
        assertThat(capacity.totalCapacityBytes()).isNull();
        assertThat(capacity.usedBytes()).isNull();
        assertThat(capacity.usedRatio()).isNull();
        assertThat(capacity.rawBytesStored()).isNull();
    }

    @Test
    void excludesNodesThatHaveNotReportedInsteadOfCountingThemAsEmpty() {
        List<StorageNode> nodes = List.of(
                node("storage-node-1", TWENTY_GIB, 4 * GIB, 16),
                silentNode("storage-node-2", 900L * GIB));

        ClusterCapacity capacity = ClusterCapacityService.summarise(nodes, 2);

        assertThat(capacity.totalNodes()).isEqualTo(2);
        assertThat(capacity.reportingNodes()).isEqualTo(1);
        // Only the node that answered contributes; the silent one is absent
        // from the sums rather than dragging the used ratio down with a zero.
        assertThat(capacity.totalCapacityBytes()).isEqualTo(TWENTY_GIB);
        assertThat(capacity.usedRatio()).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void freeRatioFallsBackToFullyFreeWhenQuotaIsUnknown() {
        StorageNode silent = silentNode("storage-node-1", 900L * GIB);

        // Placement scoring must not treat an unreported node as full.
        assertThat(silent.getFreeRatio()).isEqualTo(1.0);
        assertThat(silent.getUsedRatio()).isEqualTo(0.0);
    }
}
