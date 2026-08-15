package com.astrastore.metadata.controller;

import com.astrastore.metadata.config.SecurityConfig;
import com.astrastore.metadata.dto.bucket.BucketRequest;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.exception.DuplicateBucketException;
import com.astrastore.metadata.security.OwnerIds;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.metadata.web.ObjectResponseAssembler;
import com.astrastore.shared.security.AstraPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Every handler here reads the caller out of the security context and passes it
 * to the owner-scoped service methods, so the slice imports the real
 * {@link SecurityConfig} and each request carries an {@link AstraPrincipal}.
 * A request without one is a 401 from the filter chain, never a controller call.
 */
@WebMvcTest(BucketController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BucketControllerTest {

    private static final AstraPrincipal CALLER = new AstraPrincipal(
            42L, "dana", "dana@astrastore.test", Set.of(AstraPrincipal.ROLE_USER), false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BucketService bucketService;

    @MockBean
    private ObjectService objectService;

    /** Collaborator of the object listing, which no test here exercises. */
    @MockBean
    private ObjectResponseAssembler assembler;

    @MockBean
    private io.micrometer.tracing.Tracer tracer;

    private UUID bucketId;
    private Bucket bucket;

    @BeforeEach
    void setUp() {
        bucketId = UUID.randomUUID();
        bucket = Bucket.builder()
                .id(bucketId)
                .name("reports")
                .ownerUserId(CALLER.userId())
                .ownerId(OwnerIds.forUser(CALLER.userId()))
                .createdAt(Instant.now())
                .build();
    }

    private static RequestPostProcessor caller() {
        return authentication(new UsernamePasswordAuthenticationToken(
                CALLER, null, List.of(new SimpleGrantedAuthority("ROLE_" + AstraPrincipal.ROLE_USER))));
    }

    @Test
    void createBucket_Success() throws Exception {
        // The request names an owner the caller does not control; the handler
        // must ignore it and own the bucket to the authenticated user instead.
        BucketRequest request = new BucketRequest("reports", UUID.randomUUID());
        when(bucketService.createBucketForUser("reports", CALLER)).thenReturn(bucket);

        mockMvc.perform(post("/api/v1/buckets")
                        .with(caller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bucketId.toString()))
                .andExpect(jsonPath("$.name").value("reports"))
                .andExpect(jsonPath("$.ownerUserId").value(CALLER.userId()))
                .andExpect(jsonPath("$.ownerId").value(OwnerIds.forUser(CALLER.userId()).toString()));
    }

    @Test
    void createBucket_DuplicateName_Conflict() throws Exception {
        BucketRequest request = new BucketRequest("reports", null);
        when(bucketService.createBucketForUser("reports", CALLER))
                .thenThrow(new DuplicateBucketException("Bucket already exists"));

        mockMvc.perform(post("/api/v1/buckets")
                        .with(caller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void getBucket_Success() throws Exception {
        when(bucketService.getBucketForUser(bucketId, CALLER)).thenReturn(bucket);

        mockMvc.perform(get("/api/v1/buckets/{bucketId}", bucketId)
                        .with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bucketId.toString()))
                .andExpect(jsonPath("$.name").value("reports"));
    }

    @Test
    void deleteBucket_NonEmpty_Conflict() throws Exception {
        doThrow(new IllegalStateException("Bucket contains active objects"))
                .when(bucketService).deleteBucketForUser(bucketId, CALLER);

        mockMvc.perform(delete("/api/v1/buckets/{bucketId}", bucketId)
                        .with(caller()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
