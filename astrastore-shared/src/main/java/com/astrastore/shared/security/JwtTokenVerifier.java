package com.astrastore.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies AstraStore access tokens.
 *
 * <p>Every service holds the same signing secret and checks tokens itself,
 * rather than trusting a header injected upstream. The gateway is still the
 * front door, but a service that somehow becomes reachable directly is not
 * thereby unprotected — the previous build exposed every service on a host
 * port with no authentication at all.
 *
 * <p>Deliberately dependency-light: no Spring types, so it can be unit
 * tested and reused from the SDK and CLI.
 */
public final class JwtTokenVerifier {

    /** HS256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;

    public JwtTokenVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the JWT_SECRET environment variable.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256; got " + keyBytes.length + ".");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Verifies a token's signature and expiry and maps it to a principal.
     *
     * @return the principal, or empty if the token is missing, malformed,
     *         expired, signed with the wrong key, or not an access token.
     *         Callers get no detail about which — that distinction is useful
     *         to an attacker and to nobody else.
     */
    public Optional<AstraPrincipal> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // A refresh token must never be accepted as an access token:
            // it is long-lived and held in client storage.
            String type = claims.get("type", String.class);
            if (type != null && !"ACCESS".equals(type)) return Optional.empty();

            Long userId = readUserId(claims);
            if (userId == null) return Optional.empty();

            return Optional.of(new AstraPrincipal(
                    userId,
                    claims.get("username", String.class),
                    claims.get("email", String.class),
                    readRoles(claims),
                    Boolean.TRUE.equals(claims.get("apiKey", Boolean.class))
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Tokens have carried the user id as both a number and a string. */
    private static Long readUserId(Claims claims) {
        Object raw = claims.get("userId");
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads the roles claim, tolerating every shape it has been written in:
     * a list, a single string, or a comma-separated string. The
     * {@code ROLE_} prefix is stripped so callers compare bare role names.
     */
    private static Set<String> readRoles(Claims claims) {
        Object raw = claims.get("roles");
        Set<String> roles = new LinkedHashSet<>();

        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addRole(roles, String.valueOf(item));
            }
        } else if (raw instanceof String s) {
            for (String part : s.split(",")) {
                addRole(roles, part);
            }
        }
        return roles;
    }

    private static void addRole(Set<String> target, String value) {
        if (value == null) return;
        String cleaned = value.trim();
        if (cleaned.startsWith("ROLE_")) cleaned = cleaned.substring("ROLE_".length());
        if (!cleaned.isEmpty() && !"null".equals(cleaned)) target.add(cleaned);
    }
}
