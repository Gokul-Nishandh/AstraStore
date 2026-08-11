package com.astrastore.sdk;

import com.astrastore.sdk.exception.AstraAuthException;
import com.astrastore.sdk.model.BucketDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AstraClientTest {

    private AstraConfig config;

    @BeforeEach
    void setUp() {
        config = AstraConfig.builder()
                .baseUrl("http://localhost:8080")
                .apiKey("test-api-key-12345")
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    @DisplayName("Verify AstraConfig Builder properties")
    void testConfigBuilder() {
        assertEquals("http://localhost:8080", config.getBaseUrl());
        assertEquals("test-api-key-12345", config.getApiKey());
        assertTrue(config.hasApiKey());
        assertFalse(config.hasUserCredentials());
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
    }

    @Test
    @DisplayName("Verify AstraClient Builder instantiation")
    void testClientInstantiation() {
        AstraClient client = AstraClient.builder()
                .baseUrl("http://localhost:8080")
                .apiKey("test-api-key-12345")
                .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Verify BucketDto instantiation")
    void testBucketDto() {
        UUID bucketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        BucketDto bucket = new BucketDto(bucketId, "test-bucket", ownerId, null);

        assertEquals(bucketId, bucket.getId());
        assertEquals("test-bucket", bucket.getName());
        assertEquals(ownerId, bucket.getOwnerId());
    }
}
