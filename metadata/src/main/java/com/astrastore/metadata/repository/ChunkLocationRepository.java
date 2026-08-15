package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkLocationRepository extends JpaRepository<ChunkLocation, UUID> {

    List<ChunkLocation> findByObjectIdOrderByChunkIndexAsc(UUID objectId);

    Optional<ChunkLocation> findByObjectIdAndChunkIndex(UUID objectId, Integer chunkIndex);

    List<ChunkLocation> findByNodeId(String nodeId);

    /**
     * Every chunk a node holds, in either role, one page at a time.
     *
     * <p>{@link #findByNodeId} answers a narrower question — it matches the
     * primary column only, so a node holding a thousand replicas and nothing
     * else looks empty through it. Both columns are indexed
     * ({@code idx_chunk_locations_node_id} covers the primary; the replica
     * predicate is a sequential scan until the table is large enough to
     * warrant a second index), and the result is paged because a node in a
     * real deployment holds far more chunks than anyone wants in one response.
     *
     * <p><strong>A node has more than one name here, which is why this takes a
     * collection.</strong> {@code node_id} is written by the upload service
     * from {@code ChunkManifest.nodeIp()} and holds the node's base URL —
     * {@code http://storage-node-1:8088} — because the download service reads
     * the same column and fetches the bytes straight from it. The placement
     * service's registry, meanwhile, calls that node {@code storage-node-1}.
     * Neither is wrong and neither can be changed without touching the read
     * path, so a caller asking "what is on this node" passes every identifier
     * it knows the node by and each is matched exactly. Guessing one form from
     * the other would put a mapping in this service that belongs in placement.
     *
     * <p>The one named parameter is bound at both {@code in} sites, which
     * Hibernate expands correctly for a multi-element collection — checked
     * against Postgres with a two-name lookup, not assumed. (The comparable
     * trick does <em>not</em> work in a native query, where a name reused
     * across {@code SELECT} and {@code GROUP BY} expands to different bind
     * markers and Postgres rejects the statement; this is JPQL, and the
     * distinction is worth keeping in mind before copying this shape.)
     *
     * <p>The {@code Pageable} must carry a sort. Postgres makes no promise
     * about the order of an unsorted result, so paging through one can repeat
     * a row on page 2 and skip another entirely; {@code Pageables.sanitize}
     * guarantees a fallback sort for exactly this reason.
     */
    @Query("select c from ChunkLocation c "
            + "where c.nodeId in :nodeIds or c.replicaNodeId in :nodeIds")
    Page<ChunkLocation> findByNode(@Param("nodeIds") Collection<String> nodeIds, Pageable pageable);

    List<ChunkLocation> findByReplicationStatus(ReplicationStatus replicationStatus);

    long countByObjectId(UUID objectId);

    long countByObjectIdAndReplicationStatus(UUID objectId, ReplicationStatus replicationStatus);

    @Modifying
    @Query("delete from ChunkLocation c where c.objectId in :objectIds")
    int deleteByObjectIdIn(@Param("objectIds") Collection<UUID> objectIds);

    /**
     * Chunk totals for a whole page of objects in one query.
     *
     * <p>Each row is {@code [objectId, total, replicated]}. The per-object
     * {@code countByObjectId} pair below costs two round trips per row, which
     * is fine for a single object and ruinous for a 50-row listing.
     */
    @Query("select c.objectId, count(c), "
            + "sum(case when c.replicationStatus = :replicated or c.replicationStatus = :complete "
            + "then 1L else 0L end) "
            + "from ChunkLocation c where c.objectId in :objectIds group by c.objectId")
    List<Object[]> countChunksGrouped(@Param("objectIds") Collection<UUID> objectIds,
                                      @Param("replicated") ReplicationStatus replicated,
                                      @Param("complete") ReplicationStatus complete);
}

