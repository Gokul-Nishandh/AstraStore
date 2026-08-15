package com.astrastore.shared.api;

import java.time.Instant;

/**
 * The single error shape every AstraStore service returns.
 *
 * <p>Before this existed, callers saw three different shapes — {@code
 * {"error": "..."}}, Spring's default {@code {timestamp, status, error,
 * path}}, and raw container error pages — which is why the dashboard ended
 * up showing users bare status codes like "503".
 *
 * <p>{@code code} is a stable machine-readable token the client switches on.
 * {@code message} is written for a human and is safe to display verbatim: it
 * must never contain a stack trace, SQL, an internal hostname, or the reason
 * a lookup failed in a way that discloses another account's data.
 *
 * @param code      stable token, e.g. {@code OBJECT_NOT_FOUND}
 * @param message   human-readable, display-safe explanation
 * @param requestId correlation id for support, if one was assigned
 * @param timestamp when the failure occurred
 */
public record ApiError(
        String code,
        String message,
        String requestId,
        Instant timestamp
) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(code, message, requestId, Instant.now());
    }

    // --- Common codes -------------------------------------------------
    // Shared with the frontend's error map so a new code cannot appear on
    // screen as an untranslated token.

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CONFLICT = "CONFLICT";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
