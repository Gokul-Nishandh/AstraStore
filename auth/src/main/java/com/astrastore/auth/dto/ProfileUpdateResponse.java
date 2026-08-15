/**
 * The result of {@code PATCH /api/auth/account}.
 *
 * <p>The email address is the login identity and is baked into every issued
 * access token. Changing it invalidates the caller's own session, so the new
 * token pair is handed back in the same response and the client swaps it in
 * without bouncing the user to the login screen. When only the username
 * changed, {@code tokensReissued} is false and both token fields are null.
 */
package com.astrastore.auth.dto;

public record ProfileUpdateResponse(
        UserProfileResponse user,
        boolean tokensReissued,
        String token,
        String refreshToken
) {

    public static ProfileUpdateResponse unchangedIdentity(UserProfileResponse user) {
        return new ProfileUpdateResponse(user, false, null, null);
    }

    public static ProfileUpdateResponse reissued(UserProfileResponse user, String token, String refreshToken) {
        return new ProfileUpdateResponse(user, true, token, refreshToken);
    }
}
