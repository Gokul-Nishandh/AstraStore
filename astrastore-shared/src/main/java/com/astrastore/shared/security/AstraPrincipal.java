package com.astrastore.shared.security;

import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, as every service sees them.
 *
 * <p>This is the single identity type shared across the platform. It is
 * derived from a verified access token (or an API key resolved by the
 * gateway) and carries only what a downstream service needs in order to
 * make an authorisation decision — never the token itself.
 *
 * @param userId   the auth service's user id; the owner key for all data
 * @param username display name
 * @param email    login identity
 * @param roles    granted roles, without the {@code ROLE_} prefix
 * @param viaApiKey whether the caller authenticated with an API key rather
 *                  than an interactive login. Some destructive operations
 *                  (deleting an account, changing a role) are refused to
 *                  API keys even when the role would otherwise allow it.
 */
public record AstraPrincipal(
        Long userId,
        String username,
        String email,
        Set<String> roles,
        boolean viaApiKey
) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_DEVELOPER = "DEVELOPER";
    public static final String ROLE_READ_ONLY = "READ_ONLY";
    public static final String ROLE_USER = "USER";

    /** Every role the platform recognises, most privileged first. */
    public static final List<String> ALL_ROLES =
            List.of(ROLE_ADMIN, ROLE_DEVELOPER, ROLE_USER, ROLE_READ_ONLY);

    public AstraPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Whether this caller may write. READ_ONLY holders may list and download
     * but never create, modify or delete.
     */
    public boolean canWrite() {
        return !roles.contains(ROLE_READ_ONLY) || isAdmin();
    }

    /**
     * Whether this caller may act on data owned by {@code ownerId}.
     *
     * <p>Ownership is the default rule and admin is the only exception. Every
     * handler that returns or mutates user-scoped data must run this check —
     * it is what stops one account from reading another's objects.
     */
    public boolean canAccessOwner(Long ownerId) {
        if (ownerId == null) return false;
        return isAdmin() || ownerId.equals(userId);
    }
}
