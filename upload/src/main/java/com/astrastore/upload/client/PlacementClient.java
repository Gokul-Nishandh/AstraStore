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

@Component
@Slf4j
public class PlacementClient {

    private final RestTemplate restTemplate;
    private final String placementServiceUrl;

    public PlacementClient(
            @Value("${services.placement-url:http://localhost:8085}") String placementServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.placementServiceUrl = placementServiceUrl;
    }

    public PlacementResponse requestPlacement(PlacementRequest request) {
        String url = placementServiceUrl + "/internal/v1/placement/request";
        log.info("Requesting node placement — url={}, chunkCount={}", url, request.getChunkCount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PlacementRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PlacementResponse> response = restTemplate.postForEntity(
                url, entity, PlacementResponse.class);

        return response.getBody();
    }

    @Getter
    @Builder
    public static class PlacementRequest {
        private final int chunkCount;
        private final int replicationFactor;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlacementResponse {
        private List<NodeAssignment> assignments;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NodeAssignment {
        private int chunkIndex;
        private String primaryNodeId;
        private List<String> replicaNodeIds;
    }
}
