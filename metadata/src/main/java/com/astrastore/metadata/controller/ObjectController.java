package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectRequest;
import com.astrastore.metadata.dto.object.ObjectResponse;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ObjectController {

    private final ObjectService objectService;
    private final BucketService bucketService;

    // ==========================================
    // Public REST Endpoints (/api/v1/objects)
    // ==========================================

    @GetMapping("/api/v1/objects/{objectId}")
    public ResponseEntity<ObjectResponse> getObject(@PathVariable UUID objectId) {
        ObjectRecord objectRecord = objectService.getObject(objectId);
        return ResponseEntity.ok(toResponse(objectRecord));
    }

    @DeleteMapping("/api/v1/objects/{objectId}")
    public ResponseEntity<Void> deleteObject(@PathVariable UUID objectId) {
        objectService.softDeleteObject(objectId);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Internal Service Endpoints (/internal/v1/objects)
    // ==========================================

    @PostMapping("/internal/v1/objects")
    public ResponseEntity<ObjectResponse> createObjectInternal(@Valid @RequestBody ObjectRequest request) {
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

        ObjectRecord savedRecord = objectService.createObject(builder.build());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(savedRecord));
    }

    private ObjectResponse toResponse(ObjectRecord obj) {
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
                chunksTotal
        );
    }
}
