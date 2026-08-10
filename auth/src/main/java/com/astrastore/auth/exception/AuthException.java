/**
 * Generic authentication/authorization exception for token and credential failures.
 * Used by RefreshTokenService and other auth-related components.
 * Maps to HTTP 401 via GlobalExceptionHandler.
 */
package com.astrastore.auth.exception;

public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
