/**
 * Response payload returned after login or token refresh.
 * Contains access token, optional refresh token, and user identity.
 * The refreshToken field is only populated on login and refresh endpoints.
 */
package com.astrastore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type;
    private String refreshToken;
    private Long userId;
    private String username;
    private String email;
    private Set<String> roles;
}
