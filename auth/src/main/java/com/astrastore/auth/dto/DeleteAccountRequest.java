/**
 * Body of {@code DELETE /api/auth/account}.
 *
 * <p>Deleting an account destroys data across several services and cannot be
 * undone, so it is gated on re-entering the password rather than merely
 * holding a valid token.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(

        @NotBlank(message = "Password confirmation is required")
        @Size(max = 128, message = "Password must be at most 128 characters")
        String password
) {
}
