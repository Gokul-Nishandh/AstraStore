/**
 * Outbox row announcing that an account was deleted and that every other
 * service still holding data for that user must clean it up.
 *
 * <p>The auth service owns identity, not objects, buckets, quotas or metrics.
 * It cannot reach into the metadata or storage services, and there is no
 * message broker on the auth service's classpath, so the durable record of
 * "user N is gone" lives here in the shared PostgreSQL database. Consumers
 * poll for unprocessed rows, delete what they own, and acknowledge.
 *
 * <p>The contract is deliberately at-least-once: a consumer may see the same
 * row twice after a crash, so cleanup must be idempotent.
 */
package com.astrastore.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "user_deletion_events",
    indexes = {
        @Index(name = "idx_user_deletion_events_processed", columnList = "processed"),
        @Index(name = "idx_user_deletion_events_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeletionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id the deleted account held. This is the owner key everywhere else. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    /** Who ordered the deletion — the user themselves, or an administrator. */
    @Column(name = "requested_by")
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    @Builder.Default
    private Reason reason = Reason.SELF_SERVICE;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum Reason {
        SELF_SERVICE,
        ADMIN_ACTION
    }

    @PrePersist
    protected void onCreate() {
        requestedAt = Instant.now();
    }
}
