package com.astrastore.upload.service;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.shared.strategy.PlacementStrategy;
import com.astrastore.upload.client.HttpStorageStreamClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core orchestrator for zero-memory file upload.
 * Uses a single 8KB buffer to stream data from client to storage nodes
 * without buffering the entire file in memory.
 */
@Service
@Slf4j
public class ZeroMemoryEngine {

    private static final int BUFFER_SIZE = 8192;

    private final PlacementStrategy placementStrategy;
    private final ObjectMapper objectMapper;

    public ZeroMemoryEngine(PlacementStrategy placementStrategy, ObjectMapper objectMapper) {
        this.placementStrategy = placementStrategy;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an input stream and distributes chunks to storage nodes.
     *
     * @param inputStream the client data stream
     * @return the complete ObjectManifest with all chunk metadata
     * @throws IOException if any I/O error occurs
     */
    public ObjectManifest process(InputStream inputStream) throws IOException {
        String objectId = UUID.randomUUID().toString();
        log.info("Starting zero-memory upload — objectId={}", objectId);

        DigestService globalDigest = new DigestService();
        List<ChunkManifest> chunks = new ArrayList<>();

        String currentNode = null;
        HttpStorageStreamClient currentClient = null;
        DigestService currentChunkDigest = null;
        BoundaryTracker boundaryTracker = new BoundaryTracker();
        long totalBytesRead = 0;

        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                globalDigest.update(buffer, 0, bytesRead);

                int offset = 0;
                int remaining = bytesRead;

                while (remaining > 0) {
                    if (currentClient == null) {
                        currentNode = placementStrategy.getNextTargetNode();
                        currentClient = new HttpStorageStreamClient(objectMapper);
                        String chunkId = objectId + "-chunk-" + String.format("%04d", chunks.size());
                        currentClient.openStream(currentNode, chunkId);
                        currentChunkDigest = new DigestService();
                        boundaryTracker.reset();
                    }

                    long bytesUntilBoundary = boundaryTracker.getBytesUntilBoundary();

                    if (bytesUntilBoundary == 0) {
                        ChunkManifest manifest = finalizeChunk(currentClient, currentChunkDigest, currentNode);
                        chunks.add(manifest);
                        currentClient = null;
                        currentChunkDigest = null;
                        continue;
                    }

                    int bytesToWrite = (int) Math.min(remaining, bytesUntilBoundary);
                    currentChunkDigest.update(buffer, offset, bytesToWrite);
                    currentClient.write(buffer, offset, bytesToWrite);

                    boolean boundaryHit = boundaryTracker.addAndCheck(bytesToWrite);

                    offset += bytesToWrite;
                    remaining -= bytesToWrite;

                    if (boundaryHit) {
                        ChunkManifest manifest = finalizeChunk(currentClient, currentChunkDigest, currentNode);
                        chunks.add(manifest);
                        currentClient = null;
                        currentChunkDigest = null;
                    }
                }
            }

            if (currentClient != null && boundaryTracker.getCurrentBytes() > 0) {
                ChunkManifest manifest = finalizeChunk(currentClient, currentChunkDigest, currentNode);
                chunks.add(manifest);
            }

        } catch (IOException e) {
            log.error("Upload failed — objectId={}", objectId, e);
            if (currentClient != null) {
                try {
                    currentClient.finalizeStream();
                } catch (IOException ex) {
                    log.warn("Failed to finalize chunk after error", ex);
                }
            }
            throw e;
        }

        String globalHash = globalDigest.extractHex();
        log.info("Upload complete — objectId={}, chunks={}, totalBytes={}, globalHash={}",
                objectId, chunks.size(), totalBytesRead, globalHash);

        return ObjectManifest.builder()
                .globalHash(globalHash)
                .chunks(chunks)
                .build();
    }

    private ChunkManifest finalizeChunk(HttpStorageStreamClient client,
                                      DigestService chunkDigest,
                                      String node) throws IOException {
        ChunkManifest manifest = client.finalizeStream();

        String computedHash = chunkDigest.extractHex();
        if (!computedHash.equalsIgnoreCase(manifest.checksum())) {
            throw new IOException("Chunk checksum mismatch — computed=" + computedHash +
                    ", storage=" + manifest.checksum());
        }

        log.debug("Chunk finalized — chunkId={}, node={}, checksum={}",
                manifest.chunkId(), node, manifest.checksum());
        return manifest;
    }
}
