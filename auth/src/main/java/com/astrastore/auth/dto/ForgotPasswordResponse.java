/**
 * Response of {@code POST /api/auth/forgot-password}.
 *
 * <p>{@code message} is identical whether or not the address belongs to an
 * account — an endpoint that answers differently is a user-enumeration
 * oracle, and this one is unauthenticated.
 *
 * <p>{@code resetToken} is null in every real deployment. It is populated
 * only when {@code astrastore.auth.expose-reset-token} is on, which exists
 * because there is no mail server here and the flow would otherwise be
 * untestable; the property is pinned off in the production profile. The field
 * is always present in the JSON so clients see one stable shape.
 */
package com.astrastore.auth.dto;

public record ForgotPasswordResponse(String message, String resetToken) {

    public static ForgotPasswordResponse of(String message) {
        return new ForgotPasswordResponse(message, null);
    }

    public static ForgotPasswordResponse withToken(String message, String resetToken) {
        return new ForgotPasswordResponse(message, resetToken);
    }
}
