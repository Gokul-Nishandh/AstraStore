package com.astrastore.sdk;

import com.astrastore.sdk.auth.AuthManager;
import com.astrastore.sdk.exception.*;
import com.astrastore.sdk.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class AstraClient implements AutoCloseable {
    private final AstraConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AuthManager authManager;

    private AstraClient(AstraConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.authManager = new AuthManager(config, httpClient, objectMapper);
    }

    public static AstraClient create(String baseUrl) {
        return builder().baseUrl(baseUrl).build();
    }

    public static AstraClient create(String baseUrl, String apiKey) {
        return builder().baseUrl(baseUrl).apiKey(apiKey).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public AuthResponseDto login(String email, String password) {
        return authManager.login(email, password);
    }

    // =========================================
    // Bucket Operations
    // =========================================

    public BucketDto createBucket(String name) {
        String jsonBody = String.format("{\"name\":\"%s\"}", name);
        HttpRequest request = prepareRequest("/api/v1/buckets")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = executeRequest(request);
        return parseResponse(response, BucketDto.class);
    }

    public BucketDto getBucket(UUID bucketId) {
        HttpRequest request = prepareRequest("/api/v1/buckets/" + bucketId)
                .GET()
                .build();

        HttpResponse<String> response = executeRequest(request);
        return parseResponse(response, BucketDto.class);
    }

    public List<BucketDto> listBuckets() {
        HttpRequest request = prepareRequest("/api/v1/buckets")
                .GET()
                .build();

        HttpResponse<String> response = executeRequest(request);
        try {
            // Note: API returns spring page or array
            if (response.body().contains("\"content\":")) {
                var node = objectMapper.readTree(response.body());
                return objectMapper.convertValue(node.get("content"), new TypeReference<List<BucketDto>>() {});
            }
            return objectMapper.readValue(response.body(), new TypeReference<List<BucketDto>>() {});
        } catch (IOException e) {
            throw new AstraException("Failed to parse bucket list: " + e.getMessage(), e);
        }
    }

    public void deleteBucket(UUID bucketId) {
        HttpRequest request = prepareRequest("/api/v1/buckets/" + bucketId)
                .DELETE()
                .build();

        HttpResponse<String> response = executeRequest(request);
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            handleErrorResponse(response.statusCode(), response.body());
        }
    }

    // =========================================
    // Object Streaming Upload & Download
    // =========================================

    public UploadResultDto uploadObject(UUID bucketId, String key, File file, String contentType) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return uploadObject(bucketId, key, inputStream, file.length(), contentType);
        }
    }

    public UploadResultDto uploadObject(UUID bucketId, String key, InputStream inputStream, long length, String contentType) {
        String path = String.format("/api/v1/buckets/%s/objects/%s", bucketId, sanitizeKey(key));
        String cType = contentType != null ? contentType : "application/octet-stream";

        HttpRequest request = prepareRequest(path)
                .header("Content-Type", cType)
                .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> inputStream))
                .build();

        HttpResponse<String> response = executeRequest(request);
        return parseResponse(response, UploadResultDto.class);
    }

    public void downloadObject(UUID bucketId, String key, OutputStream outputStream) throws IOException {
        String path = String.format("/api/v1/buckets/%s/objects/%s", bucketId, sanitizeKey(key));
        HttpRequest request = prepareRequest(path)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                try (InputStream in = response.body()) {
                    in.transferTo(outputStream);
                }
            } else {
                String errorBody = new String(response.body().readAllBytes());
                handleErrorResponse(response.statusCode(), errorBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AstraException("Download interrupted", e);
        }
    }

    public void downloadObject(UUID objectId, OutputStream outputStream) throws IOException {
        String path = "/api/v1/objects/" + objectId;
        HttpRequest request = prepareRequest(path)
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                try (InputStream in = response.body()) {
                    in.transferTo(outputStream);
                }
            } else {
                String errorBody = new String(response.body().readAllBytes());
                handleErrorResponse(response.statusCode(), errorBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AstraException("Download interrupted", e);
        }
    }

    public ObjectDto getObjectMetadata(UUID objectId) {
        HttpRequest request = prepareRequest("/api/v1/objects/" + objectId)
                .GET()
                .build();

        HttpResponse<String> response = executeRequest(request);
        return parseResponse(response, ObjectDto.class);
    }

    public void deleteObject(UUID objectId) {
        HttpRequest request = prepareRequest("/api/v1/objects/" + objectId)
                .DELETE()
                .build();

        HttpResponse<String> response = executeRequest(request);
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            handleErrorResponse(response.statusCode(), response.body());
        }
    }

    // =========================================
    // API Keys Operations
    // =========================================

    public ApiKeyDto createApiKey(String name) {
        String jsonBody = String.format("{\"name\":\"%s\"}", name);
        HttpRequest request = prepareRequest("/api/auth/keys")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = executeRequest(request);
        return parseResponse(response, ApiKeyDto.class);
    }

    public List<ApiKeyDto> listApiKeys() {
        HttpRequest request = prepareRequest("/api/auth/keys")
                .GET()
                .build();

        HttpResponse<String> response = executeRequest(request);
        try {
            return objectMapper.readValue(response.body(), new TypeReference<List<ApiKeyDto>>() {});
        } catch (IOException e) {
            throw new AstraException("Failed to parse API key list: " + e.getMessage(), e);
        }
    }

    public void revokeApiKey(Long keyId) {
        HttpRequest request = prepareRequest("/api/auth/keys/" + keyId)
                .DELETE()
                .build();

        HttpResponse<String> response = executeRequest(request);
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            handleErrorResponse(response.statusCode(), response.body());
        }
    }

    // =========================================
    // Helpers
    // =========================================

    private HttpRequest.Builder prepareRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + path))
                .timeout(config.getTimeout());

        String token = authManager.getValidToken();
        if (token != null && !token.isBlank()) {
            if (config.hasApiKey()) {
                builder.header("X-API-Key", token);
                builder.header("Authorization", "Bearer " + token);
            } else {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        return builder;
    }

    private HttpResponse<String> executeRequest(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AstraException("Request interrupted: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new AstraException("Network error while connecting to AstraStore: " + e.getMessage(), e);
        }
    }

    private <T> T parseResponse(HttpResponse<String> response, Class<T> clazz) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                return objectMapper.readValue(response.body(), clazz);
            } catch (IOException e) {
                throw new AstraException("Failed to parse JSON response: " + e.getMessage(), e);
            }
        }
        handleErrorResponse(response.statusCode(), response.body());
        return null;
    }

    private void handleErrorResponse(int statusCode, String body) {
        String msg = "AstraStore request failed [HTTP " + statusCode + "]: " + body;
        switch (statusCode) {
            case 401:
            case 403:
                throw new AstraAuthException(msg, statusCode);
            case 404:
                throw new AstraNotFoundException(msg);
            case 400:
            case 409:
                throw new AstraValidationException(msg, statusCode);
            default:
                throw new AstraServerException(msg, statusCode);
        }
    }

    private static String sanitizeKey(String key) {
        if (key == null) return "";
        return key.startsWith("/") ? key.substring(1) : key;
    }

    @Override
    public void close() {
        // No-op for HttpClient in Java 21
    }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private String apiKey;
        private String email;
        private String password;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credentials(String email, String password) {
            this.email = email;
            this.password = password;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public AstraClient build() {
            AstraConfig config = AstraConfig.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .credentials(email, password)
                    .timeout(timeout)
                    .build();
            return new AstraClient(config);
        }
    }
}
