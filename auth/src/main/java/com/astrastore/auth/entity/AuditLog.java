/**
 * Records security-relevant events for compliance and debugging.
 * Captures user, action, IP, user-agent, and success status.
 * Old logs are auto-purged to cold storage after 90 days.
 */
package com.astrastore.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
        // The console's default view is "this user, newest first", and the
        // retention job scans by time. Both are covered by these two.
        @Index(name = "idx_audit_logs_user_id_timestamp", columnList = "user_id, timestamp"),
        @Index(name = "idx_audit_logs_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_logs_action", columnList = "action")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The acting user, when one was identified. Null for events that happen
     * before identification (a failed login against an unknown address) and
     * for events whose subject has since deleted their account — deletion
     * anonymises the trail rather than destroying it.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * The email the caller presented, recorded even when no user matched it.
     * Without this a failed-login row is an unattributable blank, which is
     * exactly the row an operator most wants to read.
     */
    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    /**
     * {@code columnDefinition} is set deliberately: without it Hibernate emits
     * a CHECK constraint enumerating every value the enum had when the table
     * was first created, and {@code ddl-auto: update} never revises it. Adding
     * an action then compiles, deploys, and fails at runtime the first time it
     * is recorded — which is exactly how AUDIT_LOG_VIEWED and fourteen others
     * ended up rejected by the database. The enum is the source of truth;
     * persistence does not need to restate it.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(64)")
    private AuditAction action;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private boolean success = true;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    /**
     * Human-readable context for successful administrative actions, e.g.
     * {@code "roles [USER] -> [USER, DEVELOPER] on user 12"}. Never contains
     * a secret.
     */
    @Column(name = "detail", length = 512)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
    }
}
