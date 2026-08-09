package com.astrastore.download.client;

import com.astrastore.download.exception.MetadataUnavailableException;
import com.astrastore.download.exception.ObjectNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MetadataClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String metadataServiceUrl;

    public MetadataClient(
            ObjectMapper objectMapper,
            @Value("${services.metadata-url:http://localhost:8084}") String metadataServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.metadataServiceUrl = metadataServiceUrl;
    }

    public ObjectMetadata getObject(UUID objectId) {
        String url = metadataServiceUrl + "/api/v1/objects/" + objectId;
        log.info("Fetching object metadata — url={}", url);
        return get(url, ObjectMetadata.class);
    }

    public ObjectMetadata getObjectByBucketAndKey(UUID bucketId, String key) {
        String url = metadataServiceUrl + "/api/v1/buckets/" + bucketId + "/objects?size=500";
        log.info("Resolving object by bucket + key — url={}, key={}", url, key);

        String json = getRaw(url);
        try {
            JsonNode root = objectMapper.readTree(json);
            for (JsonNode item : root.path("content")) {
                ObjectMetadata candidate = objectMapper.treeToValue(item, ObjectMetadata.class);
                if (key.equals(candidate.key())) {
                    return candidate;
                }
            }
        } catch (JsonProcessingException e) {
            throw new MetadataUnavailableException("Invalid response from Metadata Service — " + url, e);
        }

        throw new ObjectNotFoundException("Object not found with key '" + key + "' in bucket " + bucketId);
    }

    public List<ChunkLocation> getChunkLocations(UUID objectId) {
        String url = metadataServiceUrl + "/internal/v1/objects/" + objectId + "/chunk-locations";
        log.info("Fetching chunk locations — url={}", url);

        String json = getRaw(url);
        try {
            List<ChunkLocation> locations = objectMapper.readValue(json, new TypeReference<List<ChunkLocation>>() {});
            return locations != null ? locations : List.of();
        } catch (JsonProcessingException e) {
            throw new MetadataUnavailableException("Invalid response from Metadata Service — " + url, e);
        }
    }

    private <T> T get(String url, Class<T> type) {
        String json = getRaw(url);
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw new ObjectNotFoundException("Empty response from Metadata Service — " + url);
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new MetadataUnavailableException("Invalid response from Metadata Service — " + url, e);
        }
    }

    private String getRaw(String url) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (response.getBody() == null) {
                throw new ObjectNotFoundException("Empty response from Metadata Service — " + url);
            }
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ObjectNotFoundException("Resource not found in Metadata Service — " + url);
        } catch (RestClientException e) {
            throw new MetadataUnavailableException("Metadata Service unreachable — " + url + " — " + e.getMessage(), e);
        }
    }
}
