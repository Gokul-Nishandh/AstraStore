package com.astrastore.upload.placement;

import com.astrastore.shared.strategy.PlacementStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Remote implementation of PlacementStrategy for the upload service.
 * Makes HTTP calls to the centralized placement service to determine target nodes.
 */
@Service
@Slf4j
public class RemotePlacementStrategy implements PlacementStrategy {

    private final RestTemplate restTemplate;
    private final String placementServiceUrl;

    public RemotePlacementStrategy(RestTemplateBuilder restTemplateBuilder,
                                   @Value("${astrastore.placement.url}") String placementServiceUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.placementServiceUrl = placementServiceUrl;
    }

    @Override
    public String getNextTargetNode() {
        String url = placementServiceUrl + "/api/v1/placement/next";
        log.debug("Calling placement service for next target node: {}", url);
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get next target node from placement service", e);
        }
        return null;
    }

    @Override
    public List<String> getNextTargetNodes(int count, String excludeNode) {
        String url = placementServiceUrl + "/api/v1/placement/next/multiple?count=" + count;
        if (excludeNode != null) {
            url += "&excludeNode=" + excludeNode;
        }
        
        log.debug("Calling placement service for multiple target nodes: {}", url);
        
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<String>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get multiple target nodes from placement service", e);
        }
        return List.of();
    }
}
