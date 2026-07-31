package com.astrastore.storagenode.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Initializes the storage directory structure at application startup.
 * Creates 256 hexadecimal subdirectories (00-ff) for hash-based fan-out.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StorageConfig {

    private static final String STORAGE_ROOT = "/data/storage";

    @PostConstruct
    public void initDirectoryFanOut() throws IOException {
        for (int i = 0; i < 256; i++) {
            String hex = String.format("%02x", i);
            Path dir = Paths.get(STORAGE_ROOT, hex);
            Files.createDirectories(dir);
            log.debug("Created storage directory: {}", dir);
        }
        log.info("Initialized {} hash directories under {}", 256, STORAGE_ROOT);
    }

    /**
     * Returns the absolute path where a chunk should be stored.
     * Uses the first two characters of the chunk ID as the hash prefix
     * to determine which subdirectory (00-ff) the chunk belongs to.
     *
     * @param chunkId the unique chunk identifier (hexadecimal string)
     * @return the absolute path to the chunk file
     */
    public Path getFinalPath(String chunkId) {
        String prefix = chunkId.substring(0, 2);
        return Paths.get(STORAGE_ROOT, prefix, chunkId);
    }

    public Path getStorageRoot() {
        return Paths.get(STORAGE_ROOT);
    }
}
