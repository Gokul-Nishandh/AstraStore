package com.astrastore.apigateway.security;

import com.astrastore.apigateway.config.GatewayProperties;
import com.astrastore.apigateway.error.ErrorResponses;
import com.astrastore.shared.api.ApiError;
import com.astrastore.shared.security.AstraHeaders;
import com.astrastore.shared.security.JwtTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The front door. Runs before every route filter.
 *
 * <p>In order: strip any identity headers the client sent, assign a request
 * id, reject an absurd body, then authenticate. A request reaches a
 * downstream service only if it carried a valid access token or an API key
 * the auth service recognised — with the sole exception of the public
 * allowlist (sign-in, token refresh, health).
 *
 * <p>The stripping comes first and unconditionally. If it ran after
 * authentication, a request that failed the public-path check could still
 * carry {@code X-Astra-User-Id: 1} into a service that trusts it.
 */
public class EdgeAuthenticationFilter implements GlobalFilter, Ordered {

    /** Ahead of every route filter, including the rate limiter. */
    public static final int ORDER = -100;

    /** Where the resolved caller is parked for the rate-limit key resolver. */
    public static final String IDENTITY_ATTRIBUTE = "astra.gateway.identity";
    public static final String RATE_LIMIT_SUBJECT_ATTRIBUTE = "astra.gateway.rateLimitSubject";

    private static final Logger log = LoggerFactory.getLogger(EdgeAuthenticationFilter.class);

    private static final String BEARER = "Bearer ";
    /** An inbound correlation id is echoed only if it is boring and short. */
    private static final Pattern SANE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private final JwtTokenVerifier verifier;
    private final ApiKeyIdentityResolver apiKeyResolver;
    private final DownstreamTokenIssuer tokenIssuer;
    private final GatewayProperties properties;

    private final List<PathPattern> publicPatterns;
    private final List<PathPattern> streamingPatterns;

    public EdgeAuthenticationFilter(JwtTokenVerifier verifier,
                                    ApiKeyIdentityResolver apiKeyResolver,
                                    DownstreamTokenIssuer tokenIssuer,
                                    GatewayProperties properties) {
        this.verifier = verifier;
        this.apiKeyResolver = apiKeyResolver;
        this.tokenIssuer = tokenIssuer;
        this.properties = properties;
        PathPatternParser parser = new PathPatternParser();
        this.publicPatterns = properties.getPublicPaths().stream().map(parser::parse).toList();
        this.streamingPatterns = properties.getStreamingPaths().stream().map(parser::parse).toList();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = resolveRequestId(request);
        exchange.getResponse().getHeaders().set(AstraHeaders.REQUEST_ID, requestId);

        Long declaredLength = request.getHeaders().getContentLength() < 0
                ? null : request.getHeaders().getContentLength();
        long limit = limitFor(exchange);
        if (declaredLength != null && declaredLength > limit) {
            return ErrorResponses.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    ApiError.PAYLOAD_TOO_LARGE,
                    "That upload is larger than the maximum allowed size.");
        }

        // CORS preflight carries no credentials by design.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(withCleanHeaders(exchange, requestId, null, null));
        }

        if (isPublic(exchange)) {
            exchange.getAttributes().put(RATE_LIMIT_SUBJECT_ATTRIBUTE, "ip:" + clientIp(exchange));
            return chain.filter(withCleanHeaders(exchange, requestId, null, null));
        }

        Optional<GatewayIdentity> fromToken = bearerToken(request)
                .flatMap(verifier::verify)
                .map(GatewayIdentity::fromToken);

        if (fromToken.isPresent()) {
            return proceed(exchange, chain, requestId, fromToken.get(), null);
        }

        String apiKey = request.getHeaders().getFirst(properties.getApiKeyHeader());
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKeyResolver.resolve(apiKey)
                    .flatMap(identity -> proceed(exchange, chain, requestId, identity,
                            tokenIssuer.issue(identity)))
                    .switchIfEmpty(Mono.defer(() -> unauthenticated(exchange)));
        }

        return unauthenticated(exchange);
    }

    private Mono<Void> proceed(ServerWebExchange exchange,
                               GatewayFilterChain chain,
                               String requestId,
                               GatewayIdentity identity,
                               String mintedToken) {
        exchange.getAttributes().put(IDENTITY_ATTRIBUTE, identity);
        exchange.getAttributes().put(RATE_LIMIT_SUBJECT_ATTRIBUTE, identity.rateLimitSubject());
        return chain.filter(withCleanHeaders(exchange, requestId, identity, mintedToken));
    }

    private Mono<Void> unauthenticated(ServerWebExchange exchange) {
        return ErrorResponses.write(exchange, HttpStatus.UNAUTHORIZED,
                ApiError.UNAUTHENTICATED, "Sign in to continue.");
    }

    /**
     * Rebuilds the request with client-asserted identity headers removed and
     * the gateway's own values in their place.
     */
    private ServerWebExchange withCleanHeaders(ServerWebExchange exchange,
                                               String requestId,
                                               GatewayIdentity identity,
                                               String mintedToken) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    AstraHeaders.CLIENT_FORBIDDEN.forEach(headers::remove);
                    headers.set(AstraHeaders.REQUEST_ID, requestId);
                    if (identity != null) {
                        headers.set(AstraHeaders.USER_ID, String.valueOf(identity.userId()));
                        if (identity.username() != null) {
                            headers.set(AstraHeaders.USERNAME, identity.username());
                        }
                        headers.set(AstraHeaders.ROLES, identity.rolesHeaderValue());
                    }
                    if (mintedToken != null) {
                        // Replaces the client's API key with a token every
                        // downstream service can verify for itself.
                        headers.set(HttpHeaders.AUTHORIZATION, BEARER + mintedToken);
                        headers.remove(properties.getApiKeyHeader());
                    }
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private boolean isPublic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private long limitFor(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        boolean streaming = streamingPatterns.stream().anyMatch(pattern -> pattern.matches(path));
        return streaming ? properties.getMaxStreamingRequestBytes() : properties.getMaxRequestBytes();
    }

    private static Optional<String> bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) return Optional.empty();
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    static String resolveRequestId(ServerHttpRequest request) {
        String inbound = request.getHeaders().getFirst(AstraHeaders.REQUEST_ID);
        if (inbound != null && SANE_REQUEST_ID.matcher(inbound).matches()) {
            return inbound;
        }
        if (inbound != null) {
            log.debug("Discarding malformed inbound request id");
        }
        return UUID.randomUUID().toString();
    }

    /** Also the fallback rate-limit subject, so {@code RateLimitConfig} calls it. */
    public static String clientIp(ServerWebExchange exchange) {
        var remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) return "unknown";
        return remote.getAddress().getHostAddress();
    }
}
