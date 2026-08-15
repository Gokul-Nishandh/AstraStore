package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.bucket.BucketRequest;
import com.astrastore.metadata.dto.bucket.BucketResponse;
import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.metadata.web.ObjectResponseAssembler;
import com.astrastore.metadata.web.Pageables;
import com.astrastore.shared.security.AstraPrincipal;
import com.astrastore.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Buckets, scoped to the authenticated caller.
 *
 * <p>Every handler resolves the caller first and every lookup goes through the
 * owner-scoped service methods, so a bucket id belonging to another account is
 * indistinguishable from one that does not exist.
 */
@RestController
@RequestMapping("/api/v1/buckets")
@RequiredArgsConstructor
@Validated
public class BucketController {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort DEFAULT_OBJECT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final BucketService bucketService;
    private final ObjectService objectService;
    private final ObjectResponseAssembler assembler;

    @PostMapping
    public ResponseEntity<BucketResponse> createBucket(@Valid @RequestBody BucketRequest request) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        Bucket saved = bucketService.createBucketForUser(request.name(), principal);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{bucketId}")
    public ResponseEntity<BucketResponse> getBucket(@PathVariable UUID bucketId) {
        return ResponseEntity.ok(toResponse(
                bucketService.getBucketForUser(bucketId, CurrentUser.require())));
    }

    /**
     * Look up one of the caller's buckets by name (used by CLI path-targeting).
     * Scoped to the caller: names are not globally unique, and resolving
     * someone else's bucket here would hand out its id.
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<BucketResponse> getBucketByName(
            @PathVariable @Size(max = 63) String name) {

        return ResponseEntity.ok(toResponse(
                bucketService.getBucketByNameForUser(name, CurrentUser.require())));
    }

    /**
     * @param search optional case-insensitive substring of the bucket name
     * @param pageable {@code ?sort=} accepts {@code name} and {@code createdAt},
     *                 each with {@code ,asc} or {@code ,desc}
     */
    @GetMapping
    public ResponseEntity<Page<BucketResponse>> listBuckets(
            @RequestParam(required = false) @Size(max = 200) String search,
            @PageableDefault(size = 50) Pageable pageable) {

        AstraPrincipal principal = CurrentUser.require();

        Page<BucketResponse> buckets = bucketService.listBucketsForUser(
                        principal,
                        Pageables.normalizeSearch(search),
                        Pageables.sanitize(pageable, Pageables.BUCKET_SORTS, DEFAULT_SORT))
                .map(this::toResponse);

        return ResponseEntity.ok(buckets);
    }

    /**
     * Active objects in one of the caller's buckets.
     *
     * <p>Bucket ownership is verified before a single object row is read.
     *
     * @param search optional case-insensitive substring of the object key
     * @param pageable {@code ?sort=} accepts {@code key}, {@code size} and
     *                 {@code createdAt}
     */
    @GetMapping("/{bucketId}/objects")
    public ResponseEntity<Page<ObjectResponse>> listObjectsInBucket(
            @PathVariable UUID bucketId,
            @RequestParam(required = false) @Size(max = 1024) String search,
            @PageableDefault(size = 50) Pageable pageable) {

        AstraPrincipal principal = CurrentUser.require();
        // Throws NOT_FOUND unless the caller owns the bucket.
        bucketService.getBucketForUser(bucketId, principal);

        Page<ObjectResponse> objects = assembler.toPage(
                objectService.listObjectsInBucket(
                        bucketId,
                        Pageables.normalizeSearch(search),
                        Pageables.sanitize(pageable, Pageables.OBJECT_SORTS, DEFAULT_OBJECT_SORT)),
                principal.userId());

        return ResponseEntity.ok(objects);
    }

    @DeleteMapping("/{bucketId}")
    public ResponseEntity<Void> deleteBucket(@PathVariable UUID bucketId) {
        AstraPrincipal principal = CurrentUser.require();
        CurrentUser.assertCanWrite();

        bucketService.deleteBucketForUser(bucketId, principal);

        return ResponseEntity.noContent().build();
    }

    private BucketResponse toResponse(Bucket bucket) {
        return new BucketResponse(
                bucket.getId(),
                bucket.getName(),
                bucket.getOwnerId(),
                bucket.getCreatedAt(),
                bucket.getOwnerUserId());
    }
}
