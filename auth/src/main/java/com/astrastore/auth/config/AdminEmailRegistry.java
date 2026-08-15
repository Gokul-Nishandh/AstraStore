/**
 * The set of email addresses the operator has designated as administrators,
 * read from {@code ASTRASTORE_ADMIN_EMAILS}.
 *
 * <p>This is the bootstrap answer to a chicken-and-egg problem: registration
 * always creates a plain USER, so without an out-of-band channel a fresh
 * deployment would have no administrator and no way to appoint one.
 *
 * <p>Membership is consulted at two moments, which between them make the
 * order of "start the stack" and "register an account" irrelevant:
 * <ul>
 *   <li>at startup, for accounts that already exist;</li>
 *   <li>at registration, for accounts created afterwards.</li>
 * </ul>
 *
 * <p>Comparison is case-insensitive and whitespace-tolerant, because the
 * value arrives from a hand-edited {@code .env} file.
 */
package com.astrastore.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Slf4j
public class AdminEmailRegistry {

    private final Set<String> adminEmails;

    public AdminEmailRegistry(
            @Value("${astrastore.admin-emails:${ASTRASTORE_ADMIN_EMAILS:}}") String rawAdminEmails) {
        this.adminEmails = parse(rawAdminEmails);
    }

    @PostConstruct
    void report() {
        if (adminEmails.isEmpty()) {
            log.warn("ASTRASTORE_ADMIN_EMAILS is empty — no account will be promoted to ADMIN "
                    + "automatically. Set it to bootstrap the first administrator.");
        } else {
            log.info("Configured administrator emails ({}): {}", adminEmails.size(), adminEmails);
        }
    }

    private static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .forEach(parsed::add);
        return Set.copyOf(parsed);
    }

    /** Whether this address should hold ADMIN, regardless of how it got here. */
    public boolean isConfiguredAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public Set<String> emails() {
        return adminEmails;
    }
}
