/**
 * Request payload for simulating a node failure (chaos engineering).
 * Artificially drops replica count to trigger self-healing for testing.
 */
package com.astrastore.replication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KillNodeRequest {

    @NotBlank(message = "chunkId is required")
    private String chunkId;

    @PositiveOrZero(message = "currentReplicas must be >= 0")
    private int replicasToDrop;
}
