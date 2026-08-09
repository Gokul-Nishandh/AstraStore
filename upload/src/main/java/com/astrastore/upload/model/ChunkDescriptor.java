package com.astrastore.upload.model;

public record ChunkDescriptor(
        int index,
        byte[] bytes,
        String checksum
) {
}
