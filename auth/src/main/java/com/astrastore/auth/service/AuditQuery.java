/**
 * The filter behind {@code GET /api/auth/audit} and its CSV twin.
 *
 * <p>{@code userId} is the requested scope, but it is not what the query
 * runs with. {@link #effectiveUserId(Long, Long, boolean)} decides that, and
 * a non-administrator is always forced back to their own id no matter what
 * they asked for — which is what stops the endpoint from becoming a way to
 * read somebody else's login history by guessing a number.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record AuditQuery(
        Long userId,
        AuditAction action,
        Boolean success,
        Instant from,
        Instant to,
        String q
) {

    /** Free-text is matched against a few columns; keep the term bounded. */
    private static final int MAX_TERM = 128;

    /**
     * The user id the query will actually filter on.
     *
     * @param requestedUserId the {@code userId} query parameter, if any
     * @param callerId        the authenticated caller
     * @param admin           whether the caller holds ADMIN
     * @return null for an admin who did not narrow the scope (meaning
     *         cluster-wide), otherwise the single id that may be read
     */
    public static Long effectiveUserId(Long requestedUserId, Long callerId, boolean admin) {
        if (!admin) {
            // The request's userId is ignored entirely rather than rejected:
            // a 403 on "userId=8" would confirm that user 8 exists.
            return callerId;
        }
        return requestedUserId;
    }

    /**
     * Builds the JPA predicate. Every clause is optional and null-safe, so an
     * empty filter degrades to "everything you are allowed to see".
     */
    public Specification<AuditLog> toSpecification(Long effectiveUserId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (effectiveUserId != null) {
                predicates.add(cb.equal(root.get("userId"), effectiveUserId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (q != null && !q.isBlank()) {
                String term = q.trim();
                if (term.length() > MAX_TERM) {
                    term = term.substring(0, MAX_TERM);
                }
                // Escape the LIKE wildcards so a search for "100%" is a search
                // for the literal text and not a match-everything pattern.
                String pattern = "%" + term.toLowerCase()
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_") + "%";

                List<Predicate> textMatches = new ArrayList<>();
                textMatches.add(cb.like(cb.lower(root.get("ipAddress")), pattern, '!'));
                textMatches.add(cb.like(cb.lower(root.get("userAgent")), pattern, '!'));
                textMatches.add(cb.like(cb.lower(root.get("actorEmail")), pattern, '!'));
                textMatches.add(cb.like(cb.lower(root.get("detail")), pattern, '!'));
                textMatches.add(cb.like(cb.lower(root.get("failureReason")), pattern, '!'));

                // The action column holds an enum. Rather than asking the
                // database to lower-case a cast of it — which behaves
                // differently on H2 and PostgreSQL — resolve the matching
                // constants in Java and test membership.
                List<AuditAction> matchingActions = matchingActions(term);
                if (!matchingActions.isEmpty()) {
                    textMatches.add(root.get("action").in(matchingActions));
                }

                predicates.add(cb.or(textMatches.toArray(new Predicate[0])));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** The action constants whose name contains the search term. */
    private static List<AuditAction> matchingActions(String term) {
        String needle = term.toUpperCase().replace(' ', '_');
        List<AuditAction> matches = new ArrayList<>();
        for (AuditAction candidate : AuditAction.values()) {
            if (candidate.name().contains(needle)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
