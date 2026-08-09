package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.exception.ObjectNotFoundException;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for object metadata CRUD, pagination, soft-delete, and chunk location
 * indexing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ObjectService {

    private final ObjectRepository objectRepository;
    private final ChunkLocationRepository chunkLocationRepository;

    /**
     * Creates / records metadata for an uploaded object.
     */
    public ObjectRecord createObject(ObjectRecord objectRecord) {
        if (objectRecord.getId() == null) {
            objectRecord.setId(UUID.randomUUID());
        }
        return objectRepository.save(objectRecord);
    }

    /**
     * Retrieves active object metadata by object ID.
     */
    @Transactional(readOnly = true)
    public ObjectRecord getObject(UUID objectId) {
        return objectRepository.findById(objectId)
                .filter(obj -> obj.getStatus() == ObjectStatus.ACTIVE)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found with id: " + objectId));
    }

    /**
     * Retrieves active object metadata by bucket ID and object key.
     */
    @Transactional(readOnly = true)
    public ObjectRecord getObjectByBucketAndKey(UUID bucketId, String key) {
        return objectRepository.findByBucketIdAndKey(bucketId, key)
                .filter(obj -> obj.getStatus() == ObjectStatus.ACTIVE)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Object not found with key '" + key + "' in bucket " + bucketId));
    }

    /**
     * Lists active objects in a bucket with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ObjectRecord> listObjects(UUID bucketId, Pageable pageable) {
        return objectRepository.findByBucketIdAndStatus(bucketId, ObjectStatus.ACTIVE, pageable);
    }

    /**
     * Soft-deletes an object (sets status = DELETED and deleted_at timestamp).
     */
    public void softDeleteObject(UUID objectId) {
        ObjectRecord record = objectRepository.findById(objectId)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found with id: " + objectId));
        record.setStatus(ObjectStatus.DELETED);
        record.setDeletedAt(Instant.now());
        objectRepository.save(record);
    }

    /**
     * Records initial chunk locations for an object.
     */
    public List<ChunkLocation> recordChunkLocations(List<ChunkLocation> locations) {
        return chunkLocationRepository.saveAll(locations);
    }

    /**
     * Retrieves ordered chunk locations for an object ID.
     */
    @Transactional(readOnly = true)
    public List<ChunkLocation> getChunkLocations(UUID objectId) {
        return chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(objectId);
    }

    /**
     * Updates replica node and replication status for a specific chunk.
     */
    public ChunkLocation updateChunkReplica(UUID objectId, Integer chunkIndex, String replicaNodeId,
            ReplicationStatus replicationStatus) {
        ChunkLocation location = chunkLocationRepository.findByObjectIdAndChunkIndex(objectId, chunkIndex)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Chunk location not found for object " + objectId + " index " + chunkIndex));

        if (replicaNodeId != null) {
            location.setReplicaNodeId(replicaNodeId);
        }
        if (replicationStatus != null) {
            location.setReplicationStatus(replicationStatus);
        }
        return chunkLocationRepository.save(location);
    }

    /**
     * Counts total recorded chunk locations for an object.
     */
    @Transactional(readOnly = true)
    public long countChunksTotal(UUID objectId) {
        return chunkLocationRepository.countByObjectId(objectId);
    }

    /**
     * Counts chunks that have completed replication.
     */
    @Transactional(readOnly = true)
    public long countChunksReplicated(UUID objectId) {
        return chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.REPLICATED)
                + chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.COMPLETE);
    }
}

