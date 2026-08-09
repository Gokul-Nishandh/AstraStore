package com.astrastore.upload.controller;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.shared.manifest.ObjectManifest;
import com.astrastore.upload.exception.ChecksumMismatchException;
import com.astrastore.upload.exception.ChunkWriteException;
import com.astrastore.upload.exception.NoAvailableNodesException;
import com.astrastore.upload.exception.ObjectTooLargeException;
import com.astrastore.upload.model.UploadResult;
import com.astrastore.upload.service.UploadOrchestrator;
import com.astrastore.upload.service.ZeroMemoryEngine;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@ActiveProfiles("test")
class UploadControllerTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadOrchestrator uploadOrchestrator;

    @MockBean
    private ZeroMemoryEngine zeroMemoryEngine;

    @MockBean
    private Tracer tracer;

    @Test
    void uploadFile_multipartReturnsManifestSummary() throws Exception {
        ObjectManifest manifest = ObjectManifest.builder()
                .objectId("object-id")
                .globalHash("global-hash")
                .chunks(List.of(ChunkManifest.builder()
                        .chunkId("c0").nodeIp("http://storage-node-1:8088").checksum("a").sizeBytes(5L).build()))
                .build();
        when(zeroMemoryEngine.process(any(InputStream.class))).thenReturn(manifest);

        mockMvc.perform(multipart("/")
                        .file(new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalHash").value("global-hash"))
                .andExpect(jsonPath("$.totalChunks").value(1));
    }

    @Test
    void putObject_returnsCreatedWithUploadResult() throws Exception {
        UploadResult result = new UploadResult(
                OBJECT_ID, BUCKET_ID, "q3.pdf", 100L, "hash", 2, Instant.parse("2026-07-26T10:20:00Z"));
        when(uploadOrchestrator.handleUpload(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), anyString()))
                .thenReturn(result);

        mockMvc.perform(put("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("some-bytes"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectId").value(OBJECT_ID.toString()))
                .andExpect(jsonPath("$.bucketId").value(BUCKET_ID.toString()))
                .andExpect(jsonPath("$.key").value("q3.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(100))
                .andExpect(jsonPath("$.checksum").value("hash"))
                .andExpect(jsonPath("$.chunkCount").value(2));
    }

    @Test
    void putObject_returns422OnChecksumMismatch() throws Exception {
        when(uploadOrchestrator.handleUpload(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), any()))
                .thenThrow(new ChecksumMismatchException("Chunk checksum mismatch"));

        mockMvc.perform(put("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("bytes"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void putObject_returns502OnChunkWriteFailure() throws Exception {
        when(uploadOrchestrator.handleUpload(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), any()))
                .thenThrow(new ChunkWriteException("Storage node write failed"));

        mockMvc.perform(put("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("bytes"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"));
    }

    @Test
    void putObject_returns413OnObjectTooLarge() throws Exception {
        when(uploadOrchestrator.handleUpload(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), any()))
                .thenThrow(new ObjectTooLargeException("Object exceeds maximum size"));

        mockMvc.perform(put("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("bytes"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void putObject_returns503WhenNoNodesAvailable() throws Exception {
        when(uploadOrchestrator.handleUpload(any(InputStream.class), eq(BUCKET_ID), eq("q3.pdf"), any()))
                .thenThrow(new NoAvailableNodesException("No healthy nodes available"));

        mockMvc.perform(put("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("bytes"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
