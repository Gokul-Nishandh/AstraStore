/**
 * An outstanding cross-service cleanup obligation, as exposed to operators
 * and to the services that consume it.
 */
package com.astrastore.auth.dto;

import java.time.Instant;

public record UserDeletionEventResponse(
        Long id,
        Long userId,
        String username,
        String email,
        Long requestedBy,
        String reason,
        Instant requestedAt,
        boolean processed,
        Instant processedAt
) {
}
