/**
 * Erases an account and everything the auth service holds for it, then
 * records what the rest of the platform still has to erase.
 *
 * <h2>What this service deletes</h2>
 * <ul>
 *   <li>refresh tokens — hard delete</li>
 *   <li>API keys — hard delete</li>
 *   <li>outstanding password reset tokens — hard delete</li>
 *   <li>the user row itself, and its {@code user_roles} entries</li>
 * </ul>
 *
 * <h2>What it anonymises rather than deletes</h2>
 * <p>Audit entries. Destroying them would let anyone erase the record of
 * their own activity simply by deleting their account, which defeats the
 * purpose of an audit trail; keeping them intact would retain personal data
 * after an account was asked to go away. The compromise: {@code user_id} is
 * set to null and {@code actor_email} is replaced with the non-routable
 * marker {@code deleted-user-{id}@removed.invalid}. The security history of
 * the deployment survives, correlated only within itself, and no longer names
 * a person.
 *
 * <h2>What other services must delete</h2>
 * <p>Objects, buckets, quotas, share links and cached metrics keyed by
 * {@code user_id} live in services this one cannot reach and must not
 * modify. A row is therefore written to {@code user_deletion_events} —
 * see {@link com.astrastore.auth.entity.UserDeletionEvent} for the contract —
 * and the deletion is logged at WARN so it is visible without a database
 * query. Consumers poll for unprocessed rows and acknowledge them.
 */
package com.astrastore.auth.service;

import com.astrastore.auth.entity.User;
import com.astrastore.auth.entity.UserDeletionEvent;
import com.astrastore.auth.repository.ApiKeyRepository;
import com.astrastore.auth.repository.AuditLogRepository;
import com.astrastore.auth.repository.PasswordResetTokenRepository;
import com.astrastore.auth.repository.RefreshTokenRepository;
import com.astrastore.auth.repository.UserDeletionEventRepository;
import com.astrastore.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    /**
     * The address left on anonymised audit rows. {@code .invalid} is reserved
     * by RFC 2606 and can never resolve, so the marker cannot be mistaken for
     * a real mailbox or accidentally mailed.
     */
    private static final String ANONYMISED_EMAIL_TEMPLATE = "deleted-user-%d@removed.invalid";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserDeletionEventRepository userDeletionEventRepository;

    /**
     * Deletes the account. Callers are responsible for authorisation and for
     * writing the audit entry describing who ordered it — this method only
     * carries it out.
     *
     * @param requestedBy the acting user's id; equal to the target's own id
     *                    for a self-service deletion
     */
    @Transactional
    public void deleteAccount(User user, Long requestedBy, UserDeletionEvent.Reason reason) {
        Long userId = user.getId();
        String email = user.getEmail();
        String username = user.getUsername();

        // Announce before deleting: the outbox row and the user row are
        // written in one transaction, so there is no window in which the
        // account is gone and nothing recorded that it ever existed.
        userDeletionEventRepository.save(UserDeletionEvent.builder()
                .userId(userId)
                .username(username)
                .email(email)
                .requestedBy(requestedBy)
                .reason(reason)
                .processed(false)
                .build());

        int refreshTokens = refreshTokenRepository.deleteAllByUserId(userId);
        int apiKeys = apiKeyRepository.deleteAllByUserId(userId);
        int resetTokens = passwordResetTokenRepository.deleteAllByUserId(userId);
        int auditRows = auditLogRepository.anonymiseForUser(
                userId, String.format(ANONYMISED_EMAIL_TEMPLATE, userId));

        // Re-read inside this transaction rather than removing the caller's
        // possibly-detached copy: merging a stale entity on the way to
        // deleting it can resurrect fields that changed in between.
        userRepository.findById(userId).ifPresent(userRepository::delete);

        log.warn("Account deleted — userId={}, username={}, reason={}, requestedBy={}. "
                        + "Removed refreshTokens={}, apiKeys={}, resetTokens={}; anonymised auditRows={}. "
                        + "Other services must now delete objects, buckets and quotas owned by userId={} "
                        + "(see the user_deletion_events table).",
                userId, username, reason, requestedBy,
                refreshTokens, apiKeys, resetTokens, auditRows, userId);
    }
}
