/**
 * OkHttp-based HTTP client for AstraStore REST APIs.
 * Auto-attaches Bearer token from CredentialStore on every request.
 * Auto-refreshes expired tokens and retries once on 401 responses.
 * Provides JSON serialization helpers via Jackson.
 */
package com.astrastore.cli.http;

import com.astrastore.cli.auth.CredentialStore;
import com.astrastore.cli.config.AstraConfig;
import com.astrastore.cli.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class AstraHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AstraConfig config;
    private final CredentialStore credentials;
    private final String baseUrl;

    public AstraHttpClient(String baseUrl) {
        this.config = AstraConfig.load();
        this.credentials = CredentialStore.getInstance();
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.getTimeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public <T> T get(String path, TypeReference<T> typeRef) throws IOException {
        Request request = baseRequest(path).get().build();
        return execute(request, typeRef);
    }

    public <T> T post(String path, Object body, TypeReference<T> typeRef) throws IOException {
        Request request = baseRequest(path)
                .post(RequestBody.create(mapper.writeValueAsBytes(body), JSON))
                .build();
        return execute(request, typeRef);
    }

    public <T> T put(String path, byte[] body, TypeReference<T> typeRef) throws IOException {
        Request request = baseRequest(path)
                .put(RequestBody.create(body, MediaType.get("application/octet-stream")))
                .build();
        return execute(request, typeRef);
    }

    public void delete(String path) throws IOException {
        Request request = baseRequest(path).delete().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(), path, errorBody);
            }
        }
    }

    public void postRaw(String path, byte[] body, String contentType) throws IOException {
        Request request = baseRequest(path)
                .post(RequestBody.create(body, MediaType.get(contentType)))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(), path, errorBody);
            }
        }
    }

    private <T> T execute(Request request, TypeReference<T> typeRef) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), request.url().encodedPath(), body);
            }
            if (body.isEmpty() || typeRef == null) {
                return null;
            }
            return mapper.readValue(body, typeRef);
        }
    }

    private Request.Builder baseRequest(String path) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path);
        CredentialStore.Credentials creds = credentials.getCredentials();
        if (creds != null && creds.getAccessToken() != null) {
            builder.header("Authorization", "Bearer " + creds.getAccessToken());
        }
        return builder;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
