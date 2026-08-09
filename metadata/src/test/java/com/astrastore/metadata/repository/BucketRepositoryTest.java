package com.astrastore.metadata.repository;

import com.astrastore.metadata.entity.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BucketRepositoryTest {

    @Autowired
    private BucketRepository bucketRepository;

    @Test
    void saveAndFindById() {
        Bucket saved = bucketRepository.save(bucket("reports"));

        Optional<Bucket> found = bucketRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("reports");
    }

    @Test
    void findByOwnerIdAndName_filtersByOwner() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        bucketRepository.save(bucket("reports", ownerA));
        bucketRepository.save(bucket("reports", ownerB));

        Optional<Bucket> found = bucketRepository.findByOwnerIdAndName(ownerA, "reports");
        Optional<Bucket> otherOwner = bucketRepository.findByOwnerIdAndName(ownerB, "reports");

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerId()).isEqualTo(ownerA);
        assertThat(otherOwner).isPresent();
        assertThat(otherOwner.get().getOwnerId()).isEqualTo(ownerB);
    }

    @Test
    void existsByOwnerIdAndName() {
        bucketRepository.save(bucket("reports"));

        assertThat(bucketRepository.existsByOwnerIdAndName(OWNER, "reports")).isTrue();
        assertThat(bucketRepository.existsByOwnerIdAndName(OWNER, "missing")).isFalse();
    }

    @Test
    void findByOwnerId_returnsList() {
        UUID owner = UUID.randomUUID();
        bucketRepository.save(bucket("a", owner));
        bucketRepository.save(bucket("b", owner));
        bucketRepository.save(bucket("other", UUID.randomUUID()));

        List<Bucket> buckets = bucketRepository.findByOwnerId(owner);

        assertThat(buckets).hasSize(2).extracting(Bucket::getName).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void findByOwnerId_returnsPage() {
        UUID owner = UUID.randomUUID();
        bucketRepository.save(bucket("a", owner));
        bucketRepository.save(bucket("b", owner));

        Page<Bucket> page = bucketRepository.findByOwnerId(owner, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private Bucket bucket(String name) {
        return bucket(name, OWNER);
    }

    private Bucket bucket(String name, UUID ownerId) {
        return Bucket.builder().name(name).ownerId(ownerId).build();
    }

    private static final UUID OWNER = UUID.randomUUID();
}
