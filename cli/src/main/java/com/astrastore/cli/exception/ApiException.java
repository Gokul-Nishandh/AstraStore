/**
 * Thrown when HTTP API calls fail (non-2xx status codes).
 * Wraps status code, response body, and original URL for debugging.
 * Maps to CLI exit code 6 in Main entry point.
 * Used by all AstraHttpClient callers to signal HTTP failures.
 */
package com.astrastore.cli.exception;

public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
