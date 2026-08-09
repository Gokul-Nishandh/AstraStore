package com.astrastore.download;

import com.astrastore.download.client.ChunkLocation;
import com.astrastore.download.client.ObjectMetadata;
import com.astrastore.download.fetch.ChunkFetcher;
import com.astrastore.download.verify.ChecksumVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DownloadServiceIntegrationTest {

    private static final String OBJECT_KEY = "q3.pdf";

    private static final UUID MAIN_OBJECT_ID = UUID.randomUUID();
    private static final UUID FALLBACK_OBJECT_ID = UUID.randomUUID();
    private static final UUID CORRUPT_OBJECT_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_OBJECT_ID = UUID.randomUUID();
    private static final UUID BUCKET_ID = UUID.randomUUID();

    private static final byte[] MAIN_CHUNK_0 = filled(100_000, (byte) 0x41);
    private static final byte[] MAIN_CHUNK_1 = filled(200_000, (byte) 0x42);
    private static final byte[] MAIN_BYTES = concat(MAIN_CHUNK_0, MAIN_CHUNK_1);

    private static final byte[] FALLBACK_BYTES = filled(50_000, (byte) 0x43);
    private static final byte[] CORRUPT_INTENDED = filled(75_000, (byte) 0x44);
    private static final byte[] CORRUPT_SERVED = filled(75_000, (byte) 0x99);

    private static final ChecksumVerifier VERIFIER = new ChecksumVerifier();

    private static final String MAIN_CHECKSUM = VERIFIER.sha256(MAIN_BYTES);
    private static final String FALLBACK_CHECKSUM = VERIFIER.sha256(FALLBACK_BYTES);
    private static final String CORRUPT_CHECKSUM = VERIFIER.sha256(CORRUPT_INTENDED);

    private static final FakeStorageNode PRIMARY = new FakeStorageNode();
    private static final FakeStorageNode REPLICA = new FakeStorageNode();
    private static final FakeStorageNode CORRUPT_NODE = new FakeStorageNode();

    private static final FakeMetadataServer METADATA = new FakeMetadataServer();

    static {
        PRIMARY.start();
        REPLICA.start();
        CORRUPT_NODE.start();
        METADATA.start();
    }

    static {
        String mainChunk0 = ChunkFetcher.chunkId(MAIN_OBJECT_ID, 0);
        String mainChunk1 = ChunkFetcher.chunkId(MAIN_OBJECT_ID, 1);
        String fallbackChunk = ChunkFetcher.chunkId(FALLBACK_OBJECT_ID, 0);
        String corruptChunk = ChunkFetcher.chunkId(CORRUPT_OBJECT_ID, 0);

        PRIMARY.serve(mainChunk0, MAIN_CHUNK_0);
        PRIMARY.serve(mainChunk1, MAIN_CHUNK_1);
        PRIMARY.fail(fallbackChunk);
        REPLICA.serve(fallbackChunk, FALLBACK_BYTES);
        CORRUPT_NODE.serve(corruptChunk, CORRUPT_SERVED);

        METADATA.registerObject(MAIN_OBJECT_ID, new ObjectMetadata(
                MAIN_OBJECT_ID, BUCKET_ID, OBJECT_KEY, (long) MAIN_BYTES.length, MAIN_CHECKSUM,
                "application/pdf", "ACTIVE", "2026-07-26T10:20:00Z", 2L, 2L));
        METADATA.registerLocations(MAIN_OBJECT_ID, List.of(
                new ChunkLocation(UUID.randomUUID(), MAIN_OBJECT_ID, 0, PRIMARY.baseUrl(), null,
                        "PENDING", VERIFIER.sha256(MAIN_CHUNK_0)),
                new ChunkLocation(UUID.randomUUID(), MAIN_OBJECT_ID, 1, PRIMARY.baseUrl(), null,
                        "PENDING", VERIFIER.sha256(MAIN_CHUNK_1))));

        METADATA.registerObject(FALLBACK_OBJECT_ID, new ObjectMetadata(
                FALLBACK_OBJECT_ID, BUCKET_ID, "fallback.bin", (long) FALLBACK_BYTES.length, FALLBACK_CHECKSUM,
                "application/octet-stream", "ACTIVE", "2026-07-26T10:21:00Z", 1L, 1L));
        METADATA.registerLocations(FALLBACK_OBJECT_ID, List.of(
                new ChunkLocation(UUID.randomUUID(), FALLBACK_OBJECT_ID, 0, PRIMARY.baseUrl(), REPLICA.baseUrl(),
                        "REPLICATED", VERIFIER.sha256(FALLBACK_BYTES))));

        METADATA.registerObject(CORRUPT_OBJECT_ID, new ObjectMetadata(
                CORRUPT_OBJECT_ID, BUCKET_ID, "corrupt.bin", (long) CORRUPT_INTENDED.length, CORRUPT_CHECKSUM,
                "application/octet-stream", "ACTIVE", "2026-07-26T10:22:00Z", 1L, 1L));
        METADATA.registerLocations(CORRUPT_OBJECT_ID, List.of(
                new ChunkLocation(UUID.randomUUID(), CORRUPT_OBJECT_ID, 0, CORRUPT_NODE.baseUrl(), null,
                        "PENDING", CORRUPT_CHECKSUM)));

        METADATA.registerBucket(BUCKET_ID, List.of(
                new ObjectMetadata(
                        MAIN_OBJECT_ID, BUCKET_ID, OBJECT_KEY, (long) MAIN_BYTES.length, MAIN_CHECKSUM,
                        "application/pdf", "ACTIVE", "2026-07-26T10:20:00Z", 2L, 2L)));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void metadataUrl(DynamicPropertyRegistry registry) {
        registry.add("services.metadata-url", () -> METADATA.baseUrl());
    }

    @Test
    void downloadById_streamsVerifiedObjectBytesWithHeaders() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/api/v1/objects/" + MAIN_OBJECT_ID, byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(MAIN_BYTES);
        assertThat(response.getHeaders().getFirst("Content-Type")).startsWith("application/pdf");
        assertThat(response.getHeaders().getFirst("X-Checksum-SHA256")).isEqualTo(MAIN_CHECKSUM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(MAIN_BYTES.length);
    }

    @Test
    void downloadByBucketAndKey_streamsVerifiedObjectBytes() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/api/v1/buckets/" + BUCKET_ID + "/objects/" + OBJECT_KEY, byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(MAIN_BYTES);
        assertThat(response.getHeaders().getFirst("X-Checksum-SHA256")).isEqualTo(MAIN_CHECKSUM);
    }

    @Test
    void head_returnsObjectMetadataWithoutBody() {
        HttpHeaders headers = restTemplate.headForHeaders("/api/v1/objects/" + MAIN_OBJECT_ID);

        assertThat(headers.getFirst("X-Checksum-SHA256")).isEqualTo(MAIN_CHECKSUM);
        assertThat(headers.getFirst("Content-Type")).startsWith("application/pdf");
        assertThat(headers.getContentLength()).isEqualTo(MAIN_BYTES.length);
    }

    @Test
    void download_fallsBackToReplicatedReplicaWhenPrimaryUnavailable() {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(
                "/api/v1/objects/" + FALLBACK_OBJECT_ID, byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(FALLBACK_BYTES);
        assertThat(response.getHeaders().getFirst("X-Checksum-SHA256")).isEqualTo(FALLBACK_CHECKSUM);
    }

    @Test
    void download_returns422WhenServedChunkFailsChecksumVerification() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/objects/" + CORRUPT_OBJECT_ID, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("UNPROCESSABLE_ENTITY");
    }

    @Test
    void download_returns404WhenObjectDoesNotExist() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/objects/" + UNKNOWN_OBJECT_ID, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("NOT_FOUND");
    }

    private static byte[] filled(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static final class FakeStorageNode {

        private final HttpServer server;
        private final ConcurrentMap<String, byte[]> chunks = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Boolean> failing = new ConcurrentHashMap<>();

        FakeStorageNode() {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/api/v1/chunks/", this::handleChunk);
                server.setExecutor(Executors.newCachedThreadPool());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start fake storage node", e);
            }
        }

        private void handleChunk(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String chunkId = path.substring(path.lastIndexOf('/') + 1);

            if (Boolean.TRUE.equals(failing.get(chunkId))) {
                send(exchange, 500, "node unavailable".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] data = chunks.get(chunkId);
            if (data == null) {
                send(exchange, 404, new byte[0]);
                return;
            }
            send(exchange, 200, data);
        }

        void start() {
            server.start();
        }

        void serve(String chunkId, byte[] data) {
            chunks.put(chunkId, data);
        }

        void fail(String chunkId) {
            failing.put(chunkId, Boolean.TRUE);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void send(HttpExchange exchange, int status, byte[] body) throws IOException {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static final class FakeMetadataServer {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final HttpServer server;
        private final Map<String, ObjectMetadata> objects = new ConcurrentHashMap<>();
        private final Map<String, List<ChunkLocation>> locations = new ConcurrentHashMap<>();
        private final Map<String, List<ObjectMetadata>> buckets = new ConcurrentHashMap<>();

        FakeMetadataServer() {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/api/v1/objects/", this::handleObject);
                server.createContext("/internal/v1/objects/", this::handleChunkLocations);
                server.createContext("/api/v1/buckets/", this::handleBucketListing);
                server.setExecutor(Executors.newCachedThreadPool());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start fake metadata service", e);
            }
        }

        private void handleObject(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/api/v1/objects/".length());
            ObjectMetadata metadata = objects.get(id);
            if (metadata == null) {
                send(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                return;
            }
            send(exchange, 200, MAPPER.writeValueAsString(metadata));
        }

        private void handleChunkLocations(HttpExchange exchange) throws IOException {
            String id = exchange.getRequestURI().getPath().substring("/internal/v1/objects/".length());
            String objectId = id.split("/")[0];
            List<ChunkLocation> chunkLocations = locations.get(objectId);
            if (chunkLocations == null) {
                send(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                return;
            }
            send(exchange, 200, MAPPER.writeValueAsString(chunkLocations));
        }

        private void handleBucketListing(HttpExchange exchange) throws IOException {
            String[] segments = exchange.getRequestURI().getPath().split("/");
            String bucketId = segments.length > 4 ? segments[4] : "";
            List<ObjectMetadata> objectsInBucket = buckets.getOrDefault(bucketId, List.of());
            ObjectNode page = MAPPER.createObjectNode();
            page.set("content", MAPPER.valueToTree(objectsInBucket));
            send(exchange, 200, MAPPER.writeValueAsString(page));
        }

        void registerObject(UUID objectId, ObjectMetadata metadata) {
            objects.put(objectId.toString(), metadata);
        }

        void registerLocations(UUID objectId, List<ChunkLocation> chunkLocations) {
            locations.put(objectId.toString(), chunkLocations);
        }

        void registerBucket(UUID bucketId, List<ObjectMetadata> objectsInBucket) {
            buckets.put(bucketId.toString(), objectsInBucket);
        }

        void start() {
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void send(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
