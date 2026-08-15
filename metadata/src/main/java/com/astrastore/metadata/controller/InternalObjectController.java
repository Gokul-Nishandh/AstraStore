package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectRequest;
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

/**
 * The service-to-service surface, used by upload, download and replication.
 *
 * <p>These callers hold no user token — they act on a request the gateway has
 * already authorised — so this controller is gated on the shared service
 * token by {@code InternalServiceTokenFilter} and applies <b>no ownership
 * checks</b>. Nothing here may ever be exposed through the gateway.
 *
 * <p>The read endpoints mirror the {@code /api/v1} ones that download used to
 * call before the public surface required a JWT:
 * <ul>
 *   <li>{@code GET /api/v1/objects/{id}} → {@code GET /internal/v1/objects/{id}}</li>
 *   <li>{@code GET /api/v1/buckets/{id}/objects} →
 *       {@code GET /internal/v1/buckets/{id}/objects}</li>
 * </ul>
 * The response bodies are identical, so switching is a URL change on the
 * caller's side.
 */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalObjectController {

    private final ObjectService objectService;
    private final BucketService bucketService;

    /** Commits metadata for a freshly uploaded object. */
    @PostMapping("/objects")
    public ResponseEntity<ObjectResponse> createObject(@Valid @RequestBody ObjectRequest request) {
        Bucket bucket = bucketService.getBucket(request.bucketId());

        ObjectRecord.ObjectRecordBuilder builder = ObjectRecord.builder()
                .bucket(bucket)
                .key(request.key())
                .sizeBytes(request.sizeBytes())
                .checksum(request.checksum())
                .contentType(request.contentType());

        if (request.id() != null) {
            builder.id(request.id());
        }

        ObjectRecord saved = objectService.createObject(builder.build());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Active object metadata by id. Replaces download's {@code /api/v1/objects/{id}} call. */
    @GetMapping("/objects/{objectId}")
    public ResponseEntity<ObjectResponse> getObject(@PathVariable UUID objectId) {
        return ResponseEntity.ok(toResponse(objectService.getObject(objectId)));
    }

    /**
     * Active objects in a bucket. Replaces download's
     * {@code /api/v1/buckets/{id}/objects} call for key resolution.
     */
    @GetMapping("/buckets/{bucketId}/objects")
    public ResponseEntity<Page<ObjectResponse>> listObjects(
            @PathVariable UUID bucketId,
            @PageableDefault(size = 50) Pageable pageable) {

        return ResponseEntity.ok(objectService.listObjects(bucketId, pageable).map(this::toResponse));
    }

    /**
     * Per-object chunk counts, not batched: these handlers serve one object or
     * a bucket page for a machine caller, where the extra round trips are not
     * the bottleneck the user-facing listings had.
     */
    private ObjectResponse toResponse(ObjectRecord obj) {
        long chunksTotal = objectService.countChunksTotal(obj.getId());
        long chunksReplicated = objectService.countChunksReplicated(obj.getId());

        return new ObjectResponse(
                obj.getId(),
                obj.getBucket() != null ? obj.getBucket().getId() : null,
                obj.getKey(),
                obj.getSizeBytes(),
                obj.getChecksum(),
                obj.getContentType(),
                obj.getStatus(),
                obj.getCreatedAt(),
                chunksReplicated,
                chunksTotal,
                false,
                obj.getDeletedAt());
    }
}
