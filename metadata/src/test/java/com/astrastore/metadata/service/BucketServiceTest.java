package com.astrastore.metadata.service;

import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.exception.BucketNotFoundException;
import com.astrastore.metadata.exception.DuplicateBucketException;
import com.astrastore.metadata.repository.BucketRepository;
import com.astrastore.metadata.repository.ObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    @Mock
    private BucketRepository bucketRepository;

    @Mock
    private ObjectRepository objectRepository;

    private BucketService bucketService;

    @BeforeEach
    void setUp() {
        bucketService = new BucketService(bucketRepository, objectRepository);
    }

    @Test
    void createBucket_savesWhenNameIsAvailable() {
        Bucket bucket = bucket("reports");
        when(bucketRepository.existsByOwnerIdAndName(OWNER_ID, "reports")).thenReturn(false);
        when(bucketRepository.save(bucket)).thenReturn(bucket);

        assertThat(bucketService.createBucket(bucket)).isEqualTo(bucket);
        verify(bucketRepository).save(bucket);
    }

    @Test
    void createBucket_throwsOnDuplicateName() {
        Bucket bucket = bucket("reports");
        when(bucketRepository.existsByOwnerIdAndName(OWNER_ID, "reports")).thenReturn(true);

        assertThatThrownBy(() -> bucketService.createBucket(bucket))
                .isInstanceOf(DuplicateBucketException.class)
                .hasMessageContaining("already exists");
        verify(bucketRepository, never()).save(any());
    }

    @Test
    void getBucket_returnsBucketWhenFound() {
        Bucket bucket = bucket("reports");
        when(bucketRepository.findById(BUCKET_ID)).thenReturn(Optional.of(bucket));

        assertThat(bucketService.getBucket(BUCKET_ID)).isEqualTo(bucket);
    }

    @Test
    void getBucket_throwsWhenMissing() {
        when(bucketRepository.findById(BUCKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bucketService.getBucket(BUCKET_ID))
                .isInstanceOf(BucketNotFoundException.class);
    }

    @Test
    void getBucketsByOwner_returnsList() {
        when(bucketRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(bucket("a"), bucket("b")));

        assertThat(bucketService.getBucketsByOwner(OWNER_ID)).hasSize(2);
    }

    @Test
    void getBucketsByOwner_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Bucket> page = new PageImpl<>(List.of(bucket("a")));
        when(bucketRepository.findByOwnerId(OWNER_ID, pageable)).thenReturn(page);

        assertThat(bucketService.getBucketsByOwner(OWNER_ID, pageable)).isEqualTo(page);
    }

    @Test
    void deleteBucket_deletesWhenEmpty() {
        Bucket bucket = bucket("reports");
        when(bucketRepository.findById(BUCKET_ID)).thenReturn(Optional.of(bucket));
        when(objectRepository.findByBucketIdAndStatus(eq(BUCKET_ID), eq(ObjectStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(Page.empty());

        bucketService.deleteBucket(BUCKET_ID);

        verify(objectRepository).deleteByBucketId(BUCKET_ID);
        verify(bucketRepository).delete(bucket);
    }

    @Test
    void deleteBucket_throwsWhenBucketContainsActiveObjects() {
        Bucket bucket = bucket("reports");
        ObjectRecord object = ObjectRecord.builder().key("q3.pdf").build();
        when(bucketRepository.findById(BUCKET_ID)).thenReturn(Optional.of(bucket));
        when(objectRepository.findByBucketIdAndStatus(eq(BUCKET_ID), eq(ObjectStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(object)));

        assertThatThrownBy(() -> bucketService.deleteBucket(BUCKET_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still contains active objects");
        verify(objectRepository, never()).deleteByBucketId(any());
        verify(bucketRepository, never()).delete(any());
    }

    @Test
    void exists_delegatesToRepository() {
        when(bucketRepository.existsByOwnerIdAndName(OWNER_ID, "reports")).thenReturn(true);

        assertThat(bucketService.exists(OWNER_ID, "reports")).isTrue();
    }

    @Test
    void getBucketByOwnerAndName_returnsBucketWhenFound() {
        Bucket bucket = bucket("reports");
        when(bucketRepository.findByOwnerIdAndName(OWNER_ID, "reports")).thenReturn(Optional.of(bucket));

        assertThat(bucketService.getBucketByOwnerAndName(OWNER_ID, "reports")).isEqualTo(bucket);
    }

    @Test
    void getBucketByOwnerAndName_throwsWhenMissing() {
        when(bucketRepository.findByOwnerIdAndName(OWNER_ID, "reports")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bucketService.getBucketByOwnerAndName(OWNER_ID, "reports"))
                .isInstanceOf(BucketNotFoundException.class);
    }

    private Bucket bucket(String name) {
        return Bucket.builder().id(BUCKET_ID).name(name).ownerId(OWNER_ID).build();
    }
}
