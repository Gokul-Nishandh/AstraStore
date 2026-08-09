package com.astrastore.metadata.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunk_locations", schema = "metadata", uniqueConstraints = {
        @UniqueConstraint(name = "uk_object_chunk_index", columnNames = { "object_id", "chunk_index" })
}, indexes = {
        @Index(name = "idx_chunk_locations_object_id", columnList = "object_id"),
        @Index(name = "idx_chunk_locations_node_id", columnList = "node_id"),
        @Index(name = "idx_chunk_locations_replication_status", columnList = "replication_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @NotNull
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @NotBlank
    @Column(name = "node_id", length = 64, nullable = false)
    private String nodeId;

    @Column(name = "replica_node_id", length = 64)
    private String replicaNodeId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "replication_status", nullable = false, length = 20)
    private ReplicationStatus replicationStatus = ReplicationStatus.PENDING;

    @NotBlank
    @Column(name = "checksum", length = 64, nullable = false)
    private String checksum;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}