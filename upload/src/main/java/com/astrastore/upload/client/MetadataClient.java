package com.astrastore.upload.client;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MetadataClient {

    private final RestTemplate restTemplate;
    private final String metadataServiceUrl;

    public MetadataClient(
            @Value("${services.metadata-url:http://localhost:8084}") String metadataServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.metadataServiceUrl = metadataServiceUrl;
    }

    /**
     * Step 1: Store object metadata in PostgreSQL via Metadata Service
     */
    public CreatedObjectRecordResponse createObjectRecord(CreateObjectRecordRequest request) {
        String url = metadataServiceUrl + "/internal/v1/objects";
        log.info("Committing object metadata to PostgreSQL — url={}, key={}", url, request.getKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateObjectRecordRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<CreatedObjectRecordResponse> response = restTemplate.postForEntity(
                url, entity, CreatedObjectRecordResponse.class);

        return response.getBody();
    }

    /**
     * Step 2: Store chunk locations in PostgreSQL via Metadata Service
     */
    public RecordChunkLocationsResponse recordChunkLocations(UUID objectId, List<ChunkLocationItem> chunks) {
        String url = metadataServiceUrl + "/internal/v1/objects/" + objectId + "/chunk-locations";
        log.info("Committing chunk locations to PostgreSQL — url={}, objectId={}, count={}",
                url, objectId, chunks.size());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RecordChunkLocationsRequest body = RecordChunkLocationsRequest.builder()
                .chunks(chunks)
                .build();
        HttpEntity<RecordChunkLocationsRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<RecordChunkLocationsResponse> response = restTemplate.postForEntity(
                url, entity, RecordChunkLocationsResponse.class);

        return response.getBody();
    }

    // --- DTOs ---

    @Getter
    @Builder
    public static class CreateObjectRecordRequest {
        private final UUID id;
        private final UUID bucketId;
        private final String key;
        private final Long sizeBytes;
        private final String checksum;
        private final String contentType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreatedObjectRecordResponse {
        private UUID id;
        private UUID bucketId;
        private String key;
        private Long sizeBytes;
        private String checksum;
        private String contentType;
        private String status;
        private String createdAt;
    }

    @Getter
    @Builder
    public static class RecordChunkLocationsRequest {
        private final List<ChunkLocationItem> chunks;
    }

    @Getter
    @Builder
    public static class ChunkLocationItem {
        private final Integer chunkIndex;
        private final String nodeId;
        private final String checksum;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RecordChunkLocationsResponse {
        private UUID objectId;
        private int chunksRecorded;
    }
}
