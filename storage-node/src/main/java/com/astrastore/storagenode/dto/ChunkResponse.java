package com.astrastore.storagenode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload returned after a successful chunk store operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResponse {

    private String chunkId;
    private String checksum;
    private Long sizeBytes;
}
