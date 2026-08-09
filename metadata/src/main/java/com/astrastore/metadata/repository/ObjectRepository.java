package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectRepository extends JpaRepository<ObjectRecord, UUID> {

    Optional<ObjectRecord> findByBucketIdAndKey(UUID bucketId, String key);

    Optional<ObjectRecord> findByBucketAndKey(Bucket bucket, String key);

    Page<ObjectRecord> findByBucketIdAndStatus(UUID bucketId, ObjectStatus status, Pageable pageable);

    Page<ObjectRecord> findByBucketAndStatus(Bucket bucket, ObjectStatus status, Pageable pageable);

    boolean existsByBucketIdAndKey(UUID bucketId, String key);

    boolean existsByBucketAndKey(Bucket bucket, String key);

    void deleteByBucketId(UUID bucketId);
}
