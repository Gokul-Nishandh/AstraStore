package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.exception.ObjectNotFoundException;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import com.astrastore.metadata.repository.ObjectStarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectServiceTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    @Mock
    private ObjectRepository objectRepository;

    @Mock
    private ChunkLocationRepository chunkLocationRepository;

    @Mock
    private ObjectStarRepository objectStarRepository;

    @Mock
    private ChunkCleanupPublisher chunkCleanupPublisher;

    private ObjectService objectService;

    @BeforeEach
    void setUp() {
        objectService = new ObjectService(
                objectRepository, chunkLocationRepository, objectStarRepository, chunkCleanupPublisher);
    }

    @Test
    void createObject_assignsIdWhenNullAndSaves() {
        ObjectRecord record = ObjectRecord.builder().key("q3.pdf").build();
        when(objectRepository.save(any(ObjectRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectRecord saved = objectService.createObject(record);

        assertThat(saved.getId()).isNotNull();
        verify(objectRepository).save(record);
    }

    @Test
    void createObject_keepsProvidedId() {
        ObjectRecord record = ObjectRecord.builder().id(OBJECT_ID).key("q3.pdf").build();
        when(objectRepository.save(record)).thenReturn(record);

        assertThat(objectService.createObject(record).getId()).isEqualTo(OBJECT_ID);
    }

    @Test
    void getObject_returnsActiveObject() {
        ObjectRecord record = activeRecord();
        when(objectRepository.findById(OBJECT_ID)).thenReturn(Optional.of(record));

        assertThat(objectService.getObject(OBJECT_ID)).isEqualTo(record);
    }

    @Test
    void getObject_throwsWhenMissing() {
        when(objectRepository.findById(OBJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objectService.getObject(OBJECT_ID))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void getObject_throwsWhenSoftDeleted() {
        ObjectRecord deleted = activeRecord();
        deleted.setStatus(ObjectStatus.DELETED);
        when(objectRepository.findById(OBJECT_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> objectService.getObject(OBJECT_ID))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void getObjectByBucketAndKey_returnsActiveObject() {
        ObjectRecord record = activeRecord();
        when(objectRepository.findByBucketIdAndKey(BUCKET_ID, "q3.pdf")).thenReturn(Optional.of(record));

        assertThat(objectService.getObjectByBucketAndKey(BUCKET_ID, "q3.pdf")).isEqualTo(record);
    }

    @Test
    void getObjectByBucketAndKey_throwsWhenNotFound() {
        when(objectRepository.findByBucketIdAndKey(BUCKET_ID, "q3.pdf")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objectService.getObjectByBucketAndKey(BUCKET_ID, "q3.pdf"))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void listObjects_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ObjectRecord> page = new PageImpl<>(List.of(activeRecord()));
        when(objectRepository.findByBucketIdAndStatus(BUCKET_ID, ObjectStatus.ACTIVE, pageable)).thenReturn(page);

        assertThat(objectService.listObjects(BUCKET_ID, pageable)).isEqualTo(page);
    }

    @Test
    void softDeleteObject_marksDeleted() {
        ObjectRecord record = activeRecord();
        when(objectRepository.findById(OBJECT_ID)).thenReturn(Optional.of(record));
        when(objectRepository.save(record)).thenReturn(record);

        objectService.softDeleteObject(OBJECT_ID);

        assertThat(record.getStatus()).isEqualTo(ObjectStatus.DELETED);
        assertThat(record.getDeletedAt()).isNotNull();
    }

    @Test
    void softDeleteObject_throwsWhenMissing() {
        when(objectRepository.findById(OBJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objectService.softDeleteObject(OBJECT_ID))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void recordChunkLocations_savesAll() {
        List<ChunkLocation> locations = List.of(chunkLocation(0), chunkLocation(1));
        when(chunkLocationRepository.saveAll(locations)).thenReturn(locations);

        assertThat(objectService.recordChunkLocations(locations)).hasSize(2);
    }

    @Test
    void getChunkLocations_returnsOrderedList() {
        List<ChunkLocation> locations = List.of(chunkLocation(1), chunkLocation(0));
        when(chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(OBJECT_ID)).thenReturn(locations);

        assertThat(objectService.getChunkLocations(OBJECT_ID)).isEqualTo(locations);
    }

    @Test
    void updateChunkReplica_updatesReplicaAndStatus() {
        ChunkLocation location = chunkLocation(0);
        when(chunkLocationRepository.findByObjectIdAndChunkIndex(OBJECT_ID, 0)).thenReturn(Optional.of(location));
        when(chunkLocationRepository.save(location)).thenReturn(location);

        ChunkLocation updated = objectService.updateChunkReplica(OBJECT_ID, 0, "node-b", ReplicationStatus.REPLICATED);

        assertThat(updated.getReplicaNodeId()).isEqualTo("node-b");
        assertThat(updated.getReplicationStatus()).isEqualTo(ReplicationStatus.REPLICATED);
    }

    @Test
    void updateChunkReplica_updatesOnlyReplicaWhenStatusNull() {
        ChunkLocation location = chunkLocation(0);
        when(chunkLocationRepository.findByObjectIdAndChunkIndex(OBJECT_ID, 0)).thenReturn(Optional.of(location));
        when(chunkLocationRepository.save(location)).thenReturn(location);

        objectService.updateChunkReplica(OBJECT_ID, 0, "node-b", null);

        assertThat(updatedReplica(location)).isEqualTo("node-b");
        assertThat(location.getReplicationStatus()).isEqualTo(ReplicationStatus.PENDING);
    }

    @Test
    void updateChunkReplica_throwsWhenChunkMissing() {
        when(chunkLocationRepository.findByObjectIdAndChunkIndex(OBJECT_ID, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objectService.updateChunkReplica(OBJECT_ID, 0, "node-b", ReplicationStatus.REPLICATED))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void countChunksTotal_delegates() {
        when(chunkLocationRepository.countByObjectId(OBJECT_ID)).thenReturn(2L);

        assertThat(objectService.countChunksTotal(OBJECT_ID)).isEqualTo(2L);
    }

    @Test
    void countChunksReplicated_sumsReplicatedAndComplete() {
        when(chunkLocationRepository.countByObjectIdAndReplicationStatus(OBJECT_ID, ReplicationStatus.REPLICATED))
                .thenReturn(2L);
        when(chunkLocationRepository.countByObjectIdAndReplicationStatus(OBJECT_ID, ReplicationStatus.COMPLETE))
                .thenReturn(1L);

        assertThat(objectService.countChunksReplicated(OBJECT_ID)).isEqualTo(3L);
    }

    private String updatedReplica(ChunkLocation location) {
        return location.getReplicaNodeId();
    }

    private ObjectRecord activeRecord() {
        return ObjectRecord.builder()
                .id(OBJECT_ID)
                .bucket(Bucket.builder().id(BUCKET_ID).name("reports").build())
                .key("q3.pdf")
                .sizeBytes(1024L)
                .checksum("abc123")
                .contentType("application/pdf")
                .status(ObjectStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    private ChunkLocation chunkLocation(int index) {
        return ChunkLocation.builder()
                .objectId(OBJECT_ID)
                .chunkIndex(index)
                .nodeId("storage-node-1")
                .replicationStatus(ReplicationStatus.PENDING)
                .checksum("checksum" + index)
                .build();
    }
}
