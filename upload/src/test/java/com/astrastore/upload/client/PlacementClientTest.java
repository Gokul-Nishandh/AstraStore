package com.astrastore.upload.client;

import com.astrastore.upload.client.PlacementClient.NodeAssignment;
import com.astrastore.upload.client.PlacementClient.PlacementRequest;
import com.astrastore.upload.client.PlacementClient.PlacementResponse;
import com.astrastore.upload.support.FakeHttpResponder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementClientTest {

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
    void requestPlacement_postsAndParsesAssignments() {
        SERVER.respond("POST", "/internal/v1/placement/request", 200, """
                {
                  "assignments": [
                    {
                      "chunkIndex": 0,
                      "primaryNodeId": "storage-node-1",
                      "replicaNodeIds": ["storage-node-2"]
                    },
                    {
                      "chunkIndex": 1,
                      "primaryNodeId": "storage-node-2",
                      "replicaNodeIds": ["storage-node-3"]
                    }
                  ]
                }
                """);

        PlacementClient client = new PlacementClient(SERVER.baseUrl());
        PlacementRequest request = PlacementRequest.builder()
                .chunkCount(2)
                .replicationFactor(2)
                .build();

        PlacementResponse response = client.requestPlacement(request);

        assertThat(response.getAssignments()).hasSize(2);
        NodeAssignment first = response.getAssignments().get(0);
        assertThat(first.getChunkIndex()).isZero();
        assertThat(first.getPrimaryNodeId()).isEqualTo("storage-node-1");
        assertThat(first.getReplicaNodeIds()).containsExactly("storage-node-2");
        assertThat(SERVER.requestCount()).isEqualTo(1);
    }

    @Test
    void requestPlacement_handlesEmptyAssignments() {
        SERVER.respond("POST", "/internal/v1/placement/request", 200, """
                {"assignments": []}
                """);

        PlacementClient client = new PlacementClient(SERVER.baseUrl());
        PlacementResponse response = client.requestPlacement(
                PlacementRequest.builder().chunkCount(0).replicationFactor(2).build());

        assertThat(response.getAssignments()).isEmpty();
    }
}
