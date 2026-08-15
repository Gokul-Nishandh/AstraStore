/**
 * Grants ADMIN to the accounts the operator named in
 * {@code ASTRASTORE_ADMIN_EMAILS}.
 *
 * <p>Idempotent by construction: promotion is a set insertion, so running it
 * on every boot is a no-op once the role is present, and nothing is logged
 * the second time round. That matters because the runner fires on every
 * container restart.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.config.AdminEmailRegistry;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService {

    private final AdminEmailRegistry adminEmailRegistry;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * Promotes every configured address that already has an account.
     *
     * @return how many accounts were changed by this pass
     */
    @Transactional
    public int promoteConfiguredAdmins() {
        Set<String> configured = adminEmailRegistry.emails();
        if (configured.isEmpty()) {
            return 0;
        }

        int promoted = 0;
        int alreadyAdmin = 0;
        int notRegistered = 0;

        for (String email : configured) {
            var existing = userRepository.findByEmailIgnoreCase(email);
            if (existing.isEmpty()) {
                notRegistered++;
                log.info("Admin bootstrap — '{}' is configured as an administrator but has no account yet; "
                        + "it will be promoted automatically when it registers.", email);
                continue;
            }
            User user = existing.get();
            if (user.getRoles() != null && user.getRoles().contains(Roles.ADMIN)) {
                alreadyAdmin++;
                continue;
            }
            grantAdmin(user);
            userRepository.save(user);
            auditLogService.record(user.getId(), user.getEmail(), AuditAction.ADMIN_GRANTED,
                    null, true, null, "Promoted to ADMIN by ASTRASTORE_ADMIN_EMAILS bootstrap");
            promoted++;
            log.info("Admin bootstrap — promoted '{}' (userId={}) to ADMIN.", email, user.getId());
        }

        log.info("Admin bootstrap complete — configured={}, promoted={}, alreadyAdmin={}, notRegistered={}",
                configured.size(), promoted, alreadyAdmin, notRegistered);
        return promoted;
    }

    /**
     * Promotes a freshly registered account if its address is on the list.
     *
     * <p>Called from the registration path so that starting the stack before
     * anybody has signed up works exactly as well as signing up first. Without
     * this the operator would have to restart the auth service after creating
     * their own account.
     *
     * @return true if this registration was promoted
     */
    public boolean promoteOnRegistrationIfConfigured(User user) {
        if (user == null || !adminEmailRegistry.isConfiguredAdmin(user.getEmail())) {
            return false;
        }
        grantAdmin(user);
        log.info("Admin bootstrap — newly registered '{}' is a configured administrator; granted ADMIN.",
                user.getEmail());
        return true;
    }

    private void grantAdmin(User user) {
        // Copy first: the roles set on a loaded entity is Hibernate's own
        // PersistentSet, and Set.of()-style immutable sets appear here too for
        // rows built by the old registration path.
        Set<String> roles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
        roles.add(Roles.ADMIN);
        roles.add(Roles.USER);
        user.setRoles(roles);
    }
}
