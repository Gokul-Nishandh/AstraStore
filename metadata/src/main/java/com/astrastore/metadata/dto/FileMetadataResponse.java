package com.astrastore.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response payload returned after a file metadata operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataResponse {

    private Long id;
    private String filename;
    private String contentType;
    private Long size;
    private String owner;
    private String contentHash;
    private String storageLocation;
    private Integer replicaCount;
    private Instant createdAt;
    private Instant updatedAt;
}
