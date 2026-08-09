package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.object.ObjectRequest;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.exception.ObjectNotFoundException;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObjectController.class)
@ActiveProfiles("test")
class ObjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private BucketService bucketService;

    @MockBean
    private io.micrometer.tracing.Tracer tracer;

    private UUID objectId;
    private UUID bucketId;
    private Bucket bucket;
    private ObjectRecord objectRecord;

    @BeforeEach
    void setUp() {
        objectId = UUID.randomUUID();
        bucketId = UUID.randomUUID();
        bucket = Bucket.builder().id(bucketId).name("reports").build();
        objectRecord = ObjectRecord.builder()
                .id(objectId)
                .bucket(bucket)
                .key("q3.pdf")
                .sizeBytes(1024L)
                .checksum("abc123hash")
                .contentType("application/pdf")
                .status(ObjectStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getObject_Success() throws Exception {
        when(objectService.getObject(objectId)).thenReturn(objectRecord);
        when(objectService.countChunksTotal(objectId)).thenReturn(2L);
        when(objectService.countChunksReplicated(objectId)).thenReturn(2L);

        mockMvc.perform(get("/api/v1/objects/{objectId}", objectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(objectId.toString()))
                .andExpect(jsonPath("$.key").value("q3.pdf"))
                .andExpect(jsonPath("$.chunksReplicated").value(2))
                .andExpect(jsonPath("$.chunksTotal").value(2));
    }

    @Test
    void getObject_NotFound() throws Exception {
        when(objectService.getObject(objectId)).thenThrow(new ObjectNotFoundException("Object not found"));

        mockMvc.perform(get("/api/v1/objects/{objectId}", objectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteObject_Success() throws Exception {
        doNothing().when(objectService).softDeleteObject(objectId);

        mockMvc.perform(delete("/api/v1/objects/{objectId}", objectId))
                .andExpect(status().isNoContent());
    }

    @Test
    void createObjectInternal_Success() throws Exception {
        ObjectRequest request = new ObjectRequest(objectId, bucketId, "q3.pdf", 1024L, "abc123hash", "application/pdf");
        when(bucketService.getBucket(bucketId)).thenReturn(bucket);
        when(objectService.createObject(any(ObjectRecord.class))).thenReturn(objectRecord);

        mockMvc.perform(post("/internal/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(objectId.toString()))
                .andExpect(jsonPath("$.key").value("q3.pdf"));
    }
}
