/**
 * Represents an API key issued to a user for programmatic access.
 * Stores BCrypt hash; raw key is shown only once at creation.
 * Supports expiry, revocation, and tracks last-used timestamp.
 */
package com.astrastore.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "api_keys",
    indexes = {
        @Index(name = "idx_api_keys_user_id", columnList = "user_id"),
        @Index(name = "idx_api_keys_key_prefix", columnList = "key_prefix")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "key_hash", nullable = false, length = 60)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(nullable = false)
    private String name;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
