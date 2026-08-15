/**
 * One row of the audit trail, as the console renders it.
 *
 * <p>The acting user is resolved to a username and email here rather than
 * left as a bare numeric id — reading the log "user-wise" is the whole point
 * of the view, and a screen full of {@code userId: 7} is not that.
 *
 * <p>{@code userId} is null for events recorded before the caller was
 * identified (a failed login against an address that does not exist) and for
 * events whose subject has since deleted their account. In the first case
 * {@code actorEmail} carries the address that was attempted; in the second it
 * carries an anonymised marker. The UI should fall back to {@code actorEmail}
 * whenever {@code username} is null.
 */
package com.astrastore.auth.dto;

import com.astrastore.auth.entity.AuditAction;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long userId,
        String username,
        String email,
        String actorEmail,
        AuditAction action,
        String ipAddress,
        String userAgent,
        boolean success,
        String failureReason,
        String detail,
        Instant timestamp
) {
}
