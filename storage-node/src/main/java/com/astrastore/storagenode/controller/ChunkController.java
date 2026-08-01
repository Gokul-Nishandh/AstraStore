package com.astrastore.storagenode.controller;

import com.astrastore.storagenode.dto.ChunkResponse;
import com.astrastore.storagenode.service.ChunkStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;

/**
 * REST controller for chunk storage operations.
 * Handles store, retrieve, and delete of binary chunks.
 */
@RestController
@RequestMapping("/api/v1/chunks")
@RequiredArgsConstructor
@Slf4j
public class ChunkController {

    private final ChunkStorageService chunkStorageService;

    /**
     * Stores a binary chunk and returns its computed checksum.
     * Returns 201 Created with chunk metadata, or 409 Conflict if chunk already exists.
     */
    @PostMapping("/{chunkId}")
    public ResponseEntity<ChunkResponse> storeChunk(
            @PathVariable String chunkId,
            @RequestBody byte[] body
    ) {
        log.info("Storing chunk — chunkId={}, size={}", chunkId, body.length);

        try {
            String checksum = chunkStorageService.storeChunk(chunkId, body);

            ChunkResponse response = ChunkResponse.builder()
                    .chunkId(chunkId)
                    .checksum(checksum)
                    .sizeBytes((long) body.length)
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);

        } catch (IOException e) {
            log.error("Failed to store chunk — chunkId={}", chunkId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves a stored chunk by its ID.
     * Uses zero-copy streaming to efficiently serve the binary data.
     * Returns 200 OK with the binary data, or 404 Not Found if the chunk
     * does not exist.
     */
    @GetMapping("/{chunkId}")
    public ResponseEntity<Resource> loadChunk(@PathVariable String chunkId) {
        log.info("Loading chunk — chunkId={}", chunkId);

        Path path = chunkStorageService.loadChunkAsResource(chunkId);

        if (path == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(resource.contentLength()))
                    .body(resource);
        } catch (IOException e) {
            log.error("Failed to load chunk — chunkId={}", chunkId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes a stored chunk by its ID.
     * Returns 204 No Content on success, or 404 Not Found if the chunk
     * does not exist.
     */
    @DeleteMapping("/{chunkId}")
    public ResponseEntity<Void> deleteChunk(@PathVariable String chunkId) {
        log.info("Deleting chunk — chunkId={}", chunkId);

        try {
            boolean deleted = chunkStorageService.deleteChunk(chunkId);

            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to delete chunk — chunkId={}", chunkId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
