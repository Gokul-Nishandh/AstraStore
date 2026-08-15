package com.astrastore.metadata.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's star on one object.
 *
 * <p>Modelled as its own table rather than a flag on {@code objects} because
 * starring is a property of the (user, object) pair, not of the object: the
 * moment an object is visible to more than one account — a shared bucket, an
 * admin viewing another user's data — a boolean column would show one user's
 * star to everyone.
 *
 * <p>The unique constraint is what makes {@code PUT .../star} idempotent
 * at the database level rather than only in application code.
 */
@Entity
@Table(name = "object_stars", schema = "metadata", uniqueConstraints = {
        @UniqueConstraint(name = "uk_object_star_user_object", columnNames = { "user_id", "object_id" })
}, indexes = {
        @Index(name = "idx_object_stars_user_id", columnList = "user_id"),
        @Index(name = "idx_object_stars_object_id", columnList = "object_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectStar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** {@code AstraPrincipal.userId} of the user who starred. */
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
