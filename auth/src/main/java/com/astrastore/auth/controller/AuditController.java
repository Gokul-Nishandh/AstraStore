/**
 * REST endpoint for users to view their own audit logs.
 * Paginated for performance with large log histories.
 * Users can only see their own audit events for privacy/security.
 */
package com.astrastore.auth.controller;

import com.astrastore.auth.dto.AuditLogResponse;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getMyAuditLogs(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = resolveUser(principal);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<com.astrastore.auth.entity.AuditLog> logs = auditLogService.getLogsForUser(user.getId(), pageable);

        List<AuditLogResponse> response = logs.getContent().stream()
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .userId(log.getUserId())
                        .action(log.getAction())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .success(log.isSuccess())
                        .failureReason(log.getFailureReason())
                        .timestamp(log.getTimestamp())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }

    private User resolveUser(UserDetails principal) {
        String lookup = principal.getUsername();
        return userRepository.findByUsername(lookup)
                .or(() -> userRepository.findByEmail(lookup))
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + lookup));
    }
}
