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

    Optional<Bucket> findByOwnerIdAndName(UUID ownerId, String name);

    List<Bucket> findByOwnerId(UUID ownerId);

    Page<Bucket> findByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByOwnerIdAndName(UUID ownerId, String name);

    Optional<Bucket> findByName(String name);
}
