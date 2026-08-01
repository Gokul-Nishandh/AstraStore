package com.astrastore.storagenode.service;

import com.astrastore.storagenode.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates chunk storage operations.
 * Coordinates hashing, atomic writes, and path resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkStorageService {

    private final StorageConfig storageConfig;
    private final HashService hashService;
    private final AtomicFileWriter atomicFileWriter;

    /**
     * Stores a chunk and computes its SHA-256 checksum.
     * The checksum is calculated during the write and returned in the response.
     *
     * @param chunkId the unique chunk identifier
     * @param stream  the chunk data
     * @return the computed SHA-256 checksum as a hexadecimal string
     * @throws IOException if storage fails or chunk already exists
     */
    public String storeChunk(String chunkId, InputStream stream) throws IOException {
        byte[] data = stream.readAllBytes();
        return storeChunk(chunkId, data);
    }

    /**
     * Stores a chunk and computes its SHA-256 checksum.
     *
     * @param chunkId the unique chunk identifier
     * @param data    the chunk data as byte array
     * @return the computed SHA-256 checksum as a hexadecimal string
     * @throws IOException if storage fails or chunk already exists
     */
    public String storeChunk(String chunkId, byte[] data) throws IOException {
        Path finalPath = storageConfig.getFinalPath(chunkId);

        if (Files.exists(finalPath)) {
            log.info("Chunk already exists (idempotent) — chunkId={}", chunkId);
            return hashService.computeSha256(data);
        }

        String checksum = hashService.computeSha256(data);
        atomicFileWriter.writeAtomic(new ByteArrayInputStream(data), finalPath);

        long size = Files.size(finalPath);
        log.info("Chunk stored — chunkId={}, checksum={}, size={}", chunkId, checksum, size);

        return checksum;
    }

    /**
     * Returns the path to a chunk for zero-copy streaming.
     *
     * @param chunkId the unique chunk identifier
     * @return the path, or null if not found
     */
    public Path loadChunkAsResource(String chunkId) {
        Path path = storageConfig.getFinalPath(chunkId);
        if (Files.exists(path)) {
            log.debug("Chunk found — chunkId={}", chunkId);
            return path;
        }
        log.warn("Chunk not found — chunkId={}", chunkId);
        return null;
    }

    /**
     * Deletes a chunk from storage.
     *
     * @param chunkId the unique chunk identifier
     * @return true if deleted, false if not found
     * @throws IOException if deletion fails
     */
    public boolean deleteChunk(String chunkId) throws IOException {
        Path path = storageConfig.getFinalPath(chunkId);
        boolean deleted = atomicFileWriter.delete(path);
        if (deleted) {
            log.info("Chunk deleted — chunkId={}", chunkId);
        }
        return deleted;
    }
}
