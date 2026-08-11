package com.astrastore.sdk;

import java.time.Duration;

public class AstraConfig {
    private final String baseUrl;
    private final String apiKey;
    private final String email;
    private final String password;
    private final Duration timeout;
    private final int maxRetries;

    private AstraConfig(Builder builder) {
        this.baseUrl = sanitizeUrl(builder.baseUrl);
        this.apiKey = builder.apiKey;
        this.email = builder.email;
        this.password = builder.password;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(30);
        this.maxRetries = builder.maxRetries > 0 ? builder.maxRetries : 3;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasUserCredentials() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    private static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = "http://localhost:8080";
        private String apiKey;
        private String email;
        private String password;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 3;

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

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public AstraConfig build() {
            return new AstraConfig(this);
        }
    }
}
