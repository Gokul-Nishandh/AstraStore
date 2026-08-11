package com.astrastore.sdk.auth;

import com.astrastore.sdk.AstraConfig;
import com.astrastore.sdk.exception.AstraAuthException;
import com.astrastore.sdk.model.AuthResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuthManager {
    private final AstraConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private String refreshToken;

    public AuthManager(AstraConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        if (config.hasApiKey()) {
            this.accessToken = config.getApiKey();
        }
    }

    public synchronized String getValidToken() {
        if (config.hasApiKey()) {
            return config.getApiKey();
        }
        if (accessToken != null) {
            return accessToken;
        }
        if (refreshToken != null) {
            try {
                refresh();
                return accessToken;
            } catch (Exception ignored) {
                // fall through to login
            }
        }
        if (config.hasUserCredentials()) {
            login(config.getEmail(), config.getPassword());
            return accessToken;
        }
        return null;
    }

    public synchronized AuthResponseDto login(String email, String password) {
        try {
            String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(config.getTimeout())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthResponseDto authResponse = objectMapper.readValue(response.body(), AuthResponseDto.class);
                this.accessToken = authResponse.getToken();
                this.refreshToken = authResponse.getRefreshToken();
                return authResponse;
            } else {
                throw new AstraAuthException("Authentication failed with HTTP " + response.statusCode() + ": " + response.body(), response.statusCode());
            }
        } catch (AstraAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AstraAuthException("Failed to login to AstraStore: " + e.getMessage(), 401);
        }
    }

    public synchronized AuthResponseDto refresh() {
        if (refreshToken == null) {
            throw new AstraAuthException("No refresh token available to rotate", 401);
        }
        try {
            String jsonBody = String.format("{\"refreshToken\":\"%s\"}", refreshToken);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(config.getTimeout())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthResponseDto authResponse = objectMapper.readValue(response.body(), AuthResponseDto.class);
                this.accessToken = authResponse.getToken();
                this.refreshToken = authResponse.getRefreshToken();
                return authResponse;
            } else {
                throw new AstraAuthException("Token refresh failed: " + response.body(), response.statusCode());
            }
        } catch (Exception e) {
            throw new AstraAuthException("Failed to refresh token: " + e.getMessage(), 401);
        }
    }

    public synchronized void setAccessToken(String token) {
        this.accessToken = token;
    }
}
