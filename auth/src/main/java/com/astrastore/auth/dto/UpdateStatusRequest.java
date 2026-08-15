/**
 * Body of {@code PATCH /api/auth/admin/users/{id}/status}.
 *
 * <p>{@code enabled} is boxed and required: a primitive would silently
 * default a missing field to {@code false} and disable an account nobody
 * asked to disable.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(

        @NotNull(message = "enabled is required")
        Boolean enabled,

        @Size(max = 256, message = "Reason must be at most 256 characters")
        String reason
) {
}
