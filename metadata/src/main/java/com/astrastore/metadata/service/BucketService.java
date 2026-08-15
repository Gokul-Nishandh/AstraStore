package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.exception.BucketNotFoundException;
import com.astrastore.metadata.exception.DuplicateBucketException;
import com.astrastore.metadata.repository.BucketRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import com.astrastore.metadata.security.OwnerIds;
import com.astrastore.shared.security.AstraPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Bucket CRUD, with ownership enforced here rather than in the controllers.
 *
 * <p>Putting the check at this layer means a new handler cannot forget it: the
 * only way to reach a bucket by id from a user-facing endpoint is
 * {@link #getBucketForUser}, which resolves and verifies the owner in a single
 * query.
 *
 * <p>A bucket belonging to another user is reported as {@link
 * BucketNotFoundException}, never as a permission error. Answering 403 would
 * confirm the bucket exists, which is a disclosure in itself.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BucketService {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;

    // ==================================================================
    // Owner-scoped API — everything under /api/v1 goes through these
    // ==================================================================

    /**
     * Creates a bucket owned by the caller. The owner is taken from the token;
     * a client-supplied owner id is ignored.
     */
    public Bucket createBucketForUser(String name, AstraPrincipal principal) {
        Long userId = principal.userId();
        if (bucketRepository.existsByOwnerUserIdAndName(userId, name)) {
            throw new DuplicateBucketException(
                    "Bucket with name '" + name + "' already exists for this owner.");
        }
        return bucketRepository.save(Bucket.builder()
                .name(name)
                .ownerUserId(userId)
                .ownerId(OwnerIds.forUser(userId))
                .build());
    }

    /**
     * Loads a bucket the caller is entitled to see.
     *
     * @throws BucketNotFoundException if it does not exist <em>or</em> belongs
     *                                 to someone else — deliberately
     *                                 indistinguishable.
     */
    @Transactional(readOnly = true)
    public Bucket getBucketForUser(UUID bucketId, AstraPrincipal principal) {
        if (principal.isAdmin()) {
            return bucketRepository.findById(bucketId)
                    .orElseThrow(() -> notFound(bucketId));
        }
        return bucketRepository.findByIdAndOwnerUserId(bucketId, principal.userId())
                .orElseThrow(() -> notFound(bucketId));
    }

    @Transactional(readOnly = true)
    public Bucket getBucketByNameForUser(String name, AstraPrincipal principal) {
        return bucketRepository.findByOwnerUserIdAndName(principal.userId(), name)
                .orElseThrow(() -> new BucketNotFoundException("Bucket not found: " + name));
    }

    /** The caller's buckets, optionally filtered by a case-insensitive name match. */
    @Transactional(readOnly = true)
    public Page<Bucket> listBucketsForUser(AstraPrincipal principal, String search, Pageable pageable) {
        Long userId = principal.userId();
        if (search == null) {
            return bucketRepository.findByOwnerUserId(userId, pageable);
        }
        return bucketRepository.findByOwnerUserIdAndNameContainingIgnoreCase(userId, search, pageable);
    }

    /**
     * Deletes one of the caller's buckets, refusing while it still holds active
     * objects so a delete cannot silently orphan stored chunks.
     */
    public void deleteBucketForUser(UUID bucketId, AstraPrincipal principal) {
        Bucket bucket = getBucketForUser(bucketId, principal);

        Page<ObjectRecord> activeObjects = objectRepository.findByBucketIdAndStatus(
                bucketId, ObjectStatus.ACTIVE, Pageable.ofSize(1));
        if (activeObjects.hasContent()) {
            throw new IllegalStateException(
                    "Cannot delete bucket '" + bucket.getName() + "' because it still contains active objects.");
        }

        objectRepository.deleteByBucketId(bucketId);
        bucketRepository.delete(bucket);
    }

    @Transactional(readOnly = true)
    public long countBucketsForUser(Long userId) {
        return bucketRepository.countByOwnerUserId(userId);
    }

    // ==================================================================
    // Unscoped API — internal service-to-service path only
    // ==================================================================

    /**
     * Creates a bucket from a fully populated entity.
     *
     * <p>No ownership check: reserved for the internal path. User-facing
     * handlers must call {@link #createBucketForUser}.
     */
    public Bucket createBucket(Bucket bucket) {
        if (bucketRepository.existsByOwnerIdAndName(bucket.getOwnerId(), bucket.getName())) {
            throw new DuplicateBucketException(
                    "Bucket with name '" + bucket.getName() + "' already exists for this owner.");
        }
        return bucketRepository.save(bucket);
    }

    /**
     * Loads a bucket without any ownership check.
     *
     * <p>Internal path only — upload resolves the bucket for an object it is
     * committing, having already been authorised at the gateway.
     */
    @Transactional(readOnly = true)
    public Bucket getBucket(UUID bucketId) {
        return bucketRepository.findById(bucketId)
                .orElseThrow(() -> notFound(bucketId));
    }

    @Transactional(readOnly = true)
    public List<Bucket> getBucketsByOwner(UUID ownerId) {
        return bucketRepository.findByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public Page<Bucket> getBucketsByOwner(UUID ownerId, Pageable pageable) {
        return bucketRepository.findByOwnerId(ownerId, pageable);
    }

    /** @deprecated unscoped; use {@link #deleteBucketForUser}. */
    @Deprecated
    public void deleteBucket(UUID bucketId) {
        Bucket bucket = getBucket(bucketId);

        Page<ObjectRecord> activeObjects = objectRepository.findByBucketIdAndStatus(bucketId, ObjectStatus.ACTIVE,
                Pageable.ofSize(1));
        if (activeObjects.hasContent()) {
            throw new IllegalStateException(
                    "Cannot delete bucket '" + bucket.getName() + "' because it still contains active objects.");
        }

        objectRepository.deleteByBucketId(bucketId);
        bucketRepository.delete(bucket);
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID ownerId, String bucketName) {
        return bucketRepository.existsByOwnerIdAndName(ownerId, bucketName);
    }

    @Transactional(readOnly = true)
    public Bucket getBucketByOwnerAndName(UUID ownerId, String bucketName) {
        return bucketRepository.findByOwnerIdAndName(ownerId, bucketName)
                .orElseThrow(() -> new BucketNotFoundException(
                        "Bucket '" + bucketName + "' not found for owner " + ownerId));
    }

    private static BucketNotFoundException notFound(UUID bucketId) {
        return new BucketNotFoundException("Bucket not found: " + bucketId);
    }
}
