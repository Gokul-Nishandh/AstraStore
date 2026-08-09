package com.astrastore.upload.service;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.shared.strategy.PlacementStrategy;
import com.astrastore.upload.chunking.ChecksumCalculator;
import com.astrastore.upload.client.MetadataClient;
import com.astrastore.upload.exception.ChecksumMismatchException;
import com.astrastore.upload.support.FakeStorageNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZeroMemoryEngineTest {

    private static final FakeStorageNode NODE = startNode();
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();

    @Mock
    private PlacementStrategy placementStrategy;

    @Mock
    private KafkaPublisherService kafkaPublisherService;

    @Mock
    private MetadataClient metadataClient;

    private ZeroMemoryEngine engine;

    private static FakeStorageNode startNode() {
        try {
            FakeStorageNode node = new FakeStorageNode();
            node.start();
            return node;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopNode() {
        NODE.stop();
    }

    @BeforeEach
    void setUp() {
        engine = new ZeroMemoryEngine(placementStrategy, objectMapper, kafkaPublisherService, metadataClient);
        NODE.reset();
    }

    @Test
    void process_streamsSingleChunkAndCommitsMetadata() throws Exception {
        when(placementStrategy.getNextTargetNode()).thenReturn(NODE.baseUrl());
        stubCreatedObject();

        byte[] data = "hello world".getBytes();
        ObjectManifest manifest = engine.process(
                new ByteArrayInputStream(data), BUCKET_ID, "q3.pdf", "application/pdf");

        assertThat(manifest.chunks()).hasSize(1);
        ChunkManifest chunk = manifest.chunks().get(0);
        assertThat(chunk.checksum()).isEqualTo(checksumCalculator.calculateSha256(data));
        assertThat(chunk.sizeBytes()).isEqualTo((long) data.length);
        assertThat(manifest.globalHash()).isEqualTo(checksumCalculator.calculateSha256(data));
        assertThat(manifest.objectId()).isEqualTo(OBJECT_ID.toString());
        assertThat(NODE.requestCount()).isEqualTo(1);

        verify(metadataClient).createObjectRecord(any(MetadataClient.CreateObjectRecordRequest.class));
        verify(metadataClient).recordChunkLocations(any(UUID.class), anyList());
        verify(kafkaPublisherService).publishChunkWritten(any());
    }

    @Test
    void process_withoutBucketDoesNotCommitMetadata() throws Exception {
        when(placementStrategy.getNextTargetNode()).thenReturn(NODE.baseUrl());

        byte[] data = "standalone".getBytes();
        ObjectManifest manifest = engine.process(new ByteArrayInputStream(data));

        assertThat(manifest.chunks()).hasSize(1);
        assertThat(manifest.globalHash()).isEqualTo(checksumCalculator.calculateSha256(data));
        assertThat(manifest.objectId()).isNotNull();

        verify(metadataClient, never()).createObjectRecord(any());
        verify(metadataClient, never()).recordChunkLocations(any(), anyList());
        verify(kafkaPublisherService).publishChunkWritten(any());
    }

    @Test
    void process_splitsAcrossChunkBoundary() throws Exception {
        when(placementStrategy.getNextTargetNode()).thenReturn(NODE.baseUrl());
        stubCreatedObject();

        byte[] data = new byte[(int) BoundaryTracker.CHUNK_SIZE_BYTES + 1];
        Arrays.fill(data, (byte) 0x41);

        ObjectManifest manifest = engine.process(new ByteArrayInputStream(data), BUCKET_ID, "big.bin", null);

        assertThat(manifest.chunks()).hasSize(2);
        assertThat(NODE.requestCount()).isEqualTo(2);
        assertThat(manifest.chunks().stream().mapToLong(ChunkManifest::sizeBytes).sum())
                .isEqualTo(data.length);
        assertThat(manifest.globalHash()).isEqualTo(checksumCalculator.calculateSha256(data));
        verify(metadataClient).recordChunkLocations(any(UUID.class), anyList());
    }

    @Test
    void process_throwsChecksumMismatchWhenNodeReturnsWrongChecksum() throws Exception {
        when(placementStrategy.getNextTargetNode()).thenReturn(NODE.baseUrl());
        NODE.forceChecksum("deadbeef");

        byte[] data = "corrupt-check".getBytes();

        assertThatThrownBy(() -> engine.process(new ByteArrayInputStream(data), BUCKET_ID, "q3.pdf", null))
                .isInstanceOf(ChecksumMismatchException.class);
        verify(metadataClient, never()).createObjectRecord(any());
    }

    private void stubCreatedObject() {
        MetadataClient.CreatedObjectRecordResponse created = new MetadataClient.CreatedObjectRecordResponse();
        created.setId(OBJECT_ID);
        when(metadataClient.createObjectRecord(any(MetadataClient.CreateObjectRecordRequest.class)))
                .thenReturn(created);
    }
}
