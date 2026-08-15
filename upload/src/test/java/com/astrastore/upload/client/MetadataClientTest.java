package com.astrastore.upload.client;

import com.astrastore.shared.security.InternalServiceToken;
import com.astrastore.upload.support.FakeHttpResponder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataClientTest {

    /* A real token, so the interceptor under test actually attaches a header
       rather than exercising the unauthenticated development path. */
    private static final InternalServiceToken TEST_TOKEN =
            InternalServiceToken.resolve("test-internal-token");

    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();
    private static final FakeHttpResponder SERVER = startServer();

    private static FakeHttpResponder startServer() {
        try {
            FakeHttpResponder server = new FakeHttpResponder();
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop();
    }

    @BeforeEach
    void resetServer() {
        SERVER.reset();
    }

    @Test
    void createObjectRecord_postsAndParsesResponse() {
        SERVER.respond("POST", "/internal/v1/objects", 201, """
                {
                  "id": "%s",
                  "bucketId": "%s",
                  "key": "q3.pdf",
                  "sizeBytes": 100,
                  "checksum": "hash",
                  "contentType": "application/pdf",
                  "status": "ACTIVE",
                  "createdAt": "2026-07-26T10:20:00Z"
                }
                """.formatted(OBJECT_ID, BUCKET_ID));

        MetadataClient client = new MetadataClient(SERVER.baseUrl(), TEST_TOKEN);
        MetadataClient.CreateObjectRecordRequest request = MetadataClient.CreateObjectRecordRequest.builder()
                .id(OBJECT_ID)
                .bucketId(BUCKET_ID)
                .key("q3.pdf")
                .sizeBytes(100L)
                .checksum("hash")
                .contentType("application/pdf")
                .build();

        MetadataClient.CreatedObjectRecordResponse response = client.createObjectRecord(request);

        assertThat(response.getId()).isEqualTo(OBJECT_ID);
        assertThat(response.getBucketId()).isEqualTo(BUCKET_ID);
        assertThat(response.getKey()).isEqualTo("q3.pdf");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(SERVER.requestCount()).isEqualTo(1);
    }

    @Test
    void recordChunkLocations_postsAndParsesResponse() {
        SERVER.respond("POST", "/internal/v1/objects/" + OBJECT_ID + "/chunk-locations", 201, """
                {
                  "objectId": "%s",
                  "chunksRecorded": 2
                }
                """.formatted(OBJECT_ID));

        MetadataClient client = new MetadataClient(SERVER.baseUrl(), TEST_TOKEN);
        List<MetadataClient.ChunkLocationItem> chunks = List.of(
                MetadataClient.ChunkLocationItem.builder()
                        .chunkIndex(0).nodeId("storage-node-1").checksum("a").build(),
                MetadataClient.ChunkLocationItem.builder()
                        .chunkIndex(1).nodeId("storage-node-2").checksum("b").build());

        MetadataClient.RecordChunkLocationsResponse response =
                client.recordChunkLocations(OBJECT_ID, chunks);

        assertThat(response.getObjectId()).isEqualTo(OBJECT_ID);
        assertThat(response.getChunksRecorded()).isEqualTo(2);
        assertThat(SERVER.requestCount()).isEqualTo(1);
    }
}
