/**
 * Administrative operations over other people's accounts (FR-028).
 *
 * <p>Everything here is reachable only by an ADMIN, but role checks are not
 * the interesting part — the guard rails are. Two of them exist to keep the
 * deployment administrable:
 *
 * <ul>
 *   <li><b>No self-demotion.</b> An administrator cannot remove their own
 *       ADMIN role. It is almost always a misclick, and the operator who does
 *       it loses the very console they would use to undo it.</li>
 *   <li><b>No last-administrator removal.</b> The final ADMIN cannot be
 *       demoted, disabled or deleted by anyone. If it could, the system would
 *       reach a state with no administrator and no supported route back —
 *       recoverable only by hand-editing the database.</li>
 * </ul>
 *
 * <p>Both are enforced here, in the service, rather than in the controller or
 * the UI: a client that skips the console and posts directly to the API must
 * hit the same wall.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.dto.AdminUserResponse;
import com.astrastore.auth.dto.PageResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.entity.UserDeletionEvent;
import com.astrastore.auth.exception.ConflictException;
import com.astrastore.auth.exception.ForbiddenException;
import com.astrastore.auth.exception.NotFoundException;
import com.astrastore.auth.repository.ApiKeyRepository;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.security.Roles;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private static final Set<String> SORTABLE =
            Set.of("id", "username", "email", "createdAt", "lastLoginAt", "enabled");

    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccountDeletionService accountDeletionService;
    private final AuditLogService auditLogService;

    // --- listing ---------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(String search, String role, Pageable pageable) {
        Page<User> page = userRepository.findAll(specification(search, role), pageable);

        // One grouped query for the whole page rather than a count per row.
        Map<Long, Long> keyCounts = keyCountsFor(page.getContent());

        List<AdminUserResponse> rows = page.getContent().stream()
                .map(user -> toResponse(user, keyCounts.getOrDefault(user.getId(), 0L)))
                .toList();

        return PageResponse.ofMapped(page, rows);
    }

    private Specification<User> specification(String search, String role) {
        final String normalisedRole = (role == null || role.isBlank()) ? null : role.trim().toUpperCase();
        if (normalisedRole != null && !Roles.isKnown(normalisedRole)) {
            throw new IllegalArgumentException(
                    "Unknown role '" + normalisedRole + "'. Valid roles are " + Roles.all());
        }

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String term = search.trim();
                if (term.length() > 128) {
                    term = term.substring(0, 128);
                }
                // Escaped so a search for "a_b" does not match "axb".
                String pattern = "%" + term.toLowerCase()
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern, '!'),
                        cb.like(cb.lower(root.get("email")), pattern, '!')));
            }

            if (normalisedRole != null) {
                // isMember rather than a join: joining an @ElementCollection
                // multiplies the root rows and breaks both the page size and
                // the derived count query.
                predicates.add(cb.isMember(normalisedRole, root.<Set<String>>get("roles")));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<Long, Long> keyCountsFor(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : apiKeyRepository.countActiveByUserIds(ids)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    public static AdminUserResponse toResponse(User user, long apiKeyCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                sortedRoles(user),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                apiKeyCount);
    }

    /** Most privileged first, so the console can show the meaningful role. */
    private static List<String> sortedRoles(User user) {
        if (user.getRoles() == null) {
            return List.of();
        }
        return com.astrastore.shared.security.AstraPrincipal.ALL_ROLES.stream()
                .filter(user.getRoles()::contains)
                .toList();
    }

    // --- roles -----------------------------------------------------------

    /**
     * Replaces a user's roles.
     *
     * @throws ForbiddenException if an administrator is demoting themselves
     * @throws ConflictException  if this would remove the last administrator
     */
    @Transactional
    public AdminUserResponse updateRoles(User actor, Long targetId, Set<String> requestedRoles,
                                         HttpServletRequest request) {
        Set<String> newRoles = Roles.normalise(requestedRoles);
        User target = requireUser(targetId);

        Set<String> currentRoles = new LinkedHashSet<>(
                target.getRoles() == null ? Set.of() : target.getRoles());
        boolean wasAdmin = currentRoles.contains(Roles.ADMIN);
        boolean willBeAdmin = newRoles.contains(Roles.ADMIN);

        if (wasAdmin && !willBeAdmin) {
            if (target.getId().equals(actor.getId())) {
                auditLogService.record(actor.getId(), actor.getEmail(), AuditAction.ROLE_CHANGE_DENIED,
                        request, false, "Self-demotion refused",
                        "Attempted to remove own ADMIN role");
                throw new ForbiddenException(
                        "You cannot remove your own ADMIN role. Ask another administrator to do it.");
            }
            if (userRepository.countByRole(Roles.ADMIN) <= 1) {
                auditLogService.record(actor.getId(), actor.getEmail(), AuditAction.ROLE_CHANGE_DENIED,
                        request, false, "Last administrator",
                        "Attempted to demote the last ADMIN (userId=" + targetId + ")");
                throw new ConflictException(
                        "This is the last administrator. Grant ADMIN to another account first.");
            }
        }

        if (currentRoles.equals(newRoles)) {
            // Nothing to do, and nothing worth an audit entry.
            return toResponse(target, activeKeyCount(target.getId()));
        }

        target.setRoles(new LinkedHashSet<>(newRoles));
        User saved = userRepository.save(target);

        // Roles are baked into access tokens. Leaving the old refresh tokens
        // alive would let the user mint a fresh token carrying the roles they
        // just lost, for the whole refresh window.
        refreshTokenService.revokeAllForUser(saved.getId());

        auditLogService.record(actor.getId(), actor.getEmail(), AuditAction.ROLE_CHANGED,
                request, true, null,
                "roles " + currentRoles + " -> " + newRoles + " on userId=" + saved.getId()
                        + " (" + saved.getEmail() + ")");
        log.info("Roles changed by admin userId={} — targetId={}, {} -> {}",
                actor.getId(), saved.getId(), currentRoles, newRoles);

        return toResponse(saved, activeKeyCount(saved.getId()));
    }

    // --- enable / disable -------------------------------------------------

    @Transactional
    public AdminUserResponse updateStatus(User actor, Long targetId, boolean enabled, String reason,
                                          HttpServletRequest request) {
        User target = requireUser(targetId);

        if (!enabled) {
            if (target.getId().equals(actor.getId())) {
                throw new ForbiddenException(
                        "You cannot disable your own account. Ask another administrator to do it.");
            }
            boolean targetIsAdmin = target.getRoles() != null && target.getRoles().contains(Roles.ADMIN);
            if (targetIsAdmin && target.isEnabled()
                    && userRepository.countEnabledByRole(Roles.ADMIN) <= 1) {
                throw new ConflictException(
                        "This is the last enabled administrator. Enable another one first.");
            }
        }

        if (target.isEnabled() == enabled) {
            return toResponse(target, activeKeyCount(target.getId()));
        }

        target.setEnabled(enabled);
        User saved = userRepository.save(target);

        if (!enabled) {
            // Access tokens are refused by the JWT filter as soon as the flag
            // flips; refresh tokens are stateful and must be revoked, or the
            // holder simply mints a new access token and carries on.
            refreshTokenService.revokeAllForUser(saved.getId());
        }

        auditLogService.record(actor.getId(), actor.getEmail(),
                enabled ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                request, true, null,
                (enabled ? "Enabled" : "Disabled") + " userId=" + saved.getId()
                        + " (" + saved.getEmail() + ")"
                        + (reason == null || reason.isBlank() ? "" : " — reason: " + reason));
        log.info("Account {} by admin userId={} — targetId={}",
                enabled ? "enabled" : "disabled", actor.getId(), saved.getId());

        return toResponse(saved, activeKeyCount(saved.getId()));
    }

    // --- deletion ---------------------------------------------------------

    @Transactional
    public void deleteUser(User actor, Long targetId, HttpServletRequest request) {
        User target = requireUser(targetId);

        if (target.getId().equals(actor.getId())) {
            // Not a security rule so much as an ergonomic one: self-deletion
            // has its own endpoint that demands the password.
            throw new ForbiddenException(
                    "Use DELETE /api/auth/account to delete your own account.");
        }
        boolean targetIsAdmin = target.getRoles() != null && target.getRoles().contains(Roles.ADMIN);
        if (targetIsAdmin && userRepository.countByRole(Roles.ADMIN) <= 1) {
            throw new ConflictException(
                    "This is the last administrator. Grant ADMIN to another account first.");
        }

        accountDeletionService.deleteAccount(target, actor.getId(),
                UserDeletionEvent.Reason.ADMIN_ACTION);

        auditLogService.record(actor.getId(), actor.getEmail(), AuditAction.USER_DELETED,
                request, true, null,
                "Deleted userId=" + targetId + " (" + target.getEmail() + ")");
        log.warn("Account deleted by admin userId={} — targetId={}", actor.getId(), targetId);
    }

    // --- helpers ----------------------------------------------------------

    private User requireUser(Long id) {
        if (id == null) {
            throw NotFoundException.resource("User");
        }
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.resource("User"));
    }

    private long activeKeyCount(Long userId) {
        return apiKeyRepository.findByUserIdAndRevokedFalse(userId).size();
    }

    /**
     * Paging for the user list, with the same clamping and sort allow-list
     * used by the audit endpoint.
     */
    public Pageable toPageable(Integer page, Integer size, String sort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Sort resolved = Sort.by(Sort.Direction.ASC, "id");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0].trim();
            if (SORTABLE.contains(property)) {
                Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
                resolved = "id".equals(property)
                        ? Sort.by(direction, "id")
                        : Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
            }
        }
        return PageRequest.of(safePage, safeSize, resolved);
    }
}
