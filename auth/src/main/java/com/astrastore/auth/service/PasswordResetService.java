/**
 * The forgotten-password flow: issuing a reset credential and spending it.
 *
 * <p>Two properties of this flow are load-bearing and are enforced here
 * rather than in the controller, so that no future caller can skip them:
 *
 * <ul>
 *   <li><b>The request never discloses whether an address is registered.</b>
 *       {@link #requestReset} returns an empty optional for an unknown
 *       address and a token for a known one, and the caller answers
 *       identically either way. Timing differs slightly — a known address
 *       costs an insert — but the response does not, which is what stops the
 *       endpoint being used to enumerate the user table.</li>
 *   <li><b>The stored token cannot be replayed.</b> Only a SHA-256 hash is
 *       persisted, and spending it stamps {@code usedAt} in the same
 *       transaction that changes the password.</li>
 * </ul>
 *
 * <p>There is no mail server in this deployment, so the link is written to
 * the application log at INFO. The raw token is returned to the client only
 * when {@code astrastore.auth.expose-reset-token} is on — a switch that
 * exists to make the flow testable end to end and is pinned off in the
 * production profile.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.AuditAction;
import com.astrastore.auth.entity.PasswordResetToken;
import com.astrastore.auth.entity.User;
import com.astrastore.auth.exception.AuthException;
import com.astrastore.auth.repository.PasswordResetTokenRepository;
import com.astrastore.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    @Value("${astrastore.auth.expose-reset-token:false}")
    private boolean exposeResetToken;

    @Value("${astrastore.auth.reset-token-ttl-minutes:30}")
    private long resetTokenTtlMinutes;

    @Value("${astrastore.auth.reset-link-base-url:http://localhost:3000/reset-password}")
    private String resetLinkBaseUrl;

    /**
     * Whether the raw token may be echoed to the caller. Read by the
     * controller so the decision lives in one place.
     */
    public boolean exposesToken() {
        return exposeResetToken;
    }

    /**
     * Issues a reset token for the address, if it belongs to an account.
     *
     * @return the raw token, or empty when no account matches — the caller
     *         must answer the same way in both cases
     */
    @Transactional
    public Optional<String> requestReset(String email, HttpServletRequest httpRequest) {
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        Optional<User> found = userRepository.findByEmailIgnoreCase(normalised);
        if (found.isEmpty()) {
            // Recorded so an operator can see an enumeration attempt in the
            // trail. The row has no userId, so only an administrator reading
            // cluster-wide can ever see it.
            auditLogService.record(null, normalised, AuditAction.PASSWORD_RESET_REQUESTED,
                    httpRequest, false, "No account for that address", null);
            log.info("Password reset requested for an address with no account; no link issued.");
            return Optional.empty();
        }

        User user = found.get();

        // A new request retires the previous link. Otherwise an older link
        // sitting in a mailbox stays usable for its whole lifetime, and
        // "request another one" would widen the attack window rather than
        // close it.
        passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

        String rawToken = generateRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenTtlMinutes)))
                .build());

        auditLogService.record(user.getId(), user.getEmail(), AuditAction.PASSWORD_RESET_REQUESTED,
                httpRequest, true, null,
                "Reset link issued, valid for " + resetTokenTtlMinutes + " minutes");

        // Stands in for the mail that a deployment with an SMTP relay would
        // send. Logged at INFO rather than DEBUG because it is the only way
        // an operator can complete the flow today.
        log.info("Password reset link for userId={} — {}", user.getId(), resetLink(rawToken));

        return Optional.of(rawToken);
    }

    /**
     * Spends a reset token and sets the new password.
     *
     * @throws AuthException if the token is unknown, expired or already used
     */
    @Transactional
    public void completeReset(String rawToken, String newPassword, HttpServletRequest httpRequest) {
        Optional<PasswordResetToken> found =
                passwordResetTokenRepository.findByTokenHash(sha256Hex(rawToken));

        if (found.isEmpty() || !found.get().isUsable()) {
            auditLogService.record(found.map(PasswordResetToken::getUserId).orElse(null), null,
                    AuditAction.PASSWORD_RESET_FAILED, httpRequest, false,
                    found.isEmpty() ? "Unknown reset token" : "Reset token expired or already used", null);
            // One message for all three cases: telling the caller which one
            // applies tells an attacker holding a stolen token whether it is
            // worth continuing to try.
            throw new AuthException("This password reset link is invalid or has expired");
        }

        PasswordResetToken token = found.get();
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> {
                    auditLogService.record(token.getUserId(), null, AuditAction.PASSWORD_RESET_FAILED,
                            httpRequest, false, "Account no longer exists", null);
                    return new AuthException("This password reset link is invalid or has expired");
                });

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        // Flushed before the sweep below, so the bulk update's
        // "usedAt IS NULL" filter sees this row as already spent.
        passwordResetTokenRepository.saveAndFlush(token);
        passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

        // A reset is the recovery path for a compromised account: whoever
        // held a session before this moment must lose it.
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.record(user.getId(), user.getEmail(), AuditAction.PASSWORD_RESET_COMPLETED,
                httpRequest, true, null, "Password reset via emailed link; all sessions revoked");
        log.info("Password reset completed — userId={}", user.getId());
    }

    // --- helpers ----------------------------------------------------------

    private String resetLink(String rawToken) {
        String separator = resetLinkBaseUrl.contains("?") ? "&" : "?";
        return resetLinkBaseUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes the token for storage and lookup.
     *
     * <p>A plain digest rather than BCrypt on purpose: the token is 256 bits
     * of {@link SecureRandom} output, so there is no dictionary to slow an
     * attacker down through, and an unsalted digest is what makes the lookup
     * a single indexed read instead of a scan over every outstanding row.
     */
    private static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
