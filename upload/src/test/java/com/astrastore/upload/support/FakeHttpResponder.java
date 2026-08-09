package com.astrastore.upload.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake HTTP server that returns a configurable JSON body for a given
 * HTTP method + path prefix, mirroring an internal service endpoint.
 */
public class FakeHttpResponder implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    public FakeHttpResponder() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
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

    public void respond(String method, String pathPrefix, int status, String json) {
        responses.put(method + " " + pathPrefix, new Response(status, json));
    }

    public void reset() {
        requestCount.set(0);
        responses.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        Response response = responses.entrySet().stream()
                .filter(entry -> {
                    String[] parts = entry.getKey().split(" ", 2);
                    return parts[0].equals(method) && path.startsWith(parts[1]);
                })
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(new Response(404, "{\"code\":\"NOT_FOUND\"}"));

        byte[] body = response.json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private record Response(int status, String json) {
    }
}
