package com.astrastore.metadata.controller;

import com.astrastore.metadata.config.SecurityConfig;
import com.astrastore.metadata.dto.chunk.ChunkPlacementResponse;
import com.astrastore.metadata.dto.chunk.NodeChunkResponse;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.service.ChunkPlacementService;
import com.astrastore.metadata.web.Pageables;
import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authorisation boundary is the point of this class.
 *
 * <p>These two endpoints are the only reads in the metadata service that are
 * not scoped to the caller's own rows, so the tests that matter most are the
 * refusals: a signed-in non-admin must not be able to enumerate a node's
 * chunks or another account's placements. The real {@link SecurityConfig} is
 * imported so {@code @EnableMethodSecurity} is active and the class-level
 * {@code @PreAuthorize} is genuinely exercised rather than assumed.
 */
@WebMvcTest(AdminChunkController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminChunkControllerTest {

    private static final AstraPrincipal ADMIN = new AstraPrincipal(
            1L, "ops", "ops@astrastore.test", Set.of(AstraPrincipal.ROLE_ADMIN), false);

    private static final AstraPrincipal USER = new AstraPrincipal(
            42L, "dana", "dana@astrastore.test", Set.of(AstraPrincipal.ROLE_USER), false);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChunkPlacementService chunkPlacementService;

    @MockBean
    private io.micrometer.tracing.Tracer tracer;

    private UUID objectId;
    private NodeChunkResponse nodeChunk;
    private ChunkPlacementResponse placement;

    @BeforeEach
    void setUp() {
        objectId = UUID.randomUUID();

        placement = new ChunkPlacementResponse(
                UUID.randomUUID(), 0, "storage-node-1", "storage-node-2",
                ReplicationStatus.REPLICATED, "checksum0", Instant.now());

        nodeChunk = new NodeChunkResponse(
                UUID.randomUUID(), objectId, "reports/q3.pdf", UUID.randomUUID(), "reports",
                12_000_000L, 0, NodeChunkResponse.ChunkRole.PRIMARY, "storage-node-2",
                ReplicationStatus.REPLICATED, "checksum0", Instant.now());
    }

    private static RequestPostProcessor as(AstraPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    // --- The happy paths --------------------------------------------------

    @Test
    void chunksOnNode_ReturnsPageForAdmin() throws Exception {
        when(chunkPlacementService.chunksOnNode(eq(List.of("storage-node-1")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nodeChunk), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/admin/chunks").param("nodeId", "storage-node-1").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objectKey").value("reports/q3.pdf"))
                .andExpect(jsonPath("$.content[0].role").value("PRIMARY"))
                .andExpect(jsonPath("$.content[0].peerNodeId").value("storage-node-2"))
                .andExpect(jsonPath("$.content[0].replicationStatus").value("REPLICATED"));
    }

    @Test
    void chunksOfObject_ReturnsPlacementsForAdmin() throws Exception {
        when(chunkPlacementService.placementsForObject(objectId)).thenReturn(List.of(placement));

        mockMvc.perform(get("/api/v1/admin/objects/{objectId}/chunks", objectId).with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].nodeId").value("storage-node-1"))
                .andExpect(jsonPath("$[0].replicaNodeId").value("storage-node-2"))
                .andExpect(jsonPath("$[0].replicationStatus").value("REPLICATED"));
    }

    /**
     * An object with no indexed placements is an empty list, not a 404 — the
     * console renders "no chunks recorded yet" rather than "object missing".
     */
    @Test
    void chunksOfObject_UnindexedObjectIsAnEmptyList() throws Exception {
        when(chunkPlacementService.placementsForObject(objectId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/objects/{objectId}/chunks", objectId).with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- The refusals -----------------------------------------------------

    /**
     * The disclosure this endpoint would otherwise be: a node's chunk listing
     * spans every account in the cluster, so any authenticated caller reaching
     * it could enumerate other people's objects by name.
     */
    @Test
    void chunksOnNode_NonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/chunks").param("nodeId", "storage-node-1").with(as(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiError.FORBIDDEN));

        // Refused before the service was asked anything.
        verifyNoInteractions(chunkPlacementService);
    }

    /**
     * Placement is not owner-scoped and cannot be, so a non-admin must be
     * refused outright — otherwise this becomes a way to confirm that another
     * account's object id is real and count its chunks.
     */
    @Test
    void chunksOfObject_NonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/objects/{objectId}/chunks", objectId).with(as(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ApiError.FORBIDDEN));

        verifyNoInteractions(chunkPlacementService);
    }

    @Test
    void chunksOnNode_AnonymousIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/chunks").param("nodeId", "storage-node-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiError.UNAUTHENTICATED));

        verifyNoInteractions(chunkPlacementService);
    }

    @Test
    void chunksOfObject_AnonymousIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/objects/{objectId}/chunks", objectId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ApiError.UNAUTHENTICATED));

        verifyNoInteractions(chunkPlacementService);
    }

    // --- Request handling -------------------------------------------------

    /**
     * A node answers to two names — the base URL written into
     * {@code chunk_locations.node_id} and the placement registry's short id —
     * so the console sends both and every one of them must reach the service.
     * Binding only the first would silently under-report what a node holds.
     */
    @Test
    void chunksOnNode_AcceptsEveryIdentifierTheNodeAnswersTo() throws Exception {
        when(chunkPlacementService.chunksOnNode(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nodeChunk), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/admin/chunks")
                        .param("nodeId", "storage-node-1")
                        .param("nodeId", "http://storage-node-1:8088")
                        .with(as(ADMIN)))
                .andExpect(status().isOk());

        verify(chunkPlacementService).chunksOnNode(
                eq(List.of("storage-node-1", "http://storage-node-1:8088")), any(Pageable.class));
    }

    @Test
    void chunksOnNode_MissingNodeIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/chunks").with(as(ADMIN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chunkPlacementService);
    }

    /**
     * {@code ?sort=} is bound straight into a {@code Sort} and handed to
     * Hibernate as a property path, so an unrecognised key must be dropped
     * rather than passed through — and the page size must be clamped, or one
     * request can pull the whole table.
     */
    @Test
    void chunksOnNode_SanitisesSortAndPageSize() throws Exception {
        when(chunkPlacementService.chunksOnNode(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/api/v1/admin/chunks")
                        .param("nodeId", "storage-node-1")
                        .param("sort", "dropTable,desc")
                        .param("size", "100000")
                        .with(as(ADMIN)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chunkPlacementService).chunksOnNode(eq(List.of("storage-node-1")), pageable.capture());

        assertThat(pageable.getValue().getPageSize()).isEqualTo(Pageables.MAX_PAGE_SIZE);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void chunksOnNode_AcceptsAWhitelistedSort() throws Exception {
        when(chunkPlacementService.chunksOnNode(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mockMvc.perform(get("/api/v1/admin/chunks")
                        .param("nodeId", "storage-node-1")
                        .param("sort", "chunkIndex,asc")
                        .with(as(ADMIN)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chunkPlacementService).chunksOnNode(eq(List.of("storage-node-1")), pageable.capture());

        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "chunkIndex"));
    }
}
