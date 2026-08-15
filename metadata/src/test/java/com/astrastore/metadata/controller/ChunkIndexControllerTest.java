package com.astrastore.metadata.controller;

import com.astrastore.metadata.config.SecurityConfig;
import com.astrastore.metadata.dto.chunk.RecordChunkLocationsRequest;
import com.astrastore.metadata.dto.chunk.UpdateReplicaRequest;
import com.astrastore.metadata.entity.ChunkLocation;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.shared.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * These endpoints live under {@code /internal/v1}, the service-to-service
 * surface, so the real {@link SecurityConfig} is imported rather than left to
 * the slice's default chain: the requests below have to get past
 * {@code InternalServiceTokenFilter} exactly as upload and replication do.
 */
@WebMvcTest(ChunkIndexController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties =
        "astrastore.internal.service-token=" + ChunkIndexControllerTest.SERVICE_TOKEN)
class ChunkIndexControllerTest {

    /**
     * Overrides the blank token in {@code application-test.yaml}, and must stay
     * non-blank. Blank puts the filter in its open mode, where every internal
     * request is waved through: the tests below would still pass, and
     * {@link #internalEndpoint_WithoutServiceToken_IsRejected_TokenEnforced()}
     * would pass while proving the opposite of what it claims.
     */
    static final String SERVICE_TOKEN = "chunk-index-test-service-token";

    private static final String TOKEN_HEADER = "X-Astra-Service-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private io.micrometer.tracing.Tracer tracer;

    private UUID objectId;
    private ChunkLocation location0;

    @BeforeEach
    void setUp() {
        objectId = UUID.randomUUID();
        location0 = ChunkLocation.builder()
                .id(UUID.randomUUID())
                .objectId(objectId)
                .chunkIndex(0)
                .nodeId("storage-node-1")
                .replicaNodeId("storage-node-2")
                .replicationStatus(ReplicationStatus.REPLICATED)
                .checksum("checksum0")
                .build();
    }

    @Test
    void recordChunkLocations_Success() throws Exception {
        RecordChunkLocationsRequest.ChunkLocationItem item0 = new RecordChunkLocationsRequest.ChunkLocationItem(0, "storage-node-1", "checksum0");
        RecordChunkLocationsRequest request = new RecordChunkLocationsRequest(List.of(item0));

        when(objectService.recordChunkLocations(anyList())).thenReturn(List.of(location0));

        mockMvc.perform(post("/internal/v1/objects/{objectId}/chunk-locations", objectId)
                        .header(TOKEN_HEADER, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectId").value(objectId.toString()))
                .andExpect(jsonPath("$.chunksRecorded").value(1));
    }

    @Test
    void getChunkLocations_Success() throws Exception {
        when(objectService.getChunkLocations(objectId)).thenReturn(List.of(location0));

        mockMvc.perform(get("/internal/v1/objects/{objectId}/chunk-locations", objectId)
                        .header(TOKEN_HEADER, SERVICE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].nodeId").value("storage-node-1"));
    }

    @Test
    void updateChunkReplica_Success() throws Exception {
        UpdateReplicaRequest request = new UpdateReplicaRequest("storage-node-2", ReplicationStatus.REPLICATED);
        when(objectService.updateChunkReplica(eq(objectId), eq(0), eq("storage-node-2"), eq(ReplicationStatus.REPLICATED)))
                .thenReturn(location0);

        mockMvc.perform(patch("/internal/v1/objects/{objectId}/chunk-locations/{chunkIndex}", objectId, 0)
                        .header(TOKEN_HEADER, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replicaNodeId").value("storage-node-2"))
                .andExpect(jsonPath("$.replicationStatus").value("REPLICATED"));
    }

    /**
     * The security boundary the three tests above lean on: {@code /internal/**}
     * is unreachable without the service token.
     *
     * <p>The {@code _TokenEnforced} half of the name is the point. This test is
     * only meaningful while {@link #SERVICE_TOKEN} is non-blank — a blank token
     * puts {@code InternalServiceTokenFilter} in open mode, where the request
     * below reaches the handler and returns 200, and the assertion that would
     * catch that is the status one. Removing the class-level property override
     * to "simplify" this class turns a guard into a green no-op.
     *
     * <p>Fails loudly if the filter is relaxed, if it stops being registered, or
     * if the {@code @Order(1)} internal chain stops claiming these paths.
     */
    @Test
    void internalEndpoint_WithoutServiceToken_IsRejected_TokenEnforced() throws Exception {
        mockMvc.perform(get("/internal/v1/objects/{objectId}/chunk-locations", objectId))
                .andExpect(status().isUnauthorized())
                // A rejection a client can parse, not a bare status or a
                // container error page.
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ApiError.UNAUTHENTICATED))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // Rejected before the handler, so nothing was read or written.
        verifyNoInteractions(objectService);
    }
}
