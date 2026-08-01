package com.astrastore.replication.metadata;

import com.astrastore.replication.db.MockChunkDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock implementation of MetadataClient for development/testing.
 * Updates MockChunkDatabase and logs replica locations.
 * Replace with real REST client when Metadata Service is available.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MockMetadataClient implements MetadataClient {

    private final MockChunkDatabase mockChunkDatabase;

    @Override
    public void addReplicaLocation(String chunkId, String nodeIp) {
        mockChunkDatabase.updateReplicaCount(chunkId, nodeIp);
        log.info("Mock Metadata Update: Chunk {} replicated to {}", chunkId, nodeIp);
    }
}
