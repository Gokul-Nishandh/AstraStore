package com.astrastore.upload.service;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.upload.client.MetadataClient;
import com.astrastore.upload.client.PlacementClient;
import com.astrastore.upload.client.StorageNodeClient;
import com.astrastore.upload.messaging.ReplicationEventPublisher;
import com.astrastore.upload.model.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadOrchestratorTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    @Mock
    private ZeroMemoryEngine zeroMemoryEngine;

    @Mock
    private MetadataClient metadataClient;

    @Mock
    private PlacementClient placementClient;

    @Mock
    private StorageNodeClient storageNodeClient;

    @Mock
    private ReplicationEventPublisher replicationEventPublisher;

    @Test
    void handleUpload_returnsUploadResultFromManifest() throws Exception {
        UploadOrchestrator orchestrator = new UploadOrchestrator(
                zeroMemoryEngine, metadataClient, placementClient, storageNodeClient, replicationEventPublisher);

        ObjectManifest manifest = ObjectManifest.builder()
                .objectId(OBJECT_ID.toString())
                .globalHash("global-hash")
                .chunks(List.of(
                        ChunkManifest.builder().chunkId("c0").nodeIp("n0").checksum("a").sizeBytes(10L).build(),
                        ChunkManifest.builder().chunkId("c1").nodeIp("n1").checksum("b").sizeBytes(20L).build()))
                .build();

        InputStream input = new ByteArrayInputStream("bytes".getBytes());
        when(zeroMemoryEngine.process(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), eq("application/pdf")))
                .thenReturn(manifest);

        UploadResult result = orchestrator.handleUpload(input, BUCKET_ID, "q3.pdf", "application/pdf");

        assertThat(result.objectId()).isEqualTo(OBJECT_ID);
        assertThat(result.bucketId()).isEqualTo(BUCKET_ID);
        assertThat(result.key()).isEqualTo("q3.pdf");
        assertThat(result.sizeBytes()).isEqualTo(30L);
        assertThat(result.checksum()).isEqualTo("global-hash");
        assertThat(result.chunkCount()).isEqualTo(2);
    }

    @Test
    void handleUpload_returnsNullObjectIdWhenManifestHasNone() throws Exception {
        UploadOrchestrator orchestrator = new UploadOrchestrator(
                zeroMemoryEngine, metadataClient, placementClient, storageNodeClient, replicationEventPublisher);

        ObjectManifest manifest = ObjectManifest.builder()
                .objectId(null)
                .globalHash("hash")
                .chunks(List.of())
                .build();

        when(zeroMemoryEngine.process(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), eq("application/pdf")))
                .thenReturn(manifest);

        UploadResult result = orchestrator.handleUpload(
                new ByteArrayInputStream(new byte[0]), BUCKET_ID, "q3.pdf", "application/pdf");

        assertThat(result.objectId()).isNull();
        assertThat(result.sizeBytes()).isZero();
        assertThat(result.chunkCount()).isZero();
    }
}
