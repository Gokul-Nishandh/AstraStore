/**
 * REST endpoints for API key CRUD operations.
 * Requires authenticated user; only manages keys belonging to the caller.
 * Returns the raw key exactly once at creation for secure distribution.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.ApiKeyCreatedResponse;
import com.astrastore.auth.dto.ApiKeyRequest;
import com.astrastore.auth.dto.ApiKeyResponse;
import com.astrastore.auth.entity.ApiKey;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.service.ApiKeyService;
import com.astrastore.auth.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(
            @Valid @RequestBody ApiKeyRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {

        User user = resolveUser(principal);
        ApiKeyService.CreatedApiKey created = apiKeyService.createApiKey(
                user.getId(), request.getName(), request.getExpiresAt());

        auditLogService.logEvent(user.getId(), AuditAction.API_KEY_CREATED,
                httpRequest, true, null);

        ApiKeyCreatedResponse response = ApiKeyCreatedResponse.builder()
                .id(created.entity().getId())
                .name(created.entity().getName())
                .key(created.rawKey())
                .keyPrefix(created.entity().getKeyPrefix())
                .expiresAt(created.entity().getExpiresAt())
                .createdAt(created.entity().getCreatedAt())
                .build();

        log.info("API key created for user — userId={}, keyId={}", user.getId(), created.entity().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {

        User user = resolveUser(principal);
        List<ApiKey> keys = apiKeyService.listApiKeys(user.getId());

        auditLogService.logEvent(user.getId(), AuditAction.API_KEY_LIST_VIEWED,
                httpRequest, true, null);

        List<ApiKeyResponse> response = keys.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeApiKey(
            @PathVariable Long keyId,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {

        User user = resolveUser(principal);
        apiKeyService.revokeApiKey(user.getId(), keyId);

        auditLogService.logEvent(user.getId(), AuditAction.API_KEY_REVOKED,
                httpRequest, true, null);

        log.info("API key revoked — userId={}, keyId={}", user.getId(), keyId);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(UserDetails principal) {
        String lookup = principal.getUsername();
        return userRepository.findByUsername(lookup)
                .or(() -> userRepository.findByEmail(lookup))
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + lookup));
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return ApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(key.getKeyPrefix())
                .expiresAt(key.getExpiresAt())
                .createdAt(key.getCreatedAt())
                .lastUsedAt(key.getLastUsedAt())
                .build();
    }
}
