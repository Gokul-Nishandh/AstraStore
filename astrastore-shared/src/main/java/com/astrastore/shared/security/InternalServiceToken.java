package com.astrastore.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The shared secret guarding {@code /internal/**} — held by metadata, which
 * checks it, and by upload, download and replication, which present it.
 *
 * <p>A blank token disables the check. That is a convenience on a laptop and a
 * hole anywhere else: {@code /internal/**} skips ownership entirely, so an
 * unauthenticated one lets anything on the container network read and rewrite
 * any user's object metadata. {@link #resolve} therefore refuses to produce an
 * instance without a token unless a development or test profile is active — a
 * misconfigured deployment fails to start instead of coming up open.
 *
 * <p>The secret itself is never logged, at any level. Only whether enforcement
 * is on.
 */
public final class InternalServiceToken {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceToken.class);

    /** The property every service binds; the same name on all four of them. */
    public static final String PROPERTY = "astrastore.internal.service-token";

    /** Profiles a developer runs by hand, where an unset token is acceptable. */
    private static final Set<String> DEVELOPMENT_PROFILES =
            Set.of("dev", "development", "local", "test");

    private final String value;

    private InternalServiceToken(String value) {
        this.value = value;
    }

    /**
     * @param configured     the raw configured value, possibly null or blank
     * @param activeProfiles the profiles Spring resolved, empty when none were
     *                       set — which is the case for a plain
     *                       {@code docker compose up}, so it counts as
     *                       production and must have a token
     * @throws IllegalStateException when no token is configured outside a
     *                               development profile
     */
    public static InternalServiceToken resolve(String configured, Collection<String> activeProfiles) {
        String trimmed = configured == null ? "" : configured.trim();

        if (!trimmed.isEmpty()) {
            log.info("Internal service token: enforcement ENABLED for /internal/**.");
            return new InternalServiceToken(trimmed);
        }

        if (!isDevelopment(activeProfiles)) {
            throw new IllegalStateException(
                    PROPERTY + " is not set, which would leave /internal/** unauthenticated. "
                            + "That surface bypasses every ownership check, so it must never run open: "
                            + "set the ASTRA_INTERNAL_TOKEN environment variable on metadata, upload, "
                            + "download and replication to the same value. Running without it is "
                            + "allowed only under the " + DEVELOPMENT_PROFILES + " profiles.");
        }

        log.warn("Internal service token: enforcement DISABLED — /internal/** is unauthenticated. "
                + "Permitted only because a development profile is active: {}", activeProfiles);
        return new InternalServiceToken("");
    }

    /** Convenience for a single profile name, or none at all. */
    public static InternalServiceToken resolve(String configured, String... activeProfiles) {
        return resolve(configured, List.of(activeProfiles));
    }

    private static boolean isDevelopment(Collection<String> activeProfiles) {
        return activeProfiles != null && activeProfiles.stream()
                .filter(profile -> profile != null)
                .anyMatch(profile -> DEVELOPMENT_PROFILES.contains(profile.trim().toLowerCase(Locale.ROOT)));
    }

    public boolean isConfigured() {
        return !value.isEmpty();
    }

    /** The secret itself. Put it in a header; do not log it or echo it back. */
    public String value() {
        return value;
    }
}
