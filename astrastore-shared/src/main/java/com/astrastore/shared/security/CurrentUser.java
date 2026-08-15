package com.astrastore.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the authenticated caller out of the security context.
 *
 * <p>Prefer {@link #require()} in handlers: it fails loudly rather than
 * silently treating an unauthenticated request as anonymous, which is how
 * ownership checks get skipped by accident.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AstraPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        if (authentication.getPrincipal() instanceof AstraPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /**
     * @throws AccessDeniedException if the request is not authenticated.
     */
    public static AstraPrincipal require() {
        return find().orElseThrow(() ->
                new AccessDeniedException("Authentication is required for this operation."));
    }

    /** The caller's user id — the owner key used for every data query. */
    public static Long requireUserId() {
        return require().userId();
    }

    /**
     * Asserts the caller may act on data owned by {@code ownerId}.
     *
     * <p>Call this in every handler that loads a record by id. Without it, a
     * valid token for account A can read account B's object simply by
     * guessing its identifier.
     */
    public static void assertOwner(Long ownerId) {
        if (!require().canAccessOwner(ownerId)) {
            // Deliberately identical to the "not found" message elsewhere:
            // confirming that a resource exists but belongs to someone else
            // is itself a disclosure.
            throw new AccessDeniedException("Resource not found.");
        }
    }

    /** Asserts the caller is not READ_ONLY. */
    public static void assertCanWrite() {
        if (!require().canWrite()) {
            throw new AccessDeniedException("Your role does not permit changes.");
        }
    }

    /** Asserts the caller holds ADMIN. */
    public static void assertAdmin() {
        if (!require().isAdmin()) {
            throw new AccessDeniedException("Administrator access is required.");
        }
    }

    /** Thrown when a caller is authenticated but not permitted. */
    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
