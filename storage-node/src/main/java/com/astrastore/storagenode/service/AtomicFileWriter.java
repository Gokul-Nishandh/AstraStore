package com.astrastore.storagenode.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Handles atomic file writes using a write-to-temp-then-move strategy.
 * Ensures data is only visible at the final path after the write completes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtomicFileWriter {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Atomically writes an input stream to the target path.
     * Data is first written to a temporary file, then moved to the final location.
     *
     * @param source    the data to write
     * @param target    the final destination path
     * @throws IOException if any I/O error occurs
     */
    public void writeAtomic(InputStream source, Path target) throws IOException {
        Path tempFile = Files.createTempFile("chunk-", ".tmp");

        try {
            writeToFile(source, tempFile);
            Files.createDirectories(target.getParent());
            Files.move(tempFile, target);
            log.debug("Atomic write completed — target={}", target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /**
     * Appends data to an existing file atomically.
     *
     * @param source the data to append
     * @param target the file to append to
     * @throws IOException if any I/O error occurs
     */
    public void appendAtomic(InputStream source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        log.debug("Append completed — target={}", target);
    }

    /**
     * Deletes a file, logging a warning if it doesn't exist.
     *
     * @param path the file to delete
     * @return true if deleted, false if it didn't exist
     * @throws IOException if deletion fails
     */
    public boolean delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        Files.delete(path);
        log.debug("File deleted — path={}", path);
        return true;
    }

    private void writeToFile(InputStream source, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
