/**
 * Resolves the authenticated caller into a persistent {@link User}, and says
 * how they authenticated.
 *
 * <p>Every handler that touches user-scoped data goes through here rather
 * than trusting a path variable, because the id in a URL is chosen by the
 * caller and the identity in the security context is not.
 */
package com.astrastore.auth.security;

import com.astrastore.auth.entity.User;
import com.astrastore.auth.exception.AuthException;
import com.astrastore.auth.exception.ForbiddenException;
import com.astrastore.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CallerContext {

    private final UserRepository userRepository;

    /**
     * The account behind the current request.
     *
     * @throws AuthException if the request is unauthenticated, or if the
     *         security context names an account that no longer exists (which
     *         happens for a beat after a self-deletion).
     */
    @Transactional(readOnly = true)
    public User requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user && user.getId() != null) {
            // Re-read rather than trusting the detached entity in the security
            // context: roles or enabled-state may have changed since the token
            // was minted, and a stale copy would grant stale privileges.
            return userRepository.findById(user.getId())
                    .orElseThrow(() -> new AuthException("Authenticated account no longer exists"));
        }

        String lookup = authentication.getName();
        if (lookup == null || lookup.isBlank() || "anonymousUser".equals(lookup)) {
            throw new AuthException("Authentication required");
        }
        return userRepository.findByUsername(lookup)
                .or(() -> userRepository.findByEmailIgnoreCase(lookup))
                .orElseThrow(() -> new AuthException("Authenticated account no longer exists"));
    }

    /**
     * Whether this request was authenticated with an API key rather than an
     * interactive login.
     */
    public boolean viaApiKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof ApiKeyAuthenticationToken;
    }

    /**
     * Refuses operations that a long-lived script credential has no business
     * performing: anything that changes who an account is, what it may do, or
     * whether it continues to exist.
     */
    public void requireInteractiveLogin(String operation) {
        if (viaApiKey()) {
            throw new ForbiddenException(
                    operation + " requires an interactive login; API keys are not accepted for this operation");
        }
    }

    public boolean isAdmin(User user) {
        return user != null && user.getRoles() != null && user.getRoles().contains(Roles.ADMIN);
    }
}
