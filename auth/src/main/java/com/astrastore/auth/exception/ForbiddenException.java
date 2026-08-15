/**
 * The caller is authenticated and the resource exists, but this actor may not
 * perform this action — an API key attempting an account-level change, or an
 * administrator trying to strip their own ADMIN role.
 *
 * <p>Use this only when acknowledging the resource discloses nothing the
 * caller does not already know. When it would, raise
 * {@link NotFoundException} instead.
 *
 * <p>Maps to HTTP 403 with {@code ApiError.FORBIDDEN}.
 */
package com.astrastore.auth.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
