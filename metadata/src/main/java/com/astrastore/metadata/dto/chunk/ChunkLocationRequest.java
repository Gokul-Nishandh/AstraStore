package com.astrastore.metadata.dto.chunk;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChunkLocationRequest(

        @NotNull UUID objectId,

        @NotNull Integer chunkIndex,

        @NotBlank String nodeId,

        String checksum

) {
}