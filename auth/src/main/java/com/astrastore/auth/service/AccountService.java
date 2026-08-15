/**
 * Self-service account lifecycle: profile, password, deletion.
 *
 * <p>Everything here acts on the caller's own account and nobody else's. The
 * account is taken from the security context, never from a request parameter,
 * so there is no id for a caller to tamper with.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.dto.ProfileUpdateResponse;
import com.astrastore.auth.dto.UpdateProfileRequest;
import com.astrastore.auth.dto.UserProfileResponse;
import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.entity.UserDeletionEvent;
import com.astrastore.auth.exception.AuthException;
import com.astrastore.auth.exception.ConflictException;
import com.astrastore.auth.repository.PasswordResetTokenRepository;
import com.astrastore.auth.repository.UserRepository;
import com.astrastore.auth.security.JwtService;
import com.astrastore.shared.security.AstraPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AccountDeletionService accountDeletionService;
    private final AuditLogService auditLogService;

    // --- profile ---------------------------------------------------------

    public static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                AstraPrincipal.ALL_ROLES.stream()
                        .filter(role -> user.getRoles() != null && user.getRoles().contains(role))
                        .toList(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    /**
     * Applies a partial profile update.
     *
     * <p>A null field means "leave it alone". Changing the email address
     * changes the login identity and every claim embedded in the caller's
     * outstanding tokens, so that case revokes the old session and returns a
     * fresh token pair in the same response.
     */
    @Transactional
    public ProfileUpdateResponse updateProfile(User caller, UpdateProfileRequest request,
                                               HttpServletRequest httpRequest) {
        User user = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AuthException("Authenticated account no longer exists"));

        String previousEmail = user.getEmail();
        String previousUsername = user.getUsername();

        boolean usernameChanged = false;
        boolean emailChanged = false;

        if (request.username() != null && !request.username().trim().equals(previousUsername)) {
            user.setUsername(request.username().trim());
            usernameChanged = true;
        }

        if (request.email() != null) {
            String normalised = request.email().trim().toLowerCase(Locale.ROOT);
            if (!normalised.equalsIgnoreCase(previousEmail)) {
                // Uniqueness is checked here for a clean 409 and enforced by
                // the unique constraint underneath, which is what actually
                // wins a race between two simultaneous changes.
                if (userRepository.existsByEmailIgnoreCase(normalised)) {
                    throw new ConflictException("That email address is already in use");
                }
                user.setEmail(normalised);
                emailChanged = true;
            }
        }

        if (!usernameChanged && !emailChanged) {
            return ProfileUpdateResponse.unchangedIdentity(toProfile(user));
        }

        User saved = userRepository.save(user);

        if (emailChanged) {
            // Any outstanding reset link was addressed to the old mailbox.
            passwordResetTokenRepository.invalidateOutstandingForUser(saved.getId(), Instant.now());
            refreshTokenService.revokeAllForUser(saved.getId());

            auditLogService.record(saved.getId(), saved.getEmail(), AuditAction.EMAIL_CHANGED,
                    httpRequest, true, null,
                    "Email changed from " + previousEmail + " to " + saved.getEmail());
        }
        if (usernameChanged) {
            auditLogService.record(saved.getId(), saved.getEmail(), AuditAction.PROFILE_UPDATED,
                    httpRequest, true, null,
                    "Username changed from '" + previousUsername + "' to '" + saved.getUsername() + "'");
        }

        log.info("Profile updated — userId={}, usernameChanged={}, emailChanged={}",
                saved.getId(), usernameChanged, emailChanged);

        if (!emailChanged) {
            // The username is the token subject, so it too invalidates the
            // caller's access token; hand back a fresh pair either way.
            return reissue(saved);
        }
        return reissue(saved);
    }

    private ProfileUpdateResponse reissue(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshTokenService.CreatedRefreshToken refresh =
                refreshTokenService.createRefreshToken(user.getId());
        return ProfileUpdateResponse.reissued(toProfile(user), accessToken, refresh.rawToken());
    }

    // --- password --------------------------------------------------------

    /**
     * Changes the caller's password.
     *
     * <p>Requires the current password. On success every <em>other</em>
     * session is revoked — the point of changing a password after a suspected
     * compromise is to evict whoever else is holding one.
     */
    @Transactional
    public void changePassword(User caller, String currentPassword, String newPassword,
                               HttpServletRequest httpRequest) {
        User user = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AuthException("Authenticated account no longer exists"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            auditLogService.record(user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGE_FAILED,
                    httpRequest, false, "Current password did not match", null);
            throw new AuthException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ConflictException("The new password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());
        passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

        auditLogService.record(user.getId(), user.getEmail(), AuditAction.PASSWORD_CHANGED,
                httpRequest, true, null, "Password changed; all other sessions revoked");
        log.info("Password changed — userId={}", user.getId());
    }

    // --- deletion --------------------------------------------------------

    /**
     * Deletes the caller's own account after re-confirming the password.
     *
     * <p>An administrator who is the last one standing cannot delete
     * themselves either: the deployment would be left unadministrable, and
     * "I deleted my own account" is no better an excuse than "another admin
     * demoted me".
     */
    @Transactional
    public void deleteOwnAccount(User caller, String password, HttpServletRequest httpRequest) {
        User user = userRepository.findById(caller.getId())
                .orElseThrow(() -> new AuthException("Authenticated account no longer exists"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            auditLogService.record(user.getId(), user.getEmail(), AuditAction.ACCOUNT_DELETED,
                    httpRequest, false, "Password confirmation failed", null);
            throw new AuthException("Password is incorrect");
        }

        boolean isAdmin = user.getRoles() != null
                && user.getRoles().contains(com.astrastore.auth.security.Roles.ADMIN);
        if (isAdmin && userRepository.countByRole(com.astrastore.auth.security.Roles.ADMIN) <= 1) {
            throw new ConflictException(
                    "You are the last administrator. Grant ADMIN to another account before deleting yours.");
        }

        Long userId = user.getId();
        String email = user.getEmail();

        // Written before the row disappears, then anonymised along with the
        // rest of the trail — the entry survives, the identity does not.
        auditLogService.record(userId, email, AuditAction.ACCOUNT_DELETED,
                httpRequest, true, null, "Self-service account deletion");

        accountDeletionService.deleteAccount(user, userId, UserDeletionEvent.Reason.SELF_SERVICE);
    }

    // --- lookups ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<User> adminsExcluding(Long userId) {
        return userRepository.findAllByRole(com.astrastore.auth.security.Roles.ADMIN).stream()
                .filter(u -> !u.getId().equals(userId))
                .toList();
    }
}
