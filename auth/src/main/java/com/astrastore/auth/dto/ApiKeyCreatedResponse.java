/**
 * Response payload returned exactly once at API key creation.
 * Contains the raw key which cannot be retrieved later — user must store it.
 * Includes all metadata from ApiKeyResponse plus the raw key value.
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
public class ApiKeyCreatedResponse {

    private Long id;
    private String name;
    private String key;
    private String keyPrefix;
    private Instant expiresAt;
    private Instant createdAt;
}
