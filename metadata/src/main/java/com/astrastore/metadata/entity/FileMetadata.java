package com.astrastore.metadata.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Represents the metadata record for a stored file/object in AstraStore.
 * Stored in PostgreSQL via Spring Data JPA.
 */
@Entity
@Table(
    name = "file_metadata",
    indexes = {
        @Index(name = "idx_file_owner", columnList = "owner"),
        @Index(name = "idx_file_storage_location", columnList = "storage_location")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Original filename as submitted by the client.
     */
    @NotBlank
    @Column(nullable = false)
    private String filename;

    /**
     * MIME content type, e.g. "image/png", "application/pdf".
     */
    @NotBlank
    @Column(name = "content_type", nullable = false)
    private String contentType;

    /**
     * File size in bytes.
     */
    @Positive
    @Column(nullable = false)
    private Long size;

    /**
     * User who uploaded the file.
     */
    @NotBlank
    @Column(nullable = false)
    private String owner;

    /**
     * SHA-256 hash of the file content — used for deduplication and integrity checks.
     */
    @Column(name = "content_hash", unique = true)
    private String contentHash;

    /**
     * The storage node / path where the primary replica lives.
     * Format depends on the storage backend: e.g. "node-1:/data/abc123" or an S3 bucket key.
     */
    @NotBlank
    @Column(name = "storage_location", nullable = false)
    private String storageLocation;

    /**
     * Number of replica copies kept for durability.
     */
    @Positive
    @Column(name = "replica_count", nullable = false)
    @Builder.Default
    private Integer replicaCount = 1;

    /**
     * Timestamp when the file was first stored.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the last metadata update.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
