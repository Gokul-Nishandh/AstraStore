package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.Bucket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    // --- Legacy UUID-owner lookups ---------------------------------------
    // Kept for the (owner_id, name) uniqueness check. Not used for access
    // control: isolation always runs off ownerUserId below.

    Optional<Bucket> findByOwnerIdAndName(UUID ownerId, String name);

    List<Bucket> findByOwnerId(UUID ownerId);

    Page<Bucket> findByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);

    Optional<Bucket> findByName(String name);

    // --- Owner-scoped lookups (the access-control path) -------------------

    /**
     * The only safe way to load a bucket by id on a user-facing endpoint:
     * a miss covers both "no such bucket" and "someone else's bucket", so the
     * caller cannot tell them apart.
     */
    Optional<Bucket> findByIdAndOwnerUserId(UUID id, Long ownerUserId);

    Page<Bucket> findByOwnerUserId(Long ownerUserId, Pageable pageable);

    Page<Bucket> findByOwnerUserIdAndNameContainingIgnoreCase(Long ownerUserId, String name, Pageable pageable);

    Optional<Bucket> findByOwnerUserIdAndName(Long ownerUserId, String name);

    boolean existsByOwnerUserIdAndName(Long ownerUserId, String name);

    long countByOwnerUserId(Long ownerUserId);
}
