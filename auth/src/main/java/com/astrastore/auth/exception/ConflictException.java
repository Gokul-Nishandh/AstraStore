/**
 * The request is well-formed but conflicts with the current state — a taken
 * email address, or a role change that would leave the deployment with no
 * administrator.
 *
 * <p>Maps to HTTP 409 with {@code ApiError.CONFLICT}.
 */
package com.astrastore.auth.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
