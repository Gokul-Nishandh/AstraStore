package com.astrastore.download.dto;

import com.astrastore.download.client.ChunkLocation;

import java.util.List;
import java.util.UUID;

public record DownloadPayload(
        UUID objectId,
        String contentType,
        long sizeBytes,
        String checksum,
        List<ChunkLocation> chunks
) {
    public boolean isEmpty() {
        return chunks.isEmpty();
    }
}
