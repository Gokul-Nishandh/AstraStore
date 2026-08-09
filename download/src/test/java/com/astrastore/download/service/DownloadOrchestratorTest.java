package com.astrastore.download.service;

import com.astrastore.download.client.ChunkLocation;
import com.astrastore.download.client.MetadataClient;
import com.astrastore.download.client.ObjectMetadata;
import com.astrastore.download.dto.DownloadPayload;
import com.astrastore.download.exception.ChecksumVerificationException;
import com.astrastore.download.fetch.ChunkFetcher;
import com.astrastore.download.fetch.ChunkReassembler;
import com.astrastore.download.fetch.FetchedChunk;
import com.astrastore.download.verify.ChecksumVerifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadOrchestratorTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();
    private static final byte[] CHUNK_0 = "chunk-zero-data".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHUNK_1 = "chunk-one-data".getBytes(StandardCharsets.UTF_8);

    @Mock
    private MetadataClient metadataClient;

    @Mock
    private ChunkFetcher chunkFetcher;

    private final ChecksumVerifier checksumVerifier = new ChecksumVerifier();
    private final ChunkReassembler chunkReassembler = new ChunkReassembler();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private DownloadOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DownloadOrchestrator(
                metadataClient, chunkFetcher, chunkReassembler, checksumVerifier, 1, meterRegistry);
    }

    @Test
    void prepare_buildsPayloadFromMetadataAndChunkLocations() {
        ObjectMetadata metadata = objectMetadata("abc123");
        when(metadataClient.getObject(OBJECT_ID)).thenReturn(metadata);
        when(metadataClient.getChunkLocations(OBJECT_ID)).thenReturn(List.of(chunkLocation(0)));

        DownloadPayload payload = orchestrator.prepare(OBJECT_ID);

        assertThat(payload.objectId()).isEqualTo(OBJECT_ID);
        assertThat(payload.contentType()).isEqualTo("application/pdf");
        assertThat(payload.sizeBytes()).isEqualTo(30L);
        assertThat(payload.checksum()).isEqualTo("abc123");
        assertThat(payload.chunks()).hasSize(1);
    }

    @Test
    void prepareByBucketAndKey_resolvesThenLoadsChunks() {
        ObjectMetadata metadata = objectMetadata("abc123");
        when(metadataClient.getObjectByBucketAndKey(BUCKET_ID, "q3.pdf")).thenReturn(metadata);
        when(metadataClient.getChunkLocations(OBJECT_ID)).thenReturn(List.of(chunkLocation(0)));

        DownloadPayload payload = orchestrator.prepareByBucketAndKey(BUCKET_ID, "q3.pdf");

        assertThat(payload.objectId()).isEqualTo(OBJECT_ID);
    }

    @Test
    void writeBody_emptyObjectWritesNothingAndSkipsFetch() throws Exception {
        DownloadPayload payload = new DownloadPayload(OBJECT_ID, "application/pdf", 0L, "e3b0c4", List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        orchestrator.writeBody(payload, out);

        assertThat(out.toByteArray()).isEmpty();
        verify(chunkFetcher, never()).submitAll(any(), anyList());
    }

    @Test
    void writeBody_streamsVerifiedChunksAndMatchesObjectDigest() throws Exception {
        ObjectMetadata metadata = objectMetadata(checksumVerifier.sha256(concat(CHUNK_0, CHUNK_1)));
        when(metadataClient.getObject(OBJECT_ID)).thenReturn(metadata);
        when(metadataClient.getChunkLocations(OBJECT_ID)).thenReturn(List.of(chunkLocation(0), chunkLocation(1)));
        stubFetch();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        orchestrator.writeBody(orchestrator.prepare(OBJECT_ID), out);

        assertThat(out.toByteArray()).isEqualTo(concat(CHUNK_0, CHUNK_1));
        assertThat(meterRegistry.counter("download.object_checksum_mismatch_total").count()).isZero();
    }

    @Test
    void writeBody_recordsCounterWhenObjectDigestMismatches() throws Exception {
        ObjectMetadata metadata = objectMetadata("0000000000000000000000000000000000000000000000000000000000000000");
        when(metadataClient.getObject(OBJECT_ID)).thenReturn(metadata);
        when(metadataClient.getChunkLocations(OBJECT_ID)).thenReturn(List.of(chunkLocation(0), chunkLocation(1)));
        stubFetch();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        orchestrator.writeBody(orchestrator.prepare(OBJECT_ID), out);

        assertThat(meterRegistry.counter("download.object_checksum_mismatch_total").count()).isEqualTo(1);
    }

    @Test
    void writeBody_throwsWhenAChunkFailsChecksumVerification() throws Exception {
        ChunkLocation location = new ChunkLocation(
                UUID.randomUUID(), OBJECT_ID, 0, "http://node-a", null, "PENDING",
                "0000000000000000000000000000000000000000000000000000000000000000");
        when(metadataClient.getObject(OBJECT_ID)).thenReturn(objectMetadata("deadbeef"));
        when(metadataClient.getChunkLocations(OBJECT_ID)).thenReturn(List.of(location));
        stubFetch();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> orchestrator.writeBody(orchestrator.prepare(OBJECT_ID), out))
                .isInstanceOf(ChecksumVerificationException.class);
    }

    private void stubFetch() {
        when(chunkFetcher.submitAll(eq(OBJECT_ID), anyList())).thenAnswer(invocation -> {
            List<ChunkLocation> locations = invocation.getArgument(1);
            return locations.stream()
                    .map(loc -> {
                        byte[] bytes = loc.chunkIndex() == 0 ? CHUNK_0 : CHUNK_1;
                        return CompletableFuture.completedFuture(
                                new FetchedChunk(loc.chunkIndex(), loc.nodeId(), bytes, loc.checksum()));
                    })
                    .collect(Collectors.toList());
        });
        when(chunkFetcher.await(any())).thenAnswer(invocation ->
                ((CompletableFuture<FetchedChunk>) invocation.getArgument(0)).join());
    }

    private ObjectMetadata objectMetadata(String checksum) {
        return new ObjectMetadata(
                OBJECT_ID, BUCKET_ID, "q3.pdf", 30L, checksum, "application/pdf",
                "ACTIVE", "2026-07-26T10:20:00Z", 2L, 2L);
    }

    private ChunkLocation chunkLocation(int index) {
        return new ChunkLocation(
                UUID.randomUUID(), OBJECT_ID, index, "http://node-a", null, "PENDING",
                checksumVerifier.sha256(index == 0 ? CHUNK_0 : CHUNK_1));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
