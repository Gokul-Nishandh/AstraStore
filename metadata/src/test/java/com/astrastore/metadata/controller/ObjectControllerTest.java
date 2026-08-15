package com.astrastore.metadata.controller;

import com.astrastore.metadata.config.SecurityConfig;
import com.astrastore.metadata.dto.object.ObjectRequest;
import com.astrastore.metadata.entity.Bucket;
import com.astrastore.metadata.entity.ObjectRecord;
import com.astrastore.metadata.entity.ObjectStatus;
import com.astrastore.metadata.entity.ReplicationStatus;
import com.astrastore.metadata.exception.ObjectNotFoundException;
import com.astrastore.metadata.repository.ChunkLocationRepository;
import com.astrastore.metadata.service.BucketService;
import com.astrastore.metadata.service.ObjectService;
import com.astrastore.metadata.service.StarService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers both surfaces that serve an {@code ObjectResponse} for a single
 * object: the user-facing {@link ObjectController}, whose caller is an
 * {@link AstraPrincipal} from the token, and {@link InternalObjectController},
 * whose caller is upload holding the shared service token. The real
 * {@link SecurityConfig} is imported so each is reached the way it is in
 * production.
 *
 * <p>The real {@link ObjectResponseAssembler} is imported too, with its two
 * data-source collaborators mocked — the star flag and the chunk counts are
 * part of the JSON contract the dashboard reads, so they are worth asserting
 * against the actual mapping rather than a stub of it.
 */
@WebMvcTest({ ObjectController.class, InternalObjectController.class })
@Import({ SecurityConfig.class, ObjectResponseAssembler.class })
@ActiveProfiles("test")
@TestPropertySource(properties =
        "astrastore.internal.service-token=" + ObjectControllerTest.SERVICE_TOKEN)
class ObjectControllerTest {

    /**
     * Overrides the blank token in {@code application-test.yaml}. Blank puts
     * {@code InternalServiceTokenFilter} in its open mode, which would let the
     * internal test pass without presenting a credential at all.
     */
    static final String SERVICE_TOKEN = "object-controller-test-service-token";

    private static final String TOKEN_HEADER = "X-Astra-Service-Token";

    private static final AstraPrincipal CALLER = new AstraPrincipal(
            42L, "dana", "dana@astrastore.test", Set.of(AstraPrincipal.ROLE_USER), false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private BucketService bucketService;

    @MockBean
    private StarService starService;

    @MockBean
    private ChunkLocationRepository chunkLocationRepository;

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
        bucket = Bucket.builder().id(bucketId).name("reports").ownerUserId(CALLER.userId()).build();
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

    private static RequestPostProcessor caller() {
        return authentication(new UsernamePasswordAuthenticationToken(
                CALLER, null, List.of(new SimpleGrantedAuthority("ROLE_" + AstraPrincipal.ROLE_USER))));
    }

    @Test
    void getObject_Success() throws Exception {
        when(objectService.getObjectForUser(objectId, CALLER)).thenReturn(objectRecord);
        when(chunkLocationRepository.countChunksGrouped(
                List.of(objectId), ReplicationStatus.REPLICATED, ReplicationStatus.COMPLETE))
                .thenReturn(List.<Object[]>of(new Object[] { objectId, 2L, 2L }));
        when(starService.starredAmong(CALLER.userId(), List.of(objectId))).thenReturn(Set.of(objectId));

        mockMvc.perform(get("/api/v1/objects/{objectId}", objectId)
                        .with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(objectId.toString()))
                .andExpect(jsonPath("$.key").value("q3.pdf"))
                .andExpect(jsonPath("$.chunksReplicated").value(2))
                .andExpect(jsonPath("$.chunksTotal").value(2))
                // Resolved for this caller, and null while the object is active.
                .andExpect(jsonPath("$.starred").value(true))
                .andExpect(jsonPath("$.deletedAt").value(nullValue()));
    }

    @Test
    void getObject_NotFound() throws Exception {
        when(objectService.getObjectForUser(objectId, CALLER))
                .thenThrow(new ObjectNotFoundException("Object not found"));

        mockMvc.perform(get("/api/v1/objects/{objectId}", objectId)
                        .with(caller()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteObject_Success() throws Exception {
        when(objectService.softDeleteForUser(objectId, CALLER)).thenReturn(objectRecord);

        mockMvc.perform(delete("/api/v1/objects/{objectId}", objectId)
                        .with(caller()))
                .andExpect(status().isNoContent());

        // The delete is a soft one — the object goes to the trash, not away.
        verify(objectService).softDeleteForUser(objectId, CALLER);
    }

    @Test
    void createObjectInternal_Success() throws Exception {
        ObjectRequest request = new ObjectRequest(objectId, bucketId, "q3.pdf", 1024L, "abc123hash", "application/pdf");
        when(bucketService.getBucket(bucketId)).thenReturn(bucket);
        when(objectService.createObject(any(ObjectRecord.class))).thenReturn(objectRecord);
        when(objectService.countChunksTotal(objectId)).thenReturn(2L);
        when(objectService.countChunksReplicated(objectId)).thenReturn(2L);

        mockMvc.perform(post("/internal/v1/objects")
                        .header(TOKEN_HEADER, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(objectId.toString()))
                .andExpect(jsonPath("$.key").value("q3.pdf"))
                .andExpect(jsonPath("$.chunksReplicated").value(2))
                .andExpect(jsonPath("$.chunksTotal").value(2))
                // The internal caller is a service, not a user: there is nobody
                // whose star could be resolved, so it is always false here.
                .andExpect(jsonPath("$.starred").value(false))
                .andExpect(jsonPath("$.deletedAt").value(nullValue()));
    }
}
