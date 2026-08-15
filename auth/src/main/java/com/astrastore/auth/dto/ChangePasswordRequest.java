/**
 * Body of {@code POST /api/auth/account/password}.
 *
 * <p>The current password is required even though the caller is already
 * authenticated: it is what stops a stolen access token, or an unattended
 * browser, from being escalated into permanent ownership of the account.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @Size(max = 128, message = "Password must be at most 128 characters")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String newPassword
) {
}
