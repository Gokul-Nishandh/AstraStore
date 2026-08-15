package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectRepository extends JpaRepository<ObjectRecord, UUID> {

    // --- Unscoped lookups -------------------------------------------------
    // Used by the internal service-to-service path and by owner-scoped service
    // methods that apply the ownership check themselves. Never call these
    // directly from a /api/v1 handler.

    Optional<ObjectRecord> findByBucketIdAndKey(UUID bucketId, String key);

    /**
     * Resolves a batch of objects with their bucket already loaded.
     *
     * <p>The fetch join is the point. {@code ObjectRecord.bucket} is lazy, so
     * reading the bucket name off a page of fifty rows would otherwise issue
     * fifty extra selects — and outside a transaction, throw instead.
     */
    @Query("select o from ObjectRecord o left join fetch o.bucket where o.id in :ids")
    List<ObjectRecord> findAllByIdInFetchingBucket(@Param("ids") Collection<UUID> ids);

    Optional<ObjectRecord> findByBucketAndKey(Bucket bucket, String key);

    Page<ObjectRecord> findByBucketIdAndStatus(UUID bucketId, ObjectStatus status, Pageable pageable);

    Page<ObjectRecord> findByBucketAndStatus(Bucket bucket, ObjectStatus status, Pageable pageable);

    boolean existsByBucketIdAndKey(UUID bucketId, String key);

    boolean existsByBucketAndKey(Bucket bucket, String key);

    void deleteByBucketId(UUID bucketId);

    // --- Bucket-scoped listing with search -------------------------------

    Page<ObjectRecord> findByBucketIdAndStatusAndKeyContainingIgnoreCase(
            UUID bucketId, ObjectStatus status, String key, Pageable pageable);

    // --- Owner-scoped lookups (the access-control path) -------------------

    /**
     * Load-by-id for user-facing endpoints. A miss covers "no such object",
     * "someone else's object" and "object in an unowned bucket" alike, so a
     * caller probing ids learns nothing about another account.
     */
    @Query("select o from ObjectRecord o where o.id = :id and o.bucket.ownerUserId = :ownerUserId")
    Optional<ObjectRecord> findByIdAndOwner(@Param("id") UUID id,
                                            @Param("ownerUserId") Long ownerUserId);

    Page<ObjectRecord> findByBucket_OwnerUserIdAndStatus(
            Long ownerUserId, ObjectStatus status, Pageable pageable);

    Page<ObjectRecord> findByBucket_OwnerUserIdAndStatusAndKeyContainingIgnoreCase(
            Long ownerUserId, ObjectStatus status, String key, Pageable pageable);

    List<ObjectRecord> findByBucket_OwnerUserIdAndStatus(
            Long ownerUserId, ObjectStatus status);

    long countByBucket_OwnerUserIdAndStatus(Long ownerUserId, ObjectStatus status);

    long countByBucketIdAndStatus(UUID bucketId, ObjectStatus status);

    /** Total bytes the caller currently stores. {@code 0} when they store nothing. */
    @Query("select coalesce(sum(o.sizeBytes), 0) from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status")
    long sumSizeBytesByOwnerAndStatus(@Param("ownerUserId") Long ownerUserId,
                                      @Param("status") ObjectStatus status);

    // --- Starred ----------------------------------------------------------

    /**
     * The caller's starred objects. Joined against {@code object_stars} in the
     * database rather than filtered in memory, and scoped by owner *and* status
     * so a stale star on a trashed or foreign object cannot leak a row back.
     */
    @Query(value = "select o from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "and exists (select 1 from ObjectStar s where s.objectId = o.id and s.userId = :ownerUserId)",
            countQuery = "select count(o) from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "and exists (select 1 from ObjectStar s where s.objectId = o.id and s.userId = :ownerUserId)")
    Page<ObjectRecord> findStarredByOwner(@Param("ownerUserId") Long ownerUserId,
                                          @Param("status") ObjectStatus status,
                                          Pageable pageable);

    /**
     * Kept in step with {@link #findStarredByOwner} on purpose: the stat the
     * dashboard shows must be the number of rows the starred page will render,
     * not the number of rows in {@code object_stars}.
     */
    @Query("select count(o) from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "and exists (select 1 from ObjectStar s where s.objectId = o.id and s.userId = :ownerUserId)")
    long countStarredByOwner(@Param("ownerUserId") Long ownerUserId,
                             @Param("status") ObjectStatus status);

    @Query(value = "select o from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "and lower(o.key) like lower(concat('%', :search, '%')) "
            + "and exists (select 1 from ObjectStar s where s.objectId = o.id and s.userId = :ownerUserId)",
            countQuery = "select count(o) from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "and lower(o.key) like lower(concat('%', :search, '%')) "
            + "and exists (select 1 from ObjectStar s where s.objectId = o.id and s.userId = :ownerUserId)")
    Page<ObjectRecord> searchStarredByOwner(@Param("ownerUserId") Long ownerUserId,
                                            @Param("status") ObjectStatus status,
                                            @Param("search") String search,
                                            Pageable pageable);

    // --- Aggregates for the dashboard charts -------------------------------

    /**
     * Bytes and object counts grouped by content type, for the caller only.
     *
     * <p>Grouped in the database rather than by walking a listing: the
     * dashboard would otherwise only ever describe the page it happened to
     * fetch, which is the same defect the scalar totals were introduced to
     * fix.
     *
     * @return rows of {@code [contentType, objectCount, totalBytes]}
     */
    @Query("select coalesce(o.contentType, 'application/octet-stream'), count(o), coalesce(sum(o.sizeBytes), 0) "
            + "from ObjectRecord o "
            + "where o.bucket.ownerUserId = :ownerUserId and o.status = :status "
            + "group by o.contentType")
    List<Object[]> aggregateByContentType(@Param("ownerUserId") Long ownerUserId,
                                          @Param("status") ObjectStatus status);

    /**
     * Objects and bytes written per day since {@code from}, for the caller only.
     *
     * <p>Days on which nothing was uploaded are absent rather than zero; the
     * client fills the gaps, because only it knows the range it means to plot.
     *
     * @return rows of {@code [day, objectCount, totalBytes]}
     */
    @Query(value = "select date_trunc('day', o.created_at) as day, count(*), coalesce(sum(o.size_bytes), 0) "
            + "from metadata.objects o "
            + "join metadata.buckets b on b.id = o.bucket_id "
            + "where b.owner_user_id = :ownerUserId and o.status = :status "
            + "and o.created_at >= :from "
            + "group by 1 order by 1", nativeQuery = true)
    List<Object[]> aggregateDailyUploads(@Param("ownerUserId") Long ownerUserId,
                                         @Param("status") String status,
                                         @Param("from") java.time.Instant from);
}
