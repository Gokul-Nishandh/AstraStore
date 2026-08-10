/**
 * Request payload for token refresh and logout operations.
 * Contains the raw refresh token issued at login.
 * Tokens are validated server-side against Redis + PostgreSQL.
 */
package com.astrastore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
