package com.astrastore.upload.client;

import com.astrastore.shared.manifest.ChunkManifest;
import com.astrastore.upload.exception.ChunkWriteException;
import com.astrastore.upload.support.FakeStorageNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpStorageStreamClientTest {

    private static final FakeStorageNode NODE = startNode();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static FakeStorageNode startNode() {
        try {
            FakeStorageNode node = new FakeStorageNode();
            node.start();
            return node;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopNode() {
        NODE.stop();
    }

    @BeforeEach
    void resetNode() {
        NODE.reset();
    }

    @Test
    void writeAndFinalize_returnsParsedManifest() throws IOException {
        HttpStorageStreamClient client = new HttpStorageStreamClient(objectMapper);
        byte[] data = "chunk-bytes".getBytes(StandardCharsets.UTF_8);

        client.openStream(NODE.baseUrl(), "chunk-1");
        client.write(data, 0, data.length);
        ChunkManifest manifest = client.finalizeStream();

        assertThat(manifest.chunkId()).isEqualTo("chunk-1");
        assertThat(manifest.checksum()).isEqualTo(sha256(data));
        assertThat(manifest.sizeBytes()).isEqualTo((long) data.length);
        assertThat(manifest.nodeIp()).isEqualTo(NODE.baseUrl());
    }

    @Test
    void finalize_throwsChunkWriteOnNon201Response() throws IOException {
        NODE.failRequests();

        HttpStorageStreamClient client = new HttpStorageStreamClient(objectMapper);
        client.openStream(NODE.baseUrl(), "chunk-fail");
        client.write(new byte[]{1}, 0, 1);

        assertThatThrownBy(client::finalizeStream).isInstanceOf(ChunkWriteException.class);
    }

    @Test
    void openStream_throwsChunkWriteOnConnectionFailure() throws IOException {
        FakeStorageNode dead = new FakeStorageNode();
        dead.start();
        String url = dead.baseUrl();
        dead.stop();

        HttpStorageStreamClient client = new HttpStorageStreamClient(objectMapper);

        assertThatThrownBy(() -> client.openStream(url, "chunk-dead"))
                .isInstanceOf(ChunkWriteException.class);
    }

    private String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
