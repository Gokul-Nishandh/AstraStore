package com.astrastore.auth.controller;

import com.astrastore.auth.entity.User;
import com.astrastore.auth.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an API key to an identity, for the gateway only.
 *
 * <p>The gateway authenticates every request at the edge and forwards a
 * trusted identity downstream. For a bearer token it can do that alone; for
 * an API key it cannot, because only this service holds the hashes. This is
 * the call it makes.
 *
 * <p>The key never appears in a log line, a response body, or an error — not
 * even its prefix. A caller learns exactly one thing: whether the key they
 * already hold is currently valid.
 *
 * <p>Lives under {@code /internal/**}, which is gated by the shared service
 * token and is not routed by the gateway, so it is unreachable from outside
 * the compose network.
 */
@RestController
@RequestMapping("/internal/v1/auth/api-keys")
@RequiredArgsConstructor
@Slf4j
public class InternalApiKeyController {

    private static final Map<String, Object> INVALID = Map.of("valid", false);

    private final ApiKeyService apiKeyService;

    /**
     * @param apiKey the raw key, presented in the same header the client used
     * @return {@code {valid, userId, username, email, roles}}, or
     *         {@code {valid:false}} with 401 when the key is unknown, revoked
     *         or expired
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(401).body(INVALID);
        }

        Optional<User> resolved = apiKeyService.validateApiKey(apiKey.trim());
        if (resolved.isEmpty()) {
            // Deliberately indistinguishable from a revoked or expired key:
            // a caller probing this endpoint learns nothing about which.
            return ResponseEntity.status(401).body(INVALID);
        }

        User user = resolved.get();
        if (!user.isEnabled()) {
            return ResponseEntity.status(401).body(INVALID);
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "roles", List.copyOf(user.getRoles())));
    }
}
