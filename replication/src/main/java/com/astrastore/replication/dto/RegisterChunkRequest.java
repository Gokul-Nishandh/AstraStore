/**
 * Request payload for registering a chunk in the MockChunkDatabase.
 * Captures chunk metadata needed to track and heal replication state.
 */
package com.astrastore.replication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterChunkRequest {

    @NotBlank(message = "chunkId is required")
    private String chunkId;

    @Positive(message = "sizeBytes must be positive")
    private long sizeBytes;

    @NotBlank(message = "checksum is required")
    private String checksum;

    @Positive(message = "targetReplicas must be positive")
    private int targetReplicas;

    @NotEmpty(message = "replicaNodes must contain at least one node")
    private List<String> replicaNodes;
}
