package com.astrastore.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Everything the edge needs to know, driven by environment variables.
 *
 * <p>No value here is a secret; the signing key is read separately from
 * {@code jwt.secret} so it never lands in a properties dump.
 */
@ConfigurationProperties(prefix = "astrastore.gateway")
public class GatewayProperties {

    /**
     * Paths reachable without a token. Everything not listed requires either
     * a Bearer access token or a valid API key — the allowlist is the whole
     * policy, so adding a route does not silently make it public.
     */
    private List<String> publicPaths = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            // The status page renders before the user is known, and this
            // discloses nothing beyond up/down for a service the user can
            // already see is up by reaching this gateway at all.
            "/health/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    );

    /**
     * Origins allowed to call the API from a browser.
     *
     * <p>Deliberately not {@code *}: the dashboard sends a bearer token on
     * every request, and a wildcard origin cannot carry credentials. The
     * default covers the dashboard container and the Vite dev server, both
     * of which serve on 5173.
     */
    private List<String> allowedOrigins = List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

    /** Header carrying a programmatic credential. Matches the auth service. */
    private String apiKeyHeader = "X-API-Key";

    /** Base URL of the auth service, used to resolve API keys. */
    private String authServiceUrl = "http://auth:8081";

    /** Auth-service endpoint that exchanges an API key for an identity. */
    private String apiKeyVerifyPath = "/internal/v1/auth/api-keys/verify";

    /** How long a successfully validated API key stays cached in Redis. */
    private Duration apiKeyCacheTtl = Duration.ofMinutes(5);

    /**
     * How long a rejected API key is remembered. Short, but long enough that
     * a flood of bad keys cannot be turned into a flood of auth-service calls.
     */
    private Duration apiKeyNegativeCacheTtl = Duration.ofSeconds(30);

    /** Timeout for the API-key resolution call. */
    private Duration apiKeyResolveTimeout = Duration.ofSeconds(3);

    /**
     * Lifetime of the access token the gateway mints for an API-key caller so
     * downstream services — which verify tokens themselves — accept the hop.
     * Deliberately tiny: it is a transport detail, not a session.
     */
    private Duration downstreamTokenTtl = Duration.ofMinutes(2);

    /** Largest body accepted on a non-streaming route. */
    private long maxRequestBytes = 1_048_576L;

    /** Largest body accepted on an object-upload route (5 GiB). */
    private long maxStreamingRequestBytes = 5_368_709_120L;

    /** Routes whose bodies stream through untouched and may be very large. */
    private List<String> streamingPaths = List.of("/api/v1/buckets/*/objects/**");

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public String getAuthServiceUrl() {
        return authServiceUrl;
    }

    public void setAuthServiceUrl(String authServiceUrl) {
        this.authServiceUrl = authServiceUrl;
    }

    public String getApiKeyVerifyPath() {
        return apiKeyVerifyPath;
    }

    public void setApiKeyVerifyPath(String apiKeyVerifyPath) {
        this.apiKeyVerifyPath = apiKeyVerifyPath;
    }

    public Duration getApiKeyCacheTtl() {
        return apiKeyCacheTtl;
    }

    public void setApiKeyCacheTtl(Duration apiKeyCacheTtl) {
        this.apiKeyCacheTtl = apiKeyCacheTtl;
    }

    public Duration getApiKeyNegativeCacheTtl() {
        return apiKeyNegativeCacheTtl;
    }

    public void setApiKeyNegativeCacheTtl(Duration apiKeyNegativeCacheTtl) {
        this.apiKeyNegativeCacheTtl = apiKeyNegativeCacheTtl;
    }

    public Duration getApiKeyResolveTimeout() {
        return apiKeyResolveTimeout;
    }

    public void setApiKeyResolveTimeout(Duration apiKeyResolveTimeout) {
        this.apiKeyResolveTimeout = apiKeyResolveTimeout;
    }

    public Duration getDownstreamTokenTtl() {
        return downstreamTokenTtl;
    }

    public void setDownstreamTokenTtl(Duration downstreamTokenTtl) {
        this.downstreamTokenTtl = downstreamTokenTtl;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public long getMaxStreamingRequestBytes() {
        return maxStreamingRequestBytes;
    }

    public void setMaxStreamingRequestBytes(long maxStreamingRequestBytes) {
        this.maxStreamingRequestBytes = maxStreamingRequestBytes;
    }

    public List<String> getStreamingPaths() {
        return streamingPaths;
    }

    public void setStreamingPaths(List<String> streamingPaths) {
        this.streamingPaths = streamingPaths;
    }
}
