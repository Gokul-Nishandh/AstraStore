/**
 * Business logic for API key lifecycle — generation, listing, revocation, validation.
 * Generates Stripe-style keys (astra_sk_*) with BCrypt hashing; raw key shown only once.
 * Enforces 1-year max expiry and supports ownership checks for security.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.ApiKey;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.exception.ApiKeyException;
import com.astrastore.auth.repository.ApiKeyRepository;
import com.astrastore.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String KEY_PREFIX = "astra_sk_";
    private static final int RANDOM_BYTES = 24;
    private static final long MAX_EXPIRY_DAYS = 365;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public record CreatedApiKey(ApiKey entity, String rawKey) {}

    @Transactional
    public CreatedApiKey createApiKey(Long userId, String name, Instant expiresAt) {
        if (name == null || name.isBlank()) {
            throw new ApiKeyException("API key name is required");
        }
        if (expiresAt != null && expiresAt.isAfter(Instant.now().plus(MAX_EXPIRY_DAYS, ChronoUnit.DAYS))) {
            throw new ApiKeyException("API key expiry cannot exceed 1 year");
        }

        String rawKey = generateRawKey();
        String keyHash = passwordEncoder.encode(rawKey);
        String keyPrefix = rawKey.substring(0, 12) + "...";

        ApiKey apiKey = ApiKey.builder()
                .userId(userId)
                .keyHash(keyHash)
                .keyPrefix(keyPrefix)
                .name(name)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key created — userId={}, keyId={}, prefix={}", userId, saved.getId(), keyPrefix);
        return new CreatedApiKey(saved, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeys(Long userId) {
        return apiKeyRepository.findByUserIdAndRevokedFalse(userId);
    }

    @Transactional
    public void revokeApiKey(Long userId, Long keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ApiKeyException("API key not found"));
        if (apiKey.isRevoked()) {
            throw new ApiKeyException("API key already revoked");
        }
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
        log.info("API key revoked — userId={}, keyId={}", userId, keyId);
    }

    @Transactional
    public Optional<User> validateApiKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String keyPrefix = rawKey.substring(0, 12) + "...";
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndRevokedFalse(keyPrefix).stream()
                .filter(ApiKey::isValid)
                .toList();
        for (ApiKey candidate : candidates) {
            if (passwordEncoder.matches(rawKey, candidate.getKeyHash())) {
                candidate.setLastUsedAt(Instant.now());
                apiKeyRepository.save(candidate);
                return userRepository.findById(candidate.getUserId());
            }
        }
        return Optional.empty();
    }

    private String generateRawKey() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return KEY_PREFIX + randomPart;
    }
}
