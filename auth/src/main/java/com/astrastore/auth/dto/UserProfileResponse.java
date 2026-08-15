/**
 * The caller's own profile, returned by {@code GET /api/auth/account}.
 * Carries no secret of any kind.
 */
package com.astrastore.auth.dto;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        List<String> roles,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {
}
