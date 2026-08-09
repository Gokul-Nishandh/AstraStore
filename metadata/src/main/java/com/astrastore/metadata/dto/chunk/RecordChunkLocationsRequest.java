package com.astrastore.metadata.dto.chunk;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecordChunkLocationsRequest(

        @NotEmpty
        List<ChunkLocationItem> chunks

) {
    public record ChunkLocationItem(

            @NotNull
            Integer chunkIndex,

            @NotNull
            String nodeId,

            String checksum

    ) {}
}
