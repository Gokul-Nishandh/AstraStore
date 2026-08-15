/**
 * A user as the administration console sees them.
 *
 * <p>Deliberately absent: the password hash. An admin has no legitimate use
 * for another account's BCrypt digest, and shipping it to a browser turns
 * every XSS into an offline cracking job.
 */
package com.astrastore.auth.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        List<String> roles,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        long apiKeyCount
) {
}
