package com.astrastore.upload.support;

import com.astrastore.upload.chunking.ChecksumCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake storage node that accepts chunk writes over HTTP and returns the
 * computed SHA-256 checksum, mirroring the real storage node agent API.
 */
public class FakeStorageNode implements AutoCloseable {

    private final HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<String> receivedChunkIds = new ArrayList<>();

    private volatile boolean failRequests;
    private volatile String forcedChecksum;

    public FakeStorageNode() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/chunks/", this::handleChunk);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requestCount.get();
    }

    public List<String> receivedChunkIds() {
        return List.copyOf(receivedChunkIds);
    }

    public void reset() {
        requestCount.set(0);
        receivedChunkIds.clear();
        failRequests = false;
        forcedChecksum = null;
    }

    public void failRequests() {
        failRequests = true;
    }

    public void forceChecksum(String checksum) {
        forcedChecksum = checksum;
    }

    private void handleChunk(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String chunkId = path.substring(path.lastIndexOf('/') + 1);
        receivedChunkIds.add(chunkId);

        if (failRequests) {
            send(exchange, 500, "{\"error\":\"storage failure\"}");
            return;
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        String checksum = forcedChecksum != null
                ? forcedChecksum
                : checksumCalculator.calculateSha256(body);

        String json = objectMapper.writeValueAsString(Map.of(
                "chunkId", chunkId,
                "checksum", checksum,
                "sizeBytes", (long) body.length));
        send(exchange, 201, json);
    }

    private void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
