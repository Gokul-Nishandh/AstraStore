package com.astrastore.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints the very short-lived access token a downstream service sees when the
 * client authenticated with an API key.
 *
 * <p>Downstream services verify tokens themselves rather than trusting a
 * header — that is what makes them safe when reached directly. An API key is
 * meaningless to them, so the edge converts a resolved API-key identity into
 * the one credential every service already understands. The token lives for
 * about a minute and never leaves the internal network.
 */
public class DownstreamTokenIssuer {

    private final SecretKey signingKey;
    private final Duration ttl;

    public DownstreamTokenIssuer(String secret, Duration ttl) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the JWT_SECRET environment variable.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes for HS256; got " + keyBytes.length + ".");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = ttl == null ? Duration.ofMinutes(2) : ttl;
    }

    public String issue(GatewayIdentity identity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(identity.userId()))
                .claim("userId", identity.userId())
                .claim("username", identity.username())
                .claim("email", identity.email())
                .claim("roles", List.copyOf(identity.roles()))
                .claim("type", "ACCESS")
                .claim("apiKey", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }
}
