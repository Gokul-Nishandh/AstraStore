/**
 * The platform's role vocabulary, and the only place that decides what a
 * valid role string is.
 *
 * <p>Roles are stored and transmitted bare — {@code ADMIN}, not
 * {@code ROLE_ADMIN}. The {@code ROLE_} prefix exists only inside Spring
 * Security's authority model and is added and stripped at that boundary, in
 * {@code User.getAuthorities()} and in {@code JwtService}. Mixing the two
 * conventions is what produced tokens nothing downstream could read.
 */
package com.astrastore.auth.security;

import com.astrastore.shared.security.AstraPrincipal;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Roles {

    public static final String ADMIN = AstraPrincipal.ROLE_ADMIN;
    public static final String DEVELOPER = AstraPrincipal.ROLE_DEVELOPER;
    public static final String USER = AstraPrincipal.ROLE_USER;
    public static final String READ_ONLY = AstraPrincipal.ROLE_READ_ONLY;

    /** Assigned to every account created through registration. */
    public static final String DEFAULT_ROLE = USER;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    private static final Set<String> KNOWN = Set.copyOf(AstraPrincipal.ALL_ROLES);

    private Roles() {
    }

    public static Set<String> all() {
        return KNOWN;
    }

    public static boolean isKnown(String role) {
        return role != null && KNOWN.contains(role.trim().toUpperCase());
    }

    /**
     * Normalises a caller-supplied role set: trims, upper-cases, strips any
     * {@code ROLE_} prefix a client added by mistake, and rejects anything
     * not in the vocabulary. Order is preserved so audit detail reads the way
     * the operator typed it.
     *
     * @throws IllegalArgumentException if any value is not a known role
     */
    public static Set<String> normalise(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Set<String> normalised = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Role must not be blank");
            }
            String cleaned = value.trim().toUpperCase();
            if (cleaned.startsWith(AUTHORITY_PREFIX)) {
                cleaned = cleaned.substring(AUTHORITY_PREFIX.length());
            }
            if (!KNOWN.contains(cleaned)) {
                throw new IllegalArgumentException(
                        "Unknown role '" + cleaned + "'. Valid roles are " + AstraPrincipal.ALL_ROLES);
            }
            normalised.add(cleaned);
        }
        return normalised;
    }

    /** Strips the Spring authority prefix, if present. */
    public static String stripPrefix(String authority) {
        if (authority == null) return null;
        return authority.startsWith(AUTHORITY_PREFIX)
                ? authority.substring(AUTHORITY_PREFIX.length())
                : authority;
    }
}
