package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.bucket.BucketRequest;
import com.astrastore.metadata.dto.bucket.BucketResponse;
import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/buckets")
@RequiredArgsConstructor
public class BucketController {

    private static final UUID DEFAULT_OWNER_ID = UUID.nameUUIDFromBytes("default-owner".getBytes());

    private final BucketService bucketService;
    private final com.astrastore.metadata.repository.BucketRepository bucketRepository;
    private final ObjectService objectService;

    @PostMapping
    public ResponseEntity<BucketResponse> createBucket(
            @Valid @RequestBody BucketRequest request) {

        UUID ownerId = request.ownerId() != null ? request.ownerId() : DEFAULT_OWNER_ID;

        Bucket bucket = Bucket.builder()
                .name(request.name())
                .ownerId(ownerId)
                .build();

        Bucket savedBucket = bucketService.createBucket(bucket);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(savedBucket));
    }

    @GetMapping("/{bucketId}")
    public ResponseEntity<BucketResponse> getBucket(
            @PathVariable UUID bucketId) {

        Bucket bucket = bucketService.getBucket(bucketId);

        return ResponseEntity.ok(toResponse(bucket));
    }

    /**
     * Look up a bucket by its human-readable name (used by CLI path-targeting).
     * Returns 404 if no bucket with that name exists.
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<BucketResponse> getBucketByName(
            @PathVariable String name) {

        Bucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new com.astrastore.metadata.exception.BucketNotFoundException(
                        "Bucket not found: " + name));

        return ResponseEntity.ok(toResponse(bucket));
    }

    @GetMapping
    public ResponseEntity<Page<BucketResponse>> getBucketsByOwner(
            @RequestParam(required = false) UUID ownerId,
            @PageableDefault(size = 50) Pageable pageable) {

        UUID targetOwnerId = ownerId != null ? ownerId : DEFAULT_OWNER_ID;

        Page<BucketResponse> buckets = bucketService.getBucketsByOwner(targetOwnerId, pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/{bucketId}/objects")
    public ResponseEntity<Page<ObjectResponse>> getObjectsInBucket(
            @PathVariable UUID bucketId,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<ObjectResponse> objects = objectService.listObjects(bucketId, pageable)
                .map(this::toObjectResponse);

        return ResponseEntity.ok(objects);
    }

    @DeleteMapping("/{bucketId}")
    public ResponseEntity<Void> deleteBucket(
            @PathVariable UUID bucketId) {

        bucketService.deleteBucket(bucketId);

        return ResponseEntity.noContent().build();
    }

    private BucketResponse toResponse(Bucket bucket) {
        return new BucketResponse(
                bucket.getId(),
                bucket.getName(),
                bucket.getOwnerId(),
                bucket.getCreatedAt());
    }

    private ObjectResponse toObjectResponse(ObjectRecord obj) {
        long chunksTotal = objectService.countChunksTotal(obj.getId());
        long chunksReplicated = objectService.countChunksReplicated(obj.getId());

        return new ObjectResponse(
                obj.getId(),
                obj.getBucket().getId(),
                obj.getKey(),
                obj.getSizeBytes(),
                obj.getChecksum(),
                obj.getContentType(),
                obj.getStatus(),
                obj.getCreatedAt(),
                chunksReplicated,
                chunksTotal);
    }
}