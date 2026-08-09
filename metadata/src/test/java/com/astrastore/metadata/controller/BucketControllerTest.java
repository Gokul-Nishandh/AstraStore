package com.astrastore.metadata.controller;

import com.astrastore.metadata.dto.bucket.BucketRequest;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.exception.DuplicateBucketException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BucketController.class)
@ActiveProfiles("test")
class BucketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BucketService bucketService;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private io.micrometer.tracing.Tracer tracer;

    private UUID bucketId;
    private UUID ownerId;
    private Bucket bucket;

    @BeforeEach
    void setUp() {
        bucketId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        bucket = Bucket.builder()
                .id(bucketId)
                .name("reports")
                .ownerId(ownerId)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void createBucket_Success() throws Exception {
        BucketRequest request = new BucketRequest("reports", ownerId);
        when(bucketService.createBucket(any(Bucket.class))).thenReturn(bucket);

        mockMvc.perform(post("/api/v1/buckets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bucketId.toString()))
                .andExpect(jsonPath("$.name").value("reports"))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
    }

    @Test
    void createBucket_DuplicateName_Conflict() throws Exception {
        BucketRequest request = new BucketRequest("reports", ownerId);
        when(bucketService.createBucket(any(Bucket.class)))
                .thenThrow(new DuplicateBucketException("Bucket already exists"));

        mockMvc.perform(post("/api/v1/buckets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void getBucket_Success() throws Exception {
        when(bucketService.getBucket(bucketId)).thenReturn(bucket);

        mockMvc.perform(get("/api/v1/buckets/{bucketId}", bucketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bucketId.toString()))
                .andExpect(jsonPath("$.name").value("reports"));
    }

    @Test
    void deleteBucket_NonEmpty_Conflict() throws Exception {
        doThrow(new IllegalStateException("Bucket contains active objects"))
                .when(bucketService).deleteBucket(bucketId);

        mockMvc.perform(delete("/api/v1/buckets/{bucketId}", bucketId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
