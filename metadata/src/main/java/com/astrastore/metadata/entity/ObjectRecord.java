package com.astrastore.metadata.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "objects", schema = "metadata", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bucket_key", columnNames = { "bucket_id", "key" })
}, indexes = {
        @Index(name = "idx_objects_bucket_id", columnList = "bucket_id"),
        @Index(name = "idx_objects_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private Bucket bucket;

    @NotBlank
    @Column(name = "key", length = 1024, nullable = false)
    private String key;

    @NotNull
    @Column(name = "size_bytes", nullable = false)
    @PositiveOrZero
    private Long sizeBytes;

    @NotBlank
    @Column(name = "checksum", length = 64, nullable = false)
    private String checksum;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ObjectStatus status = ObjectStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
