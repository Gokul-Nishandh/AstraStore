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

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
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

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
    }
}
