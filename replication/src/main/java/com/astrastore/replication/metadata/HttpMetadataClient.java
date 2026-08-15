package com.astrastore.replication.metadata;

import com.astrastore.replication.db.MockChunkDatabase;
import com.astrastore.shared.security.InternalServiceToken;
import com.astrastore.shared.security.InternalServiceTokenInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * REST implementation of MetadataClient that records replica locations in the
 * Metadata Service (PostgreSQL) via its internal chunk-location PATCH endpoint,
 * replacing the earlier in-memory mock. Also keeps the local MockChunkDatabase
 * in sync so existing health/heal endpoints keep working.
 */
@Component
@Primary
@Slf4j
public class HttpMetadataClient implements MetadataClient {

    private static final String CHUNK_ID_DELIMITER = "-chunk-";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 1000L;

    private final MockChunkDatabase mockChunkDatabase;
    private final RestTemplate restTemplate;
    private final String metadataServiceUrl;

    public HttpMetadataClient(
            MockChunkDatabase mockChunkDatabase,
            ObjectMapper objectMapper,
            @Value("${services.metadata-url:http://metadata:8084}") String metadataServiceUrl,
            InternalServiceToken serviceToken) {
        this.mockChunkDatabase = mockChunkDatabase;
        this.metadataServiceUrl = metadataServiceUrl;
        this.restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
        this.restTemplate.getInterceptors().add(new InternalServiceTokenInterceptor(serviceToken));
    }

    @Override
    public void addReplicaLocation(String chunkId, String nodeIp) {
        mockChunkDatabase.updateReplicaCount(chunkId, nodeIp);

        try {
            ChunkIdRef ref = ChunkIdRef.parse(chunkId);
            updateChunkLocationWithRetry(ref, nodeIp);
        } catch (Exception e) {
            log.error("Failed to record replica location in Metadata Service — chunkId={}, error={}",
                    chunkId, e.getMessage(), e);
        }
    }

    private void updateChunkLocationWithRetry(ChunkIdRef ref, String replicaNodeId) throws InterruptedException {
        String url = metadataServiceUrl
                + "/internal/v1/objects/" + ref.objectId()
                + "/chunk-locations/" + ref.chunkIndex();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("replicaNodeId", replicaNodeId, "replicationStatus", "REPLICATED"), headers);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Chunk location updated — objectId={}, chunkIndex={}, replica={}, status={}",
                            ref.objectId(), ref.chunkIndex(), replicaNodeId, response.getStatusCode());
                    return;
                }
                log.warn("Metadata PATCH returned {} for chunk — attempt {}/{}",
                        response.getStatusCode(), attempt, MAX_RETRIES);
            } catch (Exception e) {
                log.warn("Metadata PATCH failed for chunk — attempt {}/{}, error={}",
                        attempt, MAX_RETRIES, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException("Metadata Service did not accept the replica update for chunk index "
                + ref.chunkIndex() + " after " + MAX_RETRIES + " attempts");
    }

    private record ChunkIdRef(UUID objectId, int chunkIndex) {
        static ChunkIdRef parse(String chunkId) {
            int idx = chunkId.indexOf(CHUNK_ID_DELIMITER);
            if (idx <= 0 || idx + CHUNK_ID_DELIMITER.length() >= chunkId.length()) {
                throw new IllegalArgumentException("Invalid chunk id: " + chunkId);
            }
            return new ChunkIdRef(
                    UUID.fromString(chunkId.substring(0, idx)),
                    Integer.parseInt(chunkId.substring(idx + CHUNK_ID_DELIMITER.length())));
        }
    }
}
