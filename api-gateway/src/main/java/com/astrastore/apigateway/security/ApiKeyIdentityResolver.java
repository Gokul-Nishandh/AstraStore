package com.astrastore.apigateway.security;

import com.astrastore.apigateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Turns an API key into an identity, without asking the auth service on every
 * request.
 *
 * <p>The raw key is never logged, never used as a cache key and never stored:
 * Redis is keyed by a SHA-256 digest of the key, and only the resolved
 * identity is cached. Rejections are cached too, briefly — otherwise a
 * stream of invalid keys becomes a denial-of-service against auth.
 */
public class ApiKeyIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyIdentityResolver.class);

    private static final String CACHE_PREFIX = "astra:gw:apikey:";
    private static final String INVALID_MARKER = "!";

    private final WebClient authClient;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final GatewayProperties properties;

    public ApiKeyIdentityResolver(WebClient authClient,
                                  ReactiveStringRedisTemplate redis,
                                  ObjectMapper objectMapper,
                                  GatewayProperties properties) {
        this.authClient = authClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * @return the resolved identity, or an empty {@link Mono} if the key is
     *         unknown, revoked, expired, or the auth service cannot say.
     */
    public Mono<GatewayIdentity> resolve(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Mono.empty();

        String digest = sha256(apiKey);
        String cacheKey = CACHE_PREFIX + digest;

        return readCache(cacheKey)
                .flatMap(cached -> INVALID_MARKER.equals(cached)
                        ? Mono.<GatewayIdentity>empty()
                        : Mono.justOrEmpty(deserialize(cached, digest)))
                .switchIfEmpty(Mono.defer(() -> verifyRemotely(apiKey, cacheKey, digest)));
    }

    private Mono<String> readCache(String cacheKey) {
        if (redis == null) return Mono.empty();
        return redis.opsForValue().get(cacheKey)
                .onErrorResume(e -> {
                    log.debug("API key cache read unavailable: {}", e.toString());
                    return Mono.empty();
                });
    }

    private Mono<GatewayIdentity> verifyRemotely(String apiKey, String cacheKey, String digest) {
        return authClient.post()
                .uri(properties.getApiKeyVerifyPath())
                .header(properties.getApiKeyHeader(), apiKey)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        // Any non-2xx is a rejection: the body is discarded
                        // unread so nothing the auth service says can reach a
                        // client, and the caller gets a plain 401.
                        return response.releaseBody().then(Mono.<JsonNode>empty());
                    }
                    return response.bodyToMono(JsonNode.class);
                })
                .timeout(properties.getApiKeyResolveTimeout())
                .flatMap(node -> Mono.justOrEmpty(toIdentity(node, digest)))
                .flatMap(identity -> cache(cacheKey, identity).thenReturn(identity))
                .switchIfEmpty(Mono.defer(() -> cacheInvalid(cacheKey).then(Mono.empty())))
                .onErrorResume(e -> {
                    // No detail about the key, ever — not even its prefix.
                    log.warn("API key could not be resolved with the auth service: {}",
                            e.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    private Mono<Boolean> cache(String cacheKey, GatewayIdentity identity) {
        if (redis == null) return Mono.just(false);
        String payload = serialize(identity);
        if (payload == null) return Mono.just(false);
        return redis.opsForValue()
                .set(cacheKey, payload, properties.getApiKeyCacheTtl())
                .onErrorResume(e -> Mono.just(false));
    }

    private Mono<Boolean> cacheInvalid(String cacheKey) {
        if (redis == null) return Mono.just(false);
        return redis.opsForValue()
                .set(cacheKey, INVALID_MARKER, properties.getApiKeyNegativeCacheTtl())
                .onErrorResume(e -> Mono.just(false));
    }

    private String serialize(GatewayIdentity identity) {
        try {
            return objectMapper.writeValueAsString(new CachedIdentity(
                    identity.userId(), identity.username(), identity.email(), identity.roles()));
        } catch (Exception e) {
            return null;
        }
    }

    private GatewayIdentity deserialize(String payload, String digest) {
        try {
            CachedIdentity cached = objectMapper.readValue(payload, CachedIdentity.class);
            if (cached == null || cached.userId() == null) return null;
            return new GatewayIdentity(cached.userId(), cached.username(), cached.email(),
                    cached.roles(), true, "key:" + digest);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads the auth service's answer leniently: the userId may arrive as a
     * number or a string, and roles as a list or a comma-separated string.
     */
    static GatewayIdentity toIdentity(JsonNode node, String digest) {
        if (node == null || node.isNull()) return null;
        if (node.has("valid") && !node.path("valid").asBoolean(true)) return null;

        JsonNode idNode = node.hasNonNull("userId") ? node.get("userId") : node.get("id");
        if (idNode == null || idNode.isNull()) return null;

        Long userId;
        try {
            userId = idNode.isNumber() ? idNode.asLong() : Long.parseLong(idNode.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }

        Set<String> roles = new LinkedHashSet<>();
        JsonNode rolesNode = node.get("roles");
        if (rolesNode != null && rolesNode.isArray()) {
            rolesNode.forEach(role -> addRole(roles, role.asText()));
        } else if (rolesNode != null && rolesNode.isTextual()) {
            for (String part : rolesNode.asText().split(",")) addRole(roles, part);
        }

        return new GatewayIdentity(
                userId,
                node.path("username").asText(null),
                node.path("email").asText(null),
                roles,
                true,
                "key:" + digest);
    }

    private static void addRole(Set<String> target, String value) {
        if (value == null) return;
        String cleaned = value.trim();
        if (cleaned.startsWith("ROLE_")) cleaned = cleaned.substring("ROLE_".length());
        if (!cleaned.isEmpty() && !"null".equals(cleaned)) target.add(cleaned);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** What actually goes into Redis — identity only, never the credential. */
    record CachedIdentity(Long userId, String username, String email, Set<String> roles) {
        CachedIdentity {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }
}
