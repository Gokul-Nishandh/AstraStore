package com.astrastore.metadata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating or updating a file metadata record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @Positive(message = "Size must be positive")
    private Long size;

    @NotBlank(message = "Owner is required")
    private String owner;

    private String contentHash;

    @NotBlank(message = "Storage location is required")
    private String storageLocation;

    @Positive
    private Integer replicaCount;
}
