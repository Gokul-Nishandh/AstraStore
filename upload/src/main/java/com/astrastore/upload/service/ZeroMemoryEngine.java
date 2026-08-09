package com.astrastore.upload.service;

import com.astrastore.shared.events.ChunkWrittenEvent;
import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.shared.strategy.PlacementStrategy;
import com.astrastore.upload.client.HttpStorageStreamClient;
import com.astrastore.upload.exception.ChecksumMismatchException;
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
    private final KafkaPublisherService kafkaPublisherService;
    private final com.astrastore.upload.client.MetadataClient metadataClient;

    public ZeroMemoryEngine(PlacementStrategy placementStrategy,
                            ObjectMapper objectMapper,
                            KafkaPublisherService kafkaPublisherService,
                            com.astrastore.upload.client.MetadataClient metadataClient) {
        this.placementStrategy = placementStrategy;
        this.objectMapper = objectMapper;
        this.kafkaPublisherService = kafkaPublisherService;
        this.metadataClient = metadataClient;
    }

    public ObjectManifest process(InputStream inputStream) throws IOException {
        return process(inputStream, null, null, null);
    }

    /**
     * Processes an input stream, distributes chunks to storage nodes, and commits metadata to PostgreSQL.
     */
    public ObjectManifest process(InputStream inputStream, UUID bucketId, String key, String contentType) throws IOException {
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

        // --- Commit Metadata to PostgreSQL via Metadata Service ---
        // Fail closed: if the metadata commit fails, the upload must NOT return
        // 201 Created, otherwise the client receives a success for an object
        // that was never recorded (Volume 1, Section 10.1 consistency model).
        UUID committedId = null;
        if (bucketId != null) {
            com.astrastore.upload.client.MetadataClient.CreateObjectRecordRequest objReq =
                    com.astrastore.upload.client.MetadataClient.CreateObjectRecordRequest.builder()
                            .id(UUID.fromString(objectId))
                            .bucketId(bucketId)
                            .key(key != null ? key : "uploaded-" + objectId)
                            .sizeBytes(totalBytesRead)
                            .checksum(globalHash)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build();

            com.astrastore.upload.client.MetadataClient.CreatedObjectRecordResponse createdObj =
                    metadataClient.createObjectRecord(objReq);

            committedId = createdObj.getId();

            List<com.astrastore.upload.client.MetadataClient.ChunkLocationItem> chunkItems = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ChunkManifest cm = chunks.get(i);
                chunkItems.add(com.astrastore.upload.client.MetadataClient.ChunkLocationItem.builder()
                        .chunkIndex(i)
                        .nodeId(cm.nodeIp())
                        .checksum(cm.checksum())
                        .build());
            }

            metadataClient.recordChunkLocations(committedId, chunkItems);
            log.info("Successfully committed object metadata and {} chunk locations to PostgreSQL", chunks.size());
        }

        String committedObjectId = committedId != null ? committedId.toString() : objectId;

        return ObjectManifest.builder()
                .objectId(committedObjectId)
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
            throw new ChecksumMismatchException("Chunk checksum mismatch — computed=" + computedHash +
                    ", storage=" + manifest.checksum());
        }

        log.debug("Chunk finalized — chunkId={}, node={}, checksum={}",
                manifest.chunkId(), node, manifest.checksum());

        publishChunkEvent(manifest);

        return manifest;
    }

    private void publishChunkEvent(ChunkManifest manifest) {
        ChunkWrittenEvent event = ChunkWrittenEvent.builder()
                .chunkId(manifest.chunkId())
                .primaryNodeIp(manifest.nodeIp())
                .sizeBytes(manifest.sizeBytes())
                .checksum(manifest.checksum())
                .build();

        kafkaPublisherService.publishChunkWritten(event);
    }
}
