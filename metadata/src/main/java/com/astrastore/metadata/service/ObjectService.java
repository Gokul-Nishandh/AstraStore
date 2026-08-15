package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.exception.ObjectNotFoundException;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import com.astrastore.metadata.repository.ObjectStarRepository;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.metadata.dto.stats.StorageBreakdown;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Object metadata: listings, search, soft delete, trash, permanent delete and
 * chunk-location indexing.
 *
 * <p>Ownership lives here. An object's owner is its bucket's
 * {@code ownerUserId}, and every user-facing lookup resolves the two together
 * in one query ({@code findByIdAndOwner}) so there is no window in which a
 * record is loaded before the check runs. Objects belonging to another account
 * are reported as {@link ObjectNotFoundException} — a 403 would confirm the id
 * is real.
 *
 * <p>Soft-deleted rows are excluded from every listing, search and starred
 * result; they are reachable only through the trash endpoints.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ObjectService {

    private final ObjectRepository objectRepository;
    private final ChunkLocationRepository chunkLocationRepository;
    private final ObjectStarRepository objectStarRepository;
    private final ChunkCleanupPublisher chunkCleanupPublisher;

    // ==================================================================
    // Owner-scoped reads
    // ==================================================================

    /** An active object the caller owns. */
    @Transactional(readOnly = true)
    public ObjectRecord getObjectForUser(UUID objectId, AstraPrincipal principal) {
        return requireOwned(objectId, principal, ObjectStatus.ACTIVE);
    }

    /** A soft-deleted object the caller owns. */
    @Transactional(readOnly = true)
    public ObjectRecord getTrashedObjectForUser(UUID objectId, AstraPrincipal principal) {
        return requireOwned(objectId, principal, ObjectStatus.DELETED);
    }

    /** An object the caller owns, whatever its status. */
    @Transactional(readOnly = true)
    public ObjectRecord getAnyObjectForUser(UUID objectId, AstraPrincipal principal) {
        return requireOwned(objectId, principal, null);
    }

    /**
     * Active objects in one of the caller's buckets.
     *
     * <p>The bucket's ownership is verified by {@code bucketOwnerGuard} before
     * any row is read, so listing another user's bucket is a 404 rather than a
     * page of their filenames.
     */
    @Transactional(readOnly = true)
    public Page<ObjectRecord> listObjectsInBucket(UUID bucketId, String search, Pageable pageable) {
        if (search == null) {
            return objectRepository.findByBucketIdAndStatus(bucketId, ObjectStatus.ACTIVE, pageable);
        }
        return objectRepository.findByBucketIdAndStatusAndKeyContainingIgnoreCase(
                bucketId, ObjectStatus.ACTIVE, search, pageable);
    }

    /** The caller's active objects across every bucket they own. */
    @Transactional(readOnly = true)
    public Page<ObjectRecord> listObjectsForUser(Long userId, String search, Pageable pageable) {
        if (search == null) {
            return objectRepository.findByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.ACTIVE, pageable);
        }
        return objectRepository.findByBucket_OwnerUserIdAndStatusAndKeyContainingIgnoreCase(
                userId, ObjectStatus.ACTIVE, search, pageable);
    }

    /** The caller's most recently created objects, newest first. */
    @Transactional(readOnly = true)
    public List<ObjectRecord> listRecentForUser(Long userId, int limit) {
        Pageable page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return objectRepository.findByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.ACTIVE, page)
                .getContent();
    }

    /** The caller's starred, still-active objects. */
    @Transactional(readOnly = true)
    public Page<ObjectRecord> listStarredForUser(Long userId, String search, Pageable pageable) {
        if (search == null) {
            return objectRepository.findStarredByOwner(userId, ObjectStatus.ACTIVE, pageable);
        }
        return objectRepository.searchStarredByOwner(userId, ObjectStatus.ACTIVE, search, pageable);
    }

    /** The caller's soft-deleted objects. */
    @Transactional(readOnly = true)
    public Page<ObjectRecord> listTrashForUser(Long userId, String search, Pageable pageable) {
        if (search == null) {
            return objectRepository.findByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.DELETED, pageable);
        }
        return objectRepository.findByBucket_OwnerUserIdAndStatusAndKeyContainingIgnoreCase(
                userId, ObjectStatus.DELETED, search, pageable);
    }

    // ==================================================================
    // Owner-scoped writes
    // ==================================================================

    /** Moves one of the caller's objects to the trash. Idempotent. */
    public ObjectRecord softDeleteForUser(UUID objectId, AstraPrincipal principal) {
        ObjectRecord record = requireOwned(objectId, principal, null);
        if (record.getStatus() == ObjectStatus.DELETED) {
            return record;
        }
        record.setStatus(ObjectStatus.DELETED);
        record.setDeletedAt(Instant.now());
        return objectRepository.save(record);
    }

    /** Returns a trashed object to ACTIVE. Idempotent on an already-active object. */
    public ObjectRecord restoreForUser(UUID objectId, AstraPrincipal principal) {
        ObjectRecord record = requireOwned(objectId, principal, null);
        if (record.getStatus() == ObjectStatus.ACTIVE) {
            return record;
        }
        record.setStatus(ObjectStatus.ACTIVE);
        record.setDeletedAt(null);
        return objectRepository.save(record);
    }

    /**
     * Removes the metadata row for good and asks the storage tier to reclaim
     * the chunks.
     *
     * <p>Order matters: the chunk locations are read <em>before</em> anything is
     * deleted, because they are the only record of where the bytes live. Stars
     * pointing at the object go with it.
     */
    public void permanentlyDeleteForUser(UUID objectId, AstraPrincipal principal) {
        ObjectRecord record = requireOwned(objectId, principal, null);
        purge(List.of(record));
    }

    /**
     * Permanently deletes everything in the caller's trash.
     *
     * @return the number of objects removed
     */
    public int emptyTrashForUser(Long userId) {
        List<ObjectRecord> trashed =
                objectRepository.findByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.DELETED);
        if (trashed.isEmpty()) {
            return 0;
        }
        purge(trashed);
        return trashed.size();
    }

    private void purge(List<ObjectRecord> records) {
        List<UUID> ids = records.stream().map(ObjectRecord::getId).toList();

        // Read locations first — deleting them is what makes the bytes
        // unreachable, so the cleanup events must be built from live rows.
        List<ChunkLocation> locations = ids.stream()
                .flatMap(id -> chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(id).stream())
                .toList();

        objectStarRepository.deleteByObjectIdIn(ids);
        chunkLocationRepository.deleteByObjectIdIn(ids);
        objectRepository.deleteAll(records);

        for (UUID id : ids) {
            chunkCleanupPublisher.publishCleanup(id,
                    locations.stream().filter(l -> id.equals(l.getObjectId())).toList());
        }
    }

    // ==================================================================
    // Stats
    // ==================================================================

    @Transactional(readOnly = true)
    public long countActiveForUser(Long userId) {
        return objectRepository.countByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public long countTrashedForUser(Long userId) {
        return objectRepository.countByBucket_OwnerUserIdAndStatus(userId, ObjectStatus.DELETED);
    }

    @Transactional(readOnly = true)
    public long sumActiveBytesForUser(Long userId) {
        return objectRepository.sumSizeBytesByOwnerAndStatus(userId, ObjectStatus.ACTIVE);
    }

    /**
     * Storage broken down by file category and by day.
     *
     * <p>Both halves are aggregated in the database over the caller's own
     * rows. Days with no uploads are omitted rather than returned as zero:
     * the client knows the range it is plotting and can fill the gaps, while
     * the server inventing a zero would be indistinguishable from a real day
     * on which nothing happened.
     *
     * @param days window for the daily series, clamped to a sane range
     */
    @Transactional(readOnly = true)
    public StorageBreakdown storageBreakdown(Long userId, int days) {
        int window = Math.max(1, Math.min(days, 365));

        Map<String, long[]> categories = new LinkedHashMap<>();
        for (Object[] row : objectRepository.aggregateByContentType(userId, ObjectStatus.ACTIVE)) {
            String category = categoryOf((String) row[0]);
            long[] totals = categories.computeIfAbsent(category, key -> new long[2]);
            totals[0] += ((Number) row[1]).longValue();
            totals[1] += ((Number) row[2]).longValue();
        }

        List<StorageBreakdown.CategorySlice> byCategory = categories.entrySet().stream()
                .map(e -> new StorageBreakdown.CategorySlice(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(StorageBreakdown.CategorySlice::totalBytes).reversed())
                .toList();

        Instant from = Instant.now().minus(window, ChronoUnit.DAYS);
        List<StorageBreakdown.DailyPoint> daily =
                objectRepository.aggregateDailyUploads(userId, ObjectStatus.ACTIVE.name(), from).stream()
                        .map(row -> new StorageBreakdown.DailyPoint(
                                toIsoDate(row[0]),
                                ((Number) row[1]).longValue(),
                                ((Number) row[2]).longValue()))
                        .toList();

        return new StorageBreakdown(byCategory, daily);
    }

    /**
     * A grouped timestamp as a plain {@code YYYY-MM-DD} day.
     *
     * <p>The type the driver hands back for a {@code timestamptz} is not fixed
     * — Postgres returns {@link Instant} here, other drivers and older
     * configurations return {@link java.sql.Timestamp} or
     * {@link java.time.OffsetDateTime}. A single cast breaks on whichever one
     * you did not test against, so each is accepted.
     */
    private static String toIsoDate(Object value) {
        Instant instant = switch (value) {
            case Instant i -> i;
            case java.sql.Timestamp t -> t.toInstant();
            case java.time.OffsetDateTime o -> o.toInstant();
            case java.time.LocalDateTime l -> l.toInstant(ZoneOffset.UTC);
            case null -> throw new IllegalStateException("Grouped day was null");
            default -> throw new IllegalStateException(
                    "Unsupported timestamp type: " + value.getClass().getName());
        };
        return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /**
     * Collapses a MIME type into the same category vocabulary the console uses
     * for file icons, so a slice in the chart and the tile beside a file name
     * always agree. Kept deliberately in step with {@code fileCategory} in the
     * dashboard's format helpers.
     */
    private static String categoryOf(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return "image";
        if (type.startsWith("video/")) return "video";
        if (type.startsWith("audio/")) return "audio";
        if (type.contains("pdf")) return "pdf";
        if (type.contains("spreadsheet") || type.contains("csv") || type.contains("excel")) return "spreadsheet";
        if (type.contains("presentation") || type.contains("powerpoint")) return "presentation";
        if (type.contains("word") || type.startsWith("text/") || type.contains("markdown")) return "document";
        if (type.contains("zip") || type.contains("archive") || type.contains("compressed") || type.contains("tar")) return "archive";
        return "other";
    }

    /**
     * Starred <em>active</em> objects — the same set {@code GET /api/v1/starred}
     * pages through, so the dashboard's count and its list agree.
     */
    @Transactional(readOnly = true)
    public long countStarredForUser(Long userId) {
        return objectRepository.countStarredByOwner(userId, ObjectStatus.ACTIVE);
    }

    // ==================================================================
    // Unscoped API — internal service-to-service path only
    // ==================================================================

    /** Creates / records metadata for an uploaded object. Internal path only. */
    public ObjectRecord createObject(ObjectRecord objectRecord) {
        if (objectRecord.getId() == null) {
            objectRecord.setId(UUID.randomUUID());
        }
        return objectRepository.save(objectRecord);
    }

    /**
     * Loads an active object with no ownership check.
     *
     * <p>Internal path only — download resolves an object it has already been
     * authorised to serve. User-facing handlers must call
     * {@link #getObjectForUser}.
     */
    @Transactional(readOnly = true)
    public ObjectRecord getObject(UUID objectId) {
        return objectRepository.findById(objectId)
                .filter(obj -> obj.getStatus() == ObjectStatus.ACTIVE)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found with id: " + objectId));
    }

    @Transactional(readOnly = true)
    public ObjectRecord getObjectByBucketAndKey(UUID bucketId, String key) {
        return objectRepository.findByBucketIdAndKey(bucketId, key)
                .filter(obj -> obj.getStatus() == ObjectStatus.ACTIVE)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Object not found with key '" + key + "' in bucket " + bucketId));
    }

    @Transactional(readOnly = true)
    public Page<ObjectRecord> listObjects(UUID bucketId, Pageable pageable) {
        return objectRepository.findByBucketIdAndStatus(bucketId, ObjectStatus.ACTIVE, pageable);
    }

    /** @deprecated unscoped; use {@link #softDeleteForUser}. */
    @Deprecated
    public void softDeleteObject(UUID objectId) {
        ObjectRecord record = objectRepository.findById(objectId)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found with id: " + objectId));
        record.setStatus(ObjectStatus.DELETED);
        record.setDeletedAt(Instant.now());
        objectRepository.save(record);
    }

    public List<ChunkLocation> recordChunkLocations(List<ChunkLocation> locations) {
        return chunkLocationRepository.saveAll(locations);
    }

    @Transactional(readOnly = true)
    public List<ChunkLocation> getChunkLocations(UUID objectId) {
        return chunkLocationRepository.findByObjectIdOrderByChunkIndexAsc(objectId);
    }

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

    @Transactional(readOnly = true)
    public long countChunksTotal(UUID objectId) {
        return chunkLocationRepository.countByObjectId(objectId);
    }

    @Transactional(readOnly = true)
    public long countChunksReplicated(UUID objectId) {
        return chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.REPLICATED)
                + chunkLocationRepository.countByObjectIdAndReplicationStatus(objectId, ReplicationStatus.COMPLETE);
    }

    // ==================================================================

    /**
     * The single gate every user-facing object lookup passes through.
     *
     * @param requiredStatus the status the caller expects, or {@code null} for any
     */
    private ObjectRecord requireOwned(UUID objectId, AstraPrincipal principal, ObjectStatus requiredStatus) {
        ObjectRecord record = (principal.isAdmin()
                ? objectRepository.findById(objectId)
                : objectRepository.findByIdAndOwner(objectId, principal.userId()))
                .orElseThrow(() -> new ObjectNotFoundException("Object not found with id: " + objectId));

        if (requiredStatus != null && record.getStatus() != requiredStatus) {
            throw new ObjectNotFoundException("Object not found with id: " + objectId);
        }
        return record;
    }
}
