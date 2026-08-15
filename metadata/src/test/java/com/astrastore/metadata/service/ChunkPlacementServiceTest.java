package com.astrastore.metadata.service;

import com.astrastore.metadata.dto.chunk.NodeChunkResponse;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkPlacementServiceTest {

    private static final Pageable PAGE =
            PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Mock
    private ChunkLocationRepository chunkLocationRepository;

    @Mock
    private ObjectRepository objectRepository;

    @InjectMocks
    private ChunkPlacementService chunkPlacementService;

    @Test
    void placementsForObject_MapsEveryFieldInIndexOrder() {
        UUID objectId = UUID.randomUUID();
        when(chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(objectId))
                .thenReturn(List.of(
                        chunk(objectId, 0, "node-a", "node-b", ReplicationStatus.REPLICATED),
                        chunk(objectId, 1, "node-b", null, ReplicationStatus.PENDING)));

        var placements = chunkPlacementService.placementsForObject(objectId);

        assertThat(placements).hasSize(2);
        assertThat(placements.get(0).chunkIndex()).isZero();
        assertThat(placements.get(0).nodeId()).isEqualTo("node-a");
        assertThat(placements.get(0).replicaNodeId()).isEqualTo("node-b");
        assertThat(placements.get(0).replicationStatus()).isEqualTo(ReplicationStatus.REPLICATED);
        // An unplaced second copy stays null so the console can say "not yet"
        // rather than repeating the primary.
        assertThat(placements.get(1).replicaNodeId()).isNull();
    }

    /**
     * Which column matched decides both the role and what the peer is: the
     * node named in {@code node_id} is the primary and its peer is the
     * replica, and vice versa. Getting this backwards would tell an operator
     * the wrong node is about to lose the only copy.
     */
    @Test
    void chunksOnNode_DerivesRoleAndPeerFromTheMatchingColumn() {
        UUID primaryObject = UUID.randomUUID();
        UUID replicaObject = UUID.randomUUID();

        when(chunkLocationRepository.findByNode(Set.of("node-a"), PAGE)).thenReturn(page(
                chunk(primaryObject, 0, "node-a", "node-b", ReplicationStatus.REPLICATED),
                chunk(replicaObject, 3, "node-c", "node-a", ReplicationStatus.REPLICATED)));

        when(objectRepository.findAllByIdInFetchingBucket(anyCollection())).thenReturn(List.of(
                object(primaryObject, "reports/q3.pdf", 12_000_000L, "reports"),
                object(replicaObject, "logs/app.log", 900L, "logs")));

        List<NodeChunkResponse> rows = chunkPlacementService.chunksOnNode(List.of("node-a"), PAGE).getContent();

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).role()).isEqualTo(NodeChunkResponse.ChunkRole.PRIMARY);
        assertThat(rows.get(0).peerNodeId()).isEqualTo("node-b");
        assertThat(rows.get(0).objectKey()).isEqualTo("reports/q3.pdf");
        assertThat(rows.get(0).bucketName()).isEqualTo("reports");
        assertThat(rows.get(0).objectSizeBytes()).isEqualTo(12_000_000L);

        assertThat(rows.get(1).role()).isEqualTo(NodeChunkResponse.ChunkRole.REPLICA);
        assertThat(rows.get(1).peerNodeId()).isEqualTo("node-c");
        assertThat(rows.get(1).chunkIndex()).isEqualTo(3);
    }

    /**
     * A chunk whose object is gone is an orphan — a cleanup event that never
     * landed. It is reported with null object fields rather than dropped,
     * because finding those rows is a reason to open this view.
     */
    @Test
    void chunksOnNode_KeepsChunksWhoseObjectNoLongerExists() {
        UUID missing = UUID.randomUUID();
        when(chunkLocationRepository.findByNode(Set.of("node-a"), PAGE))
                .thenReturn(page(chunk(missing, 0, "node-a", "node-b", ReplicationStatus.REPLICATED)));
        when(objectRepository.findAllByIdInFetchingBucket(anyCollection())).thenReturn(List.of());

        List<NodeChunkResponse> rows = chunkPlacementService.chunksOnNode(List.of("node-a"), PAGE).getContent();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).objectId()).isEqualTo(missing);
        assertThat(rows.get(0).objectKey()).isNull();
        assertThat(rows.get(0).bucketName()).isNull();
        // Null, never 0 — the console renders an em dash for "no data".
        assertThat(rows.get(0).objectSizeBytes()).isNull();
    }

    /**
     * Objects are resolved once for the whole page. The per-row alternative
     * is what turned the object listing into 150 statements per request.
     */
    @Test
    void chunksOnNode_ResolvesEachObjectOnceForTheWholePage() {
        UUID objectId = UUID.randomUUID();
        when(chunkLocationRepository.findByNode(Set.of("node-a"), PAGE)).thenReturn(page(
                chunk(objectId, 0, "node-a", "node-b", ReplicationStatus.REPLICATED),
                chunk(objectId, 1, "node-a", "node-b", ReplicationStatus.REPLICATED),
                chunk(objectId, 2, "node-a", "node-b", ReplicationStatus.REPLICATED)));
        when(objectRepository.findAllByIdInFetchingBucket(List.of(objectId)))
                .thenReturn(List.of(object(objectId, "big.iso", 24_000_000L, "isos")));

        chunkPlacementService.chunksOnNode(List.of("node-a"), PAGE);

        // Three chunks of one object: one lookup, with one id in it.
        verify(objectRepository).findAllByIdInFetchingBucket(List.of(objectId));
    }

    /**
     * One node, two names. Chunk rows carry the base URL the upload service
     * recorded; the placement registry calls the same machine
     * {@code storage-node-1}. A caller passing both must get its chunks, and
     * the role must still come out right — the row matched on the URL, not on
     * the name the caller happened to think of first.
     */
    @Test
    void chunksOnNode_MatchesAnyOfTheNodesIdentities() {
        UUID objectId = UUID.randomUUID();
        List<String> identities = List.of("storage-node-1", "http://storage-node-1:8088");

        when(chunkLocationRepository.findByNode(Set.copyOf(identities), PAGE)).thenReturn(page(
                chunk(objectId, 0, "http://storage-node-1:8088", "http://storage-node-2:8088",
                        ReplicationStatus.REPLICATED)));
        when(objectRepository.findAllByIdInFetchingBucket(anyCollection()))
                .thenReturn(List.of(object(objectId, "q3.pdf", 1024L, "reports")));

        List<NodeChunkResponse> rows =
                chunkPlacementService.chunksOnNode(identities, PAGE).getContent();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).role()).isEqualTo(NodeChunkResponse.ChunkRole.PRIMARY);
        assertThat(rows.get(0).peerNodeId()).isEqualTo("http://storage-node-2:8088");
    }

    @Test
    void chunksOnNode_EmptyPageAsksForNoObjects() {
        when(chunkLocationRepository.findByNode(Set.of("node-a"), PAGE)).thenReturn(page());

        assertThat(chunkPlacementService.chunksOnNode(List.of("node-a"), PAGE)).isEmpty();
        verifyNoInteractions(objectRepository);
    }

    // ----------------------------------------------------------------------

    private static Page<ChunkLocation> page(ChunkLocation... chunks) {
        List<ChunkLocation> content = List.of(chunks);
        return new PageImpl<>(content, PAGE, content.size());
    }

    private static ChunkLocation chunk(UUID objectId, int index, String nodeId, String replicaNodeId,
                                       ReplicationStatus status) {
        return ChunkLocation.builder()
                .id(UUID.randomUUID())
                .objectId(objectId)
                .chunkIndex(index)
                .nodeId(nodeId)
                .replicaNodeId(replicaNodeId)
                .replicationStatus(status)
                .checksum("checksum-" + index)
                .createdAt(Instant.now())
                .build();
    }

    private static ObjectRecord object(UUID id, String key, long sizeBytes, String bucketName) {
        return ObjectRecord.builder()
                .id(id)
                .bucket(Bucket.builder().id(UUID.randomUUID()).name(bucketName).build())
                .key(key)
                .sizeBytes(sizeBytes)
                .checksum("object-checksum")
                .build();
    }
}
