package com.astrastore.metadata.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buckets", schema = "metadata", uniqueConstraints = {
        @UniqueConstraint(name = "uk_owner_bucket_name", columnNames = { "owner_id", "name" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 63)
    @Column(name = "name", nullable = false, length = 63)
    private String name;

    /**
     * Legacy owner key. Retained because the {@code (owner_id, name)} unique
     * constraint and the frontend's {@code Bucket.ownerId} field depend on it.
     * Derived deterministically from {@link #ownerUserId} — see
     * {@code com.astrastore.metadata.security.OwnerIds}.
     */
    @NotNull
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /**
     * The real owner key: {@code AstraPrincipal.userId} from the access token.
     * Every isolation query filters on this column.
     *
     * <p>Nullable so rows written before authentication existed still load
     * under {@code ddl-auto=update}. A null owner matches no caller, so those
     * rows are invisible rather than public — the safe direction. They need a
     * one-off backfill to become reachable again.
     */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
