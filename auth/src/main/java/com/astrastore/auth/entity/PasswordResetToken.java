/**
 * Single-use, short-lived credential that authorises one password reset.
 *
 * <p>Only a SHA-256 hash of the token is stored. A dump of this table
 * therefore does not let the reader reset anybody's password — the raw value
 * exists only in the link handed to the user and is never persisted or
 * returned by any endpoint.
 */
package com.astrastore.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_password_reset_tokens_hash", columnList = "token_hash"),
        @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Hex-encoded SHA-256 of the raw token. Never the token itself. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set the moment the token is spent, which is what makes it single-use. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return usedAt == null && !isExpired();
    }
}
