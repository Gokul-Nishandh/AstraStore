package com.astrastore.download.service;

import com.astrastore.download.client.ChunkLocation;
import com.astrastore.download.client.MetadataClient;
import com.astrastore.download.client.ObjectMetadata;
import com.astrastore.download.dto.DownloadPayload;
import com.astrastore.download.fetch.ChunkFetcher;
import com.astrastore.download.fetch.ChunkReassembler;
import com.astrastore.download.fetch.FetchedChunk;
import com.astrastore.download.verify.ChecksumVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DownloadOrchestrator {

    private final MetadataClient metadataClient;
    private final ChunkFetcher chunkFetcher;
    private final ChunkReassembler chunkReassembler;
    private final ChecksumVerifier checksumVerifier;
    private final int streamBufferKb;
    private final Counter objectChecksumMismatch;

    public DownloadOrchestrator(
            MetadataClient metadataClient,
            ChunkFetcher chunkFetcher,
            ChunkReassembler chunkReassembler,
            ChecksumVerifier checksumVerifier,
            @Value("${download.stream-buffer-kb:256}") int streamBufferKb,
            MeterRegistry meterRegistry) {
        this.metadataClient = metadataClient;
        this.chunkFetcher = chunkFetcher;
        this.chunkReassembler = chunkReassembler;
        this.checksumVerifier = checksumVerifier;
        this.streamBufferKb = streamBufferKb;
        this.objectChecksumMismatch = Counter.builder("download.object_checksum_mismatch_total")
                .description("Objects whose reassembled bytes failed the stored checksum check")
                .register(meterRegistry);
    }

    public DownloadPayload prepare(UUID objectId) {
        ObjectMetadata metadata = metadataClient.getObject(objectId);
        return toPayload(metadata, metadataClient.getChunkLocations(objectId));
    }

    public DownloadPayload prepareByBucketAndKey(UUID bucketId, String key) {
        ObjectMetadata metadata = metadataClient.getObjectByBucketAndKey(bucketId, key);
        return toPayload(metadata, metadataClient.getChunkLocations(metadata.id()));
    }

    public void writeBody(DownloadPayload payload, OutputStream out) throws IOException {
        List<ChunkLocation> ordered = payload.chunks().stream()
                .sorted(Comparator.comparing(ChunkLocation::chunkIndex))
                .toList();

        if (ordered.isEmpty()) {
            out.flush();
            return;
        }

        List<CompletableFuture<FetchedChunk>> futures = chunkFetcher.submitAll(payload.objectId(), ordered);
        MessageDigest objectDigest = chunkReassembler.newObjectDigest();

        BufferedOutputStream buffered = new BufferedOutputStream(out, streamBufferKb * 1024);
        for (int i = 0; i < ordered.size(); i++) {
            FetchedChunk fetched = chunkFetcher.await(futures.get(i));
            checksumVerifier.verifyChunk(fetched.data(), fetched.expectedChecksum());
            chunkReassembler.write(fetched.data(), objectDigest, buffered);
        }
        buffered.flush();

        String computed = chunkReassembler.objectChecksum(objectDigest);
        if (!checksumVerifier.objectDigestMatches(computed, payload.checksum())) {
            log.error("Object checksum mismatch after reassembly — objectId={}, expected={}, computed={}",
                    payload.objectId(), payload.checksum(), computed);
            objectChecksumMismatch.increment();
        }
    }

    private DownloadPayload toPayload(ObjectMetadata metadata, List<ChunkLocation> chunks) {
        return new DownloadPayload(
                metadata.id(),
                metadata.contentType(),
                metadata.sizeBytes() != null ? metadata.sizeBytes() : 0L,
                metadata.checksum(),
                chunks);
    }
}
