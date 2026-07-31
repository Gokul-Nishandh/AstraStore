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
     * Stores a chunk after verifying its SHA-256 hash.
     * Buffers the entire stream to enable both hash computation and writing.
     *
     * @param chunkId      the unique chunk identifier
     * @param stream       the chunk data
     * @param expectedHash the expected SHA-256 hash
     * @return true if stored successfully, false if hash mismatch or chunk exists
     * @throws IOException if storage fails
     */
    /**
     * Stores a chunk after verifying its SHA-256 hash.
     * Buffers the entire stream to enable both hash computation and writing.
     *
     * @param chunkId      the unique chunk identifier
     * @param stream       the chunk data
     * @param expectedHash the expected SHA-256 hash
     * @return true if stored successfully, false if hash mismatch or chunk exists
     * @throws IOException if storage fails
     */
    public boolean storeChunk(String chunkId, InputStream stream, String expectedHash) throws IOException {
        byte[] data = stream.readAllBytes();
        return storeChunk(chunkId, data, expectedHash);
    }

    /**
     * Stores a chunk after verifying its SHA-256 hash.
     *
     * @param chunkId      the unique chunk identifier
     * @param data        the chunk data as byte array
     * @param expectedHash the expected SHA-256 hash
     * @return true if stored successfully, false if hash mismatch or chunk exists
     * @throws IOException if storage fails
     */
    public boolean storeChunk(String chunkId, byte[] data, String expectedHash) throws IOException {
        Path finalPath = storageConfig.getFinalPath(chunkId);

        if (Files.exists(finalPath)) {
            log.warn("Chunk already exists — chunkId={}", chunkId);
            return false;
        }

        String computedHash = hashService.computeSha256(data);

        if (!hashService.verifyHash(computedHash, expectedHash)) {
            log.warn("Hash mismatch — chunkId={}, expected={}, computed={}",
                    chunkId, expectedHash, computedHash);
            return false;
        }

        atomicFileWriter.writeAtomic(new ByteArrayInputStream(data), finalPath);
        log.info("Chunk stored — chunkId={}, size={}", chunkId, Files.size(finalPath));
        return true;
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
