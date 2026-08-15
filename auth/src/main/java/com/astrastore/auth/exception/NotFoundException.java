/**
 * The requested resource does not exist — or exists but belongs to someone
 * else, which callers must not be able to tell apart.
 *
 * <p>Answering "403 Forbidden" for a resource that exists and "404 Not Found"
 * for one that does not turns any id-taking endpoint into an oracle for
 * enumerating other accounts' data. Both cases raise this.
 *
 * <p>Maps to HTTP 404 with {@code ApiError.NOT_FOUND}.
 */
package com.astrastore.auth.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    /** The generic message used whenever the caller may not learn more. */
    public static NotFoundException resource(String what) {
        return new NotFoundException(what + " not found");
    }
}
