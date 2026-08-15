/**
 * Body of {@code PATCH /api/auth/account}.
 *
 * <p>Both fields are optional — this is a patch, and a null field means
 * "leave it alone". Sending a blank string is not a way to erase a value;
 * the pattern constraints reject it.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        // Letters, digits, space, dot, underscore and hyphen. Excludes the
        // characters that let a display name impersonate markup or another
        // account when rendered in the console.
        @Size(min = 2, max = 100, message = "Username must be between 2 and 100 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$",
                message = "Username may contain letters, numbers, spaces, dots, underscores and hyphens only")
        String username,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email
) {
}
