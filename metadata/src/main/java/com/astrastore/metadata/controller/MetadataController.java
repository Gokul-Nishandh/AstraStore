package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.FileMetadataRequest;
import com.astrastore.metadata.dto.FileMetadataResponse;
import com.astrastore.metadata.entity.FileMetadata;
import com.astrastore.metadata.repository.FileMetadataRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for file metadata CRUD operations.
 * The gateway strips the /metadata prefix before forwarding here,
 * so these are reachable at /api/metadata/files, etc.
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Slf4j
public class MetadataController {

    private final FileMetadataRepository metadataRepository;

    /**
     * Lists all files. Use ?owner=<email> to filter by owner.
     */
    @GetMapping("/files")
    public ResponseEntity<?> listFiles(
            @RequestParam(required = false) String owner,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("Listing files — owner={}", owner);

        if (owner != null && !owner.isBlank()) {
            Page<FileMetadata> page = metadataRepository.findByOwner(owner, pageable);
            return ResponseEntity.ok(page.map(this::toResponse));
        }

        Page<FileMetadata> page = metadataRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    /**
     * Returns metadata for a single file by ID.
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<?> getFile(@PathVariable Long id) {
        log.info("Fetching file metadata — id={}", id);
        return metadataRepository.findById(id)
                .map(this::toResponse)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(id));
    }

    /**
     * Creates a new file metadata record.
     * Returns 409 Conflict if a file with the same content hash already exists.
     */
    @PostMapping("/files")
    public ResponseEntity<?> createFile(@Valid @RequestBody FileMetadataRequest request) {
        log.info("Creating file metadata — filename={}, owner={}",
                request.getFilename(), request.getOwner());

        // Deduplication check
        if (request.getContentHash() != null &&
            metadataRepository.existsByContentHash(request.getContentHash())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A file with this content hash already exists"));
        }

        FileMetadata entity = toEntity(request);
        FileMetadata saved = metadataRepository.save(entity);
        log.info("File metadata created — id={}", saved.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(saved));
    }

    /**
     * Updates an existing file metadata record.
     */
    @PutMapping("/files/{id}")
    public ResponseEntity<?> updateFile(
            @PathVariable Long id,
            @Valid @RequestBody FileMetadataRequest request
    ) {
        log.info("Updating file metadata — id={}", id);
        return metadataRepository.findById(id)
                .map(existing -> {
                    existing.setFilename(request.getFilename());
                    existing.setContentType(request.getContentType());
                    existing.setSize(request.getSize());
                    existing.setOwner(request.getOwner());
                    existing.setStorageLocation(request.getStorageLocation());
                    if (request.getReplicaCount() != null) {
                        existing.setReplicaCount(request.getReplicaCount());
                    }
                    FileMetadata updated = metadataRepository.save(existing);
                    log.info("File metadata updated — id={}", id);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .<ResponseEntity<?>>map(r -> r)
                .orElseGet(() -> notFound(id));
    }

    /**
     * Deletes a file metadata record.
     */
    @DeleteMapping("/files/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        log.info("Deleting file metadata — id={}", id);
        if (!metadataRepository.existsById(id)) {
            return notFound(id);
        }
        metadataRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- private helpers ----

    private ResponseEntity<?> notFound(Long id) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "File metadata not found with id: " + id));
    }

    private FileMetadata toEntity(FileMetadataRequest req) {
        return FileMetadata.builder()
                .filename(req.getFilename())
                .contentType(req.getContentType())
                .size(req.getSize())
                .owner(req.getOwner())
                .contentHash(req.getContentHash())
                .storageLocation(req.getStorageLocation())
                .replicaCount(req.getReplicaCount() != null ? req.getReplicaCount() : 1)
                .build();
    }

    private FileMetadataResponse toResponse(FileMetadata entity) {
        return FileMetadataResponse.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .contentType(entity.getContentType())
                .size(entity.getSize())
                .owner(entity.getOwner())
                .contentHash(entity.getContentHash())
                .storageLocation(entity.getStorageLocation())
                .replicaCount(entity.getReplicaCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
