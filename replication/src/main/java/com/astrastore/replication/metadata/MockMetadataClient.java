package com.astrastore.replication.metadata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock implementation of MetadataClient for development/testing.
 * Logs replica locations instead of persisting them.
 * Replace with real REST client when Metadata Service is available.
 */
@Component
@Slf4j
public class MockMetadataClient implements MetadataClient {

    @Override
    public void addReplicaLocation(String chunkId, String nodeIp) {
        log.info("Mock Metadata Update: Chunk {} replicated to {}", chunkId, nodeIp);
    }
}
