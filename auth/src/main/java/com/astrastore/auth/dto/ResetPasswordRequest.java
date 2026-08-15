/**
 * Body of {@code POST /api/auth/reset-password}.
 *
 * <p>{@code token} is the raw value from the reset link. The server holds
 * only its SHA-256 hash, so this is the one and only moment the raw token
 * exists on the server side — and it is never logged or echoed back.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Reset token is required")
        @Size(max = 256, message = "Reset token is malformed")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String newPassword
) {
}
