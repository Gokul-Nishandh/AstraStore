package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ChunkLocationRepositoryTest {

    /** Paging an unsorted result is unstable, so every page here carries a sort. */
    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Pageable PAGE = PageRequest.of(0, 50, SORT);

    @Autowired
    private ChunkLocationRepository chunkLocationRepository;

    @Test
    void findByObjectIdOrderByChunkIndexAsc_returnsOrderedList() {
        UUID objectId = UUID.randomUUID();
        chunkLocationRepository.saveAll(List.of(
                chunk(objectId, 1),
                chunk(objectId, 0),
                chunk(objectId, 2)));

        List<ChunkLocation> found = chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(objectId);

        assertThat(found).hasSize(3);
        assertThat(found).extracting(ChunkLocation::getChunkIndex).containsExactly(0, 1, 2);
    }

    @Test
    void findByObjectIdAndChunkIndex_returnsMatchingChunk() {
        UUID objectId = UUID.randomUUID();
        chunkLocationRepository.save(chunk(objectId, 0));

        assertThat(chunkLocationRepository.findByObjectIdAndChunkIndex(objectId, 0)).isPresent();
        assertThat(chunkLocationRepository.findByObjectIdAndChunkIndex(objectId, 5)).isEmpty();
    }

    @Test
    void findByNodeId_returnsChunksOnNode() {
        chunkLocationRepository.saveAll(List.of(
                chunk(UUID.randomUUID(), 0, "node-a", ReplicationStatus.PENDING),
                chunk(UUID.randomUUID(), 0, "node-a", ReplicationStatus.PENDING),
                chunk(UUID.randomUUID(), 0, "node-b", ReplicationStatus.PENDING)));

        List<ChunkLocation> onNodeA = chunkLocationRepository.findByNodeId("node-a");

        assertThat(onNodeA).hasSize(2);
    }

    /**
     * The distinction {@code findByNodeId} does not make. A node holding
     * nothing but replicas is not an empty node, and the operations console
     * has to say so — hence the second predicate.
     */
    @Test
    void findByNode_matchesPrimaryAndReplicaAlike() {
        chunkLocationRepository.saveAll(List.of(
                chunk(UUID.randomUUID(), 0, "node-a", ReplicationStatus.REPLICATED, "node-b"),
                chunk(UUID.randomUUID(), 0, "node-b", ReplicationStatus.REPLICATED, "node-a"),
                chunk(UUID.randomUUID(), 0, "node-c", ReplicationStatus.PENDING, null)));

        Page<ChunkLocation> onNodeA = chunkLocationRepository.findByNode(Set.of("node-a"), PAGE);

        // One where it is the primary, one where it is the replica.
        assertThat(onNodeA.getTotalElements()).isEqualTo(2);
        assertThat(onNodeA.getContent())
                .allSatisfy(c -> assertThat(c.getNodeId().equals("node-a")
                        || "node-a".equals(c.getReplicaNodeId())).isTrue());

        // And the narrower query really does miss the replica-only row, which
        // is why this method exists.
        assertThat(chunkLocationRepository.findByNodeId("node-a")).hasSize(1);
    }

    /**
     * The case the console actually issues.
     *
     * <p>A node answers to two names — the base URL upload records and the
     * short id the placement registry uses — so the collection this query is
     * given normally holds more than one value, and the same name is bound at
     * both {@code in} sites. A single-element assertion would pass without
     * exercising either fact.
     */
    @Test
    void findByNode_matchesEveryIdentifierWhenGivenMoreThanOne() {
        chunkLocationRepository.saveAll(List.of(
                chunk(UUID.randomUUID(), 0, "http://node-a:8088", ReplicationStatus.REPLICATED, "http://node-b:8088"),
                chunk(UUID.randomUUID(), 1, "http://node-b:8088", ReplicationStatus.REPLICATED, "node-a"),
                chunk(UUID.randomUUID(), 2, "http://node-c:8088", ReplicationStatus.PENDING, null)));

        Set<String> identities = Set.of("node-a", "http://node-a:8088");
        Page<ChunkLocation> found = chunkLocationRepository.findByNode(identities, PAGE);

        // One matched on the URL as primary, one on the short id as replica.
        assertThat(found.getTotalElements()).isEqualTo(2);
        assertThat(found.getContent()).extracting(ChunkLocation::getChunkIndex)
                .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void findByNode_pagesWithATotalCoveringEveryMatch() {
        for (int i = 0; i < 5; i++) {
            chunk(UUID.randomUUID(), i, "node-a", ReplicationStatus.REPLICATED, "node-b");
        }

        Page<ChunkLocation> firstPage = chunkLocationRepository.findByNode(
                Set.of("node-a"), PageRequest.of(0, 2, SORT));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }

    @Test
    void findByNode_unknownNodeIsEmptyRatherThanEverything() {
        chunk(UUID.randomUUID(), 0, "node-a", ReplicationStatus.REPLICATED, "node-b");

        assertThat(chunkLocationRepository.findByNode(Set.of("node-z"), PAGE)).isEmpty();
    }

    @Test
    void findByReplicationStatus_returnsChunksInState() {
        chunkLocationRepository.saveAll(List.of(
                chunk(UUID.randomUUID(), 0, "node-a", ReplicationStatus.REPLICATED),
                chunk(UUID.randomUUID(), 0, "node-b", ReplicationStatus.REPLICATED),
                chunk(UUID.randomUUID(), 0, "node-c", ReplicationStatus.PENDING)));

        List<ChunkLocation> replicated = chunkLocationRepository.findByReplicationStatus(ReplicationStatus.REPLICATED);

        assertThat(replicated).hasSize(2);
    }

    @Test
    void countByObjectId() {
        UUID objectId = UUID.randomUUID();
        chunkLocationRepository.saveAll(List.of(chunk(objectId, 0), chunk(objectId, 1)));

        assertThat(chunkLocationRepository.countByObjectId(objectId)).isEqualTo(2);
    }

    @Test
    void countByObjectIdAndReplicationStatus() {
        UUID objectId = UUID.randomUUID();
        chunkLocationRepository.saveAll(List.of(
                chunk(objectId, 0, "node-a", ReplicationStatus.REPLICATED),
                chunk(objectId, 1, "node-b", ReplicationStatus.COMPLETE),
                chunk(objectId, 2, "node-c", ReplicationStatus.PENDING)));

        assertThat(chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.REPLICATED))
                .isEqualTo(1);
        assertThat(chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.COMPLETE))
                .isEqualTo(1);
    }

    private ChunkLocation chunk(UUID objectId, int index) {
        return chunk(objectId, index, "node-a", ReplicationStatus.PENDING);
    }

    private ChunkLocation chunk(UUID objectId, int index, String nodeId, ReplicationStatus status) {
        return chunk(objectId, index, nodeId, status, null);
    }

    private ChunkLocation chunk(UUID objectId, int index, String nodeId, ReplicationStatus status,
                                String replicaNodeId) {
        return chunkLocationRepository.save(ChunkLocation.builder()
                .objectId(objectId)
                .chunkIndex(index)
                .nodeId(nodeId)
                .replicaNodeId(replicaNodeId)
                .replicationStatus(status)
                .checksum("checksum-" + index)
                .build());
    }
}
