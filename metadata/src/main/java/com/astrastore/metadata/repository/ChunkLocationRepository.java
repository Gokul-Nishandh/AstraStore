package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkLocationRepository extends JpaRepository<ChunkLocation, UUID> {

    List<ChunkLocation> findByObjectIdOrderByChunkIndexAsc(UUID objectId);

    Optional<ChunkLocation> findByObjectIdAndChunkIndex(UUID objectId, Integer chunkIndex);

    List<ChunkLocation> findByNodeId(String nodeId);

    List<ChunkLocation> findByReplicationStatus(ReplicationStatus replicationStatus);

    long countByObjectId(UUID objectId);

    long countByObjectIdAndReplicationStatus(UUID objectId, ReplicationStatus replicationStatus);
}

