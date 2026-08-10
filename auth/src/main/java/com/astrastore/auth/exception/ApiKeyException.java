/**
 * Thrown when API key operations fail (validation, revocation, expiry violations).
 * Maps to HTTP 400/404 responses via GlobalExceptionHandler.
 * Used throughout ApiKeyService to signal recoverable client errors.
 */
package com.astrastore.auth.exception;

public class ApiKeyException extends RuntimeException {
    public ApiKeyException(String message) {
        super(message);
    }
}
