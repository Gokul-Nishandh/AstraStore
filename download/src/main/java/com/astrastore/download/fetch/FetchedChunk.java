package com.astrastore.download.fetch;

public record FetchedChunk(
        int chunkIndex,
        String sourceNode,
        byte[] data,
        String expectedChecksum
) {
}
