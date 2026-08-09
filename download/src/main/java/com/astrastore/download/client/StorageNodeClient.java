package com.astrastore.download.client;

import com.astrastore.download.exception.ChunkUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class StorageNodeClient {

    private final RestTemplate restTemplate;
    private final int fallbackTimeoutMs;

    public StorageNodeClient(@Value("${download.fallback-timeout-ms:3000}") int fallbackTimeoutMs) {
        this.fallbackTimeoutMs = fallbackTimeoutMs;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(fallbackTimeoutMs);
        factory.setReadTimeout(fallbackTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public byte[] readChunk(String nodeAddress, String chunkId) {
        String url = normalize(nodeAddress) + "/api/v1/chunks/" + chunkId;
        log.info("Reading chunk — url={}", url);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new ChunkUnavailableException(
                        "Storage node returned " + response.getStatusCode() + " for chunk " + chunkId + " at " + url);
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new ChunkUnavailableException(
                    "Failed to read chunk " + chunkId + " from " + nodeAddress + " — " + e.getMessage(), e);
        }
    }

    private String normalize(String nodeAddress) {
        String trimmed = nodeAddress.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.regionMatches(true, 0, "http://", 0, 7) || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        return "http://" + trimmed;
    }
}
