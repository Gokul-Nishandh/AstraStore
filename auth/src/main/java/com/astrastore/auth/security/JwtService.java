/**
 * Generates and validates JWT access and refresh tokens using HS256.
 * Supports separate token types (ACCESS, REFRESH) with type-aware validation.
 * Configurable expiry via jwt.expiration-ms and jwt.refresh-expiration-ms.
 */
package com.astrastore.auth.security;

import com.astrastore.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Service for generating and validating JWT tokens.
 *
 * <p>The access tokens produced here are read by every other service through
 * {@code com.astrastore.shared.security.JwtTokenVerifier}, which needs five
 * things: the {@code ACCESS} type marker, a numeric {@code userId}, a
 * {@code username}, an {@code email}, and {@code roles} as a JSON <em>array</em>
 * of bare role names.
 *
 * <p>The previous implementation wrote roles with
 * {@code authorities.forEach(a -> claims.put("roles", a.getAuthority()))},
 * which overwrote the claim on every iteration and left a single role behind
 * as a bare string — and never wrote the identity claims at all. A user with
 * both ADMIN and USER arrived downstream holding whichever role the iterator
 * happened to yield last, so admin authorisation was effectively random.
 */
@Service
public class JwtService {

    /** HS256 needs at least 256 bits of key material, same floor as the verifier. */
    private static final int MIN_SECRET_BYTES = 32;

    public enum TokenType {
        ACCESS, REFRESH
    }

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public TokenType extractTokenType(String token) {
        String type = extractClaim(token, c -> c.get("type", String.class));
        return type != null ? TokenType.valueOf(type) : TokenType.ACCESS;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Builds an access token carrying the full identity of the caller.
     *
     * <p>{@code roles} is always a {@code List<String>} of bare names, even
     * when the user holds exactly one role — a scalar would be silently
     * tolerated by the verifier today and is the shape that caused the bug.
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", TokenType.ACCESS.name());
        extraClaims.put("roles", bareRoles(userDetails));

        if (userDetails instanceof User user) {
            extraClaims.put("userId", user.getId());
            extraClaims.put("username", user.getUsername());
            extraClaims.put("email", user.getEmail());
        }
        return buildToken(extraClaims, userDetails.getUsername(), accessExpirationMs);
    }

    /**
     * The bare role names behind a UserDetails' authorities, deduplicated and
     * in a stable order.
     */
    private static List<String> bareRoles(UserDetails userDetails) {
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : userDetails.getAuthorities()) {
            String bare = Roles.stripPrefix(authority.getAuthority());
            if (bare != null && !bare.isBlank()) {
                roles.add(bare);
            }
        }
        return List.copyOf(roles);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", TokenType.REFRESH.name());
        return buildToken(extraClaims, userDetails.getUsername(), refreshExpirationMs);
    }

    public String generateRefreshToken(Long userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", TokenType.REFRESH.name());
        extraClaims.put("userId", userId);
        return buildToken(extraClaims, String.valueOf(userId), refreshExpirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        return extractTokenType(token) == TokenType.ACCESS
                && isTokenValid(token, userDetails);
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        return extractTokenType(token) == TokenType.REFRESH
                && isTokenValid(token, userDetails);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
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
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
