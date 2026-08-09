package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ChunkLocationRepositoryTest {

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
        return chunkLocationRepository.save(ChunkLocation.builder()
                .objectId(objectId)
                .chunkIndex(index)
                .nodeId(nodeId)
                .replicationStatus(status)
                .checksum("checksum-" + index)
                .build());
    }
}
