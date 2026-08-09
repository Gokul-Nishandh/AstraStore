package com.astrastore.download.controller;

import com.astrastore.download.dto.DownloadPayload;
import com.astrastore.download.exception.ChecksumVerificationException;
import com.astrastore.download.exception.ChunkUnavailableException;
import com.astrastore.download.exception.ObjectNotFoundException;
import com.astrastore.download.service.DownloadOrchestrator;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DownloadController.class)
@ActiveProfiles("test")
class DownloadControllerTest {

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();
    private static final byte[] CONTENT = "download-content".getBytes(StandardCharsets.UTF_8);
    private static final String CHECKSUM = "abc123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DownloadOrchestrator downloadOrchestrator;

    @MockBean
    private Tracer tracer;

    @Test
    void downloadById_streamsBytesWithHeaders() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID)).thenReturn(payload());
        stubStreamingBody();

        mockMvc.perform(get("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", CONTENT.length))
                .andExpect(header().string("X-Checksum-SHA256", CHECKSUM))
                .andExpect(content().bytes(CONTENT));
    }

    @Test
    void downloadByBucketAndKey_streamsBytesWithHeaders() throws Exception {
        when(downloadOrchestrator.prepareByBucketAndKey(BUCKET_ID, "q3.pdf")).thenReturn(payload());
        stubStreamingBody();

        mockMvc.perform(get("/api/v1/buckets/{bucketId}/objects/{key}", BUCKET_ID, "q3.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Checksum-SHA256", CHECKSUM))
                .andExpect(content().bytes(CONTENT));
    }

    @Test
    void downloadByBucketAndKey_withFolderKey_streamsBytes() throws Exception {
        when(downloadOrchestrator.prepareByBucketAndKey(BUCKET_ID, "reports/q3.pdf")).thenReturn(payload());
        stubStreamingBody();

        mockMvc.perform(get("/api/v1/buckets/{bucketId}/objects/reports/q3.pdf", BUCKET_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Checksum-SHA256", CHECKSUM))
                .andExpect(content().bytes(CONTENT));
    }

    @Test
    void head_returnsHeadersWithoutBody() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID)).thenReturn(payload());

        mockMvc.perform(head("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", CONTENT.length))
                .andExpect(header().string("X-Checksum-SHA256", CHECKSUM))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void downloadById_returns404WhenObjectMissing() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID))
                .thenThrow(new ObjectNotFoundException("Object not found"));

        mockMvc.perform(get("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void downloadById_returns422OnChecksumFailure() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID))
                .thenThrow(new ChecksumVerificationException("Chunk checksum mismatch"));

        mockMvc.perform(get("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void downloadById_returns502WhenChunkUnavailable() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID))
                .thenThrow(new ChunkUnavailableException("No node returned the chunk"));

        mockMvc.perform(get("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"));
    }

    @Test
    void download_defaultsContentTypeWhenNull() throws Exception {
        when(downloadOrchestrator.prepare(OBJECT_ID)).thenReturn(
                new DownloadPayload(OBJECT_ID, null, CONTENT.length, CHECKSUM, List.of()));
        stubStreamingBody();

        mockMvc.perform(get("/api/v1/objects/{objectId}", OBJECT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    private void stubStreamingBody() throws Exception {
        doAnswer(invocation -> {
            OutputStream os = invocation.getArgument(1);
            os.write(CONTENT);
            return null;
        }).when(downloadOrchestrator).writeBody(any(DownloadPayload.class), any(OutputStream.class));
    }

    private DownloadPayload payload() {
        return new DownloadPayload(OBJECT_ID, "application/pdf", CONTENT.length, CHECKSUM, List.of());
    }
}
