package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ObjectRepositoryTest {

    @Autowired
    private ObjectRepository objectRepository;

    @Autowired
    private BucketRepository bucketRepository;

    @Test
    void findByBucketIdAndKey_returnsObject() {
        Bucket bucket = bucketRepository.save(bucket());
        objectRepository.save(object(bucket, "q3.pdf", ObjectStatus.ACTIVE));

        Optional<ObjectRecord> found = objectRepository.findByBucketIdAndKey(bucket.getId(), "q3.pdf");

        assertThat(found).isPresent();
        assertThat(found.get().getKey()).isEqualTo("q3.pdf");
        assertThat(found.get().getBucket().getId()).isEqualTo(bucket.getId());
    }

    @Test
    void findByBucketIdAndKey_returnsEmptyForDifferentKey() {
        Bucket bucket = bucketRepository.save(bucket());
        objectRepository.save(object(bucket, "q3.pdf", ObjectStatus.ACTIVE));

        assertThat(objectRepository.findByBucketIdAndKey(bucket.getId(), "other.pdf")).isEmpty();
    }

    @Test
    void findByBucketIdAndStatus_filtersActiveObjects() {
        Bucket bucket = bucketRepository.save(bucket());
        objectRepository.save(object(bucket, "active.pdf", ObjectStatus.ACTIVE));
        objectRepository.save(object(bucket, "deleted.pdf", ObjectStatus.DELETED));

        Page<ObjectRecord> active = objectRepository.findByBucketIdAndStatus(
                bucket.getId(), ObjectStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(active.getContent()).hasSize(1);
        assertThat(active.getContent().get(0).getKey()).isEqualTo("active.pdf");
    }

    @Test
    void existsByBucketIdAndKey() {
        Bucket bucket = bucketRepository.save(bucket());
        objectRepository.save(object(bucket, "q3.pdf", ObjectStatus.ACTIVE));

        assertThat(objectRepository.existsByBucketIdAndKey(bucket.getId(), "q3.pdf")).isTrue();
        assertThat(objectRepository.existsByBucketIdAndKey(bucket.getId(), "missing.pdf")).isFalse();
    }

    @Test
    void deleteByBucketId_removesObjects() {
        Bucket bucket = bucketRepository.save(bucket());
        objectRepository.save(object(bucket, "q3.pdf", ObjectStatus.ACTIVE));

        objectRepository.deleteByBucketId(bucket.getId());

        assertThat(objectRepository.findByBucketIdAndKey(bucket.getId(), "q3.pdf")).isEmpty();
    }

    private Bucket bucket() {
        return bucketRepository.save(Bucket.builder().name("reports").ownerId(UUID.randomUUID()).build());
    }

    private ObjectRecord object(Bucket bucket, String key, ObjectStatus status) {
        return objectRepository.save(ObjectRecord.builder()
                .id(UUID.randomUUID())
                .bucket(bucket)
                .key(key)
                .sizeBytes(1024L)
                .checksum("abc123")
                .contentType("application/pdf")
                .status(status)
                .build());
    }
}
