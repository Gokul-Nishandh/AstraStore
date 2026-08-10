/**
 * Response payload for audit log retrieval.
 * Contains event details for display in admin UIs and CLI tools.
 * Excludes internal fields like failure_reason for non-admin endpoints.
 */
package com.astrastore.auth.dto;

import com.astrastore.auth.entity.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private AuditAction action;
    private String ipAddress;
    private String userAgent;
    private boolean success;
    private String failureReason;
    private Instant timestamp;
}
