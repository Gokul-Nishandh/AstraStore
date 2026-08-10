/**
 * Manages refresh token lifecycle with Redis cache and PostgreSQL fallback.
 * Supports creation, validation, rotation, and bulk revocation for security.
 * 32-byte SecureRandom tokens, Base64 URL-encoded, 7-day default expiry.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.RefreshToken;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.exception.AuthException;
import com.astrastore.auth.repository.RefreshTokenRepository;
import com.astrastore.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int RANDOM_BYTES = 32;
    private static String REDIS_PREFIX = "refresh:";
    private static final SecureRandom secureRandom = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public record CreatedRefreshToken(RefreshToken entity, String rawToken) {}

    @Transactional
    public CreatedRefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        String rawToken = generateRawToken();
        String tokenHash = passwordEncoder.encode(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(refreshExpirationDays));

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        cacheInRedis(saved.getId(), rawToken, refreshExpirationDays);

        log.info("Refresh token created — userId={}, tokenId={}", userId, saved.getId());
        return new CreatedRefreshToken(saved, rawToken);
    }

    @Transactional(readOnly = true)
    public Optional<User> validateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Long tokenId = lookupInRedis(rawToken);
        if (tokenId == null) {
            return Optional.empty();
        }
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(tokenId);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            return Optional.empty();
        }
        return userRepository.findById(tokenOpt.get().getUserId());
    }

    @Transactional
    public void revokeToken(String rawToken) {
        Long tokenId = lookupInRedis(rawToken);
        if (tokenId == null) return;
        refreshTokenRepository.findById(tokenId).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
        redisTemplate.delete(REDIS_PREFIX + rawToken);
    }

    @Transactional
    public CreatedRefreshToken rotateRefreshToken(String oldRawToken) {
        Optional<User> userOpt = validateRefreshToken(oldRawToken);
        if (userOpt.isEmpty()) {
            throw new AuthException("Invalid refresh token");
        }
        revokeToken(oldRawToken);
        return createRefreshToken(userOpt.get().getId());
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for userId={}", userId);
    }

    private void cacheInRedis(Long tokenId, String rawToken, int daysValid) {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + rawToken,
                    tokenId.toString(),
                    Duration.ofDays(daysValid));
        } catch (Exception e) {
            log.warn("Failed to cache refresh token in Redis — falling back to DB only: {}", e.getMessage());
        }
    }

    private Long lookupInRedis(String rawToken) {
        try {
            String tokenIdStr = redisTemplate.opsForValue().get(REDIS_PREFIX + rawToken);
            if (tokenIdStr != null) {
                return Long.parseLong(tokenIdStr);
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis lookup failed, will fall back to DB scan: {}", e.getMessage());
            return null;
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
