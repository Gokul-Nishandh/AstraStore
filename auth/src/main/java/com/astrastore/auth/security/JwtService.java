/**
 * Generates and validates JWT access and refresh tokens using HS256.
 * Supports separate token types (ACCESS, REFRESH) with type-aware validation.
 * Configurable expiry via jwt.expiration-ms and jwt.refresh-expiration-ms.
 */
package com.astrastore.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for generating and validating JWT tokens.
 * Supports separate access and refresh token types.
 */
@Service
public class JwtService {

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

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        userDetails.getAuthorities().forEach(auth ->
            extraClaims.put("roles", auth.getAuthority())
        );
        extraClaims.put("type", TokenType.ACCESS.name());
        return buildToken(extraClaims, userDetails.getUsername(), accessExpirationMs);
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
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
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
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
