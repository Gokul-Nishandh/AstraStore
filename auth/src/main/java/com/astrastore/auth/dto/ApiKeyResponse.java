/**
 * Response payload for API key listing (no raw key included).
 * Contains metadata only; raw key is never returned after creation.
 * Users see key prefix for identification in the list.
 */
package com.astrastore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    private Long id;
    private String name;
    private String keyPrefix;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant lastUsedAt;
}
