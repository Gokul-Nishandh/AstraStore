package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.exception.BucketNotFoundException;
import com.astrastore.metadata.exception.DuplicateBucketException;
import com.astrastore.metadata.repository.BucketRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for Bucket CRUD operations and ownership checks.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BucketService {

    private final BucketRepository bucketRepository;
    private final ObjectRepository objectRepository;

    /**
     * Creates a new bucket for the given owner.
     */
    public Bucket createBucket(Bucket bucket) {
        if (bucketRepository.existsByOwnerIdAndName(bucket.getOwnerId(), bucket.getName())) {
            throw new DuplicateBucketException(
                    "Bucket with name '" + bucket.getName() + "' already exists for this owner.");
        }
        return bucketRepository.save(bucket);
    }

    /**
     * Returns a bucket by its ID.
     */
    @Transactional(readOnly = true)
    public Bucket getBucket(UUID bucketId) {
        return bucketRepository.findById(bucketId)
                .orElseThrow(() -> new BucketNotFoundException("Bucket not found: " + bucketId));
    }

    /**
     * Returns all buckets owned by a user.
     */
    @Transactional(readOnly = true)
    public List<Bucket> getBucketsByOwner(UUID ownerId) {
        return bucketRepository.findByOwnerId(ownerId);
    }

    /**
     * Returns a paginated list of buckets owned by a user.
     */
    @Transactional(readOnly = true)
    public Page<Bucket> getBucketsByOwner(UUID ownerId, Pageable pageable) {
        return bucketRepository.findByOwnerId(ownerId, pageable);
    }

    /**
     * Deletes a bucket after verifying it contains no active objects (cascade
     * guardrail).
     */
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

    /**
     * Checks whether a bucket exists for an owner.
     */
    @Transactional(readOnly = true)
    public boolean exists(UUID ownerId, String bucketName) {
        return bucketRepository.existsByOwnerIdAndName(ownerId, bucketName);
    }

    /**
     * Finds a bucket by owner ID and bucket name.
     */
    @Transactional(readOnly = true)
    public Bucket getBucketByOwnerAndName(UUID ownerId, String bucketName) {
        return bucketRepository.findByOwnerIdAndName(ownerId, bucketName)
                .orElseThrow(() -> new BucketNotFoundException(
                        "Bucket '" + bucketName + "' not found for owner " + ownerId));
    }
}